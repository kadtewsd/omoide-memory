import { MemoryFeedItem } from '../types';
import { FeedPhotoCard } from './FeedPhotoCard';
import { FeedVideoCard } from './FeedVideoCard';

interface Props {
    items: MemoryFeedItem[];
    onItemClick: (item: MemoryFeedItem) => void;
}

export function FeedGrid({ items, onItemClick }: Props) {
    return (
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4 auto-rows-[200px]">
            {items.map((item, idx) => {
                const key = `${item.type}-${item.id}-${idx}`;
                if (item.type === 'VIDEO') {
                    return <FeedVideoCard key={key} item={item} onClick={() => onItemClick(item)} />;
                }
                return <FeedPhotoCard key={key} item={item} onClick={() => onItemClick(item)} />;
            })}
        </div>
    );
}
