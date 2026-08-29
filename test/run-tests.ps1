# Builds and starts the Minstrel Windows server, runs the end-to-end regression
# tests (test/regression.js), then stops the server.
#
# Requirements: JDK 21+, Maven, Node.js and an installed Edge/Chrome browser.
# First run:  npm install   (in the test/ directory)

$ErrorActionPreference = 'Stop'
$root      = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$serverDir = Join-Path $root 'server'
$testDir   = Join-Path $root 'test'
$webRoot   = Join-Path $root 'web'
$dataRoot  = Join-Path $root 'data'
$jar       = Join-Path $serverDir 'target\minstrel-server.jar'
$port      = 8001

# --- install deps ----------------------------------------------------
if (-not (Test-Path (Join-Path $testDir 'node_modules'))) {
    Write-Host '== Installing test dependencies (puppeteer-core) =='
    Push-Location $testDir
    try { npm install } finally { Pop-Location }
}

# --- build -----------------------------------------------------------
Write-Host '== Building the Java server =='
Push-Location $serverDir
try {
    mvn -q -o package
    if ($LASTEXITCODE -ne 0) { mvn -q package }
} finally {
    Pop-Location
}
if (-not (Test-Path $jar)) {
    Write-Error 'Build failed: minstrel-server.jar was not produced.'
    exit 1
}

# --- run server + tests ---------------------------------------------
Write-Host "== Starting server on port $port =="
$process = Start-Process -FilePath 'java' `
    -ArgumentList @('-jar', $jar, "$port", $webRoot, $dataRoot) `
    -PassThru -NoNewWindow -RedirectStandardOutput (Join-Path $root 'server-out.txt') `
    -RedirectStandardError (Join-Path $root 'server-err.txt')

try {
    # wait for the server
    $up = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            Invoke-WebRequest -Uri "http://127.0.0.1:$port/" -UseBasicParsing -TimeoutSec 1 | Out-Null
            $up = $true
            break
        } catch { }
    }
    if (-not $up) { throw 'Server did not start in time.' }

    Write-Host "== Running regression tests =="
    $env:BASE_URL = "http://127.0.0.1:$port/"
    Push-Location $testDir
    try {
        node regression.js
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($code -ne 0) { Write-Error 'Regression tests FAILED.' }
    else { Write-Host 'Regression tests PASSED.' }
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
    }
    Remove-Item -Force (Join-Path $root 'server-out.txt'), (Join-Path $root 'server-err.txt') -ErrorAction SilentlyContinue
}