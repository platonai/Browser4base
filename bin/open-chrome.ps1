#!/usr/bin/env pwsh

param (
    [switch]$Native
)

$repoRoot = (git rev-parse --show-toplevel 2>$null)
Set-Location $repoRoot

# Import common utility script
. $repoRoot\bin\common\Util.ps1

Fix-Encoding-UTF8

if (-not $Native) {
    Write-Host "Launching OpenChrome.kt..."

    # Compile first to ensure dependencies are ready
    & "$PSScriptRoot\..\mvnw.cmd" -pl examples/browser4-examples -am compile -D"skipTests"

    # Then run the specific project
    & "$PSScriptRoot\..\mvnw.cmd" -pl examples/browser4-examples exec:java -D"exec.mainClass=ai.platon.pulsar.tools.OpenChromeKt" -D"exec.classpathScope=test"

    if ($LASTEXITCODE -eq 0) {
        exit 0
    }
    Write-Warning "Failed to launch OpenChrome.kt. Falling back to native launcher..."
}

$chromePaths = @(
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
)

$chromePath = $null
foreach ($path in $chromePaths) {
    if (Test-Path -Path $path) {
        $chromePath = $path
        break
    }
}

if ($null -eq $chromePath) {
    Write-Error "Chrome executable not found."
    exit 1
}

$userDataDir = Join-Path -Path $env:USERPROFILE -ChildPath ".pulsar\browser\chrome\default\pulsar_chrome"

if (-not (Test-Path -Path $userDataDir)) {
    New-Item -ItemType Directory -Force -Path $userDataDir | Out-Null
}

Write-Host "Launching Chrome..."
Write-Host "EXE: $chromePath"
Write-Host "DIR: $userDataDir"

Start-Process -FilePath $chromePath -ArgumentList "--user-data-dir=`"$userDataDir`""
