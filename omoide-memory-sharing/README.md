# omoide-memory-sharing

LAN 内で写真・動画へのコメントを閲覧するための、閲覧専用共有アプリケーションです。

---

## 🗂️ リポジトリ構成

- **`backend/`**: Spring Boot 3.x (WebFlux) + jOOQ によるバックエンド開発（詳細は [backend/README.md](file:///Users/kazuteru.sakaida/dev/omoide-memory/omoide-memory-sharing/backend/README.md) を参照）
- **`frontend/`**: React 19 + TypeScript + Vite によるフロントエンド開発（詳細は [frontend/README.md](file:///Users/kazuteru.sakaida/dev/omoide-memory/omoide-memory-sharing/frontend/README.md) を参照）

---

## 💻 Windows 上でのビルド・配置・起動手順

Windows 上で PowerShell スクリプト（`.ps1`）を使用して成果物をビルドし、指定ディレクトリへ自動配置・起動する方法です。

### 1. バックエンドのビルドと配置

`build-backend.ps1` を使用して JAR ファイルのビルドおよび指定パスへの配置を行います。

```powershell
# バックエンドをビルドし、C:\app\backend へ配置する例
.\build-backend.ps1 -DestinationPath "C:\app\backend"
```

#### 起動コマンド (Windows PowerShell / Command Prompt)

```powershell
java -jar C:\app\backend\backend.jar
```

---

### 2. フロントエンドのビルドと配置

`build-frontend.ps1` を使用して React 成果物（`dist`）のビルドおよび指定パスへの配置を行います。

```powershell
# フロントエンドをビルドし、C:\app\frontend へ配置する例
.\build-frontend.ps1 -DestinationPath "C:\app\frontend"
```

#### 起動・静的ファイル配信の例 (npx serve 等)

```powershell
npx serve -s C:\app\frontend -l 3000
```

---

## 📸 photo_comment_collector.js の使い方

画面上で Google Photos 上でコメントをまとめてコピーし、スプレッドシートへ貼り付けるための補助スクリプトです。

### 🎯 目的
Google Photos のコメントは API で直接取得できないため、以下の手順で効率的に収集します：

1. コメントをドラッグ選択
2. `Q` キーを押す
3. クリップボードへコピー
4. スプレッドシートへ貼り付け

---

## 📊 スプレッドシート出力仕様

以下の列順（タブ区切り TSV 形式）で貼り付け可能です：

```
コンテンツ	コメント本文	投稿者・日付
```

### 🚀 使い方

1. ブラウザで Google Photos のコメント画面を開く
2. デベロッパーツール（`Ctrl + Shift + I`）の Console タブを開く
3. [photo_comment_collector.js](file:///Users/kazuteru.sakaida/dev/omoide-memory/omoide-memory-sharing/photo_comment_collector.js) の内容をコピーして貼り付け、Enter
