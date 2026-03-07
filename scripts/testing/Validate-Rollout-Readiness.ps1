param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$PrimaryCameraId = 1,
    [long]$SecondaryCameraId = 2,
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

$routePrimaryResp = Invoke-ApiPostJson -Path "/api/inference/route" -BodyObj @{ camera_id = $PrimaryCameraId }
$routePrimaryData = Get-PropValue -Obj $routePrimaryResp -Name "data"
$routePrimaryTraceId = Get-PropValue -Obj $routePrimaryData -Name "trace_id"
$routePrimaryBackend = Get-PropValue -Obj $routePrimaryData -Name "backend_type"
$routePrimaryGlobalBackend = Get-PropValue -Obj $routePrimaryData -Name "global_backend_type"
$routePrimaryOverrideSource = Get-PropValue -Obj $routePrimaryData -Name "override_source"
$checks += New-CheckResult -Api "/api/inference/route(primary-gray-check)" -Passed (($routePrimaryResp.code -eq 0) -and ($routePrimaryTraceId -ne $null) -and ($routePrimaryTraceId -ne "") -and ($routePrimaryBackend -ne $null) -and ($routePrimaryBackend -ne "") -and ($routePrimaryGlobalBackend -ne $null) -and ($routePrimaryGlobalBackend -ne "") -and (Is-ExpectedBackend -ActualBackend $routePrimaryBackend) -and (Is-ExpectedOverrideSource -ActualSource $routePrimaryOverrideSource)) -Detail ("code={0}; trace_id={1}; camera_id={2}; backend_type={3}; global_backend_type={4}; override_source={5}; expected_backend={6}; expected_override_source={7}" -f $routePrimaryResp.code, $routePrimaryTraceId, $PrimaryCameraId, $routePrimaryBackend, $routePrimaryGlobalBackend, $routePrimaryOverrideSource, $ExpectedBackendType, $ExpectedOverrideSource)

$routeBatchResp = Invoke-ApiPostJson -Path "/api/inference/route/batch" -BodyObj @{ camera_ids = @($PrimaryCameraId, $SecondaryCameraId) }
$routeBatchData = Get-PropValue -Obj $routeBatchResp -Name "data"
$routeBatchTraceId = Get-PropValue -Obj $routeBatchData -Name "trace_id"
$routeBatchDefaultFallbackUsed = Get-PropValue -Obj $routeBatchData -Name "default_fallback_used"
$routeBatchResolvedCameraCount = Get-PropValue -Obj $routeBatchData -Name "resolved_camera_count"
$routeBatchList = Get-PropValue -Obj $routeBatchData -Name "route_list"
$routeBatchFirst = $null
$routeBatchSecond = $null
if ($routeBatchList -is [System.Collections.IList] -and $routeBatchList.Count -ge 2) {
    $routeBatchFirst = $routeBatchList[0]
    $routeBatchSecond = $routeBatchList[1]
}
$routeBatchFirstCameraId = Get-PropValue -Obj $routeBatchFirst -Name "camera_id"
$routeBatchSecondCameraId = Get-PropValue -Obj $routeBatchSecond -Name "camera_id"
$routeBatchFirstBackend = Get-PropValue -Obj $routeBatchFirst -Name "backend_type"
$routeBatchSecondBackend = Get-PropValue -Obj $routeBatchSecond -Name "backend_type"
$routeBatchFirstOverrideSource = Get-PropValue -Obj $routeBatchFirst -Name "override_source"
$routeBatchSecondOverrideSource = Get-PropValue -Obj $routeBatchSecond -Name "override_source"
$routeBatchConsistentWithSingle = ($routePrimaryBackend -eq $routeBatchFirstBackend) -and ($routePrimaryOverrideSource -eq $routeBatchFirstOverrideSource)
$checks += New-CheckResult -Api "/api/inference/route/batch(gray-consistency)" -Passed (($routeBatchResp.code -eq 0) -and ($routeBatchTraceId -ne $null) -and ($routeBatchTraceId -ne "") -and ($routeBatchList -is [System.Collections.IList]) -and ($routeBatchList.Count -ge 2) -and ($routeBatchResolvedCameraCount -ne $null) -and (([int]$routeBatchResolvedCameraCount) -eq $routeBatchList.Count) -and ($routeBatchDefaultFallbackUsed -is [bool]) -and (-not [bool]$routeBatchDefaultFallbackUsed) -and ($routeBatchFirstCameraId -ne $null) -and (([long]$routeBatchFirstCameraId) -eq $PrimaryCameraId) -and ($routeBatchSecondCameraId -ne $null) -and (([long]$routeBatchSecondCameraId) -eq $SecondaryCameraId) -and ($routeBatchFirstBackend -ne $null) -and ($routeBatchSecondBackend -ne $null) -and ($routeBatchConsistentWithSingle) -and (Is-ExpectedBackend -ActualBackend $routeBatchFirstBackend) -and (Is-ExpectedOverrideSource -ActualSource $routeBatchFirstOverrideSource)) -Detail ("code={0}; trace_id={1}; resolved_camera_count={2}; default_fallback_used={3}; first_camera_id={4}; second_camera_id={5}; first_backend={6}; second_backend={7}; first_override_source={8}; second_override_source={9}; consistent_with_single={10}" -f $routeBatchResp.code, $routeBatchTraceId, $routeBatchResolvedCameraCount, $routeBatchDefaultFallbackUsed, $routeBatchFirstCameraId, $routeBatchSecondCameraId, $routeBatchFirstBackend, $routeBatchSecondBackend, $routeBatchFirstOverrideSource, $routeBatchSecondOverrideSource, $routeBatchConsistentWithSingle)

$routeFallbackResp = Invoke-ApiGet -Path "/api/inference/route/batch?camera_ids=,,"
$routeFallbackData = Get-PropValue -Obj $routeFallbackResp -Name "data"
$routeFallbackTraceId = Get-PropValue -Obj $routeFallbackData -Name "trace_id"
$routeFallbackDefaultUsed = Get-PropValue -Obj $routeFallbackData -Name "default_fallback_used"
$routeFallbackList = Get-PropValue -Obj $routeFallbackData -Name "route_list"
$routeFallbackFirst = $null
if ($routeFallbackList -is [System.Collections.IList] -and $routeFallbackList.Count -ge 1) {
    $routeFallbackFirst = $routeFallbackList[0]
}
$routeFallbackFirstCameraId = Get-PropValue -Obj $routeFallbackFirst -Name "camera_id"
$routeFallbackExpandedCandidateCount = Get-PropValue -Obj $routeFallbackData -Name "expanded_candidate_count"
$checks += New-CheckResult -Api "/api/inference/route/batch(rollback-fallback)" -Passed (($routeFallbackResp.code -eq 0) -and ($routeFallbackTraceId -ne $null) -and ($routeFallbackTraceId -ne "") -and ($routeFallbackDefaultUsed -is [bool]) -and ([bool]$routeFallbackDefaultUsed) -and ($routeFallbackList -is [System.Collections.IList]) -and ($routeFallbackList.Count -ge 1) -and ($routeFallbackFirstCameraId -ne $null) -and (([long]$routeFallbackFirstCameraId) -eq 1) -and ($routeFallbackExpandedCandidateCount -ne $null) -and (([int]$routeFallbackExpandedCandidateCount) -eq 0)) -Detail ("code={0}; trace_id={1}; default_fallback_used={2}; first_camera_id={3}; expanded_candidate_count={4}; list_size={5}" -f $routeFallbackResp.code, $routeFallbackTraceId, $routeFallbackDefaultUsed, $routeFallbackFirstCameraId, $routeFallbackExpandedCandidateCount, ($(if ($routeFallbackList -is [System.Collections.IList]) { $routeFallbackList.Count } else { -1 })))

$checks | Format-Table -AutoSize | Out-String | Write-Output

$failedCount = ($checks | Where-Object { -not $_.passed }).Count
if ($failedCount -gt 0) {
    Write-Output ("FAIL: {0} rollout readiness checks failed." -f $failedCount)
    exit 2
}

Write-Output "PASS: rollout readiness checks passed."
