$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$powerShellExecutable = (Get-Process -Id $PID).Path

function Invoke-FoundationStage {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [Parameter(Mandatory = $true)]
        [scriptblock] $Command
    )

    Write-Host "==> $Name"
    & $Command
    $stageExitCode = $LASTEXITCODE
    if ($null -ne $stageExitCode -and $stageExitCode -ne 0) {
        [Console]::Error.WriteLine("Foundation verification failed during '$Name' (exit code $stageExitCode).")
        exit $stageExitCode
    }
}

Push-Location $repoRoot
try {
    Invoke-FoundationStage 'verify-repository.ps1' {
        & $powerShellExecutable -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-repository.ps1')
    }
    Invoke-FoundationStage 'verify-foundation-clean.ps1' {
        & $powerShellExecutable -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-foundation-clean.ps1')
    }
    Invoke-FoundationStage 'verify-security-profiles.ps1' {
        & $powerShellExecutable -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-security-profiles.ps1')
    }
    Invoke-FoundationStage 'verify-swagger-annotations.ps1' {
        & $powerShellExecutable -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-swagger-annotations.ps1')
    }
    Invoke-FoundationStage 'mvnw.cmd checkstyle:check' { & (Join-Path $repoRoot 'mvnw.cmd') checkstyle:check }

    Push-Location (Join-Path $repoRoot 'frontend')
    try {
        Invoke-FoundationStage 'npm ci' { & npm ci }
        Invoke-FoundationStage 'npm run lint' { & npm run lint }
        Invoke-FoundationStage 'npm run format:check' { & npm run format:check }
    }
    finally {
        Pop-Location
    }

    Invoke-FoundationStage 'mvnw.cmd clean test' { & (Join-Path $repoRoot 'mvnw.cmd') clean test }

    Push-Location (Join-Path $repoRoot 'frontend')
    try {
        Invoke-FoundationStage 'foundation contract tests' {
            & node --test (Join-Path $repoRoot 'test/foundation-contract.test.mjs')
        }
        Invoke-FoundationStage 'npm test' { & npm test }
        Invoke-FoundationStage 'npm run build:prod' { & npm run build:prod }
        Invoke-FoundationStage 'npm audit --audit-level=high' { & npm audit --audit-level=high }
    }
    finally {
        Pop-Location
    }

    Write-Host 'Foundation verification passed.'
}
finally {
    Pop-Location
}
