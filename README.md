# omoide-memory (おもいでメモリ)

家族の大切な写真・動画を安全にバックアップ・整理し、家族間で楽しく閲覧・共有・活用するための統合プライベートシステムです。

---

## 🎯 やりたいこと（目的とコアフロー）

スマートフォンで日々撮影される大量の写真や動画を、クラウドの容量制限やプライバシーを気にすることなく、自宅ストレージに安全に保管し、家族みんなで快適に共有・活用できる仕組みを提供します。

### 全体アーキテクチャ・フロー

```
[スマートフォン (Android)]
  │  omoide-memory-uploader
  │  自宅の安全な Wi-Fi を検知して写真・動画を自動/手動アップロード
  ▼
[一時保管: Google Drive]
  │  (写真・動画ファイル + FCM デバイストークン)
  ▼
[ホームサーバー / PC バッチ]
  │  omoide-memory-downloader
  │  ① Google Drive から未取り込みファイルを自動ダウンロード
  │  ② 撮影年月別 (YYYY/MM/photo, YYYY/MM/video) にローカルストレージへ自動整理
  │  ③ EXIF / 動画メタデータ抽出 (FFmpeg)・位置情報逆ジオコーディング
  │  ④ PostgreSQL データベースへ同期
  │  ⑤ FCM 経由で Android 端末へダウンロード完了プッシュ通知を送信
  ▼
[LAN 内 Web アプリ (ブラウザ)]
  omoide-memory-sharing (Backend / Frontend)
  ① 写真・動画フィードの年月別・コメント付き高速ブラウズ
  ② アルバム作成 & ZIP ダウンロード
  ③ フォトブック作成支援:
     - ユーザーが「このアルバムの枚数 N」を指定
     - お気に入りの写真を M 枚手動選択
     - 「あと N - M 枚はランダムで補完」して一括 ZIP ダウンロード
```

---

## 📁 フォルダ構成（リポジトリ構造）

本リポジトリはマルチプロジェクト（モノレポ）構成となっています。

```
omoide-memory/
├── omoide-memory-uploader/     # Android アップローダーアプリ
├── omoide-memory-downloader/   # Google Drive ダウンロード & バックアップバッチ
├── omoide-memory-sharing/      # Web 写真共有・閲覧・フォトブック選択アプリ
│   ├── backend/                # 共有 Web API (Spring Boot + jOOQ)
│   └── frontend/               # 共有 Web UI (React 19 + TypeScript)
├── omoide-memory-jooq/         # jOOQ 共通 DB アクセスモジュール
├── omoide-memory-migration/    # Flyway による PostgreSQL マイグレーション
├── ai_script/                  # 各機能の仕様書・設計書・作業手順
├── scripts/                    # 運用・開発補助スクリプト (ktlint-all.sh 等)
├── docker-compose.yml          # PostgreSQL 16 開発用コンテナ設定
└── README.md                   # 本ドキュメント
```

### モジュール別詳細

| ディレクトリ | 役割 | 主な技術スタック |
|---|---|---|
| **`omoide-memory-uploader`** | スマホの写真・動画を Google Drive へアップロードする Android アプリ。信頼できる自宅 Wi-Fi 接続時のみの自動同期や FCM トークン連携に対応。 | Kotlin, Jetpack Compose, WorkManager, Hilt, Room, FCM |
| **`omoide-memory-downloader`** | Google Drive からファイルを自動取得し、年月フォルダへ整理・メタデータ解析 (EXIF/FFmpeg)・DB 登録を行い、完了時に端末へ PUSH 通知を送る無人実行バッチ。 | Kotlin, Spring Boot (ApplicationRunner), Google Drive API, FFmpeg, FCM HTTP v1 |
| **`omoide-memory-sharing`** | 自宅 LAN 内で家族が写真・動画を閲覧・コメントしたり、アルバム作成やフォトブック用写真選択を行う Web システム。 | - |
| ├─ **`backend`** | 写真・動画フィード提供、コメント管理、アルバム ZIP 生成、フォトブック用ランダム補完 API。 | Kotlin, Spring Boot 3 (WebFlux), jOOQ, R2DBC |
| └─ **`frontend`** | 年月タブ切り替え、フィード表示、アルバム作成、目標枚数指定とランダム自動補完を備えたフォトブック選択 UI。 | React 19, TypeScript, Vite, Tailwind CSS |
| **`omoide-memory-jooq`** | jOOQ コード生成および共通データベース操作インターフェース。 | jOOQ, Kotlin, PostgreSQL |
| **`omoide-memory-migration`** | データベーススキーマのバージョン管理・適用。 | Flyway, Spring Boot, PostgreSQL |

---

## 🚀 クイックスタート（環境構築）

### 1. データベースの起動 (PostgreSQL)

モノレポ直下で Docker / Podman を起動します：

```bash
docker compose up -d
# または
podman compose up -d
```

### 2. マイグレーションの実行

必要なテーブル（`synced_omoide_photo`, `synced_omoide_video` など）を作成します：

```bash
cd omoide-memory-migration
./gradlew bootRun
```

各サブモジュールの個別セットアップや起動手順については、各ディレクトリ配下の README を参照してください：
- [omoide-memory-downloader/README.md](file:///Users/kazuteru.sakaida/dev/omoide-memory/omoide-memory-downloader/README.md)
- [omoide-memory-sharing/README.md](file:///Users/kazuteru.sakaida/dev/omoide-memory/omoide-memory-sharing/README.md)

---

## 🔔 プッシュ通知 (Firebase Cloud Messaging) 設定手順

ダウンロード完了時に Android 端末へプッシュ通知を送信するためのセットアップ手順です。

### 1. Firebase プロジェクトのセットアップ & Android アプリ登録

1. [Firebase コンソール](https://console.firebase.google.com/) にアクセスし、プロジェクトを作成（または既存プロジェクトを選択）
2. 「Cloud Messaging」を有効化
3. Android アプリを追加:
   - **Android パッケージ名**: `com.kasakaid.omoidememory`
   - （ニックネーム等は任意、SHA-1 は省略可）
4. **`google-services.json`** をダウンロードし、以下のパスに配置:
   ```
   omoide-memory-uploader/app/google-services.json
   ```
   > ⚠️ `google-services.json` は機密情報を含むため、リポジトリにはコミットしないでください（`.gitignore` 設定済み）。

### 2. Google Cloud Service Account の権限設定

ダウンローダーが FCM HTTP v1 API 経由で通知を送信するため、使用する Service Account に FCM 送信権限を付与します:

1. [Google Cloud コンソール](https://console.cloud.google.com/) を開く
2. 対象プロジェクト → 「IAM と管理」→ 「IAM」
3. ダウンローダーで使用する Service Account を選択し、編集
4. ロールを追加: **「Firebase Cloud Messaging 管理者」** (`roles/firebase.admin` または `roles/cloudmessaging.admin`)
5. 保存

### 3. ダウンローダー側の環境変数設定

ダウンローダー（エントリーポイント: `DownloadFromGDrive.kt`）を実行する環境に以下の環境変数を設定します:

| 環境変数 | 説明 | 例 / 取得元 |
|---|---|---|
| `FCM_PROJECT_ID` | Firebase プロジェクト ID | Firebase コンソール → プロジェクト設定 → プロジェクト ID |
| `GOOGLE_SA_CREDENTIAL_PATH` | Service Account の JSON 鍵ファイルパス | Google Drive アクセス用の SA 鍵と共用可能 |

---

## 🛠️ 開発規約・コード品質 (ktlint)

本プロジェクトでは Kotlin コードの品質とフォーマットを統一するために **ktlint** を使用しています。

### なぜ ktlint を使うのか

- コードフォーマットの自動統一
- レビュー時の不要な差分（インデント・改行など）の削減
- CI でのスタイルチェック自動化
- pre-commit フックでの自動整形

`ktlint` は Kotlin 公式コーディング規約に準拠した軽量な Lint / Formatter ツールです。pre-commit 時に **変更された Kotlin ファイルのみ** を対象にフォーマットを実行しています。

### ktlint のインストール方法

#### macOS
```bash
brew install ktlint
```

#### Windows (Scoop 推奨)
```bash
scoop install ktlint
```

### 全プロジェクトの一括チェック

```bash
./scripts/ktlint-all.sh
```
