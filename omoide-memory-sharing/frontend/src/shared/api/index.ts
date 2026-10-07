/// <reference types="vite/client" />
import {
    MemoryFeedItem,
    Comment,
    AlbumSummary,
    AlbumDetail,
    FetchFeedParams,
    FetchRandomPhotoParams,
    FetchAlbumRandomPhotosParams,
    FeedPageResponse,
    AlbumResponse,
    AddContentResource,
    ContentsCountResponse,
    FetchContentsCountParams,
    ClearAlbumContentsParams,
} from '@/shared/types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const fetchFeed = async ({
    startInclusive,
    endExclusive,
    mode,
    cursorCaptureTime,
    cursorId,
    limit,
    contentType,
}: FetchFeedParams): Promise<FeedPageResponse> => {
    const url = new URL('/feed', API_BASE_URL);
    if (startInclusive) url.searchParams.append('startInclusive', startInclusive);
    if (endExclusive) url.searchParams.append('endExclusive', endExclusive);
    if (mode) url.searchParams.append('mode', mode);
    if (cursorCaptureTime) url.searchParams.append('cursorCaptureTime', cursorCaptureTime);
    if (cursorId) url.searchParams.append('cursorId', cursorId);
    if (limit) url.searchParams.append('limit', String(limit));
    if (contentType) url.searchParams.append('contentType', contentType);

    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch feed');
    return response.json();
};

export const fetchContentsCount = async ({
    startInclusive,
    endExclusive,
    mode,
    contentType,
}: FetchContentsCountParams): Promise<ContentsCountResponse> => {
    const url = new URL('/contents-count', API_BASE_URL);
    if (startInclusive) url.searchParams.append('startInclusive', startInclusive);
    url.searchParams.append('endExclusive', endExclusive);
    if (mode) url.searchParams.append('mode', mode);
    if (contentType) url.searchParams.append('contentType', contentType);

    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch contents count');
    return response.json();
};

export const fetchComments = async (id: string): Promise<Comment[]> => {
    const endpoint = `${API_BASE_URL}/content/${id}/comments`;
    const response = await fetch(endpoint);
    if (!response.ok) throw new Error('Failed to fetch comments');
    return response.json();
};

export const fetchCapturedYearMonths = async (): Promise<string[]> => {
    const url = new URL('/contents-captured-ym', API_BASE_URL);
    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch captured dates');
    const dates: string[] = await response.json();
    return dates;
};

export const fetchCommentCreatedYearMonths = async (): Promise<string[]> => {
    const url = new URL('/comment-created-ym', API_BASE_URL);
    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch comment created dates');
    const dates: string[] = await response.json();
    return dates;
};

export const getVideoStreamUrl = (id: string): string => {
    return `${API_BASE_URL}/video/${id}/stream`;
};

export const getVideoThumbnailUrl = (id: string): string => {
    return `${API_BASE_URL}/video/${id}/thumbnail`;
};

export const getImageUrl = (id: string): string => {
    return `${API_BASE_URL}/content/${id}/image`;
};

export const createAlbum = async (albumName: string): Promise<AlbumResponse> => {
    const url = new URL('/albums', API_BASE_URL);
    const response = await fetch(url.toString(), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ albumName }),
    });
    if (!response.ok) throw new Error('Failed to create album');
    return response.json();
};

export const clearAlbumContentsAndChangePeriod = async ({
    albumId,
    periodFrom,
    periodTo,
}: ClearAlbumContentsParams): Promise<void> => {
    const url = new URL(`/albums/${albumId}/contents`, API_BASE_URL);
    const response = await fetch(url.toString(), {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ periodFrom, periodTo }),
    });
    if (!response.ok) throw new Error('Failed to clear album contents and update period');
};

export const addContent = async ({
    albumId,
    contentId,
    resource,
}: {
    albumId: string;
    contentId: string;
    resource: AddContentResource;
}): Promise<void> => {
    const url = new URL(`/albums/${albumId}/contents/${contentId}`, API_BASE_URL);
    const response = await fetch(url.toString(), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(resource),
    });
    if (!response.ok) throw new Error('Failed to add content');
};

export const confirmAlbum = async (albumId: string): Promise<void> => {
    const url = new URL(`/albums/${albumId}/confirm`, API_BASE_URL);
    const response = await fetch(url.toString(), { method: 'POST' });
    if (!response.ok) throw new Error('Failed to confirm album');
};

export const fetchAlbumRandomPhotos = async ({
    albumId,
    count,
}: FetchAlbumRandomPhotosParams): Promise<MemoryFeedItem[]> => {
    const url = new URL(`/albums/${albumId}/photos/random`, API_BASE_URL);
    if (count !== undefined) url.searchParams.append('count', String(count));

    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch album random photos');
    return response.json();
};

export const startAlbumDownloadJob = async (
    albumId: string,
): Promise<{ jobId: string; albumId: string; status: string }> => {
    const url = new URL(`/albums/${albumId}/download-jobs`, API_BASE_URL);
    const response = await fetch(url.toString(), {
        method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to start album download job');
    return response.json();
};

export const getAlbumDownloadJobEventsUrl = (jobId: string): string => {
    return `${API_BASE_URL}/albums/download-jobs/${jobId}/events`;
};

export const downloadJobFile = async (
    jobId: string,
): Promise<{ blob: Blob; fileName: string }> => {
    const url = new URL(`/albums/download-jobs/${jobId}/file`, API_BASE_URL);
    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to download job file');

    const contentDisposition = response.headers.get('Content-Disposition');
    let fileName = 'album.zip';
    if (contentDisposition) {
        const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?([^;]+)/i);
        if (match && match[1]) {
            fileName = decodeURIComponent(match[1].replace(/["']/g, ''));
        }
    }

    const blob = await response.blob();
    return { blob, fileName };
};

export const fetchAlbums = async (): Promise<AlbumSummary[]> => {
    const url = new URL('/albums', API_BASE_URL);
    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch albums');
    return response.json();
};

export const fetchAlbumDetail = async (albumId: string): Promise<AlbumDetail> => {
    const url = new URL(`/albums/${albumId}`, API_BASE_URL);
    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch album detail');
    return response.json();
};

export const fetchRandomPhoto = async ({
    startInclusive,
    endExclusive,
}: FetchRandomPhotoParams): Promise<MemoryFeedItem | null> => {
    const url = new URL('/photos/random', API_BASE_URL);
    if (startInclusive) url.searchParams.append('startInclusive', startInclusive);
    url.searchParams.append('endExclusive', endExclusive);

    const response = await fetch(url.toString());
    if (response.status === 204) return null;
    if (!response.ok) throw new Error('Failed to fetch random photo');
    const text = await response.text();
    if (!text) return null;
    return JSON.parse(text) as MemoryFeedItem;
};
