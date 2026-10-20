param(
    [string]$Report = "$PSScriptRoot/../services/coverage-report/target/site/jacoco-aggregate/jacoco.xml",
    [double]$MinimumLines = 0.61,
    [double]$MinimumBranches = 0.59
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Report)) { throw 'Aggregate coverage report is missing. Run Maven verify first.' }
[xml]$coverage = Get-Content -LiteralPath $Report -Raw
$groups = @($coverage.report.group | ForEach-Object name)
foreach ($name in @('api-gateway', 'document-service', 'indexing-service', 'search-service', 'common-events')) {
    if ($name -notin $groups) { throw "Missing aggregate coverage group: $name" }
}
foreach ($gate in @(@{Type='LINE'; Minimum=$MinimumLines}, @{Type='BRANCH'; Minimum=$MinimumBranches})) {
    $counter = $coverage.report.counter | Where-Object type -EQ $gate.Type
    if (-not $counter) { throw "Missing coverage counter: $($gate.Type)" }
    $total = [double]$counter.covered + [double]$counter.missed
    if ($total -le 0) { throw "Empty coverage counter: $($gate.Type)" }
    $ratio = [double]$counter.covered / $total
    Write-Output ('Aggregate {0}: {1:P2}; minimum {2:P2}' -f $gate.Type, $ratio, $gate.Minimum)
    if ($ratio -lt $gate.Minimum) { throw "Aggregate $($gate.Type) coverage is below the baseline." }
}
