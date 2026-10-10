import { useState } from 'react';
import { MemoryFeedItem, PhotobookPeriod } from '@/shared/types';
import { usePhotobookSelection } from '@/shared/hooks/usePhotobookSelection';
import { useAlbumDownloadJob } from '@/shared/hooks/useAlbumDownloadJob';
import { PhotobookSelectionView } from './PhotobookSelectionView';
import { PhotobookPreviewView } from './PhotobookPreviewView';
import { PhotobookState } from './types';
import { confirmAlbum } from '@/shared/api';

export interface PhotobookEditorProps {
    albumId: string;
    initialPhotos?: MemoryFeedItem[];
    initialAlbumName: string;
    initialMaxCount?: number;
    initialPeriod?: PhotobookPeriod;
    title: string;
    previewTitle: string;
    onComplete: () => void;
    onCancel: () => void;
}

/**
 * フォトブック・アルバム作成および編集の共通ワークフローコンポーネント。
 * 写真選択フェーズ（SelectionView）とプレビュー確認フェーズ（PreviewView）を統括し、
 * アルバムの保存・更新・ZIPダウンロード完了までを一貫して制御する。
 */
export function PhotobookEditor({
    albumId,
    initialPhotos,
    initialAlbumName,
    initialMaxCount,
    initialPeriod,
    title,
    previewTitle,
    onComplete,
    onCancel,
}: PhotobookEditorProps) {
    const [state, setState] = useState<PhotobookState>({ value: 'selecting' });

    const {
        selectedPhotoIds,
        selectedPhotos,
        savingPhotoIds,
        period,
        monthTabs,
        maxCount,
        fileNamePrefix,
        isSelectingRandom,
        setMaxCount,
        setSelectedPhotos,
        togglePhotoSelection,
        fillRemaining,
        replacePhoto,
        selectMonthTab,
        selectDateRange,
    } = usePhotobookSelection({
        albumId,
        initialPhotos,
        initialAlbumName,
        initialMaxCount,
        initialPeriod,
    });

    const { startDownload } = useAlbumDownloadJob();

    if (state.value === 'selecting') {
        return (
            <PhotobookSelectionView
                selectedPhotoIds={selectedPhotoIds}
                selectedCount={selectedPhotos.length}
                savingPhotoIds={savingPhotoIds}
                maxCount={maxCount}
                period={period}
                monthTabs={monthTabs}
                title={title}
                isSelectingRandom={isSelectingRandom}
                onTogglePhoto={togglePhotoSelection}
                onSelectMonthTab={selectMonthTab}
                onSelectDateRange={selectDateRange}
                onChangeMaxCount={setMaxCount}
                onFillRemaining={fillRemaining}
                onConfirm={() => setState({ value: 'previewing' })}
                onBackToMain={onCancel}
            />
        );
    }

    const handleConfirmAlbum = async () => {
        if (selectedPhotos.length === 0) return;

        setState({ value: 'confirming', message: 'アルバムを確定中...' });
        try {
            await confirmAlbum(albumId);

            setState({ value: 'confirming', message: 'ダウンロード準備中...' });
            await startDownload({
                albumId,
                onProgress: (percentage) =>
                    setState({ value: 'confirming', message: `ZIPファイル作成中... (${percentage}%)` }),
            });
            setState({ value: 'selecting' });
            onComplete();
        } catch {
            setState({ value: 'previewing' });
        }
    };

    const handleDeletePhoto = (targetId: string) => {
        const targetPhoto = selectedPhotos.find(p => p.id === targetId);
        if (targetPhoto) {
            togglePhotoSelection(targetPhoto);
        }
    };

    const handleReplacePhoto = async (targetId: string) => {
        const result = await replacePhoto(targetId);
        if (result) {
            setState({ value: 'replace-confirming', oldPhoto: result.oldPhoto, newPhoto: result.newPhoto });
        }
    };

    const handleConfirmReplace = () => {
        if (state.value !== 'replace-confirming') return;
        const newPhoto = state.newPhoto;
        const nextPhotos = selectedPhotos
            .map(p => (p.id === state.oldPhoto.id ? newPhoto : p))
            .sort((a, b) => {
                const aTime = a.captureTime ?? a.commentedAt;
                const bTime = b.captureTime ?? b.commentedAt;
                return aTime < bTime ? -1 : aTime > bTime ? 1 : 0;
            });
        setSelectedPhotos(nextPhotos);
        setState({ value: 'previewing' });
    };

    const handleCancelReplace = () => {
        setState({ value: 'previewing' });
    };

    return (
        <PhotobookPreviewView
            selectedPhotos={selectedPhotos}
            maxCount={maxCount}
            defaultAlbumName={fileNamePrefix}
            title={previewTitle}
            state={state}
            isSelectingRandom={isSelectingRandom}
            onDeletePhoto={handleDeletePhoto}
            onReplacePhoto={handleReplacePhoto}
            onConfirmReplace={handleConfirmReplace}
            onCancelReplace={handleCancelReplace}
            onBackToSelect={() => setState({ value: 'selecting' })}
            onCreateAlbum={handleConfirmAlbum}
        />
    );
}
