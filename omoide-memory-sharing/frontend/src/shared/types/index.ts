export type FilterMode = 'COMMENT_ONLY' | 'ALL' | 'ALBUM' | 'PHOTOBOOK';
export type ContentType = 'ALL' | 'PHOTO' | 'VIDEO';

export interface MemoryFeedItem {
    id: string | null; // From UUID
    type: 'PHOTO' | 'VIDEO' | null;
    commentedAt: string; // ISO 8601 string from OffsetDateTime
    captureTime?: string | null;
    commentCount?: number;
}

export interface Comment {
    id: string; // From UUID
    commenterName: string;
    commenterIconBase64: string | null;
    commentBody: string;
    commentedAt: string;
}

export interface AlbumSummary {
    albumId: string;
    albumName: string;
    count: number;
    createdAt: string;
    coverPhotoId: string | null;
}

export interface AlbumDetail {
    albumId: string;
    albumName: string;
    count: number;
    createdAt: string;
    photos: MemoryFeedItem[];
}

/** `fetchFeed` のパラメータ */
export interface FetchFeedParams {
    startInclusive?: string;
    endExclusive?: string;
    mode?: FilterMode;
    cursorCaptureTime?: string;
    cursorId?: string;
    limit?: number;
    contentType?: ContentType;
}

export interface FeedCursor {
    captureTime: string;
    id: string;
}

export interface FeedPageResponse {
    items: MemoryFeedItem[];
    nextCursor: FeedCursor | null;
    hasNext: boolean;
}

/** `fetchRandomFillPhotos` のパラメータ */
export interface FetchRandomFillPhotosParams {
    startInclusive: string;
    endExclusive: string;
    excludeIds: string[];
    count: number;
}

/** 期間選択の種別 */
export type PeriodSelectionType = 'MONTH_TAB' | 'DATE_RANGE';

/** 単一月タブによる期間指定 */
export interface MonthTabPeriod {
    type: 'MONTH_TAB';
    yearMonth: string; // "YYYY-MM"
}

/** カレンダーによる期間（from ~ to）指定 */
export interface DateRangePeriod {
    type: 'DATE_RANGE';
    fromYearMonth: string; // "YYYY-MM"
    toYearMonth: string;   // "YYYY-MM"
}

export type PhotobookPeriod = MonthTabPeriod | DateRangePeriod;

