param(
    [string]$TaskName = "Java3588_ContextAutosave",
    [int]$TimeoutSeconds = 15
)

$ErrorActionPreference = "Stop"

$taskExe = "C:\Windows\System32\schtasks.exe"
if (-not (Test-Path $taskExe)) {
    throw "schtasks not found: $taskExe"
}

Write-Output "INFO: schtasks executable found"

$svc = Get-Service -Name Schedule -ErrorAction SilentlyContinue
if ($null -eq $svc) {
    Write-Warning "WARN: Task Scheduler service not found."
    exit 2
}
if ($svc.Status -ne "Running") {
    Write-Warning "WARN: Task Scheduler service is not running. status=$($svc.Status)"
    exit 2
}
Write-Output "INFO: Task Scheduler service running"

function Invoke-Schtasks {
    param(
        [string]$ArgText,
        [int]$TimeoutSeconds
    )

    $stdout = [System.IO.Path]::GetTempFileName()
    $stderr = [System.IO.Path]::GetTempFileName()
    $p = Start-Process -FilePath $taskExe -ArgumentList $ArgText -NoNewWindow -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $finished = $p.WaitForExit($TimeoutSeconds * 1000)

    if (-not $finished) {
        try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
        $outText = if (Test-Path $stdout) { Get-Content -Path $stdout -Raw } else { "" }
        $errText = if (Test-Path $stderr) { Get-Content -Path $stderr -Raw } else { "" }
        Remove-Item -Path $stdout,$stderr -Force -ErrorAction SilentlyContinue
        return [pscustomobject]@{
            ExitCode = 124
            StdOut = $outText
            StdErr = $errText
            TimedOut = $true
        }
    }

    $outText = if (Test-Path $stdout) { Get-Content -Path $stdout -Raw } else { "" }
    $errText = if (Test-Path $stderr) { Get-Content -Path $stderr -Raw } else { "" }
    Remove-Item -Path $stdout,$stderr -Force -ErrorAction SilentlyContinue
    return [pscustomobject]@{
        ExitCode = $p.ExitCode
        StdOut = $outText
        StdErr = $errText
        TimedOut = $false
    }
}

$r2 = Invoke-Schtasks -ArgText "/Query /TN `"$TaskName`"" -TimeoutSeconds $TimeoutSeconds

if ($r2.TimedOut) {
    Write-Warning "WARN: schtasks /Query /TN timed out after $TimeoutSeconds seconds."
    exit 3
}

if ($r2.ExitCode -eq 0) {
    Write-Output "INFO: task exists: $TaskName"
    exit 0
}

$detailText = ($r2.StdOut + " " + $r2.StdErr).Trim()
if ($detailText -match "cannot find|找不到|系统找不到|The system cannot find") {
    Write-Output "INFO: task not found: $TaskName"
    if ($detailText) {
        Write-Output "DETAIL: $detailText"
    }
    exit 1
}

Write-Warning "WARN: failed to query specific task (exit=$($r2.ExitCode)). detail: $detailText"
exit 2
