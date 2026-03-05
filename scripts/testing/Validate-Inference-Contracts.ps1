param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$CameraId = 1,
    [long]$ModelId = 1,
    [long]$AlgorithmId = 1,
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
$checks += New-CheckResult -Api "/api/inference/test" -Passed (($testResp.code -eq 0) -and ($testTraceId -ne $null) -and ($testTraceId -ne "") -and ($testBackend -ne $null) -and ($testBackend -ne "") -and ($testResult -ne $null) -and ($testResultTraceId -ne $null) -and ($testResultTraceId -ne "")) -Detail ("code={0}; trace_id={1}; backend_type={2}; result_trace_id={3}" -f $testResp.code, $testTraceId, $testBackend, $testResultTraceId)

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
$checks += New-CheckResult -Api "/api/inference/dispatch" -Passed (($dispatchResp.code -eq 0) -and ($dispatchTraceId -ne $null) -and ($dispatchTraceId -ne "") -and ($dispatchBackend -ne $null) -and ($dispatchBackend -ne "") -and ($dispatchResult -ne $null) -and ($dispatchReport -ne $null) -and ($dispatchReportStatus -ne $null) -and ($dispatchReportStatus -ne "") -and ($dispatchIdempotent -ne $null) -and ($dispatchIdempotentStatus -ne $null) -and ($dispatchIdempotentStatus -ne "")) -Detail ("code={0}; trace_id={1}; backend_type={2}; report_status={3}; idem_status={4}" -f $dispatchResp.code, $dispatchTraceId, $dispatchBackend, $dispatchReportStatus, $dispatchIdempotentStatus)

$checks | Format-Table -AutoSize | Out-String | Write-Output

$failedCount = ($checks | Where-Object { -not $_.passed }).Count
if ($failedCount -gt 0) {
    Write-Output ("FAIL: {0} checks failed." -f $failedCount)
    exit 2
}

Write-Output "PASS: all inference contract checks passed."
