import { useState, useCallback, useEffect } from 'react';
import { fetchCapturedYearMonths, fetchAlbumRandomPhotos, replaceAlbumContent, addContent, clearAlbumContentsAndChangePeriod } from '@/shared/api';
import { MemoryFeedItem, PhotobookPeriod } from '@/shared/types';
import { isoToJstYearMonth, getCurrentYearMonth } from '@/shared/hooks/useFeed';
import { periodRangeToDates } from '@/shared/components/PeriodSelector';

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
    initialPeriod?: PhotobookPeriod;
}

export interface UsePhotobookSelectionResult {
    selectedPhotoIds: Set<string>;
    selectedPhotos: MemoryFeedItem[];
    savingPhotoIds: Set<string>;
    period: PhotobookPeriod;
    monthTabs: string[];
    maxCount: number;
    fileNamePrefix: string;
    isSelectingRandom: boolean;
    setMaxCount: (count: number) => void;
    setSelectedPhotos: (photos: MemoryFeedItem[]) => void;
    togglePhotoSelection: (photo: MemoryFeedItem) => Promise<void>;
    clearSelection: () => void;
    fillRemaining: () => Promise<void>;
    replacePhoto: (targetId: string) => Promise<{ oldPhoto: MemoryFeedItem; newPhoto: MemoryFeedItem } | null>;
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
    initialPeriod,
}: UsePhotobookSelectionParams): UsePhotobookSelectionResult {
    const [selectedPhotos, setSelectedPhotos] = useState<MemoryFeedItem[]>(initialPhotos);
    const [savingPhotoIds, setSavingPhotoIds] = useState<Set<string>>(new Set());
    const [period, setPeriod] = useState<PhotobookPeriod>(
        initialPeriod ?? {
            type: 'MONTH_TAB',
            yearMonth: getCurrentYearMonth(),
        }
    );
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
                    if (!initialPeriod) {
                        const defaultYm = yearMonths[0];
                        setPeriod({
                            type: 'MONTH_TAB',
                            yearMonth: defaultYm,
                        });
                        const { periodFrom, periodTo } = periodRangeToDates({ fromYearMonth: defaultYm, toYearMonth: defaultYm });
                        clearAlbumContentsAndChangePeriod({ albumId, periodFrom, periodTo }).catch(err =>
                            console.error('アルバム期間の初期更新に失敗しました:', err)
                        );
                    }
                }
            } catch (err) {
                console.error('年月の取得に失敗しました:', err);
            }
        };

        initYearMonths();
    }, [albumId, initialPeriod]);

    const togglePhotoSelection = useCallback(async (photo: MemoryFeedItem) => {
        if (photo.id === null) return;
        const photoId = photo.id;

        const isAlreadySelected = selectedPhotos.some(p => p.id === photoId);
        if (!isAlreadySelected && selectedPhotos.length >= maxCount) return;

        setSavingPhotoIds(prev => new Set([...prev, photoId]));

        const nextPhotos = isAlreadySelected
            ? selectedPhotos.filter(p => p.id !== photoId)
            : [...selectedPhotos, photo];

        setSelectedPhotos(nextPhotos);

        if (!isAlreadySelected) {
            try {
                await addContent({
                    albumId,
                    resource: { photoId, capturedAt: photo.captureTime ?? null },
                });
            } catch (err) {
                console.error('写真の保存に失敗しました:', err);
            }
        }

        setSavingPhotoIds(prev => {
            const next = new Set(prev);
            next.delete(photoId);
            return next;
        });
    }, [albumId, selectedPhotos, maxCount]);

    const clearSelection = useCallback(() => {
        setSelectedPhotos([]);
    }, []);

    /**
     * 自動補完: サーバー側で albumId の既存写真を除外した上でランダムに (maxCount - 現在の選択数) 件選出。
     * レスポンスはアルバムの最新状態（capturedAt ASC 順）なのでそのまま selectedPhotos に置き換える。
     */
    const fillRemaining = useCallback(async () => {
        const remaining = maxCount - selectedPhotos.length;
        if (remaining <= 0) return;

        setIsSelectingRandom(true);
        try {
            const albumCurrentState = await fetchAlbumRandomPhotos({
                albumId,
                count: remaining,
            });
            setSelectedPhotos(albumCurrentState.photos);
        } catch (err) {
            console.error('ランダム選出に失敗しました:', err);
        } finally {
            setIsSelectingRandom(false);
        }
    }, [albumId, maxCount, selectedPhotos]);

    /**
     * 差し替え: 旧写真を削除し、サーバー側でランダムに1件選出・保存。
     * { oldPhoto, newPhoto } を返して呼び出し元がダイアログを制御する。
     * 呼び出し元がダイアログで OK を押した時点で selectedPhotos を capturedAt ASC でソートして反映する。
     */
    const replacePhoto = useCallback(async (targetId: string): Promise<{ oldPhoto: MemoryFeedItem; newPhoto: MemoryFeedItem } | null> => {
        const oldPhoto = selectedPhotos.find(p => p.id === targetId);
        if (!oldPhoto) return null;

        setIsSelectingRandom(true);
        try {
            const newPhoto = await replaceAlbumContent({ albumId, photoId: targetId });
            return { oldPhoto, newPhoto };
        } catch (err) {
            console.error('写真差し替えに失敗しました:', err);
            return null;
        } finally {
            setIsSelectingRandom(false);
        }
    }, [albumId, selectedPhotos]);

    /**
     * 年月タブ選択: 単月モードに切り替え、カレンダー選択を解除
     */
    const selectMonthTab = useCallback(async (ym: string) => {
        setPeriod({
            type: 'MONTH_TAB',
            yearMonth: ym,
        });
        setSelectedPhotos([]);
        const { periodFrom, periodTo } = periodRangeToDates({ fromYearMonth: ym, toYearMonth: ym });
        try {
            await clearAlbumContentsAndChangePeriod({ albumId, periodFrom, periodTo });
        } catch (err) {
            console.error('アルバムコンテンツの削除と期間更新に失敗しました:', err);
        }
    }, [albumId]);

    /**
     * カレンダー期間選択: 期間モードに切り替え、年月タブの選択を解除
     */
    const selectDateRange = useCallback(async (params: { fromYearMonth: string; toYearMonth: string }) => {
        const isConflict = params.fromYearMonth > params.toYearMonth;
        const toYearMonth = isConflict ? params.fromYearMonth : params.toYearMonth;
        setPeriod({
            type: 'DATE_RANGE',
            fromYearMonth: params.fromYearMonth,
            toYearMonth,
        });
        setSelectedPhotos([]);
        const { periodFrom, periodTo } = periodRangeToDates({ fromYearMonth: params.fromYearMonth, toYearMonth });
        try {
            await clearAlbumContentsAndChangePeriod({ albumId, periodFrom, periodTo });
        } catch (err) {
            console.error('アルバムコンテンツの削除と期間更新に失敗しました:', err);
        }
    }, [albumId]);

    return {
        selectedPhotoIds,
        selectedPhotos,
        savingPhotoIds,
        period,
        monthTabs,
        maxCount,
        fileNamePrefix,
        isSelectingRandom,
        setMaxCount,
        setSelectedPhotos,
        togglePhotoSelection,
        clearSelection,
        fillRemaining,
        replacePhoto,
        selectMonthTab,
        selectDateRange,
    };
}
