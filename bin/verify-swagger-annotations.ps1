$ErrorActionPreference = 'Stop'

$controllerRoot = Join-Path $PSScriptRoot '..\opc-admin\src\main\java\com\opc\web\controller'
$files = @(Get-ChildItem -LiteralPath $controllerRoot -Filter '*Controller.java' -File -Recurse | Sort-Object FullName)
$errors = New-Object System.Collections.Generic.List[string]
$mappingPattern = '^\s*@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b'
$chinesePattern = '[\u4e00-\u9fff]'

function Get-AnnotationRecords([string[]]$SourceLines) {
    $records = New-Object System.Collections.Generic.List[object]
    for ($lineIndex = 0; $lineIndex -lt $SourceLines.Length; $lineIndex++) {
        $annotationMatch = [regex]::Match($SourceLines[$lineIndex], '^\s*@(?<name>\w+)\b')
        if (-not $annotationMatch.Success) { continue }

        $start = $lineIndex
        $text = $SourceLines[$lineIndex]
        $depth = 0
        $opened = $false
        for ($charIndex = 0; $charIndex -lt $text.Length; $charIndex++) {
            if ($text[$charIndex] -eq '(') { $depth++; $opened = $true }
            elseif ($text[$charIndex] -eq ')') { $depth-- }
        }
        while ($opened -and $depth -gt 0 -and ($lineIndex + 1) -lt $SourceLines.Length) {
            $lineIndex++
            $text += "`n" + $SourceLines[$lineIndex]
            for ($charIndex = 0; $charIndex -lt $SourceLines[$lineIndex].Length; $charIndex++) {
                if ($SourceLines[$lineIndex][$charIndex] -eq '(') { $depth++ }
                elseif ($SourceLines[$lineIndex][$charIndex] -eq ')') { $depth-- }
            }
        }
        $records.Add([pscustomobject]@{ Name = $annotationMatch.Groups['name'].Value; Start = $start; End = $lineIndex; Text = $text })
    }
    return $records.ToArray()
}

function Test-OnlyTrivia([string[]]$SourceLines, [int]$First, [int]$Last) {
    for ($index = $First; $index -le $Last; $index++) {
        if ($SourceLines[$index] -match '^\s*@' -or
            $SourceLines[$index] -match '^\s*(//|/\*|\*|\*/)?\s*$') { continue }
        return $false
    }
    return $true
}

foreach ($file in $files) {
    $lines = [System.IO.File]::ReadAllLines($file.FullName)
    $relativePath = $file.FullName.Substring((Resolve-Path (Join-Path $PSScriptRoot '..')).Path.Length).TrimStart('\', '/')
    $annotations = @(Get-AnnotationRecords $lines)

    $classIndices = @()
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match '^\s*(?:(?:public|abstract|final)\s+)*class\s+') { $classIndices += $i }
    }
    $classTagFound = $false
    foreach ($classIndex in $classIndices) {
        $preceding = @($annotations | Where-Object { $_.Name -eq 'Tag' -and $_.End -lt $classIndex } | Select-Object -Last 1)
        if ($preceding.Count -eq 0) { continue }
        $tag = $preceding[0]
        if (-not (Test-OnlyTrivia $lines ($tag.End + 1) ($classIndex - 1))) { continue }
        $classTagFound = $true
        if ($tag.Text -notmatch ('name\s*=\s*"[^"]*' + $chinesePattern + '[^"]*"')) {
            $errors.Add("${relativePath}:$($tag.Start + 1): @Tag 缺少包含中文的 name")
        }
        break
    }
    if (-not $classTagFound) {
        $errors.Add("${relativePath}:1: Controller 类缺少带中文 name 的类级 @Tag")
    }

    foreach ($mapping in $annotations | Where-Object { $_.Name -match '^(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)$' }) {
        $nextDeclaration = $lines.Length
        $declarationType = ''
        for ($i = $mapping.End + 1; $i -lt $lines.Length; $i++) {
            if ($lines[$i] -match '^\s*(?:(?:public|abstract|final)\s+)*class\s+') {
                $nextDeclaration = $i
                $declarationType = 'class'
                break
            }
            if ($lines[$i] -match '^\s*(?:public|protected|private)\s+.+\(') {
                $nextDeclaration = $i
                $declarationType = 'method'
                break
            }
        }
        if ($declarationType -ne 'method') { continue }

        # Include annotations contiguous on either side of the mapping annotation.
        $blockAnnotations = @($annotations | Where-Object { $_.Start -lt $nextDeclaration })
        $mappingPosition = [array]::IndexOf($blockAnnotations, $mapping)
        $first = $mappingPosition
        while ($first -gt 0 -and (Test-OnlyTrivia $lines ($blockAnnotations[$first - 1].End + 1) ($blockAnnotations[$first].Start - 1))) { $first-- }
        $last = $mappingPosition
        while (($last + 1) -lt $blockAnnotations.Count -and (Test-OnlyTrivia $lines ($blockAnnotations[$last].End + 1) ($blockAnnotations[$last + 1].Start - 1))) { $last++ }
        $operation = $null
        for ($a = $first; $a -le $last; $a++) {
            if ($blockAnnotations[$a].Name -eq 'Operation') { $operation = $blockAnnotations[$a]; break }
        }
        if ($null -eq $operation) {
            $errors.Add("${relativePath}:$($mapping.Start + 1): 映射注解对应的方法缺少带中文 summary 的 @Operation")
        } elseif ($operation.Text -notmatch ('summary\s*=\s*"[^"]*' + $chinesePattern + '[^"]*"')) {
            $errors.Add("${relativePath}:$($operation.Start + 1): @Operation 缺少包含中文的 summary")
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Output $_ }
    exit 1
}

Write-Output "Swagger 中文注解检查通过，共检查 $($files.Count) 个 Controller。"
exit 0
