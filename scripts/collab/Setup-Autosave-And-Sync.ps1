param(
    [string]$Root = (Get-Location).Path,
    [string]$TaskName = "Java3588_ContextAutosave",
    [string]$TaskId = "global",
    [string]$Stage = "collab",
    [int]$IntervalMinutes = 15,
    [int]$CompactEvery = 4,
    [string]$Token = "",
    [switch]$SkipGithubSync,
    [switch]$AllowLoopFallback,
    [switch]$SkipAdminCheck,
    [switch]$UseScheduler
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Text)
    Write-Output ("[STEP] " + $Text)
}

if ($UseScheduler -and (-not $SkipAdminCheck)) {
    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    $isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        throw "This setup should be run in an elevated PowerShell (Run as administrator). Use -SkipAdminCheck to bypass."
    }
}

$requiredScripts = @(
    "scripts/collab/New-TaskWorkspace.ps1",
    "scripts/collab/Invoke-Hook.ps1",
    "scripts/collab/Install-AutosaveTask.ps1",
    "scripts/collab/Test-Schtasks.ps1",
    "scripts/collab/Start-AutosaveLoop.ps1",
    "scripts/collab/Append-ProcessLog.ps1",
    "scripts/collab/Sync-RecorderRepo.ps1"
)

foreach ($rel in $requiredScripts) {
    $full = Join-Path $Root $rel
    if (-not (Test-Path $full)) {
        throw "required script missing: $full"
    }
}

Write-Step "Create or reuse task workspace"
& (Join-Path $Root "scripts/collab/New-TaskWorkspace.ps1") -TaskId $TaskId -Owner "setup-agent" -Root $Root | Out-Null

Write-Step "Emit task_started hook"
& (Join-Path $Root "scripts/collab/Invoke-Hook.ps1") -Event "task_started" -TaskId $TaskId -Stage $Stage -Summary "one-click setup started" -Root $Root | Out-Null

$taskInstalled = $false
if ($UseScheduler) {
    try {
        Write-Step "Install scheduled autosave task"
        & (Join-Path $Root "scripts/collab/Install-AutosaveTask.ps1") -Root $Root -TaskName $TaskName -TaskId $TaskId -Stage $Stage -IntervalMinutes $IntervalMinutes -CompactEvery $CompactEvery -TimeoutSeconds 20 | Out-Null
        $taskInstalled = $true
    } catch {
        if ($AllowLoopFallback) {
            & (Join-Path $Root "scripts/collab/Append-ProcessLog.ps1") -TaskId $TaskId -Stage "setup" -Event "scheduler_fallback" -Summary ("schtasks install failed; fallback enabled. error=" + $_.Exception.Message) -Root $Root | Out-Null
            Write-Warning "schtasks install failed. Fallback command:"
            Write-Warning "powershell -ExecutionPolicy Bypass -File .\scripts\collab\Start-AutosaveLoop.ps1 -Root . -TaskId $TaskId -Stage $Stage -IntervalMinutes $IntervalMinutes -CompactEvery $CompactEvery"
        } else {
            throw
        }
    }
} else {
    Write-Step "Skip scheduler (event-driven sync mode)"
}

if ($taskInstalled) {
    Write-Step "Verify schtasks status"
    & (Join-Path $Root "scripts/collab/Test-Schtasks.ps1") -TaskName $TaskName | Out-Null
}

if (-not $SkipGithubSync) {
    Write-Step "Sync process artifacts to recorder repository"
    if ([string]::IsNullOrWhiteSpace($Token)) {
        $Token = $env:GITHUB_TOKEN
    }
    & (Join-Path $Root "scripts/collab/Sync-RecorderRepo.ps1") -Root $Root -Token $Token | Out-Null
} else {
    Write-Step "Skip GitHub sync by option"
}

Write-Step "Emit milestone hook"
& (Join-Path $Root "scripts/collab/Invoke-Hook.ps1") -Event "milestone_reached" -TaskId $TaskId -Stage $Stage -Summary "one-click setup completed" -Root $Root | Out-Null

Write-Output "OK: setup completed"
