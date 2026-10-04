import { useState } from 'react';
import { CancelButton, PrimaryButton } from '@/shared/components/button';

interface Props {
    isOpen: boolean;
    selectedCount: number;
    defaultAlbumName?: string;
    onClose: () => void;
    onSubmit: (albumName: string) => Promise<void>;
}

export function CreateAlbumModal({ isOpen, selectedCount, defaultAlbumName = '', onClose, onSubmit }: Props) {
    const [albumName, setAlbumName] = useState(defaultAlbumName);
    const [submitting, setSubmitting] = useState(false);

    // isOpen が true になったときに defaultAlbumName をセット
    const [prevIsOpen, setPrevIsOpen] = useState(isOpen);
    if (isOpen !== prevIsOpen) {
        setPrevIsOpen(isOpen);
        if (isOpen) {
            setAlbumName(defaultAlbumName);
        }
    }

    if (!isOpen) return null;

    const handleSubmit = async (e: React.SubmitEvent) => {
        e.preventDefault();
        if (!albumName.trim()) return;
        setSubmitting(true);
        try {
            await onSubmit(albumName.trim());
            setAlbumName('');
            onClose();
        } catch (err) {
            console.error('アルバム作成失敗:', err);
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div className="bg-white rounded-2xl p-6 w-full max-w-md shadow-xl space-y-4">
                <h2 className="text-lg font-bold text-gray-900">アルバムの作成・ダウンロード</h2>
                <p className="text-sm text-gray-600">
                    選択した {selectedCount} 枚の写真でアルバムを作成し、Zipファイルをダウンロードします。
                </p>

                <form onSubmit={handleSubmit} className="space-y-4">
                    <div>
                        <label htmlFor="album-name-input" className="block text-xs font-semibold text-gray-700 mb-1">
                            アルバム名
                        </label>
                        <input
                            id="album-name-input"
                            type="text"
                            value={albumName}
                            onChange={(e) => setAlbumName(e.target.value)}
                            placeholder="例: 2026年夏の旅行"
                            required
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-2">
                        <CancelButton onClick={onClose} disabled={submitting} />
                        <PrimaryButton
                            type="submit"
                            disabled={submitting || !albumName.trim()}
                        >
                            {submitting ? (
                                <>
                                    <span className="animate-spin h-4 w-4 border-2 border-white border-t-transparent rounded-full" />
                                    <span>処理中...</span>
                                </>
                            ) : (
                                <span>作成＆ダウンロード</span>
                            )}
                        </PrimaryButton>
                    </div>
                </form>
            </div>
        </div>
    );
}
