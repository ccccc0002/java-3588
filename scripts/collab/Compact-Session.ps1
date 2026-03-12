param(
    [string]$TaskId = "global",
    [string]$Stage = "compact",
    [string]$Summary = "manual compact requested",
    [string]$Root = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "Invoke-Hook.ps1") -Event "session_compacted" -TaskId $TaskId -Stage $Stage -Summary $Summary -Root $Root | Out-Null

Write-Output "OK: session compacted"
