param(
    [string] $ReportPath,
    [string] $PomPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $ReportPath) {
    $ReportPath = Join-Path $repoRoot 'opc-coverage/target/site/jacoco-aggregate/jacoco.xml'
}
if (-not $PomPath) {
    $PomPath = Join-Path $repoRoot 'pom.xml'
}

function Fail-CoverageVerification {
    param([string] $Message)

    [Console]::Error.WriteLine("Aggregate coverage verification failed: $Message")
    exit 1
}

if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
    Fail-CoverageVerification "report not found at $ReportPath"
}
if (-not (Test-Path -LiteralPath $PomPath -PathType Leaf)) {
    Fail-CoverageVerification "POM not found at $PomPath"
}

[xml] $pom = [IO.File]::ReadAllText($PomPath, [Text.Encoding]::UTF8)
[xml] $report = [IO.File]::ReadAllText($ReportPath, [Text.Encoding]::UTF8)
$thresholdText = $pom.project.properties.'jacoco.minimum.line.ratio'
$threshold = [decimal] 0
if (-not [decimal]::TryParse(
    $thresholdText,
    [Globalization.NumberStyles]::Number,
    [Globalization.CultureInfo]::InvariantCulture,
    [ref] $threshold
)) {
    Fail-CoverageVerification 'jacoco.minimum.line.ratio must be a decimal POM property'
}

$lineCounter = @($report.report.counter | Where-Object { $_.type -eq 'LINE' })
$instructionCounter = @($report.report.counter | Where-Object { $_.type -eq 'INSTRUCTION' })
if ($lineCounter.Count -ne 1 -or $instructionCounter.Count -ne 1) {
    Fail-CoverageVerification 'aggregate report must contain total LINE and INSTRUCTION counters'
}

$lineMissed = [long] $lineCounter[0].missed
$lineCovered = [long] $lineCounter[0].covered
$instructionTotal = [long] $instructionCounter[0].missed + [long] $instructionCounter[0].covered
$lineTotal = $lineMissed + $lineCovered
if ($lineTotal -le 0 -or $instructionTotal -le 0) {
    Fail-CoverageVerification 'aggregate report is empty'
}

$expectedModules = @($pom.project.modules.module | ForEach-Object { [string] $_ } | Where-Object { $_ -ne 'opc-coverage' } | Sort-Object -Unique)
$groups = @($report.report.group)
$actualModules = @($groups | ForEach-Object { [string] $_.name } | Sort-Object -Unique)
$missingModules = @($expectedModules | Where-Object { $_ -notin $actualModules })
$unexpectedModules = @($actualModules | Where-Object { $_ -notin $expectedModules })
if (
    $expectedModules.Count -eq 0 -or
    $actualModules.Count -ne $expectedModules.Count -or
    $missingModules.Count -gt 0 -or
    $unexpectedModules.Count -gt 0
) {
    Fail-CoverageVerification "aggregate module set mismatch; missing: $($missingModules -join ', '); unexpected: $($unexpectedModules -join ', ')"
}

$emptyPackageModules = @($groups | Where-Object { @($_.package).Count -eq 0 } | ForEach-Object { [string] $_.name })
if ($emptyPackageModules.Count -gt 0) {
    Fail-CoverageVerification "aggregate modules contain no business packages: $($emptyPackageModules -join ', ')"
}

$ratio = [decimal] $lineCovered / [decimal] $lineTotal
$percentage = ($ratio * [decimal] 100).ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
$thresholdPercentage = ($threshold * [decimal] 100).ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
if ($ratio -lt $threshold) {
    Fail-CoverageVerification "LINE coverage $percentage% is below the $thresholdPercentage% baseline"
}

Write-Host "Aggregate LINE coverage: $percentage% (baseline: $thresholdPercentage%; $lineCovered/$lineTotal lines; $($groups.Count) modules)."
