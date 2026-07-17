[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 5173,

    [ValidateRange(1, 65535)]
    [int]$LoopbackPort = 17653,

    [ValidateSet("win-x64", "win-arm64")]
    [string]$Runtime = "win-x64"
)

$ErrorActionPreference = "Stop"

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw "The full OEIMS bootstrap currently requires Windows because it installs Sentinel."
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)

if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Open PowerShell as Administrator and run this script again."
}

$startScript = Join-Path $PSScriptRoot "start-oeims.ps1"
$installScript = Join-Path $PSScriptRoot "install-sentinel.ps1"
$localUrl = "http://localhost:$Port"

& $startScript -Port $Port -NoBrowser
& $installScript `
    -ServerUrl $localUrl `
    -LoopbackPort $LoopbackPort `
    -Runtime $Runtime

Start-Process $localUrl

Write-Host ""
Write-Host "The complete local OEIMS system is running." -ForegroundColor Green
Write-Host "Open: $localUrl"
