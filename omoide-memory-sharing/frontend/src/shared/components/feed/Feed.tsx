import { ReactNode } from 'react';
import { MemoryFeedItem } from '@/shared/types';
import { FeedContentContainer } from './FeedContentContainer';

export interface FeedProps {
    items: MemoryFeedItem[];
    hasNext: boolean;
    loadingInitial: boolean;
    loadingMore: boolean;
    loadMore: () => Promise<void> | void;
    emptyMessage?: string;
    children: ReactNode;
}

/**
 * 共通フィードコンテンツ表示コンポーネント。
 * ヘッダーや日付選択などの画面制御は呼び出し元（Page）が担い、
 * 本コンポーネントは純粋に実行結果（一覧アイテム、ローディング、無限スクロール、空状態）の描画に専念する。
 */
export function Feed({
    items,
    hasNext,
    loadingInitial,
    loadingMore,
    loadMore,
    emptyMessage,
    children,
}: FeedProps) {
    return (
        <FeedContentContainer
            loadingInitial={loadingInitial}
            hasItems={(items?.length ?? 0) > 0}
            hasNext={hasNext}
            loadingMore={loadingMore}
            loadMore={loadMore}
            emptyMessage={emptyMessage}
        >
            {children}
        </FeedContentContainer>
    );
}
