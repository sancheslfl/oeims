<#
.SYNOPSIS
    Registers/logs in a test student, joins a session by code,
    and writes token + participantId into Sentinel Service config.

.EXAMPLE
    .\join-session.ps1 -Code A3F9

.EXAMPLE
    .\join-session.ps1 -Code A3F9 -PublishService
#>

param(
    [Parameter(Mandatory)]
    [string] $Code,

    [string] $Email = "A49347@alunos.isel.pt",
    [string] $Password = "Test1234!",
    [string] $ApiBaseUrl = "http://localhost:8080/api",
    [string] $RealtimeBaseUrl = "",

    # Installed Windows Service folder
    [string] $PublishDir = "C:\OEIMS\Client",

    [switch] $PublishService
)

$ErrorActionPreference = "Stop"

function Get-RealtimeBaseUrl {
    param(
        [Parameter(Mandatory)]
        [string] $ApiBaseUrl
    )

    $baseUrl = $ApiBaseUrl.TrimEnd('/')

    if ($baseUrl.EndsWith("/api")) {
        $baseUrl = $baseUrl.Substring(0, $baseUrl.Length - 4)
    }

    if ($baseUrl.StartsWith("https://")) {
        return "wss://" + $baseUrl.Substring("https://".Length)
    }

    if ($baseUrl.StartsWith("http://")) {
        return "ws://" + $baseUrl.Substring("http://".Length)
    }

    return $baseUrl
}

function Find-ServiceProjectDir {
    $candidates = @(
        (Join-Path $PSScriptRoot "Sentinel\Service"),
        (Join-Path $PSScriptRoot "Service"),
        $PSScriptRoot
    )

    foreach ($candidate in $candidates) {
        $projectPath = Join-Path $candidate "Service.csproj"

        if (Test-Path $projectPath) {
            return $candidate
        }
    }

    throw "Could not find Service.csproj. Put this script in PROJ/, Sentinel/, or Sentinel/Service/."
}

function Set-JsonProperty {
    param(
        [Parameter(Mandatory)]
        [object] $Object,

        [Parameter(Mandatory)]
        [string] $Name,

        [Parameter(Mandatory)]
        [AllowNull()]
        [object] $Value
    )

    if ($Object.PSObject.Properties[$Name]) {
        $Object.$Name = $Value
    } else {
        $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    }
}

function Set-ServiceServerConfig {
    param(
        [Parameter(Mandatory)]
        [string] $Path,

        [Parameter(Mandatory)]
        [string] $ApiBaseUrl,

        [Parameter(Mandatory)]
        [string] $RealtimeBaseUrl,

        [Parameter(Mandatory)]
        [string] $Token,

        [Parameter(Mandatory)]
        [string] $ParticipantId
    )

    $directory = Split-Path $Path -Parent

    if (-not (Test-Path $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    if (Test-Path $Path) {
        $content = Get-Content $Path -Raw

        if ([string]::IsNullOrWhiteSpace($content)) {
            $settings = [pscustomobject]@{}
        } else {
            $settings = $content | ConvertFrom-Json
        }
    } else {
        $settings = [pscustomobject]@{}
    }

    if (-not $settings.PSObject.Properties["Server"]) {
        $settings | Add-Member -NotePropertyName "Server" -NotePropertyValue ([pscustomobject]@{})
    }

    Set-JsonProperty -Object $settings.Server -Name "Enabled" -Value $true
    Set-JsonProperty -Object $settings.Server -Name "ApiBaseUrl" -Value $ApiBaseUrl
    Set-JsonProperty -Object $settings.Server -Name "RealtimeBaseUrl" -Value $RealtimeBaseUrl
    Set-JsonProperty -Object $settings.Server -Name "Token" -Value $Token
    Set-JsonProperty -Object $settings.Server -Name "ParticipantId" -Value $ParticipantId

    if ($settings.Server.PSObject.Properties["BaseUrl"]) {
        $settings.Server.PSObject.Properties.Remove("BaseUrl")
    }

    [System.IO.File]::WriteAllText(
        $Path,
        ($settings | ConvertTo-Json -Depth 10)
    )

    Write-Host "Patched: $Path" -ForegroundColor Green
}

$ApiBaseUrl = $ApiBaseUrl.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($RealtimeBaseUrl)) {
    $RealtimeBaseUrl = Get-RealtimeBaseUrl -ApiBaseUrl $ApiBaseUrl
} else {
    $RealtimeBaseUrl = $RealtimeBaseUrl.TrimEnd('/')
}

$serviceProjectDir = Find-ServiceProjectDir
$serviceProjectPath = Join-Path $serviceProjectDir "Service.csproj"

$jsonHeader = @{
    "Content-Type" = "application/json"
}

Write-Host ""
Write-Host "OEIMS - Sentinel session setup" -ForegroundColor Cyan
Write-Host "Code: $Code | Student: $Email"
Write-Host "API base URL: $ApiBaseUrl"
Write-Host "Realtime base URL: $RealtimeBaseUrl"
Write-Host "Visual Studio config dir: $serviceProjectDir"

if ($PublishService) {
    Write-Host "Windows Service config dir: $PublishDir"
} else {
    Write-Host "Windows Service config dir: skipped because -PublishService was not used" -ForegroundColor DarkGray
}

Write-Host ""

Write-Host "[1/4] Registering student..." -NoNewline
try {
    $body = [ordered]@{
        email = $Email
        password = $Password
        role = "STUDENT"
    } | ConvertTo-Json

    $null = Invoke-RestMethod `
        -Uri "$ApiBaseUrl/auth/register" `
        -Method Post `
        -Body $body `
        -Headers $jsonHeader

    Write-Host " done." -ForegroundColor Green
} catch {
    Write-Host " already registered." -ForegroundColor DarkGray
}

Write-Host "[2/4] Logging in..." -NoNewline
$body = [ordered]@{
    email = $Email
    password = $Password
} | ConvertTo-Json

$auth = Invoke-RestMethod `
    -Uri "$ApiBaseUrl/auth/login" `
    -Method Post `
    -Body $body `
    -Headers $jsonHeader

$token = $auth.token
Write-Host " ok." -ForegroundColor Green

Write-Host "[3/4] Joining session '$Code'..." -NoNewline
$authHeaders = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $token"
}

$body = [ordered]@{
    code = $Code
} | ConvertTo-Json

$join = Invoke-RestMethod `
    -Uri "$ApiBaseUrl/sessions/join" `
    -Method Post `
    -Body $body `
    -Headers $authHeaders

Write-Host " joined." -ForegroundColor Green
Write-Host "   Exam:     $($join.examTitle)" -ForegroundColor DarkGray
Write-Host "   Duration: $($join.durationMins) min" -ForegroundColor DarkGray

Write-Host "[4/4] Updating Sentinel Service configuration..." -ForegroundColor Cyan

if ($PublishService) {
    Write-Host "Publishing service to $PublishDir..." -ForegroundColor Yellow

    dotnet publish $serviceProjectPath `
        -c Release `
        -o $PublishDir `
        --self-contained false

    Write-Host "Published service." -ForegroundColor Green
}

$configTargets = @(
    # Visual Studio/source config
    (Join-Path $serviceProjectDir "appsettings.json"),
    (Join-Path $serviceProjectDir "appsettings.Development.json"),
    (Join-Path $serviceProjectDir "appsettings.Production.json")
)

if ($PublishService) {
    # Installed Windows Service config
    $configTargets += @(
        (Join-Path $PublishDir "appsettings.json"),
        (Join-Path $PublishDir "appsettings.Development.json"),
        (Join-Path $PublishDir "appsettings.Production.json")
    )
}

foreach ($path in $configTargets) {
    Set-ServiceServerConfig `
        -Path $path `
        -ApiBaseUrl $ApiBaseUrl `
        -RealtimeBaseUrl $RealtimeBaseUrl `
        -Token $token `
        -ParticipantId $join.participantId
}

Write-Host ""
Write-Host "Sentinel Service configuration patched." -ForegroundColor Cyan
Write-Host ""
Write-Host "  ApiBaseUrl      : $ApiBaseUrl"
Write-Host "  RealtimeBaseUrl : $RealtimeBaseUrl"
Write-Host "  TokenLength     : $($token.Length)"
Write-Host "  ParticipantId   : $($join.participantId)"
Write-Host "  SessionId       : $($join.sessionId)"
Write-Host ""

Write-Host "For Visual Studio:" -ForegroundColor Yellow
Write-Host "  Run Service again after this script patches the source config." -ForegroundColor Yellow
Write-Host ""

if ($PublishService) {
    Write-Host "For Windows Service:" -ForegroundColor Yellow
    Write-Host "  Restart-Service oeims" -ForegroundColor Yellow
    Write-Host ""
} else {
    Write-Host "For Windows Service:" -ForegroundColor DarkGray
    Write-Host "  Not patched. Use -PublishService when you want to publish and patch C:\OEIMS\Client." -ForegroundColor DarkGray
    Write-Host ""
}

Write-Host "Publish + patch in one command:" -ForegroundColor Yellow
Write-Host "  .\join-session.ps1 -Code $Code -PublishService" -ForegroundColor Yellow
Write-Host ""