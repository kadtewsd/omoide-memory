import { useComments } from '../hooks/useComments';
import { AlbumGrid } from '../components/AlbumGrid';
import { MemoryModal } from '../components/MemoryModal';

export function PhotobookListPage() {
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <main className="p-4 sm:p-6 lg:p-8">
                <AlbumGrid onPhotoClick={openModal} />
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
