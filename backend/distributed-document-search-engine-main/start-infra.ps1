[CmdletBinding()]
param([switch]$ValidateOnly, [ValidateRange(30, 600)][int]$TimeoutSeconds = 180)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\load-env.ps1')
Import-DocNestEnvironment (Join-Path $PSScriptRoot '.env')
Assert-DocNestEnvironment @('POSTGRES_USER', 'POSTGRES_PASSWORD', 'MINIO_ROOT_USER', 'MINIO_ROOT_PASSWORD')
$composePath = Join-Path $PSScriptRoot 'infra\docker-compose.yml'
docker compose -p infra -f $composePath config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration is invalid. See the Compose error above.' }
if ($ValidateOnly) { Write-Host 'Compose configuration is valid.'; return }
docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot connect to Docker. Start Docker Desktop with Linux containers first.' }

$minioImage = if ($env:MINIO_IMAGE) { $env:MINIO_IMAGE } else { 'quay.io/minio/minio:latest' }
$cachedMinio = docker image ls --quiet --filter "reference=$minioImage"
if (-not $cachedMinio -and -not $env:MINIO_IMAGE) {
    $env:MINIO_IMAGE = 'docnest/minio-local:2025-09-07'
    $cachedMinio = docker image ls --quiet --filter "reference=$env:MINIO_IMAGE"
    if (-not $cachedMinio) {
        Write-Host 'No cached MinIO image. Building the pinned official source (first run may take several minutes).'
        docker build -t $env:MINIO_IMAGE -f (Join-Path $PSScriptRoot 'infra\minio-ci.Dockerfile') (Join-Path $PSScriptRoot 'infra')
        if ($LASTEXITCODE -ne 0) { throw 'MinIO image build failed. Existing containers and volumes have not been removed.' }
    }
}

for ($attempt = 1; $attempt -le 3; $attempt++) {
    docker compose -p infra -f $composePath up -d --wait --wait-timeout $TimeoutSeconds
    if ($LASTEXITCODE -eq 0) {
        Write-Host 'Infrastructure is healthy: Kafka, Zookeeper, PostgreSQL, MinIO, Elasticsearch and Redis.'
        Write-Host 'Document Service will check/create its MinIO bucket when the backend starts.'
        return
    }
    $kafkaId = docker compose -p infra -f $composePath ps --all --quiet kafka
    $retryKafka = $false
    if ($kafkaId) {
        $state = docker inspect --format '{{.State.Status}}' $kafkaId
        $kafkaLog = docker logs --tail 200 $kafkaId 2>&1 | Out-String
        $retryKafka = $state -eq 'exited' -and $kafkaLog -match 'NodeExistsException'
    }
    if ($retryKafka -and $attempt -lt 3) {
        Write-Warning "Kafka encountered a stale Zookeeper registration. Waiting 15 seconds before retry $($attempt + 1)/3."
        Start-Sleep -Seconds 15
        continue
    }
    docker compose -p infra -f $composePath ps -a
    throw 'One or more infrastructure services failed readiness checks. See the service error/status above; inspect its logs with docker logs <container>. No data volumes were deleted.'
}
