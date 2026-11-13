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

function Test-CommonResponseModels([string]$Root, [System.Collections.Generic.List[string]]$Errors) {
    $models = @(
        @{ RelativePath = 'opc-common/src/main/java/com/opc/common/core/domain/AjaxResult.java'; Type = 'AjaxResult'; Fields = @() },
        @{ RelativePath = 'opc-common/src/main/java/com/opc/common/core/page/TableDataInfo.java'; Type = 'TableDataInfo'; Fields = @('total', 'rows', 'code', 'msg') },
        @{ RelativePath = 'opc-common/src/main/java/com/opc/common/core/domain/R.java'; Type = 'R'; Fields = @('code', 'msg', 'data') }
    )
    foreach ($model in $models) {
        $path = Join-Path $Root $model.RelativePath
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            $Errors.Add("$($model.RelativePath):1: 缺少公共响应模型源文件")
            continue
        }
        $lines = [System.IO.File]::ReadAllLines($path)
        $source = $lines -join "`n"
        $classSchema = [regex]::Match($source, '@Schema\s*\(\s*description\s*=\s*"(?<description>[^"]*)"\s*\)\s*\r?\n\s*(?:public\s+)?class\s+' + [regex]::Escape($model.Type) + '\b')
        if (-not $classSchema.Success) {
            $Errors.Add("$($model.RelativePath):1: $($model.Type) 缺少类级 @Schema(description=...)")
        } elseif ($classSchema.Groups['description'].Value -notmatch $chinesePattern) {
            $Errors.Add("$($model.RelativePath):1: $($model.Type) 类级 @Schema description 缺少中文")
        }
        foreach ($field in $model.Fields) {
            $fieldSchema = [regex]::Match($source, '@Schema\s*\(\s*description\s*=\s*"(?<description>[^"]*)"\s*\)\s*\r?\n\s*(?:private|protected|public)\s+[^;=]+\s+' + [regex]::Escape($field) + '\s*(?:=|;)')
            if (-not $fieldSchema.Success) {
                $Errors.Add("$($model.RelativePath):1: 字段 $field 缺少带中文 description 的 @Schema")
            } elseif ($fieldSchema.Groups['description'].Value -notmatch $chinesePattern) {
                $Errors.Add("$($model.RelativePath):1: 字段 $field 的 @Schema description 缺少中文")
            }
        }
        if ($model.Type -eq 'AjaxResult' -and $source -match '(?m)^\s*@Schema\b[^\r\n]*(?:\r?\n\s*)+(?:public|protected|private)\s+static\s+final\b') {
            $Errors.Add("$($model.RelativePath):1: AjaxResult 不应把 HashMap 静态常量声明为 JSON @Schema 字段")
        }
    }
}

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

function Get-MethodParameterTexts([string[]]$MaskedLines, [int]$MethodStart) {
    $text = New-Object System.Text.StringBuilder
    $started = $false
    $depth = 0
    $angleDepth = 0
    $bracketDepth = 0
    $quote = [char]0
    for ($lineIndex = $MethodStart; $lineIndex -lt $MaskedLines.Length; $lineIndex++) {
        $line = $MaskedLines[$lineIndex]
        for ($index = 0; $index -lt $line.Length; $index++) {
            $character = $line[$index]
            if ($quote -ne [char]0) {
                [void]$text.Append($character)
                if ($character -eq '\' -and ($index + 1) -lt $line.Length) {
                    $index++
                    [void]$text.Append($line[$index])
                } elseif ($character -eq $quote) { $quote = [char]0 }
                continue
            }
            if (($character -eq '"') -or ($character -eq "'")) {
                $quote = $character
                if ($started) { [void]$text.Append($character) }
                continue
            }
            if (-not $started) {
                if ($character -eq '(') {
                    $started = $true
                    $depth = 1
                }
                continue
            }
            if ($character -eq '(') { $depth++ }
            elseif ($character -eq ')') {
                $depth--
                if ($depth -eq 0) {
                    $parameters = New-Object System.Collections.Generic.List[string]
                    $parameterText = $text.ToString()
                    $start = 0
                    $parameterAngle = 0
                    $parameterParenthesis = 0
                    $parameterBracket = 0
                    for ($parameterIndex = 0; $parameterIndex -lt $parameterText.Length; $parameterIndex++) {
                        $parameterCharacter = $parameterText[$parameterIndex]
                        if ($parameterCharacter -eq '<') { $parameterAngle++ }
                        elseif ($parameterCharacter -eq '>' -and $parameterAngle -gt 0) { $parameterAngle-- }
                        elseif ($parameterCharacter -eq '(') { $parameterParenthesis++ }
                        elseif ($parameterCharacter -eq ')' -and $parameterParenthesis -gt 0) { $parameterParenthesis-- }
                        elseif ($parameterCharacter -eq '[') { $parameterBracket++ }
                        elseif ($parameterCharacter -eq ']' -and $parameterBracket -gt 0) { $parameterBracket-- }
                        elseif ($parameterCharacter -eq ',' -and $parameterAngle -eq 0 -and $parameterParenthesis -eq 0 -and $parameterBracket -eq 0) {
                            $candidate = $parameterText.Substring($start, $parameterIndex - $start).Trim()
                            if ($candidate.Length -gt 0) { $parameters.Add($candidate) }
                            $start = $parameterIndex + 1
                        }
                    }
                    $candidate = $parameterText.Substring($start).Trim()
                    if ($candidate.Length -gt 0) { $parameters.Add($candidate) }
                    return $parameters.ToArray()
                }
            } elseif ($character -eq '<') { $angleDepth++ }
            elseif ($character -eq '>' -and $angleDepth -gt 0) { $angleDepth-- }
            elseif ($character -eq '[') { $bracketDepth++ }
            elseif ($character -eq ']' -and $bracketDepth -gt 0) { $bracketDepth-- }
            [void]$text.Append($character)
        }
        if ($started) { [void]$text.Append("`n") }
    }
    return @()
}

function Test-SpringInternalParameter([string]$ParameterText) {
    if ($ParameterText -match '@(?:AuthenticationPrincipal|CurrentSecurityContext|SessionAttribute|RequestAttribute)\b') { return $true }
    $withoutAnnotations = [regex]::Replace($ParameterText, '@[\w$.]+(?:\s*\([^)]*\))?', ' ')
    $normalized = [regex]::Replace($withoutAnnotations, '\s+', ' ').Trim()
    $typeMatch = [regex]::Match($normalized, '(?<type>[\w$.<>?,\[\] ]+)\s+[\w$]+$')
    if (-not $typeMatch.Success) { return $false }
    $type = $typeMatch.Groups['type'].Value.Trim()
    $hasExplicitRequestBinding = $ParameterText -match '@(?:RequestBody|RequestParam|PathVariable|RequestHeader|RequestPart|CookieValue|ModelAttribute)\b'
    if (-not $hasExplicitRequestBinding -and $type -match '(?:^|[<, ])Map$') { return $true }
    return $type -match '(?:^|[<, ])(?:jakarta\.servlet\.)?http\.HttpServlet(?:Request|Response)$' -or
        $type -match '(?:^|[<, ])(?:HttpServletRequest|HttpServletResponse|ServletRequest|ServletResponse|HttpSession|WebRequest|NativeWebRequest|ServletWebRequest|Principal|Authentication|SecurityContext|Model|ModelMap|RedirectAttributes|BindingResult|Errors|SessionStatus|UriComponentsBuilder|Locale|TimeZone|ZoneId|HttpHeaders|PushBuilder|ServletContext|WebDataBinder|InputStream|OutputStream|Reader|Writer|HttpMethod)$'
}

function Test-ChineseOpenApiParameter([string]$ParameterText) {
    $annotation = [regex]::Match($ParameterText, '@Parameter\s*\((?<arguments>[^)]*)\)')
    if (-not $annotation.Success) { return $false }
    return $annotation.Groups['arguments'].Value -match 'description\s*=\s*"[^"]*' + $chinesePattern + '[^"]*"'
}

function Test-ChineseOpenApiRequestBody([string]$ParameterText) {
    $annotation = [regex]::Match($ParameterText, '@(?:io\.swagger\.v3\.oas\.annotations\.parameters\.)?RequestBody\s*\((?<arguments>[^)]*)\)')
    if (-not $annotation.Success) { return $false }
    return $annotation.Groups['arguments'].Value -match 'description\s*=\s*"[^"]*' + $chinesePattern + '[^"]*"'
}

Test-CommonResponseModels $repoRoot $errors

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
        $methodStart = -1
        $publicInstanceMethod = $false
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
                $methodStart = $i
                $publicInstanceMethod = $joinedSignature -match '^\s*public\s+(?!static\b)'
                break
            }
            if ($maskedLines[$i].Contains('{') -or $maskedLines[$i].Contains(';') -or $maskedLines[$i].Contains('}')) { break }
        }
        if ($declarationType -eq 'class') { continue }
        if ($declarationType -ne 'method') {
            $errors.Add("${relativePath}:$($mapping.Start + 1): 无法确定映射注解所属的 Controller 类或方法")
            continue
        }

        if ($publicInstanceMethod) {
            foreach ($parameter in @(Get-MethodParameterTexts $maskedLines $methodStart)) {
                if (Test-SpringInternalParameter $parameter) { continue }
                $isRequestBody = $parameter -match '@RequestBody\b'
                if ($isRequestBody) {
                    if (-not (Test-ChineseOpenApiRequestBody $parameter)) {
                        $errors.Add("${relativePath}:$($mapping.Start + 1): @RequestBody 参数缺少带中文 description 的 OpenAPI RequestBody")
                    }
                    continue
                }
                if (-not ($parameter -match '@Parameter\b')) {
                    $errors.Add("${relativePath}:$($mapping.Start + 1): 映射方法存在未覆盖的请求参数：$($parameter.Trim())（请添加带中文 description 的 @Parameter）")
                } elseif (-not (Test-ChineseOpenApiParameter $parameter)) {
                    $errors.Add("${relativePath}:$($mapping.Start + 1): @Parameter 缺少包含中文的 description")
                }
            }
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
