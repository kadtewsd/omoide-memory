import { MemoryFeedItem, Comment } from '../types';
import { CommentPanel } from './CommentPanel';
import { ContentNotFound } from './ContentNotFound';
import { VideoPlayer } from './VideoPlayer';

interface Props {
    selectedItem: MemoryFeedItem | null;
    comments: Comment[];
    commentsLoading: boolean;
    onClose: () => void;
}

export function MemoryModal({ selectedItem, comments, commentsLoading, onClose }: Props) {
    if (!selectedItem) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex flex-col md:flex-row bg-black/95 animate-in fade-in duration-200"
            role="dialog"
            aria-modal="true"
            aria-label="おもいで詳細表示"
        >
            <div className="flex-1 flex flex-col min-h-0 relative">
                <header className="p-3 sm:p-4 flex justify-between items-center text-white z-10 bg-gradient-to-b from-black/60 to-transparent">
                    <button
                        type="button"
                        onClick={onClose}
                        aria-label="モーダルを閉じる"
                        className="p-2 hover:bg-white/20 active:bg-white/30 rounded-full transition-colors min-w-[44px] min-h-[44px] flex items-center justify-center"
                    >
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                    <div className="text-xs sm:text-sm font-medium text-gray-300">
                        {selectedItem.commentedAt ? new Date(selectedItem.commentedAt).toLocaleDateString() : ''}
                    </div>
                </header>

                <div className="flex-1 flex items-center justify-center p-2 sm:p-4 min-h-0 overflow-hidden">
                    {selectedItem.type === 'VIDEO' && selectedItem.id ? (
                        <VideoPlayer
                            videoId={selectedItem.id}
                            poster={selectedItem.thumbnailBase64}
                        />
                    ) : selectedItem.contentBase64 ? (
                        <img
                            src={selectedItem.contentBase64}
                            alt="拡大写真"
                            className="max-w-full max-h-full object-contain rounded-lg"
                        />
                    ) : (
                        <div className="w-full h-full max-w-lg max-h-96 relative">
                           <ContentNotFound />
                        </div>
                    )}
                </div>
            </div>

            {/* Comment Section: Bottom drawer on mobile, side panel on desktop */}
            <div className="h-64 sm:h-72 md:h-auto md:w-80 border-t md:border-t-0 md:border-l border-gray-800 flex-shrink-0 bg-white">
                <CommentPanel comments={comments} loading={commentsLoading} />
            </div>
        </div>
    );
}
