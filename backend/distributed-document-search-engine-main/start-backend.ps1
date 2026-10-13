[CmdletBinding()]
param(
    [switch]$DryRun,
    [ValidateRange(1, 600)]
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$backendRoot = $PSScriptRoot
. (Join-Path $PSScriptRoot 'scripts\load-env.ps1')
Import-DocNestEnvironment (Join-Path $PSScriptRoot '.env')
if (-not $DryRun) {
    Assert-DocNestEnvironment @('JWT_SECRET', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'MINIO_ROOT_USER', 'MINIO_ROOT_PASSWORD')
    if ([Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) { throw 'JWT_SECRET must contain at least 32 bytes.' }
}
$services = @(
    @{ Name = 'document-service'; Port = 8081; Timezone = $true },
    @{ Name = 'indexing-service'; Port = 8082; Timezone = $true },
    @{ Name = 'search-service'; Port = 8083; Timezone = $false },
    @{ Name = 'api-gateway'; Port = 8080; Timezone = $true }
)

function Test-LocalPort([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $pending = $client.ConnectAsync('localhost', $Port)
        return ($pending.Wait(400) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

# Validate every path before starting any process.
foreach ($service in $services) {
    $service.Directory = Join-Path $backendRoot ('services\' + $service.Name)
    if (-not (Test-Path -LiteralPath (Join-Path $service.Directory 'pom.xml'))) {
        throw "Missing pom.xml: $($service.Directory)"
    }
}

$pendingServices = @()
foreach ($service in $services) {
    if (Test-LocalPort $service.Port) {
        Write-Host "SKIP $($service.Name): port $($service.Port) is already occupied."
        Write-Host '     This does not verify the identity or health of that process.'
    } else {
        $pendingServices += $service
    }
}
if ($pendingServices.Count -eq 0) {
    Write-Host 'No new services started.'
    exit 0
}

$maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
$shell = Join-Path $PSHOME 'powershell.exe'
if (-not (Test-Path -LiteralPath $shell)) {
    $shell = Join-Path $PSHOME 'pwsh.exe'
}

if ($DryRun) {
    foreach ($service in $pendingServices) {
        Write-Host "WOULD START $($service.Name) on port $($service.Port) from $($service.Directory)"
    }
    Write-Host "Maven: $maven"
    Write-Host 'Dry run only: no processes started and no files written.'
    exit 0
}

# A fresh checkout needs the shared event artifact before individual services run.
& $maven -B -ntp -f (Join-Path $backendRoot 'services\common-events\pom.xml') install '-DskipTests'
if ($LASTEXITCODE -ne 0) { throw 'Could not build common-events. No services were launched.' }

$logRoot = Join-Path $backendRoot ('logs\launcher\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
Write-Host "Logs: $logRoot"
Write-Host 'Docker infrastructure must already be running.'

$started = @()
foreach ($service in $pendingServices) {
    $arguments = "'spring-boot:run'"
    if ($service.Timezone) {
        $arguments += " '-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Kolkata'"
    }
    # Encode the command to preserve Unicode paths and avoid nested shell quoting.
    $quotedMaven = "'" + $maven.Replace("'", "''") + "'"
    $command = "& $quotedMaven $arguments; exit `$LASTEXITCODE"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    $stdout = Join-Path $logRoot ($service.Name + '.stdout.log')
    $stderr = Join-Path $logRoot ($service.Name + '.stderr.log')
    try {
        $process = Start-Process -FilePath $shell -ArgumentList @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encoded) -WorkingDirectory $service.Directory -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
        $started += @{ Service = $service; Process = $process; Ready = $false; Failed = $false }
        Write-Host "START $($service.Name): launcher PID $($process.Id)"
    } catch {
        Write-Warning "Failed to launch $($service.Name): $_"
        Write-Warning 'Other processes already launched remain running. Inspect the log directory.'
        exit 1
    }
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    foreach ($item in $started) {
        if ($item.Ready -or $item.Failed) { continue }
        $item.Process.Refresh()
        if ($item.Process.HasExited) {
            $item.Failed = $true
            Write-Warning "$($item.Service.Name) exited. Check its stdout and stderr logs."
        } elseif (Test-LocalPort $item.Service.Port) {
            $item.Ready = $true
            Write-Host "LISTENING $($item.Service.Name): $($item.Service.Port)"
        }
    }
    if (@($started | Where-Object { -not $_.Ready -and -not $_.Failed }).Count -eq 0) { break }
    Start-Sleep -Seconds 2
}

Write-Host "Logs: $logRoot"
Write-Host 'A listening port is not an end-to-end health check. Verify upload and search in the app.'
Write-Host 'Services run in the background; closing this terminal does not stop them.'
Write-Host 'To stop a service started here, use its printed launcher PID: taskkill /PID <PID> /T'
if (@($started | Where-Object { -not $_.Ready }).Count -gt 0) {
    Write-Warning 'Some services failed or did not listen before the timeout. Slow processes may still be starting.'
    exit 1
}
