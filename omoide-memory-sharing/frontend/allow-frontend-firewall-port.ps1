<#
.SYNOPSIS
    LAN 公開用に Windows ファイアウォールでフロントエンドのポートを開放するスクリプト。

.DESCRIPTION
    フロントエンドアプリ (Vite 開発サーバー / preview / serve 等) を LAN 内の他デバイスからアクセス可能にするため、
    Windows ファイアウォールの受信規則 (Inbound Rule) を追加・設定します。
    ※本スクリプトの実行には「管理者権限」が必要です。

.PARAMETER Port
    開放する TCP ポート番号。デフォルトは Vite / Vite Preview / serve の標準ポートである 5173 です。

.PARAMETER RuleName
    ファイアウォール規則の名前。デフォルトは "OmoideMemorySharingFrontend" です。

.EXAMPLE
    # デフォルトポート (5173) を開放する（管理者権限 PowerShell で実行）
    .\allow-frontend-firewall-port.ps1

.EXAMPLE
    # ポート 3000 を開放する
    .\allow-frontend-firewall-port.ps1 -Port 3000
#>

param (
    # [任意] 開放する TCP ポート番号（デフォルト: 5173）
    [Parameter(Mandatory = $false, HelpMessage = "開放する TCP ポート番号を指定します (例: 5173, 3000)")]
    [int]$Port = 5173,

    # [任意] ファイアウォール規則の名前（デフォルト: OmoideMemorySharingFrontend）
    [Parameter(Mandatory = $false, HelpMessage = "ファイアウォール規則の識別名を指定します")]
    [string]$RuleName = "OmoideMemorySharingFrontend"
)

$ErrorActionPreference = "Stop"

# 管理者権限チェック
$currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
$isAdministrator = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdministrator) {
    Write-Error "管理者権限が必要です。PowerShell を「管理者として実行」して再度スクリプトを実行してください。"
    exit 1
}

Write-Host "Windows Firewall 規則を設定しています... [RuleName: $RuleName, Port: $Port]" -ForegroundColor Cyan

# 既存の同名ルールが存在するか確認
$existingRule = Get-NetFirewallRule -Name $RuleName -ErrorAction SilentlyContinue

if ($existingRule) {
    Write-Host "既存の規則 '$RuleName' が見つかりました。ポート $Port 用に再設定します..." -ForegroundColor Yellow
    Remove-NetFirewallRule -Name $RuleName
}

# 新規受信規則 (Inbound Rule) の追加
New-NetFirewallRule `
    -Name $RuleName `
    -DisplayName "$RuleName (Port $Port)" `
    -Description "Omoide Memory Sharing Frontend LAN Access Port $Port" `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $Port | Out-Null

Write-Host "Windows ファイアウォールで TCP ポート $Port の開放が完了しました。" -ForegroundColor Green
Write-Host "LAN 内の他端末から http://<このPCのIPアドレス>:$Port でアクセスできます。" -ForegroundColor Green
