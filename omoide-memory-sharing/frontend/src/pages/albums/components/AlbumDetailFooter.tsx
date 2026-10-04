import { AlbumDetailState } from './types';
import { CancelButton, EditButton, PrimaryButton } from '@/shared/components/button';

export interface AlbumDetailFooterProps {
    state: AlbumDetailState;
    isSubmitting: boolean;
    isDownloading: boolean;
    onStartDelete: () => void;
    onCancelDelete: () => void;
    onAddPhotos: () => void;
    onCommitDelete: () => void;
    onDownloadZip: () => void;
}

export function AlbumDetailFooter({
    state,
    isSubmitting,
    isDownloading,
    onStartDelete,
    onCancelDelete,
    onAddPhotos,
    onCommitDelete,
    onDownloadZip,
}: AlbumDetailFooterProps) {
    const renderModeToggleButton = () => {
        switch (state.value) {
            case 'view':
                return <EditButton onClick={onStartDelete} />;
            case 'delete':
                return <CancelButton onClick={onCancelDelete} />;
        }
    };

    const renderActionButton = () => {
        switch (state.value) {
            case 'view':
                return (
                    <PrimaryButton onClick={onDownloadZip} disabled={isDownloading}>
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                        </svg>
                        <span>
                            {isDownloading
                                ? 'ダウンロード中...'
                                : 'このアルバムをZipダウンロード'}
                        </span>
                    </PrimaryButton>
                );
            case 'delete':
                return (
                    <button
                        type="button"
                        onClick={onCommitDelete}
                        disabled={isSubmitting || state.deleteTargetIds.size === 0}
                        className="px-5 py-2.5 text-sm font-semibold text-white bg-red-500 hover:bg-red-600 active:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-colors flex items-center gap-2 min-h-[44px]"
                    >
                        {isSubmitting ? (
                            <>
                                <span className="animate-spin h-4 w-4 border-2 border-white border-t-transparent rounded-full" />
                                <span>処理中...</span>
                            </>
                        ) : (
                            <span>決定（{state.deleteTargetIds.size} 枚削除）</span>
                        )}
                    </button>
                );
        }
    };

    return (
        <div className="p-4 border-t border-gray-200 bg-white flex justify-end gap-3 flex-wrap">
            {renderModeToggleButton()}
            <button
                type="button"
                onClick={onAddPhotos}
                className="px-5 py-2.5 text-sm font-semibold text-green-700 bg-green-50 hover:bg-green-100 active:bg-green-200 rounded-xl transition-colors flex items-center gap-2 min-h-[44px]"
            >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                追加する
            </button>
            {renderActionButton()}
        </div>
    );
}
