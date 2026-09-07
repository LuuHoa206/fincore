[CmdletBinding()]
param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly
)

$ErrorActionPreference = 'Stop'

if ($BackendOnly -and $FrontendOnly) {
    throw 'Choose either -BackendOnly or -FrontendOnly, not both.'
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $repositoryRoot '.env'

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw "Missing $environmentFile. Copy .env.example to .env, set local-only secrets, then run this script again."
}

Get-Content -LiteralPath $environmentFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#')) {
        return
    }

    if ($line -notmatch '^(?<name>[A-Z][A-Z0-9_]*)=(?<value>.*)$') {
        throw "Invalid .env entry: $line"
    }

    $value = $Matches.value.Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    Set-Item -Path "Env:$($Matches.name)" -Value $value
}

function Test-PortAvailable([int]$Port) {
    return -not (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Start-Backend {
    if (-not (Test-PortAvailable 8080)) {
        Write-Host 'Backend is already listening on http://localhost:8080.' -ForegroundColor Yellow
        return
    }

    $backendPath = Join-Path $repositoryRoot 'backend'
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', '.\\mvnw.cmd spring-boot:run' -WorkingDirectory $backendPath -WindowStyle Hidden
    Write-Host 'Started backend. Wait for http://localhost:8080/actuator/health/readiness to report UP.' -ForegroundColor Green
}

function Start-Frontend {
    if (-not (Test-PortAvailable 5173)) {
        Write-Host 'Frontend is already listening on http://localhost:5173.' -ForegroundColor Yellow
        return
    }

    $frontendPath = Join-Path $repositoryRoot 'frontend'
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'npm run dev -- --host 127.0.0.1' -WorkingDirectory $frontendPath -WindowStyle Hidden
    Write-Host 'Started frontend at http://localhost:5173.' -ForegroundColor Green
}

if (-not $FrontendOnly) {
    Start-Backend
}

if (-not $BackendOnly) {
    Start-Frontend
}

Write-Host 'Use Ctrl+C only when running processes in a visible terminal; this launcher starts them in the background.' -ForegroundColor DarkGray
