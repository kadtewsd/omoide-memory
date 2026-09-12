import { MemoryFeedItem } from '../types';
import { FeedPhotoCard } from './FeedPhotoCard';
import { formatYearMonthDisplay } from '../hooks/useFeed';
import { usePhotobookMonthPhotos } from '../hooks/usePhotobookSelection';
import { PHOTOBOOK_ABSOLUTE_MAX } from '../hooks/usePhotobookSelection';

interface Props {
    selectedPhotoIds: Set<string>;
    selectedCount: number;
    maxCount: number;
    currentYearMonth: string;
    monthTabs: string[];
    onTogglePhoto: (photo: MemoryFeedItem) => void;
    onSelectMonthTab: (ym: string) => void;
    onChangeMaxCount: (count: number) => void;
    onFillRemaining: () => Promise<void>;
    onConfirm: () => void;
}

/**
 * フォトブック写真選択フェーズ。
 * 年月タブで月を切り替え、写真のみのグリッドからタップ選択する。
 * ユーザーが目標枚数（最大 200）を指定でき、その枚数に達するまで自動補完ボタンを表示する。
 */
export function PhotobookSelectionView({
    selectedPhotoIds,
    selectedCount,
    maxCount,
    currentYearMonth,
    monthTabs,
    onTogglePhoto,
    onSelectMonthTab,
    onChangeMaxCount,
    onFillRemaining,
    onConfirm,
}: Props) {
    const { photos, loading } = usePhotobookMonthPhotos(currentYearMonth);
    const remaining = maxCount - selectedCount;

    const handleMaxCountInput = (raw: string) => {
        const parsed = parseInt(raw, 10);
        if (isNaN(parsed) || parsed < 1) return;
        onChangeMaxCount(Math.min(parsed, PHOTOBOOK_ABSOLUTE_MAX));
    };

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-0 z-10 bg-white/95 backdrop-blur-md shadow-sm border-b border-gray-200 px-4 sm:px-6 py-3.5 space-y-3">
                <div className="flex items-center justify-between gap-3 flex-wrap">
                    <h2 className="text-base sm:text-lg font-bold text-gray-900">
                        フォトブック用写真を選択
                    </h2>
                    <span className="text-sm font-semibold text-blue-700 bg-blue-50 px-3 py-1.5 rounded-full border border-blue-200">
                        {selectedCount} / {maxCount} 枚選択中
                    </span>
                </div>

                {/* 目標枚数入力 */}
                <div className="flex items-center gap-2">
                    <label className="text-sm font-medium text-gray-700 whitespace-nowrap" htmlFor="max-count-input">
                        このアルバムの枚数:
                    </label>
                    <input
                        id="max-count-input"
                        type="number"
                        min={1}
                        max={PHOTOBOOK_ABSOLUTE_MAX}
                        value={maxCount}
                        onChange={e => handleMaxCountInput(e.target.value)}
                        className="w-20 px-2 py-1 text-sm font-semibold text-center border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-500">枚（最大 {PHOTOBOOK_ABSOLUTE_MAX} 枚）</span>
                </div>

                {/* 年月タブ */}
                <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar -mx-4 px-4 sm:mx-0 sm:px-0">
                    <div className="flex items-center gap-2 py-0.5">
                        {monthTabs.map(ym => (
                            <button
                                key={ym}
                                type="button"
                                onClick={() => onSelectMonthTab(ym)}
                                className={`px-4 py-2 text-xs sm:text-sm font-bold rounded-full whitespace-nowrap transition-colors min-h-[40px] flex items-center justify-center ${
                                    ym === currentYearMonth
                                        ? 'bg-blue-600 text-white shadow-sm ring-2 ring-blue-600/30'
                                        : 'bg-gray-100 text-gray-800 hover:bg-gray-200 border border-gray-200'
                                }`}
                            >
                                {formatYearMonthDisplay(ym)}
                            </button>
                        ))}
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
                {loading ? (
                    <div className="flex justify-center py-20">
                        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
                    </div>
                ) : photos.length > 0 ? (
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
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                        <svg className="w-16 h-16 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        <p className="text-lg font-medium">この月に写真はありません</p>
                    </div>
                )}
            </main>
        </div>
    );
}
