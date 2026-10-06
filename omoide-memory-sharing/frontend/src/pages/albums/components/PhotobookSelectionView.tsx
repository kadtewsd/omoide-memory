import { useEffect } from 'react';
import { MemoryFeedItem, PhotobookPeriod } from '@/shared/types';
import { PeriodSelector, PeriodRange } from '@/shared/components/PeriodSelector';
import { CountBox } from '@/shared/components/CountBox';
import { getCurrentYearMonth } from '@/shared/hooks/useFeed';
import { usePhotobookPhotos } from '@/shared/hooks/usePhotobookPhotos';
import { PHOTOBOOK_ABSOLUTE_MAX } from '@/shared/hooks/usePhotobookSelection';
import { Feed, FeedPhotoCard, Select } from '@/shared/components/feed';
import { PrimaryButton } from '@/shared/components/button';

export interface PhotobookSelectionViewProps {
    selectedPhotoIds: Set<string>;
    selectedCount: number;
    maxCount: number;
    period: PhotobookPeriod;
    monthTabs: string[];
    title: string;
    isSelectingRandom: boolean;
    onTogglePhoto: (photo: MemoryFeedItem) => void;
    onSelectMonthTab: (ym: string) => void;
    onSelectDateRange: (params: { fromYearMonth: string; toYearMonth: string }) => void;
    onChangeMaxCount: (count: number) => void;
    onFillRemaining: (totalCount: number) => Promise<void>;
    onConfirm: () => void;
    onBackToMain: () => void;
}

/**
 * フォトブック・アルバム写真選択フェーズ。
 * アルバム固有の操作（枚数指定、期間指定、補完ボタン）を構築し、
 * 共通の Feed コンポーネントに注入してフィードを描画する。
 */
export function PhotobookSelectionView({
    selectedPhotoIds,
    selectedCount,
    maxCount,
    period,
    monthTabs,
    title,
    isSelectingRandom,
    onTogglePhoto,
    onSelectMonthTab,
    onSelectDateRange,
    onChangeMaxCount,
    onFillRemaining,
    onConfirm,
    onBackToMain,
}: PhotobookSelectionViewProps) {
    const { photos, hasNext, totalCount, loadingInitial, loadingMore, loadMore } = usePhotobookPhotos(period);
    // 期間の写真件数と絶対上限の小さい方を実効上限とする（totalCount が 0 のときはまだ未ロードなので絶対上限で代替）
    const effectiveMax = totalCount > 0 ? Math.min(PHOTOBOOK_ABSOLUTE_MAX, totalCount) : PHOTOBOOK_ABSOLUTE_MAX;
    const remaining = maxCount - selectedCount;

    // 期間内の写真総数が取得され、現在の maxCount が実効上限を超えている場合は実効上限に補正する
    useEffect(() => {
        if (totalCount > 0 && maxCount > effectiveMax) {
            onChangeMaxCount(effectiveMax);
        }
    }, [totalCount, effectiveMax, maxCount, onChangeMaxCount]);

    // period からカレンダー表示用 range を直接導出（Derived State）
    const initialMonth = period.type === 'MONTH_TAB' ? period.yearMonth : getCurrentYearMonth();
    const range: PeriodRange = period.type === 'DATE_RANGE'
        ? { fromYearMonth: period.fromYearMonth, toYearMonth: period.toYearMonth }
        : { fromYearMonth: initialMonth, toYearMonth: initialMonth };

    const handleRangeChange = (newRange: PeriodRange) => {
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
        <>
            <Feed
                items={photos}
                hasNext={hasNext}
                totalCount={totalCount}
                totalCountLabel="該当期間の写真"
                totalCountUnit="枚"
                loadingInitial={loadingInitial}
                loadingMore={loadingMore}
                loadMore={loadMore}
                monthTabs={monthTabs}
                selectedYearMonth={period.type === 'MONTH_TAB' ? period.yearMonth : undefined}
                onSelectMonthTab={onSelectMonthTab}
                title={title}
                onBack={onBackToMain}
                headerStatus={
                    <span className="text-sm font-semibold text-blue-700 bg-blue-50 px-3 py-1.5 rounded-full border border-blue-200">
                        {selectedCount} / {maxCount} 枚選択中
                    </span>
                }
                headerControls={
                    <>
                        <CountBox
                            label="このアルバムの枚数:"
                            value={maxCount}
                            min={1}
                            max={effectiveMax}
                            unit="枚"
                            onChange={onChangeMaxCount}
                        />
                        <div className="h-6 w-px bg-gray-300 hidden md:block" />
                        <PeriodSelector
                            range={range}
                            isActive={period.type === 'DATE_RANGE'}
                            onRangeChange={handleRangeChange}
                            onActivate={handleActivateRange}
                        />
                    </>
                }
                headerActions={
                    <>
                        {remaining > 0 && selectedCount < effectiveMax && (
                            <button
                                type="button"
                                onClick={() => onFillRemaining(totalCount)}
                                disabled={isSelectingRandom}
                                className="px-4 py-2 text-sm font-semibold text-white bg-green-600 hover:bg-green-700 active:bg-green-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-colors min-h-[44px] cursor-pointer"
                            >
                                あと {remaining} 枚はランダムで補完する
                            </button>
                        )}
                        <PrimaryButton
                            onClick={onConfirm}
                            disabled={selectedCount === 0 || isSelectingRandom}
                        >
                            <span>選択完了 → 確認へ ({selectedCount} 枚)</span>
                        </PrimaryButton>
                    </>
                }
            >
                <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-1 sm:gap-2">
                    {photos.map(item => {
                        const isSelected = item.id !== null && selectedPhotoIds.has(item.id);
                        const isAtLimit = selectedCount >= maxCount && !isSelected;
                        return (
                            <div key={item.id} className={isAtLimit ? 'opacity-50' : ''}>
                                <FeedPhotoCard
                                    item={item}
                                    mode={new Select(isSelected, () => onTogglePhoto(item))}
                                    onClick={() => onTogglePhoto(item)}
                                />
                            </div>
                        );
                    })}
                </div>
            </Feed>
            {isSelectingRandom && (
                <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex flex-col items-center justify-center">
                    <div className="bg-white px-6 py-5 rounded-2xl shadow-xl flex flex-col items-center gap-3 border border-gray-100">
                        <div className="animate-spin rounded-full h-8 w-8 border-3 border-blue-600 border-t-transparent" />
                        <p className="text-sm font-semibold text-gray-800">コンテンツ選出中...</p>
                    </div>
                </div>
            )}
        </>
    );
}
