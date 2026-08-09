<#
.SYNOPSIS
    React / Vite (frontend) アプリケーションのビルドおよび成果物の移動スクリプト。

.DESCRIPTION
    omoide-memory-sharing の frontend ディレクトリで npm run build を実行し、
    生成された静的ファイル成果物 (dist/*) を指定された配備先フォルダ (DestinationPath) へ移動配置します。

.PARAMETER DestinationPath
    ビルド成果物 (dist 配下の HTML / JS / CSS 等) を配置する移動先フォルダの絶対パスまたは相対パス。
    （例: "C:\app\frontend" または "D:\deploy\sharing-frontend"）

.EXAMPLE
    .\build-frontend.ps1 -DestinationPath "C:\app\frontend"
    frontend をビルドし、dist 配下の全成果物を C:\app\frontend に配置します。
#>

param (
    # [必須] フロントエンド成果物 (dist/*) の配置先ディレクトリパス
    [Parameter(Mandatory = $true, HelpMessage = "フロントエンド成果物の移動先ディレクトリパスを指定してください。")]
    [string]$DestinationPath
)

$ErrorActionPreference = "Stop"

# スクリプトが存在する階層配下の frontend ディレクトリを特定
$FrontendDir = Join-Path $PSScriptRoot "frontend"

Write-Host "Building frontend React application..." -ForegroundColor Cyan
Set-Location $FrontendDir

# npm run build による React 成果物 (dist) の生成
npm run build

$DistDir = Join-Path $FrontendDir "dist"

if (-not (Test-Path $DistDir)) {
    Write-Error "Frontend dist directory not found in $DistDir"
    exit 1
}

# 移動先ディレクトリが存在しない場合は新規作成
if (-not (Test-Path $DestinationPath)) {
    New-Item -ItemType Directory -Path $DestinationPath -Force | Out-Null
}

# 指定パスへ dist 内のファイルを強制作成・移動
Write-Host "Moving frontend dist contents -> $DestinationPath" -ForegroundColor Green
Move-Item -Path "$DistDir\*" -Destination $DestinationPath -Force

Write-Host "Frontend build and deployment completed." -ForegroundColor Green
