# omoide-memory-downloader

おもいでメモリ ダウンローダー - Google Driveから写真・動画を自動ダウンロードして整理するWindowsバッチアプリケーション

---

## 概要

このアプリケーションは、Google Drive のルート直下に保存された写真・動画を自動的にローカル PC にダウンロードし、撮影日時に基づいて整理して保存します。保存完了後、Google Drive 上のファイルは自動削除（ゴミ箱へ移動）されます。

家庭内での写真・動画のバックアップを目的とした、無人実行可能なバッチアプリケーションです。

---

## ⚠️ セキュリティに関する重要な注意事項

本アプリは OAuth リフレッシュトークン方式で Google Drive にアクセスします。

### Google Cloud のアプリ公開について

OAuth 同意画面の「アプリを公開」はリフレッシュトークンの7日制限を解除するための設定であり、アプリ自体やソースコードが一般公開されるわけではありません。**認証情報ファイルさえ適切に管理すれば、公開設定にしても問題ありません。**

### 認証情報ファイルの取り扱い

`credentials.json`（`client_id` / `client_secret` / `refresh_token` を含むファイル）が漏洩した場合、**第三者があなたの Google Drive に対してファイルの閲覧・ダウンロード・削除を含むあらゆる操作を実行できます。**

以下を必ず守ってください。

- **Git にコミットしない**
  `.gitignore` に認証情報ファイルのパスを追加し、絶対にリポジトリに含めないこと。誤ってコミットした場合は、履歴ごと削除したうえで Google Cloud Console からトークンを即座に無効化すること。

- **安全な場所に保管する**
  プロジェクトディレクトリの外（例: `~/dev/secrets/`）に保存し、OS のアクセス権限を適切に設定すること。

- **漏洩した場合は即座に無効化する**
  [Google Cloud Console](https://console.cloud.google.com/) →「API とサービス」→「認証情報」から OAuth クライアントを削除または無効化することで、該当トークンは即時失効します。


## 主な機能

指定したフォルダ配下の全ファイルを自動取得
- Google Drive の **ルート直下** にある全ファイルを自動取得
- 複数アカウントの同時監視に対応
- OAuth リフレッシュトークンによる無人認証

### 2. 自動ファイル整理
撮影日時に基づいて以下の構造で自動配置します。

```
<ダウンロード先>/
  └─ <年>/
      └─ <月（ゼロ埋め2桁）>/
          ├─ photo/  # 画像ファイル
          └─ video/    # 動画ファイル
```

**配置例:**
```
G:\my-memory\
  ├── 2023\
  │   ├── 05\
  │   │   ├── photo\
  │   │   │   ├── PXL_20230521_095441068.jpg
  │   │   │   └── SNOW_20230527_155202_347.jpg
  │   │   └── video\
  │   │       └── VID_20230515_183022.mp4
  │   └── 12\
  │       └── photo\
  │           └── IMG_20231225_120000.jpg
  └── 2024\
      └── 03\
          ├── photo\
          │   └── 20240315_143022.jpg
          └── video\
              └── VID_20240320_090000.mp4
```

### 3. メタデータ抽出と保存
- **画像**: EXIF情報（撮影日時、GPS座標、カメラ設定など）
- **動画**: コーデック、解像度、フレームレート、音声情報など
- **位置情報**: GPS座標から地名を自動取得（Nominatim API使用）
- **動画サムネイル**: 1秒目のフレームをJPEG画像として自動生成

すべてのメタデータはPostgreSQLに保存され、後から検索・管理が可能です。

### 4. 重複防止
- データベースでファイル名を管理し、既に取り込み済みのファイルは再ダウンロードしません

### 5. 自動削除
- ローカル保存とDB登録が完了したファイルは、Google Drive上から自動削除されます

---

## 必要な環境

| 項目                    | バージョン                   |
|-----------------------|-------------------------|
| OS                    | Windows 11              |
| JDK                   | 17                      |
| PostgreSQL            | 16（docker-composeで起動可能） |
| Google Cloud Platform | サービスアカウント               |
| ffmpeg                | 動画メタ情報抽出                |

---

## セットアップ

### 1. PostgreSQLの起動

モノレポのルートディレクトリ（`omoide-memory/`）で以下を実行します。

```bash
docker compose up -d
```

### 2. マイグレーションの実行

```bash
cd omoide-memory-migration
./gradlew bootRun
```

これで必要なテーブル（`synced_omoide_photo`, `synced_omoide_video`など）が作成されます。

# 3. Google Drive 認証設定

本アプリは OAuth リフレッシュトークンを使用して、複数の Google Drive アカウントから同時にファイルをダウンロードします。

## 3-1. 認証情報 JSON ファイルの作成 (USER_CREDENTIALS_PATH)

環境変数 `USER_CREDENTIALS_PATH` に、監視対象となるすべてのアカウントの認証情報を記述した JSON ファイルのパスを指定します。

1. 以下の形式で JSON ファイルを作成します（**Git で管理しない安全な場所に保存してください**）。

```json
[
  {
    "client_id": "YOUR_CLIENT_ID_1",
    "client_secret": "YOUR_CLIENT_SECRET_1",
    "refresh_token": "YOUR_REFRESH_TOKEN_1"
  },
  {
    "client_id": "YOUR_CLIENT_ID_2",
    "client_secret": "YOUR_CLIENT_SECRET_2",
    "refresh_token": "YOUR_REFRESH_TOKEN_2"
  }
]
```

2. ファイルのフルパスを環境変数 `USER_CREDENTIALS_PATH` に設定します。

## 3-2. ファミリー ID の設定 (OMOIDE_FAMILY_ID)

複数のアカウントから取得したコンテンツをデータベース内で一元管理するために「ファミリー ID」を使用します。

1. 任意の識別子（例: `my-home-server`）を環境変数 `OMOIDE_FAMILY_ID` に設定してください。
    - この ID は、データベースの `family_id` カラムに保存され、家族やグループ単位でのデータ管理に使用されます。

---

## 3-3. セキュリティに関する重要な注意事項

`USER_CREDENTIALS_PATH` で指定する JSON ファイルには、各アカウントの Google Drive に対する強力な操作権限（閲覧・ダウンロード・削除）が含まれています。

- **Git に絶対コミットしない**: `.gitignore` に指定するか、プロジェクトディレクトリ外に保存してください。
- **漏洩時の対応**: [Google Cloud Console](https://console.cloud.google.com/) から該当の OAuth クライアント ID を即座に削除または無効化してください。

---

---

### 4. 環境変数の設定

「システムのプロパティ」/「環境変数」「に以下を追加してください。

| 変数名                                 | 環境/システム | 対象コマンド                                 | 説明                              | 例 |
|-------------------------------------|---------|----------------------------------------|---------------------------------|---|
| `spring.profiles.active`            | システム    | 全コマンド                                  | Spring Boot プロファイル。デフォルトは debug | `local` |
| `runnerName`                        | システム    | 全コマンド                                  | 実行するコマンド名                       | `download-from-gdrive` |
| `USER_CREDENTIALS_PATH`             | 環境      | download-from-gdrive                   | 認証情報 JSON ファイルのフルパス                | `C:\secrets\gdrive_creds.json` |
| `OMOIDE_FAMILY_ID`                  | 環境      | download-from-gdrive                   | ファミリー識別子 (DBのグループ化に使用)     | `my-home-01` |
| `OMOIDE_BACKUP_DIRECTORY`           | 環境      | download-from-gdrive / ImportFromLocal | バックアップ先ディレクトリ                   | `G:\my-memory` |
| `OMOIDE_COMMENT_FILE_PATH`          | 環境      | comment-import                         | インポートするコメントファイルのパス              | `C:\Users\user\comments.csv` |
| `EXTERNAL_STORAGE_BACKUP_DIRECTORY` | 環境      | backup-to-local                       | バックアップ先ディレクトリ                   | `G:\my-memory` |


### 5. FFmpeg のインストール

本アプリケーションは動画処理に `ffmpeg` / `ffprobe` を使用します。

**用途:**
- `ffprobe`: 動画のメタデータ取得（解像度・フレームレート・コーデック・再生時間など）
- `ffmpeg`: サムネイル画像の生成

**インストール（Windows）:**

1. [https://ffmpeg.org/download.html](https://ffmpeg.org/download.html) から Windows ビルドをダウンロード
2. zip を展開（例: `C:\ffmpeg\bin`）
3. `bin` ディレクトリを環境変数 `PATH` に追加
4. PowerShell で確認

```powershell
ffmpeg -version
ffprobe -version
```

カスタムパスを使用する場合は以下の環境変数で指定できます。

```powershell
setx FFMPEG_PATH "C:\ffmpeg\bin\ffmpeg.exe"
setx FFPROBE_PATH "C:\ffmpeg\bin\ffprobe.exe"
```

---

## 5. 実行方法

実行するコマンドは `-DrunnerName` で指定します。環境変数は事前に設定済みであることを前提とします。

### 開発時の実行

コマンドごとに PowerShell スクリプト（`.ps1`）を作成し、環境変数と実行コマンドをまとめて記述します。
`.bat` は Shift\_JIS と UTF-8 の文字コード問題が発生しやすいため、`.ps1` を推奨します。

### download-from-gdrive

```powershell

$logFile = "$PSScriptRoot\log\download-from-gdrive.log"
"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行開始" | Out-File -FilePath $logFile -Encoding utf8

java -Dspring.profiles.active=local -DrunnerName=download-from-gdrive -jar C:\path\to\omoide-memory-downloader.jar 2>&1 |
    Out-File -FilePath $logFile -Encoding utf8 -Append

"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行終了" | Out-File -FilePath $logFile -Encoding utf8 -Append

$env:USER_CREDENTIALS_PATH = "C:\secrets\gdrive_creds.json"
$env:OMOIDE_FAMILY_ID       = "my-home-01"
$env:OMOIDE_BACKUP_DIRECTORY = "H:\YOUR_DIRECTORY"

java -Dspring.profiles.active=local -DrunnerName=download-from-gdrive -jar C:\path\to\omoide-memory-downloader.jar
```

### import-from-local

```powershell
$env:OMOIDE_BACKUP_DIRECTORY = "H:\YOUR_DIRECTORY"
java -Dspring.profiles.active=local -DrunnerName=importFromLocal -jar C:\path\to\omoide-memory-downloader.jar
```

### import-comments

```powershell
$env:OMOIDE_COMMENT_FILE_PATH = "C:\path\to\comments.csv"
java -Dspring.profiles.active=local -DrunnerName=commentImport -jar C:\path\to\omoide-memory-downloader.jar
```

### backup-to-local

```powershell
$logFile = "$PSScriptRoot\log\backup-to-local.log"
"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行開始" | Out-File -FilePath $logFile -Encoding utf8

"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行終了" | Out-File -FilePath $logFile -Encoding utf8 -Append

$env:OMOIDE_BACKUP_DIRECTORY          = "H:\YOUR_DIRECTORY"
$env:EXTERNAL_STORAGE_BACKUP_DIRECTORY = "G:\YOUR_DIRECTORY"
java -Dspring.profiles.active=local -DrunnerName=backup-to-local -jar C:\path\to\omoide-memory-downloader.jar 2>&1 |
    Out-File -FilePath $logFile -Encoding utf8 -Append
```

---

## Windows タスクスケジューラでの定期実行

### 1. PowerShell スクリプトの作成

`run-omoide-downloader.ps1` を作成します。
文字コードのトラブルを抑制するために、冒頭で UTF-8 出力を明示的に設定します。

```powershell
# UTF-8 出力設定（ログ文字化け防止）
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding             = [System.Text.Encoding]::UTF8

$env:USER_CREDENTIALS_PATH    = "C:\secrets\gdrive_creds.json"
$env:OMOIDE_FAMILY_ID         = "my-home-01"
$env:OMOIDE_BACKUP_DIRECTORY  = "H:\YOUR_DIRECTORY"

$logFile = "C:\logs\omoide-downloader.log"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

"[$timestamp] 実行開始" | Out-File -FilePath $logFile -Encoding utf8 -Append

java -Dspring.profiles.active=local -DrunnerName=download-from-gdrive -jar C:\path\to\omoide-memory-downloader.jar 2>&1 |
    Out-File -FilePath $logFile -Encoding utf8 -Append

"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行終了" | Out-File -FilePath $logFile -Encoding utf8 -Append
```

> **ログファイルの文字コードについて**
> `Out-File -Encoding utf8` は Windows PowerShell 5.1 では BOM 付き UTF-8 で出力されます。
> BOM なしが必要な場合は `-Encoding utf8NoBOM`（PowerShell 7 以降）または `[System.IO.File]::AppendAllText()` を使用してください。

### 2. ExecutionPolicy の設定（初回のみ）

`.ps1` はデフォルトで実行が制限されています。初回に一度だけ、管理者権限の PowerShell で以下を実行してください。

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope LocalMachine
```

| ポリシー | 意味 |
|---|---|
| `RemoteSigned` | ローカルの `.ps1` はそのまま実行可。インターネットからダウンロードしたものは署名が必要。 |
| `Unrestricted` | すべて実行可（非推奨）。 |

### 3. タスクスケジューラの設定

1. タスクスケジューラを開く
2. 「タスクの作成」をクリック
3. **全般タブ**
    - 名前: `おもいでメモリダウンローダー`
    - 「ユーザーがログオンしているかどうかにかかわらず実行する」を選択（バックグラウンド実行したい場合）
4. **トリガータブ**
    - 「新規」→ 毎日深夜 2 時などに設定
5. **操作タブ**
    - 「新規」→「プログラムの開始」
    - プログラム: `powershell.exe`
    - 引数: `-ExecutionPolicy Bypass -NonInteractive -File "C:\path\to\run-omoide-downloader.ps1"`
6. 「OK」で保存

> **引数の `-ExecutionPolicy Bypass` について**
> タスクスケジューラ経由では ExecutionPolicy が別途効いてくる場合があるため、スクリプトごとに `-ExecutionPolicy Bypass` を渡しておくのが確実です。`Set-ExecutionPolicy` でシステム全体に設定済みの場合でも、引数で明示しておくと環境差異による実行失敗を防げます。

---

主な変更点をまとめると、`.bat` をすべて `.ps1` に置き換えてスクリプト冒頭に `[Console]::OutputEncoding` と `$OutputEncoding` の2行を追加しています。タスクスケジューラの操作タブでは `powershell.exe` を直接指定し、引数に `-ExecutionPolicy Bypass -NonInteractive -File` を渡す形にしました。これで Shift\_JIS 問題と「スクリプトが実行できない」問題の両方を回避できます。

# インストールする必要があるもの

## podman
Podman を利用する利用

理由：

- Docker Desktop が不要（ライセンス問題なし）
- 軽量
- rootless 実行可能
- Docker Compose 互換（`podman compose`）

個人開発用途では Podman で十分です。

---

### Mac 環境
```
brew install podman
podman machine init
podman machine start
```

### Wiindows 環境
Windows 環境では **WSL2（Windows Subsystem for Linux）上でコンテナを動かす構成** します。
さらに、**WSL の保存先を C ドライブ以外（例：G ドライブ）へ移動することを強く推奨**します。

---

### なぜ C ドライブ以外に WSL を配置するのか？

WSL2 は Linux ファイルシステムを 1 つの仮想ディスク（`ext4.vhdx`）として保存します。
デフォルトでは以下に保存されます：
C:\Users<ユーザー名>\AppData\Local\Packages...

そのため：

- コンテナの DB データ
- ボリューム
- イメージ
- Linux ホームディレクトリ

すべてが **C ドライブ（SSD）を消費します**。

本アプリは個人用途を想定しており、

- DB は大きくなる可能性がある
- パフォーマンスよりも容量確保を優先したい

という前提から、

> ✅ WSL 自体を G ドライブなど容量の大きいディスクに配置することを推奨します。

---

## セットアップ手順

### ① WSL をインストール

PowerShell（管理者）で：

```powershell
wsl --install
````

再起動後、Ubuntu などを初期セットアップします。

---

### ② 現在の WSL 名を確認

```powershell
wsl -l -v
```

例：

```
NAME      STATE    VERSION
Ubuntu    Running  2
```

---

### ③ WSL を G ドライブへ移動する

#### 1. エクスポート

```powershell
wsl --export Ubuntu G:\wsl-backup\ubuntu.tar
```

#### 2. 保存先ディレクトリ作成

```powershell
mkdir G:\wsl\ubuntu
```

#### 3. G ドライブへインポート

```powershell
wsl --import Ubuntu-G G:\wsl\ubuntu G:\wsl-backup\ubuntu.tar --version 2
```

#### 4. デフォルトに設定

```powershell
wsl --set-default Ubuntu-G
```

#### 5. 動作確認

```powershell
wsl
```

正常に起動すれば成功です。

#### 6. 古い WSL を削除
確認後古い DSL 削除を実施

```powershell
wsl --unregister Ubuntu
```

これで C ドライブの仮想ディスクが削除されます。

---

# ④ Podman を winget でインストール

PowerShell（通常権限でOK）：

```powershell
winget search podman
```

通常はこれ：

```
RedHat.Podman
```

インストール：

```powershell
winget install RedHat.Podman
```

完了後確認：

```powershell
podman --version
```

---

# 🟢 STEP 1: podman machine 作成（いったん C に作る）

```powershell
podman machine init
podman machine start
```

確認：

```powershell
podman machine ls
wsl -l -v
```

ここで：

```
podman-machine-default
```

という WSL ディストリができます。

# 🛑 STEP 3: 完全停止

超重要：

```powershell
podman machine stop
wsl --shutdown
```

---

# 💾 STEP 4: H ドライブへ移動（正攻法）

## ① エクスポート

```powershell
wsl --export podman-machine-default H:\wsl-backup\podman-machine-default.tar
```

---

## ② 登録解除

```powershell
wsl --unregister podman-machine-default
```

---

## ③ H に再インポート

```powershell
wsl --import podman-machine-default H:\wsl\podman H:\wsl-backup\podman-machine-default.tar --version 2
```

ここで：

```
H:\wsl\podman\ext4.vhdx
```

が作られます。

---

# 🟢 STEP 5: 再起動

```powershell
podman machine start
```

確認：

```powershell
podman info
```

テスト：

```powershell
podman run --rm hello-world
```

---

# 🎯 完成後の構造

```
Windows:
  podman.exe (Cにインストール)

H:
  H:\wsl\podman\ext4.vhdx ← コンテナ実データ
```

C には巨大 VHDX は残りません。

### ⑤ コンテナ DB を起動

プロジェクトルートで：

```bash
podman compose up -d
```

これにより：

* PostgreSQL コンテナ
* DB データ
* コンテナイメージ

すべてが **G ドライブ上の WSL 領域に保存されます**。

---

### この構成のメリット

| 項目               | 効果               |
| ---------------- | ---------------- |
| C ドライブ容量         | 消費しない            |
| Linux ネイティブ ext4 | 維持               |
| DB パフォーマンス       | Windows マウントより高速 |
| 運用の安定性           | 高い               |

---

### 注意事項

* G ドライブが外付けの場合、取り外すと WSL が起動できなくなります。
* インポート後は必ず動作確認を行ってから旧ディストリを削除してください。

---


---

# 🧠 重要な注意点

Podman は：

* WSL ディストリ名で紐づく
* パスではなく「名前」で追跡する

なので import するときに

```
podman-machine-default
```

という **同じ名前** にするのが超重要です。
