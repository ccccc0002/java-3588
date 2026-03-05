param(
    [string]$Root = (Get-Location).Path,
    [string]$RecorderPath = "",
    [string]$RemoteUrl = "https://github.com/ccccc0002/java-3588.git",
    [string]$Branch = "main",
    [string]$Token = "",
    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RecorderPath)) {
    $RecorderPath = Join-Path $Root "ops/java-3588-recorder"
}

if (-not (Test-Path $RecorderPath)) {
    New-Item -Path $RecorderPath -ItemType Directory -Force | Out-Null
}

$readmePath = Join-Path $RecorderPath "README.md"
if (-not (Test-Path $readmePath)) {
    @(
        "# java-3588",
        "",
        "Process recording repository for ZLM + RK3588 migration collaboration.",
        "",
        "## Contents",
        "- docs/prd",
        "- process",
        "- state/compact",
        "- state/checkpoints"
    ) | Set-Content -Path $readmePath -Encoding UTF8
}

if (-not (Test-Path (Join-Path $RecorderPath ".git"))) {
    git -C $RecorderPath init -b $Branch | Out-Null
    git -C $RecorderPath config user.name "codex-bot"
    git -C $RecorderPath config user.email "codex-bot@example.com"
    git -C $RecorderPath remote add origin $RemoteUrl
}

function Copy-FolderContent {
    param(
        [string]$From,
        [string]$To
    )
    if (-not (Test-Path $From)) {
        return
    }
    if (-not (Test-Path $To)) {
        New-Item -Path $To -ItemType Directory -Force | Out-Null
    }
    Copy-Item -Path (Join-Path $From "*") -Destination $To -Recurse -Force
}

$targets = @(
    @{ from = (Join-Path $Root "docs/prd"); to = (Join-Path $RecorderPath "docs/prd") },
    @{ from = (Join-Path $Root "process"); to = (Join-Path $RecorderPath "process") },
    @{ from = (Join-Path $Root "state/compact"); to = (Join-Path $RecorderPath "state/compact") },
    @{ from = (Join-Path $Root "state/checkpoints"); to = (Join-Path $RecorderPath "state/checkpoints") }
)

foreach ($t in $targets) {
    Copy-FolderContent -From $t.from -To $t.to
}

git -C $RecorderPath add .

$staged = git -C $RecorderPath diff --cached --name-only
if (-not $staged) {
    Write-Output "OK: no new recorder changes"
    exit 0
}

$msg = "chore: sync process artifacts " + (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
git -C $RecorderPath commit -m $msg | Out-Null

if ($SkipPush) {
    Write-Output "OK: committed locally (push skipped)"
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Token)) {
    $Token = $env:GITHUB_TOKEN
}

if ([string]::IsNullOrWhiteSpace($Token)) {
    Write-Warning "No GitHub token found. Set -Token or env:GITHUB_TOKEN then rerun for push."
    Write-Output "OK: committed locally, pending push"
    exit 0
}

$raw = "x-access-token:$Token"
$bytes = [System.Text.Encoding]::UTF8.GetBytes($raw)
$basic = [Convert]::ToBase64String($bytes)

git -C $RecorderPath -c http.sslBackend=openssl -c ("http.extraHeader=AUTHORIZATION: basic " + $basic) push -u origin $Branch

Write-Output "OK: committed and pushed to $RemoteUrl"

