<#
.SYNOPSIS
    omoide-memory-sharing の LAN 公開用ワンストップ ビルド・起動スクリプト。

.DESCRIPTION
    omoide-memory-sharing ディレクトリをカレントディレクトリとして実行します。
    以下の処理を一括して自動実行します：
    1. Windows Defender ファイアウォールでのポート開放（デフォルト: 5173）
    2. Backend (Spring Boot) のビルド
    3. Frontend (React 19 + Vite) のビルド
    4. バックエンドおよびフロントエンドの自動起動

.PARAMETER Mode
    実行モードを指定します。
    - "Production" (デフォルト): JAR ビルドおよび Vite preview / 静的配信で安定起動
    - "Dev": bootRun および Vite 開発サーバーで即時起動

.PARAMETER FrontendPort
    フロントエンドの公開ポート番号（デフォルト: 5173）

.PARAMETER SkipFirewall
    ファイアウォールの設定をスキップします。

.PARAMETER NoLaunch
    ビルドのみ行い、アプリの自動起動を行いません。

.EXAMPLE
    # 通常のワンストップビルド＆起動（推奨）
    .\deploy-and-run-lan.ps1

.EXAMPLE
    # 開発モード（ホットリロード有効）
    .\deploy-and-run-lan.ps1 -Mode Dev
#>

param (
    [Parameter(Mandatory = $false)]
    [ValidateSet("Production", "Dev")]
    [string]$Mode = "Production",

    [Parameter(Mandatory = $false)]
    [int]$FrontendPort = 5173,

    [Parameter(Mandatory = $false)]
    [switch]$SkipFirewall,

    [Parameter(Mandatory = $false)]
    [switch]$NoLaunch
)

$ErrorActionPreference = "Stop"
$RepoRoot = $PSScriptRoot
$BackendDir = Join-Path $RepoRoot "backend"
$FrontendDir = Join-Path $RepoRoot "frontend"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   omoide-memory-sharing LAN 公開デプロイメントツール   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "実行モード : $Mode" -ForegroundColor Yellow
Write-Host "作業階層   : $RepoRoot" -ForegroundColor Yellow
Write-Host ""

# --------------------------------------------------
# 1. 管理者権限チェック & ファイアウォール設定
# --------------------------------------------------
if (-not $SkipFirewall) {
    $currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    $isAdmin = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

    if ($isAdmin) {
        Write-Host "[1/3] Windows ファイアウォールの受信規則を設定中 (Port: $FrontendPort)..." -ForegroundColor Cyan
        $ruleName = "OmoideMemorySharingFrontend"
        $existingRule = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
        if ($existingRule) {
            Remove-NetFirewallRule -Name $ruleName
        }
        New-NetFirewallRule `
            -Name $ruleName `
            -DisplayName "$ruleName (Port $FrontendPort)" `
            -Description "Omoide Memory Sharing Frontend Port $FrontendPort for LAN" `
            -Direction Inbound `
            -Action Allow `
            -Protocol TCP `
            -LocalPort $FrontendPort | Out-Null
        Write-Host "  -> ポート $FrontendPort の開放が完了しました。" -ForegroundColor Green
    } else {
        Write-Host "[1/3] 注意: 管理者権限ではないためファイアウォール自動開放をスキップしました。" -ForegroundColor Yellow
        Write-Host "  LAN 内の他端末からアクセスできない場合は、管理者権限 PowerShell で以下を実行してください:" -ForegroundColor Gray
        Write-Host "  cd `"$FrontendDir`"; .\allow-frontend-firewall-port.ps1 -Port $FrontendPort" -ForegroundColor Gray
    }
} else {
    Write-Host "[1/3] ファイアウォール設定はスキップされました。" -ForegroundColor Gray
}

# --------------------------------------------------
# 2. Backend のビルド / 準備
# --------------------------------------------------
Write-Host "`n[2/3] Backend (Spring Boot) を準備中..." -ForegroundColor Cyan
Set-Location $BackendDir

$backendJarPath = ""
if ($Mode -eq "Production") {
    Write-Host "  Gradle による JAR パッケージングを実行中 (build -x test)..." -ForegroundColor Gray
    .\gradlew.bat build -x test

    $buildJarDir = Join-Path $BackendDir "build\libs"
    $jarFile = Get-ChildItem -Path $buildJarDir -Filter "*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1
    if (-not $jarFile) {
        Write-Error "Backend JAR ファイルのビルドに失敗しました ($buildJarDir に JAR が見つかりません)。"
        exit 1
    }
    $backendJarPath = $jarFile.FullName
    Write-Host "  -> Backend ビルド完了: $backendJarPath" -ForegroundColor Green
} else {
    Write-Host "  -> 開発モードのため JAR ビルドをスキップし、bootRun を使用します。" -ForegroundColor Green
}

# --------------------------------------------------
# 3. Frontend のビルド
# --------------------------------------------------
Write-Host "`n[3/3] Frontend (React + Vite) を準備中..." -ForegroundColor Cyan
Set-Location $FrontendDir

Write-Host "  npm install を実行中..." -ForegroundColor Gray
npm install

if ($Mode -eq "Production") {
    Write-Host "  npm run build を実行中..." -ForegroundColor Gray
    npm run build
    $distDir = Join-Path $FrontendDir "dist"
    if (-not (Test-Path $distDir)) {
        Write-Error "Frontend のビルド成果物 ($distDir) が見つかりません。"
        exit 1
    }
    Write-Host "  -> Frontend ビルド完了 (dist 生成済み)。" -ForegroundColor Green
} else {
    Write-Host "  -> 開発モードのため Vite 開発サーバーで即時実行します。" -ForegroundColor Green
}

Set-Location $RepoRoot

# --------------------------------------------------
# 接続 URL の案内生成
# --------------------------------------------------
$computerName = $env:COMPUTERNAME
$localIPs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | `
            Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | `
            Select-Object -ExpandProperty IPAddress

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "   ビルド処理が正常に完了しました！                       " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "【LAN 内のスマートフォン・他端末からのアクセス先 URL】" -ForegroundColor Yellow
Write-Host "  1. Wi-Fi ルーター名前解決 (推奨・mDNS):" -ForegroundColor Cyan
Write-Host "     http://$($computerName.ToLower()).local:$FrontendPort" -ForegroundColor White
Write-Host "  2. IP アドレス直接アクセス:" -ForegroundColor Cyan
foreach ($ip in $localIPs) {
    Write-Host "     http://${ip}:$FrontendPort" -ForegroundColor White
}
Write-Host "----------------------------------------------------------" -ForegroundColor Gray
Write-Host "※ 証明書 (HTTPS) は不要です。家庭内 Wi-Fi に接続したブラウザでアクセスしてください。" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""

if ($NoLaunch) {
    Write-Host "NoLaunch オプションが指定されたため、起動をスキップして終了します。" -ForegroundColor Yellow
    exit 0
}

# --------------------------------------------------
# サービスの起動（別 Job で並列実行し、このウィンドウで両方のログを表示）
# --------------------------------------------------
Write-Host "Backend と Frontend を起動します..." -ForegroundColor Cyan
Write-Host "（このウィンドウを閉じると両方のサービスが停止します）" -ForegroundColor Yellow
Write-Host ""

if ($Mode -eq "Production") {
    $backendJob = Start-Job -ScriptBlock {
        param($jarPath)
        java -jar $jarPath
    } -ArgumentList $backendJarPath

    $frontendJob = Start-Job -ScriptBlock {
        param($frontendDir, $port)
        Set-Location $frontendDir
        npm run preview -- --host 0.0.0.0 --port $port
    } -ArgumentList $FrontendDir, $FrontendPort
} else {
    $backendJob = Start-Job -ScriptBlock {
        param($backendDir)
        Set-Location $backendDir
        .\gradlew.bat bootRun
    } -ArgumentList $BackendDir

    $frontendJob = Start-Job -ScriptBlock {
        param($frontendDir, $port)
        Set-Location $frontendDir
        npm run dev -- --host 0.0.0.0 --port $port
    } -ArgumentList $FrontendDir, $FrontendPort
}

Write-Host "[Backend ] Job ID: $($backendJob.Id)" -ForegroundColor Gray
Write-Host "[Frontend] Job ID: $($frontendJob.Id)" -ForegroundColor Gray
Write-Host ""
Write-Host "ログをストリーミング中（Ctrl+C で停止）..." -ForegroundColor Cyan
Write-Host "----------------------------------------------------------" -ForegroundColor Gray

try {
    while ($true) {
        $backendOutput  = Receive-Job -Job $backendJob  -ErrorAction SilentlyContinue
        $frontendOutput = Receive-Job -Job $frontendJob -ErrorAction SilentlyContinue

        foreach ($line in $backendOutput) {
            Write-Host "[Backend ] $line" -ForegroundColor DarkGreen
        }
        foreach ($line in $frontendOutput) {
            Write-Host "[Frontend] $line" -ForegroundColor DarkCyan
        }

        if ($backendJob.State -eq "Failed") {
            Write-Host "[Backend ] ジョブが異常終了しました。" -ForegroundColor Red
            Receive-Job -Job $backendJob -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "[Backend ] $_" -ForegroundColor Red }
            break
        }
        if ($frontendJob.State -eq "Failed") {
            Write-Host "[Frontend] ジョブが異常終了しました。" -ForegroundColor Red
            Receive-Job -Job $frontendJob -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "[Frontend] $_" -ForegroundColor Red }
            break
        }

        Start-Sleep -Milliseconds 500
    }
} finally {
    Write-Host ""
    Write-Host "サービスを停止しています..." -ForegroundColor Yellow
    Stop-Job  -Job $backendJob  -ErrorAction SilentlyContinue
    Stop-Job  -Job $frontendJob -ErrorAction SilentlyContinue
    Remove-Job -Job $backendJob  -Force -ErrorAction SilentlyContinue
    Remove-Job -Job $frontendJob -Force -ErrorAction SilentlyContinue
    Write-Host "停止完了。" -ForegroundColor Green
}
