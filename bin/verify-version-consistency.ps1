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

    $entries = @()
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadAllLines($Path, [Text.Encoding]::UTF8)) {
        $lineNumber++
        if ($line -match '^\s*$' -or $line -match '^\s*#') {
            continue
        }
        if ($line -match '^\t|^ +\t') {
            Fail-VersionVerification "application YAML uses a tab for indentation at line $lineNumber"
        }
        if ($line -notmatch '^(?<indent> *)(?<key>[^:#][^:]*):(?<value>.*)$') {
            continue
        }
        $entries += [pscustomobject]@{
            Indent = $Matches.indent.Length
            Key = $Matches.key.Trim()
            Value = $Matches.value
            Line = $lineNumber
        }
    }

    $opcEntries = @($entries | Where-Object { $_.Indent -eq 0 -and $_.Key -eq 'opc' })
    if ($opcEntries.Count -ne 1) {
        Fail-VersionVerification 'application YAML must contain exactly one top-level opc mapping'
    }
    $opcEntry = $opcEntries[0]
    $opcValue = ($opcEntry.Value -replace '\s+#.*$', '').Trim()
    if ($opcValue.Length -ne 0) {
        Fail-VersionVerification 'application YAML top-level opc value must be a mapping'
    }

    $opcChildren = @($entries | Where-Object {
        $_.Line -gt $opcEntry.Line -and $_.Indent -gt 0
    })
    $nextTopLevel = @($entries | Where-Object {
        $_.Line -gt $opcEntry.Line -and $_.Indent -eq 0
    } | Sort-Object Line | Select-Object -First 1)
    if ($nextTopLevel.Count -eq 1) {
        $opcChildren = @($opcChildren | Where-Object { $_.Line -lt $nextTopLevel[0].Line })
    }
    if ($opcChildren.Count -eq 0) {
        Fail-VersionVerification 'application YAML opc mapping is empty or missing version'
    }

    $directIndent = ($opcChildren | Measure-Object Indent -Minimum).Minimum
    $versionEntries = @($opcChildren | Where-Object {
        $_.Indent -eq $directIndent -and $_.Key -eq 'version'
    })
    if ($versionEntries.Count -ne 1) {
        Fail-VersionVerification 'application YAML opc mapping must contain exactly one version entry'
    }

    $value = ($versionEntries[0].Value -replace '\s+#.*$', '').Trim()
    if (
        $value.Length -eq 0 -or
        $value.StartsWith('[') -or
        $value.StartsWith('{') -or
        $value.StartsWith('|') -or
        $value.StartsWith('>')
    ) {
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
