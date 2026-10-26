param([switch]$AsJson)

$ErrorActionPreference = 'Stop'
$taskRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$taskIssues = [System.Collections.Generic.List[object]]::new()
$taskMarkdown = [System.Collections.Generic.List[string]]::new()
$taskQueue = [System.Collections.Generic.Queue[string]]::new()
$taskQueue.Enqueue($taskRoot)
$taskSkipDirectories = @('.git', '.gradle', 'build', 'node_modules', 'diagnostics', '.idea', '.agents', '.codex', 'private-data', 'release-output')
$taskAnchorCache = @{}
$taskLocalLinkCount = 0

function Get-TaskRelativePath([string]$TaskPath) {
    $taskBaseUri = [uri]($taskRoot.TrimEnd('\') + '\')
    return [uri]::UnescapeDataString($taskBaseUri.MakeRelativeUri([uri]$TaskPath).ToString())
}

function Add-TaskIssue([string]$TaskFile, [int]$TaskLine, [string]$TaskMessage) {
    $taskIssues.Add([pscustomobject]@{ file = $TaskFile; line = $TaskLine; message = $TaskMessage })
}

# Only read project documents. Do not follow directory links or inspect private data.
while ($taskQueue.Count -gt 0) {
    foreach ($taskEntry in Get-ChildItem -LiteralPath $taskQueue.Dequeue() -Force) {
        if (($taskEntry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { continue }
        if ($taskEntry.PSIsContainer) {
            if ($taskEntry.Name -notin $taskSkipDirectories) { $taskQueue.Enqueue($taskEntry.FullName) }
        } elseif ($taskEntry.Extension -eq '.md') { $taskMarkdown.Add($taskEntry.FullName) }
    }
}

foreach ($taskPath in $taskMarkdown) {
    $taskName = Get-TaskRelativePath $taskPath
    $taskText = [System.IO.File]::ReadAllText($taskPath)
    $taskLines = $taskText -split '\r?\n'
    $taskFence = $null
    $taskTableColumns = 0
    if ($taskText.Contains([char]0xFFFD)) { Add-TaskIssue $taskName 0 'Contains a Unicode replacement character' }
    for ($taskLineIndex = 0; $taskLineIndex -lt $taskLines.Count; $taskLineIndex++) {
        $taskLine = $taskLines[$taskLineIndex]
        $taskLineNumber = $taskLineIndex + 1
        $taskFenceMatch = [regex]::Match($taskLine, '^\s{0,3}(?<fence>`{3,}|~{3,})')
        if ($taskFenceMatch.Success) {
            $taskMarker = $taskFenceMatch.Groups['fence'].Value
            if (-not $taskFence) { $taskFence = $taskMarker }
            elseif ($taskMarker[0] -eq $taskFence[0] -and $taskMarker.Length -ge $taskFence.Length) { $taskFence = $null }
            $taskTableColumns = 0
            continue
        }
        if ($taskFence) { continue }
        if ($taskLine.TrimStart().StartsWith('|')) {
            $taskColumns = [regex]::Matches($taskLine, '(?<!\\)\|').Count
            if ($taskTableColumns -eq 0) { $taskTableColumns = $taskColumns }
            elseif ($taskColumns -ne $taskTableColumns) { Add-TaskIssue $taskName $taskLineNumber 'Table column count differs from preceding row' }
        } else { $taskTableColumns = 0 }

        foreach ($taskMatch in [regex]::Matches($taskLine, '!?\[[^\]\r\n]*\]\((?<target><[^>\r\n]+>|[^)\s]+)(?:\s+[^)]*)?\)')) {
            $taskTarget = $taskMatch.Groups['target'].Value.Trim('<', '>')
            $taskWindowsPath = $taskTarget -match '^[A-Za-z]:[\\/]'
            if (-not $taskWindowsPath -and $taskTarget -match '^[A-Za-z][A-Za-z0-9+.-]*:') { continue }
            $taskLocalLinkCount++
            $taskParts = $taskTarget -split '#', 2
            $taskLinkPath = [uri]::UnescapeDataString(($taskParts[0] -split '\?', 2)[0])
            try {
                $taskDestination = if (-not $taskLinkPath) { $taskPath }
                elseif ([System.IO.Path]::IsPathRooted($taskLinkPath)) { [System.IO.Path]::GetFullPath($taskLinkPath) }
                else { [System.IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $taskPath) $taskLinkPath)) }
            } catch {
                Add-TaskIssue $taskName $taskLineNumber "Invalid local link: $taskTarget"
                continue
            }
            if (-not (Test-Path -LiteralPath $taskDestination)) {
                Add-TaskIssue $taskName $taskLineNumber "Missing local target: $taskTarget"
                continue
            }
            if ($taskParts.Count -lt 2 -or -not $taskParts[1] -or [System.IO.Path]::GetExtension($taskDestination) -ne '.md') { continue }
            if (-not $taskAnchorCache.ContainsKey($taskDestination)) {
                $taskDestinationText = [System.IO.File]::ReadAllText($taskDestination)
                $taskAnchors = [System.Collections.Generic.List[string]]::new()
                foreach ($taskAnchor in [regex]::Matches($taskDestinationText, '<a\s+(?:id|name)="(?<id>[^"]+)"[^>]*>')) {
                    $taskAnchors.Add($taskAnchor.Groups['id'].Value)
                }
                $taskHeadingCounts = @{}
                foreach ($taskHeading in [regex]::Matches($taskDestinationText, '(?m)^#{1,6}\s+(?<heading>.+?)\s*#*\s*$')) {
                    $taskSlug = $taskHeading.Groups['heading'].Value.ToLowerInvariant()
                    $taskSlug = [regex]::Replace($taskSlug, '[^\p{L}\p{Nd}_\-\s]', '')
                    $taskSlug = [regex]::Replace($taskSlug.Trim(), '\s', '-')
                    if ($taskHeadingCounts.ContainsKey($taskSlug)) {
                        $taskHeadingCounts[$taskSlug]++
                        $taskAnchors.Add($taskSlug + '-' + $taskHeadingCounts[$taskSlug])
                    } else { $taskHeadingCounts[$taskSlug] = 0; $taskAnchors.Add($taskSlug) }
                }
                $taskAnchorCache[$taskDestination] = @($taskAnchors)
            }
            $taskFragment = [uri]::UnescapeDataString($taskParts[1])
            if ($taskFragment -notin $taskAnchorCache[$taskDestination]) { Add-TaskIssue $taskName $taskLineNumber "Missing Markdown anchor: $taskTarget" }
        }
    }
    if ($taskFence) { Add-TaskIssue $taskName $taskLines.Count 'Unclosed code fence' }
}

$taskDesign = @{}
foreach ($taskJsonName in @('tokens.json', 'glass-tokens.json', 'motion-tokens.json')) {
    $taskJsonPath = Join-Path $taskRoot "design-system/daily-life/$taskJsonName"
    try { $taskDesign[$taskJsonName] = [System.IO.File]::ReadAllText($taskJsonPath) | ConvertFrom-Json }
    catch { Add-TaskIssue "design-system/daily-life/$taskJsonName" 0 'Missing or invalid design JSON' }
}
if ($taskDesign.Count -eq 3) {
    $taskVisual = $taskDesign['tokens.json']
    $taskGlass = $taskDesign['glass-tokens.json']
    $taskMotion = $taskDesign['motion-tokens.json']
    if ($taskVisual.version -ne $taskGlass.version -or $taskVisual.version -ne $taskMotion.version) { Add-TaskIssue 'design-system/daily-life/' 0 'Design JSON versions do not match' }
    if ($taskVisual.layout -ne 'comfortable-grouped' -or $taskGlass.layout -ne $taskVisual.layout) { Add-TaskIssue 'design-system/daily-life/' 0 'Layout does not match the current comfortable grouped design' }
    if ($taskVisual.scope -ne 'five-destination-personal' -or $taskGlass.navigation.columns -ne 5 -or ($taskGlass.navigation.labels -join '/') -ne '今天/记录/计划/账本/功能箱') { Add-TaskIssue 'design-system/daily-life/' 0 'Five destinations or navigation order do not match the current specification' }
    if ($taskVisual.minimumTouchTargetDp -lt 48 -or $taskVisual.minimumRowHeightDp -lt 72 -or $taskGlass.readability.minimumTaskRowDp -ne $taskVisual.minimumRowHeightDp) { Add-TaskIssue 'design-system/daily-life/' 0 'Touch or row sizes do not match the current specification' }
}

$taskSummary = [pscustomobject]@{
    status = if ($taskIssues.Count -eq 0) { 'passed' } else { 'failed' }
    markdownFiles = $taskMarkdown.Count
    localLinks = $taskLocalLinkCount
    designJsonFiles = $taskDesign.Count
    issueCount = $taskIssues.Count
    issues = @($taskIssues)
    scope = 'Local document structure and design JSON only; no web, Android build, or device validation'
}
if ($AsJson) { $taskSummary | ConvertTo-Json -Depth 5 }
else {
    Write-Output "Documents: $($taskSummary.status); Markdown: $($taskSummary.markdownFiles); local links: $taskLocalLinkCount; design JSON: $($taskSummary.designJsonFiles); issues: $($taskIssues.Count)"
    if ($taskIssues.Count -gt 0) { $taskIssues | Format-Table -AutoSize }
    Write-Output $taskSummary.scope
}
if ($taskIssues.Count -gt 0) { exit 1 }
