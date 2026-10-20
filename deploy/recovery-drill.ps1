param([string]$OutputRoot = "$PSScriptRoot/../.local-recovery")
$ErrorActionPreference = 'Stop'
$dockerBinary = (Get-Command docker -CommandType Application -ErrorAction Stop).Source
$runId = 'docnest-drill-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$runDir = Join-Path ([IO.Path]::GetFullPath($OutputRoot)) $runId
New-Item -ItemType Directory -Path $runDir | Out-Null
$fixture = Join-Path $PSScriptRoot 'recovery-fixture.txt'
$expectedHash = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash
$containers = @()
$savedEnv = @{}
foreach ($name in @('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_DEFAULT_REGION', 'AWS_EC2_METADATA_DISABLED', 'AWS_PROFILE')) {
    $savedEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
function Docker {
    param([string[]]$Arguments)
    $result = & $dockerBinary @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: $($Arguments[0])" }
    return $result
}
function S3 {
    param([string]$Endpoint, [string[]]$Arguments)
    & aws --endpoint-url $Endpoint --region us-east-1 --no-cli-pager @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Local S3 operation failed.' }
}
function Wait-Database {
    param([string]$Name)
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        & $dockerBinary exec $Name pg_isready -U drill -d drill *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw 'Isolated database did not become ready.'
}
function Wait-Storage {
    param([string]$Name)
    $mapping = (Docker @('port', $Name, '9000/tcp') | Select-Object -First 1).Trim()
    if ($mapping -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Storage is not bound exclusively to localhost.' }
    $endpoint = "http://$mapping"
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try {
            $response = Invoke-WebRequest "$endpoint/minio/health/live" -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return $endpoint }
        } catch { Start-Sleep -Seconds 1 }
    }
    throw 'Isolated storage did not become ready.'
}
try {
    if (-not (Get-Command aws -ErrorAction SilentlyContinue)) { throw 'Install the official AWS CLI v2. It is used only with explicit localhost endpoints, not AWS cloud services.' }
    Docker @('info', '--format', '{{.OSType}}') | Out-Null
    $env:AWS_ACCESS_KEY_ID = 'drill-local-user'
    $env:AWS_SECRET_ACCESS_KEY = 'drill-local-password-not-for-production'
    $env:AWS_DEFAULT_REGION = 'us-east-1'
    $env:AWS_EC2_METADATA_DISABLED = 'true'
    Remove-Item Env:AWS_PROFILE -ErrorAction SilentlyContinue
    $started = [datetime]::UtcNow
    $sourceDb = "$runId-source-db"; $targetDb = "$runId-restored-db"
    $sourceStorage = "$runId-source-storage"; $targetStorage = "$runId-restored-storage"
    foreach ($name in @($sourceDb, $targetDb)) {
        Docker @('run', '-d', '--name', $name, '--label', "docnest.recovery=$runId", '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_USER=drill', '-e', 'POSTGRES_DB=drill', '-e', 'POSTGRES_PASSWORD=local-drill-only', 'postgres:15') | Out-Null
        $containers += $name
        Wait-Database $name
    }
    $storageArgs = @('--label', "docnest.recovery=$runId", '-p', '127.0.0.1::9000', '-e', "MINIO_ROOT_USER=$env:AWS_ACCESS_KEY_ID", '-e', "MINIO_ROOT_PASSWORD=$env:AWS_SECRET_ACCESS_KEY", 'docnest/minio-ci:2025-09-07', 'server', '/data')
    Docker (@('run', '-d', '--name', $sourceStorage) + $storageArgs) | Out-Null
    $containers += $sourceStorage
    $sourceEndpoint = Wait-Storage $sourceStorage
    S3 $sourceEndpoint @('s3api', 'create-bucket', '--bucket', 'documents') | Out-Null
    S3 $sourceEndpoint @('s3', 'cp', $fixture, 's3://documents/drill/sample.txt', '--only-show-errors')
    $sql = "CREATE TABLE recovery_documents (id integer PRIMARY KEY, storage_path text NOT NULL, sha256 text NOT NULL); INSERT INTO recovery_documents VALUES (1, 'drill/sample.txt', '$expectedHash');"
    Docker @('exec', $sourceDb, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'drill', '-d', 'drill', '-c', $sql) | Out-Null
    # Freeze synthetic writes before taking the paired storage/database backup.
    Docker @('stop', $sourceStorage) | Out-Null
    Docker @('exec', $sourceDb, 'pg_dump', '-U', 'drill', '-d', 'drill', '-Fc', '-f', '/tmp/database.dump') | Out-Null
    $dump = Join-Path $runDir 'database.dump'
    $objects = Join-Path $runDir 'minio-data'
    New-Item -ItemType Directory -Path $objects | Out-Null
    Docker @('cp', "${sourceDb}:/tmp/database.dump", $dump) | Out-Null
    Docker @('cp', "${sourceStorage}:/data/.", $objects) | Out-Null
    $restoreStarted = [datetime]::UtcNow
    Docker @('cp', $dump, "${targetDb}:/tmp/database.dump") | Out-Null
    Docker @('exec', $targetDb, 'pg_restore', '--exit-on-error', '-U', 'drill', '-d', 'drill', '/tmp/database.dump') | Out-Null
    Docker (@('create', '--name', $targetStorage) + $storageArgs) | Out-Null
    $containers += $targetStorage
    Docker @('cp', "$objects/.", "${targetStorage}:/data") | Out-Null
    Docker @('start', $targetStorage) | Out-Null
    $targetEndpoint = Wait-Storage $targetStorage
    $restoredMetadata = (Docker @('exec', $targetDb, 'psql', '-U', 'drill', '-d', 'drill', '-At', '-c', 'SELECT storage_path || ''|'' || sha256 FROM recovery_documents WHERE id=1;')).Trim()
    if ($restoredMetadata -ne "drill/sample.txt|$expectedHash") { throw 'Restored document metadata does not match.' }
    $restoredFile = Join-Path $runDir 'restored-sample.txt'
    S3 $targetEndpoint @('s3', 'cp', 's3://documents/drill/sample.txt', $restoredFile, '--only-show-errors')
    if ((Get-FileHash -LiteralPath $restoredFile -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Restored object hash does not match.' }
    $result = [ordered]@{ runId=$runId; result='PASS'; scope='Synthetic PostgreSQL metadata and MinIO cold-copy restore; not full application disaster recovery'; records=1; objects=1; sha256=$expectedHash; restoreSeconds=[math]::Round(([datetime]::UtcNow - $restoreStarted).TotalSeconds, 2); totalSeconds=[math]::Round(([datetime]::UtcNow - $started).TotalSeconds, 2) }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDir 'result.json') -Encoding UTF8
    Write-Output ($result | ConvertTo-Json -Compress)
    Write-Output "Synthetic backup retained at: $runDir"
} finally {
    foreach ($name in $containers) {
        $labelJson = & $dockerBinary inspect --format '{{json .Config.Labels}}' $name 2>$null
        if ($LASTEXITCODE -eq 0 -and ($labelJson | ConvertFrom-Json).'docnest.recovery' -eq $runId) { & $dockerBinary rm -f $name | Out-Null }
    }
    foreach ($name in $savedEnv.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnv[$name], 'Process') }
}
