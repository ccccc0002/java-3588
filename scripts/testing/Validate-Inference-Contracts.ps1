param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$CameraId = 1,
    [long]$ModelId = 1,
    [long]$AlgorithmId = 1,
    [string]$Source = "test://frame",
    [string]$ExpectedBackendType = "",
    [string]$ExpectedOverrideSource = "",
    [string]$Cookie = "",
    [int]$TimeoutSec = 10
)

$ErrorActionPreference = "Stop"

function Get-PropValue {
    param(
        [Parameter(Mandatory = $false)]$Obj,
        [Parameter(Mandatory = $true)][string]$Name
    )
    if ($null -eq $Obj) {
        return $null
    }
    $prop = $Obj.PSObject.Properties[$Name]
    if ($null -eq $prop) {
        return $null
    }
    return $prop.Value
}

function Invoke-ApiGet {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )
    $uri = ($BaseUrl.TrimEnd("/") + $Path)
    $headers = @{}
    if ($Cookie -ne "") {
        $headers["Cookie"] = $Cookie
    }
    try {
        return Invoke-RestMethod -Uri $uri -Method Get -Headers $headers -TimeoutSec $TimeoutSec
    } catch {
        return [pscustomobject]@{
            code = -1
            msg = $_.Exception.Message
            data = $null
        }
    }
}

function Invoke-ApiPostJson {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$BodyObj
    )
    $uri = ($BaseUrl.TrimEnd("/") + $Path)
    $headers = @{}
    if ($Cookie -ne "") {
        $headers["Cookie"] = $Cookie
    }
    try {
        $json = $BodyObj | ConvertTo-Json -Depth 10
        return Invoke-RestMethod -Uri $uri -Method Post -ContentType "application/json" -Body $json -Headers $headers -TimeoutSec $TimeoutSec
    } catch {
        return [pscustomobject]@{
            code = -1
            msg = $_.Exception.Message
            data = $null
        }
    }
}

function New-CheckResult {
    param(
        [string]$Api,
        [bool]$Passed,
        [string]$Detail
    )
    return [pscustomobject]@{
        api = $Api
        passed = $Passed
        detail = $Detail
    }
}

function Is-ExpectedBackend {
    param(
        [string]$ActualBackend
    )
    if ([string]::IsNullOrWhiteSpace($ExpectedBackendType)) {
        return $true
    }
    if ([string]::IsNullOrWhiteSpace($ActualBackend)) {
        return $false
    }
    return $ActualBackend.Trim().ToLowerInvariant() -eq $ExpectedBackendType.Trim().ToLowerInvariant()
}

function Is-ExpectedOverrideSource {
    param(
        [string]$ActualSource
    )
    if ([string]::IsNullOrWhiteSpace($ExpectedOverrideSource)) {
        return $true
    }
    if ([string]::IsNullOrWhiteSpace($ActualSource)) {
        return $false
    }
    return $ActualSource.Trim().ToLowerInvariant() -eq $ExpectedOverrideSource.Trim().ToLowerInvariant()
}

$checks = @()

$healthResp = Invoke-ApiGet -Path "/api/inference/health"
$healthData = Get-PropValue -Obj $healthResp -Name "data"
$healthTraceId = Get-PropValue -Obj $healthData -Name "trace_id"
$healthBackend = Get-PropValue -Obj $healthData -Name "backend_type"
$healthUpstream = Get-PropValue -Obj $healthData -Name "upstream"
$healthCircuitOpen = Get-PropValue -Obj $healthUpstream -Name "circuit_open"
$healthCircuitOpenUntilMs = Get-PropValue -Obj $healthUpstream -Name "circuit_open_until_ms"
$healthCircuitCheckPassed = $true
if (($healthBackend -ne $null) -and ($healthBackend -eq "rk3588_rknn")) {
    $healthCircuitCheckPassed = ($healthCircuitOpen -is [bool])
    if ($healthCircuitCheckPassed -and $healthCircuitOpen -eq $true) {
        $healthCircuitCheckPassed = ($healthCircuitOpenUntilMs -ne $null) -and (([long]$healthCircuitOpenUntilMs) -gt 0)
    }
}
$checks += New-CheckResult -Api "/api/inference/health" -Passed (($healthResp.code -eq 0) -and ($healthTraceId -ne $null) -and ($healthTraceId -ne "") -and ($healthBackend -ne $null) -and ($healthBackend -ne "") -and ($healthUpstream -ne $null) -and $healthCircuitCheckPassed) -Detail ("code={0}; trace_id={1}; backend_type={2}; circuit_open={3}; circuit_open_until_ms={4}" -f $healthResp.code, $healthTraceId, $healthBackend, $healthCircuitOpen, $healthCircuitOpenUntilMs)

$circuitStatusResp = Invoke-ApiGet -Path "/api/inference/circuit/status"
$circuitStatusData = Get-PropValue -Obj $circuitStatusResp -Name "data"
$circuitStatusTraceId = Get-PropValue -Obj $circuitStatusData -Name "trace_id"
$circuitStatusBackendType = Get-PropValue -Obj $circuitStatusData -Name "backend_type"
$circuitStatusCircuit = Get-PropValue -Obj $circuitStatusData -Name "circuit"
$circuitStatusInnerTraceId = Get-PropValue -Obj $circuitStatusCircuit -Name "trace_id"
$circuitStatusInnerBackend = Get-PropValue -Obj $circuitStatusCircuit -Name "backend"
$circuitStatusRouteBackend = Get-PropValue -Obj $circuitStatusCircuit -Name "route_backend"
$circuitStatusOpen = Get-PropValue -Obj $circuitStatusCircuit -Name "circuit_open"
$checks += New-CheckResult -Api "/api/inference/circuit/status" -Passed (($circuitStatusResp.code -eq 0) -and ($circuitStatusTraceId -ne $null) -and ($circuitStatusTraceId -ne "") -and ($circuitStatusBackendType -ne $null) -and ($circuitStatusBackendType -ne "") -and ($circuitStatusCircuit -ne $null) -and ($circuitStatusInnerTraceId -ne $null) -and ($circuitStatusInnerTraceId -ne "") -and ($circuitStatusInnerBackend -ne $null) -and ($circuitStatusInnerBackend -ne "") -and ($circuitStatusRouteBackend -ne $null) -and ($circuitStatusRouteBackend -ne "") -and ($circuitStatusOpen -is [bool])) -Detail ("code={0}; trace_id={1}; backend_type={2}; circuit_trace_id={3}; circuit_backend={4}; route_backend={5}; circuit_open={6}" -f $circuitStatusResp.code, $circuitStatusTraceId, $circuitStatusBackendType, $circuitStatusInnerTraceId, $circuitStatusInnerBackend, $circuitStatusRouteBackend, $circuitStatusOpen)

$circuitResetResp = Invoke-ApiGet -Path "/api/inference/circuit/reset"
$circuitResetData = Get-PropValue -Obj $circuitResetResp -Name "data"
$circuitResetTraceId = Get-PropValue -Obj $circuitResetData -Name "trace_id"
$circuitResetBackendType = Get-PropValue -Obj $circuitResetData -Name "backend_type"
$circuitResetCircuit = Get-PropValue -Obj $circuitResetData -Name "circuit"
$circuitResetInnerTraceId = Get-PropValue -Obj $circuitResetCircuit -Name "trace_id"
$circuitResetInnerBackend = Get-PropValue -Obj $circuitResetCircuit -Name "backend"
$circuitResetRouteBackend = Get-PropValue -Obj $circuitResetCircuit -Name "route_backend"
$circuitResetFlag = Get-PropValue -Obj $circuitResetCircuit -Name "reset"
$checks += New-CheckResult -Api "/api/inference/circuit/reset" -Passed (($circuitResetResp.code -eq 0) -and ($circuitResetTraceId -ne $null) -and ($circuitResetTraceId -ne "") -and ($circuitResetBackendType -ne $null) -and ($circuitResetBackendType -ne "") -and ($circuitResetCircuit -ne $null) -and ($circuitResetInnerTraceId -ne $null) -and ($circuitResetInnerTraceId -ne "") -and ($circuitResetInnerBackend -ne $null) -and ($circuitResetInnerBackend -ne "") -and ($circuitResetRouteBackend -ne $null) -and ($circuitResetRouteBackend -ne "") -and ($circuitResetFlag -is [bool])) -Detail ("code={0}; trace_id={1}; backend_type={2}; circuit_trace_id={3}; circuit_backend={4}; route_backend={5}; reset={6}" -f $circuitResetResp.code, $circuitResetTraceId, $circuitResetBackendType, $circuitResetInnerTraceId, $circuitResetInnerBackend, $circuitResetRouteBackend, $circuitResetFlag)

$deadLetterStatsResp = Invoke-ApiGet -Path "/api/inference/dead-letter/stats"
$deadLetterStatsData = Get-PropValue -Obj $deadLetterStatsResp -Name "data"
$deadLetterStatsTraceId = Get-PropValue -Obj $deadLetterStatsData -Name "trace_id"
$deadLetterStats = Get-PropValue -Obj $deadLetterStatsData -Name "dead_letter"
$deadLetterQueueSize = Get-PropValue -Obj $deadLetterStats -Name "queue_size"
$deadLetterMaxSize = Get-PropValue -Obj $deadLetterStats -Name "max_size"
$deadLetterDefaultListLimit = Get-PropValue -Obj $deadLetterStats -Name "default_list_limit"
$deadLetterMaxListLimit = Get-PropValue -Obj $deadLetterStats -Name "max_list_limit"
$deadLetterMaxReplayAttempts = Get-PropValue -Obj $deadLetterStats -Name "max_replay_attempts"
$deadLetterReplayedEntryCount = Get-PropValue -Obj $deadLetterStats -Name "replayed_entry_count"
$deadLetterReplaySuccessEntryCount = Get-PropValue -Obj $deadLetterStats -Name "replay_success_entry_count"
$deadLetterReplayFailedEntryCount = Get-PropValue -Obj $deadLetterStats -Name "replay_failed_entry_count"
$deadLetterPendingReplayEntryCount = Get-PropValue -Obj $deadLetterStats -Name "pending_replay_entry_count"
$deadLetterExhaustedReplayEntryCount = Get-PropValue -Obj $deadLetterStats -Name "exhausted_replay_entry_count"
$deadLetterRetryableEntryCount = Get-PropValue -Obj $deadLetterStats -Name "retryable_entry_count"
$deadLetterNonRetryableEntryCount = Get-PropValue -Obj $deadLetterStats -Name "non_retryable_entry_count"
$deadLetterReplayInProgressEntryCount = Get-PropValue -Obj $deadLetterStats -Name "replay_in_progress_entry_count"
$deadLetterStatsCountsConsistent = $false
if (($deadLetterReplayedEntryCount -ne $null) -and ($deadLetterReplaySuccessEntryCount -ne $null) -and ($deadLetterReplayFailedEntryCount -ne $null) -and ($deadLetterPendingReplayEntryCount -ne $null) -and ($deadLetterQueueSize -ne $null)) {
    $deadLetterStatsCountsConsistent = (([int]$deadLetterReplayedEntryCount) -eq (([int]$deadLetterReplaySuccessEntryCount) + ([int]$deadLetterReplayFailedEntryCount))) -and (([int]$deadLetterQueueSize) -eq (([int]$deadLetterReplayedEntryCount) + ([int]$deadLetterPendingReplayEntryCount)))
}
$deadLetterRetryableCountsConsistent = $false
if (($deadLetterQueueSize -ne $null) -and ($deadLetterRetryableEntryCount -ne $null) -and ($deadLetterNonRetryableEntryCount -ne $null) -and ($deadLetterExhaustedReplayEntryCount -ne $null)) {
    $deadLetterRetryableCountsConsistent = (([int]$deadLetterQueueSize) -eq (([int]$deadLetterRetryableEntryCount) + ([int]$deadLetterNonRetryableEntryCount))) -and (([int]$deadLetterNonRetryableEntryCount) -eq ([int]$deadLetterExhaustedReplayEntryCount))
}
$deadLetterReplayLockCountsConsistent = $false
if (($deadLetterQueueSize -ne $null) -and ($deadLetterReplayInProgressEntryCount -ne $null)) {
    $deadLetterReplayLockCountsConsistent = (([int]$deadLetterReplayInProgressEntryCount) -ge 0) -and (([int]$deadLetterReplayInProgressEntryCount) -le ([int]$deadLetterQueueSize))
}
$checks += New-CheckResult -Api "/api/inference/dead-letter/stats" -Passed (($deadLetterStatsResp.code -eq 0) -and ($deadLetterStatsTraceId -ne $null) -and ($deadLetterStatsTraceId -ne "") -and ($deadLetterStats -ne $null) -and ($deadLetterQueueSize -ne $null) -and (([int]$deadLetterQueueSize) -ge 0) -and ($deadLetterMaxSize -ne $null) -and (([int]$deadLetterMaxSize) -ge 1) -and ($deadLetterDefaultListLimit -ne $null) -and (([int]$deadLetterDefaultListLimit) -ge 1) -and ($deadLetterMaxListLimit -ne $null) -and (([int]$deadLetterMaxListLimit) -ge ([int]$deadLetterDefaultListLimit)) -and ($deadLetterMaxReplayAttempts -ne $null) -and (([int]$deadLetterMaxReplayAttempts) -ge 1) -and ($deadLetterReplayedEntryCount -ne $null) -and (([int]$deadLetterReplayedEntryCount) -ge 0) -and ($deadLetterReplaySuccessEntryCount -ne $null) -and (([int]$deadLetterReplaySuccessEntryCount) -ge 0) -and ($deadLetterReplayFailedEntryCount -ne $null) -and (([int]$deadLetterReplayFailedEntryCount) -ge 0) -and ($deadLetterPendingReplayEntryCount -ne $null) -and (([int]$deadLetterPendingReplayEntryCount) -ge 0) -and ($deadLetterExhaustedReplayEntryCount -ne $null) -and (([int]$deadLetterExhaustedReplayEntryCount) -ge 0) -and ($deadLetterRetryableEntryCount -ne $null) -and (([int]$deadLetterRetryableEntryCount) -ge 0) -and ($deadLetterNonRetryableEntryCount -ne $null) -and (([int]$deadLetterNonRetryableEntryCount) -ge 0) -and ($deadLetterReplayInProgressEntryCount -ne $null) -and $deadLetterStatsCountsConsistent -and $deadLetterRetryableCountsConsistent -and $deadLetterReplayLockCountsConsistent) -Detail ("code={0}; trace_id={1}; queue_size={2}; max_size={3}; default_list_limit={4}; max_list_limit={5}; max_replay_attempts={6}; replayed={7}; replay_success={8}; replay_failed={9}; pending_replay={10}; exhausted_replay={11}; retryable={12}; non_retryable={13}; replay_in_progress={14}; counts_consistent={15}; retryable_counts_consistent={16}; replay_lock_counts_consistent={17}" -f $deadLetterStatsResp.code, $deadLetterStatsTraceId, $deadLetterQueueSize, $deadLetterMaxSize, $deadLetterDefaultListLimit, $deadLetterMaxListLimit, $deadLetterMaxReplayAttempts, $deadLetterReplayedEntryCount, $deadLetterReplaySuccessEntryCount, $deadLetterReplayFailedEntryCount, $deadLetterPendingReplayEntryCount, $deadLetterExhaustedReplayEntryCount, $deadLetterRetryableEntryCount, $deadLetterNonRetryableEntryCount, $deadLetterReplayInProgressEntryCount, $deadLetterStatsCountsConsistent, $deadLetterRetryableCountsConsistent, $deadLetterReplayLockCountsConsistent)

$deadLetterLatestResp = Invoke-ApiGet -Path "/api/inference/dead-letter/latest?limit=5"
$deadLetterLatestData = Get-PropValue -Obj $deadLetterLatestResp -Name "data"
$deadLetterLatestTraceId = Get-PropValue -Obj $deadLetterLatestData -Name "trace_id"
$deadLetterLatest = Get-PropValue -Obj $deadLetterLatestData -Name "dead_letter"
$deadLetterLatestBudgetCheckPassed = $true
$deadLetterLatestBudgetDetail = "empty-list-skip"
if ($deadLetterLatest -is [System.Collections.IList] -and $deadLetterLatest.Count -gt 0) {
    $deadLetterLatestFirst = $deadLetterLatest[0]
    $deadLetterLatestFirstMaxReplayAttempts = Get-PropValue -Obj $deadLetterLatestFirst -Name "max_replay_attempts"
    $deadLetterLatestFirstRemainingReplayAttempts = Get-PropValue -Obj $deadLetterLatestFirst -Name "remaining_replay_attempts"
    $deadLetterLatestFirstReplayExhausted = Get-PropValue -Obj $deadLetterLatestFirst -Name "replay_exhausted"
    $deadLetterLatestBudgetCheckPassed = ($deadLetterLatestFirstMaxReplayAttempts -ne $null) -and (([int]$deadLetterLatestFirstMaxReplayAttempts) -ge 1) -and ($deadLetterLatestFirstRemainingReplayAttempts -ne $null) -and (([int]$deadLetterLatestFirstRemainingReplayAttempts) -ge 0) -and ($deadLetterLatestFirstReplayExhausted -is [bool])
    $deadLetterLatestBudgetDetail = ("first.max_replay_attempts={0}; first.remaining_replay_attempts={1}; first.replay_exhausted={2}" -f $deadLetterLatestFirstMaxReplayAttempts, $deadLetterLatestFirstRemainingReplayAttempts, $deadLetterLatestFirstReplayExhausted)
}
$checks += New-CheckResult -Api "/api/inference/dead-letter/latest" -Passed (($deadLetterLatestResp.code -eq 0) -and ($deadLetterLatestTraceId -ne $null) -and ($deadLetterLatestTraceId -ne "") -and ($deadLetterLatest -is [System.Collections.IList]) -and ($deadLetterLatest.Count -le 5) -and $deadLetterLatestBudgetCheckPassed) -Detail ("code={0}; trace_id={1}; list_size={2}; budget_check={3}; budget_detail={4}" -f $deadLetterLatestResp.code, $deadLetterLatestTraceId, ($(if ($deadLetterLatest -is [System.Collections.IList]) { $deadLetterLatest.Count } else { -1 })), $deadLetterLatestBudgetCheckPassed, $deadLetterLatestBudgetDetail)

$deadLetterLatestRetryableResp = Invoke-ApiGet -Path "/api/inference/dead-letter/latest?limit=5&only_retryable=1"
$deadLetterLatestRetryableData = Get-PropValue -Obj $deadLetterLatestRetryableResp -Name "data"
$deadLetterLatestRetryableTraceId = Get-PropValue -Obj $deadLetterLatestRetryableData -Name "trace_id"
$deadLetterLatestRetryable = Get-PropValue -Obj $deadLetterLatestRetryableData -Name "dead_letter"
$deadLetterLatestRetryableCheckPassed = $true
$deadLetterLatestRetryableDetail = "empty-list-skip"
if ($deadLetterLatestRetryable -is [System.Collections.IList] -and $deadLetterLatestRetryable.Count -gt 0) {
    $deadLetterLatestRetryableViolations = 0
    foreach ($deadLetterLatestRetryableItem in $deadLetterLatestRetryable) {
        $deadLetterLatestRetryableItemRemaining = Get-PropValue -Obj $deadLetterLatestRetryableItem -Name "remaining_replay_attempts"
        $deadLetterLatestRetryableItemExhausted = Get-PropValue -Obj $deadLetterLatestRetryableItem -Name "replay_exhausted"
        $deadLetterLatestRetryableItemValid = ($deadLetterLatestRetryableItemRemaining -ne $null) -and (([int]$deadLetterLatestRetryableItemRemaining) -gt 0) -and ($deadLetterLatestRetryableItemExhausted -is [bool]) -and (-not [bool]$deadLetterLatestRetryableItemExhausted)
        if (-not $deadLetterLatestRetryableItemValid) {
            $deadLetterLatestRetryableViolations += 1
        }
    }
    $deadLetterLatestRetryableCheckPassed = $deadLetterLatestRetryableViolations -eq 0
    $deadLetterLatestRetryableDetail = ("violations={0}; list_size={1}" -f $deadLetterLatestRetryableViolations, $deadLetterLatestRetryable.Count)
}
$checks += New-CheckResult -Api "/api/inference/dead-letter/latest(only_retryable=1)" -Passed (($deadLetterLatestRetryableResp.code -eq 0) -and ($deadLetterLatestRetryableTraceId -ne $null) -and ($deadLetterLatestRetryableTraceId -ne "") -and ($deadLetterLatestRetryable -is [System.Collections.IList]) -and ($deadLetterLatestRetryable.Count -le 5) -and $deadLetterLatestRetryableCheckPassed) -Detail ("code={0}; trace_id={1}; list_size={2}; retryable_check={3}; retryable_detail={4}" -f $deadLetterLatestRetryableResp.code, $deadLetterLatestRetryableTraceId, ($(if ($deadLetterLatestRetryable -is [System.Collections.IList]) { $deadLetterLatestRetryable.Count } else { -1 })), $deadLetterLatestRetryableCheckPassed, $deadLetterLatestRetryableDetail)

$deadLetterLatestExhaustedResp = Invoke-ApiGet -Path "/api/inference/dead-letter/latest?limit=5&only_exhausted=1"
$deadLetterLatestExhaustedData = Get-PropValue -Obj $deadLetterLatestExhaustedResp -Name "data"
$deadLetterLatestExhaustedTraceId = Get-PropValue -Obj $deadLetterLatestExhaustedData -Name "trace_id"
$deadLetterLatestExhausted = Get-PropValue -Obj $deadLetterLatestExhaustedData -Name "dead_letter"
$deadLetterLatestExhaustedCheckPassed = $true
$deadLetterLatestExhaustedDetail = "empty-list-skip"
if ($deadLetterLatestExhausted -is [System.Collections.IList] -and $deadLetterLatestExhausted.Count -gt 0) {
    $deadLetterLatestExhaustedViolations = 0
    foreach ($deadLetterLatestExhaustedItem in $deadLetterLatestExhausted) {
        $deadLetterLatestExhaustedItemRemaining = Get-PropValue -Obj $deadLetterLatestExhaustedItem -Name "remaining_replay_attempts"
        $deadLetterLatestExhaustedItemExhausted = Get-PropValue -Obj $deadLetterLatestExhaustedItem -Name "replay_exhausted"
        $deadLetterLatestExhaustedItemValid = ($deadLetterLatestExhaustedItemRemaining -ne $null) -and (([int]$deadLetterLatestExhaustedItemRemaining) -eq 0) -and ($deadLetterLatestExhaustedItemExhausted -is [bool]) -and ([bool]$deadLetterLatestExhaustedItemExhausted)
        if (-not $deadLetterLatestExhaustedItemValid) {
            $deadLetterLatestExhaustedViolations += 1
        }
    }
    $deadLetterLatestExhaustedCheckPassed = $deadLetterLatestExhaustedViolations -eq 0
    $deadLetterLatestExhaustedDetail = ("violations={0}; list_size={1}" -f $deadLetterLatestExhaustedViolations, $deadLetterLatestExhausted.Count)
}
$checks += New-CheckResult -Api "/api/inference/dead-letter/latest(only_exhausted=1)" -Passed (($deadLetterLatestExhaustedResp.code -eq 0) -and ($deadLetterLatestExhaustedTraceId -ne $null) -and ($deadLetterLatestExhaustedTraceId -ne "") -and ($deadLetterLatestExhausted -is [System.Collections.IList]) -and ($deadLetterLatestExhausted.Count -le 5) -and $deadLetterLatestExhaustedCheckPassed) -Detail ("code={0}; trace_id={1}; list_size={2}; exhausted_check={3}; exhausted_detail={4}" -f $deadLetterLatestExhaustedResp.code, $deadLetterLatestExhaustedTraceId, ($(if ($deadLetterLatestExhausted -is [System.Collections.IList]) { $deadLetterLatestExhausted.Count } else { -1 })), $deadLetterLatestExhaustedCheckPassed, $deadLetterLatestExhaustedDetail)

$deadLetterGetNotFoundResp = Invoke-ApiGet -Path "/api/inference/dead-letter/get?dead_letter_id=-1"
$deadLetterGetNotFoundData = Get-PropValue -Obj $deadLetterGetNotFoundResp -Name "data"
$deadLetterGetNotFoundTraceId = Get-PropValue -Obj $deadLetterGetNotFoundData -Name "trace_id"
$deadLetterGetNotFoundId = Get-PropValue -Obj $deadLetterGetNotFoundData -Name "dead_letter_id"
$checks += New-CheckResult -Api "/api/inference/dead-letter/get(not-found)" -Passed (($deadLetterGetNotFoundResp.code -ne 0) -and ($deadLetterGetNotFoundTraceId -ne $null) -and ($deadLetterGetNotFoundTraceId -ne "") -and ($deadLetterGetNotFoundId -ne $null) -and (([long]$deadLetterGetNotFoundId) -eq -1)) -Detail ("code={0}; trace_id={1}; dead_letter_id={2}" -f $deadLetterGetNotFoundResp.code, $deadLetterGetNotFoundTraceId, $deadLetterGetNotFoundId)

$deadLetterRemoveNotFoundResp = Invoke-ApiGet -Path "/api/inference/dead-letter/remove?dead_letter_id=-1"
$deadLetterRemoveNotFoundData = Get-PropValue -Obj $deadLetterRemoveNotFoundResp -Name "data"
$deadLetterRemoveNotFoundTraceId = Get-PropValue -Obj $deadLetterRemoveNotFoundData -Name "trace_id"
$deadLetterRemoveNotFoundId = Get-PropValue -Obj $deadLetterRemoveNotFoundData -Name "dead_letter_id"
$deadLetterRemoveNotFoundRemoved = Get-PropValue -Obj $deadLetterRemoveNotFoundData -Name "removed"
$checks += New-CheckResult -Api "/api/inference/dead-letter/remove(not-found)" -Passed (($deadLetterRemoveNotFoundResp.code -ne 0) -and ($deadLetterRemoveNotFoundTraceId -ne $null) -and ($deadLetterRemoveNotFoundTraceId -ne "") -and ($deadLetterRemoveNotFoundId -ne $null) -and (([long]$deadLetterRemoveNotFoundId) -eq -1) -and ($deadLetterRemoveNotFoundRemoved -is [bool]) -and (-not [bool]$deadLetterRemoveNotFoundRemoved)) -Detail ("code={0}; trace_id={1}; dead_letter_id={2}; removed={3}" -f $deadLetterRemoveNotFoundResp.code, $deadLetterRemoveNotFoundTraceId, $deadLetterRemoveNotFoundId, $deadLetterRemoveNotFoundRemoved)

$deadLetterReplayNotFoundResp = Invoke-ApiGet -Path "/api/inference/dead-letter/replay?dead_letter_id=-1"
$deadLetterReplayNotFoundData = Get-PropValue -Obj $deadLetterReplayNotFoundResp -Name "data"
$deadLetterReplayNotFoundTraceId = Get-PropValue -Obj $deadLetterReplayNotFoundData -Name "trace_id"
$deadLetterReplayNotFoundId = Get-PropValue -Obj $deadLetterReplayNotFoundData -Name "dead_letter_id"
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay(not-found)" -Passed (($deadLetterReplayNotFoundResp.code -ne 0) -and ($deadLetterReplayNotFoundTraceId -ne $null) -and ($deadLetterReplayNotFoundTraceId -ne "") -and ($deadLetterReplayNotFoundId -ne $null) -and (([long]$deadLetterReplayNotFoundId) -eq -1)) -Detail ("code={0}; trace_id={1}; dead_letter_id={2}" -f $deadLetterReplayNotFoundResp.code, $deadLetterReplayNotFoundTraceId, $deadLetterReplayNotFoundId)

$deadLetterReplayBatchResp = Invoke-ApiGet -Path "/api/inference/dead-letter/replay/batch?limit=2&only_exhausted=1"
$deadLetterReplayBatchData = Get-PropValue -Obj $deadLetterReplayBatchResp -Name "data"
$deadLetterReplayBatchTraceId = Get-PropValue -Obj $deadLetterReplayBatchData -Name "trace_id"
$deadLetterReplayBatchSelectedCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "selected_count"
$deadLetterReplayBatchRequestedOffset = Get-PropValue -Obj $deadLetterReplayBatchData -Name "requested_offset"
$deadLetterReplayBatchEffectiveOffset = Get-PropValue -Obj $deadLetterReplayBatchData -Name "effective_offset"
$deadLetterReplayBatchNextOffset = Get-PropValue -Obj $deadLetterReplayBatchData -Name "next_offset"
$deadLetterReplayBatchHasMore = Get-PropValue -Obj $deadLetterReplayBatchData -Name "has_more"
$deadLetterReplayBatchTotalSelectedCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "total_selected_count"
$deadLetterReplayBatchStrictResume = Get-PropValue -Obj $deadLetterReplayBatchData -Name "strict_resume"
$deadLetterReplayBatchExpectedTotal = Get-PropValue -Obj $deadLetterReplayBatchData -Name "expected_total_selected_count"
$deadLetterReplayBatchActualTotal = Get-PropValue -Obj $deadLetterReplayBatchData -Name "actual_total_selected_count"
$deadLetterReplayBatchExpectedWindowFingerprint = Get-PropValue -Obj $deadLetterReplayBatchData -Name "expected_window_fingerprint"
$deadLetterReplayBatchActualWindowFingerprint = Get-PropValue -Obj $deadLetterReplayBatchData -Name "actual_window_fingerprint"
$deadLetterReplayBatchProcessedCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "processed_count"
$deadLetterReplayBatchSuccessCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "success_count"
$deadLetterReplayBatchFailedCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "failed_count"
$deadLetterReplayBatchEffectiveLimit = Get-PropValue -Obj $deadLetterReplayBatchData -Name "effective_limit"
$deadLetterReplayBatchMaxLimit = Get-PropValue -Obj $deadLetterReplayBatchData -Name "max_limit"
$deadLetterReplayBatchTruncated = Get-PropValue -Obj $deadLetterReplayBatchData -Name "truncated"
$deadLetterReplayBatchDryRun = Get-PropValue -Obj $deadLetterReplayBatchData -Name "dry_run"
$deadLetterReplayBatchStopOnError = Get-PropValue -Obj $deadLetterReplayBatchData -Name "stop_on_error"
$deadLetterReplayBatchStoppedOnError = Get-PropValue -Obj $deadLetterReplayBatchData -Name "stopped_on_error"
$deadLetterReplayBatchDryRunCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "dry_run_count"
$deadLetterReplayBatchSuccessIds = Get-PropValue -Obj $deadLetterReplayBatchData -Name "success_dead_letter_ids"
$deadLetterReplayBatchFailedIds = Get-PropValue -Obj $deadLetterReplayBatchData -Name "failed_dead_letter_ids"
$deadLetterReplayBatchDryRunIds = Get-PropValue -Obj $deadLetterReplayBatchData -Name "dry_run_dead_letter_ids"
$deadLetterReplayBatchRemainingCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "remaining_count"
$deadLetterReplayBatchFailedReplayInProgressCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "failed_replay_in_progress_count"
$deadLetterReplayBatchFailedReplayExhaustedCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "failed_replay_exhausted_count"
$deadLetterReplayBatchFailedOtherCount = Get-PropValue -Obj $deadLetterReplayBatchData -Name "failed_other_count"
$deadLetterReplayBatchResults = Get-PropValue -Obj $deadLetterReplayBatchData -Name "results"
$deadLetterReplayBatchCountsConsistent = $false
if (($deadLetterReplayBatchSelectedCount -ne $null) -and ($deadLetterReplayBatchRequestedOffset -ne $null) -and ($deadLetterReplayBatchEffectiveOffset -ne $null) -and ($deadLetterReplayBatchNextOffset -ne $null) -and ($deadLetterReplayBatchHasMore -is [bool]) -and ($deadLetterReplayBatchTotalSelectedCount -ne $null) -and ($deadLetterReplayBatchStrictResume -is [bool]) -and ($deadLetterReplayBatchExpectedTotal -eq $null) -and ($deadLetterReplayBatchActualTotal -ne $null) -and ($deadLetterReplayBatchExpectedWindowFingerprint -eq $null) -and ($deadLetterReplayBatchActualWindowFingerprint -ne $null) -and ($deadLetterReplayBatchActualWindowFingerprint -ne "") -and ($deadLetterReplayBatchProcessedCount -ne $null) -and ($deadLetterReplayBatchSuccessCount -ne $null) -and ($deadLetterReplayBatchFailedCount -ne $null) -and ($deadLetterReplayBatchEffectiveLimit -ne $null) -and ($deadLetterReplayBatchMaxLimit -ne $null) -and ($deadLetterReplayBatchTruncated -is [bool]) -and ($deadLetterReplayBatchDryRun -is [bool]) -and ($deadLetterReplayBatchStopOnError -is [bool]) -and ($deadLetterReplayBatchStoppedOnError -is [bool]) -and ($deadLetterReplayBatchDryRunCount -ne $null) -and ($deadLetterReplayBatchSuccessIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchFailedIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchDryRunIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchRemainingCount -ne $null) -and ($deadLetterReplayBatchFailedReplayInProgressCount -ne $null) -and ($deadLetterReplayBatchFailedReplayExhaustedCount -ne $null) -and ($deadLetterReplayBatchFailedOtherCount -ne $null) -and ($deadLetterReplayBatchResults -is [System.Collections.IList])) {
    $deadLetterReplayBatchCountsConsistent = (([int]$deadLetterReplayBatchSelectedCount) -eq ([int]$deadLetterReplayBatchProcessedCount)) -and ($deadLetterReplayBatchResults.Count -eq ([int]$deadLetterReplayBatchProcessedCount)) -and ($deadLetterReplayBatchResults.Count -le 2) -and (([int]$deadLetterReplayBatchEffectiveLimit) -le ([int]$deadLetterReplayBatchMaxLimit)) -and (([int]$deadLetterReplayBatchSelectedCount) -le ([int]$deadLetterReplayBatchEffectiveLimit)) -and (([int]$deadLetterReplayBatchActualTotal) -eq ([int]$deadLetterReplayBatchTotalSelectedCount)) -and (([bool]$deadLetterReplayBatchStrictResume -eq $false)) -and (([int]$deadLetterReplayBatchActualWindowFingerprint.Length) -eq 64) -and (([int]$deadLetterReplayBatchEffectiveOffset) -ge 0) -and (([int]$deadLetterReplayBatchEffectiveOffset) -le ([int]$deadLetterReplayBatchTotalSelectedCount)) -and (([int]$deadLetterReplayBatchNextOffset) -eq (([int]$deadLetterReplayBatchEffectiveOffset) + ([int]$deadLetterReplayBatchProcessedCount))) -and (([bool]$deadLetterReplayBatchHasMore) -eq (([int]$deadLetterReplayBatchNextOffset) -lt ([int]$deadLetterReplayBatchTotalSelectedCount))) -and (([int]$deadLetterReplayBatchFailedCount) -eq (([int]$deadLetterReplayBatchFailedReplayInProgressCount) + ([int]$deadLetterReplayBatchFailedReplayExhaustedCount) + ([int]$deadLetterReplayBatchFailedOtherCount))) -and (([int]$deadLetterReplayBatchSuccessCount) -eq $deadLetterReplayBatchSuccessIds.Count) -and (([int]$deadLetterReplayBatchFailedCount) -eq $deadLetterReplayBatchFailedIds.Count) -and (([int]$deadLetterReplayBatchDryRunCount) -eq $deadLetterReplayBatchDryRunIds.Count) -and (([int]$deadLetterReplayBatchRemainingCount) -eq (([int]$deadLetterReplayBatchSelectedCount) - ([int]$deadLetterReplayBatchProcessedCount))) -and (([bool]$deadLetterReplayBatchStopOnError) -eq $false) -and (([bool]$deadLetterReplayBatchStoppedOnError) -eq $false)
    if ([bool]$deadLetterReplayBatchDryRun) {
        $deadLetterReplayBatchCountsConsistent = $deadLetterReplayBatchCountsConsistent -and (([int]$deadLetterReplayBatchDryRunCount) -eq ([int]$deadLetterReplayBatchProcessedCount)) -and (([int]$deadLetterReplayBatchSuccessCount) -eq 0) -and (([int]$deadLetterReplayBatchFailedCount) -eq 0)
    } else {
        $deadLetterReplayBatchCountsConsistent = $deadLetterReplayBatchCountsConsistent -and (([int]$deadLetterReplayBatchDryRunCount) -eq 0) -and (([int]$deadLetterReplayBatchProcessedCount) -eq (([int]$deadLetterReplayBatchSuccessCount) + ([int]$deadLetterReplayBatchFailedCount)))
    }
}
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch" -Passed (($deadLetterReplayBatchResp.code -eq 0) -and ($deadLetterReplayBatchTraceId -ne $null) -and ($deadLetterReplayBatchTraceId -ne "") -and ($deadLetterReplayBatchResults -is [System.Collections.IList]) -and $deadLetterReplayBatchCountsConsistent) -Detail ("code={0}; trace_id={1}; selected={2}; requested_offset={3}; effective_offset={4}; next_offset={5}; has_more={6}; total_selected={7}; strict_resume={8}; expected_total={9}; actual_total={10}; expected_window={11}; actual_window={12}; processed={13}; success={14}; failed={15}; effective_limit={16}; max_limit={17}; truncated={18}; dry_run={19}; stop_on_error={20}; stopped_on_error={21}; dry_run_count={22}; remaining_count={23}; success_ids={24}; failed_ids={25}; dry_run_ids={26}; failed_in_progress={27}; failed_exhausted={28}; failed_other={29}; list_size={30}; counts_consistent={31}" -f $deadLetterReplayBatchResp.code, $deadLetterReplayBatchTraceId, $deadLetterReplayBatchSelectedCount, $deadLetterReplayBatchRequestedOffset, $deadLetterReplayBatchEffectiveOffset, $deadLetterReplayBatchNextOffset, $deadLetterReplayBatchHasMore, $deadLetterReplayBatchTotalSelectedCount, $deadLetterReplayBatchStrictResume, $deadLetterReplayBatchExpectedTotal, $deadLetterReplayBatchActualTotal, $deadLetterReplayBatchExpectedWindowFingerprint, $deadLetterReplayBatchActualWindowFingerprint, $deadLetterReplayBatchProcessedCount, $deadLetterReplayBatchSuccessCount, $deadLetterReplayBatchFailedCount, $deadLetterReplayBatchEffectiveLimit, $deadLetterReplayBatchMaxLimit, $deadLetterReplayBatchTruncated, $deadLetterReplayBatchDryRun, $deadLetterReplayBatchStopOnError, $deadLetterReplayBatchStoppedOnError, $deadLetterReplayBatchDryRunCount, $deadLetterReplayBatchRemainingCount, ($(if ($deadLetterReplayBatchSuccessIds -is [System.Collections.IList]) { $deadLetterReplayBatchSuccessIds.Count } else { -1 })), ($(if ($deadLetterReplayBatchFailedIds -is [System.Collections.IList]) { $deadLetterReplayBatchFailedIds.Count } else { -1 })), ($(if ($deadLetterReplayBatchDryRunIds -is [System.Collections.IList]) { $deadLetterReplayBatchDryRunIds.Count } else { -1 })), $deadLetterReplayBatchFailedReplayInProgressCount, $deadLetterReplayBatchFailedReplayExhaustedCount, $deadLetterReplayBatchFailedOtherCount, ($(if ($deadLetterReplayBatchResults -is [System.Collections.IList]) { $deadLetterReplayBatchResults.Count } else { -1 })), $deadLetterReplayBatchCountsConsistent)

$deadLetterReplayBatchByIdsResp = Invoke-ApiGet -Path "/api/inference/dead-letter/replay/batch?dead_letter_ids=52,51,52&dry_run=1&limit=5"
$deadLetterReplayBatchByIdsData = Get-PropValue -Obj $deadLetterReplayBatchByIdsResp -Name "data"
$deadLetterReplayBatchByIdsTraceId = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "trace_id"
$deadLetterReplayBatchByIdsSource = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "selection_source"
$deadLetterReplayBatchByIdsProcessed = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "processed_count"
$deadLetterReplayBatchByIdsRequestedOffset = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "requested_offset"
$deadLetterReplayBatchByIdsEffectiveOffset = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "effective_offset"
$deadLetterReplayBatchByIdsNextOffset = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "next_offset"
$deadLetterReplayBatchByIdsHasMore = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "has_more"
$deadLetterReplayBatchByIdsTotalSelectedCount = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "total_selected_count"
$deadLetterReplayBatchByIdsEffectiveLimit = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "effective_limit"
$deadLetterReplayBatchByIdsMaxLimit = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "max_limit"
$deadLetterReplayBatchByIdsTruncated = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "truncated"
$deadLetterReplayBatchByIdsStopOnError = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "stop_on_error"
$deadLetterReplayBatchByIdsStoppedOnError = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "stopped_on_error"
$deadLetterReplayBatchByIdsDryRunCount = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "dry_run_count"
$deadLetterReplayBatchByIdsSuccessIds = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "success_dead_letter_ids"
$deadLetterReplayBatchByIdsFailedIds = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "failed_dead_letter_ids"
$deadLetterReplayBatchByIdsDryRunIds = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "dry_run_dead_letter_ids"
$deadLetterReplayBatchByIdsRemainingCount = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "remaining_count"
$deadLetterReplayBatchByIdsResults = Get-PropValue -Obj $deadLetterReplayBatchByIdsData -Name "results"
$deadLetterReplayBatchByIdsValid = ($deadLetterReplayBatchByIdsResp.code -eq 0) -and ($deadLetterReplayBatchByIdsTraceId -ne $null) -and ($deadLetterReplayBatchByIdsTraceId -ne "") -and ($deadLetterReplayBatchByIdsSource -eq "explicit_ids") -and ($deadLetterReplayBatchByIdsResults -is [System.Collections.IList]) -and ($deadLetterReplayBatchByIdsProcessed -ne $null) -and ($deadLetterReplayBatchByIdsRequestedOffset -ne $null) -and ($deadLetterReplayBatchByIdsEffectiveOffset -ne $null) -and ($deadLetterReplayBatchByIdsNextOffset -ne $null) -and ($deadLetterReplayBatchByIdsHasMore -is [bool]) -and ($deadLetterReplayBatchByIdsTotalSelectedCount -ne $null) -and ($deadLetterReplayBatchByIdsEffectiveLimit -ne $null) -and ($deadLetterReplayBatchByIdsMaxLimit -ne $null) -and ($deadLetterReplayBatchByIdsTruncated -is [bool]) -and ($deadLetterReplayBatchByIdsStopOnError -is [bool]) -and ($deadLetterReplayBatchByIdsStoppedOnError -is [bool]) -and ($deadLetterReplayBatchByIdsDryRunCount -ne $null) -and ($deadLetterReplayBatchByIdsSuccessIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchByIdsFailedIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchByIdsDryRunIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchByIdsRemainingCount -ne $null) -and (([int]$deadLetterReplayBatchByIdsProcessed) -eq 2) -and (([int]$deadLetterReplayBatchByIdsDryRunCount) -eq 2) -and ($deadLetterReplayBatchByIdsResults.Count -eq 2) -and (([int]$deadLetterReplayBatchByIdsEffectiveLimit) -le ([int]$deadLetterReplayBatchByIdsMaxLimit)) -and (([int]$deadLetterReplayBatchByIdsRequestedOffset) -eq 0) -and (([int]$deadLetterReplayBatchByIdsEffectiveOffset) -eq 0) -and (([int]$deadLetterReplayBatchByIdsNextOffset) -eq 2) -and (-not [bool]$deadLetterReplayBatchByIdsHasMore) -and (([int]$deadLetterReplayBatchByIdsTotalSelectedCount) -eq 2) -and (([bool]$deadLetterReplayBatchByIdsStopOnError) -eq $false) -and (([bool]$deadLetterReplayBatchByIdsStoppedOnError) -eq $false) -and ($deadLetterReplayBatchByIdsSuccessIds.Count -eq 0) -and ($deadLetterReplayBatchByIdsFailedIds.Count -eq 0) -and ($deadLetterReplayBatchByIdsDryRunIds.Count -eq 2) -and (([int]$deadLetterReplayBatchByIdsRemainingCount) -eq 0)
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(by-ids,dry-run)" -Passed $deadLetterReplayBatchByIdsValid -Detail ("code={0}; trace_id={1}; source={2}; processed={3}; requested_offset={4}; effective_offset={5}; next_offset={6}; has_more={7}; total_selected={8}; effective_limit={9}; max_limit={10}; truncated={11}; stop_on_error={12}; stopped_on_error={13}; dry_run_count={14}; remaining_count={15}; success_ids={16}; failed_ids={17}; dry_run_ids={18}; list_size={19}" -f $deadLetterReplayBatchByIdsResp.code, $deadLetterReplayBatchByIdsTraceId, $deadLetterReplayBatchByIdsSource, $deadLetterReplayBatchByIdsProcessed, $deadLetterReplayBatchByIdsRequestedOffset, $deadLetterReplayBatchByIdsEffectiveOffset, $deadLetterReplayBatchByIdsNextOffset, $deadLetterReplayBatchByIdsHasMore, $deadLetterReplayBatchByIdsTotalSelectedCount, $deadLetterReplayBatchByIdsEffectiveLimit, $deadLetterReplayBatchByIdsMaxLimit, $deadLetterReplayBatchByIdsTruncated, $deadLetterReplayBatchByIdsStopOnError, $deadLetterReplayBatchByIdsStoppedOnError, $deadLetterReplayBatchByIdsDryRunCount, $deadLetterReplayBatchByIdsRemainingCount, $deadLetterReplayBatchByIdsSuccessIds.Count, $deadLetterReplayBatchByIdsFailedIds.Count, $deadLetterReplayBatchByIdsDryRunIds.Count, ($(if ($deadLetterReplayBatchByIdsResults -is [System.Collections.IList]) { $deadLetterReplayBatchByIdsResults.Count } else { -1 })))

$deadLetterReplayBatchBodyIdsReq = @{
    dead_letter_ids = @(93, 92, 93)
}
$deadLetterReplayBatchBodyIdsResp = Invoke-ApiPostJson -Path "/api/inference/dead-letter/replay/batch?dry_run=1&limit=5" -BodyObj $deadLetterReplayBatchBodyIdsReq
$deadLetterReplayBatchBodyIdsData = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsResp -Name "data"
$deadLetterReplayBatchBodyIdsTraceId = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "trace_id"
$deadLetterReplayBatchBodyIdsSource = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "selection_source"
$deadLetterReplayBatchBodyIdsProcessed = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "processed_count"
$deadLetterReplayBatchBodyIdsRequestedOffset = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "requested_offset"
$deadLetterReplayBatchBodyIdsEffectiveOffset = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "effective_offset"
$deadLetterReplayBatchBodyIdsNextOffset = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "next_offset"
$deadLetterReplayBatchBodyIdsHasMore = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "has_more"
$deadLetterReplayBatchBodyIdsTotalSelectedCount = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "total_selected_count"
$deadLetterReplayBatchBodyIdsDryRunCount = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "dry_run_count"
$deadLetterReplayBatchBodyIdsSuccessIds = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "success_dead_letter_ids"
$deadLetterReplayBatchBodyIdsFailedIds = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "failed_dead_letter_ids"
$deadLetterReplayBatchBodyIdsDryRunIds = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "dry_run_dead_letter_ids"
$deadLetterReplayBatchBodyIdsRemainingCount = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "remaining_count"
$deadLetterReplayBatchBodyIdsResults = Get-PropValue -Obj $deadLetterReplayBatchBodyIdsData -Name "results"
$deadLetterReplayBatchBodyIdsValid = ($deadLetterReplayBatchBodyIdsResp.code -eq 0) -and ($deadLetterReplayBatchBodyIdsTraceId -ne $null) -and ($deadLetterReplayBatchBodyIdsTraceId -ne "") -and ($deadLetterReplayBatchBodyIdsSource -eq "explicit_ids") -and ($deadLetterReplayBatchBodyIdsResults -is [System.Collections.IList]) -and ($deadLetterReplayBatchBodyIdsProcessed -ne $null) -and ($deadLetterReplayBatchBodyIdsRequestedOffset -ne $null) -and ($deadLetterReplayBatchBodyIdsEffectiveOffset -ne $null) -and ($deadLetterReplayBatchBodyIdsNextOffset -ne $null) -and ($deadLetterReplayBatchBodyIdsHasMore -is [bool]) -and ($deadLetterReplayBatchBodyIdsTotalSelectedCount -ne $null) -and ($deadLetterReplayBatchBodyIdsDryRunCount -ne $null) -and ($deadLetterReplayBatchBodyIdsSuccessIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchBodyIdsFailedIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchBodyIdsDryRunIds -is [System.Collections.IList]) -and ($deadLetterReplayBatchBodyIdsRemainingCount -ne $null) -and (([int]$deadLetterReplayBatchBodyIdsProcessed) -eq 2) -and (([int]$deadLetterReplayBatchBodyIdsDryRunCount) -eq 2) -and (([int]$deadLetterReplayBatchBodyIdsRequestedOffset) -eq 0) -and (([int]$deadLetterReplayBatchBodyIdsEffectiveOffset) -eq 0) -and (([int]$deadLetterReplayBatchBodyIdsNextOffset) -eq 2) -and (-not [bool]$deadLetterReplayBatchBodyIdsHasMore) -and (([int]$deadLetterReplayBatchBodyIdsTotalSelectedCount) -eq 2) -and ($deadLetterReplayBatchBodyIdsResults.Count -eq 2) -and ($deadLetterReplayBatchBodyIdsSuccessIds.Count -eq 0) -and ($deadLetterReplayBatchBodyIdsFailedIds.Count -eq 0) -and ($deadLetterReplayBatchBodyIdsDryRunIds.Count -eq 2) -and (([int]$deadLetterReplayBatchBodyIdsRemainingCount) -eq 0)
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(body-ids,dry-run)" -Passed $deadLetterReplayBatchBodyIdsValid -Detail ("code={0}; trace_id={1}; source={2}; processed={3}; requested_offset={4}; effective_offset={5}; next_offset={6}; has_more={7}; total_selected={8}; dry_run_count={9}; remaining_count={10}; success_ids={11}; failed_ids={12}; dry_run_ids={13}; list_size={14}" -f $deadLetterReplayBatchBodyIdsResp.code, $deadLetterReplayBatchBodyIdsTraceId, $deadLetterReplayBatchBodyIdsSource, $deadLetterReplayBatchBodyIdsProcessed, $deadLetterReplayBatchBodyIdsRequestedOffset, $deadLetterReplayBatchBodyIdsEffectiveOffset, $deadLetterReplayBatchBodyIdsNextOffset, $deadLetterReplayBatchBodyIdsHasMore, $deadLetterReplayBatchBodyIdsTotalSelectedCount, $deadLetterReplayBatchBodyIdsDryRunCount, $deadLetterReplayBatchBodyIdsRemainingCount, $deadLetterReplayBatchBodyIdsSuccessIds.Count, $deadLetterReplayBatchBodyIdsFailedIds.Count, $deadLetterReplayBatchBodyIdsDryRunIds.Count, ($(if ($deadLetterReplayBatchBodyIdsResults -is [System.Collections.IList]) { $deadLetterReplayBatchBodyIdsResults.Count } else { -1 })))

$deadLetterReplayBatchBodyLimitReq = @{
    limit = 1
    dry_run = 1
    dead_letter_ids = @(96, 95)
}
$deadLetterReplayBatchBodyLimitResp = Invoke-ApiPostJson -Path "/api/inference/dead-letter/replay/batch?limit=5" -BodyObj $deadLetterReplayBatchBodyLimitReq
$deadLetterReplayBatchBodyLimitData = Get-PropValue -Obj $deadLetterReplayBatchBodyLimitResp -Name "data"
$deadLetterReplayBatchBodyLimitTraceId = Get-PropValue -Obj $deadLetterReplayBatchBodyLimitData -Name "trace_id"
$deadLetterReplayBatchBodyLimitRequested = Get-PropValue -Obj $deadLetterReplayBatchBodyLimitData -Name "requested_limit"
$deadLetterReplayBatchBodyLimitEffective = Get-PropValue -Obj $deadLetterReplayBatchBodyLimitData -Name "effective_limit"
$deadLetterReplayBatchBodyLimitProcessed = Get-PropValue -Obj $deadLetterReplayBatchBodyLimitData -Name "processed_count"
$deadLetterReplayBatchBodyLimitDryRunCount = Get-PropValue -Obj $deadLetterReplayBatchBodyLimitData -Name "dry_run_count"
$deadLetterReplayBatchBodyLimitValid = ($deadLetterReplayBatchBodyLimitResp.code -eq 0) -and ($deadLetterReplayBatchBodyLimitTraceId -ne $null) -and ($deadLetterReplayBatchBodyLimitTraceId -ne "") -and ($deadLetterReplayBatchBodyLimitRequested -ne $null) -and (([int]$deadLetterReplayBatchBodyLimitRequested) -eq 1) -and ($deadLetterReplayBatchBodyLimitEffective -ne $null) -and (([int]$deadLetterReplayBatchBodyLimitEffective) -eq 1) -and ($deadLetterReplayBatchBodyLimitProcessed -ne $null) -and (([int]$deadLetterReplayBatchBodyLimitProcessed) -eq 1) -and ($deadLetterReplayBatchBodyLimitDryRunCount -ne $null) -and (([int]$deadLetterReplayBatchBodyLimitDryRunCount) -eq 1)
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(body-limit-priority)" -Passed $deadLetterReplayBatchBodyLimitValid -Detail ("code={0}; trace_id={1}; requested_limit={2}; effective_limit={3}; processed={4}; dry_run_count={5}" -f $deadLetterReplayBatchBodyLimitResp.code, $deadLetterReplayBatchBodyLimitTraceId, $deadLetterReplayBatchBodyLimitRequested, $deadLetterReplayBatchBodyLimitEffective, $deadLetterReplayBatchBodyLimitProcessed, $deadLetterReplayBatchBodyLimitDryRunCount)

$deadLetterReplayBatchBodyStopReq = @{
    stop_on_error = 1
    dead_letter_ids = @(-1, -2)
}
$deadLetterReplayBatchBodyStopResp = Invoke-ApiPostJson -Path "/api/inference/dead-letter/replay/batch" -BodyObj $deadLetterReplayBatchBodyStopReq
$deadLetterReplayBatchBodyStopData = Get-PropValue -Obj $deadLetterReplayBatchBodyStopResp -Name "data"
$deadLetterReplayBatchBodyStopTraceId = Get-PropValue -Obj $deadLetterReplayBatchBodyStopData -Name "trace_id"
$deadLetterReplayBatchBodyStopEnabled = Get-PropValue -Obj $deadLetterReplayBatchBodyStopData -Name "stop_on_error"
$deadLetterReplayBatchBodyStopStopped = Get-PropValue -Obj $deadLetterReplayBatchBodyStopData -Name "stopped_on_error"
$deadLetterReplayBatchBodyStopSelected = Get-PropValue -Obj $deadLetterReplayBatchBodyStopData -Name "selected_count"
$deadLetterReplayBatchBodyStopProcessed = Get-PropValue -Obj $deadLetterReplayBatchBodyStopData -Name "processed_count"
$deadLetterReplayBatchBodyStopFailed = Get-PropValue -Obj $deadLetterReplayBatchBodyStopData -Name "failed_count"
$deadLetterReplayBatchBodyStopRemaining = Get-PropValue -Obj $deadLetterReplayBatchBodyStopData -Name "remaining_count"
$deadLetterReplayBatchBodyStopValid = ($deadLetterReplayBatchBodyStopResp.code -eq 0) -and ($deadLetterReplayBatchBodyStopTraceId -ne $null) -and ($deadLetterReplayBatchBodyStopTraceId -ne "") -and ($deadLetterReplayBatchBodyStopEnabled -is [bool]) -and ([bool]$deadLetterReplayBatchBodyStopEnabled) -and ($deadLetterReplayBatchBodyStopStopped -is [bool]) -and ([bool]$deadLetterReplayBatchBodyStopStopped) -and ($deadLetterReplayBatchBodyStopSelected -ne $null) -and (([int]$deadLetterReplayBatchBodyStopSelected) -eq 2) -and ($deadLetterReplayBatchBodyStopProcessed -ne $null) -and (([int]$deadLetterReplayBatchBodyStopProcessed) -eq 1) -and ($deadLetterReplayBatchBodyStopFailed -ne $null) -and (([int]$deadLetterReplayBatchBodyStopFailed) -eq 1) -and ($deadLetterReplayBatchBodyStopRemaining -ne $null) -and (([int]$deadLetterReplayBatchBodyStopRemaining) -eq 1)
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(body-stop-on-error)" -Passed $deadLetterReplayBatchBodyStopValid -Detail ("code={0}; trace_id={1}; stop_on_error={2}; stopped_on_error={3}; selected={4}; processed={5}; failed={6}; remaining={7}" -f $deadLetterReplayBatchBodyStopResp.code, $deadLetterReplayBatchBodyStopTraceId, $deadLetterReplayBatchBodyStopEnabled, $deadLetterReplayBatchBodyStopStopped, $deadLetterReplayBatchBodyStopSelected, $deadLetterReplayBatchBodyStopProcessed, $deadLetterReplayBatchBodyStopFailed, $deadLetterReplayBatchBodyStopRemaining)

$deadLetterReplayBatchOffsetResp = Invoke-ApiGet -Path "/api/inference/dead-letter/replay/batch?dead_letter_ids=601,602,603,604&dry_run=1&limit=2&offset=2"
$deadLetterReplayBatchOffsetData = Get-PropValue -Obj $deadLetterReplayBatchOffsetResp -Name "data"
$deadLetterReplayBatchOffsetTraceId = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "trace_id"
$deadLetterReplayBatchOffsetProcessed = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "processed_count"
$deadLetterReplayBatchOffsetDryRunCount = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "dry_run_count"
$deadLetterReplayBatchOffsetRequested = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "requested_offset"
$deadLetterReplayBatchOffsetEffective = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "effective_offset"
$deadLetterReplayBatchOffsetNext = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "next_offset"
$deadLetterReplayBatchOffsetHasMore = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "has_more"
$deadLetterReplayBatchOffsetTotal = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "total_selected_count"
$deadLetterReplayBatchOffsetResults = Get-PropValue -Obj $deadLetterReplayBatchOffsetData -Name "results"
$deadLetterReplayBatchOffsetFirst = $null
if ($deadLetterReplayBatchOffsetResults -is [System.Collections.IList] -and $deadLetterReplayBatchOffsetResults.Count -gt 0) {
    $deadLetterReplayBatchOffsetFirst = Get-PropValue -Obj $deadLetterReplayBatchOffsetResults[0] -Name "dead_letter_id"
}
$deadLetterReplayBatchOffsetValid = ($deadLetterReplayBatchOffsetResp.code -eq 0) -and ($deadLetterReplayBatchOffsetTraceId -ne $null) -and ($deadLetterReplayBatchOffsetTraceId -ne "") -and ($deadLetterReplayBatchOffsetProcessed -ne $null) -and (([int]$deadLetterReplayBatchOffsetProcessed) -eq 2) -and ($deadLetterReplayBatchOffsetDryRunCount -ne $null) -and (([int]$deadLetterReplayBatchOffsetDryRunCount) -eq 2) -and ($deadLetterReplayBatchOffsetRequested -ne $null) -and (([int]$deadLetterReplayBatchOffsetRequested) -eq 2) -and ($deadLetterReplayBatchOffsetEffective -ne $null) -and (([int]$deadLetterReplayBatchOffsetEffective) -eq 2) -and ($deadLetterReplayBatchOffsetNext -ne $null) -and (([int]$deadLetterReplayBatchOffsetNext) -eq 4) -and ($deadLetterReplayBatchOffsetHasMore -is [bool]) -and (-not [bool]$deadLetterReplayBatchOffsetHasMore) -and ($deadLetterReplayBatchOffsetTotal -ne $null) -and (([int]$deadLetterReplayBatchOffsetTotal) -eq 4) -and ($deadLetterReplayBatchOffsetResults -is [System.Collections.IList]) -and ($deadLetterReplayBatchOffsetResults.Count -eq 2) -and ($deadLetterReplayBatchOffsetFirst -ne $null) -and (([long]$deadLetterReplayBatchOffsetFirst) -eq 603)
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(offset-window,dry-run)" -Passed $deadLetterReplayBatchOffsetValid -Detail ("code={0}; trace_id={1}; processed={2}; dry_run_count={3}; requested_offset={4}; effective_offset={5}; next_offset={6}; has_more={7}; total_selected={8}; first_dead_letter_id={9}; list_size={10}" -f $deadLetterReplayBatchOffsetResp.code, $deadLetterReplayBatchOffsetTraceId, $deadLetterReplayBatchOffsetProcessed, $deadLetterReplayBatchOffsetDryRunCount, $deadLetterReplayBatchOffsetRequested, $deadLetterReplayBatchOffsetEffective, $deadLetterReplayBatchOffsetNext, $deadLetterReplayBatchOffsetHasMore, $deadLetterReplayBatchOffsetTotal, $deadLetterReplayBatchOffsetFirst, ($(if ($deadLetterReplayBatchOffsetResults -is [System.Collections.IList]) { $deadLetterReplayBatchOffsetResults.Count } else { -1 })))

$deadLetterReplayBatchStrictResumeReq = @{
    strict_resume = 1
    expected_total_selected_count = 999
    dead_letter_ids = @(811, 812)
    dry_run = 1
}
$deadLetterReplayBatchStrictResumeResp = Invoke-ApiPostJson -Path "/api/inference/dead-letter/replay/batch?limit=2" -BodyObj $deadLetterReplayBatchStrictResumeReq
$deadLetterReplayBatchStrictResumeData = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeResp -Name "data"
$deadLetterReplayBatchStrictResumeTraceId = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeData -Name "trace_id"
$deadLetterReplayBatchStrictResumeEnabled = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeData -Name "strict_resume"
$deadLetterReplayBatchStrictResumeExpected = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeData -Name "expected_total_selected_count"
$deadLetterReplayBatchStrictResumeActual = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeData -Name "actual_total_selected_count"
$deadLetterReplayBatchStrictResumeValid = ($deadLetterReplayBatchStrictResumeResp.code -ne 0) -and ($deadLetterReplayBatchStrictResumeTraceId -ne $null) -and ($deadLetterReplayBatchStrictResumeTraceId -ne "") -and ($deadLetterReplayBatchStrictResumeEnabled -is [bool]) -and ([bool]$deadLetterReplayBatchStrictResumeEnabled) -and ($deadLetterReplayBatchStrictResumeExpected -ne $null) -and (([int]$deadLetterReplayBatchStrictResumeExpected) -eq 999) -and ($deadLetterReplayBatchStrictResumeActual -ne $null) -and (([int]$deadLetterReplayBatchStrictResumeActual) -eq 2)
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(strict-resume-mismatch)" -Passed $deadLetterReplayBatchStrictResumeValid -Detail ("code={0}; trace_id={1}; strict_resume={2}; expected_total={3}; actual_total={4}" -f $deadLetterReplayBatchStrictResumeResp.code, $deadLetterReplayBatchStrictResumeTraceId, $deadLetterReplayBatchStrictResumeEnabled, $deadLetterReplayBatchStrictResumeExpected, $deadLetterReplayBatchStrictResumeActual)

$deadLetterReplayBatchStrictResumeQueryResp = Invoke-ApiGet -Path "/api/inference/dead-letter/replay/batch?dead_letter_ids=821,822&dry_run=1&limit=2&strict_resume=1&expected_total_selected_count=999"
$deadLetterReplayBatchStrictResumeQueryData = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeQueryResp -Name "data"
$deadLetterReplayBatchStrictResumeQueryTraceId = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeQueryData -Name "trace_id"
$deadLetterReplayBatchStrictResumeQueryEnabled = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeQueryData -Name "strict_resume"
$deadLetterReplayBatchStrictResumeQueryExpected = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeQueryData -Name "expected_total_selected_count"
$deadLetterReplayBatchStrictResumeQueryActual = Get-PropValue -Obj $deadLetterReplayBatchStrictResumeQueryData -Name "actual_total_selected_count"
$deadLetterReplayBatchStrictResumeQueryValid = ($deadLetterReplayBatchStrictResumeQueryResp.code -ne 0) -and ($deadLetterReplayBatchStrictResumeQueryTraceId -ne $null) -and ($deadLetterReplayBatchStrictResumeQueryTraceId -ne "") -and ($deadLetterReplayBatchStrictResumeQueryEnabled -is [bool]) -and ([bool]$deadLetterReplayBatchStrictResumeQueryEnabled) -and ($deadLetterReplayBatchStrictResumeQueryExpected -ne $null) -and (([int]$deadLetterReplayBatchStrictResumeQueryExpected) -eq 999) -and ($deadLetterReplayBatchStrictResumeQueryActual -ne $null) -and (([int]$deadLetterReplayBatchStrictResumeQueryActual) -eq 2)
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(strict-resume-query-mismatch)" -Passed $deadLetterReplayBatchStrictResumeQueryValid -Detail ("code={0}; trace_id={1}; strict_resume={2}; expected_total={3}; actual_total={4}" -f $deadLetterReplayBatchStrictResumeQueryResp.code, $deadLetterReplayBatchStrictResumeQueryTraceId, $deadLetterReplayBatchStrictResumeQueryEnabled, $deadLetterReplayBatchStrictResumeQueryExpected, $deadLetterReplayBatchStrictResumeQueryActual)

$deadLetterReplayBatchFingerprintReq = @{
    strict_resume = 1
    expected_total_selected_count = 2
    expected_window_fingerprint = "deadbeef"
    dead_letter_ids = @(831, 832)
    dry_run = 1
}
$deadLetterReplayBatchFingerprintResp = Invoke-ApiPostJson -Path "/api/inference/dead-letter/replay/batch?limit=2" -BodyObj $deadLetterReplayBatchFingerprintReq
$deadLetterReplayBatchFingerprintData = Get-PropValue -Obj $deadLetterReplayBatchFingerprintResp -Name "data"
$deadLetterReplayBatchFingerprintTraceId = Get-PropValue -Obj $deadLetterReplayBatchFingerprintData -Name "trace_id"
$deadLetterReplayBatchFingerprintEnabled = Get-PropValue -Obj $deadLetterReplayBatchFingerprintData -Name "strict_resume"
$deadLetterReplayBatchFingerprintExpected = Get-PropValue -Obj $deadLetterReplayBatchFingerprintData -Name "expected_window_fingerprint"
$deadLetterReplayBatchFingerprintActual = Get-PropValue -Obj $deadLetterReplayBatchFingerprintData -Name "actual_window_fingerprint"
$deadLetterReplayBatchFingerprintValid = ($deadLetterReplayBatchFingerprintResp.code -ne 0) -and ($deadLetterReplayBatchFingerprintTraceId -ne $null) -and ($deadLetterReplayBatchFingerprintTraceId -ne "") -and ($deadLetterReplayBatchFingerprintEnabled -is [bool]) -and ([bool]$deadLetterReplayBatchFingerprintEnabled) -and ($deadLetterReplayBatchFingerprintExpected -eq "deadbeef") -and ($deadLetterReplayBatchFingerprintActual -ne $null) -and ($deadLetterReplayBatchFingerprintActual -ne "") -and (([string]$deadLetterReplayBatchFingerprintActual).Length -eq 64) -and ($deadLetterReplayBatchFingerprintActual -ne "deadbeef")
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(strict-resume-fingerprint-mismatch)" -Passed $deadLetterReplayBatchFingerprintValid -Detail ("code={0}; trace_id={1}; strict_resume={2}; expected_window={3}; actual_window={4}" -f $deadLetterReplayBatchFingerprintResp.code, $deadLetterReplayBatchFingerprintTraceId, $deadLetterReplayBatchFingerprintEnabled, $deadLetterReplayBatchFingerprintExpected, $deadLetterReplayBatchFingerprintActual)

$testReq = @{
    camera_id = $CameraId
    model_id = $ModelId
    frame = @{
        source = $Source
        timestamp_ms = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    }
    roi = @()
}

$testResp = Invoke-ApiPostJson -Path "/api/inference/test" -BodyObj $testReq
$testData = Get-PropValue -Obj $testResp -Name "data"
$testTraceId = Get-PropValue -Obj $testData -Name "trace_id"
$testBackend = Get-PropValue -Obj $testData -Name "backend_type"
$testResult = Get-PropValue -Obj $testData -Name "result"
$testResultTraceId = Get-PropValue -Obj $testResult -Name "trace_id"
$checks += New-CheckResult -Api "/api/inference/test" -Passed (($testResp.code -eq 0) -and ($testTraceId -ne $null) -and ($testTraceId -ne "") -and ($testBackend -ne $null) -and ($testBackend -ne "") -and ($testResult -ne $null) -and ($testResultTraceId -ne $null) -and ($testResultTraceId -ne "") -and (Is-ExpectedBackend -ActualBackend $testBackend)) -Detail ("code={0}; trace_id={1}; backend_type={2}; expected_backend={3}; result_trace_id={4}" -f $testResp.code, $testTraceId, $testBackend, $ExpectedBackendType, $testResultTraceId)

$dispatchReq = @{
    trace_id = ("contract-" + [Guid]::NewGuid().ToString("N"))
    camera_id = $CameraId
    model_id = $ModelId
    algorithm_id = $AlgorithmId
    persist_report = 0
    frame = @{
        source = $Source
        timestamp_ms = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    }
    roi = @()
}

$dispatchResp = Invoke-ApiPostJson -Path "/api/inference/dispatch" -BodyObj $dispatchReq
$dispatchData = Get-PropValue -Obj $dispatchResp -Name "data"
$dispatchTraceId = Get-PropValue -Obj $dispatchData -Name "trace_id"
$dispatchBackend = Get-PropValue -Obj $dispatchData -Name "backend_type"
$dispatchResult = Get-PropValue -Obj $dispatchData -Name "result"
$dispatchReport = Get-PropValue -Obj $dispatchData -Name "report"
$dispatchReportStatus = Get-PropValue -Obj $dispatchReport -Name "status"
$dispatchIdempotent = Get-PropValue -Obj $dispatchData -Name "idempotent"
$dispatchIdempotentStatus = Get-PropValue -Obj $dispatchIdempotent -Name "status"
$checks += New-CheckResult -Api "/api/inference/dispatch" -Passed (($dispatchResp.code -eq 0) -and ($dispatchTraceId -ne $null) -and ($dispatchTraceId -ne "") -and ($dispatchBackend -ne $null) -and ($dispatchBackend -ne "") -and ($dispatchResult -ne $null) -and ($dispatchReport -ne $null) -and ($dispatchReportStatus -ne $null) -and ($dispatchReportStatus -ne "") -and ($dispatchIdempotent -ne $null) -and ($dispatchIdempotentStatus -ne $null) -and ($dispatchIdempotentStatus -ne "") -and (Is-ExpectedBackend -ActualBackend $dispatchBackend)) -Detail ("code={0}; trace_id={1}; backend_type={2}; expected_backend={3}; report_status={4}; idem_status={5}" -f $dispatchResp.code, $dispatchTraceId, $dispatchBackend, $ExpectedBackendType, $dispatchReportStatus, $dispatchIdempotentStatus)

$routeReq = @{
    camera_id = $CameraId
}

$routeResp = Invoke-ApiPostJson -Path "/api/inference/route" -BodyObj $routeReq
$routeData = Get-PropValue -Obj $routeResp -Name "data"
$routeTraceId = Get-PropValue -Obj $routeData -Name "trace_id"
$routeCameraId = Get-PropValue -Obj $routeData -Name "camera_id"
$routeBackend = Get-PropValue -Obj $routeData -Name "backend_type"
$routeGlobalBackend = Get-PropValue -Obj $routeData -Name "global_backend_type"
$routeOverrideSource = Get-PropValue -Obj $routeData -Name "override_source"
$checks += New-CheckResult -Api "/api/inference/route" -Passed (($routeResp.code -eq 0) -and ($routeTraceId -ne $null) -and ($routeTraceId -ne "") -and ($routeCameraId -ne $null) -and (([long]$routeCameraId) -eq $CameraId) -and ($routeBackend -ne $null) -and ($routeBackend -ne "") -and ($routeGlobalBackend -ne $null) -and ($routeGlobalBackend -ne "") -and (Is-ExpectedBackend -ActualBackend $routeBackend) -and (Is-ExpectedOverrideSource -ActualSource $routeOverrideSource)) -Detail ("code={0}; trace_id={1}; camera_id={2}; backend_type={3}; global_backend_type={4}; override_source={5}; expected_backend={6}; expected_override_source={7}" -f $routeResp.code, $routeTraceId, $routeCameraId, $routeBackend, $routeGlobalBackend, $routeOverrideSource, $ExpectedBackendType, $ExpectedOverrideSource)

$routeBatchReq = @{
    camera_ids = @($CameraId)
}

$routeBatchResp = Invoke-ApiPostJson -Path "/api/inference/route/batch" -BodyObj $routeBatchReq
$routeBatchData = Get-PropValue -Obj $routeBatchResp -Name "data"
$routeBatchTraceId = Get-PropValue -Obj $routeBatchData -Name "trace_id"
$routeBatchGlobalBackend = Get-PropValue -Obj $routeBatchData -Name "global_backend_type"
$routeBatchList = Get-PropValue -Obj $routeBatchData -Name "route_list"
$routeBatchTruncated = Get-PropValue -Obj $routeBatchData -Name "truncated"
$routeBatchMaxCameraIds = Get-PropValue -Obj $routeBatchData -Name "max_camera_ids"
$routeBatchMaxCameraIdsCap = Get-PropValue -Obj $routeBatchData -Name "max_camera_ids_cap"
$routeBatchDefaultFallbackUsed = Get-PropValue -Obj $routeBatchData -Name "default_fallback_used"
$routeBatchResolvedCameraCount = Get-PropValue -Obj $routeBatchData -Name "resolved_camera_count"
$routeBatchInputTokenCount = Get-PropValue -Obj $routeBatchData -Name "input_token_count"
$routeBatchExpandedCandidateCount = Get-PropValue -Obj $routeBatchData -Name "expanded_candidate_count"
$routeBatchInvalidTokenCount = Get-PropValue -Obj $routeBatchData -Name "invalid_token_count"
$routeBatchDuplicateFilteredCount = Get-PropValue -Obj $routeBatchData -Name "duplicate_filtered_count"
$routeBatchHitSources = Get-PropValue -Obj $routeBatchData -Name "hit_sources"
$routeBatchTruncatedSource = Get-PropValue -Obj $routeBatchData -Name "truncated_source"
$routeBatchSourceStats = Get-PropValue -Obj $routeBatchData -Name "source_stats"
$routeBatchBodyCameraIdsStats = Get-PropValue -Obj $routeBatchSourceStats -Name "body_camera_ids"
$routeBatchBodyCameraIdsUniqueAdded = Get-PropValue -Obj $routeBatchBodyCameraIdsStats -Name "unique_added_count"
$routeBatchFirst = $null
if ($routeBatchList -is [System.Collections.IList] -and $routeBatchList.Count -gt 0) {
    $routeBatchFirst = $routeBatchList[0]
}
$routeBatchFirstCameraId = Get-PropValue -Obj $routeBatchFirst -Name "camera_id"
$routeBatchFirstBackend = Get-PropValue -Obj $routeBatchFirst -Name "backend_type"
$routeBatchFirstHitSource = $null
if ($routeBatchHitSources -is [System.Collections.IList] -and $routeBatchHitSources.Count -gt 0) {
    $routeBatchFirstHitSource = $routeBatchHitSources[0]
}
$checks += New-CheckResult -Api "/api/inference/route/batch" -Passed (($routeBatchResp.code -eq 0) -and ($routeBatchTraceId -ne $null) -and ($routeBatchTraceId -ne "") -and ($routeBatchGlobalBackend -ne $null) -and ($routeBatchGlobalBackend -ne "") -and ($routeBatchList -is [System.Collections.IList]) -and ($routeBatchList.Count -gt 0) -and ($routeBatchFirstCameraId -ne $null) -and (([long]$routeBatchFirstCameraId) -eq $CameraId) -and ($routeBatchFirstBackend -ne $null) -and ($routeBatchFirstBackend -ne "") -and ($routeBatchTruncated -is [bool]) -and (([int]$routeBatchMaxCameraIds) -ge 1) -and (([int]$routeBatchMaxCameraIds) -le 500) -and (([int]$routeBatchMaxCameraIdsCap) -eq 500) -and ($routeBatchDefaultFallbackUsed -is [bool]) -and ($routeBatchDefaultFallbackUsed -eq $false) -and ($routeBatchResolvedCameraCount -ne $null) -and (([int]$routeBatchResolvedCameraCount) -eq $routeBatchList.Count) -and ($routeBatchInputTokenCount -ne $null) -and (([int]$routeBatchInputTokenCount) -ge 0) -and ($routeBatchExpandedCandidateCount -ne $null) -and (([int]$routeBatchExpandedCandidateCount) -ge ([int]$routeBatchResolvedCameraCount)) -and ($routeBatchInvalidTokenCount -ne $null) -and (([int]$routeBatchInvalidTokenCount) -ge 0) -and ($routeBatchDuplicateFilteredCount -ne $null) -and (([int]$routeBatchDuplicateFilteredCount) -ge 0) -and ($routeBatchHitSources -is [System.Collections.IList]) -and ($routeBatchHitSources.Count -ge 1) -and ($routeBatchFirstHitSource -eq "body_camera_ids") -and ($routeBatchTruncatedSource -eq $null) -and ($routeBatchSourceStats -ne $null) -and ($routeBatchBodyCameraIdsStats -ne $null) -and ($routeBatchBodyCameraIdsUniqueAdded -ne $null) -and (([int]$routeBatchBodyCameraIdsUniqueAdded) -ge 1) -and (Is-ExpectedBackend -ActualBackend $routeBatchFirstBackend)) -Detail ("code={0}; trace_id={1}; global_backend_type={2}; first_camera_id={3}; first_backend_type={4}; truncated={5}; max_camera_ids={6}; max_camera_ids_cap={7}; default_fallback_used={8}; resolved_camera_count={9}; input_token_count={10}; expanded_candidate_count={11}; invalid_token_count={12}; duplicate_filtered_count={13}; hit_sources={14}; truncated_source={15}; body_camera_ids_unique_added={16}; expected_backend={17}" -f $routeBatchResp.code, $routeBatchTraceId, $routeBatchGlobalBackend, $routeBatchFirstCameraId, $routeBatchFirstBackend, $routeBatchTruncated, $routeBatchMaxCameraIds, $routeBatchMaxCameraIdsCap, $routeBatchDefaultFallbackUsed, $routeBatchResolvedCameraCount, $routeBatchInputTokenCount, $routeBatchExpandedCandidateCount, $routeBatchInvalidTokenCount, $routeBatchDuplicateFilteredCount, (($routeBatchHitSources -join ",")), $routeBatchTruncatedSource, $routeBatchBodyCameraIdsUniqueAdded, $ExpectedBackendType)

$nextCameraId = $CameraId + 1
$routeBatchRangeReq = @{
    camera_ids = ("{0}-{1},{0}" -f $CameraId, $nextCameraId)
}
$routeBatchRangeResp = Invoke-ApiPostJson -Path "/api/inference/route/batch" -BodyObj $routeBatchRangeReq
$routeBatchRangeData = Get-PropValue -Obj $routeBatchRangeResp -Name "data"
$routeBatchRangeTraceId = Get-PropValue -Obj $routeBatchRangeData -Name "trace_id"
$routeBatchRangeList = Get-PropValue -Obj $routeBatchRangeData -Name "route_list"
$routeBatchRangeTruncated = Get-PropValue -Obj $routeBatchRangeData -Name "truncated"
$routeBatchRangeMaxCameraIds = Get-PropValue -Obj $routeBatchRangeData -Name "max_camera_ids"
$routeBatchRangeMaxCameraIdsCap = Get-PropValue -Obj $routeBatchRangeData -Name "max_camera_ids_cap"
$routeBatchRangeResolvedCameraCount = Get-PropValue -Obj $routeBatchRangeData -Name "resolved_camera_count"
$routeBatchRangeHitSources = Get-PropValue -Obj $routeBatchRangeData -Name "hit_sources"
$routeBatchRangeTruncatedSource = Get-PropValue -Obj $routeBatchRangeData -Name "truncated_source"
$routeBatchRangeSourceStats = Get-PropValue -Obj $routeBatchRangeData -Name "source_stats"
$routeBatchRangeFirst = $null
$routeBatchRangeSecond = $null
if ($routeBatchRangeList -is [System.Collections.IList] -and $routeBatchRangeList.Count -gt 0) {
    $routeBatchRangeFirst = $routeBatchRangeList[0]
}
if ($routeBatchRangeList -is [System.Collections.IList] -and $routeBatchRangeList.Count -gt 1) {
    $routeBatchRangeSecond = $routeBatchRangeList[1]
}
$routeBatchRangeFirstCameraId = Get-PropValue -Obj $routeBatchRangeFirst -Name "camera_id"
$routeBatchRangeSecondCameraId = Get-PropValue -Obj $routeBatchRangeSecond -Name "camera_id"
$checks += New-CheckResult -Api "/api/inference/route/batch(range)" -Passed (($routeBatchRangeResp.code -eq 0) -and ($routeBatchRangeTraceId -ne $null) -and ($routeBatchRangeTraceId -ne "") -and ($routeBatchRangeList -is [System.Collections.IList]) -and ($routeBatchRangeList.Count -ge 2) -and ($routeBatchRangeFirstCameraId -ne $null) -and ($routeBatchRangeSecondCameraId -ne $null) -and (([long]$routeBatchRangeFirstCameraId) -eq $CameraId) -and (([long]$routeBatchRangeSecondCameraId) -eq $nextCameraId) -and ($routeBatchRangeTruncated -is [bool]) -and (([int]$routeBatchRangeMaxCameraIds) -ge 1) -and (([int]$routeBatchRangeMaxCameraIds) -le 500) -and (([int]$routeBatchRangeMaxCameraIdsCap) -eq 500) -and ($routeBatchRangeResolvedCameraCount -ne $null) -and (([int]$routeBatchRangeResolvedCameraCount) -eq $routeBatchRangeList.Count) -and ($routeBatchRangeHitSources -is [System.Collections.IList]) -and ($routeBatchRangeHitSources.Count -ge 1) -and ($routeBatchRangeTruncatedSource -eq $null) -and ($routeBatchRangeSourceStats -ne $null) -and (Get-PropValue -Obj $routeBatchRangeSourceStats -Name "body_camera_ids")) -Detail ("code={0}; trace_id={1}; first_camera_id={2}; second_camera_id={3}; truncated={4}; max_camera_ids={5}; max_camera_ids_cap={6}; resolved_camera_count={7}; hit_sources={8}; truncated_source={9}; source_stats_keys={10}; expected_first={11}; expected_second={12}" -f $routeBatchRangeResp.code, $routeBatchRangeTraceId, $routeBatchRangeFirstCameraId, $routeBatchRangeSecondCameraId, $routeBatchRangeTruncated, $routeBatchRangeMaxCameraIds, $routeBatchRangeMaxCameraIdsCap, $routeBatchRangeResolvedCameraCount, (($routeBatchRangeHitSources -join ",")), $routeBatchRangeTruncatedSource, (($routeBatchRangeSourceStats.PSObject.Properties.Name -join ",")), $CameraId, $nextCameraId)

$routeBatchAliasReq = @{
    cameras = @($CameraId, $nextCameraId)
}
$routeBatchAliasResp = Invoke-ApiPostJson -Path "/api/inference/route/batch" -BodyObj $routeBatchAliasReq
$routeBatchAliasData = Get-PropValue -Obj $routeBatchAliasResp -Name "data"
$routeBatchAliasTraceId = Get-PropValue -Obj $routeBatchAliasData -Name "trace_id"
$routeBatchAliasList = Get-PropValue -Obj $routeBatchAliasData -Name "route_list"
$routeBatchAliasFirst = $null
$routeBatchAliasSecond = $null
if ($routeBatchAliasList -is [System.Collections.IList] -and $routeBatchAliasList.Count -gt 0) {
    $routeBatchAliasFirst = $routeBatchAliasList[0]
}
if ($routeBatchAliasList -is [System.Collections.IList] -and $routeBatchAliasList.Count -gt 1) {
    $routeBatchAliasSecond = $routeBatchAliasList[1]
}
$routeBatchAliasFirstCameraId = Get-PropValue -Obj $routeBatchAliasFirst -Name "camera_id"
$routeBatchAliasSecondCameraId = Get-PropValue -Obj $routeBatchAliasSecond -Name "camera_id"
$checks += New-CheckResult -Api "/api/inference/route/batch(cameras-alias)" -Passed (($routeBatchAliasResp.code -eq 0) -and ($routeBatchAliasTraceId -ne $null) -and ($routeBatchAliasTraceId -ne "") -and ($routeBatchAliasList -is [System.Collections.IList]) -and ($routeBatchAliasList.Count -ge 2) -and (([long]$routeBatchAliasFirstCameraId) -eq $CameraId) -and (([long]$routeBatchAliasSecondCameraId) -eq $nextCameraId)) -Detail ("code={0}; trace_id={1}; first_camera_id={2}; second_camera_id={3}; expected_first={4}; expected_second={5}" -f $routeBatchAliasResp.code, $routeBatchAliasTraceId, $routeBatchAliasFirstCameraId, $routeBatchAliasSecondCameraId, $CameraId, $nextCameraId)

$routeBatchQueryAliasResp = Invoke-ApiGet -Path ("/api/inference/route/batch?cameras={0}%2C{1}" -f $CameraId, $nextCameraId)
$routeBatchQueryAliasData = Get-PropValue -Obj $routeBatchQueryAliasResp -Name "data"
$routeBatchQueryAliasTraceId = Get-PropValue -Obj $routeBatchQueryAliasData -Name "trace_id"
$routeBatchQueryAliasList = Get-PropValue -Obj $routeBatchQueryAliasData -Name "route_list"
$routeBatchQueryAliasFirst = $null
$routeBatchQueryAliasSecond = $null
if ($routeBatchQueryAliasList -is [System.Collections.IList] -and $routeBatchQueryAliasList.Count -gt 0) {
    $routeBatchQueryAliasFirst = $routeBatchQueryAliasList[0]
}
if ($routeBatchQueryAliasList -is [System.Collections.IList] -and $routeBatchQueryAliasList.Count -gt 1) {
    $routeBatchQueryAliasSecond = $routeBatchQueryAliasList[1]
}
$routeBatchQueryAliasFirstCameraId = Get-PropValue -Obj $routeBatchQueryAliasFirst -Name "camera_id"
$routeBatchQueryAliasSecondCameraId = Get-PropValue -Obj $routeBatchQueryAliasSecond -Name "camera_id"
$checks += New-CheckResult -Api "/api/inference/route/batch(query-cameras-alias)" -Passed (($routeBatchQueryAliasResp.code -eq 0) -and ($routeBatchQueryAliasTraceId -ne $null) -and ($routeBatchQueryAliasTraceId -ne "") -and ($routeBatchQueryAliasList -is [System.Collections.IList]) -and ($routeBatchQueryAliasList.Count -ge 2) -and (([long]$routeBatchQueryAliasFirstCameraId) -eq $CameraId) -and (([long]$routeBatchQueryAliasSecondCameraId) -eq $nextCameraId)) -Detail ("code={0}; trace_id={1}; first_camera_id={2}; second_camera_id={3}; expected_first={4}; expected_second={5}" -f $routeBatchQueryAliasResp.code, $routeBatchQueryAliasTraceId, $routeBatchQueryAliasFirstCameraId, $routeBatchQueryAliasSecondCameraId, $CameraId, $nextCameraId)

$routeBatchQueryRangeResp = Invoke-ApiGet -Path ("/api/inference/route/batch?camera_range={1}-{0}" -f $CameraId, $nextCameraId)
$routeBatchQueryRangeData = Get-PropValue -Obj $routeBatchQueryRangeResp -Name "data"
$routeBatchQueryRangeTraceId = Get-PropValue -Obj $routeBatchQueryRangeData -Name "trace_id"
$routeBatchQueryRangeList = Get-PropValue -Obj $routeBatchQueryRangeData -Name "route_list"
$routeBatchQueryRangeFirst = $null
$routeBatchQueryRangeSecond = $null
if ($routeBatchQueryRangeList -is [System.Collections.IList] -and $routeBatchQueryRangeList.Count -gt 0) {
    $routeBatchQueryRangeFirst = $routeBatchQueryRangeList[0]
}
if ($routeBatchQueryRangeList -is [System.Collections.IList] -and $routeBatchQueryRangeList.Count -gt 1) {
    $routeBatchQueryRangeSecond = $routeBatchQueryRangeList[1]
}
$routeBatchQueryRangeFirstCameraId = Get-PropValue -Obj $routeBatchQueryRangeFirst -Name "camera_id"
$routeBatchQueryRangeSecondCameraId = Get-PropValue -Obj $routeBatchQueryRangeSecond -Name "camera_id"
$checks += New-CheckResult -Api "/api/inference/route/batch(query-camera_range-alias)" -Passed (($routeBatchQueryRangeResp.code -eq 0) -and ($routeBatchQueryRangeTraceId -ne $null) -and ($routeBatchQueryRangeTraceId -ne "") -and ($routeBatchQueryRangeList -is [System.Collections.IList]) -and ($routeBatchQueryRangeList.Count -ge 2) -and (([long]$routeBatchQueryRangeFirstCameraId) -eq $nextCameraId) -and (([long]$routeBatchQueryRangeSecondCameraId) -eq $CameraId)) -Detail ("code={0}; trace_id={1}; first_camera_id={2}; second_camera_id={3}; expected_first={4}; expected_second={5}" -f $routeBatchQueryRangeResp.code, $routeBatchQueryRangeTraceId, $routeBatchQueryRangeFirstCameraId, $routeBatchQueryRangeSecondCameraId, $nextCameraId, $CameraId)

$routeBatchFallbackResp = Invoke-ApiGet -Path "/api/inference/route/batch?camera_ids=,,"
$routeBatchFallbackData = Get-PropValue -Obj $routeBatchFallbackResp -Name "data"
$routeBatchFallbackTraceId = Get-PropValue -Obj $routeBatchFallbackData -Name "trace_id"
$routeBatchFallbackList = Get-PropValue -Obj $routeBatchFallbackData -Name "route_list"
$routeBatchFallbackDefaultUsed = Get-PropValue -Obj $routeBatchFallbackData -Name "default_fallback_used"
$routeBatchFallbackExpandedCandidateCount = Get-PropValue -Obj $routeBatchFallbackData -Name "expanded_candidate_count"
$routeBatchFallbackHitSources = Get-PropValue -Obj $routeBatchFallbackData -Name "hit_sources"
$routeBatchFallbackTruncatedSource = Get-PropValue -Obj $routeBatchFallbackData -Name "truncated_source"
$routeBatchFallbackSourceStats = Get-PropValue -Obj $routeBatchFallbackData -Name "source_stats"
$routeBatchFallbackFirst = $null
if ($routeBatchFallbackList -is [System.Collections.IList] -and $routeBatchFallbackList.Count -gt 0) {
    $routeBatchFallbackFirst = $routeBatchFallbackList[0]
}
$routeBatchFallbackFirstCameraId = Get-PropValue -Obj $routeBatchFallbackFirst -Name "camera_id"
$checks += New-CheckResult -Api "/api/inference/route/batch(default-fallback)" -Passed (($routeBatchFallbackResp.code -eq 0) -and ($routeBatchFallbackTraceId -ne $null) -and ($routeBatchFallbackTraceId -ne "") -and ($routeBatchFallbackList -is [System.Collections.IList]) -and ($routeBatchFallbackList.Count -ge 1) -and (([long]$routeBatchFallbackFirstCameraId) -eq 1) -and ($routeBatchFallbackDefaultUsed -is [bool]) -and ($routeBatchFallbackDefaultUsed -eq $true) -and ($routeBatchFallbackExpandedCandidateCount -ne $null) -and (([int]$routeBatchFallbackExpandedCandidateCount) -eq 0) -and ($routeBatchFallbackHitSources -is [System.Collections.IList]) -and ($routeBatchFallbackHitSources.Count -eq 0) -and ($routeBatchFallbackTruncatedSource -eq $null) -and ($routeBatchFallbackSourceStats -ne $null) -and (($routeBatchFallbackSourceStats.PSObject.Properties.Count) -eq 0)) -Detail ("code={0}; trace_id={1}; first_camera_id={2}; default_fallback_used={3}; expanded_candidate_count={4}; hit_sources={5}; truncated_source={6}; source_stats_size={7}; expected_first={8}" -f $routeBatchFallbackResp.code, $routeBatchFallbackTraceId, $routeBatchFallbackFirstCameraId, $routeBatchFallbackDefaultUsed, $routeBatchFallbackExpandedCandidateCount, (($routeBatchFallbackHitSources -join ",")), $routeBatchFallbackTruncatedSource, $routeBatchFallbackSourceStats.PSObject.Properties.Count, 1)

$checks | Format-Table -AutoSize | Out-String | Write-Output

$failedCount = ($checks | Where-Object { -not $_.passed }).Count
if ($failedCount -gt 0) {
    Write-Output ("FAIL: {0} checks failed." -f $failedCount)
    exit 2
}

Write-Output "PASS: all inference contract checks passed."
