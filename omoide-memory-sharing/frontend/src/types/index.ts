export type FilterMode = 'COMMENT_ONLY' | 'ALL' | 'ALBUM';

export interface MemoryFeedItem {
    id: string | null; // From UUID
    type: 'PHOTO' | 'VIDEO' | null;
    contentBase64: string | null;
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
    coverPhotoBase64: string | null;
}

export interface AlbumDetail {
    albumId: string;
    albumName: string;
    count: number;
    createdAt: string;
    photos: MemoryFeedItem[];
}

