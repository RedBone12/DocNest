[CmdletBinding()]
param([switch]$ValidateOnly)
& (Join-Path $PSScriptRoot 'backend\distributed-document-search-engine-main\start-infra.ps1') -ValidateOnly:$ValidateOnly
