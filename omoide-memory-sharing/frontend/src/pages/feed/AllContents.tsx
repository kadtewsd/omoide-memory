import { useState } from 'react';
import { useFeed } from '@/shared/hooks/useFeed';
import { useComments } from '@/shared/hooks/useComments';
import { usePhotoSelection } from '@/shared/hooks/usePhotoSelection';
import { useAlbumDownloadJob } from '@/shared/hooks/useAlbumDownloadJob';
import { Feed, FeedGrid } from '@/shared/components/feed';
import { MemoryModal } from '@/shared/components/MemoryModal';
import { CreateAlbumModal } from '@/shared/components/CreateAlbumModal';
import { saveAlbum } from '@/shared/api';
import { PrimaryButton, SecondaryButton } from '@/shared/components/button';

export function AllContentsPage() {
    const {
        items,
        hasNext,
        totalCount,
        loadingInitial,
        loadingMore,
        loadMore,
        currentYearMonth,
        monthTabs,
        selectMonthTab,
    } = useFeed('ALL');
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();
    const { selectedPhotoIds, togglePhotoSelection, clearSelection } = usePhotoSelection();
    const { startDownload } = useAlbumDownloadJob();
    const [isAlbumModalOpen, setIsAlbumModalOpen] = useState(false);
    const [isSelectMode, setIsSelectMode] = useState(false);

    const handleCreateAlbumSubmit = async (albumName: string) => {
        const photoIds = Array.from(selectedPhotoIds);
        const album = await saveAlbum({ albumName, photoIds });
        clearSelection();
        setIsSelectMode(false);
        setIsAlbumModalOpen(false);
        await startDownload({ albumId: album.albumId });
    };

    return (
        <>
            <Feed
                items={items}
                hasNext={hasNext}
                totalCount={totalCount}
                totalCountLabel="該当月の思い出"
                totalCountUnit="件"
                loadingInitial={loadingInitial}
                loadingMore={loadingMore}
                loadMore={loadMore}
                monthTabs={monthTabs}
                selectedYearMonth={currentYearMonth}
                onSelectMonthTab={selectMonthTab}
                headerStatus={
                    isSelectMode && (
                        <div className="flex items-center gap-2 bg-blue-100 text-blue-900 px-3 py-1.5 rounded-full text-xs font-bold border border-blue-200">
                            <span>{selectedPhotoIds.size} 枚選択中</span>
                            {selectedPhotoIds.size > 0 && (
                                <button
                                    type="button"
                                    onClick={clearSelection}
                                    aria-label="選択をクリア"
                                    className="hover:text-blue-700 p-1 min-h-[36px] min-w-[36px] flex items-center justify-center font-bold cursor-pointer"
                                >
                                    ✕
                                </button>
                            )}
                        </div>
                    )
                }
                headerActions={
                    isSelectMode ? (
                        <>
                            {selectedPhotoIds.size > 0 && (
                                <PrimaryButton onClick={() => setIsAlbumModalOpen(true)}>
                                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                    </svg>
                                    <span>アルバムを作成</span>
                                </PrimaryButton>
                            )}
                            <SecondaryButton
                                onClick={() => {
                                    setIsSelectMode(false);
                                    clearSelection();
                                }}
                                className="px-3 py-2 text-xs sm:text-sm"
                            >
                                選択を終了
                            </SecondaryButton>
                        </>
                    ) : (
                        <SecondaryButton
                            onClick={() => setIsSelectMode(true)}
                            className="px-3.5 py-2 text-xs sm:text-sm"
                        >
                            写真を選択
                        </SecondaryButton>
                    )
                }
            >
                <FeedGrid
                    items={items || []}
                    filterMode="ALL"
                    selectedPhotoIds={selectedPhotoIds}
                    onTogglePhotoSelect={isSelectMode ? togglePhotoSelection : undefined}
                    onItemClick={openModal}
                />
            </Feed>

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
        </>
    );
}
