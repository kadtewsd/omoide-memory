import { useState, useEffect } from 'react';
import { MemoryFeedItem, PhotobookPeriod } from '@/shared/types';
import { FeedPhotoCard } from '@/shared/components/FeedPhotoCard';
import { PeriodSelector, PeriodRange } from '@/shared/components/PeriodSelector';
import { CountBox } from '@/shared/components/CountBox';
import { InfiniteScrollLoader } from '@/shared/components/InfiniteScrollLoader';
import { formatYearMonthDisplay, getCurrentYearMonth } from '@/shared/hooks/useFeed';
import { usePhotobookPhotos } from '@/pages/albums/hooks/usePhotobookPhotos';
import { PHOTOBOOK_ABSOLUTE_MAX } from '@/pages/albums/hooks/usePhotobookSelection';

interface Props {
    selectedPhotoIds: Set<string>;
    selectedCount: number;
    maxCount: number;
    period: PhotobookPeriod;
    monthTabs: string[];
    onTogglePhoto: (photo: MemoryFeedItem) => void;
    onSelectMonthTab: (ym: string) => void;
    onSelectDateRange: (params: { fromYearMonth: string; toYearMonth: string }) => void;
    onChangeMaxCount: (count: number) => void;
    onFillRemaining: () => Promise<void>;
    onConfirm: () => void;
    onBackToMain: () => void;
}

/**
 * フォトブック写真選択フェーズ。
 * - 枚数指定の横に期間（カレンダー）選択を配置。
 * - 年月タブ（単月選択）とカレンダー（期間選択）の排他制御。
 * - メインのトップページへの復帰導線。
 */
export function PhotobookSelectionView({
    selectedPhotoIds,
    selectedCount,
    maxCount,
    period,
    monthTabs,
    onTogglePhoto,
    onSelectMonthTab,
    onSelectDateRange,
    onChangeMaxCount,
    onFillRemaining,
    onConfirm,
    onBackToMain,
}: Props) {
    const { photos, hasNext, loadingInitial, loadingMore, loadMore } = usePhotobookPhotos(period);
    const remaining = maxCount - selectedCount;

    const isMonthTabMode = period.type === 'MONTH_TAB';
    const isDateRangeMode = period.type === 'DATE_RANGE';

    // カレンダーの入力値（ローカル管理）
    const initialMonth = period.type === 'MONTH_TAB' ? period.yearMonth : getCurrentYearMonth();
    const [range, setRange] = useState<PeriodRange>({
        fromYearMonth: period.type === 'DATE_RANGE' ? period.fromYearMonth : initialMonth,
        toYearMonth: period.type === 'DATE_RANGE' ? period.toYearMonth : initialMonth,
    });

    // period が DATE_RANGE に変わったときの同期
    useEffect(() => {
        if (period.type === 'DATE_RANGE') {
            setRange({
                fromYearMonth: period.fromYearMonth,
                toYearMonth: period.toYearMonth,
            });
        }
    }, [period]);

    const handleRangeChange = (newRange: PeriodRange) => {
        setRange(newRange);
        onSelectDateRange({
            fromYearMonth: newRange.fromYearMonth,
            toYearMonth: newRange.toYearMonth,
        });
    };

    const handleActivateRange = () => {
        onSelectDateRange({
            fromYearMonth: range.fromYearMonth,
            toYearMonth: range.toYearMonth,
        });
    };

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md shadow-sm border-b border-gray-200 px-4 sm:px-6 py-3.5 space-y-3">
                <div className="flex items-center justify-between gap-3 flex-wrap">
                    <div className="flex items-center gap-3">
                        <button
                            type="button"
                            onClick={onBackToMain}
                            className="p-2 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-xl transition-colors"
                            aria-label="メインに戻る"
                        >
                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                            </svg>
                        </button>
                        <h2 className="text-base sm:text-lg font-bold text-gray-900">
                            フォトブック用写真を選択
                        </h2>
                    </div>
                    <span className="text-sm font-semibold text-blue-700 bg-blue-50 px-3 py-1.5 rounded-full border border-blue-200">
                        {selectedCount} / {maxCount} 枚選択中
                    </span>
                </div>

                {/* 枚数入力とカレンダー期間指定（横並び配置） */}
                <div className="flex items-center gap-4 flex-wrap">
                    {/* 目標枚数入力（件数ボックス） */}
                    <CountBox
                        label="このアルバムの枚数:"
                        value={maxCount}
                        min={1}
                        max={PHOTOBOOK_ABSOLUTE_MAX}
                        unit="枚"
                        onChange={onChangeMaxCount}
                    />

                    <div className="h-6 w-px bg-gray-300 hidden md:block" />

                    {/* カレンダー期間指定（枚数の横に配置・排他制御） */}
                    <PeriodSelector
                        range={range}
                        isActive={isDateRangeMode}
                        onRangeChange={handleRangeChange}
                        onActivate={handleActivateRange}
                    />
                </div>

                {/* 年月タブ（カレンダー期間選択時は解除/非活性ハイライトなし） */}
                <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar -mx-4 px-4 sm:mx-0 sm:px-0">
                    <div className="flex items-center gap-2 py-0.5">
                        {monthTabs.map(ym => {
                            const isSelectedTab = isMonthTabMode && period.yearMonth === ym;
                            return (
                                <button
                                    key={ym}
                                    type="button"
                                    onClick={() => onSelectMonthTab(ym)}
                                    className={`px-4 py-2 text-xs sm:text-sm font-bold rounded-full whitespace-nowrap transition-colors min-h-[40px] flex items-center justify-center ${isSelectedTab
                                        ? 'bg-blue-600 text-white shadow-sm ring-2 ring-blue-600/30'
                                        : 'bg-gray-100 text-gray-800 hover:bg-gray-200 border border-gray-200'
                                        }`}
                                >
                                    {formatYearMonthDisplay(ym)}
                                </button>
                            );
                        })}
                    </div>
                </div>

                {/* アクションボタン */}
                <div className="flex items-center gap-2.5 flex-wrap">
                    {remaining > 0 && (
                        <button
                            type="button"
                            onClick={onFillRemaining}
                            className="px-4 py-2 text-sm font-semibold text-white bg-green-600 hover:bg-green-700 active:bg-green-800 rounded-xl shadow-sm transition-colors min-h-[44px]"
                        >
                            あと {remaining} 枚はランダムで補完する
                        </button>
                    )}
                    <button
                        type="button"
                        onClick={onConfirm}
                        disabled={selectedCount === 0}
                        className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:bg-gray-300 disabled:cursor-not-allowed rounded-xl shadow-sm transition-colors min-h-[44px]"
                    >
                        選択完了 → 確認へ ({selectedCount} 枚)
                    </button>
                </div>
            </header>

            <main className="p-4 sm:p-6 lg:p-8">
                {loadingInitial ? (
                    <div className="flex justify-center py-20">
                        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
                    </div>
                ) : photos.length > 0 ? (
                    <>
                        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-1 sm:gap-2">
                            {photos.map(photo => {
                                const isSelected = photo.id !== null && selectedPhotoIds.has(photo.id);
                                const isAtLimit = selectedCount >= maxCount && !isSelected;
                                return (
                                    <div key={photo.id} className={isAtLimit ? 'opacity-50' : ''}>
                                        <FeedPhotoCard
                                            item={photo}
                                            isSelected={isSelected}
                                            onToggleSelect={
                                                isAtLimit ? undefined : () => onTogglePhoto(photo)
                                            }
                                            onClick={() => onTogglePhoto(photo)}
                                        />
                                    </div>
                                );
                            })}
                        </div>
                        <InfiniteScrollLoader
                            onLoadMore={loadMore}
                            hasMore={hasNext}
                            loading={loadingMore}
                        />
                    </>
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                        <svg className="w-16 h-16 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        <p className="text-lg font-medium">この期間に写真はありません</p>
                    </div>
                )}
            </main>
        </div>
    );
}

