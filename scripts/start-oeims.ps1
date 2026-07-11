[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 5173,

    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "docker-compose.yml"
$envFile = Join-Path $repoRoot ".env"

function New-RandomSecret {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $generator.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    }
    finally {
        $generator.Dispose()
    }
}

function Get-LanAddress {
    if (-not (Get-Command Get-NetRoute -ErrorAction SilentlyContinue)) {
        return $null
    }

    $route = Get-NetRoute `
        -AddressFamily IPv4 `
        -DestinationPrefix "0.0.0.0/0" `
        -ErrorAction SilentlyContinue |
        Sort-Object RouteMetric |
        Select-Object -First 1

    if (-not $route) {
        return $null
    }

    return Get-NetIPAddress `
        -AddressFamily IPv4 `
        -InterfaceIndex $route.InterfaceIndex `
        -AddressState Preferred `
        -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike "169.254.*" } |
        Select-Object -ExpandProperty IPAddress -First 1
}

if (-not (Test-Path $composeFile)) {
    throw "Could not find docker-compose.yml at $composeFile."
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker Desktop is required and was not found in PATH."
}

& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker is installed but is not running. Start Docker Desktop and try again."
}

if (-not (Test-Path $envFile)) {
    Set-Content `
        -LiteralPath $envFile `
        -Value "JWT_SECRET=$(New-RandomSecret)" `
        -Encoding Ascii
}
elseif (-not (Select-String -LiteralPath $envFile -Pattern '^JWT_SECRET=.+$' -Quiet)) {
    Add-Content `
        -LiteralPath $envFile `
        -Value "JWT_SECRET=$(New-RandomSecret)" `
        -Encoding Ascii
}

$previousPort = $env:OEIMS_PORT
$env:OEIMS_PORT = $Port.ToString()

Push-Location $repoRoot
try {
    & docker compose --env-file $envFile -f $composeFile up --build -d

    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose could not start OEIMS."
    }
}
finally {
    Pop-Location

    if ($null -eq $previousPort) {
        Remove-Item Env:\OEIMS_PORT -ErrorAction SilentlyContinue
    }
    else {
        $env:OEIMS_PORT = $previousPort
    }
}

$localUrl = "http://localhost:$Port"
$deadline = (Get-Date).AddMinutes(2)
$ready = $false

while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest `
            -Uri $localUrl `
            -UseBasicParsing `
            -TimeoutSec 3

        if ($response.StatusCode -lt 500) {
            $ready = $true
            break
        }
    }
    catch {
        Start-Sleep -Seconds 2
    }
}

if (-not $ready) {
    throw "OEIMS containers started, but the web interface was not ready after two minutes. Run 'docker compose logs' from the repository root."
}

$lanAddress = Get-LanAddress

Write-Host ""
Write-Host "OEIMS is ready." -ForegroundColor Green
Write-Host "Professor console: $localUrl"

if ($lanAddress) {
    Write-Host "Student access:    http://${lanAddress}:$Port"
}

if (-not $NoBrowser) {
    Start-Process $localUrl
}
