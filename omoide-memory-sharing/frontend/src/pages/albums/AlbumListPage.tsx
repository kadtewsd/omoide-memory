import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useComments } from '@/shared/hooks/useComments';
import { AlbumGrid, PhotobookEditor } from './components';
import { MemoryModal } from '@/shared/components/MemoryModal';
import { PrimaryButton } from '@/shared/components/button';
import { AlbumDetail } from '@/shared/types';
import { PHOTOBOOK_ABSOLUTE_MAX } from '@/shared/hooks/usePhotobookSelection';

export function AlbumListPage() {
    const navigate = useNavigate();
    const [editingAlbum, setEditingAlbum] = useState<AlbumDetail | null>(null);
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();

    if (editingAlbum) {
        return (
            <PhotobookEditor
                albumId={editingAlbum.albumId}
                title={`アルバム編集: ${editingAlbum.albumName}`}
                previewTitle={`アルバム確認: ${editingAlbum.albumName}`}
                initialPhotos={editingAlbum.photos}
                initialAlbumName={editingAlbum.albumName}
                initialMaxCount={
                    editingAlbum.photos.length > 0
                        ? Math.max(editingAlbum.photos.length, PHOTOBOOK_ABSOLUTE_MAX)
                        : PHOTOBOOK_ABSOLUTE_MAX
                }
                onComplete={() => setEditingAlbum(null)}
                onCancel={() => setEditingAlbum(null)}
            />
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <main className="p-4 sm:p-6 lg:p-8 space-y-6">
                <div className="flex items-center justify-between">
                    <h1 className="text-xl font-bold text-gray-900">アルバム一覧</h1>
                    <PrimaryButton onClick={() => navigate('/pages/albums/new')}>
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        <span>新規アルバム作成</span>
                    </PrimaryButton>
                </div>
                <AlbumGrid
                    onPhotoClick={openModal}
                    onEditAlbum={(albumDetail) => setEditingAlbum(albumDetail)}
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
