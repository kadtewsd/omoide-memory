<#
.SYNOPSIS
    Spring Boot (backend) アプリケーションのビルドおよび成果物 (JAR) の移動スクリプト。

.DESCRIPTION
    omoide-memory-sharing の backend ディレクトリで Gradle ビルドを実行し、
    生成された JAR ファイルを指定された配備先フォルダ (DestinationPath) へ移動配置します。

.PARAMETER DestinationPath
    ビルド成果物 (backend.jar) を配置する移動先フォルダの絶対パスまたは相対パス。
    （例: "C:\app\backend" または "D:\deploy\sharing-backend"）

.EXAMPLE
    .\build-backend.ps1 -DestinationPath "C:\app\backend"
    backend をビルドし、成果物 JAR を C:\app\backend\backend.jar に配置します。
#>

param (
    # [必須] 成果物 (backend.jar) の配置先ディレクトリパス
    [Parameter(Mandatory = $true, HelpMessage = "成果物 (backend.jar) の移動先ディレクトリパスを指定してください。")]
    [string]$DestinationPath
)

$ErrorActionPreference = "Stop"

# スクリプトが存在する階層配下の backend ディレクトリを特定
$BackendDir = Join-Path $PSScriptRoot "backend"

Write-Host "Building backend Spring Boot application..." -ForegroundColor Cyan
Set-Location $BackendDir

# OS 環境に応じた gradlew の実行
if (Get-Command "gradlew.bat" -ErrorAction SilentlyContinue) {
    .\gradlew.bat build -x test
} else {
    sh gradlew build -x test
}

# ビルド成果物 (build/libs/*.jar) の探索 (plain JAR を除く)
$BuildJarDir = Join-Path $BackendDir "build\libs"
$JarFile = Get-ChildItem -Path $BuildJarDir -Filter "*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1

if (-not $JarFile) {
    Write-Error "Build artifact JAR file not found in $BuildJarDir"
    exit 1
}

# 移動先ディレクトリが存在しない場合は新規作成
if (-not (Test-Path $DestinationPath)) {
    New-Item -ItemType Directory -Path $DestinationPath -Force | Out-Null
}

# 指定パスへ backend.jar として強制作成・移動
$TargetJarPath = Join-Path $DestinationPath "backend.jar"
Write-Host "Moving artifact $($JarFile.FullName) -> $TargetJarPath" -ForegroundColor Green
Move-Item -Path $JarFile.FullName -Destination $TargetJarPath -Force

Write-Host "Backend build and deployment completed." -ForegroundColor Green
