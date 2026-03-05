param(
    [string]$Root = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$cfgFile = Join-Path $Root "hooks/autosave-config.json"
if (-not (Test-Path $cfgFile)) {
    throw "autosave config missing: $cfgFile"
}

$cfg = Get-Content -Path $cfgFile -Raw | ConvertFrom-Json

$taskId = $cfg.task_id
$stage = $cfg.stage
$compactEvery = [int]$cfg.compact_every

$counterFile = Join-Path $Root "state/autosave-counter.txt"
$counter = 0
if (Test-Path $counterFile) {
    $raw = Get-Content -Path $counterFile -Raw
    if ($raw -match "^\d+$") {
        $counter = [int]$raw
    }
}
$counter++
$counter | Set-Content -Path $counterFile -Encoding ASCII

& (Join-Path $PSScriptRoot "Invoke-Hook.ps1") -Event "timer_tick" -TaskId $taskId -Stage $stage -Summary "scheduled autosave tick #$counter" -Root $Root | Out-Null

if (($compactEvery -gt 0) -and (($counter % $compactEvery) -eq 0)) {
    & (Join-Path $PSScriptRoot "Invoke-Hook.ps1") -Event "compact_tick" -TaskId $taskId -Stage $stage -Summary "scheduled compact tick #$counter" -Root $Root | Out-Null
}

Write-Output "OK: autosave tick done (#$counter)"

