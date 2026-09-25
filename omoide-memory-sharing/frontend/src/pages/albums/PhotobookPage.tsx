import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePhotobookSelection } from '@/pages/albums/hooks/usePhotobookSelection';
import { useAlbumDownloadJob } from '@/pages/albums/hooks/useAlbumDownloadJob';
import { PhotobookSelectionView } from '@/pages/albums/components/PhotobookSelectionView';
import {
    PhotobookPreviewView,
    PhotobookState,
    SelectingState,
    PreviewingState,
    CreatingState,
} from '@/pages/albums/components/PhotobookPreviewView';
import { saveAlbum } from '@/shared/api';

export function PhotobookPage() {
    const navigate = useNavigate();
    const [state, setState] = useState<PhotobookState>(new SelectingState());

    const {
        selectedPhotoIds,
        selectedPhotos,
        period,
        monthTabs,
        maxCount,
        fileNamePrefix,
        setMaxCount,
        togglePhotoSelection,
        fillRemaining,
        replacePhoto,
        selectMonthTab,
        selectDateRange,
    } = usePhotobookSelection();

    const { startDownload } = useAlbumDownloadJob();

    // 1. 選択中（SelectingState）の場合は SelectionView を描画
    if (state instanceof SelectingState) {
        return (
            <PhotobookSelectionView
                selectedPhotoIds={selectedPhotoIds}
                selectedCount={selectedPhotos.length}
                maxCount={maxCount}
                period={period}
                monthTabs={monthTabs}
                onTogglePhoto={togglePhotoSelection}
                onSelectMonthTab={selectMonthTab}
                onSelectDateRange={selectDateRange}
                onChangeMaxCount={setMaxCount}
                onFillRemaining={fillRemaining}
                onConfirm={() => setState(new PreviewingState())}
                onBackToMain={() => navigate('/')}
            />
        );
    }

    // 2. アルバム作成中（CreatingState）の処理
    const handleCreateAlbum = async (albumName: string) => {
        const photoIds = selectedPhotos
            .map(p => p.id)
            .filter((id): id is string => id !== null);
        if (photoIds.length === 0) return;

        setState(new CreatingState('アルバムを作成中...'));
        try {
            const album = await saveAlbum({ albumName, photoIds });
            setState(new CreatingState('ダウンロード準備中...'));
            await startDownload({
                albumId: album.albumId,
                onProgress: (percentage) =>
                    setState(new CreatingState(`ZIPファイル作成中... (${percentage}%)`)),
            });
            setState(new SelectingState());
            navigate('/albums');
        } catch {
            setState(new PreviewingState());
        }
    };

    const handleDeletePhoto = (targetId: string) => {
        togglePhotoSelection(selectedPhotos.find(p => p.id === targetId)!);
    };

    return (
        <PhotobookPreviewView
            selectedPhotos={selectedPhotos}
            maxCount={maxCount}
            defaultAlbumName={fileNamePrefix}
            state={state}
            onDeletePhoto={handleDeletePhoto}
            onReplacePhoto={replacePhoto}
            onBackToSelect={() => setState(new SelectingState())}
            onCreateAlbum={handleCreateAlbum}
        />
    );
}
