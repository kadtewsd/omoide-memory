import { FeedPhotoCard, Select } from '@/shared/components/FeedPhotoCard';
import { SelectionFeedProps } from './types';
import { FeedMonthTabs } from './FeedMonthTabs';
import { FeedContentContainer } from './FeedContentContainer';

/**
 * 選択モードのフィードコンポーネント。
 * コンテンツ（写真）選択と選択状況の表示、ヘッダー拡張スロットの描画を行う。
 */
export function SelectionFeed({
    items,
    hasNext,
    loadingInitial,
    loadingMore,
    loadMore,
    monthTabs,
    selectedYearMonth,
    onSelectMonthTab,
    selectedPhotoIds,
    selectedCount = selectedPhotoIds.size,
    maxCount,
    onTogglePhoto,
    title = 'コンテンツを選択',
    onBack,
    headerControls,
    headerActions,
}: SelectionFeedProps) {
    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md shadow-sm border-b border-gray-200 px-4 sm:px-6 py-3.5 space-y-3">
                <div className="flex items-center justify-between gap-3 flex-wrap">
                    <div className="flex items-center gap-3">
                        {onBack && (
                            <button
                                type="button"
                                onClick={onBack}
                                className="p-2 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-xl transition-colors"
                                aria-label="戻る"
                            >
                                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                                </svg>
                            </button>
                        )}
                        <h2 className="text-base sm:text-lg font-bold text-gray-900">
                            {title}
                        </h2>
                    </div>
                    <span className="text-sm font-semibold text-blue-700 bg-blue-50 px-3 py-1.5 rounded-full border border-blue-200">
                        {maxCount !== undefined ? `${selectedCount} / ${maxCount} 枚選択中` : `${selectedCount} 枚選択中`}
                    </span>
                </div>

                {headerControls && (
                    <div className="flex items-center gap-4 flex-wrap">
                        {headerControls}
                    </div>
                )}

                {monthTabs && monthTabs.length > 0 && onSelectMonthTab && (
                    <FeedMonthTabs
                        monthTabs={monthTabs}
                        selectedYearMonth={selectedYearMonth}
                        onSelectMonthTab={onSelectMonthTab}
                    />
                )}

                {headerActions && (
                    <div className="flex items-center gap-2.5 flex-wrap">
                        {headerActions}
                    </div>
                )}
            </header>

            <FeedContentContainer
                loadingInitial={loadingInitial}
                hasItems={items.length > 0}
                hasNext={hasNext}
                loadingMore={loadingMore}
                loadMore={loadMore}
                emptyMessage="この期間に写真はありません"
            >
                <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-1 sm:gap-2">
                    {items.map(item => {
                        const isSelected = item.id !== null && selectedPhotoIds.has(item.id);
                        const isAtLimit = maxCount !== undefined && selectedCount >= maxCount && !isSelected;
                        return (
                            <div key={item.id} className={isAtLimit ? 'opacity-50' : ''}>
                                <FeedPhotoCard
                                    item={item}
                                    mode={
                                        new Select(
                                            isSelected,
                                            isAtLimit ? null : () => onTogglePhoto(item)
                                        )
                                    }
                                    onClick={() => onTogglePhoto(item)}
                                />
                            </div>
                        );
                    })}
                </div>
            </FeedContentContainer>
        </div>
    );
}
