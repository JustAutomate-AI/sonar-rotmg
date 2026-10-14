# Builds Sonar from the current source and puts it in the test install, leaving
# the copy the user actually plays with untouched.
#
# The test install lives apart on purpose: its own settings, saved characters and
# window layout, so trying an unfinished build can never disturb a real session.
param(
    [string]$Test = "E:\user\Documents\Peach Test",
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$workshop = Split-Path -Parent $PSScriptRoot

if (-not $SkipBuild) {
    Write-Host "Building..." -ForegroundColor Cyan
    Push-Location $workshop
    try {
        & "$workshop\gradlew.bat" shadowJar -q
        if ($LASTEXITCODE -ne 0) { throw "the build failed - nothing was deployed" }
    } finally {
        Pop-Location
    }
}

$jar = Get-ChildItem "$workshop\build\libs\Sonar.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw "no jar was produced in build\libs" }

# the workshop's own run folder is what the self-tests use, so keep it in step too
Copy-Item $jar.FullName "$workshop\run\Sonar.jar" -Force

if (-not (Test-Path "$Test\run")) { throw "no test install at $Test - create it before deploying" }
Copy-Item $jar.FullName "$Test\run\Sonar.jar" -Force

$version = Select-String -Path "$workshop\src\main\java\sonar\ui\Updater.java" -Pattern 'CURRENT\s*=\s*"([^"]+)"' |
    ForEach-Object { $_.Matches[0].Groups[1].Value } | Select-Object -First 1

Write-Host "Deployed Sonar $version to the test install." -ForegroundColor Green
Write-Host "  $Test\Start Peach Test.bat"
