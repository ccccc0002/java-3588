param(
    [int]$TimeoutSeconds = 10,
    [switch]$RestartService
)

$ErrorActionPreference = "Stop"

function Invoke-WithTimeout {
    param(
        [string]$FilePath,
        [string]$ArgumentList,
        [int]$TimeoutSeconds
    )
    $stdout = [System.IO.Path]::GetTempFileName()
    $stderr = [System.IO.Path]::GetTempFileName()
    $p = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList -NoNewWindow -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $finished = $p.WaitForExit($TimeoutSeconds * 1000)
    if (-not $finished) {
        try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
        $outText = if (Test-Path $stdout) { Get-Content -Path $stdout -Raw } else { "" }
        $errText = if (Test-Path $stderr) { Get-Content -Path $stderr -Raw } else { "" }
        Remove-Item -Path $stdout,$stderr -Force -ErrorAction SilentlyContinue
        return [pscustomobject]@{ ExitCode = 124; TimedOut = $true; StdOut = $outText; StdErr = $errText }
    }
    $outText = if (Test-Path $stdout) { Get-Content -Path $stdout -Raw } else { "" }
    $errText = if (Test-Path $stderr) { Get-Content -Path $stderr -Raw } else { "" }
    Remove-Item -Path $stdout,$stderr -Force -ErrorAction SilentlyContinue
    return [pscustomobject]@{ ExitCode = $p.ExitCode; TimedOut = $false; StdOut = $outText; StdErr = $errText }
}

$taskExe = "C:\Windows\System32\schtasks.exe"
if (-not (Test-Path $taskExe)) {
    throw "schtasks not found: $taskExe"
}

$svc = Get-Service -Name Schedule -ErrorAction SilentlyContinue
if ($null -eq $svc) {
    throw "Task Scheduler service not found"
}

Write-Output "INFO: Schedule service status=$($svc.Status)"
if ($RestartService) {
    Write-Output "INFO: restarting Schedule service..."
    try {
        $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
        $isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
        if (-not $isAdmin) {
            Write-Warning "WARN: current shell is not elevated. skip restarting Schedule service."
        } else {
            Restart-Service -Name Schedule -Force
            Start-Sleep -Seconds 2
            $svc = Get-Service -Name Schedule
            Write-Output "INFO: Schedule service status after restart=$($svc.Status)"
        }
    } catch {
        Write-Warning "WARN: failed to restart Schedule service. continue with health check. detail: $($_.Exception.Message)"
    }
}

$r = Invoke-WithTimeout -FilePath $taskExe -ArgumentList "/Query /TN `"\Microsoft\Windows\Defrag\ScheduledDefrag`"" -TimeoutSeconds $TimeoutSeconds
if ($r.TimedOut) {
    Write-Warning "WARN: schtasks timed out after $TimeoutSeconds seconds."
    exit 3
}

if ($r.ExitCode -eq 0) {
    Write-Output "INFO: schtasks health check passed."
    exit 0
}

Write-Warning "WARN: schtasks health check failed exit=$($r.ExitCode)"
Write-Output "DETAIL: $($r.StdOut) $($r.StdErr)"
exit 2
