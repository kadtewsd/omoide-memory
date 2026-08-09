import { useState } from 'react';
import { MemoryFeedItem, FilterMode } from '../types';
import { FeedPhotoCard } from './FeedPhotoCard';
import { FeedVideoCard } from './FeedVideoCard';

interface Props {
    items: MemoryFeedItem[];
    filterMode: FilterMode;
    onItemClick: (item: MemoryFeedItem) => void;
}

export function FeedGrid({ items, filterMode, onItemClick }: Props) {
    const [openCommentIds, setOpenCommentIds] = useState<Record<string, boolean>>({});

    const toggleComments = (id: string, e: React.MouseEvent) => {
        e.stopPropagation();
        setOpenCommentIds(prev => ({ ...prev, [id]: !prev[id] }));
    };

    return (
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4 auto-rows-[200px]">
            {items.map((item, idx) => {
                const key = `${item.type}-${item.id}-${idx}`;
                const hasComments = (item.commentCount || 0) > 0;
                const isCommentsOpen = !!openCommentIds[item.id];

                return (
                    <div key={key} className="flex flex-col gap-1">
                        {item.type === 'VIDEO' ? (
                            <FeedVideoCard item={item} onClick={() => onItemClick(item)} />
                        ) : (
                            <FeedPhotoCard item={item} onClick={() => onItemClick(item)} />
                        )}

                        {filterMode === 'ALL' && hasComments && (
                            <button
                                type="button"
                                onClick={(e) => toggleComments(item.id, e)}
                                className="text-xs text-blue-600 hover:text-blue-800 font-medium flex items-center justify-between px-1 py-0.5 bg-gray-100 rounded"
                            >
                                <span>{isCommentsOpen ? 'コメントをとじる' : 'コメントをひらく'}</span>
                                <span className="bg-blue-100 text-blue-800 rounded-full px-1.5 py-0.2 text-[10px]">
                                    {item.commentCount}
                                </span>
                            </button>
                        )}
                        {filterMode === 'ALL' && hasComments && isCommentsOpen && (
                            <button
                                type="button"
                                onClick={() => onItemClick(item)}
                                className="text-xs text-gray-600 bg-white border border-gray-200 rounded p-2 text-left hover:bg-gray-50 shadow-sm"
                            >
                                コメントを表示（クリックで詳細）
                            </button>
                        )}
                    </div>
                );
            })}
        </div>
    );
}

