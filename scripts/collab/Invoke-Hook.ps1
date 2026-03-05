param(
    [Parameter(Mandatory = $true)][string]$Event,
    [string]$TaskId = "global",
    [string]$Stage = "unspecified",
    [string]$Summary = "",
    [string]$Root = (Get-Location).Path,
    [string]$Token = "",
    [switch]$SyncSkipPush
)

$ErrorActionPreference = "Stop"

$configFile = Join-Path $Root "hooks/hook-config.json"
if (-not (Test-Path $configFile)) {
    Write-Error "hook config missing: $configFile"
    exit 1
}

$config = Get-Content -Path $configFile -Raw | ConvertFrom-Json
$actions = @()

if ($config.event_actions.$Event) {
    $actions = @($config.event_actions.$Event)
} else {
    $actions = @($config.default_actions)
}

foreach ($action in $actions) {
    switch ($action) {
        "checkpoint" {
            & (Join-Path $PSScriptRoot "Save-Checkpoint.ps1") -TaskId $TaskId -Stage $Stage -Event $Event -Summary $Summary -Root $Root | Out-Null
        }
        "compact" {
            & (Join-Path $PSScriptRoot "Compact-Context.ps1") -TaskId $TaskId -Root $Root | Out-Null
        }
        "log" {
            & (Join-Path $PSScriptRoot "Append-ProcessLog.ps1") -TaskId $TaskId -Stage $Stage -Event $Event -Summary $Summary -Root $Root | Out-Null
        }
        "sync" {
            try {
                $syncScript = Join-Path $PSScriptRoot "Sync-RecorderRepo.ps1"
                if ($SyncSkipPush) {
                    & $syncScript -Root $Root -Token $Token -SkipPush | Out-Null
                } else {
                    & $syncScript -Root $Root -Token $Token | Out-Null
                }
                & (Join-Path $PSScriptRoot "Append-ProcessLog.ps1") -TaskId $TaskId -Stage $Stage -Event "sync_done" -Summary "event-driven sync completed" -Root $Root | Out-Null
            } catch {
                & (Join-Path $PSScriptRoot "Append-ProcessLog.ps1") -TaskId $TaskId -Stage $Stage -Event "sync_failed" -Summary ("event-driven sync failed: " + $_.Exception.Message) -Root $Root | Out-Null
                Write-Warning "sync action failed for event=${Event}: $($_.Exception.Message)"
            }
        }
        default {
            Write-Error "unknown action in hook config: $action"
            exit 2
        }
    }
}

Write-Output "OK: hook invoked event=$Event actions=$($actions -join ',')"
