export interface MemoryFeedItem {
    id: string; // From UUID
    type: 'PHOTO' | 'VIDEO' | null;
    contentBase64: string | null;
    commentedAt: string; // ISO 8601 string from OffsetDateTime
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
