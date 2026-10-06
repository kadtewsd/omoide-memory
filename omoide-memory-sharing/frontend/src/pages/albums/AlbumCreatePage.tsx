import { useNavigate } from 'react-router-dom';
import { PhotobookEditor } from './components';

export function AlbumCreatePage() {
    const navigate = useNavigate();

    const handleBack = () => {
        navigate('/pages/albums');
    };

    return (
        <PhotobookEditor
            title="フォトブック用写真を選択"
            previewTitle="フォトブック確認"
            onComplete={handleBack}
            onCancel={handleBack}
        />
    );
}
