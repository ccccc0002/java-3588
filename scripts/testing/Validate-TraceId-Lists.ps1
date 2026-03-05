param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
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

function Invoke-PostForm {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $false)][hashtable]$Form = @{}
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

function Check-TraceFromListResponse {
    param(
        [Parameter(Mandatory = $true)]$Response,
        [Parameter(Mandatory = $true)][string]$Api
    )
    $list = Get-PropValue -Obj $Response -Name "data"
    if ($null -eq $list) {
        return New-CheckResult -Api $Api -Passed $false -Detail ("code={0}; data=null" -f $Response.code)
    }

    $size = 0
    try {
        $size = @($list).Count
    } catch {
        $size = 0
    }

    if ($Response.code -ne 0) {
        return New-CheckResult -Api $Api -Passed $false -Detail ("code={0}; msg={1}" -f $Response.code, $Response.msg)
    }

    if ($size -eq 0) {
        return New-CheckResult -Api $Api -Passed $true -Detail "code=0; list empty, trace_id not verifiable"
    }

    $first = @($list)[0]
    $traceId = Get-PropValue -Obj $first -Name "trace_id"
    $passed = ($traceId -ne $null) -and ($traceId -ne "")
    return New-CheckResult -Api $Api -Passed $passed -Detail ("code=0; size={0}; trace_id={1}" -f $size, $traceId)
}

$checks = @()

$playListResp = Invoke-PostForm -Path "/stream/play_list"
$checks += Check-TraceFromListResponse -Response $playListResp -Api "/stream/play_list"

$cameraListResp = Invoke-PostForm -Path "/stream/camera_list" -Form @{ page = 1; limit = 5 }
$checks += Check-TraceFromListResponse -Response $cameraListResp -Api "/stream/camera_list"

$cameraApiResp = Invoke-PostForm -Path "/api/camera/list"
$checks += Check-TraceFromListResponse -Response $cameraApiResp -Api "/api/camera/list"

$checks | Format-Table -AutoSize | Out-String | Write-Output

$failedCount = ($checks | Where-Object { -not $_.passed }).Count
if ($failedCount -gt 0) {
    Write-Output ("FAIL: {0} checks failed." -f $failedCount)
    exit 2
}

Write-Output "PASS: all trace_id list checks passed."
