$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceRoots = @(
    'opc-admin/src/main/java'
    'opc-generator/src/main/java'
    'opc-quartz/src/main/java'
) | ForEach-Object { Join-Path $repoRoot $_ } | Where-Object { Test-Path -LiteralPath $_ -PathType Container }

$files = @($sourceRoots | ForEach-Object {
    Get-ChildItem -LiteralPath $_ -Filter '*Controller.java' -File -Recurse
} | Sort-Object FullName)

$errors = New-Object System.Collections.Generic.List[string]
$mappingPattern = '@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b'
$methodPattern = '^\s*(?:public|protected)\s+(?:static\s+)?[\w<>,.?\[\] ]+\s+(?<name>[A-Za-z_]\w*)\s*\('
$publicControllerAllowlist = @(
    'CaptchaController'
    'CommonController'
    'SysLoginController'
    'SysRegisterController'
    'SysIndexController'
)
$publicPathPattern = '/(?:captchaImage|login|register|unlockscreen|common(?:/|$))'
$mutatingMappingPattern = '@(?:PostMapping|PutMapping|DeleteMapping|PatchMapping)\b'
$highRiskNamePattern = '(?i)(add|create|save|update|edit|remove|delete|import|export|grant|auth|reset|force|clean|run|change|synch|upload|avatar|password)'

function Get-RelativePath([string] $Path) {
    return $Path.Substring($repoRoot.Length + 1).Replace('\', '/')
}

foreach ($file in $files) {
    $relativePath = Get-RelativePath $file.FullName
    $lines = [System.IO.File]::ReadAllLines($file.FullName)
    $controllerName = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    for ($mappingLineIndex = 0; $mappingLineIndex -lt $lines.Length; $mappingLineIndex++) {
        $mappingLine = $lines[$mappingLineIndex]
        if ($mappingLine -notmatch $mappingPattern) { continue }
        # Class-level @RequestMapping defines a route prefix, not an endpoint.
        if ($mappingLine -notmatch '^\s+') { continue }

        $methodLineIndex = -1
        for ($candidate = $mappingLineIndex; $candidate -le [Math]::Min($lines.Length - 1, $mappingLineIndex + 25); $candidate++) {
            if ($lines[$candidate] -match $methodPattern) {
                $methodLineIndex = $candidate
                break
            }
        }
        if ($methodLineIndex -lt 0) { continue }

        # Only inspect the annotation/comment block immediately above this mapping.
        # Looking back a fixed number of lines can incorrectly inherit annotations
        # from the preceding endpoint and hide a missing contract.
        $annotationStart = $mappingLineIndex
        for ($candidate = $mappingLineIndex - 1; $candidate -ge 0; $candidate--) {
            $trimmed = $lines[$candidate].Trim()
            if ($trimmed -eq '' -or $trimmed.StartsWith('@') -or $trimmed.StartsWith('//') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('*') -or $trimmed -eq '*/') {
                $annotationStart = $candidate
                continue
            }
            break
        }
        $context = ($lines[$annotationStart..$methodLineIndex] -join "`n")
        $method = [regex]::Match($lines[$methodLineIndex], $methodPattern)
        if (-not $method.Success) {
            $errors.Add("${relativePath}:$($methodLineIndex + 1): 映射方法无法解析，请补充 API 契约检查白名单或调整方法格式")
            continue
        }

        $methodName = $method.Groups['name'].Value
        $mappingText = $mappingLine
        $isPublic = $publicControllerAllowlist -contains $controllerName -or $context -match $publicPathPattern
        $hasPermission = $context -match '@PreAuthorize\s*\('
        $hasOperation = $context -match '@Operation\s*\([^\r\n]*summary\s*=\s*"[^"]*[\u4e00-\u9fff]'
        $hasLog = $context -match '@Log\s*\('
        $isMutating = $mappingText -match $mutatingMappingPattern
        $isHighRisk = ($methodName -match $highRiskNamePattern) -and ($methodName -notmatch '(?i)^(importTemplate|authRole)$')
        $isAuditRequired = (($isMutating -or $isHighRisk) -and ($methodName -notmatch '(?i)^(importTemplate|authRole)$'))

        if (-not $hasOperation) {
            $errors.Add("${relativePath}:$($methodLineIndex + 1) ${methodName} 缺少带中文 summary 的 @Operation")
        }
        if (-not $isPublic -and -not $hasPermission) {
            $errors.Add("${relativePath}:$($methodLineIndex + 1) ${methodName} 缺少服务端 @PreAuthorize 权限校验")
        }
        if ($isAuditRequired -and -not $isPublic -and -not $hasLog) {
            $errors.Add("${relativePath}:$($methodLineIndex + 1) ${methodName} 缺少高风险操作 @Log 审计记录")
        }
    }
}

if ($errors.Count -gt 0) {
    foreach ($errorMessage in $errors) { [Console]::Error.WriteLine($errorMessage) }
    Write-Output "API contract verification failed with $($errors.Count) error(s)."
    exit 1
}

Write-Output "API contract verification passed: checked $($files.Count) controllers."
exit 0
