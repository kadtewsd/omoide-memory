import { FeedLoadingSpinner } from './FeedLoadingSpinner';
import { FeedEmptyView } from './FeedEmptyView';
import { InfiniteScrollLoader } from '@/shared/components/InfiniteScrollLoader';

export interface FeedContentContainerProps {
    loadingInitial: boolean;
    hasItems: boolean;
    hasNext: boolean;
    loadingMore: boolean;
    loadMore: () => void;
    emptyMessage?: string;
    children: React.ReactNode;
}

export function FeedContentContainer({
    loadingInitial,
    hasItems,
    hasNext,
    loadingMore,
    loadMore,
    emptyMessage,
    children,
}: FeedContentContainerProps) {
    return (
        <main className="p-4 sm:p-6 lg:p-8">
            {loadingInitial ? (
                <FeedLoadingSpinner />
            ) : hasItems ? (
                <>
                    {children}
                    <InfiniteScrollLoader
                        onLoadMore={loadMore}
                        hasMore={hasNext}
                        loading={loadingMore}
                    />
                </>
            ) : (
                <FeedEmptyView message={emptyMessage} />
            )}
        </main>
    );
}
