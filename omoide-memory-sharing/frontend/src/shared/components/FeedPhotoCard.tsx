import { useState } from 'react';
import { MemoryFeedItem } from '@/shared/types';
import { ContentNotFound } from '@/shared/components/ContentNotFound';
import { getImageUrl } from '@/shared/api';
import { CardMode } from './FeedPhotoCardMode';

export type { CardMode } from './FeedPhotoCardMode';
export { Select, View } from './FeedPhotoCardMode';

interface Props {
    item: MemoryFeedItem;
    mode: CardMode;
    onClick: () => void;
}

export function FeedPhotoCard({ item, mode, onClick }: Props) {
    const [hasError, setHasError] = useState(false);
    return (
        <div
            className={`group relative rounded-2xl overflow-hidden cursor-pointer bg-gray-100 transition-transform active:scale-95 aspect-square ${mode.styleName}`}
            onClick={onClick}
        >
            {/* Selection Checkbox */}
            {mode.onToggle && (
                <button
                    type="button"
                    aria-label={mode.ariaLabel}
                    className="absolute top-2 left-2 z-[2] p-2 min-w-[44px] min-h-[44px] flex items-center justify-center rounded-full focus:outline-none"
                    onClick={(e) => {
                        e.stopPropagation();
                        mode.onToggle?.(e);
                    }}
                >
                    <div
                        className={`w-7 h-7 rounded-full flex items-center justify-center border-2 border-white transition-colors ${mode.indicatorStyleName}`}
                    >
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                        </svg>
                    </div>
                </button>
            )}

            {item.id && !hasError ? (
                <img
                    src={getImageUrl(item.id)}
                    alt="Memory"
                    className="w-full h-full object-cover transition-transform group-hover:scale-105 duration-500"
                    loading="lazy"
                    onError={() => setHasError(true)}
                />
            ) : (
                <ContentNotFound />
            )}

            {/* Gradient overlay */}
            <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />

            {(item.commentCount || 0) > 0 && (
                <div className="absolute bottom-2 left-2 flex items-center gap-1 text-white text-xs drop-shadow-md">
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                    </svg>
                    {item.commentCount}
                </div>
            )}
        </div>
    );
}
