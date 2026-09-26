import { MemoryFeedItem, PhotobookPeriod } from '@/shared/types';
import { PeriodSelector, PeriodRange } from '@/shared/components/PeriodSelector';
import { CountBox } from '@/shared/components/CountBox';
import { getCurrentYearMonth } from '@/shared/hooks/useFeed';
import { usePhotobookPhotos } from '@/pages/albums/hooks/usePhotobookPhotos';
import { PHOTOBOOK_ABSOLUTE_MAX } from '@/pages/albums/hooks/usePhotobookSelection';
import { SelectionFeed } from '@/shared/components/feed';

export interface PhotobookSelectionViewProps {
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
 * アルバム固有の操作（枚数指定、期間指定、補完ボタン）を構築し、
 * shared 配下の SelectionFeed コンポーネントに渡して描画する。
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
}: PhotobookSelectionViewProps) {
    const { photos, hasNext, loadingInitial, loadingMore, loadMore } = usePhotobookPhotos(period);
    const remaining = maxCount - selectedCount;

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
        <SelectionFeed
            items={photos}
            hasNext={hasNext}
            loadingInitial={loadingInitial}
            loadingMore={loadingMore}
            loadMore={loadMore}
            monthTabs={monthTabs}
            selectedYearMonth={period.type === 'MONTH_TAB' ? period.yearMonth : undefined}
            onSelectMonthTab={onSelectMonthTab}
            selectedPhotoIds={selectedPhotoIds}
            selectedCount={selectedCount}
            maxCount={maxCount}
            onTogglePhoto={onTogglePhoto}
            title="フォトブック用写真を選択"
            onBack={onBackToMain}
            headerControls={
                <>
                    <CountBox
                        label="このアルバムの枚数:"
                        value={maxCount}
                        min={1}
                        max={PHOTOBOOK_ABSOLUTE_MAX}
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
                </>
            }
        />
    );
}

