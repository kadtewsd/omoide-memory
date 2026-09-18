<#
.SYNOPSIS
    Script to configure Windows Firewall rules for LAN access (Frontend and Backend).

.DESCRIPTION
    Adds Windows Firewall inbound rules to allow other devices on the LAN to access
    the frontend app (Vite: 5173) and backend API (Spring Boot: 8080).
    Requires administrator privileges.

.PARAMETER FrontendPort
    TCP port for frontend (default: 5173).

.PARAMETER BackendPort
    TCP port for backend (default: 8080).

.PARAMETER RuleNamePrefix
    Prefix for firewall rule names (default: OmoideMemorySharing).

.EXAMPLE
    .\allow-frontend-firewall-port.ps1
#>

param (
    [Parameter(Mandatory = $false, HelpMessage = "Frontend TCP port to allow (e.g. 5173)")]
    [Alias("Port")]
    [int]$FrontendPort = 5173,

    [Parameter(Mandatory = $false, HelpMessage = "Backend TCP port to allow (e.g. 8080)")]
    [int]$BackendPort = 8080,

    [Parameter(Mandatory = $false, HelpMessage = "Prefix for firewall rule names")]
    [Alias("RuleName")]
    [string]$RuleNamePrefix = "OmoideMemorySharing"
)

$ErrorActionPreference = "Stop"

# Check administrator privileges
$currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
$isAdministrator = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdministrator) {
    Write-Error "Administrator privileges required. Please run PowerShell as Administrator."
    exit 1
}

Write-Host "Configuring Windows Firewall rules... [Frontend: $FrontendPort, Backend: $BackendPort]" -ForegroundColor Cyan

$frontendRule = "${RuleNamePrefix}Frontend"
$backendRule = "${RuleNamePrefix}Backend"

# Clean up existing rules
foreach ($rule in @($frontendRule, $backendRule, "OmoideMemorySharingFrontend")) {
    $existingRule = Get-NetFirewallRule -Name $rule -ErrorAction SilentlyContinue
    if ($existingRule) {
        Write-Host "Updating existing rule '$rule'..." -ForegroundColor Yellow
        Remove-NetFirewallRule -Name $rule
    }
}

# Add Inbound Rule (Frontend)
New-NetFirewallRule `
    -Name $frontendRule `
    -DisplayName "$frontendRule (Port $FrontendPort)" `
    -Description "Omoide Memory Sharing Frontend LAN Access Port $FrontendPort" `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $FrontendPort | Out-Null

# Add Inbound Rule (Backend)
New-NetFirewallRule `
    -Name $backendRule `
    -DisplayName "$backendRule (Port $BackendPort)" `
    -Description "Omoide Memory Sharing Backend LAN Access Port $BackendPort" `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $BackendPort | Out-Null

Write-Host "Windows Firewall ports opened: Frontend $FrontendPort, Backend $BackendPort." -ForegroundColor Green
Write-Host "Access URLs from other devices on LAN:" -ForegroundColor Green
Write-Host "  Frontend: http://<This-PC-IP>:$FrontendPort" -ForegroundColor White
Write-Host "  Backend : http://<This-PC-IP>:$BackendPort" -ForegroundColor White
