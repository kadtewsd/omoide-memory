import { useState } from 'react';
import { fetchComments } from '@/shared/api';
import { MemoryFeedItem, Comment } from '@/shared/types';

export function useComments() {
    const [selectedItem, setSelectedItem] = useState<MemoryFeedItem | null>(null);
    const [comments, setComments] = useState<Comment[]>([]);
    const [commentsLoading, setCommentsLoading] = useState(false);

    const openModal = async (item: MemoryFeedItem) => {
        setSelectedItem(item);
        if (!item.id) {
            setComments([]);
            return;
        }
        setCommentsLoading(true);
        try {
            const comms = await fetchComments(item.id);
            setComments(comms);
        } catch (err) {
            console.error(err);
        } finally {
            setCommentsLoading(false);
        }
    };

    const closeModal = () => {
        setSelectedItem(null);
        setComments([]);
    };

    return { selectedItem, comments, commentsLoading, openModal, closeModal };
}
