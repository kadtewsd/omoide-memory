<#
.SYNOPSIS
    One-stop build and run script for omoide-memory-sharing on LAN.

.DESCRIPTION
    Builds the frontend and backend (if needed), and runs the application in preview mode (npm run preview).
    All output and messages are in English to prevent encoding/parser errors.

.PARAMETER FrontendPort
    Port for the frontend server (default: 5173).

.PARAMETER BackendPort
    Port for the backend server (default: 8080).

.PARAMETER SkipFirewall
    Skip Windows Firewall rule configuration.

.PARAMETER SkipBuild
    Skip building the frontend and backend.

.PARAMETER NoLaunch
    Build only; do not start the servers.
#>

param (
    [Parameter(Mandatory = $false)]
    [int]$FrontendPort = 5173,

    [Parameter(Mandatory = $false)]
    [int]$BackendPort = 8080,

    [Parameter(Mandatory = $false)]
    [switch]$SkipFirewall,

    [Parameter(Mandatory = $false)]
    [switch]$SkipBuild,

    [Parameter(Mandatory = $false)]
    [switch]$NoLaunch
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$RepoRoot = $PSScriptRoot
$BackendDir = Join-Path $RepoRoot "backend"
$FrontendDir = Join-Path $RepoRoot "frontend"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   omoide-memory-sharing LAN Deployment Tool             " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Frontend Port : $FrontendPort" -ForegroundColor Yellow
Write-Host "Backend Port  : $BackendPort" -ForegroundColor Yellow
Write-Host "Working Dir   : $RepoRoot" -ForegroundColor Yellow
Write-Host ""

# --------------------------------------------------
# 1. Firewall rules
# --------------------------------------------------
if (-not $SkipFirewall) {
    $currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    $isAdmin = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

    if ($isAdmin) {
        Write-Host "[1/3] Configuring Windows Firewall inbound rules..." -ForegroundColor Cyan
        $rules = @(
            @{ Name = "OmoideMemorySharingFrontend"; Port = $FrontendPort },
            @{ Name = "OmoideMemorySharingBackend"; Port = $BackendPort }
        )
        foreach ($r in $rules) {
            $existing = Get-NetFirewallRule -Name $r.Name -ErrorAction SilentlyContinue
            if ($existing) {
                Remove-NetFirewallRule -Name $r.Name
            }
            New-NetFirewallRule `
                -Name $r.Name `
                -DisplayName "$($r.Name) (Port $($r.Port))" `
                -Direction Inbound `
                -Action Allow `
                -Protocol TCP `
                -LocalPort $r.Port | Out-Null
        }
        Write-Host "  -> Firewall rules configured successfully." -ForegroundColor Green
    } else {
        Write-Host "[1/3] Skipped firewall configuration (administrator privileges not detected)." -ForegroundColor Yellow
    }
} else {
    Write-Host "[1/3] Firewall configuration skipped." -ForegroundColor Gray
}

# --------------------------------------------------
# 2. Backend JAR
# --------------------------------------------------
Write-Host "`n[2/3] Preparing Backend (Spring Boot)..." -ForegroundColor Cyan
$buildJarDir = Join-Path $BackendDir "build\libs"

if (-not $SkipBuild) {
    Write-Host "  Building Backend with gradlew..." -ForegroundColor Gray
    Set-Location $BackendDir
    if (Test-Path ".\gradlew.bat") {
        .\gradlew.bat build -x test
    } else {
        sh gradlew build -x test
    }
}

$jarFile = Get-ChildItem -Path $buildJarDir -Filter "*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1

if (-not $jarFile) {
    Write-Error "Backend JAR file not found in $buildJarDir"
    exit 1
}
$backendJarPath = $jarFile.FullName
Write-Host "  -> Backend JAR ready: $backendJarPath" -ForegroundColor Green

# --------------------------------------------------
# 3. Frontend Build
# --------------------------------------------------
Write-Host "`n[3/3] Preparing Frontend (React + Vite)..." -ForegroundColor Cyan
Set-Location $FrontendDir

if (-not $SkipBuild) {
    # Remove stale TypeScript-compiled artifacts that shadow vite.config.ts on Windows.
    # If these files exist, Vite prefers the .js over the .ts, causing @ alias resolution to fail.
    $staleArtifacts = @("vite.config.js", "vite.config.d.ts", "tsconfig.tsbuildinfo", "tsconfig.node.tsbuildinfo")
    foreach ($artifact in $staleArtifacts) {
        $artifactPath = Join-Path $FrontendDir $artifact
        if (Test-Path $artifactPath) {
            Remove-Item $artifactPath -Force
            Write-Host "  Removed stale artifact: $artifact" -ForegroundColor DarkYellow
        }
    }

    Write-Host "  Running npm run build..." -ForegroundColor Gray
    npm run build
    $distDir = Join-Path $FrontendDir "dist"
    if (-not (Test-Path $distDir)) {
        Write-Error "Frontend dist directory not found: $distDir"
        exit 1
    }
    Write-Host "  -> Frontend build completed." -ForegroundColor Green
} else {
    Write-Host "  -> Frontend build skipped." -ForegroundColor Gray
}

Set-Location $RepoRoot

# --------------------------------------------------
# Access URL Information
# --------------------------------------------------
$computerName = $env:COMPUTERNAME
$localIPs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | `
            Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | `
            Select-Object -ExpandProperty IPAddress

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "   Build completed successfully!                          " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "LAN Access URLs for mobile and other devices:" -ForegroundColor Yellow
Write-Host "  1. Hostname (mDNS):" -ForegroundColor Cyan
Write-Host "     http://$($computerName.ToLower()).local:$FrontendPort" -ForegroundColor White
Write-Host "  2. Local IP addresses:" -ForegroundColor Cyan
foreach ($ip in $localIPs) {
    Write-Host "     http://${ip}:$FrontendPort" -ForegroundColor White
}
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""

if ($NoLaunch) {
    Write-Host "NoLaunch flag is set. Exiting without starting servers." -ForegroundColor Yellow
    exit 0
}

# --------------------------------------------------
# Launch Services
# --------------------------------------------------
Write-Host "Starting Backend and Frontend Preview..." -ForegroundColor Cyan
Write-Host "Press Ctrl+C to stop both services." -ForegroundColor Yellow
Write-Host ""

$backendProcess = $null

try {
    # Check if backend port is already in use
    $portActive = Get-NetTCPConnection -LocalPort $BackendPort -ErrorAction SilentlyContinue | Where-Object { $_.State -eq 'Listen' }
    if ($portActive) {
        Write-Host "Terminating existing process on port $BackendPort (PID: $($portActive.OwningProcess))..." -ForegroundColor Yellow
        Stop-Process -Id $portActive.OwningProcess -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
    Write-Host "Starting Backend on port $BackendPort..." -ForegroundColor Cyan
    $backendProcess = Start-Process -FilePath "java" -ArgumentList "-jar", "`"$backendJarPath`"" -PassThru -NoNewWindow
    Write-Host "Backend process started (PID: $($backendProcess.Id))." -ForegroundColor Green

    # Run frontend in preview mode (foreground)
    Write-Host "Starting Frontend (npm run preview)..." -ForegroundColor Cyan
    Set-Location $FrontendDir
    npm run preview -- --host 0.0.0.0 --port $FrontendPort
} finally {
    Write-Host "`nStopping services..." -ForegroundColor Yellow
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Write-Host "Stopping Backend (PID: $($backendProcess.Id))..." -ForegroundColor Yellow
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
    }
    Write-Host "All services stopped." -ForegroundColor Green
}
