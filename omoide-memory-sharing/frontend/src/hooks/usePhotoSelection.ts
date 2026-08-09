import { useState, useCallback } from 'react';

export function usePhotoSelection() {
    const [selectedPhotoIds, setSelectedPhotoIds] = useState<Set<string>>(new Set());

    const togglePhotoSelection = useCallback((photoId: string) => {
        setSelectedPhotoIds(prev => {
            const next = new Set(prev);
            if (next.has(photoId)) {
                next.delete(photoId);
            } else {
                next.add(photoId);
            }
            return next;
        });
    }, []);

    const clearSelection = useCallback(() => {
        setSelectedPhotoIds(new Set());
    }, []);

    return {
        selectedPhotoIds,
        togglePhotoSelection,
        clearSelection,
    };
}
