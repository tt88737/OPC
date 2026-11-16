$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$requiredFiles = @(
    '.dockerignore',
    '.env.example',
    'compose.yaml',
    'docker/backend.Dockerfile',
    'frontend/.dockerignore',
    'frontend/Dockerfile',
    'frontend/docker/nginx.conf'
)

foreach ($relativePath in $requiredFiles) {
    $path = Join-Path $repoRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required container file is missing: $relativePath"
    }
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($null -eq $docker) {
    throw 'Docker CLI is required to validate container delivery files.'
}

Push-Location $repoRoot
try {
    $env:OPC_DB_PASSWORD = 'container-validation-database-password'
    $env:OPC_MYSQL_ROOT_PASSWORD = 'container-validation-root-password'
    $env:OPC_REDIS_PASSWORD = 'container-validation-redis-password'
    $env:OPC_TOKEN_SECRET = 'container-validation-token-secret-that-is-at-least-sixty-four-characters-long'
    $env:OPC_BOOTSTRAP_ADMIN_PASSWORD = 'container-validation-admin-password'

    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose config failed with exit code $LASTEXITCODE"
    }

    Write-Host 'Container delivery verification passed.'
}
finally {
    Remove-Item Env:OPC_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:OPC_MYSQL_ROOT_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:OPC_REDIS_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:OPC_TOKEN_SECRET -ErrorAction SilentlyContinue
    Remove-Item Env:OPC_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}

