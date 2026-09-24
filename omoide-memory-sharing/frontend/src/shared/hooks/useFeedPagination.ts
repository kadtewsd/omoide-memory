import { useState, useCallback, useEffect } from 'react';
import { fetchFeed } from '@/shared/api';
import { ContentType, FeedCursor, FeedPageResponse, FilterMode, MemoryFeedItem } from '@/shared/types';
import { isValidIsoDate } from '@/shared/date';

export interface UseFeedPaginationParams {
    startInclusive?: string;
    endExclusive?: string;
    mode?: FilterMode;
    contentType?: ContentType;
    limit?: number;
}

export interface UseFeedPaginationResult {
    items: MemoryFeedItem[];
    hasNext: boolean;
    loadingInitial: boolean;
    loadingMore: boolean;
    loadMore: () => Promise<void>;
    refresh: () => Promise<void>;
}

/**
 * Keyset ページネーション（カーソルベース）によるフィード取得を管理する共通カスタムフック。
 * 初回ロードおよびスクロールによる追加ロード（loadMore）のステート管理と API フェッチを一元化する。
 */
export function useFeedPagination({
    startInclusive,
    endExclusive,
    mode,
    contentType,
    limit = 25,
}: UseFeedPaginationParams): UseFeedPaginationResult {
    const [items, setItems] = useState<MemoryFeedItem[]>([]);
    const [nextCursor, setNextCursor] = useState<FeedCursor | null>(null);
    const [hasNext, setHasNext] = useState(false);
    const [loadingInitial, setLoadingInitial] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);

    const executeFetch = useCallback(
        async (cursor?: FeedCursor | null): Promise<FeedPageResponse | null> => {
            if (!startInclusive || !endExclusive) return null;
            if (!isValidIsoDate(startInclusive) || !isValidIsoDate(endExclusive)) return null;

            return fetchFeed({
                startInclusive,
                endExclusive,
                mode,
                contentType,
                cursorCaptureTime: cursor?.captureTime,
                cursorId: cursor?.id,
                limit,
            });
        },
        [startInclusive, endExclusive, mode, contentType, limit]
    );

    const loadInitial = useCallback(async () => {
        if (!startInclusive || !endExclusive || !isValidIsoDate(startInclusive) || !isValidIsoDate(endExclusive)) {
            setItems([]);
            setNextCursor(null);
            setHasNext(false);
            setLoadingInitial(false);
            return;
        }

        setLoadingInitial(true);
        setItems([]);
        setNextCursor(null);
        setHasNext(false);
        try {
            const res = await executeFetch(null);
            const feedItems = Array.isArray(res) ? res : (res?.items ?? []);
            setItems(feedItems);
            setNextCursor(Array.isArray(res) ? null : (res?.nextCursor ?? null));
            setHasNext(Array.isArray(res) ? false : (res?.hasNext ?? false));
        } catch (err) {
            console.error('フィードの取得に失敗しました:', err);
            setItems([]);
        } finally {
            setLoadingInitial(false);
        }
    }, [startInclusive, endExclusive, executeFetch]);

    const loadMore = useCallback(async () => {
        if (!hasNext || loadingMore || loadingInitial || !nextCursor) return;

        setLoadingMore(true);
        try {
            const res = await executeFetch(nextCursor);
            const feedItems = Array.isArray(res) ? res : (res?.items ?? []);
            setItems(prev => [...prev, ...feedItems]);
            setNextCursor(Array.isArray(res) ? null : (res?.nextCursor ?? null));
            setHasNext(Array.isArray(res) ? false : (res?.hasNext ?? false));
        } catch (err) {
            console.error('追加フィードの取得に失敗しました:', err);
        } finally {
            setLoadingMore(false);
        }
    }, [hasNext, loadingMore, loadingInitial, nextCursor, executeFetch]);

    useEffect(() => {
        loadInitial();
    }, [loadInitial]);

    return {
        items,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
        refresh: loadInitial,
    };
}
