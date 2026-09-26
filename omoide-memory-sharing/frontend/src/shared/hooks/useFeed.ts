import { useState, useEffect, useCallback } from 'react';
import { fetchCapturedYearMonths, fetchCommentCreatedYearMonths } from '@/shared/api';
import { FilterMode, MemoryFeedItem } from '@/shared/types';
import { useFeedPagination } from '@/shared/hooks/useFeedPagination';
import { isValidYearMonth, normalizeYearMonth } from '@/shared/date';

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

export function getYearMonthRangeIso(yearMonthStr: string): { startInclusive?: string; endExclusive?: string } {
    if (!isValidYearMonth(yearMonthStr)) {
        return { startInclusive: undefined, endExclusive: undefined };
    }
    const normalized = normalizeYearMonth(yearMonthStr);
    const [year, month] = normalized.split('-').map(Number);
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

/**
 * メインフィード（ALL または COMMENT_ONLY）の年月タブ選択と一覧ページネーションを管理するカスタムフック。
 */
export function useFeed(filterMode: FilterMode): UseFeedResult {
    const [currentYearMonth, setCurrentYearMonth] = useState<string>('');
    const [monthTabs, setMonthTabs] = useState<string[]>([]);

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

    const { startInclusive, endExclusive } = currentYearMonth
        ? getYearMonthRangeIso(currentYearMonth)
        : { startInclusive: undefined, endExclusive: undefined };

    const {
        items,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
    } = useFeedPagination({
        startInclusive,
        endExclusive,
        mode: filterMode,
        limit: 25,
    });

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
