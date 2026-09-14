<#
.SYNOPSIS
    omoide-memory-sharing の LAN 公開起動専用スクリプト。

.DESCRIPTION
    ビルド済みの成果物（または既存コード）を検出し、
    Backend および Frontend を LAN 内公開モード (0.0.0.0:5173) で即時起動します。

.PARAMETER Mode
    "Production" (ビルド成果物 JAR + preview) または "Dev" (bootRun + vite dev)。デフォルトは "Production"。

.PARAMETER FrontendPort
    フロントエンドの待ち受けポート（デフォルト: 5173）

.EXAMPLE
    .\start-lan.ps1
#>

param (
    [Parameter(Mandatory = $false)]
    [ValidateSet("Production", "Dev")]
    [string]$Mode = "Production",

    [Parameter(Mandatory = $false)]
    [int]$FrontendPort = 5173
)

$ErrorActionPreference = "Stop"
$RepoRoot = $PSScriptRoot
$BackendDir = Join-Path $RepoRoot "backend"
$FrontendDir = Join-Path $RepoRoot "frontend"

$ComputerName = $env:COMPUTERNAME
$LocalIPs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | `
            Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | `
            Select-Object -ExpandProperty IPAddress

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   omoide-memory-sharing LAN サーバー起動              " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "【LAN 内のスマートフォン・他端末からのアクセス先 URL】" -ForegroundColor Yellow
Write-Host "  1. Wi-Fi ルーター名前解決 (推奨・mDNS):" -ForegroundColor Cyan
Write-Host "     http://$($ComputerName.ToLower()).local:$FrontendPort" -ForegroundColor White
Write-Host "  2. IP アドレス直接アクセス:" -ForegroundColor Cyan
foreach ($ip in $LocalIPs) {
    Write-Host "     http://${ip}:$FrontendPort" -ForegroundColor White
}
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

if ($Mode -eq "Production") {
    $BuildJarDir = Join-Path $BackendDir "build\libs"
    $JarFile = Get-ChildItem -Path $BuildJarDir -Filter "*.jar" -ErrorAction SilentlyContinue | `
               Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1

    if (-not $JarFile) {
        Write-Warning "ビルド済み JAR が見つかりません。先に .\deploy-and-run-lan.ps1 を実行してビルドしてください。"
        Write-Host "Dev モードにフォールバックして起動します..." -ForegroundColor Yellow
        $Mode = "Dev"
    } else {
        Write-Host "Production モードで起動します..." -ForegroundColor Green
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Backend (Spring Boot)...' -ForegroundColor Cyan; java -jar `"$($JarFile.FullName)`""
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Frontend Preview...' -ForegroundColor Cyan; Set-Location `"$FrontendDir`"; npm run preview -- --host 0.0.0.0 --port $FrontendPort"
        exit 0
    }
}

if ($Mode -eq "Dev") {
    Write-Host "Dev モードで起動します..." -ForegroundColor Green
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Backend (bootRun)...' -ForegroundColor Cyan; Set-Location `"$BackendDir`"; .\gradlew.bat bootRun"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Frontend Dev...' -ForegroundColor Cyan; Set-Location `"$FrontendDir`"; npm run dev -- --host 0.0.0.0 --port $FrontendPort"
}
