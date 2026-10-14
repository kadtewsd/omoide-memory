import { useState } from 'react';
import { PhotobookPeriod, AlbumContentItem } from '@/shared/types';
import { usePhotobookSelection } from '@/shared/hooks/usePhotobookSelection';
import { useAlbumDownloadJob } from '@/shared/hooks/useAlbumDownloadJob';
import { PhotobookSelectionView } from './PhotobookSelectionView';
import { PhotobookPreviewView } from './PhotobookPreviewView';
import { PhotobookState } from './types';
import { confirmAlbum } from '@/shared/api';

export interface PhotobookEditorProps {
    albumId: string;
    initialPhotos?: AlbumContentItem[];
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
        const randomPhotoIds = new Set(
            selectedPhotos.filter(p => p.isRandom && p.photoId !== null).map(p => p.photoId as string)
        );
        return (
            <PhotobookSelectionView
                selectedPhotoIds={selectedPhotoIds}
                randomPhotoIds={randomPhotoIds}
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
        const targetPhoto = selectedPhotos.find(p => p.photoId === targetId);
        if (targetPhoto) {
            setSelectedPhotos(selectedPhotos.filter(p => p.photoId !== targetId));
        }
    };

    const handleReplacePhoto = async (targetId: string) => {
        const targetPhoto = selectedPhotos.find(p => p.photoId === targetId);
        if (!targetPhoto) return;
        const result = await replacePhoto(targetPhoto);
        if (result) {
            setState({ value: 'replace-confirming', oldPhoto: result.oldPhoto, newPhoto: result.newPhoto });
        }
    };

    const handleConfirmReplace = () => {
        if (state.value !== 'replace-confirming') return;
        const newPhoto = state.newPhoto;
        const nextPhotos = selectedPhotos
            .map(p => (p.photoId === state.oldPhoto.photoId ? newPhoto : p))
            .sort((a, b) => {
                const aTime = a.captureTime;
                const bTime = b.captureTime;
                if (!aTime && !bTime) return 0;
                if (!aTime) return 1;
                if (!bTime) return -1;
                return aTime < bTime ? -1 : aTime > bTime ? 1 : 0;
            });
        setSelectedPhotos(nextPhotos);
        setState({ value: 'previewing' });
    };

    const handleReplaceAgain = async () => {
        if (state.value !== 'replace-confirming') return;
        const currentOldPhoto = state.oldPhoto;
        const result = await replacePhoto(state.newPhoto);
        if (result) {
            setState({ value: 'replace-confirming', oldPhoto: currentOldPhoto, newPhoto: result.newPhoto });
        }
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
            onReplaceAgain={handleReplaceAgain}
            onBackToSelect={() => setState({ value: 'selecting' })}
            onCreateAlbum={handleConfirmAlbum}
        />
    );
}
