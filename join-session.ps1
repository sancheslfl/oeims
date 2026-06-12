<#
.SYNOPSIS
    Registers (or reuses) a test student, joins a session by code, and writes
    the resulting token + participantId into all daemon config locations that
    may be used by development, Release, or the published Windows Service.

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

.PARAMETER TargetFramework
    Daemon target framework used by Release output. Defaults to net8.0.

.PARAMETER PublishDir
    Published Windows Service folder. Defaults to C:\OEIMS\Client.

.PARAMETER PublishDaemon
    Publishes the daemon to PublishDir before patching config files.

.EXAMPLE
    .\join-session.ps1 -Code A3F9

.EXAMPLE
    .\join-session.ps1 -Code A3F9 -PublishDaemon

.EXAMPLE
    .\join-session.ps1 -Code A3F9 -ApiBaseUrl http://localhost:8080/api -RealtimeBaseUrl ws://localhost:8080
#>
param(
    [Parameter(Mandatory)]
    [string] $Code,

    [string] $Email = "A49347@alunos.isel.pt",
    [string] $Password = "Test1234!",
    [string] $ApiBaseUrl = "http://localhost:8080/api",
    [string] $RealtimeBaseUrl = "",
    [string] $TargetFramework = "net8.0",
    [string] $PublishDir = "C:\OEIMS\Client",

    [switch] $PublishDaemon
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

function Set-DaemonServerConfig {
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
        [string] $ParticipantId,

        [bool] $CreateIfMissing = $false
    )

    if (-not (Test-Path $Path)) {
        if (-not $CreateIfMissing) {
            Write-Host "Skipping missing config: $Path" -ForegroundColor DarkGray
            return
        }

        $directory = Split-Path $Path -Parent

        if (-not (Test-Path $directory)) {
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
        }

        $settings = [pscustomobject]@{}
    } else {
        $content = Get-Content $Path -Raw

        if ([string]::IsNullOrWhiteSpace($content)) {
            $settings = [pscustomobject]@{}
        } else {
            $settings = $content | ConvertFrom-Json
        }
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

$daemonProjectDir = Join-Path $PSScriptRoot "Daemon"
$daemonProjectPath = Join-Path $daemonProjectDir "Daemon.csproj"

$jsonHeader = @{ "Content-Type" = "application/json" }

Write-Host ""
Write-Host "OEIMS - student session setup" -ForegroundColor Cyan
Write-Host "Code: $Code | Student: $Email"
Write-Host "API base URL: $ApiBaseUrl"
Write-Host "Realtime base URL: $RealtimeBaseUrl"
Write-Host "Publish dir: $PublishDir"
Write-Host ""

# 1. Register
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

# 2. Login
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

# 3. Join session
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

# 4. Publish and patch configs
Write-Host "[4/4] Updating daemon configuration..." -ForegroundColor Cyan

if ($PublishDaemon) {
    Write-Host "Publishing daemon to $PublishDir..." -ForegroundColor Yellow

    dotnet publish $daemonProjectPath `
        -c Release `
        -o $PublishDir `
        --self-contained false

    Write-Host "Published daemon." -ForegroundColor Green
}

$configTargets = @(
    @{
        Path = Join-Path $daemonProjectDir "appsettings.json"
        CreateIfMissing = $false
    },
    @{
        Path = Join-Path $daemonProjectDir "appsettings.Development.json"
        CreateIfMissing = $false
    },
    @{
        Path = Join-Path $daemonProjectDir "appsettings.Production.json"
        CreateIfMissing = $false
    },
    @{
        Path = Join-Path $daemonProjectDir "bin\Release\$TargetFramework\appsettings.json"
        CreateIfMissing = $false
    },
    @{
        Path = Join-Path $daemonProjectDir "bin\Release\$TargetFramework\appsettings.Development.json"
        CreateIfMissing = $false
    },
    @{
        Path = Join-Path $daemonProjectDir "bin\Release\$TargetFramework\appsettings.Production.json"
        CreateIfMissing = $false
    },
    @{
        Path = Join-Path $PublishDir "appsettings.json"
        CreateIfMissing = $true
    },
    @{
        Path = Join-Path $PublishDir "appsettings.Development.json"
        CreateIfMissing = $true
    },
    @{
        Path = Join-Path $PublishDir "appsettings.Production.json"
        CreateIfMissing = $true
    }
)

foreach ($target in $configTargets) {
    Set-DaemonServerConfig `
        -Path $target.Path `
        -ApiBaseUrl $ApiBaseUrl `
        -RealtimeBaseUrl $RealtimeBaseUrl `
        -Token $token `
        -ParticipantId $join.participantId `
        -CreateIfMissing $target.CreateIfMissing
}

Write-Host ""
Write-Host "Daemon configuration patched." -ForegroundColor Cyan
Write-Host ""
Write-Host "  ApiBaseUrl      : $ApiBaseUrl"
Write-Host "  RealtimeBaseUrl : $RealtimeBaseUrl"
Write-Host "  TokenLength     : $($token.Length)"
Write-Host "  ParticipantId   : $($join.participantId)"
Write-Host "  SessionId       : $($join.sessionId)"
Write-Host ""
Write-Host "If using Visual Studio Release:" -ForegroundColor Yellow
Write-Host "  Run the daemon again after this script patched the Release output." -ForegroundColor Yellow
Write-Host ""
Write-Host "If using the Windows Service:" -ForegroundColor Yellow
Write-Host "  Restart-Service oeims" -ForegroundColor Yellow
Write-Host ""
Write-Host "Or publish + patch in one command:" -ForegroundColor Yellow
Write-Host "  .\join-session.ps1 -Code $Code -PublishDaemon" -ForegroundColor Yellow
Write-Host ""