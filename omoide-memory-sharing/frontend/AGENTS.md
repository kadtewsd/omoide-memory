# AGENTS.md - Web フロントエンド (React / TypeScript) 開発ガイドライン

本ドキュメントは、ブラウザアプリ（Web フロントエンド）開発におけるコーディング規約、アーキテクチャ、技術選定の指針を定めたものです。

---

## 技術スタック

| 技術 | 用途 |
|---|---|
| **TypeScript** | 型安全な開発 |
| **React 19.2** | UIライブラリ |
| **React Hooks** | 状態管理・副作用 |
| **Zod** | スキーマバリデーション |
| **Vanilla CSS** | スタイリング |

---

## コンポーネント設計原則

### 1. 単一責任とコンポーネント分割

❌ **悪い例（大きな単一コンポーネント）**:

```tsx
// UserPage.tsx（500行）
export const UserPage = () => {
    // ヘッダー、サイドバー、メインコンテンツ、フッターが全部入っている
    return <div>...</div>
}
```

✅ **良い例（小さなコンポーネントの組み合わせ）**:

```tsx
// UserPage.tsx
export const UserPage = () => {
    return (
        <>
            <Header />
            <Sidebar />
            <UserContent />
            <Footer />
        </>
    )
}

// UserContent.tsx
export const UserContent = () => {
    return (
        <div>
            <UserProfile />
            <UserActivity />
        </div>
    )
}
```

**ルール**:
- 大きなコンポーネントを一つ作るのではなく、小さな単一責任のコンポーネントに分割する
- 各コンポーネントは再利用可能な粒度で設計する

---

### 2. デザイン & レスポンシブ設計

- **モダン & プレミアムデザイン**:
  - HSL tailored やダークモードを活用したカラーパレット
  - Inter, Roboto などのモダンタイポグラフィ
  - 微細なアニメーションやスムーズなトランジション
- **アクセシビリティ & レスポンシブ**:
  - モバイル・デスクトップの両方で快適に操作できるタッチターゲットサイズと柔軟なレイアウト

---

### 3. TypeScript 型明示ルール

- **関数の引数はオブジェクト型（`interface` / `type`）で定義し、プロパティ名を明示して渡すこと。**
  - 引数が 2 つ以上ある場合は、位置引数ではなくオブジェクト引数（Named Object Parameters）を採用する。
  - `types/index.ts` など共通の型ファイルに `FetchXxxParams` のような専用のパラメータ型を定義し、API 関数・カスタムフックで使い回すこと。

  ❌ **悪い例（位置引数で何を渡しているか分からない）**:
  ```ts
  fetchRandomFillPhotos(startInclusive, endExclusive, currentExcludeIds, remaining);
  ```

  ✅ **良い例（オブジェクト引数で意図が明確）**:
  ```ts
  fetchRandomFillPhotos({
      startInclusive,
      endExclusive,
      excludeIds: currentExcludeIds,
      count: remaining,
  });
  ```

- **関数の戻り値型も明示すること。**
  - `Promise<T>` の型パラメータを省略しない。
  - カスタムフックの戻り値はオブジェクト型として `interface` で定義することが望ましい。

- **`any` の使用を禁止する。** 型が不明な場合は `unknown` を使い、型ガードで絞り込むこと。

---

### 4. フラグ管理の排除と状態モデリング（State 構造体 / クラス）

- **Boolean フラグ（`isOpen`, `isProcessing`, `isSelectMode` 等）の乱立や、`null` による暗黙の状態管理を徹底して嫌うこと。**
  - 複数のフラグを組み合わせると `!isProcessing && isModalOpen` のような認知負荷の高い否定・複合判定が生じ、あり得ない不正な状態（例: 処理中なのにモーダルが開くなど）を許してしまいます。
- **コンポーネントや画面の状態は「state」として構造体・クラスで定義・モデリングすること。**

---

### 5. 文字列での状態比較の禁止（interface / class を定義し instanceof で比較せよ）

- **【厳格遵守・絶対禁止】文字列での状態比較（`state.type === 'SELECT'` や `status === 'PROCESSING'` 等）を完全禁止。**
  - **文字列はコンパイルエラーにならないのでリファクタ時に静かに壊れる。** タイポやステータス名の変更時に検知できず、実行時に不具合を引き起こす最大の原因となります。
- **状態やモードを表現する場合は interface をきり、それを実装した class を定義して `instanceof` で比較すること。**

❌ **悪い例（文字列リテラルで状態を比較 / リファクタ時に静かに壊れる）**:
```tsx
if (state.type === 'SELECTING') {
    return <SelectionView ... />;
}
if (state.status === 'PROCESSING') {
    return <ProcessingView ... />;
}
```

✅ **良い例（interface/class を定義し instanceof で型安全に比較）**:
```tsx
export interface PhotobookState {}

export class SelectingState implements PhotobookState {}
export class PreviewingState implements PhotobookState {}
export class CreatingState implements PhotobookState {
    constructor(readonly message: string) {}
}

// 判定時は instanceof で型安全に比較
if (state instanceof SelectingState) {
    return <SelectionView ... />;
}
if (state instanceof CreatingState) {
    return <div>{state.message}</div>;
}
```

---

### 6. 過剰な状態細分化の禁止（無駄に状態を増やさず「なにかに集約」してシンプルに保つ）

- **状態クラス・型の過剰な細分化を禁止する。**
  - 「モーダルを開いている」「メッセージを出している」「ステップが進んだ」といった些細な変化ごとに、あえて細かく別個の State クラス（状態型）を乱立させてはならない。
- **メッセージや付随情報に集約して状態数を最小限に抑えること。**
  - 本質的に同一の関心を持つフェーズは 1 つの状態（例: `CreatingState`）に集約し、進捗文言や付随データはプロパティ（`message: string`）に持たせることで状態の数を極限まで減らし、シンプルさを保つこと。
  - （例: 「写真選択中（`SelectingState`）」と「アルバム作成中（`CreatingState(message = ...)`）」の 2 状態で集約・表現する）。

---

### 7. なんらかの判断・状態判定をフラグ変数へ言い換えることの絶対禁止（直接判定式で表現せよ）

- **【厳格遵守・絶対禁止】あらゆる判断をローカルの Boolean フラグ変数へ言い換えることを禁止。**
  - `instanceof` による状態判定に限らず、件数判定（`selectedPhotos.length === 0`）、ヌル判定、真偽条件など、**なんらかの判断をローカル変数でフラグ（`const isProcessing = ...`, `const hasItems = ...` 等）に言い換えて保持することを徹底して禁止する**。
- **フラグで言い換えることは、冗長に何度も短い判断を直接書くことよりも更に悪である。**
  - フラグ変数を作成すると、そのフラグがコンポーネント内やスコープ内で一人歩きし、本来排他的に表現されているはずの状態モデリングや最新のデータ状態が崩壊し、再びフラグベースの曖昧な制御・状態爆発に逆戻りする。
- 判断・判定が必要な箇所では妥協せず、直接その場に判定式（`state instanceof CreatingState`, `selectedPhotos.length === 0` 等）を記述すること。

