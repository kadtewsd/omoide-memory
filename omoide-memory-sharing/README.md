# omoide-memory-sharing

LAN 内で写真・動画へのコメントを閲覧するための、超ミニマムな共有アプリです。
「誰が何を言ったか」を Google Photo ライクな UI で閲覧することに特化しており、更新系機能は一切持たない **閲覧専用** アプリです。

---

## Repository Structure

```
omoide-memory-sharing/
├── backend/          # Spring Boot 3.x (WebFlux) + jOOQ
├── frontend/         # React + Tailwind CSS
└── README.md
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.x (WebFlux), jOOQ |
| jOOQ DSL | `:omoide-memory-jooq`（生成済み DSL を利用） |
| Frontend | React, Tailwind CSS |

---

## Features

- **フィード一覧**（`GET /api/feed?cursor=...`）
  コメント付きの写真・動画をキャプチャ日時の降順で混合表示。カーソルベースのページングに対応。

- **コメントスレッド**（`GET /api/content/{type}/{id}/comments`）
  特定のコンテンツに紐づく全コメントをスレッド形式で表示。`commented_at` 昇順。

- **Google Photo 風 UI**
  グリッドレイアウト、無限スクロール（または「もっと見る」）、コメント件数バッジ、サイドバー/モーダルでのスレッド表示。

---

## Database

### 対象テーブル

| 種別 | コメントテーブル | コンテンツテーブル |
|---|---|---|
| Photo | `comment_omoide_photo` (V1.3) | `synced_omoide_photo` |
| Video | `comment_omoide_video` (V1.5) | `synced_omoide_video` (V1.4) |

### クエリ概要

- **フィード**: `comment_omoide_***` INNER JOIN `synced_omoide_***`、コンテンツ ID で集約、`capture_time DESC` 順
- **スレッド**: `omoide_photo_id` / `omoide_video_id` で絞り込み、`commented_at ASC` 順、`comment_seq` で整合性維持

---

## API

```
GET /api/feed?cursor={cursor}
GET /api/content/{type}/{id}/comments
```

- Video サムネイル（`thumbnail_image`）は Base64 文字列にエンコードして返却
- Photo はファイルパスをそのまま返却（フロントエンドが配信 URL を組み立て）

---

## Out of Scope

- 認証・ログイン（Auth/Login）
- 作成・更新・削除操作（Create / Update / Delete）
- 複雑なフィルタリング

---

## Getting Started

### Backend

```bash
cd backend
./gradlew bootRun
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## 📸 photo_comment_collector.js の使い方
画面上で Google Photos 上でコメントをまとめてコピーし、スプレッドシートへ貼り付けるための補助スクリプトです。

### 🎯 目的
Google の Photo のコメントは API などで取得できません。
このため、下記の方法で、使用します。

- コメントをドラッグ選択
- `Q` キーを押す
- 即クリップボードへコピー
- スプレッドシートへ貼り付け

を高速に繰り返すためのツールです。

---


## 📊 スプレッドシート出力仕様

本スクリプトは、以下の列順でスプレッドシートへ貼り付けることを前提としています。

```
コンテンツ	コメント本文	投稿者・日付
```

### 🧾 出力形式

- **タブ区切り（TSV形式）**
- そのままスプレッドシートへ一発貼り付け可能
- 列の自動分割を前提としています

---

## 📌 各カラムの仕様

### 1️⃣ コンテンツ

- Google Photos 上では直接コピーできないため、
- スクリプト側で取得・付与します
- 対象の写真／投稿を識別するための内部名称です

---

### 2️⃣ コメント本文

- ユーザーがドラッグ選択したコメント本文
- 不要な UI 文言（Reply / Save / Seen by など）は自動除外

---

### 3️⃣ 投稿者・日付

- 「投稿者名 + 区切り記号 + 日付」形式
- 日付に **年の記載がない場合は当年として解釈可能**
  - 例: `May 12` → `2026 May 12`
- 年付きの場合はそのまま使用

---

## 🔎 例

```
IMG_1234	素敵な写真ですね！	山田太郎 · May 12
```

---

## 🚀 使い方

### ① Google Photos を開く

ブラウザで Google Photos を開き、コメント表示画面を表示します。

### ② デベロッパーツールを開く

- Mac: `Cmd + Option + I`
- Windows: `Ctrl + Shift + I`

「Console」タブを開きます。

### ③ スクリプトを貼り付けて実行

`photo_comment_collector.js` の中身をすべてコピーし、Console に貼り付けて Enter。

以下のメッセージが表示されれば準備完了です：


