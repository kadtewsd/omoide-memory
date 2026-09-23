import { useState } from 'react';
import { useFeed, formatYearMonthDisplay } from '@/shared/hooks/useFeed';
import { useComments } from '@/shared/hooks/useComments';
import { usePhotoSelection } from '@/shared/hooks/usePhotoSelection';
import { FeedGrid } from '@/shared/components/FeedGrid';
import { InfiniteScrollLoader } from '@/shared/components/InfiniteScrollLoader';
import { MemoryModal } from '@/shared/components/MemoryModal';
import { CreateAlbumModal } from '@/shared/components/CreateAlbumModal';
import { saveAlbum, downloadAlbumZip } from '@/shared/api';

export function ContentWithCommentPage() {
    const {
        items,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
        currentYearMonth,
        monthTabs,
        selectMonthTab,
    } = useFeed('COMMENT_ONLY');
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();
    const { selectedPhotoIds, togglePhotoSelection, clearSelection } = usePhotoSelection();
    const [isAlbumModalOpen, setIsAlbumModalOpen] = useState(false);
    const [isSelectMode, setIsSelectMode] = useState(false);

    const handleCreateAlbumSubmit = async (albumName: string) => {
        const photoIds = Array.from(selectedPhotoIds);
        await saveAlbum(albumName, photoIds);
        const blob = await downloadAlbumZip(albumName, photoIds);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${albumName}.zip`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        clearSelection();
        setIsSelectMode(false);
    };

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <div className="sticky top-[69px] z-20 bg-white/95 backdrop-blur-md border-b border-gray-200 px-4 sm:px-6 py-2 space-y-2">
                <div className="flex items-center justify-end gap-2">
                    {isSelectMode ? (
                        <div className="flex items-center gap-2">
                            {selectedPhotoIds.size > 0 && (
                                <>
                                    <div className="flex items-center gap-2 bg-blue-100 text-blue-900 px-3 py-1.5 rounded-full text-xs font-bold border border-blue-200">
                                        <span>{selectedPhotoIds.size} 枚選択中</span>
                                        <button
                                            type="button"
                                            onClick={clearSelection}
                                            aria-label="選択をクリア"
                                            className="hover:text-blue-700 p-1 min-h-[36px] min-w-[36px] flex items-center justify-center font-bold"
                                        >
                                            ✕
                                        </button>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => setIsAlbumModalOpen(true)}
                                        className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 active:bg-blue-800 rounded-xl shadow-sm transition-colors flex items-center gap-2 min-h-[44px]"
                                    >
                                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                        </svg>
                                        <span>アルバムを作成</span>
                                    </button>
                                </>
                            )}
                            <button
                                type="button"
                                onClick={() => {
                                    setIsSelectMode(false);
                                    clearSelection();
                                }}
                                className="px-3 py-2 text-xs sm:text-sm font-semibold text-gray-700 hover:text-gray-900 bg-gray-100 hover:bg-gray-200 rounded-xl transition-colors min-h-[44px]"
                            >
                                選択を終了
                            </button>
                        </div>
                    ) : (
                        <button
                            type="button"
                            onClick={() => setIsSelectMode(true)}
                            className="px-3.5 py-2 text-xs sm:text-sm font-semibold text-gray-700 hover:text-gray-900 bg-gray-100 hover:bg-gray-200 rounded-xl transition-colors min-h-[44px]"
                        >
                            写真を選択
                        </button>
                    )}
                </div>

                <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar -mx-4 px-4 sm:mx-0 sm:px-0">
                    <div className="flex items-center gap-2 py-0.5">
                        {monthTabs.map(ym => {
                            const isSelected = ym === currentYearMonth;
                            return (
                                <button
                                    key={ym}
                                    type="button"
                                    onClick={() => selectMonthTab(ym)}
                                    className={`px-4 py-2 text-xs sm:text-sm font-bold rounded-full whitespace-nowrap transition-colors min-h-[40px] flex items-center justify-center ${isSelected
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
            </div>

            <main className="p-4 sm:p-6 lg:p-8">
                {loadingInitial ? (
                    <div className="flex justify-center py-20">
                        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
                    </div>
                ) : (items?.length ?? 0) > 0 ? (
                    <>
                        <FeedGrid
                            items={items || []}
                            filterMode="COMMENT_ONLY"
                            selectedPhotoIds={selectedPhotoIds}
                            onTogglePhotoSelect={isSelectMode ? togglePhotoSelection : undefined}
                            onItemClick={openModal}
                        />
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
                        <p className="text-lg font-medium">表示できるおもいではまだありません</p>
                    </div>
                )}
            </main>

            <MemoryModal
                selectedItem={selectedItem}
                comments={comments}
                commentsLoading={commentsLoading}
                onClose={closeModal}
            />

            <CreateAlbumModal
                isOpen={isAlbumModalOpen}
                selectedCount={selectedPhotoIds.size}
                onClose={() => setIsAlbumModalOpen(false)}
                onSubmit={handleCreateAlbumSubmit}
            />
        </div>
    );
}
