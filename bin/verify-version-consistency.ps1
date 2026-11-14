param(
    [string] $ExpectedVersion,
    [string] $PomPath,
    [string] $FrontendPackagePath,
    [string] $ApplicationYamlPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not $PomPath) {
    $PomPath = Join-Path $repoRoot 'pom.xml'
}
if (-not $FrontendPackagePath) {
    $FrontendPackagePath = Join-Path $repoRoot 'frontend/package.json'
}
if (-not $ApplicationYamlPath) {
    $ApplicationYamlPath = Join-Path $repoRoot 'opc-admin/src/main/resources/application.yml'
}

function Fail-VersionVerification {
    param([string] $Message)

    [Console]::Error.WriteLine("Version consistency verification failed: $Message")
    exit 1
}

function Assert-VersionFile {
    param(
        [string] $Path,
        [string] $Description
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Fail-VersionVerification "$Description not found at $Path"
    }
}

function Assert-SemVer {
    param(
        [string] $Version,
        [string] $Description
    )

    if ($Version -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
        Fail-VersionVerification "$Description must be a three-part SemVer value"
    }
}

function Read-ApplicationVersion {
    param([string] $Path)

    $lines = [IO.File]::ReadAllLines($Path, [Text.Encoding]::UTF8)
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index].Contains("`t")) {
            Fail-VersionVerification "application YAML contains a tab at line $($index + 1)"
        }
    }

    $opcLineIndexes = @(for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^opc\s*:') {
            $index
        }
    })
    if ($opcLineIndexes.Count -ne 1) {
        Fail-VersionVerification 'application YAML must contain exactly one top-level opc mapping'
    }
    $opcLineIndex = $opcLineIndexes[0]
    if ($lines[$opcLineIndex] -notmatch '^opc:\s*(?:#.*)?$') {
        Fail-VersionVerification 'application YAML top-level opc must be an exclusive mapping line'
    }

    $blockEndIndex = $lines.Count
    for ($index = $opcLineIndex + 1; $index -lt $lines.Count; $index++) {
        $line = $lines[$index]
        if ($line -match '^\s*$' -or $line -match '^\s*#') {
            continue
        }
        if ($line -notmatch '^ ') {
            $blockEndIndex = $index
            break
        }
    }

    $versionLineIndexes = @(for ($index = $opcLineIndex + 1; $index -lt $blockEndIndex; $index++) {
        if ($lines[$index] -match '^ +version\s*:') {
            $index
        }
    })
    if ($versionLineIndexes.Count -ne 1) {
        Fail-VersionVerification 'application YAML opc mapping must contain exactly one version entry'
    }

    $versionLine = $lines[$versionLineIndexes[0]]
    if ($versionLine -notmatch '^  version: +(?<value>[^#]*?)(?: +#.*)? *$') {
        Fail-VersionVerification 'application YAML opc.version must use two-space indentation and a space before its scalar value'
    }
    $value = $Matches.value.Trim()
    if ($value.Length -eq 0) {
        Fail-VersionVerification 'application YAML opc.version must be a scalar value'
    }
    if (
        ($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))
    ) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    return $value
}

Assert-VersionFile -Path $PomPath -Description 'root POM'
Assert-VersionFile -Path $FrontendPackagePath -Description 'frontend package.json'
Assert-VersionFile -Path $ApplicationYamlPath -Description 'application YAML'

try {
    [xml] $pom = [IO.File]::ReadAllText($PomPath, [Text.Encoding]::UTF8)
    $pomVersionNodes = @($pom.SelectNodes('/*[local-name()="project"]/*[local-name()="version"]'))
    if ($pomVersionNodes.Count -ne 1) {
        Fail-VersionVerification 'root POM must contain exactly one project version'
    }
    $pomVersion = $pomVersionNodes[0].InnerText.Trim()
}
catch {
    Fail-VersionVerification "root POM could not be parsed: $($_.Exception.Message)"
}

try {
    $frontendPackage = [IO.File]::ReadAllText($FrontendPackagePath, [Text.Encoding]::UTF8) | ConvertFrom-Json
    $frontendVersion = [string] $frontendPackage.version
    if ([string]::IsNullOrWhiteSpace($frontendVersion)) {
        Fail-VersionVerification 'frontend package.json must contain a version'
    }
    $frontendVersion = $frontendVersion.Trim()
}
catch {
    Fail-VersionVerification "frontend package.json could not be parsed: $($_.Exception.Message)"
}

$applicationVersion = Read-ApplicationVersion -Path $ApplicationYamlPath
Assert-SemVer -Version $pomVersion -Description 'root POM version'
Assert-SemVer -Version $frontendVersion -Description 'frontend package.json version'
Assert-SemVer -Version $applicationVersion -Description 'application YAML version'

if ($pomVersion -ne $frontendVersion -or $pomVersion -ne $applicationVersion) {
    Fail-VersionVerification "version mismatch: POM=$pomVersion, frontend=$frontendVersion, application=$applicationVersion"
}

if ($ExpectedVersion) {
    $normalizedExpectedVersion = $ExpectedVersion
    if ($normalizedExpectedVersion.StartsWith('v')) {
        $normalizedExpectedVersion = $normalizedExpectedVersion.Substring(1)
    }
    Assert-SemVer -Version $normalizedExpectedVersion -Description 'ExpectedVersion'
    if ($pomVersion -ne $normalizedExpectedVersion) {
        Fail-VersionVerification "expected version $normalizedExpectedVersion does not match repository version $pomVersion"
    }
}

Write-Host "Version consistency verification passed: $pomVersion"
