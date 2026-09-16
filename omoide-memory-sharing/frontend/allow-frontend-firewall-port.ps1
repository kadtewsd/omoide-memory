<#
.SYNOPSIS
    LAN 公開用に Windows ファイアウォールでフロントエンドおよびバックエンドのポートを開放するスクリプト。

.DESCRIPTION
    フロントエンドアプリ (Vite: 5173) およびバックエンド API (Spring Boot: 8080) を
    LAN 内の他デバイスからアクセス可能にするため、Windows ファイアウォールの受信規則 (Inbound Rule) を追加・設定します。
    ※本スクリプトの実行には「管理者権限」が必要です。

.PARAMETER FrontendPort
    開放するフロントエンドの TCP ポート番号。デフォルトは 5173 です。

.PARAMETER BackendPort
    開放するバックエンドの TCP ポート番号。デフォルトは 8080 です。

.PARAMETER RuleNamePrefix
    ファイアウォール規則の名前のプレフィックス。デフォルトは "OmoideMemorySharing" です。

.EXAMPLE
    # デフォルトポート (5173, 8080) を開放する（管理者権限 PowerShell で実行）
    .\allow-frontend-firewall-port.ps1

.EXAMPLE
    # ポートを指定して開放する
    .\allow-frontend-firewall-port.ps1 -FrontendPort 3000 -BackendPort 8080
#>

param (
    # [任意] 開放するフロントエンド TCP ポート番号（デフォルト: 5173）
    [Parameter(Mandatory = $false, HelpMessage = "開放するフロントエンド TCP ポート番号を指定します (例: 5173, 3000)")]
    [Alias("Port")]
    [int]$FrontendPort = 5173,

    # [任意] 開放するバックエンド TCP ポート番号（デフォルト: 8080）
    [Parameter(Mandatory = $false, HelpMessage = "開放するバックエンド TCP ポート番号を指定します (例: 8080)")]
    [int]$BackendPort = 8080,

    # [任意] ファイアウォール規則のプレフィックス（デフォルト: OmoideMemorySharing）
    [Parameter(Mandatory = $false, HelpMessage = "ファイアウォール規則のプレフィックスを指定します")]
    [Alias("RuleName")]
    [string]$RuleNamePrefix = "OmoideMemorySharing"
)

$ErrorActionPreference = "Stop"

# 管理者権限チェック
$currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
$isAdministrator = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdministrator) {
    Write-Error "管理者権限が必要です。PowerShell を「管理者として実行」して再度スクリプトを実行してください。"
    exit 1
}

Write-Host "Windows Firewall 規則を設定しています... [Frontend: $FrontendPort, Backend: $BackendPort]" -ForegroundColor Cyan

$frontendRule = "${RuleNamePrefix}Frontend"
$backendRule = "${RuleNamePrefix}Backend"

# 既存ルールのクリーンアップ
foreach ($rule in @($frontendRule, $backendRule, "OmoideMemorySharingFrontend")) {
    $existingRule = Get-NetFirewallRule -Name $rule -ErrorAction SilentlyContinue
    if ($existingRule) {
        Write-Host "既存の規則 '$rule' を更新します..." -ForegroundColor Yellow
        Remove-NetFirewallRule -Name $rule
    }
}

# 新規受信規則 (Frontend) の追加
New-NetFirewallRule `
    -Name $frontendRule `
    -DisplayName "$frontendRule (Port $FrontendPort)" `
    -Description "Omoide Memory Sharing Frontend LAN Access Port $FrontendPort" `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $FrontendPort | Out-Null

# 新規受信規則 (Backend) の追加
New-NetFirewallRule `
    -Name $backendRule `
    -DisplayName "$backendRule (Port $BackendPort)" `
    -Description "Omoide Memory Sharing Backend LAN Access Port $BackendPort" `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $BackendPort | Out-Null

Write-Host "Windows ファイアウォールでポート $FrontendPort (Frontend) および $BackendPort (Backend) の開放が完了しました。" -ForegroundColor Green
Write-Host "LAN 内の他端末からアクセスできます:" -ForegroundColor Green
Write-Host "  Frontend: http://<このPCのIPアドレス>:$FrontendPort" -ForegroundColor White
Write-Host "  Backend : http://<このPCのIPアドレス>:$BackendPort" -ForegroundColor White

