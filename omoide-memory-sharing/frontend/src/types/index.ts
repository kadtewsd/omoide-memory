export type FilterMode = 'COMMENT_ONLY' | 'ALL' | 'ALBUM' | 'PHOTOBOOK';

export interface MemoryFeedItem {
    id: string | null; // From UUID
    type: 'PHOTO' | 'VIDEO' | null;
    commentedAt: string; // ISO 8601 string from OffsetDateTime
    captureTime?: string | null;
    thumbnailBase64?: string | null;
    thumbnailMimeType?: string | null;
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
}

/** `fetchRandomFillPhotos` のパラメータ */
export interface FetchRandomFillPhotosParams {
    startInclusive: string;
    endExclusive: string;
    excludeIds: string[];
    count: number;
}
