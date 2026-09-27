import { useState } from 'react';
import { MemoryFeedItem } from '@/shared/types';
import { usePhotobookSelection } from '@/shared/hooks/usePhotobookSelection';
import { useAlbumDownloadJob } from '@/shared/hooks/useAlbumDownloadJob';
import { PhotobookSelectionView } from './PhotobookSelectionView';
import { PhotobookPreviewView } from './PhotobookPreviewView';
import {
    PhotobookState,
    SelectingState,
    PreviewingState,
    CreatingState,
} from './types';
import { saveAlbum } from '@/shared/api';

export interface PhotobookEditorProps {
    initialPhotos?: MemoryFeedItem[];
    initialAlbumName?: string;
    initialMaxCount?: number;
    title: string;
    previewTitle: string;
    onComplete: () => void;
    onCancel: () => void;
}

/**
 * フォトブック・アルバム作成および編集の共通ワークフローコンポーネント。
 * 写真選択フェーズ（SelectionView）とプレビュー確認フェーズ（PreviewView）を統括し、
 * アルバムの保存・ZIPダウンロード完了までを一貫して制御する。
 */
export function PhotobookEditor({
    initialPhotos,
    initialAlbumName,
    initialMaxCount,
    title,
    previewTitle,
    onComplete,
    onCancel,
}: PhotobookEditorProps) {
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
    } = usePhotobookSelection({
        initialPhotos,
        initialAlbumName,
        initialMaxCount,
    });

    const { startDownload } = useAlbumDownloadJob();

    if (state instanceof SelectingState) {
        return (
            <PhotobookSelectionView
                selectedPhotoIds={selectedPhotoIds}
                selectedCount={selectedPhotos.length}
                maxCount={maxCount}
                period={period}
                monthTabs={monthTabs}
                title={title}
                onTogglePhoto={togglePhotoSelection}
                onSelectMonthTab={selectMonthTab}
                onSelectDateRange={selectDateRange}
                onChangeMaxCount={setMaxCount}
                onFillRemaining={fillRemaining}
                onConfirm={() => setState(new PreviewingState())}
                onBackToMain={onCancel}
            />
        );
    }

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
            onComplete();
        } catch {
            setState(new PreviewingState());
        }
    };

    const handleDeletePhoto = (targetId: string) => {
        const targetPhoto = selectedPhotos.find(p => p.id === targetId);
        if (targetPhoto) {
            togglePhotoSelection(targetPhoto);
        }
    };

    return (
        <PhotobookPreviewView
            selectedPhotos={selectedPhotos}
            maxCount={maxCount}
            defaultAlbumName={fileNamePrefix}
            title={previewTitle}
            state={state}
            onDeletePhoto={handleDeletePhoto}
            onReplacePhoto={replacePhoto}
            onBackToSelect={() => setState(new SelectingState())}
            onCreateAlbum={handleCreateAlbum}
        />
    );
}
