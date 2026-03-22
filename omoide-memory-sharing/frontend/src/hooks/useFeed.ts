import { useState, useEffect, useCallback } from 'react';
import { fetchFeed } from '../api';
import { MemoryFeedItem } from '../types';

export function useFeed() {
    const [items, setItems] = useState<MemoryFeedItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [hasMore, setHasMore] = useState(true);

    const loadMore = useCallback(async () => {
        if (loading || !hasMore) return;
        setLoading(true);
        try {
            const cursor = items.length > 0 ? items[items.length - 1].id : undefined;
            const newItems = await fetchFeed(cursor, 20);
            if (newItems.length === 0) {
                setHasMore(false);
            } else {
                setItems(prev => [...prev, ...newItems]);
            }
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, [items, loading, hasMore]);

    useEffect(() => {
        if (items.length === 0) {
            loadMore();
        }
    }, [loadMore, items.length]);

    return { items, loading, hasMore, loadMore };
}
