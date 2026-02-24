import { MemoryFeedItem, Comment } from '../types';

export const fetchFeed = async (cursor?: string, limit = 20): Promise<MemoryFeedItem[]> => {
    const url = new URL('/api/feed', window.location.origin);
    if (cursor) url.searchParams.append('cursor', cursor);
    url.searchParams.append('limit', limit.toString());

    const response = await fetch(url.toString());
    if (!response.ok) throw new Error('Failed to fetch feed');
    return response.json();
};

export const fetchComments = async (type: 'PHOTO' | 'VIDEO', id: number): Promise<Comment[]> => {
    const endpoint = `/api/content/${type.toLowerCase()}/${id}/comments`;
    const response = await fetch(endpoint);
    if (!response.ok) throw new Error('Failed to fetch comments');
    return response.json();
};
