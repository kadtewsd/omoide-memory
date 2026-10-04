import { AlbumDetail, MemoryFeedItem } from '@/shared/types';
import { FeedPhotoCard } from '@/shared/components/feed/FeedPhotoCard';
import { View } from '@/shared/components/feed/FeedPhotoCardMode';
import { AlbumDetailState } from './types';

export interface AlbumDetailPhotoGridProps {
    albumDetail: AlbumDetail | null;
    state: AlbumDetailState;
    loading: boolean;
    onPhotoClick: (item: MemoryFeedItem) => void;
    onToggleDeleteTarget: (photoId: string) => void;
}

export function AlbumDetailPhotoGrid({
    albumDetail,
    state,
    loading,
    onPhotoClick,
    onToggleDeleteTarget,
}: AlbumDetailPhotoGridProps) {
    if (loading) {
        return (
            <div className="flex justify-center py-20">
                <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (!albumDetail || albumDetail.photos.length === 0) {
        return (
            <p className="text-center py-10 text-gray-500 text-sm">
                写真が見つかりませんでした。
            </p>
        );
    }

    return (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3 sm:gap-4">
            {albumDetail.photos.map((item) => (
                <PhotoGridItem
                    key={item.id}
                    item={item}
                    state={state}
                    onPhotoClick={onPhotoClick}
                    onToggleDeleteTarget={onToggleDeleteTarget}
                />
            ))}
        </div>
    );
}

interface PhotoGridItemProps {
    item: MemoryFeedItem;
    state: AlbumDetailState;
    onPhotoClick: (item: MemoryFeedItem) => void;
    onToggleDeleteTarget: (photoId: string) => void;
}

function PhotoGridItem({
    item,
    state,
    onPhotoClick,
    onToggleDeleteTarget,
}: PhotoGridItemProps) {
    switch (state.value) {
        case 'view':
            return (
                <div
                    className="relative aspect-square rounded-xl overflow-hidden shadow-sm cursor-pointer"
                    onClick={() => onPhotoClick(item)}
                >
                    <FeedPhotoCard
                        item={item}
                        mode={new View()}
                        onClick={() => {}}
                    />
                </div>
            );

        case 'delete': {
            const isDeleteTarget = item.id !== null && state.deleteTargetIds.has(item.id);
            return (
                <div
                    className="relative aspect-square rounded-xl overflow-hidden shadow-sm cursor-pointer"
                    onClick={() => {
                        if (item.id !== null) {
                            onToggleDeleteTarget(item.id);
                        }
                    }}
                >
                    <div className={`w-full h-full transition-opacity ${isDeleteTarget ? 'opacity-40' : ''}`}>
                        <FeedPhotoCard
                            item={item}
                            mode={new View()}
                            onClick={() => {}}
                        />
                    </div>
                    {isDeleteTarget ? (
                        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                            <div className="bg-red-500 rounded-full p-1.5">
                                <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            </div>
                        </div>
                    ) : (
                        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                            <div className="bg-white/70 rounded-full p-1">
                                <svg className="w-4 h-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                </svg>
                            </div>
                        </div>
                    )}
                </div>
            );
        }
    }
}
