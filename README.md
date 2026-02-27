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
