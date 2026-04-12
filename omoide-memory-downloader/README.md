# omoide-memory-downloader

おもいでメモリ ダウンローダー - Google Driveから写真・動画を自動ダウンロードして整理するWindowsバッチアプリケーション

---

## 概要

このアプリケーションは、Google Drive のルート直下に保存された写真・動画を自動的にローカル PC にダウンロードし、撮影日時に基づいて整理して保存します。

家庭内での写真・動画のバックアップを目的とした、無人実行可能なバッチアプリケーションです。

---

## ⚠️ セキュリティに関する重要な注意事項

本アプリは Google Drive に認証してアクセスします。認証方式によって取り扱うべき機密情報が異なりますが、いずれの方式でも**認証情報が漏洩した場合は第三者があなたの Google Drive を操作できます。**

以下を必ず守ってください。

- **Git にコミットしない**
  `.gitignore` に環境変数ファイルや bat / ps1 ファイルのパスを追加し、絶対にリポジトリに含めないこと。誤ってコミットした場合は、履歴ごと削除したうえで Google Cloud Console から認証情報を即座に無効化すること。

- **安全な場所に保管する**
  プロジェクトディレクトリの外（例: `~/dev/secrets/`）に保存し、OS のアクセス権限を適切に設定すること。

- **漏洩した場合は即座に無効化する**
  [Google Cloud Console](https://console.cloud.google.com/) →「API とサービス」→「認証情報」から対象のクライアントまたはサービスアカウントキーを削除・無効化することで、該当認証情報は即時失効します。

---

## 主な機能

### 1. 自動ファイル取得

- Google Drive の **ルート直下** にある全ファイルを自動取得
- 複数アカウントの同時監視に対応

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

### 5. ダウンロード後の後処理（選択制）

ローカル保存とDB登録が完了したファイルをどう扱うかは、認証方式によって異なります。詳細は後述の「認証方式の選択」を参照してください。

---

## 必要な環境

| 項目                    | バージョン                        |
|-----------------------|------------------------------|
| OS                    | Windows 11                   |
| JDK                   | 17                           |
| PostgreSQL            | 16（docker-composeで起動可能）      |
| Google Cloud Platform | 後述の認証方式に応じて設定                |
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

# 3. Google Drive 認証設定

## 認証方式の選択

本アプリは以下の2つの認証方式に対応しています。用途に応じていずれかを選択してください。

| 比較項目 | OAuth リフレッシュトークン方式 | サービスアカウント（SA）方式 |
|---|---|---|
| **Drive 上のファイル削除** | ✅ 可能（本人アカウントとして操作） | ❌ 2025年4月15日以降作成のSAでは不可 |
| **トークンの期限** | ⚠️ テストモードでは7日で失効 | ✅ 期限なし（キーファイルを削除しない限り） |
| **無人運用の手間** | ❌ 7日ごとに手動再取得が必要 | ✅ 一度設定すれば継続的に無人実行可能 |
| **セットアップの複雑さ** | 中（ブラウザ操作が必要） | 中（GCP でのSA作成・共有設定が必要） |
| **用途** | Drive上のファイルを削除しながら取り込みたい場合 | ダウンロードのみ行い、削除は別の手段で行う場合 |

### どちらを選ぶべきか

**SA方式（デフォルト実装）を推奨します。**

Drive 上のファイル削除については、**モバイルアプリ（omoide-memory Android アプリ）側で指定ファイルを削除する機能を追加予定**です。バックアップ完了後はモバイルアプリ側で削除操作を行う運用にすることで、SA 方式の「削除不可」という制約を実用上カバーできます。7日ごとのトークン再取得が不要な分、SA方式の方が日常運用は楽です。

一方、**「ダウンロードと同時に Drive 上からも完全に消したい」という要件がある場合はOAuth方式**を選択してください。ただし定期的な手動作業が発生することを許容する必要があります。

---

## 3-A. サービスアカウント（SA）方式

### 概要と制約

サービスアカウントは無人実行に最適で、トークンの失効がありません。ただし、**2025年4月15日以降に作成されたサービスアカウントは「マイドライブ」へのアクセスが制限されており、ファイルの削除操作ができません。**

そのため本実装では、ダウンロード完了後の Drive 上ファイル削除処理は無効化されています（コード上はコメントアウト）。削除はモバイルアプリ側や手動で行ってください。

### SA の作成手順

#### ① Google Cloud Console でプロジェクト・API を準備する

1. [Google Cloud Console](https://console.cloud.google.com/) にアクセス
2. **新しいプロジェクトを作成**（既存プロジェクトがあればスキップ）
   - プロジェクト名: 例 `omoide-memory`
3. **Google Drive API を有効化**
   - 「API とサービス」→「ライブラリ」→「Google Drive API」を検索して有効化

#### ② サービスアカウントを作成する

1. 「IAM と管理」→「サービスアカウント」→「サービスアカウントを作成」
2. 名前・ID を設定（例: `omoide-downloader`）
3. ロールは不要（Drive アクセスは共有設定で制御するため）
4. 作成後、「キー」タブ →「鍵を追加」→「JSON」でキーファイルをダウンロード
   - **このJSONファイルが秘密鍵です。Git には絶対に含めないこと**

#### ③ Drive のフォルダをSAと共有する

対象の Google Drive アカウントにて、監視したいフォルダ（またはルート直下のファイルを対象にする場合はフォルダ単位での共有設定）を SA のメールアドレス（`xxx@xxx.iam.gserviceaccount.com`）と共有します。

> ⚠️ SA はマイドライブのルート直下にあるファイルを直接列挙することはできません。**対象ファイルを特定のフォルダに入れて共有する運用**にしてください。

#### ④ 環境変数を設定する

| 変数名 | 説明 | 例 |
|---|---|---|
| `GDRIVE_SA_KEY_PATH` | SAキーJSONファイルのパス | `C:\secrets\sa-key.json` |
| `OMOIDE_FAMILY_ID` | ファミリー識別子 | `my-home-01` |
| `OMOIDE_BACKUP_DIRECTORY` | バックアップ先ディレクトリ | `G:\my-memory` |

---

## 3-B. OAuth リフレッシュトークン方式

### 概要と制約

本人アカウントとして Drive を操作できるため、ファイルの削除も含めた完全な操作が可能です。ただし、**テストモードで運用する場合はリフレッシュトークンが7日ごとに失効**するため、定期的な手動再取得が必要です。

### なぜテストモードのままにするのか

本アプリは **Google Cloud の OAuth 同意画面を「テスト」モードのまま運用します。**

理由は以下の通りです。

- 本アプリは **家庭内の特定アカウントのみ**を対象としており、不特定多数への公開を想定していない
- 公開すると任意の Google アカウントがこのアプリを使って自分のドライブへアクセスできる状態になる
- クライアントID・シークレットが漏洩した際のリスクが、テストモードよりも格段に大きくなる

### テストモードの制約

| 項目 | 内容 |
|------|------|
| 登録できるテストユーザー数 | 最大100アカウント |
| リフレッシュトークンの有効期限 | **7日で失効** |
| 無人運用 | **そのままでは不可**（後述の対応が必要） |

### ⚠️ リフレッシュトークン7日失効への対応

テストモードでは、リフレッシュトークンが7日ごとに失効します。失効すると次回実行時にエラーになるため、**定期的に手動で再取得が必要**です。

再取得は後述の手順（④）を再実施するだけです。

### リフレッシュトークン取得手順

#### ① Google Cloud Console でプロジェクト・API を準備する

1. [Google Cloud Console](https://console.cloud.google.com/) にアクセス

2. **新しいプロジェクトを作成**（既存プロジェクトがあればスキップ）
   - プロジェクト名: 例 `omoide-memory`

3. **Google Drive API を有効化**
   - 「API とサービス」→「ライブラリ」→「Google Drive API」を検索して有効化

#### ② OAuth 同意画面を設定する

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

#### ③ OAuth クライアント ID を作成する

1. 「API とサービス」→「認証情報」→「認証情報を作成」→「OAuth クライアント ID」
2. アプリケーションの種類: **デスクトップアプリ**
3. 名前: 例 `omoide-desktop-client`
4. 作成後、`client_id` と `client_secret` をメモ

#### ④ リフレッシュトークンを取得する（7日ごとに再実施）

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

#### ⑤ 環境変数を設定する

| 変数名 | 説明 | 例 |
|---|---|---|
| `GDRIVE_CLIENT_ID` | OAuth クライアント ID | `xxxxx.apps.googleusercontent.com` |
| `GDRIVE_CLIENT_SECRET` | OAuth クライアントシークレット | `GOCSPX-xxxxxxxx` |
| `GDRIVE_REFRESH_TOKENS` | リフレッシュトークン（複数アカウントはカンマ区切り） | `token_A,token_B` |
| `OMOIDE_FAMILY_ID` | ファミリー識別子 | `my-home-01` |
| `OMOIDE_BACKUP_DIRECTORY` | バックアップ先ディレクトリ | `G:\my-memory` |

**`GDRIVE_REFRESH_TOKENS` の設定例:**

```
GDRIVE_REFRESH_TOKENS=1//token_for_account_A,1//token_for_account_B
```

- アカウントが1つの場合はそのまま1件だけ記載
- トークン自体にカンマが含まれないことを確認してから設定すること
- 7日ごとに失効するため、再取得後はこの変数を更新する

---

### 4. 共通環境変数

認証方式に依存しない共通の環境変数は以下の通りです。「システムのプロパティ」→「環境変数」に追加してください。

| 変数名 | 環境/システム | 対象コマンド | 説明 | 例 |
|------|---------|---------|------|---|
| `spring.profiles.active` | システム | 全コマンド | Spring Boot プロファイル | `local` |
| `runnerName` | システム | 全コマンド | 実行するコマンド名 | `download-from-gdrive` |
| `OMOIDE_FAMILY_ID` | 環境 | download-from-gdrive | ファミリー識別子 | `my-home-01` |
| `OMOIDE_BACKUP_DIRECTORY` | 環境 | download-from-gdrive / ImportFromLocal | バックアップ先ディレクトリ | `G:\my-memory` |
| `OMOIDE_COMMENT_FILE_PATH` | 環境 | comment-import | インポートするコメントファイルのパス | `C:\Users\user\comments.csv` |
| `EXTERNAL_STORAGE_BACKUP_DIRECTORY` | 環境 | backup-to-local | バックアップ先ディレクトリ | `G:\my-memory` |

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
# --- SA方式の場合 ---
$env:GDRIVE_SA_KEY_PATH      = "C:\secrets\sa-key.json"
$env:OMOIDE_FAMILY_ID        = "my-home-01"
$env:OMOIDE_BACKUP_DIRECTORY = "H:\YOUR_DIRECTORY"

# --- OAuth方式の場合 ---
# $env:GDRIVE_CLIENT_ID      = "YOUR_CLIENT_ID"
# $env:GDRIVE_CLIENT_SECRET  = "YOUR_CLIENT_SECRET"
# $env:GDRIVE_REFRESH_TOKENS = "token_A,token_B"
# $env:OMOIDE_FAMILY_ID      = "my-home-01"
# $env:OMOIDE_BACKUP_DIRECTORY = "H:\YOUR_DIRECTORY"

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

# --- SA方式の場合 ---
$env:GDRIVE_SA_KEY_PATH      = "C:\secrets\sa-key.json"
$env:OMOIDE_FAMILY_ID        = "my-home-01"
$env:OMOIDE_BACKUP_DIRECTORY = "H:\YOUR_DIRECTORY"

# --- OAuth方式の場合（7日ごとにこのトークンを更新する必要あり）---
# $env:GDRIVE_CLIENT_ID        = "YOUR_CLIENT_ID"
# $env:GDRIVE_CLIENT_SECRET    = "YOUR_CLIENT_SECRET"
# $env:GDRIVE_REFRESH_TOKENS   = "token_A,token_B"
# $env:OMOIDE_FAMILY_ID        = "my-home-01"
# $env:OMOIDE_BACKUP_DIRECTORY = "H:\YOUR_DIRECTORY"

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

> **OAuth方式を使用している場合のリフレッシュトークン更新について**
> テストモードのため、リフレッシュトークンは **7日ごとに失効**します。失効した場合は手順 3-B ④ を再実施し、スクリプト内の `GDRIVE_REFRESH_TOKENS` の値を更新してください。SA方式ではこの作業は不要です。

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
