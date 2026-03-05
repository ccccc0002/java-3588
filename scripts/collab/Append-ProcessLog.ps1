param(
    [string]$TaskId = "global",
    [string]$Stage = "unspecified",
    [string]$Event = "note",
    [string]$Summary = "",
    [string]$Actor = "codex-agent",
    [string]$Root = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$processDir = Join-Path $Root "process"
$logFile = Join-Path $processDir "PROCESS-LOG.md"
$latestFile = Join-Path $processDir "latest-status.json"

if (-not (Test-Path $processDir)) {
    New-Item -Path $processDir -ItemType Directory -Force | Out-Null
}

if (-not (Test-Path $logFile)) {
    @(
        "# Process Log",
        "",
        "| Time | Task | Stage | Event | Actor | Summary |",
        "|---|---|---|---|---|---|"
    ) | Set-Content -Path $logFile -Encoding UTF8
}

$time = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
$safeSummary = ($Summary -replace "\|", "/")
$line = "| $time | $TaskId | $Stage | $Event | $Actor | $safeSummary |"

$maxRetry = 8
$written = $false
for ($i = 1; $i -le $maxRetry; $i++) {
    $mutex = $null
    $acquired = $false
    try {
        $mutex = New-Object System.Threading.Mutex($false, "Global\Java3588_ProcessLog")
        $acquired = $mutex.WaitOne(3000)
        if (-not $acquired) {
            throw "process log mutex acquire timeout"
        }

        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::AppendAllText($logFile, ($line + [Environment]::NewLine), $utf8NoBom)
        $written = $true
        break
    } catch {
        if ($i -eq $maxRetry) {
            throw
        }
        Start-Sleep -Milliseconds 120
    } finally {
        if ($mutex -ne $null) {
            if ($acquired) {
                $mutex.ReleaseMutex() | Out-Null
            }
            $mutex.Dispose()
        }
    }
}

$latest = [ordered]@{
    time = $time
    task_id = $TaskId
    stage = $Stage
    event = $Event
    actor = $Actor
    summary = $Summary
}

$latest | ConvertTo-Json -Depth 5 | Set-Content -Path $latestFile -Encoding UTF8
Write-Output "OK: process log appended"
