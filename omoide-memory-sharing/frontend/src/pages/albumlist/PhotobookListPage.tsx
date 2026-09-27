import { useState } from 'react';
import { useComments } from '@/shared/hooks/useComments';
import { AlbumGrid, PhotobookEditor } from '@/shared/components/albums';
import { MemoryModal } from '@/shared/components/MemoryModal';
import { AlbumDetail } from '@/shared/types';
import { PHOTOBOOK_ABSOLUTE_MAX } from '@/shared/hooks/usePhotobookSelection';

class ViewingListState {}

class EditingAlbumState {
    constructor(readonly albumDetail: AlbumDetail) {}
}

type PhotobookListPageState = ViewingListState | EditingAlbumState;

export function PhotobookListPage() {
    const [pageState, setPageState] = useState<PhotobookListPageState>(new ViewingListState());
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();

    if (pageState instanceof EditingAlbumState) {
        return (
            <PhotobookEditor
                title={`アルバム編集: ${pageState.albumDetail.albumName}`}
                previewTitle={`アルバム確認: ${pageState.albumDetail.albumName}`}
                initialPhotos={pageState.albumDetail.photos}
                initialAlbumName={pageState.albumDetail.albumName}
                initialMaxCount={
                    pageState.albumDetail.photos.length > 0
                        ? Math.max(pageState.albumDetail.photos.length, PHOTOBOOK_ABSOLUTE_MAX)
                        : PHOTOBOOK_ABSOLUTE_MAX
                }
                onComplete={() => setPageState(new ViewingListState())}
                onCancel={() => setPageState(new ViewingListState())}
            />
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <main className="p-4 sm:p-6 lg:p-8">
                <AlbumGrid
                    onPhotoClick={openModal}
                    onEditAlbum={(albumDetail) => setPageState(new EditingAlbumState(albumDetail))}
                />
            </main>
            <MemoryModal
                selectedItem={selectedItem}
                comments={comments}
                commentsLoading={commentsLoading}
                onClose={closeModal}
            />
        </div>
    );
}
