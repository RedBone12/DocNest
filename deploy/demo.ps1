param(
    [ValidateSet('Start', 'Status', 'Verify', 'Stop')]
    [string]$Action = 'Start',
    [switch]$Build,
    [switch]$RecordVideo
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$frontend = Join-Path $repoRoot 'frontend/distributed-document-search-engine-ui-main'
$composeArgs = @('compose', '--env-file', 'deploy/.env.example', '-f', 'deploy/compose.yaml')

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE." }
}

Push-Location $repoRoot
try {
    Invoke-Checked docker @('info', '--format', '{{.OSType}}')
    Invoke-Checked docker ($composeArgs + @('config', '--quiet'))
    switch ($Action) {
        'Start' {
            & docker image inspect 'docnest/minio-ci:2025-09-07' *> $null
            if ($LASTEXITCODE -ne 0) {
                Invoke-Checked docker @('build', '-t', 'docnest/minio-ci:2025-09-07', '-f',
                    'backend/distributed-document-search-engine-main/infra/minio-ci.Dockerfile',
                    'backend/distributed-document-search-engine-main/infra')
            }
            $upArgs = @('up', '-d', '--wait', '--wait-timeout', '600')
            if ($Build) { $upArgs += '--build' }
            Invoke-Checked docker ($composeArgs + $upArgs)
            Invoke-Checked docker ($composeArgs + @('ps'))
            Write-Host 'Local demo: http://localhost:8088 (AI and SMTP are not configured).'
        }
        'Status' { Invoke-Checked docker ($composeArgs + @('ps')) }
        'Verify' {
            $response = Invoke-WebRequest 'http://localhost:8088' -UseBasicParsing -TimeoutSec 15
            if ($response.StatusCode -ne 200) { throw 'The frontend is not ready.' }
            Push-Location $frontend
            $previousBaseUrl = $env:E2E_BASE_URL
            $previousVideo = $env:E2E_RECORD_VIDEO
            try {
                # Always target this local disposable-account demo, never a remote tenant.
                $env:E2E_BASE_URL = 'http://localhost:8088'
                $env:E2E_RECORD_VIDEO = if ($RecordVideo) { '1' } else { '0' }
                Invoke-Checked npm.cmd @('run', 'test:e2e')
            } finally {
                $env:E2E_BASE_URL = $previousBaseUrl
                $env:E2E_RECORD_VIDEO = $previousVideo
                Pop-Location
            }
        }
        'Stop' {
            Invoke-Checked docker ($composeArgs + @('stop'))
            Write-Host 'Demo stopped. Containers and data volumes are retained.'
        }
    }
} finally { Pop-Location }
