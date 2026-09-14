<#
.SYNOPSIS
    omoide-memory-sharing の LAN 公開用ワンストップ ビルド・デプロイ・起動スクリプト。

.DESCRIPTION
    Windows 上で以下の処理を一括して自動実行します：
    1. Git pull による最新ソースコードの取得（任意）
    2. Windows Defender ファイアウォールでのポート開放（デフォルト: 5173）
    3. Backend (Spring Boot) のビルド
    4. Frontend (React 19 + Vite) のビルド
    5. 配備先へのコピーおよび Git リソース (.git) の削除（任意）
    6. バックエンドおよびフロントエンドの自動起動
    7. Wi-Fi ルーター / mDNS (<PC名>.local) によるアクセス URL の案内

.PARAMETER Mode
    実行モードを指定します。
    - "Production" (デフォルト): JAR ビルドおよび Vite preview / 静的配信で安定起動
    - "Dev": bootRun および Vite 開発サーバーで即時起動

.PARAMETER SkipPull
    Git pull をスキップしてローカルの現状コードでビルドします。

.PARAMETER CleanGit
    指定した配備先 (DeployPath) へ成果物をコピーし、.git などの不要なバージョン管理リソースを削除します。

.PARAMETER DeployPath
    CleanGit 指定時のデプロイ先フォルダ（デフォルト: "." カレントディレクトリ）

.PARAMETER FrontendPort
    フロントエンドの公開ポート番号（デフォルト: 5173）

.PARAMETER SkipFirewall
    ファイアウォールの設定をスキップします。

.PARAMETER NoLaunch
    ビルド・デプロイのみ行い、アプリの自動起動を行いません。

.EXAMPLE
    # 通常のワンストップビルド＆起動（推奨）
    .\deploy-and-run-lan.ps1

.EXAMPLE
    # カレントディレクトリで .git をクリーンアップして起動
    .\deploy-and-run-lan.ps1 -CleanGit

.EXAMPLE
    # 別の配備先フォルダ (例: D:\apps\omoide) へクリーン配置（.git 削除）して起動
    .\deploy-and-run-lan.ps1 -CleanGit -DeployPath "D:\apps\omoide"
#>

param (
    [Parameter(Mandatory = $false)]
    [ValidateSet("Production", "Dev")]
    [string]$Mode = "Production",

    [Parameter(Mandatory = $false)]
    [switch]$SkipPull,

    [Parameter(Mandatory = $false)]
    [switch]$CleanGit,

    [Parameter(Mandatory = $false)]
    [string]$DeployPath = ".",

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
        Write-Host "[1/5] Windows ファイアウォールの受信規則を設定中 (Port: $FrontendPort)..." -ForegroundColor Cyan
        $RuleName = "OmoideMemorySharingFrontend"
        $existingRule = Get-NetFirewallRule -Name $RuleName -ErrorAction SilentlyContinue
        if ($existingRule) {
            Remove-NetFirewallRule -Name $RuleName
        }
        New-NetFirewallRule `
            -Name $RuleName `
            -DisplayName "$RuleName (Port $FrontendPort)" `
            -Description "Omoide Memory Sharing Frontend Port $FrontendPort for LAN" `
            -Direction Inbound `
            -Action Allow `
            -Protocol TCP `
            -LocalPort $FrontendPort | Out-Null
        Write-Host "  -> ポート $FrontendPort の開放が完了しました。" -ForegroundColor Green
    } else {
        Write-Host "[1/5] 注意: 管理者権限ではないためファイアウォール自動開放をスキップしました。" -ForegroundColor Yellow
        Write-Host "  LAN 内の他端末からアクセスできない場合は、管理者権限 PowerShell で以下を実行してください:" -ForegroundColor Gray
        Write-Host "  cd `"$FrontendDir`"; .\allow-frontend-firewall-port.ps1 -Port $FrontendPort" -ForegroundColor Gray
    }
} else {
    Write-Host "[1/5] ファイアウォール設定はスキップされました。" -ForegroundColor Gray
}

# --------------------------------------------------
# 2. Git Pull (最新コードの取得)
# --------------------------------------------------
if (-not $SkipPull) {
    Write-Host "`n[2/5] Git リポジトリから最新コードを取得中 (git pull)..." -ForegroundColor Cyan
    try {
        git -C $RepoRoot pull
        Write-Host "  -> 最新コードを取得しました。" -ForegroundColor Green
    } catch {
        Write-Host "  -> git pull に失敗したか、git 管理外です。ローカルの現状コードで続行します。" -ForegroundColor Yellow
    }
} else {
    Write-Host "`n[2/5] Git pull はスキップされました。" -ForegroundColor Gray
}

# --------------------------------------------------
# 3. Backend のビルド / 準備
# --------------------------------------------------
Write-Host "`n[3/5] Backend (Spring Boot) を準備中..." -ForegroundColor Cyan
Set-Location $BackendDir

$BackendJarPath = ""
if ($Mode -eq "Production") {
    Write-Host "  Gradle による JAR パッケージングを実行中 (build -x test)..." -ForegroundColor Gray
    if (Get-Command "gradlew.bat" -ErrorAction SilentlyContinue) {
        .\gradlew.bat build -x test
    } else {
        gradle build -x test
    }

    $BuildJarDir = Join-Path $BackendDir "build\libs"
    $JarFile = Get-ChildItem -Path $BuildJarDir -Filter "*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1
    if (-not $JarFile) {
        Write-Error "Backend JAR ファイルのビルドに失敗しました ($BuildJarDir に JAR が見つかりません)。"
        exit 1
    }
    $BackendJarPath = $JarFile.FullName
    Write-Host "  -> Backend ビルド完了: $BackendJarPath" -ForegroundColor Green
} else {
    Write-Host "  -> 開発モードのため JAR ビルドをスキップし、bootRun を使用します。" -ForegroundColor Green
}

# --------------------------------------------------
# 4. Frontend のビルド
# --------------------------------------------------
Write-Host "`n[4/5] Frontend (React + Vite) を準備中..." -ForegroundColor Cyan
Set-Location $FrontendDir

Write-Host "  npm install を実行中..." -ForegroundColor Gray
npm install

if ($Mode -eq "Production") {
    Write-Host "  npm run build を実行中..." -ForegroundColor Gray
    npm run build
    $DistDir = Join-Path $FrontendDir "dist"
    if (-not (Test-Path $DistDir)) {
        Write-Error "Frontend のビルド成果物 ($DistDir) が見つかりません。"
        exit 1
    }
    Write-Host "  -> Frontend ビルド完了 (dist 生成済み)。" -ForegroundColor Green
} else {
    Write-Host "  -> 開発モードのため Vite 開発サーバーで即時実行します。" -ForegroundColor Green
}

# --------------------------------------------------
# 5. クリーンデプロイ (.git 削除) 処理（指定時のみ）
# --------------------------------------------------
$TargetBackendDir = $BackendDir
$TargetFrontendDir = $FrontendDir
$TargetJarFile = $BackendJarPath

if ($CleanGit) {
    Write-Host "`n[5/5] デプロイ先へ配置し、Git リソース (.git) をクリーンアップ中..." -ForegroundColor Cyan
    if (-not (Test-Path $DeployPath)) {
        New-Item -ItemType Directory -Path $DeployPath -Force | Out-Null
    }

    $ResolvedDeployPath = (Resolve-Path $DeployPath).Path
    $ResolvedRepoRoot = (Resolve-Path $RepoRoot).Path
    $IsSameDirectory = ($ResolvedDeployPath -eq $ResolvedRepoRoot)

    if (-not $IsSameDirectory) {
        $DeployBackendDir = Join-Path $ResolvedDeployPath "backend"
        $DeployFrontendDir = Join-Path $ResolvedDeployPath "frontend"

        # Backend の配置
        if (-not (Test-Path $DeployBackendDir)) { New-Item -ItemType Directory -Path $DeployBackendDir -Force | Out-Null }
        if ($Mode -eq "Production") {
            $TargetJarFile = Join-Path $DeployBackendDir "backend.jar"
            Copy-Item -Path $BackendJarPath -Destination $TargetJarFile -Force
        } else {
            Copy-Item -Path "$BackendDir\*" -Destination $DeployBackendDir -Recurse -Force
        }

        # Frontend の配置
        if (-not (Test-Path $DeployFrontendDir)) { New-Item -ItemType Directory -Path $DeployFrontendDir -Force | Out-Null }
        Copy-Item -Path "$FrontendDir\*" -Destination $DeployFrontendDir -Recurse -Force

        $TargetBackendDir = $DeployBackendDir
        $TargetFrontendDir = $DeployFrontendDir
    } else {
        Write-Host "  -> カレントディレクトリへの配備のため、ファイルコピーはスキップします。" -ForegroundColor Gray
    }

    # 不要な .git の完全削除（配備先または親リポジトリの .git を検出して削除）
    $PotentialGitDirs = @(
        (Join-Path $ResolvedDeployPath ".git"),
        (Join-Path $ResolvedRepoRoot "..\\.git")
    )
    foreach ($gitDir in $PotentialGitDirs) {
        if (Test-Path $gitDir) {
            Remove-Item -Path $gitDir -Recurse -Force
            Write-Host "  -> 不要な Git リソース ($gitDir) を削除しました。" -ForegroundColor Green
        }
    }

    Write-Host "  -> デプロイ配置完了: $ResolvedDeployPath" -ForegroundColor Green
} else {
    Write-Host "`n[5/5] クリーン配置オプション (-CleanGit) は未指定のため、作業ディレクトリで直接実行します。" -ForegroundColor Gray
}

Set-Location $RepoRoot

# --------------------------------------------------
# 接続 URL の案内生成
# --------------------------------------------------
$ComputerName = $env:COMPUTERNAME
$LocalIPs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | `
            Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | `
            Select-Object -ExpandProperty IPAddress

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "   ビルド＆デプロイ処理が正常に完了しました！             " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "【LAN 内のスマートフォン・他端末からのアクセス先 URL】" -ForegroundColor Yellow
Write-Host "  1. Wi-Fi ルーター名前解決 (推奨・mDNS):" -ForegroundColor Cyan
Write-Host "     http://$($ComputerName.ToLower()).local:$FrontendPort" -ForegroundColor White
Write-Host "  2. IP アドレス直接アクセス:" -ForegroundColor Cyan
foreach ($ip in $LocalIPs) {
    Write-Host "     http://${ip}:$FrontendPort" -ForegroundColor White
}
Write-Host "----------------------------------------------------------" -ForegroundColor Gray
Write-Host "※ 証明書 (HTTPS) は不要です。家庭内 Wi-Fi に接続したブラウザでアクセスしてください。" -ForegroundColor Gray
Write-Host "※ 写真選択・フォトブック機能は上部メニューからご利用いただけます。" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""

if ($NoLaunch) {
    Write-Host "NoLaunch オプションが指定されたため、起動をスキップして終了します。" -ForegroundColor Yellow
    exit 0
}

# --------------------------------------------------
# サービスの起動
# --------------------------------------------------
Write-Host "Backend と Frontend を起動します (別ウィンドウで開きます)..." -ForegroundColor Cyan

if ($Mode -eq "Production") {
    # Backend JAR 起動
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Backend (Spring Boot)...' -ForegroundColor Cyan; java -jar `"$TargetJarFile`""
    
    # Frontend Preview 起動 (host 0.0.0.0, port 5173)
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Frontend Preview...' -ForegroundColor Cyan; Set-Location `"$TargetFrontendDir`"; npm run preview -- --host 0.0.0.0 --port $FrontendPort"
} else {
    # Backend bootRun 起動
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Backend (bootRun)...' -ForegroundColor Cyan; Set-Location `"$TargetBackendDir`"; .\gradlew.bat bootRun"
    
    # Frontend Dev 起動 (host 0.0.0.0, port 5173)
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Frontend Dev...' -ForegroundColor Cyan; Set-Location `"$TargetFrontendDir`"; npm run dev -- --host 0.0.0.0 --port $FrontendPort"
}

Write-Host "起動プロセスを開始しました。ウィンドウを閉じるとアプリが終了します。" -ForegroundColor Green
