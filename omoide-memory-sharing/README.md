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
npx serve -s C:\app\frontend -l 5173
```

#### 🌐 LAN 内公開時の Windows ファイアウォール許可設定

LAN 内の他デバイス（スマホや PC 等）からアクセスできるようにするには、管理者権限の PowerShell で以下を実行し、ポートを開放します。

```powershell
# デフォルトポート (5173) の場合
cd frontend
.\allow-frontend-firewall-port.ps1

# 3000 ポート等に変更して公開する場合
.\allow-frontend-firewall-port.ps1 -Port 3000
```

---

## 🛡️ 技術的な安全性について

本アプリケーションを LAN 内で公開・ポート開放する際の安全性（セキュリティ設計）について説明します。

### 1. インターネット経由の攻撃に対する境界防御（ルーター保護）
- 一般的な家庭用ネットワークでは、ルーターの **NAT（ネットワークアドレス変換）/ ファイアウォール機能** により、外部（インターネット）からの直接の通信要求はすべて遮断されています。
- ルーターのポート転送（Port Forwarding）や DMZ 設定を明示的に行わない限り、**インターネット上の第三者が本アプリのポート（5173 や 8080）へアクセス・アタックすることは物理的に不可能です**。

### 2. LAN 内アクセスの範囲と物理的セキュリティ
- Windows ファイアウォールでポートを開放した場合、アクセス可能な範囲は **「同じ自宅 Wi-Fi / ルーターに接続している端末」** 限定となります。
- したがって、接続できるのは **「自宅ルーターの Wi-Fi パスワード（暗号化キー）を知っていて、実際に接続を許可された家族・端末のみ」** という境界線（仕切り）で保護されます。
- 第三者がアクセスするにはルーター本体に記載された Wi-Fi パスワードを知る必要があるため、自宅内運用において非常に高い安全性を維持できます。

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
