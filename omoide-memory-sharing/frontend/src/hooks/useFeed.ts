import { useState, useEffect, useCallback } from 'react';
import { fetchFeed, fetchCapturedYearMonths, fetchCommentCreatedYearMonths } from '../api';
import { MemoryFeedItem, FilterMode } from '../types';

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

export function useFeed() {
    const [filterMode, setFilterMode] = useState<FilterMode>('ALL');
    const [currentYearMonth, setCurrentYearMonth] = useState<string>('');
    const [monthTabs, setMonthTabs] = useState<string[]>([]);
    const [items, setItems] = useState<MemoryFeedItem[]>([]);
    const [loading, setLoading] = useState(false);

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
            } catch (err) {
                console.error('年月の取得に失敗しました:', err);
                const fallbackYm = getCurrentYearMonth();
                setMonthTabs([fallbackYm]);
                setCurrentYearMonth(fallbackYm);
            }
        };

        initYearMonths();
    }, [filterMode]);

    const loadMonthData = useCallback(async (ym: string, mode: FilterMode) => {
        if (!ym) return;
        setLoading(true);
        try {
            const { startInclusive, endExclusive } = getYearMonthRangeIso(ym);
            const fetched = await fetchFeed(startInclusive, endExclusive, mode);
            setItems(fetched);
        } catch (err) {
            console.error('データの取得に失敗しました:', err);
            setItems([]);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (currentYearMonth) {
            loadMonthData(currentYearMonth, filterMode);
        }
    }, [currentYearMonth, filterMode, loadMonthData]);

    const selectMonthTab = useCallback((ym: string) => {
        setCurrentYearMonth(ym);
    }, []);

    const changeFilterMode = useCallback((mode: FilterMode) => {
        setFilterMode(mode);
    }, []);

    return {
        items,
        loading,
        filterMode,
        currentYearMonth,
        monthTabs,
        selectMonthTab,
        changeFilterMode,
    };
}

