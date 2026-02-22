

# インストールする必要があるもの

## podman
Podman を利用する利用

理由：

- Docker Desktop が不要（ライセンス問題なし）
- 軽量
- rootless 実行可能
- Docker Compose 互換（`podman compose`）

個人開発用途では Podman で十分です。

---

### Mac 環境
```
brew install podman
podman machine init
podman machine start
```

### Wiindows 環境
Windows 環境では **WSL2（Windows Subsystem for Linux）上でコンテナを動かす構成** します。
さらに、**WSL の保存先を C ドライブ以外（例：G ドライブ）へ移動することを強く推奨**します。

---

### なぜ C ドライブ以外に WSL を配置するのか？

WSL2 は Linux ファイルシステムを 1 つの仮想ディスク（`ext4.vhdx`）として保存します。
デフォルトでは以下に保存されます：
C:\Users<ユーザー名>\AppData\Local\Packages...

そのため：

- コンテナの DB データ
- ボリューム
- イメージ
- Linux ホームディレクトリ

すべてが **C ドライブ（SSD）を消費します**。

本アプリは個人用途を想定しており、

- DB は大きくなる可能性がある
- パフォーマンスよりも容量確保を優先したい

という前提から、

> ✅ WSL 自体を G ドライブなど容量の大きいディスクに配置することを推奨します。

---

## セットアップ手順

### ① WSL をインストール

PowerShell（管理者）で：

```powershell
wsl --install
````

再起動後、Ubuntu などを初期セットアップします。

---

### ② 現在の WSL 名を確認

```powershell
wsl -l -v
```

例：

```
NAME      STATE    VERSION
Ubuntu    Running  2
```

---

### ③ WSL を G ドライブへ移動する

#### 1. エクスポート

```powershell
wsl --export Ubuntu G:\wsl-backup\ubuntu.tar
```

#### 2. 保存先ディレクトリ作成

```powershell
mkdir G:\wsl\ubuntu
```

#### 3. G ドライブへインポート

```powershell
wsl --import Ubuntu-G G:\wsl\ubuntu G:\wsl-backup\ubuntu.tar --version 2
```

#### 4. デフォルトに設定

```powershell
wsl --set-default Ubuntu-G
```

#### 5. 動作確認

```powershell
wsl
```

正常に起動すれば成功です。

#### 6. 古い WSL を削除
確認後古い DSL 削除を実施

```powershell
wsl --unregister Ubuntu
```

これで C ドライブの仮想ディスクが削除されます。

---

### ④ WSL 内で Podman をインストール

WSL に入り：

```bash
sudo apt update
sudo apt install -y podman
```

確認：

```bash
podman --version
```

---

### ⑤ コンテナ DB を起動

プロジェクトルートで：

```bash
podman compose up -d
```

これにより：

* PostgreSQL コンテナ
* DB データ
* コンテナイメージ

すべてが **G ドライブ上の WSL 領域に保存されます**。

---

### この構成のメリット

| 項目               | 効果               |
| ---------------- | ---------------- |
| C ドライブ容量         | 消費しない            |
| Linux ネイティブ ext4 | 維持               |
| DB パフォーマンス       | Windows マウントより高速 |
| 運用の安定性           | 高い               |

---

### 注意事項

* G ドライブが外付けの場合、取り外すと WSL が起動できなくなります。
* インポート後は必ず動作確認を行ってから旧ディストリを削除してください。

---


## ktlint

本プロジェクトでは Kotlin コードの品質とフォーマットを統一するために**ktlint** を使用しています。

---

## なぜ ktlint を使うのか

本プロジェクトでは以下を目的としています：

- コードフォーマットの自動統一
- レビュー時の不要な差分（インデント・改行など）の削減
- CI でのスタイルチェック自動化
- pre-commit フックでの自動整形

`ktlint` は Kotlin 公式コーディング規約に準拠した軽量な Lint / Formatter ツールです。

pre-commit 時に **変更された Kotlin ファイルのみ** を対象にフォーマットを実行しています。

---

## ktlint のインストール方法

### macOS

Homebrew を利用してください：

```bash
brew install ktlint
```

### Windows
#### Scoop を利用する場合（推奨）
```
scoop install ktlint
```

## gradle の ktlint
プロジェクト全体にかかり、lint-staged で実行すると stage 以外のファイルも lint されます。
ktlint は lint-staged で渡されたファイル名を引数に lint してくれるので、pre-commit 経由では ktlint の cli を利用しています。

### scripts/ktlint-all.sh
gradle を実行して全プロジェクトの ktlint を実行します。
