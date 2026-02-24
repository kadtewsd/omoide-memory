# omoide-memory-downloader

おもいでメモリ ダウンローダー - Google Driveから写真・動画を自動ダウンロードして整理するWindowsバッチアプリケーション

---

## 概要

このアプリケーションは、Google Driveの指定フォルダに保存された写真・動画を自動的にローカルPCにダウンロードし、撮影日時に基づいて整理して保存します。保存完了後、Google Drive上のファイルは自動削除されます。

家庭内での写真・動画のバックアップを目的とした、無人実行可能なバッチアプリケーションです。

---

## 主な機能

### 1. Google Driveからのファイル取得
- 指定したフォルダ配下の全ファイルを自動取得
- サービスアカウントによる無人認証（ブラウザ認証不要）

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

# 3. Google Drive 認証設定（OAuth リフレッシュトークン方式）

## 3-1. リフレッシュトークンを使用する理由

当初、無人実行に適した**サービスアカウント**を使用していました。しかし、2025年4月15日以降に作成されたサービスアカウントは「マイドライブ」へのアクセスが制限されており、**ファイルの削除操作ができない**ことが判明しました。

そこで代替として、対象アカウントの**OAuth リフレッシュトークン**を使用する方式に切り替えました。リフレッシュトークンは一度取得すれば長期間有効であり、対象アカウントのすべての権限でドライブを操作できるため、ファイルの削除を含むバックアップ処理を無人で実行できます。

> ⚠️ **注意**: OAuth アプリが「テスト」モードの場合、リフレッシュトークンの有効期限は **7日間** です。本番運用には後述のアプリ公開（本番モード移行）が必須です。

---

## 3-2. リフレッシュトークン取得手順

### ① Google Cloud Console でプロジェクト・API を準備する

1. [Google Cloud Console](https://console.cloud.google.com/) にアクセス

2. **新しいプロジェクトを作成**（既存プロジェクトがあればスキップ）
    - プロジェクト名: 例 `omoide-memory`

3. **Google Drive API を有効化**
    - 「API とサービス」→「ライブラリ」→「Google Drive API」を検索して有効化

### ② OAuth 同意画面を設定する

1. 「API とサービス」→「OAuth 同意画面」を開く
2. [対象] にてユーザーの種類: **外部** を選択して「作成」
3. 必須項目を入力
    - アプリ名: 例 `omoide-memory`
    - ユーザーサポートメール: 自分のメールアドレス
    - デベロッパーの連絡先: 自分のメールアドレス
4. 「スコープ」の設定で以下を追加
    - `https://www.googleapis.com/auth/drive`
5. 「テストユーザー」に自分の Google アカウントを追加

### ③ OAuth クライアント ID を作成する

1. 「API とサービス」→「認証情報」→「認証情報を作成」→「OAuth クライアント ID」
2. [クライアント] にて [クライアントを作成]。アプリケーションの種類: **デスクトップアプリ**
3. 名前: 例 `omoide-desktop-client`
4. 作成後、`client_id` と `client_secret` をメモ（または JSON をダウンロード）

### ④ リフレッシュトークンを取得する

ブラウザで以下の URL にアクセスして認可コードを取得します（`CLIENT_ID` は実際の値に置き換えてください）。

```
https://accounts.google.com/o/oauth2/v2/auth?client_id=CLIENT_ID&redirect_uri=urn:ietf:wg:oauth:2.0:oob&response_type=code&scope=https://www.googleapis.com/auth/drive&access_type=offline&prompt=consent
```

1. 自分のアカウントでログインして権限を許可
2. 表示された**認可コード**をコピー
3. 以下のコマンドでリフレッシュトークンを取得

```bash
curl -X POST https://oauth2.googleapis.com/token \
  -d client_id=CLIENT_ID \
  -d client_secret=CLIENT_SECRET \
  -d code=取得した認可コード \
  -d redirect_uri=urn:ietf:wg:oauth:2.0:oob \
  -d grant_type=authorization_code
```

4. レスポンス中の `"refresh_token"` の値を安全な場所に保存
    - 推奨パス: `~/dev/secrets/omoide-memory/credentials.json`

```json
{
  "client_id": "YOUR_CLIENT_ID",
  "client_secret": "YOUR_CLIENT_SECRET",
  "refresh_token": "YOUR_REFRESH_TOKEN"
}
```

### ⑤ フォルダ ID を取得する

1. Google Drive で対象フォルダを開く
2. URL の末尾がフォルダ ID
    - 例: `https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9i0j` → ID は `1a2b3c4d5e6f7g8h9i0j`

---

## 3-3. アプリを公開してリフレッシュトークンを永続化する

OAuth アプリが**テストモード**のままだと、リフレッシュトークンは **7日後に失効**します。無人バックアップを継続稼働させるには、アプリを**本番モード（公開済み）**に移行する必要があります。

> 「公開」と言っても一般に公開されるわけではありません。スコープが `drive` のみであれば、Googleの審査なしに公開できます（未確認アプリの警告は出ますが動作に問題はありません）。

### 公開手順

1. [Google Cloud Console](https://console.cloud.google.com/) →「API とサービス」→「OAuth 同意画面」を開く
2. 「アプリを公開」ボタンをクリック
3. 確認ダイアログで「確認」

公開後は、同じ手順（3-2 ④）でリフレッシュトークンを再取得してください。このトークンは有効期限なしで使用できます（ただし、明示的にアクセスを取り消した場合を除く）。

### 4. 環境変数の設定

Windowsの環境変数に以下を設定します。

#### 設定方法（Windows）

「システムのプロパティ」→「環境変数」→「ユーザー環境変数」に以下を追加:

| 変数名 | 説明                              | 例                                                             |
|---|---------------------------------|---------------------------------------------------------------|
| `OMOIDE_BACKUP_DESTINATION` | ダウンロード先ディレクトリ                   | `G:\my-memory`                                                |
| `OMOIDE_GDRIVE_CREDENTIALS_PATH` | サービスアカウントJSONの絶対パス | `/Users/user/dev/secrets/omoide-memory/omoide-memory-sa.json` |
| `OMOIDE_FOLDER_ID` | Google DriveのフォルダID             | `1a2b3c4d5e6f7g8h9i0j`                                        |

**注意事項:**
- パス区切りは**スラッシュ (`/`)** を使用してください（Windowsでも同様）

5. ffmpg
6. # FFmpeg について（必須依存）

本アプリケーションは実行時に **FFmpeg** を必要とします。

## FFmpeg とは？

[FFmpeg](https://ffmpeg.org/) は、動画・音声の変換、解析、エンコード、デコードなどを行うためのオープンソースのマルチメディアフレームワークです。

コマンドラインツールとして以下が提供されています：

- `ffmpeg` … 動画・音声の変換やサムネイル生成などを行う
- `ffprobe` … 動画・音声ファイルのメタデータを解析する

---

## 本アプリケーションでの用途

本アプリケーションでは FFmpeg を以下の目的で使用しています：

- `ffprobe`
    - 動画のメタデータ取得
        - 解像度
        - フレームレート
        - コーデック情報
        - 再生時間 など

- `ffmpeg`
    - サムネイル画像の生成

そのため、実行時に `ffmpeg` および `ffprobe` コマンドが利用可能である必要があります。

---

## インストール方法

### macOS

#### Homebrew を使用する場合（推奨）

```bash
brew install ffmpeg
```

Homebrew でインストールした場合、自動的に PATH が通るため追加設定は不要です。

確認：
```
ffmpeg -version
ffprobe -version
```

### Linux

#### Ubuntu / Debian

```bash
sudo apt update
sudo apt install ffmpeg
```

#### Arch Linux

```bash
sudo pacman -S ffmpeg
```

インストール後、通常は自動的に PATH に追加されます。

---

### Windows

1. 公式サイトから Windows ビルドをダウンロード
   [https://ffmpeg.org/download.html](https://ffmpeg.org/download.html)

2. zip を展開（例）

```
C:\ffmpeg\bin
```

3. `bin` ディレクトリを **環境変数 PATH** に追加する

4. PowerShell で確認

```powershell
ffmpeg -version
ffprobe -version
```

---

## カスタムパスを使用する場合

PATH に追加せずに使用する場合は、以下の環境変数で実行ファイルの場所を指定できます。

```bash
FFMPEG_PATH=/path/to/ffmpeg
FFPROBE_PATH=/path/to/ffprobe
```

Windows の場合：

```powershell
setx FFMPEG_PATH "C:\ffmpeg\bin\ffmpeg.exe"
setx FFPROBE_PATH "C:\ffmpeg\bin\ffprobe.exe"
```

---

#### コマンドラインでの設定例

```cmd
set OMOIDE_BACKUP_DESTINATION=G:\my-memory
set OMOIDE_GDRIVE_CREDENTIALS_PATH=c:/dev/secrets/omoide-memory/omoide-memory-sa.json
set OMOIDE_FOLDER_ID=1a2b3c4d5e6f7g8h9i0j
```

### 5. Spring設定ファイル

`src/main/resources/application-local.yml` を作成し、以下を記述します。

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/omoide_memory?currentSchema=omoide_memory
    username: root
    password: root
```

---

## 実行方法

### 開発時の実行

```bash
./gradlew :omoide-memory-downloader:bootRun -Dspring.profiles.active=local -Drunner_name=downloadFromGDrive
```

### jarファイルでの実行

```bash
# ビルド
./gradlew :omoide-memory-downloader:build

# 実行
java -Dspring.profiles.active=local -Drunner_name=downloadFromGDrive -jar omoide-memory-downloader/build/libs/omoide-memory-downloader-0.0.1-SNAPSHOT.jar
```

---

## システムプロパティ

実行時に以下のシステムプロパティを**必ず**指定してください。

| プロパティ名 | 説明 | 必須 | 例 |
|---|---|---|---|
| `spring.profiles.active` | Spring Bootプロファイル | ✅ | `local` |
| `runner_name` | 実行するApplicationRunnerの名前 | ✅ | `downloadFromGDrive` |

**指定方法:**
```bash
java -Dspring.profiles.active=local -Drunner_name=downloadFromGDrive -jar app.jar
```

---

## Windowsタスクスケジューラでの定期実行

### 1. バッチファイルの作成

`run-omoide-downloader.bat` を作成します。

```batch
@echo off
set OMOIDE_BACKUP_DESTINATION=G:\my-memory
set OMOIDE_GDRIVE_CREDENTIALS_PATH=/Users/user/dev/secrets/omoide-memory/omoide-memory-sa.json
set OMOIDE_FOLDER_ID=1a2b3c4d5e6f7g8h9i0j

java -Dspring.profiles.active=local -Drunner_name=downloadFromGDrive -jar C:\path\to\omoide-memory-downloader-0.0.1-SNAPSHOT.jar >> C:\logs\omoide-downloader.log 2>&1
```

### 2. タスクスケジューラの設定

1. タスクスケジューラを開く
2. 「タスクの作成」をクリック
3. **全般タブ**
   - 名前: `おもいでメモリダウンローダー`
   - 「ユーザーがログオンしているときのみ実行する」を選択
4. **トリガータブ**
   - 「新規」→ 毎日深夜2時などに設定
5. **操作タブ**
   - 「新規」→「プログラムの開始」
   - プログラム: `C:\path\to\run-omoide-downloader.bat`
6. 「OK」で保存

---

## トラブルシューティング

### エラー: `環境変数 OMOIDE_BACKUP_DESTINATION が設定されていません`

→ 環境変数が正しく設定されているか確認してください。コマンドプロンプトで `echo %OMOIDE_BACKUP_DESTINATION%` で確認できます。

### エラー: `絶対パスは指定できません`

→ `OMOIDE_GDRIVE_CREDENTIALS_PATH` に絶対パスを指定してください。

### エラー: `User credentials are required to call the Google Drive API`

→ サービスアカウントのJSONファイルが正しく読み込まれていません。パスを確認してください。

### エラー: `The caller does not have permission`

→ Google Driveのフォルダがサービスアカウントに共有されていません。フォルダの共有設定を確認してください。

### Nominatim APIのレート制限エラー

→ Nominatim APIは1秒に1リクエストまでの制限があります。大量のファイルを処理する場合は時間がかかります。アプリ側で自動的に1秒の遅延を入れています。

---

## データベーススキーマ

### 写真テーブル (`synced_omoide_photo`)

撮影日時、GPS座標、カメラ設定（絞り・ISO・焦点距離など）、デバイス情報を保存します。

### 動画テーブル (`synced_omoide_video`)

撮影日時、GPS座標、動画情報（解像度・コーデック・フレームレート・音声情報など）、サムネイル画像を保存します。

詳細は `omoide-memory-jooq` プロジェクトのマイグレーションファイルを参照してください。

---

## 今後の拡張予定

- [ ] 複数フォルダ対応
- [ ] Slack/LINE通知機能
- [ ] 進捗表示UI
- [ ] 差分同期（変更されたファイルのみ再取得）
- [ ] Dropbox/OneDrive対応

---

## ライセンス

家庭内利用を目的としたプライベートプロジェクトです。
