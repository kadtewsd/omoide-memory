import { useState } from 'react';
import { MemoryFeedItem } from '@/shared/types';
import { ContentNotFound } from '@/shared/components/ContentNotFound';
import { getImageUrl } from '@/shared/api';
import { CancelButton, PrimaryButton } from '@/shared/components/button';
import { PhotobookState } from './types';

export interface PhotobookPreviewViewProps {
    selectedPhotos: MemoryFeedItem[];
    maxCount: number;
    defaultAlbumName: string;
    title: string;
    state: PhotobookState;
    isSelectingRandom: boolean;
    onDeletePhoto: (targetId: string) => void;
    onReplacePhoto: (targetId: string) => Promise<void>;
    onConfirmReplace: () => void;
    onCancelReplace: () => void;
    onBackToSelect: () => void;
    onCreateAlbum: () => Promise<void>;
}

/**
 * フォトブック・アルバムプレビューフェーズ。
 * 選択済み写真を追加順で全件グリッド表示し、タップでアクションシートを表示する。
 * 写真の削除・差し替え、およびアルバムの確定・ZIPダウンロードを実行する。
 */
export function PhotobookPreviewView({
    selectedPhotos,
    maxCount,
    title,
    state,
    isSelectingRandom,
    onDeletePhoto,
    onReplacePhoto,
    onConfirmReplace,
    onCancelReplace,
    onBackToSelect,
    onCreateAlbum,
}: PhotobookPreviewViewProps) {
    const [actionTargetId, setActionTargetId] = useState<string | null>(null);

    const isInteractable = state.value !== 'confirming' && state.value !== 'replace-confirming';

    const handlePhotoTap = (photoId: string) => {
        if (!isInteractable) return;
        setActionTargetId(photoId);
    };

    const handleConfirm = async () => {
        await onCreateAlbum();
    };

    const handleDelete = () => {
        if (actionTargetId === null) return;
        onDeletePhoto(actionTargetId);
        setActionTargetId(null);
    };

    const handleReplace = async () => {
        if (actionTargetId === null) return;
        const targetId = actionTargetId;
        setActionTargetId(null);
        await onReplacePhoto(targetId);
    };

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md shadow-sm border-b border-gray-200 px-4 sm:px-6 py-3.5 space-y-3">
                <div className="flex items-center justify-between gap-3 flex-wrap">
                    <div className="flex items-center gap-3">
                        <button
                            type="button"
                            onClick={onBackToSelect}
                            disabled={!isInteractable}
                            className="p-2 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-600 hover:text-gray-900 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors cursor-pointer"
                            aria-label="選択に戻る"
                        >
                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                            </svg>
                        </button>
                        <h2 className="text-base sm:text-lg font-bold text-gray-900">
                            {title}
                        </h2>
                        <span className="text-sm font-semibold text-blue-700 bg-blue-50 px-3 py-1.5 rounded-full border border-blue-200">
                            {selectedPhotos.length} 枚 / {maxCount}枚
                        </span>
                    </div>

                    {state.value === 'confirming' ? (
                        <div className="px-4 py-2 text-sm font-semibold text-blue-700 bg-blue-50 border border-blue-200 rounded-xl min-h-[44px] flex items-center gap-2">
                            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600" />
                            <span>{state.message}</span>
                        </div>
                    ) : (
                        <PrimaryButton
                            onClick={handleConfirm}
                            disabled={selectedPhotos.length === 0 || !isInteractable}
                        >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                            </svg>
                            <span>アルバムを確定・ダウンロード</span>
                        </PrimaryButton>
                    )}
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
                                className={`relative rounded-2xl overflow-hidden cursor-pointer bg-gray-100 aspect-square active:scale-95 transition-transform ${photo.isRandom ? 'ring-2 ring-emerald-400' : ''}`}
                                onClick={() => photo.id !== null && handlePhotoTap(photo.id)}
                                role="button"
                                aria-label="写真の操作"
                            >
                                {photo.id ? (
                                    <img
                                        src={getImageUrl(photo.id)}
                                        alt="選択済み写真"
                                        className="w-full h-full object-cover"
                                        loading="lazy"
                                    />
                                ) : (
                                    <ContentNotFound />
                                )}
                                {photo.isRandom && (
                                    <div className="absolute bottom-0 left-0 right-0 flex items-center justify-center gap-0.5 bg-emerald-500/80 backdrop-blur-sm py-0.5">
                                        {/* ✨ U+2728 SPARKLES */}
                                        <span className="text-[10px] leading-none">✨</span>
                                        <span className="text-[9px] font-bold text-white leading-none tracking-tight">Random Pick!</span>
                                    </div>
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
                        <CancelButton
                            onClick={() => setActionTargetId(null)}
                            className="w-full py-3 min-h-[48px]"
                        />
                    </div>
                </div>
            )}

            {/* 差し替え確認ダイアログ */}
            {state.value === 'replace-confirming' && (
                <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-5 space-y-4">
                        <h3 className="text-base font-bold text-gray-900 text-center">写真を差し替えますか？</h3>
                        <div className="flex items-center gap-3">
                            <div className="flex-1 space-y-1">
                                <p className="text-[10px] font-semibold text-gray-500 text-center">変更前</p>
                                <div className="aspect-square rounded-xl overflow-hidden bg-gray-100">
                                    {state.oldPhoto.id && (
                                        <img
                                            src={getImageUrl(state.oldPhoto.id)}
                                            alt="変更前の写真"
                                            className="w-full h-full object-cover"
                                        />
                                    )}
                                </div>
                            </div>
                            <span className="text-gray-400 text-xl font-bold flex-shrink-0">→</span>
                            <div className="flex-1 space-y-1">
                                <p className="text-[10px] font-semibold text-emerald-600 text-center">変更後</p>
                                <div className="aspect-square rounded-xl overflow-hidden bg-gray-100 ring-2 ring-emerald-400">
                                    {state.newPhoto.id && (
                                        <img
                                            src={getImageUrl(state.newPhoto.id)}
                                            alt="変更後の写真"
                                            className="w-full h-full object-cover"
                                        />
                                    )}
                                </div>
                            </div>
                        </div>
                        <div className="flex gap-2">
                            <button
                                type="button"
                                onClick={onCancelReplace}
                                className="flex-1 px-4 py-2.5 text-sm font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-xl transition-colors min-h-[44px]"
                            >
                                戻す
                            </button>
                            <button
                                type="button"
                                onClick={onConfirmReplace}
                                className="flex-1 px-4 py-2.5 text-sm font-semibold text-white bg-emerald-500 hover:bg-emerald-600 rounded-xl transition-colors min-h-[44px]"
                            >
                                OK
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* コンテンツ選出中オーバーレイ */}
            {isSelectingRandom && (
                <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex flex-col items-center justify-center">
                    <div className="bg-white px-6 py-5 rounded-2xl shadow-xl flex flex-col items-center gap-3 border border-gray-100">
                        <div className="animate-spin rounded-full h-8 w-8 border-3 border-blue-600 border-t-transparent" />
                        <p className="text-sm font-semibold text-gray-800">コンテンツ選出中...</p>
                    </div>
                </div>
            )}
        </div>
    );
}
