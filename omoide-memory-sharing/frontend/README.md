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
├── .env.example         # 環境変数テンプレート (コミット対象)
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
- **フォトブック用写真選択**: 期間指定（From 〜 To カレンダー）および目標枚数に基づく写真選択・ランダム補完・ZIP ダウンロード

---

## Getting Started

### 1. パッケージインストール

```bash
cd omoide-memory-sharing/frontend
npm install
```

### 2. 環境変数ファイル（`.env`）の作成

環境変数は `.env` で管理します（`.env` は Git コミット対象外です）。
`.env.example` をコピーして `.env` を作成し、必要に応じて環境に合わせて調整してください。

```bash
cp .env.example .env
```

#### 設定項目

| 環境変数名 | 説明 | 設定例 |
|---|---|---|
| `VITE_API_URL` | バックエンド API のオリジン URL | `http://<バックエンドPCのIPまたはホスト名>:8080` |
| `VITE_ALLOWED_HOSTS` | Vite サーバーでアクセスを許可するホスト名（カンマ区切り） | `localhost,127.0.0.1,.local,<アクセスを許可するIPやホスト名>` |

#### `.env` の作成例

##### ① ローカル PC 単体で開発する場合（デフォルト）
```env
VITE_API_URL=http://localhost:8080
VITE_ALLOWED_HOSTS=localhost,127.0.0.1,.local
```

##### ② LAN 内の他端末（スマートフォンや別 PC）からアクセスする場合

**パターン A: IP アドレス直接指定（最も確実・推奨）**
```env
# バックエンド (Spring Boot: 8080) が稼働している PC の IP アドレスを指定
VITE_API_URL=http://<このPCのIPアドレス>:8080

# Vite (フロントエンド: 5173) へのアクセスを許可するホスト名や IP アドレスを指定
VITE_ALLOWED_HOSTS=localhost,127.0.0.1,.local,<このPCのIPアドレス>
```

**パターン B: ホスト名（mDNS）指定**
```env
# mDNS が有効な家庭内 Wi-Fi 環境であれば、<ホスト名>.local 形式での指定も可能です
VITE_API_URL=http://<このPCのホスト名>.local:8080

# Vite (フロントエンド: 5173) へのアクセスを許可するホスト名を指定
VITE_ALLOWED_HOSTS=localhost,127.0.0.1,.local,<このPCのホスト名>.local
```

> [!TIP]
> - `<このPCのIPアドレス>` は、ターミナルで `ipconfig` (Windows) または `ifconfig` (macOS) を実行して確認できます（例: `192.168.1.50`）。
> - `<このPCのホスト名>` は、ターミナルで `hostname` を実行して確認できます（例: `mmadminnomacbook-pro`）。
>   ※ macOS の `hostname` コマンドは最初から末尾に `.local` が含まれる場合があるため、重複して `<ホスト名>.local.local` にならないようご注意ください。

### 3. 実行コマンド

#### 開発サーバー起動
```bash
npm run dev
```

#### 本番ビルド & プレビュー実行（環境変数を読み込んで実行）
```bash
npm run prod
```
※ `npm run build` でビルドした成果物を `vite preview` で起動します。

#### 型チェック & ビルドのみ
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
