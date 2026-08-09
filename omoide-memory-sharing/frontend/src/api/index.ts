/// <reference types="vite/client" />
import { MemoryFeedItem, Comment, FilterMode } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const fetchFeed = async (
    startInclusive?: string,
    endExclusive?: string,
    mode: FilterMode = 'COMMENT_ONLY',
): Promise<MemoryFeedItem[]> => {
    const url = new URL('/api/feed', API_BASE_URL);
    if (startInclusive) url.searchParams.append('startInclusive', startInclusive);
    if (endExclusive) url.searchParams.append('endExclusive', endExclusive);
    url.searchParams.append('mode', mode);

    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch feed');
    return response.json();
};

export const fetchComments = async (id: string): Promise<Comment[]> => {
    const endpoint = `${API_BASE_URL}/api/content/${id}/comments`;
    const response = await fetch(endpoint);
    if (!response.ok) throw new Error('Failed to fetch comments');
    return response.json();
};

export const fetchCapturedYearMonths = async (): Promise<string[]> => {
    const url = new URL('/api/contents-captured-ym', API_BASE_URL);
    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch captured dates');
    const dates: string[] = await response.json();
    return dates;
};

export const fetchCommentCreatedYearMonths = async (): Promise<string[]> => {
    const url = new URL('/api/comment-created-ym', API_BASE_URL);
    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch comment created dates');
    const dates: string[] = await response.json();
    return dates;
};


