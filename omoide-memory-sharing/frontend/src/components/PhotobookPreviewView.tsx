import { useState } from 'react';
import { MemoryFeedItem } from '../types';
import { ContentNotFound } from './ContentNotFound';

/** アクションシートで選択中の写真ID（null = 非表示） */
type ActionTargetId = string | null;

interface Props {
    selectedPhotos: MemoryFeedItem[];
    maxCount: number;
    currentYearMonth: string;
    onDeletePhoto: (targetId: string) => void;
    onReplacePhoto: (targetId: string) => Promise<void>;
    onBackToSelect: () => void;
    onDownloadZip: () => Promise<void>;
}

/**
 * フォトブックプレビューフェーズ。
 * 選択済み写真を追加順で全件グリッド表示し、タップでアクションシートを表示する。
 * 削除・差し替え・ZIP ダウンロードの操作をここから行う。
 */
export function PhotobookPreviewView({
    selectedPhotos,
    maxCount,
    onDeletePhoto,
    onReplacePhoto,
    onBackToSelect,
    onDownloadZip,
}: Props) {
    const [actionTargetId, setActionTargetId] = useState<ActionTargetId>(null);
    const [isDownloading, setIsDownloading] = useState(false);

    const handlePhotoTap = (photoId: string) => {
        setActionTargetId(photoId);
    };

    const handleDelete = () => {
        if (actionTargetId === null) return;
        onDeletePhoto(actionTargetId);
        setActionTargetId(null);
    };

    const handleReplace = async () => {
        if (actionTargetId === null) return;
        await onReplacePhoto(actionTargetId);
        setActionTargetId(null);
    };

    const handleDownload = async () => {
        setIsDownloading(true);
        try {
            await onDownloadZip();
        } finally {
            setIsDownloading(false);
        }
    };

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-0 z-10 bg-white/95 backdrop-blur-md shadow-sm border-b border-gray-200 px-4 sm:px-6 py-3.5 space-y-3">
                <div className="flex items-center justify-between gap-3 flex-wrap">
                    <div className="flex items-center gap-3">
                        <button
                            type="button"
                            onClick={onBackToSelect}
                            className="p-2 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-xl transition-colors"
                            aria-label="選択に戻る"
                        >
                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                            </svg>
                        </button>
                        <h2 className="text-base sm:text-lg font-bold text-gray-900">
                            フォトブック確認
                        </h2>
                        <span className="text-sm font-semibold text-blue-700 bg-blue-50 px-3 py-1.5 rounded-full border border-blue-200">
                            {selectedPhotos.length} 枚 / {maxCount}枚
                        </span>
                    </div>

                    <button
                        type="button"
                        onClick={handleDownload}
                        disabled={isDownloading || selectedPhotos.length === 0}
                        className="px-4 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:bg-gray-300 disabled:cursor-not-allowed rounded-xl shadow-sm transition-colors min-h-[44px] flex items-center gap-2"
                    >
                        {isDownloading ? (
                            <>
                                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" />
                                <span>ダウンロード中...</span>
                            </>
                        ) : (
                            <>
                                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                </svg>
                                <span>ZIP ダウンロード</span>
                            </>
                        )}
                    </button>
                </div>

                <p className="text-xs text-gray-500">
                    写真をタップすると「削除」または「差し替え」ができます
                </p>
            </header>

            <main className="p-4 sm:p-6 lg:p-8">
                {selectedPhotos.length > 0 ? (
                    <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-1 sm:gap-2">
                        {selectedPhotos.map(photo => (
                            <div
                                key={photo.id}
                                className="relative rounded-2xl overflow-hidden cursor-pointer bg-gray-100 aspect-square active:scale-95 transition-transform"
                                onClick={() => photo.id !== null && handlePhotoTap(photo.id)}
                                role="button"
                                aria-label="写真の操作"
                            >
                                {photo.contentBase64 ? (
                                    <img
                                        src={photo.contentBase64}
                                        alt="選択済み写真"
                                        className="w-full h-full object-cover"
                                        loading="lazy"
                                    />
                                ) : (
                                    <ContentNotFound />
                                )}
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                        <p className="text-lg font-medium">写真が選択されていません</p>
                        <button
                            type="button"
                            onClick={onBackToSelect}
                            className="mt-4 px-4 py-2 text-sm font-semibold text-blue-600 hover:underline"
                        >
                            選択に戻る
                        </button>
                    </div>
                )}
            </main>

            {/* アクションシート（モーダル） */}
            {actionTargetId !== null && (
                <div
                    className="fixed inset-0 z-50 bg-black/50 flex items-end sm:items-center justify-center"
                    onClick={() => setActionTargetId(null)}
                >
                    <div
                        className="bg-white w-full sm:w-80 rounded-t-2xl sm:rounded-2xl shadow-xl p-4 space-y-2"
                        onClick={e => e.stopPropagation()}
                    >
                        <p className="text-center text-sm font-semibold text-gray-700 pb-2">
                            この写真をどうしますか？
                        </p>
                        <button
                            type="button"
                            onClick={handleReplace}
                            className="w-full px-4 py-3 text-sm font-semibold text-blue-700 bg-blue-50 hover:bg-blue-100 rounded-xl transition-colors min-h-[48px]"
                        >
                            同月の別の写真に差し替え
                        </button>
                        <button
                            type="button"
                            onClick={handleDelete}
                            className="w-full px-4 py-3 text-sm font-semibold text-red-600 bg-red-50 hover:bg-red-100 rounded-xl transition-colors min-h-[48px]"
                        >
                            この写真を削除
                        </button>
                        <button
                            type="button"
                            onClick={() => setActionTargetId(null)}
                            className="w-full px-4 py-3 text-sm font-semibold text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-xl transition-colors min-h-[48px]"
                        >
                            キャンセル
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
