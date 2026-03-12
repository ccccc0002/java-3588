param(
    [Parameter(Mandatory = $true)][string]$TaskId,
    [Parameter(Mandatory = $true)][string]$PhaseId,
    [Parameter(Mandatory = $true)][string]$Summary,
    [string]$Root = (Get-Location).Path,
    [switch]$CompactNow
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "Invoke-Hook.ps1") -Event "phase_checkpoint" -TaskId $TaskId -Stage $PhaseId -Summary $Summary -Root $Root | Out-Null

if ($CompactNow) {
    & (Join-Path $PSScriptRoot "Invoke-Hook.ps1") -Event "session_compacted" -TaskId $TaskId -Stage $PhaseId -Summary ("compact after checkpoint: " + $Summary) -Root $Root | Out-Null
}

Write-Output "OK: important node saved (phase=$PhaseId)"
