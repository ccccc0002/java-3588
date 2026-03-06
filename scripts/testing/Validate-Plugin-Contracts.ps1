param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$PluginId = "face-detector",
    [string]$Version = "1.0.0",
    [string]$Runtime = "rk3588_rknn",
    [string]$HealthUrl = "http://127.0.0.1:18080/health",
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
$manifestBody = @{
    plugin_id = $PluginId
    version = $Version
    runtime = $Runtime
    capabilities = @("inference", "alert")
}

$validateResp = Invoke-ApiPostJson -Path "/api/plugin/manifest/validate" -BodyObj $manifestBody
$validateData = Get-PropValue -Obj $validateResp -Name "data"
$validateTraceId = Get-PropValue -Obj $validateData -Name "trace_id"
$validateValid = Get-PropValue -Obj $validateData -Name "valid"
$validateSchema = Get-PropValue -Obj $validateData -Name "schema_version"
$validateManifest = Get-PropValue -Obj $validateData -Name "normalized_manifest"
$validatePluginId = Get-PropValue -Obj $validateManifest -Name "plugin_id"
$validateVersion = Get-PropValue -Obj $validateManifest -Name "version"
$validateRuntime = Get-PropValue -Obj $validateManifest -Name "runtime"
$checks += New-CheckResult -Api "/api/plugin/manifest/validate" -Passed (($validateResp.code -eq 0) -and ($validateTraceId -ne $null) -and ($validateTraceId -ne "") -and ($validateValid -eq $true) -and ($validateSchema -eq "plugin-manifest.v1") -and ($validatePluginId -eq $PluginId) -and ($validateVersion -eq $Version) -and ($validateRuntime -eq $Runtime)) -Detail ("code={0}; trace_id={1}; valid={2}; schema_version={3}; plugin_id={4}; version={5}; runtime={6}" -f $validateResp.code, $validateTraceId, $validateValid, $validateSchema, $validatePluginId, $validateVersion, $validateRuntime)

$probeResp = Invoke-ApiPostJson -Path "/api/plugin/health/probe" -BodyObj @{ health_url = $HealthUrl }
$probeData = Get-PropValue -Obj $probeResp -Name "data"
$probeTraceId = Get-PropValue -Obj $probeData -Name "trace_id"
$probeHealthy = Get-PropValue -Obj $probeData -Name "healthy"
$probeStatus = Get-PropValue -Obj $probeData -Name "status"
$probeUrl = Get-PropValue -Obj $probeData -Name "health_url"
$checks += New-CheckResult -Api "/api/plugin/health/probe" -Passed (($probeResp.code -eq 0) -and ($probeTraceId -ne $null) -and ($probeTraceId -ne "") -and ($probeHealthy -eq $true) -and ($probeStatus -eq "ok") -and ($probeUrl -eq $HealthUrl)) -Detail ("code={0}; trace_id={1}; healthy={2}; status={3}; health_url={4}" -f $probeResp.code, $probeTraceId, $probeHealthy, $probeStatus, $probeUrl)

$registerBody = @{
    plugin_id = $PluginId
    version = $Version
    runtime = $Runtime
    capabilities = @("inference", "alert")
    health_url = $HealthUrl
}
$registerResp = Invoke-ApiPostJson -Path "/api/plugin/register" -BodyObj $registerBody
$registerData = Get-PropValue -Obj $registerResp -Name "data"
$registerTraceId = Get-PropValue -Obj $registerData -Name "trace_id"
$registerAccepted = Get-PropValue -Obj $registerData -Name "accepted"
$registerStatus = Get-PropValue -Obj $registerData -Name "registration_status"
$registerId = Get-PropValue -Obj $registerData -Name "registration_id"
$registerSchema = Get-PropValue -Obj $registerData -Name "schema_version"
$checks += New-CheckResult -Api "/api/plugin/register" -Passed (($registerResp.code -eq 0) -and ($registerTraceId -ne $null) -and ($registerTraceId -ne "") -and ($registerAccepted -eq $true) -and ($registerStatus -eq "accepted") -and ($registerId -eq ($PluginId + ":" + $Version)) -and ($registerSchema -eq "plugin-manifest.v1")) -Detail ("code={0}; trace_id={1}; accepted={2}; registration_status={3}; registration_id={4}; schema_version={5}" -f $registerResp.code, $registerTraceId, $registerAccepted, $registerStatus, $registerId, $registerSchema)

$listResp = Invoke-ApiGet -Path "/api/plugin/list"
$listData = Get-PropValue -Obj $listResp -Name "data"
$listTraceId = Get-PropValue -Obj $listData -Name "trace_id"
$listPlugins = Get-PropValue -Obj $listData -Name "plugins"
$listFound = $false
if ($listPlugins -is [System.Collections.IList]) {
    foreach ($plugin in $listPlugins) {
        $candidateId = Get-PropValue -Obj $plugin -Name "registration_id"
        if ($candidateId -eq $registerId) {
            $listFound = $true
        }
    }
}
$checks += New-CheckResult -Api "/api/plugin/list" -Passed (($listResp.code -eq 0) -and ($listTraceId -ne $null) -and ($listTraceId -ne "") -and ($listPlugins -is [System.Collections.IList]) -and $listFound) -Detail ("code={0}; trace_id={1}; list_size={2}; found_registration={3}" -f $listResp.code, $listTraceId, ($(if ($listPlugins -is [System.Collections.IList]) { $listPlugins.Count } else { -1 })), $listFound)

$detailResp = Invoke-ApiGet -Path ("/api/plugin/detail?registration_id=" + [uri]::EscapeDataString($registerId))
$detailData = Get-PropValue -Obj $detailResp -Name "data"
$detailTraceId = Get-PropValue -Obj $detailData -Name "trace_id"
$detailFound = Get-PropValue -Obj $detailData -Name "found"
$detailPlugin = Get-PropValue -Obj $detailData -Name "plugin"
$detailRegistrationId = Get-PropValue -Obj $detailPlugin -Name "registration_id"
$checks += New-CheckResult -Api "/api/plugin/detail" -Passed (($detailResp.code -eq 0) -and ($detailTraceId -ne $null) -and ($detailTraceId -ne "") -and ($detailFound -eq $true) -and ($detailRegistrationId -eq $registerId)) -Detail ("code={0}; trace_id={1}; found={2}; registration_id={3}" -f $detailResp.code, $detailTraceId, $detailFound, $detailRegistrationId)

$checks | Format-Table -AutoSize | Out-String | Write-Output

$failedCount = ($checks | Where-Object { -not $_.passed }).Count
if ($failedCount -gt 0) {
    Write-Output ("FAIL: {0} plugin contract checks failed." -f $failedCount)
    exit 2
}

Write-Output "PASS: all plugin contract checks passed."
