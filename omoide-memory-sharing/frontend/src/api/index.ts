/// <reference types="vite/client" />
import { MemoryFeedItem, Comment } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const fetchFeed = async (cursor?: string, limit = 20): Promise<MemoryFeedItem[]> => {
    const url = new URL('/api/feed', API_BASE_URL);
    if (cursor) url.searchParams.append('cursor', cursor);
    url.searchParams.append('limit', limit.toString());

    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch feed');
    return response.json();
};

export const fetchComments = async (type: 'PHOTO' | 'VIDEO', id: number): Promise<Comment[]> => {
    const endpoint = `${API_BASE_URL}/api/content/${type.toLowerCase()}/${id}/comments`;
    const response = await fetch(endpoint);
    if (!response.ok) throw new Error('Failed to fetch comments');
    return response.json();
};
