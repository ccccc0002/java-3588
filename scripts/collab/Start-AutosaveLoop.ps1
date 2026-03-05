param(
    [int]$IntervalMinutes = 15,
    [int]$CompactEvery = 4,
    [string]$TaskId = "global",
    [string]$Stage = "autosave",
    [int]$MaxIterations = 0,
    [string]$Root = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

if ($IntervalMinutes -lt 1) {
    throw "IntervalMinutes must be >= 1"
}

$i = 0
while ($true) {
    $i++
    & (Join-Path $PSScriptRoot "Invoke-Hook.ps1") -Event "timer_tick" -TaskId $TaskId -Stage $Stage -Summary "periodic autosave tick #$i" -Root $Root | Out-Null

    if (($CompactEvery -gt 0) -and (($i % $CompactEvery) -eq 0)) {
        & (Join-Path $PSScriptRoot "Invoke-Hook.ps1") -Event "compact_tick" -TaskId $TaskId -Stage $Stage -Summary "periodic compact tick #$i" -Root $Root | Out-Null
    }

    if (($MaxIterations -gt 0) -and ($i -ge $MaxIterations)) {
        break
    }

    Start-Sleep -Seconds ($IntervalMinutes * 60)
}

Write-Output "OK: autosave loop finished after $i iterations"

