# omoide-memory-sharing

家庭内 LAN 内で写真・動画やコメントを閲覧し、フォトブック（外部サービス向け）用の写真を選択・ZIP ダウンロードできる共有アプリケーションです。

---

## 📱 主な機能

- **フォトブック用 写真選択**:
  - 年月ごとの写真一覧グリッド表示（動画除外）
  - 目標枚数 N（上限200枚）の設定と手動タップ選択
  - 「あと (N - M) 枚はランダムで補完する」自動補完
  - 選択プレビュー画面での写真の差し替え・削除
  - 最終選定リストの ZIP ダウンロード
- **写真・動画・コメント閲覧**:
  - 年月・コメント投稿日ごとのメディアフィード表示
  - 高速サムネイル表示および動画ストリーミング再生
  - 写真・動画に寄せられた家族のコメント一覧閲覧
- **アルバム作成・ダウンロード**:
  - お気に入りの写真を選択してカスタムアルバムを作成・一括ダウンロード

---

## 🚀 LAN 公開クイックスタート (Windows)

`omoide-memory-sharing` ディレクトリをカレントディレクトリとして PowerShell を開き、以下を実行します。
ファイアウォール開放・Backend/Frontend のビルドから両サービスの同時起動まで一括実行されます。

> **前提条件**:
> - Windows 10 / 11
> - Java 21 以上
> - Node.js v20 以上
> - PostgreSQL（`omoide_memory` データベース）が起動していること

### 1. ワンストップ実行（推奨）

管理者権限で PowerShell を開き、`omoide-memory-sharing` ディレクトリで以下を実行します：

```powershell
# ポート開放 -> Backend/Frontend ビルド -> 両サービス同時起動
.\deploy-and-run-lan.ps1
```

実行後、Backend（ポート 8080）と Frontend Preview（ポート 5173）が**同一ウィンドウ**でログをストリーミング表示しながら起動します。
スマートフォンからアクセスするための URL もコンソールに表示されます。
**Ctrl+C** で Backend・Frontend の両方を同時に停止できます。


### 2. 日常の起動（ビルド済みの場合）

ビルド済みの状態から即座にサービスを再開する場合は、通常の PowerShell で以下を実行します：

```powershell
.\start-lan.ps1
```

---

## 📲 スマートフォン・他端末からのアクセス方法

同じ家庭内 Wi-Fi ルーターに接続したスマートフォン（iPhone / Android）やタブレットのブラウザ（Safari, Chrome 等）を開き、以下のアドレスを入力します：

```
http://<ホストPC名>.local:5173
```

- **例**: PC のコンピュータ名が `DESKTOP-PHOTO` の場合:
  `http://desktop-photo.local:5173`
- **IP アドレスによるアクセス**:
  ルーターの環境により mDNS 名で接続できない場合は、スクリプト実行時に表示されたローカル IP アドレス（例: `http://192.168.1.15:5173`）を入力してください。

> [!NOTE]
> **HTTPS / 証明書が不要な理由**
> 本システムは家庭内 LAN（ルーターの内側）で完結しており、ルーターの NAT / ファイアウォールによって外部インターネットからの通信は物理的に遮断されています。第三者からの盗聴・侵入リスクがない閉域ネットワークのため、自己署名証明書の警告等を避けて平文 HTTP で安全・快適に利用できます。

---

## ⚙️ スクリプトオプション詳細

`deploy-and-run-lan.ps1` は以下のパラメータを指定可能です：

| パラメータ | 型 | デフォルト | 説明 |
|---|---|---|---|
| `-Mode` | `Production` \| `Dev` | `Production` | `Production`: JAR パッケージング & Vite preview<br>`Dev`: bootRun & Vite dev |
| `-FrontendPort` | int | `5173` | フロントエンドの公開ポート |
| `-SkipFirewall`| switch | なし | Windows ファイアウォール設定をスキップ |
| `-NoLaunch` | switch | なし | ビルドのみ行い、自動起動しない |

#### 開発モード（ホットリロード有効）での実行例:
```powershell
.\deploy-and-run-lan.ps1 -Mode Dev
```


---

## 🛠️ 手動ビルド・個別実行手順

個別にコンポーネントを制御したい場合は、以下の単体スクリプトをご利用ください。

### バックエンド個別ビルド
```powershell
.\build-backend.ps1 -DestinationPath "C:\app\backend"
java -jar C:\app\backend\backend.jar
```

### フロントエンド個別ビルド
```powershell
.\build-frontend.ps1 -DestinationPath "C:\app\frontend"
cd frontend
npm run preview -- --host 0.0.0.0 --port 5173
```

### ファイアウォールポート個別開放
```powershell
cd frontend
.\allow-frontend-firewall-port.ps1 -Port 5173
```

---

## 🛡️ セキュリティと境界防御について

1. **家庭内 Wi-Fi による境界防御**:
   接続できるのは、自宅ルーターの Wi-Fi 暗号化パスワードを知っている端末のみです。
2. **ルーター NAT による外部攻撃遮断**:
   ルーターのポート転送（Port Forwarding）を行わない限り、インターネット上の第三者が本システムへ到達することは不可能です。
3. **Vite リバースプロキシによるポート集約**:
   フロントエンド（ポート 5173）がバックエンド API を内部プロキシ転送するため、外部に公開するポートは 5173 のみとなり、CORS のリスクも構造的に排除しています。

---

## ❓ トラブルシューティング

| 症状 | 原因 | 解決策 |
|---|---|---|
| スマホから `<PC名>.local:5173` で開けない | Wi-Fi ルーターのプライバシーセパレーター機能が有効になっている | ルーター管理画面で「プライバシーセパレーター（端末間通信の分離）」を無効にするか、IP アドレス直接（`http://192.168.x.x:5173`）でアクセスしてください。 |
| 接続タイムアウトになる | Windows ファイアウォールでブロックされている | 管理者権限の PowerShell で `cd frontend; .\allow-frontend-firewall-port.ps1` を再実行してください。また、Windows のネットワーク接続が「プライベート」に設定されているか確認してください。 |
| 写真一覧やコメントが読み込まれない | バックエンドまたは DB が起動していない | バックエンドのコンソールウィンドウにエラーが出ていないか確認し、PostgreSQL サービスが動作しているか確認してください。 |
| フォトブックの「ZIP ダウンロード」が始まらない | 写真の枚数が多く処理に時間がかかっている | 画面上にダウンロード通知が出るまで数秒お待ちください。スマートフォンのブラウザにダウンロード許可ダイアログが表示されていないか確認してください。 |

---

## 📸 補助ツール: photo_comment_collector.js

Google Photos 上のコメントを効率的に収集するためのブラウザコンソール用スクリプトです。
詳細は [photo_comment_collector.js](file:///Users/kazuteru.sakaida/dev/omoide-memory/omoide-memory-sharing/photo_comment_collector.js) を参照してください。
