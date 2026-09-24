import { FilterMode, MemoryFeedItem } from '@/shared/types';

export interface NormalFeedProps {
    filterMode: FilterMode;
}

export interface SelectionFeedProps {
    items: MemoryFeedItem[];
    hasNext: boolean;
    loadingInitial: boolean;
    loadingMore: boolean;
    loadMore: () => void;
    monthTabs?: string[];
    selectedYearMonth?: string;
    onSelectMonthTab?: (ym: string) => void;
    selectedPhotoIds: Set<string>;
    selectedCount?: number;
    maxCount?: number;
    onTogglePhoto: (photo: MemoryFeedItem) => void;
    title?: string;
    onBack?: () => void;
    headerControls?: React.ReactNode;
    headerActions?: React.ReactNode;
}
