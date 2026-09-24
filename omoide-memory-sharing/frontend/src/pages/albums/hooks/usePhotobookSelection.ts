import { useState, useCallback, useEffect } from 'react';
import { fetchCapturedYearMonths, fetchRandomFillPhotos } from '@/shared/api';
import { MemoryFeedItem, PhotobookPeriod } from '@/shared/types';
import { isoToJstYearMonth, getCurrentYearMonth } from '@/shared/hooks/useFeed';
import { getPeriodIsoRange } from '@/pages/albums/hooks/usePhotobookPhotos';

/** フォトブック選択の絶対上限枚数（サービス仕様の制限値） */
export const PHOTOBOOK_ABSOLUTE_MAX = 200;

/**
 * 期間情報からダウンロード用ファイル名の接頭辞を生成する
 */
export function getPhotobookFileNamePrefix(period: PhotobookPeriod): string {
    if (period.type === 'MONTH_TAB') {
        return `photobook_${period.yearMonth.replace('-', '')}`;
    }
    const fromStr = period.fromYearMonth.replace('-', '');
    const toStr = period.toYearMonth.replace('-', '');
    return `photobook_${fromStr}_${toStr}`;
}

export interface UsePhotobookSelectionResult {
    selectedPhotoIds: Set<string>;
    selectedPhotos: MemoryFeedItem[];
    period: PhotobookPeriod;
    monthTabs: string[];
    maxCount: number;
    fileNamePrefix: string;
    setMaxCount: (count: number) => void;
    togglePhotoSelection: (photo: MemoryFeedItem) => void;
    clearSelection: () => void;
    fillRemaining: () => Promise<void>;
    replacePhoto: (targetId: string) => Promise<void>;
    selectMonthTab: (ym: string) => void;
    selectDateRange: (params: { fromYearMonth: string; toYearMonth: string }) => void;
}

/**
 * フォトブック選択・差し替え・自動補完を管理するカスタムフック。
 */
export function usePhotobookSelection(): UsePhotobookSelectionResult {
    const [selectedPhotos, setSelectedPhotos] = useState<MemoryFeedItem[]>([]);
    const [period, setPeriod] = useState<PhotobookPeriod>({
        type: 'MONTH_TAB',
        yearMonth: getCurrentYearMonth(),
    });
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
                    setPeriod({
                        type: 'MONTH_TAB',
                        yearMonth: yearMonths[0],
                    });
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
     * 自動補完: 現在選択中の期間の未選択写真を (maxCount - 現在の選択数) 件補充する。
     * maxCount に達している場合は何もしない。
     */
    const fillRemaining = useCallback(async () => {
        const remaining = maxCount - selectedPhotos.length;
        if (remaining <= 0) return;

        const { startInclusive, endExclusive } = getPeriodIsoRange(period);
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
    }, [selectedPhotos, period, maxCount]);

    /**
     * 差し替え: プレビュー画面で targetId の写真を同期間の別の写真1枚と差し替える。
     * 差し替え後も合計枚数は変わらない（1対1の交換）。
     */
    const replacePhoto = useCallback(async (targetId: string) => {
        const { startInclusive, endExclusive } = getPeriodIsoRange(period);
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
    }, [selectedPhotos, period]);

    /**
     * 年月タブ選択: 単月モードに切り替え、カレンダー選択を解除
     */
    const selectMonthTab = useCallback((ym: string) => {
        setPeriod({
            type: 'MONTH_TAB',
            yearMonth: ym,
        });
    }, []);

    /**
     * カレンダー期間選択: 期間モードに切り替え、年月タブの選択を解除
     */
    const selectDateRange = useCallback((params: { fromYearMonth: string; toYearMonth: string }) => {
        const isConflict = params.fromYearMonth > params.toYearMonth;
        setPeriod({
            type: 'DATE_RANGE',
            fromYearMonth: params.fromYearMonth,
            toYearMonth: isConflict ? params.fromYearMonth : params.toYearMonth,
        });
    }, []);

    const fileNamePrefix = getPhotobookFileNamePrefix(period);

    return {
        selectedPhotoIds,
        selectedPhotos,
        period,
        monthTabs,
        maxCount,
        fileNamePrefix,
        setMaxCount,
        togglePhotoSelection,
        clearSelection,
        fillRemaining,
        replacePhoto,
        selectMonthTab,
        selectDateRange,
    };
}
