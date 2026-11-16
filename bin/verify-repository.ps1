$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot

try {
    $requiredTrackedFiles = @(
        'frontend/package-lock.json'
        'frontend/src/utils/passwordPolicy.js'
        'frontend/test/passwordPolicy.test.js'
        'test/foundation-contract.test.mjs'
        'bin/verify-foundation.ps1'
        'bin/verify-api-contracts.ps1'
        'bin/verify-version-consistency.ps1'
        '.mvn/wrapper/maven-wrapper.properties'
        'mvnw'
        'mvnw.cmd'
        '.github/dependabot.yml'
        '.github/workflows/ci.yml'
        '.github/workflows/dependency-check.yml'
    )
    foreach ($requiredFile in $requiredTrackedFiles) {
        if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
            throw "$requiredFile does not exist"
        }

        $previousErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & git ls-files --error-unmatch -- $requiredFile 2>$null | Out-Null
            $trackedExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($trackedExitCode -ne 0) {
            $notTrackedMessage = '{0}{1}{2}' -f ([char]0x672A), ([char]0x7EB3), ([char]0x7BA1)
            throw "$requiredFile $notTrackedMessage"
        }

        & git check-ignore --no-index --quiet -- $requiredFile
        if ($LASTEXITCODE -eq 0) {
            throw "$requiredFile is ignored by .gitignore"
        }
        if ($LASTEXITCODE -ne 1) {
            throw "Unable to check ignore status for $requiredFile"
        }
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
