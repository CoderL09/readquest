param([switch]$SkipFrontendInstall)
$ErrorActionPreference='Stop'
$frontendPath=Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend'
if (-not (Test-Path -LiteralPath (Join-Path $frontendPath 'package.json'))) {throw 'Place backend-java beside the frontend directory.'}
Push-Location $frontendPath
try {
    if (-not $SkipFrontendInstall) {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) {throw 'Frontend dependency installation failed.'}
    }
    & npm.cmd run check
    if ($LASTEXITCODE -ne 0) {throw 'Frontend build failed.'}
} finally {Pop-Location}
Push-Location $PSScriptRoot
try {
    & mvn.cmd -Pfrontend clean verify
    if ($LASTEXITCODE -ne 0) {throw 'Java verification or packaging failed.'}
    Write-Host 'Built target/readquest-backend-0.1.0-SNAPSHOT.jar with the frontend included.'
} finally {Pop-Location}
