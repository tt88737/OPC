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
    '--glob', '!worktrees/**',
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
    '--regexp', ($patterns -join '|'),
    '.'
)

Push-Location $repoRoot
try {
    & rg @arguments
    $scanExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($scanExitCode -eq 0) {
    Write-Output 'Foundation residue check failed: upstream examples, default credentials, or legacy copyright text found.'
    exit 1
}

if ($scanExitCode -eq 1) {
    Write-Output 'Foundation residue check passed.'
    exit 0
}

Write-Output "Foundation residue scan failed to execute. rg exit code: $scanExitCode"
exit $scanExitCode
