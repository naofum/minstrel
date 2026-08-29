# Builds and starts the Minstrel Windows server, then opens the app in a browser.
#
# Requirements: JDK 21+ and Maven on PATH.
#
# The server
#   - serves the app (web/)  at http://127.0.0.1:8000/
#   - serves the data (data/) at http://127.0.0.1:8000/data/...
#   - exposes the Cordova plugin replacements at /api/commander, /api/librarian, /api/unzipper
#
# Place EPUB/CBZ/ABZ files into data/minstrel/ and press "Scan default directory"
# in the app on first run to import them.

$ErrorActionPreference = 'Stop'
$root     = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverDir = Join-Path $root 'server'
$webRoot  = Join-Path $root 'web'
$dataRoot = Join-Path $root 'data'
$jar      = Join-Path $serverDir 'target\minstrel-server.jar'
$port     = 8000

# --- build ---------------------------------------------------------------
Write-Host '== Building the Java server =='
Push-Location $serverDir
try {
    mvn -q -o package
    if ($LASTEXITCODE -ne 0) {
        mvn -q package
    }
} finally {
    Pop-Location
}
if (-not (Test-Path $jar)) {
    Write-Error 'Build failed: minstrel-server.jar was not produced.'
    exit 1
}

# --- data directories ----------------------------------------------------
if (-not (Test-Path $dataRoot)) { New-Item -ItemType Directory -Path $dataRoot | Out-Null }
$libDir = Join-Path $dataRoot 'minstrel'
if (-not (Test-Path $libDir)) {
    New-Item -ItemType Directory -Path $libDir | Out-Null
    Write-Host "== Created data directory: $libDir =="
    Write-Host '   Copy your EPUB / CBZ / ABZ files there.'
}

# --- run -----------------------------------------------------------------
Write-Host "== Starting server at http://127.0.0.1:$port/ =="
Write-Host '   Press Ctrl+C to stop.'
$process = Start-Process -FilePath 'java' `
    -ArgumentList @('-jar', $jar, "$port", $webRoot, $dataRoot) `
    -PassThru -NoNewWindow

# wait until the server responds, then open the browser
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 500
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$port/" -UseBasicParsing -TimeoutSec 1 | Out-Null
        break
    } catch {
        # not up yet
    }
}
Start-Process "http://127.0.0.1:$port/"

$process.WaitForExit()