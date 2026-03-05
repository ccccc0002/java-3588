param(
    [string]$TaskName = "Java3588_ContextAutosave"
)

$ErrorActionPreference = "Stop"

$taskExe = "C:\Windows\System32\schtasks.exe"
if (-not (Test-Path $taskExe)) {
    throw "schtasks not found: $taskExe"
}

$stdout = [System.IO.Path]::GetTempFileName()
$stderr = [System.IO.Path]::GetTempFileName()
$args = "/Delete /F /TN `"$TaskName`""
$proc = Start-Process -FilePath $taskExe -ArgumentList $args -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
$outText = ""
$errText = ""
if (Test-Path $stdout) { $outText = Get-Content -Path $stdout -Raw }
if (Test-Path $stderr) { $errText = Get-Content -Path $stderr -Raw }
Remove-Item -Path $stdout,$stderr -Force -ErrorAction SilentlyContinue

if ($proc.ExitCode -ne 0) {
    throw "failed to remove scheduled task (exit=$($proc.ExitCode)): $outText $errText"
}

Write-Output "OK: scheduled task removed: $TaskName"
