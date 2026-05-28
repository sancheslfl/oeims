<#
.SYNOPSIS
    Registers (or reuses) a test student, joins a session by code, and writes
    the resulting token + participantId into Daemon/appsettings.Development.json
    so the daemon can be started immediately.

.PARAMETER Code
    Session code shown on the professor console (e.g. A3F9). Mandatory.

.PARAMETER Email
    Student email to register/login with. Defaults to A49347@alunos.isel.pt.

.PARAMETER Password
    Student password. Defaults to Test1234!

.PARAMETER ApiBaseUrl
    REST API base URL. Defaults to http://localhost:8080/api.

.PARAMETER RealtimeBaseUrl
    Realtime server base URL for WebSocket connections.
    Defaults to the ApiBaseUrl host converted to ws:// and without /api.

.EXAMPLE
    .\join-session.ps1 -Code A3F9
    .\join-session.ps1 -Code A3F9 -Email alice@isel.pt -Password hunter2
    .\join-session.ps1 -Code A3F9 -ApiBaseUrl http://localhost:8080/api -RealtimeBaseUrl ws://localhost:8080
#>
param(
    [Parameter(Mandatory)]
    [string] $Code,

    [string] $Email    = "A49347@alunos.isel.pt",
    [string] $Password = "Test1234!",
    [string] $ApiBaseUrl = "http://localhost:8080/api",
    [string] $RealtimeBaseUrl = ""
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

$ApiBaseUrl = $ApiBaseUrl.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($RealtimeBaseUrl)) {
    $RealtimeBaseUrl = Get-RealtimeBaseUrl -ApiBaseUrl $ApiBaseUrl
} else {
    $RealtimeBaseUrl = $RealtimeBaseUrl.TrimEnd('/')
}

$jsonHeader = @{ "Content-Type" = "application/json" }

Write-Host ""
Write-Host "OEIMS - student session setup" -ForegroundColor Cyan
Write-Host "Code: $Code | Student: $Email"
Write-Host "API base URL: $ApiBaseUrl"
Write-Host "Realtime base URL: $RealtimeBaseUrl"
Write-Host ""

# 1. Register
Write-Host "[1/3] Registering student..." -NoNewline
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

# 2. Login
Write-Host "[2/3] Logging in..." -NoNewline
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

# 3. Join session
Write-Host "[3/3] Joining session '$Code'..." -NoNewline
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

# 4. Patch Daemon/appsettings.Development.json
$settingsPath = Join-Path $PSScriptRoot "Daemon\appsettings.Development.json"
$settings = Get-Content $settingsPath -Raw | ConvertFrom-Json

if (-not $settings.PSObject.Properties["Server"]) {
    $settings | Add-Member -NotePropertyName "Server" -NotePropertyValue ([pscustomobject]@{})
}

Set-JsonProperty -Object $settings.Server -Name "Enabled" -Value $true
Set-JsonProperty -Object $settings.Server -Name "ApiBaseUrl" -Value $ApiBaseUrl
Set-JsonProperty -Object $settings.Server -Name "RealtimeBaseUrl" -Value $RealtimeBaseUrl
Set-JsonProperty -Object $settings.Server -Name "Token" -Value $token
Set-JsonProperty -Object $settings.Server -Name "ParticipantId" -Value $join.participantId

# Remove old BaseUrl to avoid accidentally using the wrong path later.
if ($settings.Server.PSObject.Properties["BaseUrl"]) {
    $settings.Server.PSObject.Properties.Remove("BaseUrl")
}

$updated = $settings | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($settingsPath, $updated)

Write-Host ""
Write-Host "appsettings.Development.json patched." -ForegroundColor Cyan
Write-Host ""
Write-Host "  ApiBaseUrl      : $ApiBaseUrl"
Write-Host "  RealtimeBaseUrl : $RealtimeBaseUrl"
Write-Host "  Token           : $token"
Write-Host "  ParticipantId   : $($join.participantId)"
Write-Host "  SessionId       : $($join.sessionId)"
Write-Host ""
Write-Host "Start the daemon:" -ForegroundColor Yellow
Write-Host "  cd Daemon; dotnet run" -ForegroundColor Yellow
Write-Host ""