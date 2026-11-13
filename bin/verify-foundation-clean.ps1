$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$brandPattern = 'ruo' + 'yi'
$chinesePattern = [string][char]0x82E5 + [string][char]0x4F9D
$taskPattern = 'r' + 'yTask'
$taskClassPattern = 'R' + 'yTask'
$passwordPattern = 'admin' + '123'
$copyrightPattern = 'Copyright ' + [string][char]0x00A9 + ' 2018-2026'
$shortNamePattern = '\b' + 'r' + 'y' + '\b'
$patterns = @(
    $brandPattern,
    $chinesePattern,
    $taskPattern,
    $taskClassPattern,
    $passwordPattern,
    $copyrightPattern,
    $shortNamePattern
)

$arguments = @(
    '--no-ignore',
    '--line-number',
    '--ignore-case',
    '--hidden',
    '--glob', '!.git',
    '--glob', '!**/.git/**',
    '--glob', '!.worktrees/**',
    '--glob', '!**/.worktrees/**',
    '--glob', '!worktrees/**',
    '--glob', '!**/worktrees/**',
    '--glob', '!**/target/**',
    '--glob', '!**/node_modules/**',
    '--glob', '!**/dist/**',
    '--glob', '!docs/superpowers/**',
    '--glob', '!THIRD_PARTY_NOTICES.md',
    '--glob', '!opc-local.env.ps1',
    '--glob', '!**/*.local',
    '--glob', '!**/.idea/**',
    '--glob', '!**/.vscode/**',
    '--glob', '!.local-backups/**',
    '--glob', '!dependency-tree*.txt',
    '--glob', '!task_plan.md',
    '--glob', '!findings.md',
    '--glob', '!progress.md',
    '--glob', '!powershell-console-backup.reg',
    '--glob', '!powershell-diagnostics.md',
    '--regexp', ($patterns -join '|')
)

Push-Location $repoRoot
try {
    $primaryArguments = $arguments + @('--glob', '!**/build/**', '.')
    & rg @primaryArguments
    $primaryExitCode = $LASTEXITCODE

    $sourceBuildExitCode = 1
    $sourceBuildRoot = 'frontend/src/views/tool/build'
    if (Test-Path -LiteralPath $sourceBuildRoot -PathType Container) {
        & rg @arguments $sourceBuildRoot
        $sourceBuildExitCode = $LASTEXITCODE
    }
} finally {
    Pop-Location
}

if ($primaryExitCode -gt 1 -or $sourceBuildExitCode -gt 1) {
    Write-Output "Foundation residue scan failed to execute. rg exit codes: primary=$primaryExitCode source-build=$sourceBuildExitCode"
    exit 2
}

if ($primaryExitCode -eq 0 -or $sourceBuildExitCode -eq 0) {
    Write-Output 'Foundation residue check failed: upstream examples, default credentials, or legacy copyright text found.'
    exit 1
}

if ($primaryExitCode -eq 1 -and $sourceBuildExitCode -eq 1) {
    Write-Output 'Foundation residue check passed.'
    exit 0
}

Write-Output "Foundation residue scan returned unexpected exit codes: primary=$primaryExitCode source-build=$sourceBuildExitCode"
exit 2
