import { useState, useCallback, useEffect } from 'react';
import { fetchCapturedYearMonths, fetchFeed, fetchRandomFillPhotos } from '../api';
import { MemoryFeedItem } from '../types';
import { isoToJstYearMonth, getYearMonthRangeIso } from './useFeed';

/** フォトブック選択の絶対上限枚数（サービス仕様の制限値） */
export const PHOTOBOOK_ABSOLUTE_MAX = 200;

export function usePhotobookSelection() {
    const [selectedPhotos, setSelectedPhotos] = useState<MemoryFeedItem[]>([]);
    const [currentYearMonth, setCurrentYearMonth] = useState<string>('');
    const [monthTabs, setMonthTabs] = useState<string[]>([]);
    /**
     * ユーザーが指定する目標枚数。
     * 自動補完・上限チェックはこの値を基準に計算する。
     * PHOTOBOOK_ABSOLUTE_MAX (200) を超えることはできない。
     */
    const [maxCount, setMaxCount] = useState<number>(PHOTOBOOK_ABSOLUTE_MAX);

    // 選択済み写真IDのSetは selectedPhotos から都度導出する（単一の状態ソース）
    const selectedPhotoIds: Set<string> = new Set(
        selectedPhotos.map(p => p.id).filter((id): id is string => id !== null)
    );

    useEffect(() => {
        const initYearMonths = async () => {
            try {
                const datesIso = await fetchCapturedYearMonths();
                const yearMonths = Array.from(
                    new Set<string>(datesIso.map(isoToJstYearMonth))
                );
                if (yearMonths.length > 0) {
                    setMonthTabs(yearMonths);
                    setCurrentYearMonth(yearMonths[0]);
                }
            } catch (err) {
                console.error('年月の取得に失敗しました:', err);
            }
        };

        initYearMonths();
    }, []);

    const togglePhotoSelection = useCallback((photo: MemoryFeedItem) => {
        if (photo.id === null) return;

        setSelectedPhotos(prev => {
            const isAlreadySelected = prev.some(p => p.id === photo.id);
            if (isAlreadySelected) {
                return prev.filter(p => p.id !== photo.id);
            }
            if (prev.length >= maxCount) return prev;
            return [...prev, photo];
        });
    }, [maxCount]);

    const clearSelection = useCallback(() => {
        setSelectedPhotos([]);
    }, []);

    /**
     * 自動補完: 現在選択中の年月の未選択写真を (maxCount - 現在の選択数) 件補充する。
     * maxCount に達している場合は何もしない。
     */
    const fillRemaining = useCallback(async () => {
        const remaining = maxCount - selectedPhotos.length;
        if (remaining <= 0 || !currentYearMonth) return;

        const { startInclusive, endExclusive } = getYearMonthRangeIso(currentYearMonth);
        const currentExcludeIds = selectedPhotos
            .map(p => p.id)
            .filter((id): id is string => id !== null);

        const filled = await fetchRandomFillPhotos({
            startInclusive,
            endExclusive,
            excludeIds: currentExcludeIds,
            count: remaining,
        });

        setSelectedPhotos(prev => [...prev, ...filled]);
    }, [selectedPhotos, currentYearMonth, maxCount]);

    /**
     * 差し替え: プレビュー画面で targetId の写真を同月の別の写真1枚と差し替える。
     * 差し替え後も合計枚数は変わらない（1対1の交換）。
     */
    const replacePhoto = useCallback(async (targetId: string) => {
        if (!currentYearMonth) return;

        const { startInclusive, endExclusive } = getYearMonthRangeIso(currentYearMonth);
        const currentExcludeIds = selectedPhotos
            .map(p => p.id)
            .filter((id): id is string => id !== null);

        const replacements = await fetchRandomFillPhotos({
            startInclusive,
            endExclusive,
            excludeIds: currentExcludeIds,
            count: 1,
        });

        if (replacements.length === 0) return;

        setSelectedPhotos(prev =>
            prev.map(p => (p.id === targetId ? replacements[0] : p))
        );
    }, [selectedPhotos, currentYearMonth]);

    const selectMonthTab = useCallback((ym: string) => {
        setCurrentYearMonth(ym);
    }, []);

    return {
        selectedPhotoIds,
        selectedPhotos,
        currentYearMonth,
        monthTabs,
        maxCount,
        setMaxCount,
        togglePhotoSelection,
        clearSelection,
        fillRemaining,
        replacePhoto,
        selectMonthTab,
    };
}

/**
 * 指定年月・フィルターモード ALLでフィードを取得し、写真のみ返すカスタムフック。
 * PhotobookSelectionView から使用する。
 */
export function usePhotobookMonthPhotos(currentYearMonth: string) {
    const [photos, setPhotos] = useState<MemoryFeedItem[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!currentYearMonth) return;

        const load = async () => {
            setLoading(true);
            try {
                const { startInclusive, endExclusive } = getYearMonthRangeIso(currentYearMonth);
                // バックエンドの FilterMode は PHOTOBOOK を持たないため ALL として送信する
                const fetched = await fetchFeed({ startInclusive, endExclusive, mode: 'ALL' });
                setPhotos(fetched.filter(item => item.type === 'PHOTO'));
            } catch (err) {
                console.error('写真の取得に失敗しました:', err);
                setPhotos([]);
            } finally {
                setLoading(false);
            }
        };

        load();
    }, [currentYearMonth]);

    return { photos, loading };
}
