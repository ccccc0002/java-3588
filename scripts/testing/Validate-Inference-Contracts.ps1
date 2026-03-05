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
$checks += New-CheckResult -Api "/api/inference/health" -Passed (($healthResp.code -eq 0) -and ($healthTraceId -ne $null) -and ($healthTraceId -ne "") -and ($healthBackend -ne $null) -and ($healthBackend -ne "") -and ($healthUpstream -ne $null)) -Detail ("code={0}; trace_id={1}; backend_type={2}" -f $healthResp.code, $healthTraceId, $healthBackend)

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
$routeBatchFirst = $null
if ($routeBatchList -is [System.Collections.IList] -and $routeBatchList.Count -gt 0) {
    $routeBatchFirst = $routeBatchList[0]
}
$routeBatchFirstCameraId = Get-PropValue -Obj $routeBatchFirst -Name "camera_id"
$routeBatchFirstBackend = Get-PropValue -Obj $routeBatchFirst -Name "backend_type"
$checks += New-CheckResult -Api "/api/inference/route/batch" -Passed (($routeBatchResp.code -eq 0) -and ($routeBatchTraceId -ne $null) -and ($routeBatchTraceId -ne "") -and ($routeBatchGlobalBackend -ne $null) -and ($routeBatchGlobalBackend -ne "") -and ($routeBatchList -is [System.Collections.IList]) -and ($routeBatchList.Count -gt 0) -and ($routeBatchFirstCameraId -ne $null) -and (([long]$routeBatchFirstCameraId) -eq $CameraId) -and ($routeBatchFirstBackend -ne $null) -and ($routeBatchFirstBackend -ne "") -and ($routeBatchTruncated -is [bool]) -and (([int]$routeBatchMaxCameraIds) -eq 500) -and (Is-ExpectedBackend -ActualBackend $routeBatchFirstBackend)) -Detail ("code={0}; trace_id={1}; global_backend_type={2}; first_camera_id={3}; first_backend_type={4}; truncated={5}; max_camera_ids={6}; expected_backend={7}" -f $routeBatchResp.code, $routeBatchTraceId, $routeBatchGlobalBackend, $routeBatchFirstCameraId, $routeBatchFirstBackend, $routeBatchTruncated, $routeBatchMaxCameraIds, $ExpectedBackendType)

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
$checks += New-CheckResult -Api "/api/inference/route/batch(range)" -Passed (($routeBatchRangeResp.code -eq 0) -and ($routeBatchRangeTraceId -ne $null) -and ($routeBatchRangeTraceId -ne "") -and ($routeBatchRangeList -is [System.Collections.IList]) -and ($routeBatchRangeList.Count -ge 2) -and ($routeBatchRangeFirstCameraId -ne $null) -and ($routeBatchRangeSecondCameraId -ne $null) -and (([long]$routeBatchRangeFirstCameraId) -eq $CameraId) -and (([long]$routeBatchRangeSecondCameraId) -eq $nextCameraId) -and ($routeBatchRangeTruncated -is [bool]) -and (([int]$routeBatchRangeMaxCameraIds) -eq 500)) -Detail ("code={0}; trace_id={1}; first_camera_id={2}; second_camera_id={3}; truncated={4}; max_camera_ids={5}; expected_first={6}; expected_second={7}" -f $routeBatchRangeResp.code, $routeBatchRangeTraceId, $routeBatchRangeFirstCameraId, $routeBatchRangeSecondCameraId, $routeBatchRangeTruncated, $routeBatchRangeMaxCameraIds, $CameraId, $nextCameraId)

$checks | Format-Table -AutoSize | Out-String | Write-Output

$failedCount = ($checks | Where-Object { -not $_.passed }).Count
if ($failedCount -gt 0) {
    Write-Output ("FAIL: {0} checks failed." -f $failedCount)
    exit 2
}

Write-Output "PASS: all inference contract checks passed."
