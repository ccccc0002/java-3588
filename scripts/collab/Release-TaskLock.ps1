param(
    [Parameter(Mandatory = $true)][string]$TaskId,
    [Parameter(Mandatory = $true)][string]$AgentId,
    [string]$Root = (Get-Location).Path,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$lockFile = Join-Path $Root "state/locks/$TaskId.lock.json"
if (-not (Test-Path $lockFile)) {
    Write-Output "OK: no lock to release for $TaskId"
    exit 0
}

$canRelease = $false
$lock = $null

try {
    $lock = Get-Content -Path $lockFile -Raw | ConvertFrom-Json
    if ($Force -or ($lock.agent_id -eq $AgentId)) {
        $canRelease = $true
    }
} catch {
    if ($Force) {
        $canRelease = $true
    }
}

if (-not $canRelease) {
    Write-Error "LOCK_OWNER_MISMATCH: owner=$($lock.agent_id), requested_by=$AgentId"
    exit 3
}

Remove-Item -Path $lockFile -Force

$logScript = Join-Path $PSScriptRoot "Append-ProcessLog.ps1"
& $logScript -TaskId $TaskId -Stage "lock" -Event "lock_released" -Summary "lock released by $AgentId" -Root $Root | Out-Null

Write-Output "OK: lock released for $TaskId"

