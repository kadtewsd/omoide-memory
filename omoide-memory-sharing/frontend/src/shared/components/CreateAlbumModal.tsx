import { useState } from 'react';

interface Props {
    isOpen: boolean;
    selectedCount: number;
    onClose: () => void;
    onSubmit: (albumName: string) => Promise<void>;
}

export function CreateAlbumModal({ isOpen, selectedCount, onClose, onSubmit }: Props) {
    const [albumName, setAlbumName] = useState('');
    const [submitting, setSubmitting] = useState(false);

    if (!isOpen) return null;

    const handleSubmit = async (e: React.FormEvent) => {
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
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={submitting}
                            className="px-4 py-2.5 text-sm font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 active:bg-gray-300 rounded-xl transition-colors min-h-[44px]"
                        >
                            キャンセル
                        </button>
                        <button
                            type="submit"
                            disabled={submitting || !albumName.trim()}
                            className="px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:opacity-50 rounded-xl transition-colors flex items-center justify-center gap-2 min-h-[44px]"
                        >
                            {submitting ? (
                                <>
                                    <span className="animate-spin h-4 w-4 border-2 border-white border-t-transparent rounded-full" />
                                    <span>処理中...</span>
                                </>
                            ) : (
                                <span>作成＆ダウンロード</span>
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
