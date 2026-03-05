param(
    [string]$TaskName = "Java3588_ContextAutosave",
    [string]$TaskId = "global",
    [string]$Stage = "autosave",
    [int]$IntervalMinutes = 15,
    [int]$CompactEvery = 4,
    [string]$Root = (Get-Location).Path,
    [int]$TimeoutSeconds = 20
)

$ErrorActionPreference = "Stop"

$script = Join-Path $Root "scripts/collab/Autosave-Tick.ps1"
if (-not (Test-Path $script)) {
    throw "autosave tick script not found: $script"
}

$cfgFile = Join-Path $Root "hooks/autosave-config.json"
$cfg = [ordered]@{
    task_id = $TaskId
    stage = $Stage
    interval_minutes = $IntervalMinutes
    compact_every = $CompactEvery
}
$cfg | ConvertTo-Json -Depth 3 | Set-Content -Path $cfgFile -Encoding UTF8

$runnerDir = Join-Path $Root "hooks"
if (-not (Test-Path $runnerDir)) {
    New-Item -Path $runnerDir -ItemType Directory -Force | Out-Null
}
$runnerFile = Join-Path $runnerDir "run-autosave-task.cmd"
@(
    "@echo off",
    "powershell -ExecutionPolicy Bypass -File ""$script"" -Root ""$Root"""
) | Set-Content -Path $runnerFile -Encoding ASCII

$taskExe = "C:\Windows\System32\schtasks.exe"
if (-not (Test-Path $taskExe)) {
    throw "schtasks not found: $taskExe"
}

$stdout = [System.IO.Path]::GetTempFileName()
$stderr = [System.IO.Path]::GetTempFileName()
$args = "/Create /F /SC MINUTE /MO $IntervalMinutes /TN `"$TaskName`" /TR `"$runnerFile`""
$proc = Start-Process -FilePath $taskExe -ArgumentList $args -NoNewWindow -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
$finished = $proc.WaitForExit($TimeoutSeconds * 1000)

if (-not $finished) {
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch {}
    $outText = if (Test-Path $stdout) { Get-Content -Path $stdout -Raw } else { "" }
    $errText = if (Test-Path $stderr) { Get-Content -Path $stderr -Raw } else { "" }
    Remove-Item -Path $stdout,$stderr -Force -ErrorAction SilentlyContinue
    throw "failed to install scheduled task: schtasks timed out after $TimeoutSeconds seconds. $outText $errText`nHint: run as administrator, or use Start-AutosaveLoop.ps1 fallback."
}

$outText = ""
$errText = ""
if (Test-Path $stdout) { $outText = Get-Content -Path $stdout -Raw }
if (Test-Path $stderr) { $errText = Get-Content -Path $stderr -Raw }
Remove-Item -Path $stdout,$stderr -Force -ErrorAction SilentlyContinue

if ($proc.ExitCode -ne 0) {
    throw "failed to install scheduled task (exit=$($proc.ExitCode)): $outText $errText`nHint: run as administrator, or use Start-AutosaveLoop.ps1 fallback."
}

Write-Output "OK: scheduled task installed: $TaskName"
