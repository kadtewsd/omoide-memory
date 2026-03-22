import { MemoryFeedItem } from '../types';
import { ContentNotFound } from './ContentNotFound';

interface Props {
    item: MemoryFeedItem;
    onClick: () => void;
}

export function FeedPhotoCard({ item, onClick }: Props) {
    return (
        <div
            className="group relative rounded-2xl overflow-hidden cursor-pointer bg-gray-100 transition-transform active:scale-95 aspect-square"
            onClick={onClick}
        >
            {item.contentBase64 ? (
                <img 
                    src={item.contentBase64} 
                    alt="Memory" 
                    className="w-full h-full object-cover transition-transform group-hover:scale-105 duration-500"
                    loading="lazy"
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
