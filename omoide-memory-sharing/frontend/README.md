# omoide-memory-sharing Frontend

React 19 + TypeScript + Vite + Tailwind CSS によるフロントエンド Web アプリケーションです。

---

## Repository Structure

```
frontend/
├── src/
│   ├── api/             # API クライアント関数
│   ├── components/      # React コンポーネント (MemoryModal, VideoPlayer, FeedGrid 等)
│   └── types/           # 型定義
├── package.json
├── vite.config.ts
└── README.md
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Library | React 19.2 |
| Language | TypeScript |
| Build Tool | Vite |
| Styling | Tailwind CSS |

---

## Features

- **Google Photo 風 UI**: グリッド表示・動画プレイヤー・モーダルスレッド表示
- **動画ストリーミング**: `VideoPlayer` コンポーネントによる再生
- **無限スクロール**: フィード閲覧時の追加読み込み

---

## Getting Started (Development)

### パッケージインストール

```bash
cd frontend
npm install
```

### 開発サーバー起動

```bash
npm run dev
```

### 型チェック & ビルド

```bash
npm run build
```
