# 3. Google Drive 認証設定（OAuth リフレッシュトークン方式）

## 3-1. リフレッシュトークンを使用する理由

当初、無人実行に適した**サービスアカウント**を使用していました。しかし、2025年4月15日以降に作成されたサービスアカウントは「マイドライブ」へのアクセスが制限されており、**ファイルの削除操作ができない**ことが判明しました。

そこで代替として、対象アカウントの**OAuth リフレッシュトークン**を使用する方式に切り替えました。リフレッシュトークンは revoke しない限り無期限で有効であり、対象アカウントのすべての権限でドライブを操作できるため、ファイルの削除を含むバックアップ処理を無人で実行できます。

> ⚠️ OAuth アプリが**テストモード**の場合、リフレッシュトークンは7日で失効します。無人運用には後述のアプリ公開が必須です。

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
2. ユーザーの種類: **外部** を選択して「作成」
3. 必須項目を入力
   - アプリ名: 例 `omoide-memory`
   - ユーザーサポートメール: 自分のメールアドレス
   - デベロッパーの連絡先: 自分のメールアドレス
4. 「スコープ」の設定で以下を追加
   - `https://www.googleapis.com/auth/drive`
5. 「テストユーザー」に自分の Google アカウントを追加

### ③ アプリを公開する

テストモードのままではリフレッシュトークンが7日で失効します。以下の手順で公開モードに移行してください。

> 「公開」と言っても一般に公開されるわけではありません。`drive` スコープのみであれば Google の審査なしに公開できます（未確認アプリの警告は出ますが動作に問題はありません）。

1. 「OAuth 同意画面」→「アプリを公開」ボタンをクリック
2. 確認ダイアログで「確認」

### ④ OAuth クライアント ID を作成する

1. 「API とサービス」→「認証情報」→「認証情報を作成」→「OAuth クライアント ID」
2. アプリケーションの種類: **デスクトップアプリ**
3. 名前: 例 `omoide-desktop-client`
4. 作成後、`client_id` と `client_secret` をメモ

### ⑤ リフレッシュトークンを取得する（初回のみ）

ブラウザで以下の URL にアクセスして認可コードを取得します（`CLIENT_ID` は実際の値に置き換えてください）。

```
https://accounts.google.com/o/oauth2/v2/auth?client_id=CLIENT_ID&redirect_uri=urn:ietf:wg:oauth:2.0:oob&response_type=code&scope=https://www.googleapis.com/auth/drive&access_type=offline&prompt=consent
```

1. 自分のアカウントでログインして権限を許可
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
   - この値は次の手順でシステムプロパティに設定する

### ⑥ フォルダ ID を取得する

1. Google Drive で対象フォルダを開く
2. URL の末尾がフォルダ ID
   - 例: `https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9i0j` → ID は `1a2b3c4d5e6f7g8h9i0j`

---

## 3-3. セキュリティに関する重要な注意事項

`gdriveClientSecret` と `gdriveRefreshToken` が漏洩した場合、**第三者があなたの Google Drive に対してファイルの閲覧・ダウンロード・削除を含むあらゆる操作を実行できます。**

以下を必ず守ってください。

- **bat ファイルや設定ファイルを Git にコミットしない**  
  `.gitignore` に bat ファイルのパスを追加し、絶対にリポジトリに含めないこと。

- **漏洩した場合は即座に無効化する**  
  [Google Cloud Console](https://console.cloud.google.com/) →「API とサービス」→「認証情報」から OAuth クライアントを削除することで、該当トークンは即時失効します。

---

# 4. システムプロパティ

すべてのパラメータはシステムプロパティ（`-D` オプション）で指定します。

| プロパティ名 | 対象コマンド | 説明 | 例 |
|---|---|---|---|
| `spring.profiles.active` | 全コマンド | Spring Boot プロファイル | `local` |
| `runnerName` | 全コマンド | 実行するコマンド名 | `downloadFromGDrive` |
| `gdriveClientId` | DownloadFromGDrive | OAuth クライアント ID | `123456789-abcdef.apps.googleusercontent.com` |
| `gdriveClientSecret` | DownloadFromGDrive | OAuth クライアントシークレット | `GOCSPX-xxxxxxxxxx` |
| `gdriveRefreshToken` | DownloadFromGDrive | ⑤で取得したリフレッシュトークン | `1//0gxxxxxxxx` |
| `gdriveFolderId` | DownloadFromGDrive | ダウンロード対象の Google Drive フォルダ ID | `1a2b3c4d5e6f7g8h9i0j` |
| `backupDestination` | DownloadFromGDrive | ダウンロードしたファイルの保存先ディレクトリ | `G:\my-memory` |
| `localBackupDestination` | ImportFromLocal | ダウンロード済みファイルのバックアップ先ディレクトリ | `G:\my-memory` |
| `commentFilePath` | CommentImportCommand | インポートするコメントファイルのパス | `C:\Users\user\comments.csv` |

---

# 5. 実行方法

### 開発時の実行

```bash
# DownloadFromGDrive
./gradlew :omoide-memory-downloader:bootRun \
  -Dspring.profiles.active=local \
  -DrunnerName=downloadFromGDrive \
  -DgdriveClientId=YOUR_CLIENT_ID \
  -DgdriveClientSecret=YOUR_CLIENT_SECRET \
  -DgdriveRefreshToken=YOUR_REFRESH_TOKEN \
  -DgdriveFolderId=1a2b3c4d5e6f7g8h9i0j \
  -DbackupDestination=G:\my-memory

# ImportFromLocal
./gradlew :omoide-memory-downloader:bootRun \
  -Dspring.profiles.active=local \
  -DrunnerName=importFromLocal \
  -DlocalBackupDestination=G:\my-memory

# CommentImportCommand
./gradlew :omoide-memory-downloader:bootRun \
  -Dspring.profiles.active=local \
  -DrunnerName=commentImport \
  -DcommentFilePath=C:\Users\user\comments.csv
```

### jar ファイルでの実行

```bash
# ビルド
./gradlew :omoide-memory-downloader:build

# DownloadFromGDrive
java \
  -Dspring.profiles.active=local \
  -DrunnerName=downloadFromGDrive \
  -DgdriveClientId=YOUR_CLIENT_ID \
  -DgdriveClientSecret=YOUR_CLIENT_SECRET \
  -DgdriveRefreshToken=YOUR_REFRESH_TOKEN \
  -DgdriveFolderId=1a2b3c4d5e6f7g8h9i0j \
  -DbackupDestination=G:\my-memory \
  -jar omoide-memory-downloader/build/libs/omoide-memory-downloader-0.0.1-SNAPSHOT.jar

# ImportFromLocal
java \
  -Dspring.profiles.active=local \
  -DrunnerName=importFromLocal \
  -DlocalBackupDestination=G:\my-memory \
  -jar omoide-memory-downloader/build/libs/omoide-memory-downloader-0.0.1-SNAPSHOT.jar

# CommentImportCommand
java \
  -Dspring.profiles.active=local \
  -DrunnerName=commentImport \
  -DcommentFilePath=C:\Users\user\comments.csv \
  -jar omoide-memory-downloader/build/libs/omoide-memory-downloader-0.0.1-SNAPSHOT.jar
```

---

# 6. Windows タスクスケジューラでの定期実行

### 1. バッチファイルの作成

`run-omoide-downloader.bat` を作成します。

```batch
@echo off
java ^
  -Dspring.profiles.active=local ^
  -DrunnerName=downloadFromGDrive ^
  -DgdriveClientId=YOUR_CLIENT_ID ^
  -DgdriveClientSecret=YOUR_CLIENT_SECRET ^
  -DgdriveRefreshToken=YOUR_REFRESH_TOKEN ^
  -DgdriveFolderId=1a2b3c4d5e6f7g8h9i0j ^
  -DbackupDestination=G:\my-memory ^
  -jar C:\path\to\omoide-memory-downloader-0.0.1-SNAPSHOT.jar >> C:\logs\omoide-downloader.log 2>&1
```

### 2. タスクスケジューラの設定

1. タスクスケジューラを開く
2. 「タスクの作成」をクリック
3. **全般タブ**
   - 名前: `おもいでメモリダウンローダー`
   - 「ユーザーがログオンしているときのみ実行する」を選択
4. **トリガータブ**
   - 「新規」→ 毎日深夜 2 時などに設定
5. **操作タブ**
   - 「新規」→「プログラムの開始」
   - プログラム: `C:\path\to\run-omoide-downloader.bat`
6. 「OK」で保存
