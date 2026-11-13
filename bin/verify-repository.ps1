$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot

try {
    $lockFile = 'frontend/package-lock.json'
    if (-not (Test-Path -LiteralPath $lockFile -PathType Leaf)) {
        throw "$lockFile does not exist"
    }

    & git check-ignore --no-index --quiet -- $lockFile
    if ($LASTEXITCODE -eq 0) {
        throw "$lockFile is ignored by .gitignore"
    }
    if ($LASTEXITCODE -ne 1) {
        throw "Unable to check ignore status for $lockFile"
    }

    $requiredPathspecs = @(
        '*.java'
        '*.vue'
        '*.sql'
        'pom.xml'
        '**/pom.xml'
        '*.yml'
    )
    $untrackedRequired = @(& git ls-files --others --exclude-standard -- $requiredPathspecs)
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to check for untracked repository files'
    }
    if ($untrackedRequired.Count -gt 0) {
        $details = $untrackedRequired -join [Environment]::NewLine
        throw "Untracked required repository files exist:$([Environment]::NewLine)$details"
    }

    Write-Host 'Repository tracking boundary verification passed.'
}
finally {
    Pop-Location
}
