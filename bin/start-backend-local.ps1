$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$environmentFile = Join-Path $repoRoot 'opc-local.env.ps1'
$jarPath = Join-Path $repoRoot 'opc-admin/target/opc-admin.jar'

function Find-JavaExecutable {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        return $javaCommand.Source
    }

    $javaHomes = @(
        $env:JAVA_HOME,
        [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'),
        [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($javaHome in $javaHomes) {
        $candidate = Join-Path $javaHome 'bin/java.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            $env:JAVA_HOME = $javaHome
            $env:Path = "$javaHome\bin;$env:Path"
            return $candidate
        }
    }

    throw 'Java was not found. Install Java 21 or configure JAVA_HOME to the JDK installation directory.'
}

if (-not (Test-Path -LiteralPath $environmentFile -PathType Leaf)) {
    throw 'Missing opc-local.env.ps1. Copy opc-local.env.example.ps1, fill in local values, and retry.'
}
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw 'Missing opc-admin/target/opc-admin.jar. Build the backend before starting it.'
}

[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
if ($IsWindows) {
    & chcp.com 65001 | Out-Null
}

. $environmentFile
$javaExecutable = Find-JavaExecutable
$env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS, '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8') |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ' '

Write-Host "Using Java: $javaExecutable"

Push-Location $repoRoot
try {
    & $javaExecutable -jar $jarPath
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

