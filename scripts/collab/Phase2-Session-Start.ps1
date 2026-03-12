param(
    [string]$TaskId = "PHASE2-EXEC",
    [string]$PhaseId = "Phase1",
    [string]$Summary = "phase2 session start",
    [string]$Root = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$localProfile = Join-Path $Root "state/local/rk3588-edge.local.json"
if (-not (Test-Path $localProfile)) {
    Write-Warning "local edge profile not found: $localProfile"
    Write-Warning "create it first to avoid losing SSH context."
}

& (Join-Path $PSScriptRoot "Update-PhaseStatus.ps1") -TaskId $TaskId -PhaseId $PhaseId -Status in_progress -Summary $Summary -Root $Root | Out-Null
& (Join-Path $PSScriptRoot "Save-ImportantNode.ps1") -TaskId $TaskId -PhaseId $PhaseId -Summary ("session started: " + $Summary) -Root $Root | Out-Null

Write-Output "OK: phase2 session initialized (task=$TaskId phase=$PhaseId)"
