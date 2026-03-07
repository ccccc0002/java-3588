param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$CameraId = 1,
    [long]$ModelId = 1,
    [long]$AlgorithmId = 1,
    [int]$VideoPort = 0,
    [string]$Source = "test://frame",
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

function Invoke-PostForm {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][hashtable]$Form
    )
    $uri = ($BaseUrl.TrimEnd("/") + $Path)
    $headers = @{}
    if ($Cookie -ne "") {
        $headers["Cookie"] = $Cookie
    }
    try {
        return Invoke-RestMethod -Uri $uri -Method Post -Body $Form -Headers $headers -TimeoutSec $TimeoutSec
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

$checks = @()

$startForm = @{ cameraId = $CameraId }
if ($VideoPort -gt 0) {
    $startForm["videoPort"] = $VideoPort
}
$startResp = Invoke-PostForm -Path "/stream/start" -Form $startForm
$startData = Get-PropValue -Obj $startResp -Name "data"
$startTraceId = Get-PropValue -Obj $startData -Name "trace_id"
$startPlayUrl = Get-PropValue -Obj $startData -Name "playUrl"
$checks += New-CheckResult -Api "/stream/start(trace-flow)" -Passed (($startResp.code -eq 0) -and ($startTraceId -ne $null) -and ($startTraceId -ne "") -and ($startPlayUrl -ne $null) -and ($startPlayUrl -ne "")) -Detail ("code={0}; trace_id={1}; playUrl={2}" -f $startResp.code, $startTraceId, $startPlayUrl)

$dispatchTraceSeed = ("trace-flow-" + [Guid]::NewGuid().ToString("N"))
$dispatchReq = @{
    trace_id = $dispatchTraceSeed
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
$dispatchResult = Get-PropValue -Obj $dispatchData -Name "result"
$dispatchResultTraceId = Get-PropValue -Obj $dispatchResult -Name "trace_id"
$dispatchReport = Get-PropValue -Obj $dispatchData -Name "report"
$dispatchReportTraceId = Get-PropValue -Obj $dispatchReport -Name "trace_id"
$dispatchReportStatus = Get-PropValue -Obj $dispatchReport -Name "status"
$dispatchIdempotent = Get-PropValue -Obj $dispatchData -Name "idempotent"
$dispatchIdempotentTraceId = Get-PropValue -Obj $dispatchIdempotent -Name "trace_id"
$dispatchIdempotentStatus = Get-PropValue -Obj $dispatchIdempotent -Name "status"
$dispatchRequest = Get-PropValue -Obj $dispatchData -Name "request"
$dispatchRequestFrame = Get-PropValue -Obj $dispatchRequest -Name "frame"
$dispatchRequestSource = Get-PropValue -Obj $dispatchRequestFrame -Name "source"
$dispatchTraceConsistent = ($dispatchTraceId -eq $dispatchTraceSeed) -and ($dispatchResultTraceId -eq $dispatchTraceSeed) -and ($dispatchReportTraceId -eq $dispatchTraceSeed) -and ($dispatchIdempotentTraceId -eq $dispatchTraceSeed)
$checks += New-CheckResult -Api "/api/inference/dispatch(trace-flow)" -Passed (($dispatchResp.code -eq 0) -and ($dispatchTraceId -ne $null) -and ($dispatchTraceId -ne "") -and ($dispatchReportStatus -ne $null) -and ($dispatchReportStatus -ne "") -and ($dispatchIdempotentStatus -ne $null) -and ($dispatchIdempotentStatus -ne "") -and ($dispatchRequestSource -eq $Source) -and $dispatchTraceConsistent) -Detail ("code={0}; trace_id={1}; result_trace_id={2}; report_trace_id={3}; idempotent_trace_id={4}; report_status={5}; idempotent_status={6}; request_source={7}; consistent={8}" -f $dispatchResp.code, $dispatchTraceId, $dispatchResultTraceId, $dispatchReportTraceId, $dispatchIdempotentTraceId, $dispatchReportStatus, $dispatchIdempotentStatus, $dispatchRequestSource, $dispatchTraceConsistent)

$replayNotFoundResp = Invoke-ApiGet -Path "/api/inference/dead-letter/replay?dead_letter_id=-1"
$replayNotFoundData = Get-PropValue -Obj $replayNotFoundResp -Name "data"
$replayNotFoundTraceId = Get-PropValue -Obj $replayNotFoundData -Name "trace_id"
$replayNotFoundErrorCode = Get-PropValue -Obj $replayNotFoundData -Name "error_code"
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay(trace-governance)" -Passed (($replayNotFoundResp.code -ne 0) -and ($replayNotFoundTraceId -ne $null) -and ($replayNotFoundTraceId -ne "") -and ($replayNotFoundErrorCode -eq "INFER_DL_REPLAY_NOT_FOUND")) -Detail ("code={0}; trace_id={1}; error_code={2}" -f $replayNotFoundResp.code, $replayNotFoundTraceId, $replayNotFoundErrorCode)

$strictResumeReq = @{
    strict_resume = 1
    dead_letter_ids = @(901, 902)
    dry_run = 1
}
$strictResumeResp = Invoke-ApiPostJson -Path "/api/inference/dead-letter/replay/batch?limit=2" -BodyObj $strictResumeReq
$strictResumeData = Get-PropValue -Obj $strictResumeResp -Name "data"
$strictResumeTraceId = Get-PropValue -Obj $strictResumeData -Name "trace_id"
$strictResumeErrorCode = Get-PropValue -Obj $strictResumeData -Name "error_code"
$strictResumeEnabled = Get-PropValue -Obj $strictResumeData -Name "strict_resume"
$strictResumeActualTotal = Get-PropValue -Obj $strictResumeData -Name "actual_total_selected_count"
$checks += New-CheckResult -Api "/api/inference/dead-letter/replay/batch(trace-governance)" -Passed (($strictResumeResp.code -ne 0) -and ($strictResumeTraceId -ne $null) -and ($strictResumeTraceId -ne "") -and ($strictResumeEnabled -is [bool]) -and ([bool]$strictResumeEnabled) -and ($strictResumeActualTotal -ne $null) -and (([int]$strictResumeActualTotal) -eq 2) -and ($strictResumeErrorCode -eq "INFER_DL_REPLAY_BATCH_STRICT_RESUME_EXPECTED_TOTAL_REQUIRED")) -Detail ("code={0}; trace_id={1}; strict_resume={2}; actual_total={3}; error_code={4}" -f $strictResumeResp.code, $strictResumeTraceId, $strictResumeEnabled, $strictResumeActualTotal, $strictResumeErrorCode)

$stopResp = Invoke-PostForm -Path "/stream/stop" -Form @{ cameraId = $CameraId }
$stopData = Get-PropValue -Obj $stopResp -Name "data"
$stopTraceId = Get-PropValue -Obj $stopData -Name "trace_id"
$checks += New-CheckResult -Api "/stream/stop(trace-flow)" -Passed (($stopResp.code -eq 0) -and ($stopTraceId -ne $null) -and ($stopTraceId -ne "")) -Detail ("code={0}; trace_id={1}" -f $stopResp.code, $stopTraceId)

$checks | Format-Table -AutoSize | Out-String | Write-Output

$failedCount = ($checks | Where-Object { -not $_.passed }).Count
if ($failedCount -gt 0) {
    Write-Output ("FAIL: {0} trace/governance checks failed." -f $failedCount)
    exit 2
}

Write-Output "PASS: trace and governance flow checks passed."
