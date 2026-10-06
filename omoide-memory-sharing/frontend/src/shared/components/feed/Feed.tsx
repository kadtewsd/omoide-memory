import { ReactNode } from 'react';
import { MemoryFeedItem } from '@/shared/types';
import { FeedMonthTabs } from './FeedMonthTabs';
import { FeedContentContainer } from './FeedContentContainer';
import { FeedCountBadge } from './FeedCountBadge';

export interface FeedProps {
    items: MemoryFeedItem[];
    hasNext: boolean;
    loadingInitial: boolean;
    loadingMore: boolean;
    loadMore: () => Promise<void> | void;
    monthTabs?: string[];
    selectedYearMonth?: string;
    onSelectMonthTab?: (ym: string) => void;
    totalCount?: number;
    totalCountLabel?: string;
    totalCountUnit?: string;
    title?: string;
    onBack?: () => void;
    headerControls?: ReactNode;
    headerActions?: ReactNode;
    headerStatus?: ReactNode;
    children?: ReactNode;
}

/**
 * 共通フィードコンポーネント。
 * ヘッダーレイアウト、期間/年月タブ、総件数バッジ、ローディングおよび無限スクロールコンテナを一元管理する。
 * 各ページ固有の操作・グリッド・モーダル等は呼び出し元（Page/View）から注入する。
 */
export function Feed({
    items,
    hasNext,
    loadingInitial,
    loadingMore,
    loadMore,
    monthTabs,
    selectedYearMonth,
    onSelectMonthTab,
    totalCount,
    totalCountLabel,
    totalCountUnit,
    title,
    onBack,
    headerControls,
    headerActions,
    headerStatus,
    children,
}: FeedProps) {
    const hasHeaderTop = Boolean(title || onBack || headerStatus || headerActions || (!headerControls && totalCount !== undefined && totalCount > 0));

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-[69px] z-20 bg-white/95 backdrop-blur-md border-b border-gray-200 px-4 sm:px-6 py-2.5 space-y-2.5 shadow-xs">
                {hasHeaderTop && (
                    <div className="flex items-center justify-between gap-3 flex-wrap">
                        <div className="flex items-center gap-3 flex-wrap">
                            {onBack && (
                                <button
                                    type="button"
                                    onClick={onBack}
                                    className="p-2 min-h-[40px] min-w-[40px] flex items-center justify-center text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-xl transition-colors cursor-pointer"
                                    aria-label="戻る"
                                >
                                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                                    </svg>
                                </button>
                            )}
                            {title && (
                                <h1 className="text-base sm:text-lg font-bold text-gray-900">
                                    {title}
                                </h1>
                            )}
                            {!headerControls && totalCount !== undefined && totalCount > 0 && (
                                <FeedCountBadge count={totalCount} label={totalCountLabel} unit={totalCountUnit} />
                            )}
                        </div>

                        <div className="flex items-center gap-2 flex-wrap">
                            {headerStatus}
                            {headerActions}
                        </div>
                    </div>
                )}

                {headerControls && (
                    <div className="flex items-center gap-3 sm:gap-4 flex-wrap">
                        {headerControls}
                        {totalCount !== undefined && totalCount > 0 && (
                            <FeedCountBadge count={totalCount} label={totalCountLabel} unit={totalCountUnit} />
                        )}
                    </div>
                )}

                {monthTabs && monthTabs.length > 0 && onSelectMonthTab && (
                    <FeedMonthTabs
                        monthTabs={monthTabs}
                        selectedYearMonth={selectedYearMonth}
                        onSelectMonthTab={onSelectMonthTab}
                    />
                )}
            </header>

            <FeedContentContainer
                loadingInitial={loadingInitial}
                hasItems={(items?.length ?? 0) > 0}
                hasNext={hasNext}
                loadingMore={loadingMore}
                loadMore={loadMore}
                emptyMessage="表示できるおもいではありません"
            >
                {children}
            </FeedContentContainer>
        </div>
    );
}
