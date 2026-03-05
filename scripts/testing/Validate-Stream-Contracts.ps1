param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$CameraId = 1,
    [int]$VideoPort = 0,
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

$selectResp = Invoke-PostForm -Path "/camera/selectPlay" -Form @{ cameraId = $CameraId }
$selectData = Get-PropValue -Obj $selectResp -Name "data"
$selectTraceId = Get-PropValue -Obj $selectData -Name "trace_id"
$selectPlayUrl = Get-PropValue -Obj $selectData -Name "playUrl"
$checks += New-CheckResult -Api "/camera/selectPlay" -Passed (($selectResp.code -eq 0) -and ($selectTraceId -ne $null) -and ($selectPlayUrl -ne $null) -and ($selectPlayUrl -ne "")) -Detail ("code={0}; trace_id={1}; playUrl={2}" -f $selectResp.code, $selectTraceId, $selectPlayUrl)

$startForm = @{ cameraId = $CameraId }
if ($VideoPort -gt 0) {
    $startForm["videoPort"] = $VideoPort
}
$startResp = Invoke-PostForm -Path "/stream/start" -Form $startForm
$startData = Get-PropValue -Obj $startResp -Name "data"
$startTraceId = Get-PropValue -Obj $startData -Name "trace_id"
$startPlayUrl = Get-PropValue -Obj $startData -Name "playUrl"
$checks += New-CheckResult -Api "/stream/start" -Passed (($startResp.code -eq 0) -and ($startTraceId -ne $null) -and ($startTraceId -ne "") -and ($startPlayUrl -ne $null) -and ($startPlayUrl -ne "")) -Detail ("code={0}; trace_id={1}; playUrl={2}" -f $startResp.code, $startTraceId, $startPlayUrl)

$stopResp = Invoke-PostForm -Path "/stream/stop" -Form @{ cameraId = $CameraId }
$stopData = Get-PropValue -Obj $stopResp -Name "data"
$stopTraceId = Get-PropValue -Obj $stopData -Name "trace_id"
$checks += New-CheckResult -Api "/stream/stop" -Passed (($stopResp.code -eq 0) -and ($stopTraceId -ne $null) -and ($stopTraceId -ne "")) -Detail ("code={0}; trace_id={1}" -f $stopResp.code, $stopTraceId)

$checks | Format-Table -AutoSize | Out-String | Write-Output

$failedCount = ($checks | Where-Object { -not $_.passed }).Count
if ($failedCount -gt 0) {
    Write-Output ("FAIL: {0} checks failed." -f $failedCount)
    exit 2
}

Write-Output "PASS: all stream contract checks passed."
