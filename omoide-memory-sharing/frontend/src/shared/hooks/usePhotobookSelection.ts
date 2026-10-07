import { useState, useCallback, useEffect } from 'react';
import { fetchCapturedYearMonths, fetchAlbumRandomPhotos, updateAlbum } from '@/shared/api';
import { MemoryFeedItem, PhotobookPeriod } from '@/shared/types';
import { isoToJstYearMonth, getCurrentYearMonth } from '@/shared/hooks/useFeed';
import { getPeriodIsoRange } from '@/shared/hooks/usePhotobookPhotos';

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

export interface UsePhotobookSelectionParams {
    albumId: string;
    initialPhotos?: MemoryFeedItem[];
    initialAlbumName: string;
    initialMaxCount?: number;
}

export interface UsePhotobookSelectionResult {
    selectedPhotoIds: Set<string>;
    selectedPhotos: MemoryFeedItem[];
    period: PhotobookPeriod;
    monthTabs: string[];
    maxCount: number;
    fileNamePrefix: string;
    isSelectingRandom: boolean;
    setMaxCount: (count: number) => void;
    togglePhotoSelection: (photo: MemoryFeedItem) => Promise<void>;
    clearSelection: () => Promise<void>;
    fillRemaining: () => Promise<void>;
    replacePhoto: (targetId: string) => Promise<void>;
    selectMonthTab: (ym: string) => void;
    selectDateRange: (params: { fromYearMonth: string; toYearMonth: string }) => void;
}

/**
 * フォトブック選択・差し替え・自動補完を管理するカスタムフック。
 */
export function usePhotobookSelection({
    albumId,
    initialPhotos = [],
    initialAlbumName,
    initialMaxCount = PHOTOBOOK_ABSOLUTE_MAX,
}: UsePhotobookSelectionParams): UsePhotobookSelectionResult {
    const [selectedPhotos, setSelectedPhotos] = useState<MemoryFeedItem[]>(initialPhotos);
    const [period, setPeriod] = useState<PhotobookPeriod>({
        type: 'MONTH_TAB',
        yearMonth: getCurrentYearMonth(),
    });
    const [monthTabs, setMonthTabs] = useState<string[]>([]);
    const [maxCount, setMaxCount] = useState<number>(initialMaxCount);
    const [isSelectingRandom, setIsSelectingRandom] = useState(false);

    const fileNamePrefix = initialAlbumName || getPhotobookFileNamePrefix(period);

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

    const savePhotosToAlbum = useCallback(async (photos: MemoryFeedItem[]) => {
        const photoIds = photos
            .map(p => p.id)
            .filter((id): id is string => id !== null);
        await updateAlbum({
            albumId,
            resource: {
                albumName: fileNamePrefix,
                photoIds,
                status: 'DRAFT',
            },
        });
    }, [albumId, fileNamePrefix]);

    const togglePhotoSelection = useCallback(async (photo: MemoryFeedItem) => {
        if (photo.id === null) return;

        const isAlreadySelected = selectedPhotos.some(p => p.id === photo.id);
        let nextPhotos: MemoryFeedItem[];
        if (isAlreadySelected) {
            nextPhotos = selectedPhotos.filter(p => p.id !== photo.id);
        } else {
            if (selectedPhotos.length >= maxCount) return;
            nextPhotos = [...selectedPhotos, photo];
        }

        setSelectedPhotos(nextPhotos);
        try {
            await savePhotosToAlbum(nextPhotos);
        } catch (err) {
            console.error('写真の保存に失敗しました:', err);
        }
    }, [selectedPhotos, maxCount, savePhotosToAlbum]);

    const clearSelection = useCallback(async () => {
        setSelectedPhotos([]);
        try {
            await savePhotosToAlbum([]);
        } catch (err) {
            console.error('写真のクリアに失敗しました:', err);
        }
    }, [savePhotosToAlbum]);

    /**
     * 自動補完: サーバー側で albumId の既存写真を除外した上でランダムに (maxCount - 現在の選択数) 件選出
     */
    const fillRemaining = useCallback(async () => {
        const remaining = maxCount - selectedPhotos.length;
        if (remaining <= 0) return;

        const { startInclusive, endExclusive } = getPeriodIsoRange(period);
        if (!endExclusive) return;

        setIsSelectingRandom(true);
        try {
            const newlySelected = await fetchAlbumRandomPhotos({
                albumId,
                startInclusive: startInclusive || undefined,
                endExclusive,
                count: remaining,
            });

            if (newlySelected.length > 0) {
                const nextPhotos = [...selectedPhotos, ...newlySelected];
                setSelectedPhotos(nextPhotos);
                await savePhotosToAlbum(nextPhotos);
            }
        } catch (err) {
            console.error('ランダム選出に失敗しました:', err);
        } finally {
            setIsSelectingRandom(false);
        }
    }, [albumId, maxCount, selectedPhotos, period, savePhotosToAlbum]);

    /**
     * 差し替え: サーバー側で albumId の既存写真を除外した上で同期間の別の写真を1件選出
     */
    const replacePhoto = useCallback(async (targetId: string) => {
        const { startInclusive, endExclusive } = getPeriodIsoRange(period);
        if (!endExclusive) return;

        setIsSelectingRandom(true);
        try {
            const candidates = await fetchAlbumRandomPhotos({
                albumId,
                startInclusive: startInclusive || undefined,
                endExclusive,
                count: 1,
            });

            const newPhoto = candidates.length > 0 ? candidates[0] : null;
            let nextPhotos: MemoryFeedItem[];
            if (newPhoto) {
                nextPhotos = selectedPhotos.map(p => (p.id === targetId ? newPhoto : p));
            } else {
                // もう存在しないので削除
                nextPhotos = selectedPhotos.filter(p => p.id !== targetId);
            }
            setSelectedPhotos(nextPhotos);
            await savePhotosToAlbum(nextPhotos);
        } catch (err) {
            console.error('写真差し替えに失敗しました:', err);
        } finally {
            setIsSelectingRandom(false);
        }
    }, [albumId, period, selectedPhotos, savePhotosToAlbum]);

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

    return {
        selectedPhotoIds,
        selectedPhotos,
        period,
        monthTabs,
        maxCount,
        fileNamePrefix,
        isSelectingRandom,
        setMaxCount,
        togglePhotoSelection,
        clearSelection,
        fillRemaining,
        replacePhoto,
        selectMonthTab,
        selectDateRange,
    };
}
