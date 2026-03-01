# omoide-memory-downloader

おもいでメモリ ダウンローダー - Google Driveから写真・動画を自動ダウンロードして整理するWindowsバッチアプリケーション

---

## 概要

このアプリケーションは、Google Drive のルート直下に保存された写真・動画を自動的にローカル PC にダウンロードし、撮影日時に基づいて整理して保存します。保存完了後、Google Drive 上のファイルは自動削除されます。

家庭内での写真・動画のバックアップを目的とした、無人実行可能なバッチアプリケーションです。

---

## ⚠️ セキュリティに関する重要な注意事項

本アプリは OAuth リフレッシュトークン方式で Google Drive にアクセスします。

### 認証情報の取り扱い

`client_id`・`client_secret`・`refresh_token` が漏洩した場合、**第三者があなたの Google Drive に対してファイルの閲覧・ダウンロード・削除を含むあらゆる操作を実行できます。**

以下を必ず守ってください。

- **Git にコミットしない**
  `.gitignore` に環境変数ファイルや bat / ps1 ファイルのパスを追加し、絶対にリポジトリに含めないこと。誤ってコミットした場合は、履歴ごと削除したうえで Google Cloud Console からトークンを即座に無効化すること。

- **安全な場所に保管する**
  プロジェクトディレクトリの外（例: `~/dev/secrets/`）に保存し、OS のアクセス権限を適切に設定すること。

- **漏洩した場合は即座に無効化する**
  [Google Cloud Console](https://console.cloud.google.com/) →「API とサービス」→「認証情報」から OAuth クライアントを削除または無効化することで、該当トークンは即時失効します。

---

## 主な機能

### 1. 自動ファイル取得

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
          └─ video/  # 動画ファイル
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

データベースでファイル名を管理し、既に取り込み済みのファイルは再ダウンロードしません。

### 5. 自動削除

ローカル保存とDB登録が完了したファイルは、Google Drive上から自動削除されます。

---

## 必要な環境

| 項目                    | バージョン                        |
|-----------------------|------------------------------|
| OS                    | Windows 11                   |
| JDK                   | 17                           |
| PostgreSQL            | 16（docker-composeで起動可能）      |
| Google Cloud Platform | OAuth クライアント（デスクトップアプリ）      |
| ffmpeg                | 動画メタ情報抽出                     |

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

---

# 3. Google Drive 認証設定（OAuth リフレッシュトークン方式）

## 3-1. リフレッシュトークンを使用する理由

本アプリは OAuth リフレッシュトークンを使用して、複数の Google Drive アカウントから同時にファイルをダウンロードします。

当初、無人実行に適した**サービスアカウント**を使用していました。しかし、2025年4月15日以降に作成されたサービスアカウントは「マイドライブ」へのアクセスが制限されており、**ファイルの削除操作ができない**ことが判明しました。

そこで代替として、対象アカウントの**OAuth リフレッシュトークン**を使用する方式に切り替えました。リフレッシュトークンは revoke しない限り無期限で有効であり、対象アカウントのすべての権限でドライブを操作できるため、ファイルの削除を含むバックアップ処理を無人で実行できます。

---

## 3-2. Google Cloud アプリの公開設定について（重要）

### なぜテストモードのままにするのか

本アプリは **Google Cloud の OAuth 同意画面を「テスト」モードのまま運用します。**

理由は以下の通りです。

- 本アプリは **家庭内の特定アカウントのみ**を対象としており、不特定多数への公開を想定していない
- `drive` スコープは技術的には審査なしで公開できる場合もありますが、**公開すると任意の Google アカウントがこのアプリを使って自分のドライブへアクセスできる状態**になる
- クライアントID・シークレットが漏洩した際のリスクが、テストモードよりも格段に大きくなる
- 家族・身内など限られたユーザー（テストユーザーとして登録）のみが使用するため、テストモードで十分に運用できる

### テストモードの制約

| 項目 | 内容 |
|------|------|
| 登録できるテストユーザー数 | 最大100アカウント |
| リフレッシュトークンの有効期限 | **7日で失効** |
| 無人運用 | **そのままでは不可**（後述の対応が必要） |

### ⚠️ リフレッシュトークン7日失効への対応

テストモードでは、リフレッシュトークンが7日ごとに失効します。失効すると次回実行時にエラーになるため、**定期的に手動で再取得が必要**です。

再取得は後述の手順（3-2 ⑤）を再実行するだけです。運用の手間はありますが、セキュリティリスクを最小限に抑えるためにこの方式を採用しています。

---

## 3-3. リフレッシュトークン取得手順

### ① Google Cloud Console でプロジェクト・API を準備する

1. [Google Cloud Console](https://console.cloud.google.com/) にアクセス

2. **新しいプロジェクトを作成**（既存プロジェクトがあればスキップ）
   - プロジェクト名: 例 `omoide-memory`

3. **Google Drive API を有効化**
   - 「API とサービス」→「ライブラリ」→「Google Drive API」を検索して有効化

### ② OAuth 同意画面を設定する

1. 「API とサービス」→「OAuth 同意画面」を開く
2. ユーザーの種類: **外部** を選択して「作成」
3. 必須項目を入力
   - アプリ名: 例 `omoide-memory`
   - ユーザーサポートメール: 自分のメールアドレス
   - デベロッパーの連絡先: 自分のメールアドレス
4. 「スコープ」の設定で以下を追加
   - `https://www.googleapis.com/auth/drive`
5. **「テストユーザー」にアクセスさせたい Google アカウントをすべて追加する**
   - ここに追加したアカウントのみが認証・リフレッシュトークンの取得を行えます

> ⚠️ アプリは**公開しないこと**。「アプリを公開」ボタンは押さずに「テスト」状態を維持してください。

### ③ OAuth クライアント ID を作成する

1. 「API とサービス」→「認証情報」→「認証情報を作成」→「OAuth クライアント ID」
2. アプリケーションの種類: **デスクトップアプリ**
3. 名前: 例 `omoide-desktop-client`
4. 作成後、`client_id` と `client_secret` をメモ

### ④ リフレッシュトークンを取得する（7日ごとに再実施）

ブラウザで以下の URL にアクセスして認可コードを取得します（`CLIENT_ID` は実際の値に置き換えてください）。

```
https://accounts.google.com/o/oauth2/v2/auth?client_id=CLIENT_ID&redirect_uri=urn:ietf:wg:oauth:2.0:oob&response_type=code&scope=https://www.googleapis.com/auth/drive&access_type=offline&prompt=consent
```

1. 対象アカウントでログインして権限を許可
2. 表示された**認可コード**をコピー
3. 以下のコマンドでリフレッシュトークンを取得（`CLIENT_ID` / `CLIENT_SECRET` / 認可コードは実際の値に置き換えてください）

```bash
curl -X POST https://oauth2.googleapis.com/token \
  -d client_id=CLIENT_ID \
  -d client_secret=CLIENT_SECRET \
  -d code=取得した認可コード \
  -d redirect_uri=urn:ietf:wg:oauth:2.0:oob \
  -d grant_type=authorization_code
```

4. レスポンス中の `"refresh_token"` の値をメモする
   - **再表示されないため必ず控えておくこと**
   - 複数アカウント分行う場合は、アカウントごとに上記手順を繰り返す

---

### 4. 環境変数の設定

「システムのプロパティ」→「環境変数」に以下を追加してください。

| 変数名                                  | 環境/システム | 対象コマンド                     | 説明                                                                       | 例 |
|--------------------------------------|---------|------------------------------|--------------------------------------------------------------------------|----|
| `spring.profiles.active`             | システム    | 全コマンド                        | Spring Boot プロファイル。デフォルトは debug                                          | `local` |
| `runnerName`                         | システム    | 全コマンド                        | 実行するコマンド名                                                                | `download-from-gdrive` |
| `GDRIVE_CLIENT_ID`                   | 環境      | download-from-gdrive         | OAuth クライアント ID                                                          | `xxxxx.apps.googleusercontent.com` |
| `GDRIVE_CLIENT_SECRET`               | 環境      | download-from-gdrive         | OAuth クライアントシークレット                                                       | `GOCSPX-xxxxxxxx` |
| `GDRIVE_REFRESH_TOKENS`              | 環境      | download-from-gdrive         | リフレッシュトークンをカンマ区切りで列挙（アカウント数分）                                           | `token_A,token_B,token_C` |
| `OMOIDE_FAMILY_ID`                   | 環境      | download-from-gdrive         | ファミリー識別子。データベースの `family_id` カラムに保存され、グループ単位でのデータ管理に使用されます。           | `my-home-01` |
| `OMOIDE_BACKUP_DIRECTORY`            | 環境      | download-from-gdrive / ImportFromLocal | バックアップ先ディレクトリ                                                            | `G:\my-memory` |
| `OMOIDE_COMMENT_FILE_PATH`           | 環境      | comment-import               | インポートするコメントファイルのパス                                                       | `C:\Users\user\comments.csv` |
| `EXTERNAL_STORAGE_BACKUP_DIRECTORY`  | 環境      | backup-to-local              | バックアップ先ディレクトリ                                                            | `G:\my-memory` |

### GDRIVE_REFRESH_TOKENS の設定例

複数アカウントのリフレッシュトークンをカンマ区切りで1つの環境変数にまとめます。

```
GDRIVE_REFRESH_TOKENS=1//token_for_account_A,1//token_for_account_B
```

- アカウントが1つの場合はそのまま1件だけ記載
- トークン自体にカンマが含まれないことを確認してから設定すること
- 7日ごとに失効するため、再取得後はこの変数を更新する

---

## 5. FFmpeg のインストール

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

## 6. 実行方法

実行するコマンドは `-DrunnerName` で指定します。環境変数は事前に設定済みであることを前提とします。

`.bat` は Shift\_JIS と UTF-8 の文字コード問題が発生しやすいため、`.ps1` を推奨します。

### download-from-gdrive

```powershell
$env:GDRIVE_CLIENT_ID      = "YOUR_CLIENT_ID"
$env:GDRIVE_CLIENT_SECRET  = "YOUR_CLIENT_SECRET"
$env:GDRIVE_REFRESH_TOKENS = "token_A,token_B"
$env:OMOIDE_FAMILY_ID      = "my-home-01"
$env:OMOIDE_BACKUP_DIRECTORY = "H:\YOUR_DIRECTORY"

$logFile = "$PSScriptRoot\log\download-from-gdrive.log"
"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行開始" | Out-File -FilePath $logFile -Encoding utf8

java -Dspring.profiles.active=local -DrunnerName=download-from-gdrive -jar C:\path\to\omoide-memory-downloader.jar 2>&1 |
    Out-File -FilePath $logFile -Encoding utf8 -Append

"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行終了" | Out-File -FilePath $logFile -Encoding utf8 -Append
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
$env:OMOIDE_BACKUP_DIRECTORY           = "H:\YOUR_DIRECTORY"
$env:EXTERNAL_STORAGE_BACKUP_DIRECTORY = "G:\YOUR_DIRECTORY"

$logFile = "$PSScriptRoot\log\backup-to-local.log"
"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行開始" | Out-File -FilePath $logFile -Encoding utf8

java -Dspring.profiles.active=local -DrunnerName=backup-to-local -jar C:\path\to\omoide-memory-downloader.jar 2>&1 |
    Out-File -FilePath $logFile -Encoding utf8 -Append

"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行終了" | Out-File -FilePath $logFile -Encoding utf8 -Append
```

---

## Windows タスクスケジューラでの定期実行

### 1. PowerShell スクリプトの作成

`run-omoide-downloader.ps1` を作成します。文字コードのトラブルを抑制するために、冒頭で UTF-8 出力を明示的に設定します。

```powershell
# UTF-8 出力設定（ログ文字化け防止）
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding             = [System.Text.Encoding]::UTF8

$env:GDRIVE_CLIENT_ID        = "YOUR_CLIENT_ID"
$env:GDRIVE_CLIENT_SECRET    = "YOUR_CLIENT_SECRET"
$env:GDRIVE_REFRESH_TOKENS   = "token_A,token_B"
$env:OMOIDE_FAMILY_ID        = "my-home-01"
$env:OMOIDE_BACKUP_DIRECTORY = "H:\YOUR_DIRECTORY"

$logFile  = "C:\logs\omoide-downloader.log"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

"[$timestamp] 実行開始" | Out-File -FilePath $logFile -Encoding utf8 -Append

java -Dspring.profiles.active=local -DrunnerName=download-from-gdrive -jar C:\path\to\omoide-memory-downloader.jar 2>&1 |
    Out-File -FilePath $logFile -Encoding utf8 -Append

"[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 実行終了" | Out-File -FilePath $logFile -Encoding utf8 -Append
```

> **ログファイルの文字コードについて**
> `Out-File -Encoding utf8` は Windows PowerShell 5.1 では BOM 付き UTF-8 で出力されます。BOM なしが必要な場合は `-Encoding utf8NoBOM`（PowerShell 7 以降）を使用してください。

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
3. **全般タブ**: 名前を設定し、「ユーザーがログオンしているかどうかにかかわらず実行する」を選択
4. **トリガータブ**: 「新規」→ 毎日深夜 2 時などに設定
5. **操作タブ**:
   - プログラム: `powershell.exe`
   - 引数: `-ExecutionPolicy Bypass -NonInteractive -File "C:\path\to\run-omoide-downloader.ps1"`
6. 「OK」で保存

> **リフレッシュトークンの更新について**
> テストモードのため、リフレッシュトークンは **7日ごとに失効**します。失効した場合は手順 3-3 ④ を再実施し、`GDRIVE_REFRESH_TOKENS` の値をスクリプト内で更新してください。

---

## インストールする必要があるもの

### Podman

Docker Desktop の代替として Podman を利用します。

理由:
- Docker Desktop が不要（ライセンス問題なし）
- 軽量
- rootless 実行可能
- Docker Compose 互換（`podman compose`）

個人開発用途では Podman で十分です。

---

#### Mac 環境

```bash
brew install podman
podman machine init
podman machine start
```

#### Windows 環境

Windows 環境では **WSL2（Windows Subsystem for Linux）上でコンテナを動かす構成**にします。さらに、**WSL の保存先を C ドライブ以外（例：G ドライブ）へ移動することを強く推奨**します。

---

### なぜ C ドライブ以外に WSL を配置するのか？

WSL2 は Linux ファイルシステムを1つの仮想ディスク（`ext4.vhdx`）として保存します。デフォルトでは C ドライブ配下に保存されるため、コンテナの DB データ・ボリューム・イメージ・Linux ホームディレクトリのすべてが **C ドライブ（SSD）を消費します**。

本アプリは個人用途を想定しており、DB は大きくなる可能性があるため、容量の大きいドライブに WSL を配置することを推奨します。

---

### セットアップ手順

#### ① WSL をインストール

PowerShell（管理者）で実行します。

```powershell
wsl --install
```

再起動後、Ubuntu などを初期セットアップします。

#### ② 現在の WSL 名を確認

```powershell
wsl -l -v
```

例:

```
NAME      STATE    VERSION
Ubuntu    Running  2
```

#### ③ WSL を G ドライブへ移動する

```powershell
# エクスポート
wsl --export Ubuntu G:\wsl-backup\ubuntu.tar

# 保存先ディレクトリ作成
mkdir G:\wsl\ubuntu

# G ドライブへインポート
wsl --import Ubuntu-G G:\wsl\ubuntu G:\wsl-backup\ubuntu.tar --version 2

# デフォルトに設定
wsl --set-default Ubuntu-G

# 動作確認後、古い WSL を削除
wsl --unregister Ubuntu
```

---

#### ④ Podman を winget でインストール

```powershell
winget install RedHat.Podman
podman --version
```

#### ⑤ Podman machine を作成して H ドライブへ移動する

```powershell
# machine 作成（いったん C に作る）
podman machine init
podman machine start

# 完全停止（重要）
podman machine stop
wsl --shutdown

# H ドライブへエクスポート
wsl --export podman-machine-default H:\wsl-backup\podman-machine-default.tar

# 登録解除
wsl --unregister podman-machine-default

# H ドライブへ再インポート（名前は必ず同じにすること）
wsl --import podman-machine-default H:\wsl\podman H:\wsl-backup\podman-machine-default.tar --version 2

# 再起動・確認
podman machine start
podman info
podman run --rm hello-world
```

> ⚠️ Podman は WSL ディストリ名で紐づくため、`--import` 時の名前を `podman-machine-default` のまま変えないことが重要です。

#### ⑥ コンテナ DB を起動

プロジェクトルートで実行します。

```bash
podman compose up -d
```

これにより PostgreSQL コンテナ・DB データ・コンテナイメージのすべてが G/H ドライブ上の WSL 領域に保存されます。

---

### この構成のメリット

| 項目               | 効果               |
| ---------------- | ---------------- |
| C ドライブ容量         | 消費しない            |
| Linux ネイティブ ext4 | 維持               |
| DB パフォーマンス       | Windows マウントより高速 |
| 運用の安定性           | 高い               |

> ⚠️ G/H ドライブが外付けの場合、取り外すと WSL が起動できなくなります。インポート後は必ず動作確認を行ってから旧ディストリを削除してください。
