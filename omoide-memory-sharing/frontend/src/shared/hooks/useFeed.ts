import { useState, useEffect, useCallback } from 'react';
import { fetchFeed, fetchCapturedYearMonths, fetchCommentCreatedYearMonths } from '@/shared/api';
import { FeedCursor, FilterMode, MemoryFeedItem } from '@/shared/types';

export function getCurrentYearMonth(): string {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
}

export function formatYearMonthDisplay(yearMonthStr: string): string {
    const [year, month] = yearMonthStr.split('-');
    return `${year}/${month}`;
}

export function getYearMonthRangeIso(yearMonthStr: string): { startInclusive: string; endExclusive: string } {
    const [year, month] = yearMonthStr.split('-').map(Number);
    // JST 00:00:00 は UTC 前日 15:00:00 (9時間手前)
    // JST固定オフセット (+9時間) に合わせてUTCから9時間を引いたエポックミリ秒で生成
    const start = new Date(Date.UTC(year, month - 1, 1) - 9 * 60 * 60 * 1000);
    const end = new Date(Date.UTC(year, month, 1) - 9 * 60 * 60 * 1000);

    return {
        startInclusive: start.toISOString(),
        endExclusive: end.toISOString(),
    };
}

/**
 * ISO 8601形式の日時文字列を日本標準時 (JST: UTC+9) 基準の YYYY-MM 形式に変換します。
 */
export function isoToJstYearMonth(isoStr: string): string {
    const date = new Date(isoStr);
    const jstDate = new Date(date.getTime() + 9 * 60 * 60 * 1000);
    const year = jstDate.getUTCFullYear();
    const month = String(jstDate.getUTCMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
}

export interface UseFeedResult {
    items: MemoryFeedItem[];
    hasNext: boolean;
    loadingInitial: boolean;
    loadingMore: boolean;
    loadMore: () => Promise<void>;
    currentYearMonth: string;
    monthTabs: string[];
    selectMonthTab: (ym: string) => void;
}

export function useFeed(filterMode: FilterMode): UseFeedResult {
    const [currentYearMonth, setCurrentYearMonth] = useState<string>('');
    const [monthTabs, setMonthTabs] = useState<string[]>([]);

    const [items, setItems] = useState<MemoryFeedItem[]>([]);
    const [nextCursor, setNextCursor] = useState<FeedCursor | null>(null);
    const [hasNext, setHasNext] = useState(false);
    const [loadingInitial, setLoadingInitial] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);

    useEffect(() => {
        const initYearMonths = async () => {
            try {
                const datesIso: string[] = filterMode === 'COMMENT_ONLY'
                    ? await fetchCommentCreatedYearMonths()
                    : await fetchCapturedYearMonths();

                const yearMonths: string[] = Array.from(
                    new Set<string>(
                        datesIso.map(isoToJstYearMonth)
                    )
                );

                if (yearMonths.length > 0) {
                    setMonthTabs(yearMonths);
                    setCurrentYearMonth(yearMonths[0]);
                } else {
                    const fallbackYm = getCurrentYearMonth();
                    setMonthTabs([fallbackYm]);
                    setCurrentYearMonth(fallbackYm);
                }
            } catch {
                const fallbackYm = getCurrentYearMonth();
                setMonthTabs([fallbackYm]);
                setCurrentYearMonth(fallbackYm);
            }
        };

        initYearMonths();
    }, [filterMode]);

    const loadInitial = useCallback(async (ym: string, mode: FilterMode) => {
        if (!ym) return;
        setLoadingInitial(true);
        setItems([]);
        setNextCursor(null);
        setHasNext(false);
        try {
            const { startInclusive, endExclusive } = getYearMonthRangeIso(ym);
            const res = await fetchFeed({ startInclusive, endExclusive, mode, limit: 25 });
            const feedItems = Array.isArray(res) ? res : (res?.items ?? []);
            setItems(feedItems);
            setNextCursor(Array.isArray(res) ? null : (res?.nextCursor ?? null));
            setHasNext(Array.isArray(res) ? false : (res?.hasNext ?? false));
        } catch {
            setItems([]);
        } finally {
            setLoadingInitial(false);
        }
    }, []);

    const loadMore = useCallback(async () => {
        if (!hasNext || loadingMore || loadingInitial || !nextCursor || !currentYearMonth) return;
        setLoadingMore(true);
        try {
            const { startInclusive, endExclusive } = getYearMonthRangeIso(currentYearMonth);
            const res = await fetchFeed({
                startInclusive,
                endExclusive,
                mode: filterMode,
                cursorCaptureTime: nextCursor.captureTime,
                cursorId: nextCursor.id,
                limit: 25,
            });
            const feedItems = Array.isArray(res) ? res : (res?.items ?? []);
            setItems(prev => [...prev, ...feedItems]);
            setNextCursor(Array.isArray(res) ? null : (res?.nextCursor ?? null));
            setHasNext(Array.isArray(res) ? false : (res?.hasNext ?? false));
        } catch (err) {
            console.error('追加データの取得に失敗しました:', err);
        } finally {
            setLoadingMore(false);
        }
    }, [hasNext, loadingMore, loadingInitial, nextCursor, currentYearMonth, filterMode]);

    useEffect(() => {
        if (currentYearMonth) {
            loadInitial(currentYearMonth, filterMode);
        }
    }, [currentYearMonth, filterMode, loadInitial]);

    const selectMonthTab = useCallback((ym: string) => {
        setCurrentYearMonth(ym);
    }, []);

    return {
        items,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
        currentYearMonth,
        monthTabs,
        selectMonthTab,
    };
}
