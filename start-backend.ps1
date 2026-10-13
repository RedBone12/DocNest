[CmdletBinding()]
param([switch]$DryRun, [ValidateRange(1, 600)][int]$TimeoutSeconds = 120)
& (Join-Path $PSScriptRoot 'backend\distributed-document-search-engine-main\start-backend.ps1') -DryRun:$DryRun -TimeoutSeconds $TimeoutSeconds
