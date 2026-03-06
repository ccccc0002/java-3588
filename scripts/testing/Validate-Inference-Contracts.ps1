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
$checks += New-CheckResult -Api "/api/inference/dead-letter/stats" -Passed (($deadLetterStatsResp.code -eq 0) -and ($deadLetterStatsTraceId -ne $null) -and ($deadLetterStatsTraceId -ne "") -and ($deadLetterStats -ne $null) -and ($deadLetterQueueSize -ne $null) -and (([int]$deadLetterQueueSize) -ge 0) -and ($deadLetterMaxSize -ne $null) -and (([int]$deadLetterMaxSize) -ge 1)) -Detail ("code={0}; trace_id={1}; queue_size={2}; max_size={3}" -f $deadLetterStatsResp.code, $deadLetterStatsTraceId, $deadLetterQueueSize, $deadLetterMaxSize)

$deadLetterLatestResp = Invoke-ApiGet -Path "/api/inference/dead-letter/latest?limit=5"
$deadLetterLatestData = Get-PropValue -Obj $deadLetterLatestResp -Name "data"
$deadLetterLatestTraceId = Get-PropValue -Obj $deadLetterLatestData -Name "trace_id"
$deadLetterLatest = Get-PropValue -Obj $deadLetterLatestData -Name "dead_letter"
$checks += New-CheckResult -Api "/api/inference/dead-letter/latest" -Passed (($deadLetterLatestResp.code -eq 0) -and ($deadLetterLatestTraceId -ne $null) -and ($deadLetterLatestTraceId -ne "") -and ($deadLetterLatest -is [System.Collections.IList]) -and ($deadLetterLatest.Count -le 5)) -Detail ("code={0}; trace_id={1}; list_size={2}" -f $deadLetterLatestResp.code, $deadLetterLatestTraceId, ($(if ($deadLetterLatest -is [System.Collections.IList]) { $deadLetterLatest.Count } else { -1 })))

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
