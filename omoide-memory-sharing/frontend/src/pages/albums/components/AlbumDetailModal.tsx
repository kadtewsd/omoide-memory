import { AlbumDetail, MemoryFeedItem } from '@/shared/types';
import { AlbumDetailState } from './types';
import { AlbumDetailFooter } from './AlbumDetailFooter';
import { AlbumDetailPhotoGrid } from './AlbumDetailPhotoGrid';

export interface AlbumDetailModalProps {
    /** 表示対象のアルバム詳細データ */
    albumDetail: AlbumDetail | null;
    /** 詳細モーダルの表示状態（通常閲覧モード / 削除選択モード） */
    state: AlbumDetailState;
    /** データ読み込み中フラグ */
    loading: boolean;
    /** 削除確定などの処理中フラグ */
    isSubmitting: boolean;
    /** ZIPダウンロード処理中フラグ */
    isDownloading: boolean;
    /** モーダルを閉じるコールバック */
    onClose: () => void;
    /** 写真クリック時に詳細モーダルを開くコールバック */
    onPhotoClick: (item: MemoryFeedItem) => void;
    /** 写真の削除対象選択を切り替えるコールバック */
    onToggleDeleteTarget: (photoId: string) => void;
    /** 削除モードを開始するコールバック */
    onStartDelete: () => void;
    /** 削除モードをキャンセルして通常表示に戻るコールバック */
    onCancelDelete: () => void;
    /** アルバム編集・写真追加画面へ遷移するコールバック */
    onAddPhotos: () => void;
    /** 選択した写真の削除を確定するコールバック */
    onCommitDelete: () => void;
    /** アルバムのZIPダウンロードを実行するコールバック */
    onDownloadZip: () => void;
}

/**
 * アルバムの詳細情報をポップアップ表示するモーダルコンポーネント。
 * ヘッダー、写真グリッド、およびモードに応じた操作フッターを統括する。
 */
export function AlbumDetailModal({
    albumDetail,
    state,
    loading,
    isSubmitting,
    isDownloading,
    onClose,
    onPhotoClick,
    onToggleDeleteTarget,
    onStartDelete,
    onCancelDelete,
    onAddPhotos,
    onCommitDelete,
    onDownloadZip,
}: AlbumDetailModalProps) {
    /** 状態に応じたサブタイトル（写真枚数 / 削除選択枚数）のレンダリング */
    const renderSubtitle = () => {
        if (!albumDetail) return null;
        switch (state.value) {
            case 'view':
                return (
                    <p className="text-xs sm:text-sm text-gray-500">
                        {albumDetail.count} 枚の写真
                    </p>
                );
            case 'delete':
                return (
                    <p className="text-xs sm:text-sm text-gray-500">
                        {state.deleteTargetIds.size} 枚を削除対象に選択中
                    </p>
                );
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 sm:p-6 backdrop-blur-sm">
            <div className="bg-white rounded-2xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden">
                {/* ヘッダー */}
                <div className="flex items-center justify-between p-4 sm:p-6 border-b border-gray-200 bg-white">
                    <div>
                        <h2 className="text-lg sm:text-xl font-bold text-gray-900">
                            {albumDetail?.albumName || 'アルバム詳細'}
                        </h2>
                        {renderSubtitle()}
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="p-2 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100 transition-colors"
                        aria-label="閉じる"
                    >
                        ✕
                    </button>
                </div>

                {/* 写真グリッド */}
                <div className="p-4 sm:p-6 overflow-y-auto flex-1 bg-gray-50">
                    <AlbumDetailPhotoGrid
                        albumDetail={albumDetail}
                        state={state}
                        loading={loading}
                        onPhotoClick={onPhotoClick}
                        onToggleDeleteTarget={onToggleDeleteTarget}
                    />
                </div>

                {/* フッター操作バー */}
                {albumDetail && (
                    <AlbumDetailFooter
                        state={state}
                        isSubmitting={isSubmitting}
                        isDownloading={isDownloading}
                        onStartDelete={onStartDelete}
                        onCancelDelete={onCancelDelete}
                        onAddPhotos={onAddPhotos}
                        onCommitDelete={onCommitDelete}
                        onDownloadZip={onDownloadZip}
                    />
                )}
            </div>
        </div>
    );
}
