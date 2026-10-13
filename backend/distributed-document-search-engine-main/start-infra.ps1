[CmdletBinding()]
param([switch]$ValidateOnly)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\load-env.ps1')
Import-DocNestEnvironment (Join-Path $PSScriptRoot '.env')
Assert-DocNestEnvironment @('POSTGRES_USER', 'POSTGRES_PASSWORD', 'MINIO_ROOT_USER', 'MINIO_ROOT_PASSWORD')
$composePath = Join-Path $PSScriptRoot 'infra\docker-compose.yml'
docker compose -p infra -f $composePath config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration is invalid.' }
if ($ValidateOnly) { Write-Host 'Compose configuration is valid.'; return }
docker compose -p infra -f $composePath up -d
if ($LASTEXITCODE -ne 0) { throw 'Infrastructure startup failed. Ensure Docker Desktop is running.' }
Write-Host 'Infrastructure started. MinIO bucket initialization runs in minio-init.'
Write-Host 'Check readiness: docker compose -p infra -f infra/docker-compose.yml ps -a'
