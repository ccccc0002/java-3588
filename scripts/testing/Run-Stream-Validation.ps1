param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$CameraId = 1,
    [long]$ModelId = 1,
    [long]$AlgorithmId = 1,
    [int]$VideoPort = 0,
    [string]$InferenceSource = "test://frame",
    [string]$Cookie = "",
    [int]$TimeoutSec = 10,
    [ValidateSet("quick", "full")]
    [string]$Mode = "full",
    [switch]$IncludeInference
)

$ErrorActionPreference = "Continue"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$contractsScript = Join-Path $scriptDir "Validate-Stream-Contracts.ps1"
$traceScript = Join-Path $scriptDir "Validate-TraceId-Lists.ps1"
$inferenceScript = Join-Path $scriptDir "Validate-Inference-Contracts.ps1"

if (-not (Test-Path $contractsScript)) {
    Write-Output "FAIL: missing script Validate-Stream-Contracts.ps1"
    exit 2
}
if (-not (Test-Path $traceScript)) {
    Write-Output "FAIL: missing script Validate-TraceId-Lists.ps1"
    exit 2
}
if ($IncludeInference -and -not (Test-Path $inferenceScript)) {
    Write-Output "FAIL: missing script Validate-Inference-Contracts.ps1"
    exit 2
}

$failed = 0

Write-Output ("== Mode: {0} ==" -f $Mode)

# quick: only stream contract checks; full: run contract + trace-id list checks
Write-Output "== Run: Validate-Stream-Contracts =="
& $contractsScript -BaseUrl $BaseUrl -CameraId $CameraId -VideoPort $VideoPort -Cookie $Cookie -TimeoutSec $TimeoutSec
if ($LASTEXITCODE -ne 0) {
    $failed++
}

if ($Mode -eq "full") {
    Write-Output "== Run: Validate-TraceId-Lists =="
    & $traceScript -BaseUrl $BaseUrl -Cookie $Cookie -TimeoutSec $TimeoutSec
    if ($LASTEXITCODE -ne 0) {
        $failed++
    }
}

if ($IncludeInference) {
    Write-Output "== Run: Validate-Inference-Contracts =="
    & $inferenceScript -BaseUrl $BaseUrl -CameraId $CameraId -ModelId $ModelId -AlgorithmId $AlgorithmId -Source $InferenceSource -Cookie $Cookie -TimeoutSec $TimeoutSec
    if ($LASTEXITCODE -ne 0) {
        $failed++
    }
}

if ($failed -gt 0) {
    Write-Output ("FAIL: stream validation has {0} failed stage(s)." -f $failed)
    exit 2
}

Write-Output "PASS: all stream validations passed."
