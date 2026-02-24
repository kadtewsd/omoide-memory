import { MemoryFeedItem, Comment } from '../types';
import { CommentPanel } from './CommentPanel';

interface Props {
    selectedItem: MemoryFeedItem | null;
    comments: Comment[];
    commentsLoading: boolean;
    onClose: () => void;
}

export function MemoryModal({ selectedItem, comments, commentsLoading, onClose }: Props) {
    if (!selectedItem) return null;

    return (
        <div className="fixed inset-0 z-50 flex bg-black/95 animate-in fade-in duration-200">
            <div className="flex-1 flex flex-col">
                <header className="p-4 flex justify-between items-center text-white">
                    <button
                        onClick={onClose}
                        className="p-2 hover:bg-white/10 rounded-full transition-colors"
                    >
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                    <div className="text-sm opacity-70">
                        {selectedItem.captureTime ? new Date(selectedItem.captureTime).toLocaleDateString() : ''}
                    </div>
                </header>

                <div className="flex-1 flex items-center justify-center p-4">
                    {selectedItem.type === 'VIDEO' && selectedItem.thumbnailBase64 ? (
                        <img
                            src={`data:${selectedItem.thumbnailMimeType || 'image/jpeg'};base64,${selectedItem.thumbnailBase64}`}
                            alt=""
                            className="max-w-full max-h-full object-contain"
                        />
                    ) : (
                        <div className="text-white/50">Image placeholder ({selectedItem.filePath})</div>
                    )}
                </div>
            </div>

            <CommentPanel comments={comments} loading={commentsLoading} />
        </div>
    );
}
