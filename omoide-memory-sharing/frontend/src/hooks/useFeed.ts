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
    const start = new Date(year, month - 1, 1, 0, 0, 0, 0);
    const end = new Date(year, month, 1, 0, 0, 0, 0);
    return {
        startInclusive: start.toISOString(),
        endExclusive: end.toISOString(),
    };
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
                        datesIso.map((isoStr: string) => {
                            const date = new Date(isoStr);
                            const y = date.getFullYear();
                            const m = String(date.getMonth() + 1).padStart(2, '0');
                            return `${y}-${m}`;
                        })
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

