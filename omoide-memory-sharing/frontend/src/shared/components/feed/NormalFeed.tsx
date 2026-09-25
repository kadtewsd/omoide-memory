import { useState } from 'react';
import { useFeed } from '@/shared/hooks/useFeed';
import { useComments } from '@/shared/hooks/useComments';
import { usePhotoSelection } from '@/shared/hooks/usePhotoSelection';
import { FeedGrid } from '@/shared/components/FeedGrid';
import { MemoryModal } from '@/shared/components/MemoryModal';
import { CreateAlbumModal } from '@/shared/components/CreateAlbumModal';
import { saveAlbum, downloadAlbumZip } from '@/shared/api';
import { NormalFeedProps } from './types';
import { FeedMonthTabs } from './FeedMonthTabs';
import { FeedContentContainer } from './FeedContentContainer';

/**
 * 通常モードのフィードコンポーネント。
 * 全件・コメント付き一覧の閲覧、コメントモーダル表示、簡易アルバム作成機能を提供する。
 */
export function NormalFeed({ filterMode }: NormalFeedProps) {
    const {
        items,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
        currentYearMonth,
        monthTabs,
        selectMonthTab,
    } = useFeed(filterMode);
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
                <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                        {isSelectMode && (
                            <div className="flex items-center gap-2 bg-blue-100 text-blue-900 px-3 py-1.5 rounded-full text-xs font-bold border border-blue-200">
                                <span>{selectedPhotoIds.size} 枚選択中</span>
                                {selectedPhotoIds.size > 0 && (
                                    <button
                                        type="button"
                                        onClick={clearSelection}
                                        aria-label="選択をクリア"
                                        className="hover:text-blue-700 p-1 min-h-[36px] min-w-[36px] flex items-center justify-center font-bold"
                                    >
                                        ✕
                                    </button>
                                )}
                            </div>
                        )}
                    </div>

                    <div className="flex items-center gap-2">
                        {isSelectMode ? (
                            <>
                                {selectedPhotoIds.size > 0 && (
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
                            </>
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
                </div>

                <FeedMonthTabs
                    monthTabs={monthTabs}
                    selectedYearMonth={currentYearMonth}
                    onSelectMonthTab={selectMonthTab}
                />
            </div>

            <FeedContentContainer
                loadingInitial={loadingInitial}
                hasItems={(items?.length ?? 0) > 0}
                hasNext={hasNext}
                loadingMore={loadingMore}
                loadMore={loadMore}
                emptyMessage="表示できるおもいではまだありません"
            >
                <FeedGrid
                    items={items || []}
                    filterMode={filterMode}
                    selectedPhotoIds={selectedPhotoIds}
                    onTogglePhotoSelect={isSelectMode ? togglePhotoSelection : undefined}
                    onItemClick={openModal}
                />
            </FeedContentContainer>

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
