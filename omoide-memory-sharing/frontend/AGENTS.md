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
