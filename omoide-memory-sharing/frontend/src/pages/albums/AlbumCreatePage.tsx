import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { saveAlbum } from '@/shared/api';
import { CancelButton, PrimaryButton } from '@/shared/components/button';
import { PhotobookEditor } from './components';

type CreatePageState =
    | { value: 'input_name' }
    | { value: 'creating'; albumName: string }
    | { value: 'select_photos'; albumId: string; albumName: string };

export function AlbumCreatePage() {
    const navigate = useNavigate();
    const [state, setState] = useState<CreatePageState>({ value: 'input_name' });
    const [nameInput, setNameInput] = useState('');
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const handleBack = () => {
        navigate('/pages/albums');
    };

    const handleCreateAlbum = async (e: React.FormEvent) => {
        e.preventDefault();
        const trimmed = nameInput.trim();
        if (!trimmed) return;

        setErrorMessage(null);
        setState({ value: 'creating', albumName: trimmed });

        try {
            const created = await saveAlbum({
                albumName: trimmed,
                photoIds: [],
                status: 'DRAFT',
            });
            setState({
                value: 'select_photos',
                albumId: created.albumId,
                albumName: created.albumName,
            });
        } catch (err) {
            console.error('アルバムの作成に失敗しました:', err);
            setErrorMessage('アルバムの作成に失敗しました。もう一度お試しください。');
            setState({ value: 'input_name' });
        }
    };

    if (state.value === 'select_photos') {
        return (
            <PhotobookEditor
                albumId={state.albumId}
                initialAlbumName={state.albumName}
                title={`写真を選択: ${state.albumName}`}
                previewTitle={`アルバム確認: ${state.albumName}`}
                onComplete={handleBack}
                onCancel={handleBack}
            />
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
            <div className="sm:mx-auto sm:w-full sm:max-w-md">
                <div className="bg-white py-8 px-4 shadow-xl sm:rounded-2xl sm:px-10 space-y-6 border border-gray-100">
                    <div>
                        <h2 className="text-xl font-bold text-gray-900 tracking-tight">
                            新しいアルバムを作成
                        </h2>
                        <p className="mt-1 text-sm text-gray-500">
                            アルバム名を入力して、写真の選択に進みましょう。
                        </p>
                    </div>

                    {errorMessage && (
                        <div className="p-3 bg-red-50 border border-red-200 rounded-xl text-xs text-red-600 font-medium">
                            {errorMessage}
                        </div>
                    )}

                    <form onSubmit={handleCreateAlbum} className="space-y-5">
                        <div>
                            <label htmlFor="album-name" className="block text-xs font-semibold text-gray-700 mb-1">
                                アルバム名
                            </label>
                            <input
                                id="album-name"
                                type="text"
                                value={nameInput}
                                onChange={(e) => setNameInput(e.target.value)}
                                placeholder="例: 2026年 夏の思い出"
                                required
                                autoFocus
                                disabled={state.value === 'creating'}
                                className="w-full px-3.5 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                            />
                        </div>

                        <div className="flex justify-end gap-3 pt-2">
                            <CancelButton
                                onClick={handleBack}
                                disabled={state.value === 'creating'}
                            />
                            <PrimaryButton
                                type="submit"
                                disabled={state.value === 'creating' || !nameInput.trim()}
                            >
                                {state.value === 'creating' ? (
                                    <>
                                        <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                                        <span>作成中...</span>
                                    </>
                                ) : (
                                    <>
                                        <span>写真を選択へ進む</span>
                                        <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                        </svg>
                                    </>
                                )}
                            </PrimaryButton>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}

