[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][int]$Partition,
    [Parameter(Mandatory)][long]$Offset,
    [switch]$Execute,
    [string]$EnvFile = "$PSScriptRoot/.env"
)
$ErrorActionPreference = 'Stop'
if ($Partition -lt 0 -or $Offset -lt 0) { throw 'Partition and offset must be non-negative.' }
$composeArgs = @('compose', '--env-file', $EnvFile, '-f', "$PSScriptRoot/compose.yaml")
$payload = & docker @composeArgs exec -T kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic document_uploaded_dlq --partition $Partition --offset $Offset --max-messages 1 --timeout-ms 10000
if ($LASTEXITCODE -ne 0) { throw 'Could not read the selected DLQ record.' }
$event = ($payload -join "`n") | ConvertFrom-Json
foreach ($field in @('documentId', 'ownerId', 'storagePath')) {
    if (-not $event.$field) { throw "Missing required field: $field" }
}
Write-Output "Selected document $($event.documentId), partition $Partition, offset $Offset."
if (-not $Execute) { Write-Output 'Dry run only. Investigate and fix the failure before using -Execute.'; return }
if ($PSCmdlet.ShouldProcess($event.documentId, 'Replay one DLQ event with retryCount reset')) {
    $event.retryCount = 0
    $event | ConvertTo-Json -Compress -Depth 20 | & docker @composeArgs exec -T kafka kafka-console-producer --bootstrap-server kafka:9092 --topic document_uploaded
    if ($LASTEXITCODE -ne 0) { throw 'Replay producer failed. Inspect state before retrying.' }
    Write-Output 'Event submitted. Verify READY state and search results; the original DLQ record is retained.'
}
