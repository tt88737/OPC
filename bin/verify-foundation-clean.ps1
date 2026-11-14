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
    '--glob', '!*.log',
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
    $credentialPatterns = @(
        'sys\.user\.initPassword',
        '(?<![0-9])123456(?![0-9])',
        '\$2a\$10\$7JB720yubVSZvUI0rEqK/\.VqGOZTH\.ulu33dHOiBE8ByOhJIrdAu2'
    )
    $credentialRoots = @('sql', 'frontend/src')
    $credentialRoots += Get-ChildItem -Directory -Filter 'opc-*' |
        ForEach-Object { Join-Path $_.FullName 'src' } |
        Where-Object { Test-Path $_ }
    & rg --line-number --pcre2 --hidden --glob '!**/target/**' --regexp ($credentialPatterns -join '|') @credentialRoots
    $credentialScanExitCode = $LASTEXITCODE
    if ($credentialScanExitCode -eq 0) {
        Write-Output 'Foundation credential check failed: shared password configuration, weak default, or legacy password hash found.'
        exit 1
    }
    if ($credentialScanExitCode -ne 1) {
        Write-Output "Foundation credential scan failed to execute. rg exit code: $credentialScanExitCode"
        exit $credentialScanExitCode
    }
    $placeholderHash = '$2a$10$G/SUvAekkFytyaaBYNX0a.1S1aQmkvX8W5A6LvRG9j1lFjPqvpghG'
    $fixedHashMatches = @(& rg --line-number --hidden --glob '!**/target/**' '\$2[aby]\$[0-9]{2}\$' @credentialRoots)
    $fixedHashScanExitCode = $LASTEXITCODE
    if ($fixedHashScanExitCode -gt 1) {
        Write-Output "Foundation password hash scan failed to execute. rg exit code: $fixedHashScanExitCode"
        exit $fixedHashScanExitCode
    }
    $unexpectedHashMatches = @($fixedHashMatches | Where-Object { $_ -notlike "*$placeholderHash*" })
    if ($unexpectedHashMatches.Count -gt 0) {
        $unexpectedHashMatches | Write-Output
        Write-Output 'Foundation credential check failed: fixed password hash found outside the bootstrap placeholder.'
        exit 1
    }

    $foundationSqlPath = Join-Path $repoRoot 'sql\opc_20260417.sql'
    if (Test-Path -LiteralPath $foundationSqlPath -PathType Leaf) {
        $foundationSql = [System.IO.File]::ReadAllText($foundationSqlPath)
        $demoDataPatterns = @(
            '(?im)^\s*insert\s+into\s+sys_dept\s+values',
            '(?im)^\s*insert\s+into\s+sys_post\s+values',
            '(?im)^\s*insert\s+into\s+sys_role_dept\s+values',
            '(?im)^\s*insert\s+into\s+sys_user_post\s+values',
            '(?im)^\s*insert\s+into\s+sys_notice\s+values',
            '(?im)^\s*insert\s+into\s+sys_user\s+values\(\s*1\s*,\s*(?!null\b)\S',
            '(?i)15888888888|opc@example\.com|admin@example\.com'
        )
        if ($foundationSql -match ($demoDataPatterns -join '|')) {
            Write-Output 'Foundation SQL check failed: organization, post, notice, contact, or admin association demo data found.'
            exit 1
        }
    }

    $bannerPath = Join-Path $repoRoot 'opc-admin\src\main\resources\banner.txt'
    if (Test-Path -LiteralPath $bannerPath -PathType Leaf) {
        $banner = [System.IO.File]::ReadAllText($bannerPath)
        if ($banner -match '(?i)永无\s*BUG|永不宕机|_ooOoo_|o8888888o') {
            Write-Output 'Foundation banner check failed: upstream template residue found in banner.txt.'
            exit 1
        }
    }
    Write-Output 'Foundation residue and credential checks passed.'
    exit 0
}

Write-Output "Foundation residue scan failed to execute. rg exit code: $scanExitCode"
exit $scanExitCode
