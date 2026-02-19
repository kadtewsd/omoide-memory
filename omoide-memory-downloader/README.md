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

| 項目 | バージョン                 |
|---|-----------------------|
| OS | Windows 11            |
| JDK | 17                    |
| PostgreSQL | 16（docker-composeで起動可能） |
| Google Cloud Platform | サービスアカウント             |

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

### 3. Google Drive サービスアカウントの設定

#### 3-1. サービスアカウントを作成する理由

このアプリは**無人実行**を前提としているため、ブラウザでの認証フローが使えません。
サービスアカウントを使用することで、JSONファイルのみで認証が完結し、Windowsタスクスケジューラでの自動実行が可能になります。

#### 3-2. サービスアカウント作成手順

1. **Google Cloud Consoleにアクセス**
   - https://console.cloud.google.com/

2. **新しいプロジェクトを作成**（まだない場合）
   - プロジェクト名: 例 `omoide-memory`

3. **Google Drive APIを有効化**
   - 「APIとサービス」→「ライブラリ」→「Google Drive API」を検索して有効化

4. **サービスアカウントを作成**
   - 「IAM と管理」→「サービス アカウント」→「サービス アカウントを作成」
   - 名前: 例 `omoide-downloader`
   - 「作成して続行」をクリック
   - ロールは不要（スキップ）

5. **JSONキーをダウンロード**
   - 作成したサービスアカウントをクリック
   - 「キー」タブ →「鍵を追加」→「新しい鍵を作成」
   - 「JSON」を選択して「作成」
   - ダウンロードされたJSONファイルを安全な場所に保存
     - 推奨パス: `~/dev/secrets/omoide-memory/omoide-memory-sa.json`

6. **Google Driveのフォルダを共有**
   - Google Driveで対象フォルダを右クリック → 「共有」
   - サービスアカウントのメールアドレス（例: `omoide-downloader@your-project.iam.gserviceaccount.com`）を入力して共有
   - **重要**: 2025年4月15日以降に作成されたサービスアカウントは「マイドライブ」にアクセスできません。**共有ドライブ**を使用してください。

7. **フォルダIDを取得**
   - Google Driveでフォルダを開く
   - URLの末尾がフォルダID
     - 例: `https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9i0j` → ID は `1a2b3c4d5e6f7g8h9i0j`

### 4. 環境変数の設定

Windowsの環境変数に以下を設定します。

#### 設定方法（Windows）

「システムのプロパティ」→「環境変数」→「ユーザー環境変数」に以下を追加:

| 変数名 | 説明 | 例 |
|---|---|---|
| `OMOIDE_BACKUP_DESTINATION` | ダウンロード先ディレクトリ | `G:\my-memory` |
| `OMOIDE_GDRIVE_CREDENTIALS_PATH_FROM_HOME` | サービスアカウントJSONの相対パス（ホームディレクトリから） | `dev/secrets/omoide-memory/omoide-memory-sa.json` |
| `OMOIDE_FOLDER_ID` | Google DriveのフォルダID | `1a2b3c4d5e6f7g8h9i0j` |

**注意事項:**
- `OMOIDE_GDRIVE_CREDENTIALS_PATH_FROM_HOME` は**相対パス**で指定してください
- パス区切りは**スラッシュ (`/`)** を使用してください（Windowsでも同様）
- 先頭にスラッシュは不要です
- 絶対パスを指定するとエラーになります

#### コマンドラインでの設定例

```cmd
set OMOIDE_BACKUP_DESTINATION=G:\my-memory
set OMOIDE_GDRIVE_CREDENTIALS_PATH_FROM_HOME=dev/secrets/omoide-memory/omoide-memory-sa.json
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
set OMOIDE_GDRIVE_CREDENTIALS_PATH_FROM_HOME=dev/secrets/omoide-memory/omoide-memory-sa.json
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

→ `OMOIDE_GDRIVE_CREDENTIALS_PATH_FROM_HOME` に絶対パスを指定していませんか？相対パスで指定してください。

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
