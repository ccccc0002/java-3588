param(
    [Parameter(Mandatory = $true)][string]$TaskId,
    [Parameter(Mandatory = $true)][string]$PhaseId,
    [Parameter(Mandatory = $true)][ValidateSet("pending", "in_progress", "completed", "blocked")][string]$Status,
    [string]$Summary = "",
    [ValidateSet("", "pending", "passed", "failed", "skipped")][string]$EdgeTestStatus = "",
    [string]$EdgeTestCommand = "",
    [ValidateSet("", "pending", "synced", "skipped", "failed")][string]$GithubSyncStatus = "",
    [string]$GithubSyncRef = "",
    [string]$Root = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$progressDir = Join-Path $Root "state/progress"
if (-not (Test-Path $progressDir)) {
    New-Item -Path $progressDir -ItemType Directory -Force | Out-Null
}

$statusFile = Join-Path $progressDir "phase2-status.json"

function New-PhaseObject {
    return [pscustomobject]@{
        status = "pending"
        updated_at = ""
        summary = ""
    }
}

function New-DefaultState {
    return [pscustomobject]@{
        version = 1
        task_id = $TaskId
        generated_at = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
        phases = [pscustomobject]@{
            Phase0 = (New-PhaseObject)
            Phase1 = (New-PhaseObject)
            Phase2 = (New-PhaseObject)
            Phase3 = (New-PhaseObject)
            Phase4 = (New-PhaseObject)
            Phase5 = (New-PhaseObject)
            Phase6 = (New-PhaseObject)
            Phase7 = (New-PhaseObject)
            Phase8 = (New-PhaseObject)
            Phase9 = (New-PhaseObject)
            Phase10 = (New-PhaseObject)
            Phase11 = (New-PhaseObject)
        }
        history = @()
    }
}

$state = $null
if (Test-Path $statusFile) {
    try {
        $state = Get-Content -Path $statusFile -Raw | ConvertFrom-Json
    } catch {
        $state = New-DefaultState
    }
} else {
    $state = New-DefaultState
}

if ($null -eq $state.phases) {
    $state | Add-Member -NotePropertyName "phases" -NotePropertyValue ([pscustomobject]@{}) -Force
}

if (-not ($state.phases.PSObject.Properties.Name -contains $PhaseId)) {
    $state.phases | Add-Member -NotePropertyName $PhaseId -NotePropertyValue (New-PhaseObject)
}

$now = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
$state.task_id = $TaskId
$phase = $state.phases.$PhaseId
$phase.status = $Status
$phase.updated_at = $now
$phase.summary = $Summary
if (-not ($phase.PSObject.Properties.Name -contains "quality_gate")) {
    $phase | Add-Member -NotePropertyName "quality_gate" -NotePropertyValue ([pscustomobject]@{}) -Force
}
$edgeState = if ([string]::IsNullOrWhiteSpace($EdgeTestStatus)) { "pending" } else { $EdgeTestStatus }
$syncState = if ([string]::IsNullOrWhiteSpace($GithubSyncStatus)) { "pending" } else { $GithubSyncStatus }
$phase.quality_gate = [pscustomobject]@{
    edge_test_required = $true
    edge_test_status = $edgeState
    edge_test_command = $EdgeTestCommand
    github_sync_required_on_milestone = $true
    github_sync_status = $syncState
    github_sync_ref = $GithubSyncRef
    updated_at = $now
}

$history = @()
if ($null -ne $state.history) {
    $history = @($state.history)
}
$history += [pscustomobject]@{
    timestamp = $now
    phase_id = $PhaseId
    status = $Status
    summary = $Summary
    edge_test_status = $edgeState
    edge_test_command = $EdgeTestCommand
    github_sync_status = $syncState
    github_sync_ref = $GithubSyncRef
}
if ($history.Count -gt 400) {
    $history = @($history | Select-Object -Last 400)
}
$state.history = $history

$state | ConvertTo-Json -Depth 12 | Set-Content -Path $statusFile -Encoding UTF8

$event = "phase_checkpoint"
if ($Status -eq "in_progress") {
    $event = "phase_started"
} elseif ($Status -eq "completed") {
    $event = "phase_completed"
} elseif ($Status -eq "blocked") {
    $event = "blocker_opened"
}

& (Join-Path $PSScriptRoot "Invoke-Hook.ps1") -Event $event -TaskId $TaskId -Stage $PhaseId -Summary $Summary -Root $Root | Out-Null

Write-Output "OK: phase status updated ($PhaseId => $Status)"
