param(
    [string]$TaskId = "PHASE2-EXEC",
    [string]$PhaseId = "Phase1",
    [ValidateSet("in_progress", "completed", "blocked")][string]$FinalStatus = "in_progress",
    [string]$Summary = "phase2 session end",
    [string]$Root = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "Save-ImportantNode.ps1") -TaskId $TaskId -PhaseId $PhaseId -Summary ("session ending: " + $Summary) -Root $Root | Out-Null
& (Join-Path $PSScriptRoot "Compact-Session.ps1") -TaskId $TaskId -Stage $PhaseId -Summary ("session end compact: " + $Summary) -Root $Root | Out-Null
& (Join-Path $PSScriptRoot "Update-PhaseStatus.ps1") -TaskId $TaskId -PhaseId $PhaseId -Status $FinalStatus -Summary $Summary -Root $Root | Out-Null

Write-Output "OK: phase2 session closed (phase=$PhaseId status=$FinalStatus)"
