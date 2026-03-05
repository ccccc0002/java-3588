param(
    [Parameter(Mandatory = $true)][string]$TaskId,
    [Parameter(Mandatory = $true)][string]$AgentId,
    [string]$Root = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$locksDir = Join-Path $Root "state/locks"
if (-not (Test-Path $locksDir)) {
    New-Item -Path $locksDir -ItemType Directory -Force | Out-Null
}

$lockFile = Join-Path $locksDir "$TaskId.lock.json"
if (Test-Path $lockFile) {
    $existing = Get-Content -Path $lockFile -Raw
    Write-Error "LOCKED: $TaskId already locked. detail=$existing"
    exit 2
}

$lock = [ordered]@{
    task_id = $TaskId
    agent_id = $AgentId
    timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    machine = $env:COMPUTERNAME
    user = $env:USERNAME
}

$lock | ConvertTo-Json -Depth 5 | Set-Content -Path $lockFile -Encoding UTF8

$logScript = Join-Path $PSScriptRoot "Append-ProcessLog.ps1"
& $logScript -TaskId $TaskId -Stage "lock" -Event "lock_acquired" -Summary "lock acquired by $AgentId" -Root $Root | Out-Null

Write-Output "OK: lock acquired for $TaskId by $AgentId"

