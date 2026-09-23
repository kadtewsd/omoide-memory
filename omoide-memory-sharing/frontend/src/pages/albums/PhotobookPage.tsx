import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePhotobookSelection } from '@/pages/albums/hooks/usePhotobookSelection';
import { PhotobookSelectionView } from '@/pages/albums/components/PhotobookSelectionView';
import { PhotobookPreviewView } from './components/PhotobookPreviewView';
import { downloadAlbumZip } from '@/shared/api';

type PhotobookPhase = 'select' | 'preview';

export function PhotobookPage() {
    const navigate = useNavigate();
    const [phase, setPhase] = useState<PhotobookPhase>('select');

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

    const handleDownload = async () => {
        const photoIds = selectedPhotos
            .map(p => p.id)
            .filter((id): id is string => id !== null);
        if (photoIds.length === 0) return;

        const blob = await downloadAlbumZip(fileNamePrefix, photoIds);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${fileNamePrefix}.zip`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
    };

    const handleDeletePhoto = (targetId: string) => {
        togglePhotoSelection(selectedPhotos.find(p => p.id === targetId)!);
    };

    if (phase === 'select') {
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
                onConfirm={() => setPhase('preview')}
                onBackToMain={() => navigate('/')}
            />
        );
    }

    return (
        <PhotobookPreviewView
            selectedPhotos={selectedPhotos}
            maxCount={maxCount}
            currentYearMonth={period.type === 'MONTH_TAB' ? period.yearMonth : period.fromYearMonth}
            onDeletePhoto={handleDeletePhoto}
            onReplacePhoto={replacePhoto}
            onBackToSelect={() => setPhase('select')}
            onDownloadZip={handleDownload}
        />
    );
}
