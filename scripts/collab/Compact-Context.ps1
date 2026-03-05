param(
    [string]$Root = (Get-Location).Path,
    [int]$RecentLogLines = 40,
    [int]$RecentCheckpointFiles = 8,
    [string]$TaskId = "global"
)

$ErrorActionPreference = "Stop"

$processLog = Join-Path $Root "process/PROCESS-LOG.md"
$compactDir = Join-Path $Root "state/compact"
$locksDir = Join-Path $Root "state/locks"
$checkpointRoot = Join-Path $Root "state/checkpoints"
$outFile = Join-Path $compactDir "compact-latest.md"

if (-not (Test-Path $compactDir)) {
    New-Item -Path $compactDir -ItemType Directory -Force | Out-Null
}

$logTail = @()
if (Test-Path $processLog) {
    $logTail = Get-Content -Path $processLog | Select-Object -Last $RecentLogLines
}

$checkpointFiles = @()
if (Test-Path $checkpointRoot) {
    $checkpointFiles = Get-ChildItem -Path $checkpointRoot -Recurse -File -Filter "checkpoint-*.json" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First $RecentCheckpointFiles
}

$checkpointSummary = @()
foreach ($file in $checkpointFiles) {
    try {
        $json = Get-Content -Path $file.FullName -Raw | ConvertFrom-Json
        $checkpointSummary += "- $($json.timestamp) | task=$($json.task_id) | stage=$($json.stage) | event=$($json.event)"
    } catch {
        $checkpointSummary += "- parse failed: $($file.Name)"
    }
}

$lockSummary = @()
if (Test-Path $locksDir) {
    $locks = Get-ChildItem -Path $locksDir -File -Filter "*.lock.json" | Sort-Object Name
    foreach ($lock in $locks) {
        try {
            $lockJson = Get-Content -Path $lock.FullName -Raw | ConvertFrom-Json
            $lockSummary += "- task=$($lockJson.task_id) locked_by=$($lockJson.agent_id) at $($lockJson.timestamp)"
        } catch {
            $lockSummary += "- lock parse failed: $($lock.Name)"
        }
    }
}

if ($lockSummary.Count -eq 0) {
    $lockSummary = @("- none")
}

if ($checkpointSummary.Count -eq 0) {
    $checkpointSummary = @("- none")
}

$header = @(
    "# Compact Context Snapshot",
    "",
    "- generated_at: $((Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))",
    "- task_scope: $TaskId",
    "- intent: keep short, factual state to reduce context drift",
    "",
    "## Active Locks",
    ""
) + $lockSummary + @(
    "",
    "## Recent Checkpoints",
    ""
) + $checkpointSummary + @(
    "",
    "## Recent Process Log Tail",
    ""
) + $logTail

$header | Set-Content -Path $outFile -Encoding UTF8

$logScript = Join-Path $PSScriptRoot "Append-ProcessLog.ps1"
& $logScript -TaskId $TaskId -Stage "compact" -Event "context_compacted" -Summary "compact snapshot updated" -Root $Root | Out-Null

Write-Output "OK: compact context saved to $outFile"

