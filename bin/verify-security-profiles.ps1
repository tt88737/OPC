$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$resourceRoot = Join-Path $repoRoot 'opc-admin\src\main\resources'
$errors = New-Object System.Collections.Generic.List[string]

function Read-RequiredFile([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        $errors.Add("Missing configuration file: $Path")
        return ''
    }

    return [System.IO.File]::ReadAllText($Path)
}

function Assert-Matches([string] $Content, [string] $Pattern, [string] $Message) {
    if ($Content -notmatch $Pattern) {
        $errors.Add($Message)
    }
}

function Assert-NotMatches([string] $Content, [string] $Pattern, [string] $Message) {
    if ($Content -match $Pattern) {
        $errors.Add($Message)
    }
}

$application = Read-RequiredFile (Join-Path $resourceRoot 'application.yml')
$datasource = Read-RequiredFile (Join-Path $resourceRoot 'application-datasource.yml')
$development = Read-RequiredFile (Join-Path $resourceRoot 'application-dev.yml')
$production = Read-RequiredFile (Join-Path $resourceRoot 'application-prod.yml')
$druidValidator = Read-RequiredFile (Join-Path $repoRoot 'opc-admin\src\main\java\com\opc\web\core\config\DruidConsoleCredentialsValidator.java')

if (Test-Path -LiteralPath (Join-Path $resourceRoot 'application-druid.yml')) {
    $errors.Add('Legacy application-druid.yml must be renamed to application-datasource.yml.')
}

Assert-Matches $application '(?m)^\s{4}active:\s*\$\{OPC_PROFILE:dev\}\s*$' 'application.yml must default to ${OPC_PROFILE:dev}.'
Assert-Matches $application '(?ms)^\s{4}group:\s*$.*?^\s{6}dev:\s*datasource\s*$' 'The dev profile group must include datasource.'
Assert-Matches $application '(?ms)^\s{4}group:\s*$.*?^\s{6}prod:\s*datasource\s*$' 'The prod profile group must include datasource.'
Assert-Matches $application '(?m)^\s{2}secret:\s*\$\{OPC_TOKEN_SECRET\}\s*$' 'JWT must use OPC_TOKEN_SECRET without a default.'
Assert-NotMatches $application '(?m)^\s*(springdoc|cors):\s*$' 'Profile-specific Swagger and CORS settings must not remain in application.yml.'

Assert-Matches $datasource '(?m)^\s*url:\s*\$\{OPC_DB_URL\}\s*$' 'Database URL must use OPC_DB_URL without a default.'
Assert-Matches $datasource '(?m)^\s*username:\s*\$\{OPC_DB_USERNAME\}\s*$' 'Database username must use OPC_DB_USERNAME without a default.'
Assert-Matches $datasource '(?m)^\s*password:\s*\$\{OPC_DB_PASSWORD\}\s*$' 'Database password must use OPC_DB_PASSWORD without a default.'
Assert-NotMatches $datasource '(?m)^\s*(webStatFilter|statViewServlet):\s*$' 'Druid console settings must not be in application-datasource.yml.'
Assert-NotMatches $datasource 'OPC_DRUID_(USERNAME|PASSWORD):[^\x7d]+' 'Druid credentials must not have fixed defaults.'

Assert-Matches $development '(?ms)^springdoc:\s*$.*?^\s{2}api-docs:\s*$.*?^\s{4}enabled:\s*\$\{OPC_SPRINGDOC_ENABLED:true\}\s*$' 'dev must allow OPC_SPRINGDOC_ENABLED to control API docs.'
Assert-Matches $development '(?ms)^springdoc:\s*$.*?^\s{2}swagger-ui:\s*$.*?^\s{4}enabled:\s*\$\{OPC_SPRINGDOC_ENABLED:true\}\s*$' 'dev must allow OPC_SPRINGDOC_ENABLED to control Swagger UI.'
Assert-Matches $development '(?m)^\s*webStatFilter:\s*$' 'dev must declare Druid webStatFilter.'
Assert-Matches $development '(?m)^\s*statViewServlet:\s*$' 'dev must declare Druid statViewServlet.'
Assert-Matches $development '(?ms)^\s*webStatFilter:\s*$.*?^\s*enabled:\s*\$\{OPC_DRUID_ENABLED:false\}\s*$' 'Druid webStatFilter must be disabled by default and opt-in in dev.'
Assert-Matches $development '(?ms)^\s*statViewServlet:\s*$.*?^\s*enabled:\s*\$\{OPC_DRUID_ENABLED:false\}\s*$' 'Druid statViewServlet must be disabled by default and opt-in in dev.'
Assert-Matches $development '(?m)^\s*login-username:\s*\$\{OPC_DRUID_USERNAME:\}\s*$' 'dev Druid username must come only from OPC_DRUID_USERNAME.'
Assert-Matches $development '(?m)^\s*login-password:\s*\$\{OPC_DRUID_PASSWORD:\}\s*$' 'dev Druid password must come only from OPC_DRUID_PASSWORD.'
Assert-Matches $development '(?m)^\s{2}allowed-origins:\s*\$\{OPC_CORS_ALLOWED_ORIGINS:http://localhost[^\x7d]*\}\s*$' 'dev CORS must have a localhost-only default.'
Assert-Matches $development '(?m)^\s{6}password:\s*\$\{OPC_REDIS_PASSWORD:\}\s*$' 'dev Redis must allow an empty password.'
Assert-NotMatches $development 'OPC_DRUID_(USERNAME|PASSWORD):[^\x7d]+' 'dev Druid credentials must not have fixed defaults.'
Assert-Matches $druidValidator '@ConditionalOnProperty\(name\s*=\s*"spring\.datasource\.druid\.statViewServlet\.enabled",\s*havingValue\s*=\s*"true"\)' 'Druid credential validation must run whenever the console is enabled.'
Assert-Matches $druidValidator '!StringUtils\.hasText\(username\)\s*\|\|\s*!StringUtils\.hasText\(password\)' 'Enabled Druid must reject blank usernames and passwords.'

Assert-Matches $production '(?ms)^springdoc:\s*$.*?^\s{2}api-docs:\s*$.*?^\s{4}enabled:\s*false\s*$' 'prod must disable Springdoc API docs.'
Assert-Matches $production '(?ms)^springdoc:\s*$.*?^\s{2}swagger-ui:\s*$.*?^\s{4}enabled:\s*false\s*$' 'prod must disable Swagger UI.'
Assert-Matches $production '(?ms)^spring:\s*$.*?^\s{2}devtools:\s*$.*?^\s{4}restart:\s*$.*?^\s{6}enabled:\s*false\s*$' 'prod must disable DevTools.'
Assert-Matches $production '(?ms)^\s*webStatFilter:\s*$.*?^\s*enabled:\s*false\s*$' 'prod must disable Druid webStatFilter.'
Assert-Matches $production '(?ms)^\s*statViewServlet:\s*$.*?^\s*enabled:\s*false\s*$' 'prod must disable Druid statViewServlet.'
Assert-Matches $production '(?m)^\s{6}password:\s*\$\{OPC_REDIS_PASSWORD\}\s*$' 'prod Redis must use OPC_REDIS_PASSWORD without a default.'
Assert-Matches $production '(?m)^\s{2}allowed-origins:\s*\$\{OPC_CORS_ALLOWED_ORIGINS\}\s*$' 'prod CORS must use OPC_CORS_ALLOWED_ORIGINS without a default.'
Assert-NotMatches $production 'OPC_(SPRINGDOC|DRUID|DEVTOOLS)_ENABLED' 'prod Swagger, Druid, and DevTools must not be enabled by environment toggles.'

if ($errors.Count -gt 0) {
    foreach ($errorMessage in $errors) {
        [Console]::Error.WriteLine($errorMessage)
    }
    Write-Output "Security profile verification failed with $($errors.Count) error(s)."
    exit 1
}

Write-Output 'Security profile verification passed.'
exit 0
