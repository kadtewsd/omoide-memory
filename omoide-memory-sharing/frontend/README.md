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

---

## 🌐 LAN 公開用のファイアウォール設定 (Windows)

LAN 内の他端末からアクセスする場合、Windows ファイアウォールでフロントエンドのポートを開放する必要があります。
[allow-frontend-firewall-port.ps1](file:///Users/kazuteru.sakaida/dev/omoide-memory/omoide-memory-sharing/frontend/allow-frontend-firewall-port.ps1) を管理者権限の PowerShell で実行してください。

```powershell
# デフォルトポート (5173) の開放
.\allow-frontend-firewall-port.ps1

# ポート番号を指定して開放する場合 (例: 3000)
.\allow-frontend-firewall-port.ps1 -Port 3000
```

