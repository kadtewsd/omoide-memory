export interface MemoryFeedItem {
    id: number;
    type: 'PHOTO' | 'VIDEO';
    filePath: string;
    captureTime: string | null;
    thumbnailBase64?: string;
    thumbnailMimeType?: string;
    commentCount?: number;
}

export interface Comment {
    id: number;
    commenterName: string;
    commenterIconBase64: string | null;
    commentBody: string;
    commentedAt: string;
}
