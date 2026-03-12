param(
    [string]$TaskId = "global",
    [string]$Stage = "unspecified",
    [string]$Event = "checkpoint",
    [string]$Summary = "",
    [string]$Root = (Get-Location).Path,
    [switch]$NoProcessLog
)

$ErrorActionPreference = "Stop"

$day = (Get-Date).ToString("yyyyMMdd")
$stamp = (Get-Date).ToString("yyyyMMdd-HHmmss")

$checkpointDir = Join-Path $Root "state/checkpoints/$day"
if (-not (Test-Path $checkpointDir)) {
    New-Item -Path $checkpointDir -ItemType Directory -Force | Out-Null
}

$gitBranch = ""
$gitStatus = @()
$isGitRepo = $false

try {
    git -C $Root rev-parse --is-inside-work-tree *> $null
    if ($LASTEXITCODE -eq 0) {
        $isGitRepo = $true
        $gitBranch = (git -C $Root rev-parse --abbrev-ref HEAD).Trim()
        $gitStatus = @(git -C $Root status --short)
    }
} catch {
    $isGitRepo = $false
}

$checkpoint = [ordered]@{
    timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    task_id = $TaskId
    stage = $Stage
    event = $Event
    summary = $Summary
    root = (Resolve-Path $Root).Path
    pwd = (Get-Location).Path
    machine = $env:COMPUTERNAME
    user = $env:USERNAME
    is_git_repo = $isGitRepo
    git_branch = $gitBranch
    git_status = $gitStatus
}

$file = Join-Path $checkpointDir "checkpoint-$stamp-$TaskId.json"
$json = $checkpoint | ConvertTo-Json -Depth 8
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($file, $json, $utf8NoBom)

if (-not $NoProcessLog) {
    $logScript = Join-Path $PSScriptRoot "Append-ProcessLog.ps1"
    & $logScript -TaskId $TaskId -Stage $Stage -Event $Event -Summary ("checkpoint saved: " + [IO.Path]::GetFileName($file)) -Root $Root | Out-Null
}

Write-Output "OK: checkpoint saved to $file"
