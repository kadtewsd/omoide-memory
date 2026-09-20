/// <reference types="vite/client" />
import { MemoryFeedItem, Comment, AlbumSummary, AlbumDetail, FetchFeedParams, FetchRandomFillPhotosParams, FeedPageResponse } from '../types';


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

export const saveAlbum = async (albumName: string, photoIds: string[]): Promise<{ albumId: string; albumName: string; count: number }> => {
    const url = new URL('/albums', API_BASE_URL);
    const response = await fetch(url.toString(), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ albumName, photoIds }),
    });
    if (!response.ok) throw new Error('Failed to save album');
    return response.json();
};

export const downloadAlbumZip = async (albumName: string, photoIds: string[]): Promise<Blob> => {
    const url = new URL('/albums/download', API_BASE_URL);
    const response = await fetch(url.toString(), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ albumName, photoIds }),
    });
    if (!response.ok) throw new Error('Failed to download album zip');
    return response.blob();
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

/**
 * フォトブック自動補完: 指定期間の未選択写真をランダムに count 件取得する。
 * excludeIds に含まれる写真は除外されるため重複なしで補充できる。
 */
export const fetchRandomFillPhotos = async ({
    startInclusive,
    endExclusive,
    excludeIds,
    count,
}: FetchRandomFillPhotosParams): Promise<MemoryFeedItem[]> => {
    const url = new URL('/photos/random-fill', API_BASE_URL);
    url.searchParams.append('startInclusive', startInclusive);
    url.searchParams.append('endExclusive', endExclusive);
    excludeIds.forEach(id => url.searchParams.append('excludeIds', id));
    url.searchParams.append('count', String(count));

    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch random fill photos');
    return response.json();
};





