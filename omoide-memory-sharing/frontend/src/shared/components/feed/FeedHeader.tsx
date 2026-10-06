import { ReactNode } from 'react';

export interface FeedHeaderProps {
    children: ReactNode;
    className?: string;
}

/**
 * フィード・アルバム画面の共通スティッキーヘッダー枠組み。
 * 内部のタイトル、戻るボタン、月タブ、コントロールバー、各種アクション等の配置は
 * 各ページ（呼び出し元）が自由に直接レンダリングする。
 */
export function FeedHeader({ children, className }: FeedHeaderProps) {
    return (
        <header
            className={`sticky top-[69px] z-20 bg-white/95 backdrop-blur-md border-b border-gray-200 px-4 sm:px-6 py-2.5 space-y-2.5 shadow-xs${className ? ` ${className}` : ''}`}
        >
            {children}
        </header>
    );
}
