$ErrorActionPreference = 'Stop'

$controllerRoot = Join-Path $PSScriptRoot '..\opc-admin\src\main\java\com\opc\web\controller'
$files = @(Get-ChildItem -LiteralPath $controllerRoot -Filter '*Controller.java' -File -Recurse | Sort-Object FullName)
$errors = New-Object System.Collections.Generic.List[string]
$mappingPattern = '^\s*@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b'

foreach ($file in $files) {
    $lines = [System.IO.File]::ReadAllLines($file.FullName)
    $relativePath = $file.FullName.Substring((Resolve-Path (Join-Path $PSScriptRoot '..')).Path.Length).TrimStart('\', '/')
    $tagFound = $false

    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match '^\s*@Tag\b') {
            $tagFound = ($lines[$i] -match 'name\s*=\s*"[^"]*[\u4e00-\u9fff][^"]*"')
            if (-not $tagFound) {
                $errors.Add("${relativePath}:$($i + 1): @Tag 缺少包含中文的 name")
            }
            break
        }
    }
    if (-not $tagFound -and -not ($errors | Where-Object { $_ -like "${relativePath}:* @Tag*" })) {
        $errors.Add("${relativePath}:1: Controller 类缺少带中文 name 的 @Tag")
    }

    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -notmatch $mappingPattern) { continue }

        $next = $i + 1
        while ($next -lt $lines.Length -and [string]::IsNullOrWhiteSpace($lines[$next])) { $next++ }
        if ($next -lt $lines.Length -and $lines[$next] -match '^\s*(?:public\s+)?class\s+') { continue }

        $operationFound = $false
        $j = $i - 1
        while ($j -ge 0) {
            $line = $lines[$j]
            if ([string]::IsNullOrWhiteSpace($line)) { $j--; continue }
            if ($line -match '^\s*(//|/\*|\*|\*/)') { $j--; continue }
            if ($line -match '^\s*@') {
                if ($line -match '^\s*@Operation\b') {
                    $operationFound = ($line -match 'summary\s*=\s*"[^"]*[\u4e00-\u9fff][^"]*"')
                    if (-not $operationFound) {
                        $errors.Add("${relativePath}:$($j + 1): @Operation 缺少包含中文的 summary")
                    }
                }
                $j--
                continue
            }
            break
        }
        if (-not $operationFound -and -not ($errors | Where-Object { $_ -like "${relativePath}:* @Operation*" -and $_ -match ":$($i + 1):" })) {
            $errors.Add("${relativePath}:$($i + 1): 映射注解对应的方法缺少带中文 summary 的 @Operation")
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Swagger 中文注解检查通过，共检查 $($files.Count) 个 Controller。"
exit 0
