import { MemoryFeedItem, PhotobookPeriod } from '@/shared/types';
import { getYearMonthRangeIso } from '@/shared/hooks/useFeed';
import { useFeedPagination } from '@/shared/hooks/useFeedPagination';

import { isValidYearMonth } from '@/shared/date';

/**
 * 期間（単月または from ~ to）から API 呼び出し用の ISO 範囲を算出する純粋関数。
 * 日付フォーマットが不正または from > to の場合はフィードを取得しないよう undefined を返す。
 */
export function getPeriodIsoRange(period: PhotobookPeriod): { startInclusive?: string; endExclusive?: string } {
    if (period.type === 'MONTH_TAB') {
        return getYearMonthRangeIso(period.yearMonth);
    }
    if (!isValidYearMonth(period.fromYearMonth) || !isValidYearMonth(period.toYearMonth)) {
        return { startInclusive: undefined, endExclusive: undefined };
    }
    if (period.fromYearMonth > period.toYearMonth) {
        return { startInclusive: undefined, endExclusive: undefined };
    }

    const { startInclusive } = getYearMonthRangeIso(period.fromYearMonth);
    const { endExclusive } = getYearMonthRangeIso(period.toYearMonth);
    return { startInclusive, endExclusive };
}

export interface UsePhotobookPhotosResult {
    photos: MemoryFeedItem[];
    hasNext: boolean;
    loadingInitial: boolean;
    loadingMore: boolean;
    loadMore: () => Promise<void>;
}

/**
 * 指定期間の写真をフィード API から取得するカスタムフック（一覧取得用）。
 * アルバム作成は写真のみを対象とするため、contentType に 'PHOTO' を指定して取得する。
 */
export function usePhotobookPhotos(period: PhotobookPeriod): UsePhotobookPhotosResult {
    const { startInclusive, endExclusive } = getPeriodIsoRange(period);

    const {
        items: photos,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
    } = useFeedPagination({
        startInclusive,
        endExclusive,
        contentType: 'PHOTO',
        limit: 25,
    });

    return {
        photos,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
    };
}
