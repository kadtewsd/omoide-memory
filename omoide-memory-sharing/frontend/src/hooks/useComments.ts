import { useState } from 'react';
import { fetchComments } from '../api';
import { MemoryFeedItem, Comment } from '../types';

export function useComments() {
    const [selectedItem, setSelectedItem] = useState<MemoryFeedItem | null>(null);
    const [comments, setComments] = useState<Comment[]>([]);
    const [commentsLoading, setCommentsLoading] = useState(false);

    const openModal = async (item: MemoryFeedItem) => {
        setSelectedItem(item);
        setCommentsLoading(true);
        try {
            const comms = await fetchComments(item.type, item.id);
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
