$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceRoots = New-Object System.Collections.Generic.List[string]
$pending = New-Object System.Collections.Generic.Stack[string]
$pending.Push($repoRoot)
while ($pending.Count -gt 0) {
    $directory = $pending.Pop()
    $sourceRoot = Join-Path $directory 'src\main\java'
    if (Test-Path -LiteralPath $sourceRoot -PathType Container) { $sourceRoots.Add($sourceRoot) }
    foreach ($child in Get-ChildItem -LiteralPath $directory -Directory) {
        if ($child.Name -in @('.git', '.worktrees', 'worktrees', 'target', 'node_modules', 'src')) { continue }
        $pending.Push($child.FullName)
    }
}
$files = @($sourceRoots | Where-Object { Test-Path -LiteralPath $_ -PathType Container } | ForEach-Object {
    Get-ChildItem -LiteralPath $_ -Filter '*Controller.java' -File -Recurse
} | Sort-Object FullName)
$controllerCount = 0
$errors = New-Object System.Collections.Generic.List[string]
$mappingPattern = '^\s*@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b'
$chinesePattern = '[\u4e00-\u9fff]'

function Get-CommentMaskedLines([string[]]$SourceLines) {
    $maskedLines = New-Object System.Collections.Generic.List[string]
    $inBlockComment = $false
    foreach ($sourceLine in $SourceLines) {
        $builder = New-Object System.Text.StringBuilder
        $quote = [char]0
        $index = 0
        while ($index -lt $sourceLine.Length) {
            $character = $sourceLine[$index]
            if ($inBlockComment) {
                [void]$builder.Append(' ')
                if ($character -eq '*' -and ($index + 1) -lt $sourceLine.Length -and $sourceLine[$index + 1] -eq '/') {
                    [void]$builder.Append(' ')
                    $index++
                    $inBlockComment = $false
                }
            } elseif ($quote -ne [char]0) {
                [void]$builder.Append($character)
                if ($character -eq '\' -and ($index + 1) -lt $sourceLine.Length) {
                    $index++
                    [void]$builder.Append($sourceLine[$index])
                } elseif ($character -eq $quote) {
                    $quote = [char]0
                }
            } elseif (($character -eq '"') -or ($character -eq "'")) {
                $quote = $character
                [void]$builder.Append($character)
            } elseif ($character -eq '/' -and ($index + 1) -lt $sourceLine.Length -and $sourceLine[$index + 1] -eq '*') {
                [void]$builder.Append('  ')
                $index++
                $inBlockComment = $true
            } elseif ($character -eq '/' -and ($index + 1) -lt $sourceLine.Length -and $sourceLine[$index + 1] -eq '/') {
                while ($index -lt $sourceLine.Length) { [void]$builder.Append(' '); $index++ }
                break
            } else {
                [void]$builder.Append($character)
            }
            $index++
        }
        $maskedLines.Add($builder.ToString())
    }
    return [pscustomobject]@{ Lines = $maskedLines.ToArray() }
}

function Get-ParenthesisDelta([string]$SourceLine) {
    $delta = 0
    $quote = [char]0
    $index = 0
    while ($index -lt $SourceLine.Length) {
        $character = $SourceLine[$index]
        if ($quote -ne [char]0) {
            if ($character -eq '\' -and ($index + 1) -lt $SourceLine.Length) { $index++ }
            elseif ($character -eq $quote) { $quote = [char]0 }
        } elseif (($character -eq '"') -or ($character -eq "'")) { $quote = $character }
        elseif ($character -eq '(') { $delta++ }
        elseif ($character -eq ')') { $delta-- }
        $index++
    }
    return $delta
}

function Get-AnnotationRecords([string[]]$SourceLines, [string[]]$MaskedLines) {
    $records = New-Object System.Collections.Generic.List[object]
    for ($lineIndex = 0; $lineIndex -lt $MaskedLines.Length; $lineIndex++) {
        $annotationMatch = [regex]::Match($MaskedLines[$lineIndex], '^\s*@(?<name>\w+)\b')
        if (-not $annotationMatch.Success) { continue }

        $start = $lineIndex
        $text = $SourceLines[$lineIndex]
        $depth = Get-ParenthesisDelta $MaskedLines[$lineIndex]
        $opened = $MaskedLines[$lineIndex].Contains('(')
        while ($opened -and $depth -gt 0 -and ($lineIndex + 1) -lt $SourceLines.Length) {
            $lineIndex++
            $text += "`n" + $SourceLines[$lineIndex]
            $depth += Get-ParenthesisDelta $MaskedLines[$lineIndex]
        }
        $maskedText = ($MaskedLines[$start..$lineIndex] -join "`n")
        $records.Add([pscustomobject]@{ Name = $annotationMatch.Groups['name'].Value; Start = $start; End = $lineIndex; Text = $text; MaskedText = $maskedText })
    }
    return $records.ToArray()
}

function Test-AnnotationGap([string[]]$SourceLines, [object[]]$Records, [int]$First, [int]$Last) {
    $inBlockComment = $false
    for ($index = $First; $index -le $Last; $index++) {
        $record = $null
        foreach ($candidate in $Records) {
            if ($candidate.Start -le $index -and $candidate.End -ge $index) { $record = $candidate; break }
        }
        if ($null -ne $record) { $index = $record.End; continue }

        $line = $SourceLines[$index].Trim()
        if ($inBlockComment) {
            if ($line.Contains('*/')) { $inBlockComment = $false }
            continue
        }
        if ($line -match '^/\*') {
            if (-not $line.Contains('*/')) { $inBlockComment = $true }
            continue
        }
        if ($line -match '^//' -or $line -match '^\*' -or $line -match '^\*/' -or [string]::IsNullOrWhiteSpace($line)) { continue }
        return $false
    }
    return $true
}

foreach ($file in $files) {
    $lines = [System.IO.File]::ReadAllLines($file.FullName)
    $maskedLines = (Get-CommentMaskedLines $lines).Lines
    $relativePath = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
    $annotations = @(Get-AnnotationRecords $lines $maskedLines)

    $controllerClassName = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    $classIndices = @()
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($maskedLines[$i] -match ('^\s*(?:(?:public|abstract|final)\s+)*class\s+' + [regex]::Escape($controllerClassName) + '\b')) { $classIndices += $i }
    }
    $webControllerFound = $false
    foreach ($classIndex in $classIndices) {
        foreach ($webAnnotation in $annotations | Where-Object { $_.Name -in @('RestController', 'Controller') -and $_.End -lt $classIndex }) {
            if (Test-AnnotationGap $maskedLines $annotations ($webAnnotation.End + 1) ($classIndex - 1)) {
                $webControllerFound = $true
                break
            }
        }
        if ($webControllerFound) { break }
    }
    if (-not $webControllerFound) { continue }
    $controllerCount++
    $classTagFound = $false
    foreach ($classIndex in $classIndices) {
        $preceding = @($annotations | Where-Object { $_.Name -eq 'Tag' -and $_.End -lt $classIndex } | Select-Object -Last 1)
        if ($preceding.Count -eq 0) { continue }
        $tag = $preceding[0]
        if (-not (Test-AnnotationGap $maskedLines $annotations ($tag.End + 1) ($classIndex - 1))) { continue }
        $classTagFound = $true
        if ($tag.MaskedText -notmatch ('name\s*=\s*"[^"]*' + $chinesePattern + '[^"]*"')) {
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
        $signature = New-Object System.Collections.Generic.List[string]
        for ($i = $mapping.End + 1; $i -lt $maskedLines.Length; $i++) {
            if ($maskedLines[$i] -match '^\s*(?:(?:public|abstract|final)\s+)*class\s+(?<name>[\w$]+)\b') {
                $nextDeclaration = $i
                if ($matches['name'] -eq $controllerClassName) { $declarationType = 'class' }
                else { $declarationType = 'unowned-class' }
                break
            }
            $coveredByAnnotation = $false
            foreach ($candidate in $annotations) {
                if ($candidate.Start -le $i -and $candidate.End -ge $i) { $i = $candidate.End; $coveredByAnnotation = $true; break }
            }
            if ($coveredByAnnotation -or [string]::IsNullOrWhiteSpace($maskedLines[$i])) { continue }

            $signature.Add($maskedLines[$i].Trim())
            $joinedSignature = $signature -join ' '
            if ($joinedSignature -match '^\s*(?:(?:public|protected|private|static|final|synchronized|abstract|native|default)\s+)*(?:<[^>]+>\s*)?[\w$][\w$<>,.?\[\] ]*\s+[\w$]+\s*\(') {
                $nextDeclaration = $i
                $declarationType = 'method'
                break
            }
            if ($maskedLines[$i].Contains('{') -or $maskedLines[$i].Contains(';') -or $maskedLines[$i].Contains('}')) { break }
        }
        if ($declarationType -eq 'class') { continue }
        if ($declarationType -ne 'method') {
            $errors.Add("${relativePath}:$($mapping.Start + 1): 无法确定映射注解所属的 Controller 类或方法")
            continue
        }

        # Include annotations contiguous on either side of the mapping annotation.
        $blockAnnotations = @($annotations | Where-Object { $_.Start -lt $nextDeclaration })
        $mappingPosition = [array]::IndexOf($blockAnnotations, $mapping)
        $first = $mappingPosition
        while ($first -gt 0 -and (Test-AnnotationGap $maskedLines $annotations ($blockAnnotations[$first - 1].End + 1) ($blockAnnotations[$first].Start - 1))) { $first-- }
        $last = $mappingPosition
        while (($last + 1) -lt $blockAnnotations.Count -and (Test-AnnotationGap $maskedLines $annotations ($blockAnnotations[$last].End + 1) ($blockAnnotations[$last + 1].Start - 1))) { $last++ }
        $operation = $null
        for ($a = $first; $a -le $last; $a++) {
            if ($blockAnnotations[$a].Name -eq 'Operation') { $operation = $blockAnnotations[$a]; break }
        }
        if ($null -eq $operation) {
            $errors.Add("${relativePath}:$($mapping.Start + 1): 映射注解对应的方法缺少带中文 summary 的 @Operation")
        } elseif ($operation.MaskedText -notmatch ('summary\s*=\s*"[^"]*' + $chinesePattern + '[^"]*"')) {
            $errors.Add("${relativePath}:$($operation.Start + 1): @Operation 缺少包含中文的 summary")
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Output $_ }
    Write-Output "Swagger 中文注解检查失败，共检查 $controllerCount 个 Controller，发现 $($errors.Count) 处缺失。"
    exit 1
}

Write-Output "Swagger 中文注解检查通过，共检查 $controllerCount 个 Controller。"
exit 0
