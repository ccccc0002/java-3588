param(
    [string]$MavenExecutable = "mvn",
    [string]$Test = "",
    [switch]$NoClean
)

$ErrorActionPreference = "Stop"

$goals = @()
if (-not $NoClean) {
    $goals += "clean"
}
$goals += "test"

$args = @()
$args += $goals
$args += "-DskipTests=false"

if ([string]::IsNullOrWhiteSpace($Test) -eq $false) {
    $args += ("-Dtest=" + $Test.Trim())
}

Write-Host "[TDD] Maven executable: $MavenExecutable"
Write-Host "[TDD] Running: $MavenExecutable $($args -join ' ')"

& $MavenExecutable @args
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    throw "[TDD] Test gate failed with exit code $exitCode"
}

Write-Host "[TDD] Test gate passed."
