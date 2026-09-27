import { useNavigate } from 'react-router-dom';
import { PhotobookEditor } from '@/shared/components/albums';

export function PhotobookPage() {
    const navigate = useNavigate();

    return (
        <PhotobookEditor
            title="フォトブック用写真を選択"
            previewTitle="フォトブック確認"
            onComplete={() => navigate('/albums')}
            onCancel={() => navigate('/')}
        />
    );
}
