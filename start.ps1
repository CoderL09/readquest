param([switch]$Build)
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'backend-java/start-local.ps1') -Build:$Build
