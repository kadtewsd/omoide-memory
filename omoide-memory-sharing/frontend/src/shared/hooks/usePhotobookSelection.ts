import { useState, useCallback, useEffect } from 'react';
import { fetchCapturedYearMonths, fetchFeed, fetchRandomPhoto } from '@/shared/api';
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
    initialPhotos?: MemoryFeedItem[];
    initialAlbumName?: string;
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
export function usePhotobookSelection(params: UsePhotobookSelectionParams): UsePhotobookSelectionResult {
    const [selectedPhotos, setSelectedPhotos] = useState<MemoryFeedItem[]>(params.initialPhotos || []);
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
    const [maxCount, setMaxCount] = useState<number>(params.initialMaxCount || PHOTOBOOK_ABSOLUTE_MAX);

    const [isSelectingRandom, setIsSelectingRandom] = useState(false);

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
        if (!startInclusive || !endExclusive) return;

        const selectedIdSet = new Set(
            selectedPhotos.map(p => p.id).filter((id): id is string => id !== null)
        );

        const newlySelected: MemoryFeedItem[] = [];
        let cursorCaptureTime: string | undefined = undefined;
        let cursorId: string | undefined = undefined;

        while (newlySelected.length < remaining) {
            const response = await fetchFeed({
                startInclusive,
                endExclusive,
                cursorCaptureTime,
                cursorId,
                limit: 200,
                contentType: 'PHOTO',
            });

            if (response.items.length === 0) break;

            for (const item of response.items) {
                if (item.id !== null && !selectedIdSet.has(item.id)) {
                    selectedIdSet.add(item.id);
                    newlySelected.push(item);
                    if (newlySelected.length >= remaining) {
                        break;
                    }
                }
            }

            if (!response.hasNext || response.items.length < 200) {
                break;
            }

            const lastItem = response.items[response.items.length - 1];
            cursorCaptureTime = lastItem.captureTime ?? undefined;
            cursorId = lastItem.id ?? undefined;
        }

        if (newlySelected.length > 0) {
            setSelectedPhotos(prev => [...prev, ...newlySelected]);
        }
    }, [selectedPhotos, period, maxCount]);

    /**
     * 差し替え: プレビュー画面で targetId の写真を同期間の別の写真1枚と差し替える。
     * ランダムエンドポイントから写真を取得し、クライアント側ですでに選択されていないかを確認する。
     * すでに選択されている場合は再度取得し、重複しない写真が得られたら差し替える。
     * もう存在しない場合は、取得をやめて該当写真を削除する。
     */
    const replacePhoto = useCallback(async (targetId: string) => {
        const { startInclusive, endExclusive } = getPeriodIsoRange(period);
        if (!endExclusive) return;

        setIsSelectingRandom(true);
        try {
            const selectedIdSet = new Set(
                selectedPhotos.map(p => p.id).filter((id): id is string => id !== null)
            );

            let foundPhoto: MemoryFeedItem | null = null;
            const maxRetryCount = 15;

            for (let attempt = 0; attempt < maxRetryCount; attempt++) {
                const photo = await fetchRandomPhoto({
                    startInclusive: startInclusive || undefined,
                    endExclusive,
                });

                if (!photo || photo.id === null) {
                    break;
                }

                if (!selectedIdSet.has(photo.id)) {
                    foundPhoto = photo;
                    break;
                }
            }

            if (foundPhoto) {
                setSelectedPhotos(prev =>
                    prev.map(p => (p.id === targetId ? foundPhoto : p))
                );
            } else {
                // もう存在しないので削除
                setSelectedPhotos(prev => prev.filter(p => p.id !== targetId));
            }
        } finally {
            setIsSelectingRandom(false);
        }
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

    const fileNamePrefix = params.initialAlbumName || getPhotobookFileNamePrefix(period);

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
