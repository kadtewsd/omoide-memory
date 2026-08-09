# AGENTS.md - omoide-memory 開発ガイドライン (全体共通)

本ドキュメントは `omoide-memory` リポジトリ全体の共通ガイドラインです。
サブプロジェクトごとに固有のルールが定義されています。

---

## ディレクトリ別ルール参照ガイド

作業するプロジェクト/ディレクトリに応じて、以下の `AGENTS.md` が適用されます。

| ディレクトリ | 適用される規約 | 主な対象技術 |
|---|---|---|
| `backend/`, `omoide-memory-sharing/backend/` | `backend/AGENTS.md` | Spring Boot, jOOQ, R2DBC, WebFlux, Coroutines, Arrow-kt |
| `frontend/`, `omoide-memory-sharing/frontend/` | `frontend/AGENTS.md` | React 19, TypeScript, React Hooks, Zod |
| モバイルアプリ（Android） | モバイル用 `AGENTS.md` | Jetpack Compose, Dagger Hilt, Room |

---

## リポジトリ全体共通の設計原則

1. **DRY原則と高凝集・低結合の徹底**
   - 制御結合（Control Coupling: フラグ変数等による内部分岐）を避け、単一責任のメソッド・コンポーネントを設計すること。
2. **KDoc / JSDoc による背景・意図の明記**
   - マジックナンバーや非直感的なロジック（バイト数計算、特殊な閾値など）は名前付き定数化し、コードの「背景・なぜそうしているか」をドキュメントコメントに明記すること。
3. **コミット / PR 作成ルール**
   - 関連ドキュメントやPR説明文は必要に応じて `ai_script/` 配下に保存すること。
