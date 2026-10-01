import { useState, useEffect } from 'react';
import { AlbumSummary, AlbumDetail, MemoryFeedItem } from '@/shared/types';
import { fetchAlbums, fetchAlbumDetail, getImageUrl, updateAlbum } from '@/shared/api';
import { useAlbumDownloadJob } from '@/shared/hooks/useAlbumDownloadJob';
import { FeedPhotoCard } from '@/shared/components/feed/FeedPhotoCard';
import { View } from '@/shared/components/feed/FeedPhotoCardMode';

class ViewingDetailState {}

class DeletingState {
    constructor(readonly deleteTargetIds: Set<string>) {}

    toggle(photoId: string): DeletingState {
        const next = new Set(this.deleteTargetIds);
        if (next.has(photoId)) {
            next.delete(photoId);
        } else {
            next.add(photoId);
        }
        return new DeletingState(next);
    }
}

type AlbumDetailState = ViewingDetailState | DeletingState;

export interface AlbumGridProps {
    onPhotoClick: (item: MemoryFeedItem) => void;
    onEditAlbum: (albumDetail: AlbumDetail) => void;
}

export function AlbumGrid({ onPhotoClick, onEditAlbum }: AlbumGridProps) {
    const [albums, setAlbums] = useState<AlbumSummary[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const [selectedAlbumId, setSelectedAlbumId] = useState<string | null>(null);
    const [albumDetail, setAlbumDetail] = useState<AlbumDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState<boolean>(false);
    const [detailState, setDetailState] = useState<AlbumDetailState>(new ViewingDetailState());
    const [downloadingAlbumId, setDownloadingAlbumId] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const { startDownload } = useAlbumDownloadJob();

    const handleReload = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await fetchAlbums();
            setAlbums(data);
        } catch (err) {
            console.error('Failed to load albums:', err);
            setError('アルバム一覧の取得に失敗しました');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        const initAlbums = async () => {
            try {
                const data = await fetchAlbums();
                setAlbums(data);
            } catch (err) {
                console.error('Failed to load albums:', err);
                setError('アルバム一覧の取得に失敗しました');
            } finally {
                setLoading(false);
            }
        };

        initAlbums();
    }, []);

    const closeModal = () => {
        setSelectedAlbumId(null);
        setAlbumDetail(null);
        setDetailState(new ViewingDetailState());
    };

    const handleAlbumClick = async (albumId: string) => {
        setSelectedAlbumId(albumId);
        setDetailLoading(true);
        setDetailState(new ViewingDetailState());
        try {
            const detail = await fetchAlbumDetail(albumId);
            setAlbumDetail(detail);
        } catch (err) {
            console.error('Failed to load album detail:', err);
        } finally {
            setDetailLoading(false);
        }
    };

    const handleAddPhotos = () => {
        if (!albumDetail) return;
        closeModal();
        onEditAlbum(albumDetail);
    };

    const handleCommitDelete = async () => {
        if (!albumDetail || !(detailState instanceof DeletingState)) return;
        if (submitting) return;

        const remainingPhotoIds = albumDetail.photos
            .map(p => p.id)
            .filter((id): id is string => id !== null && !detailState.deleteTargetIds.has(id));

        setSubmitting(true);
        try {
            await updateAlbum({
                albumId: albumDetail.albumId,
                resource: { albumName: albumDetail.albumName, photoIds: remainingPhotoIds },
            });
            closeModal();
            await handleReload();
        } catch (err) {
            console.error('Failed to update album:', err);
        } finally {
            setSubmitting(false);
        }
    };

    const handleDownloadZip = async (e: React.MouseEvent, albumId: string) => {
        e.stopPropagation();
        if (downloadingAlbumId === albumId) return;
        setDownloadingAlbumId(albumId);
        try {
            await startDownload({ albumId });
        } catch (err) {
            console.error('Failed to download album zip:', err);
        } finally {
            setDownloadingAlbumId(null);
        }
    };

    if (loading) {
        return (
            <div className="flex justify-center py-20">
                <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (error) {
        return (
            <div className="flex flex-col items-center justify-center py-20 text-gray-500 space-y-3">
                <p className="text-sm font-medium text-red-600">{error}</p>
                <button
                    type="button"
                    onClick={handleReload}
                    className="px-4 py-2 text-xs font-bold bg-gray-200 hover:bg-gray-300 text-gray-800 rounded-lg"
                >
                    再読み込み
                </button>
            </div>
        );
    }

    if (albums.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                <svg className="w-16 h-16 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                </svg>
                <p className="text-lg font-medium">作成されたアルバムはまだありません</p>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Album Summary Cards Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 sm:gap-6">
                {albums.map((album) => (
                    <div
                        key={album.albumId}
                        onClick={() => handleAlbumClick(album.albumId)}
                        className="group bg-white rounded-2xl border border-gray-200 overflow-hidden shadow-sm hover:shadow-md transition-all cursor-pointer flex flex-col justify-between"
                    >
                        {/* Cover Image / Placeholder */}
                        <div className="relative aspect-video bg-gray-100 overflow-hidden flex items-center justify-center">
                            {album.coverPhotoId ? (
                                <img
                                    src={getImageUrl(album.coverPhotoId)}
                                    alt={album.albumName}
                                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                                    loading="lazy"
                                />
                            ) : (
                                <div className="text-gray-400 text-xs font-semibold">カバー写真なし</div>
                            )}
                            <div className="absolute top-2 right-2 bg-black/60 backdrop-blur-md text-white text-xs font-bold px-2.5 py-1 rounded-full">
                                {album.count} 枚
                            </div>
                        </div>

                        <div className="p-4 space-y-3">
                            <div>
                                <h3 className="font-bold text-gray-900 group-hover:text-blue-600 transition-colors line-clamp-1">
                                    {album.albumName}
                                </h3>
                                <p className="text-xs text-gray-500">
                                    {new Date(album.createdAt).toLocaleDateString('ja-JP', {
                                        year: 'numeric',
                                        month: 'long',
                                        day: 'numeric',
                                    })}
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={(e) => handleDownloadZip(e, album.albumId)}
                                disabled={downloadingAlbumId === album.albumId}
                                className="w-full px-3 py-2 text-xs font-bold text-blue-600 bg-blue-50 hover:bg-blue-100 active:bg-blue-200 rounded-xl transition-colors flex items-center justify-center gap-1.5 min-h-[36px]"
                            >
                                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                </svg>
                                <span>{downloadingAlbumId === album.albumId ? 'ダウンロード中...' : 'Zipダウンロード'}</span>
                            </button>
                        </div>
                    </div>
                ))}
            </div>

            {/* Album Detail Modal */}
            {selectedAlbumId && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 sm:p-6 backdrop-blur-sm">
                    <div className="bg-white rounded-2xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden">
                        {/* Detail Header */}
                        <div className="flex items-center justify-between p-4 sm:p-6 border-b border-gray-200 bg-white">
                            <div>
                                <h2 className="text-lg sm:text-xl font-bold text-gray-900">
                                    {albumDetail?.albumName || 'アルバム詳細'}
                                </h2>
                                {albumDetail && (
                                    <p className="text-xs sm:text-sm text-gray-500">
                                        {detailState instanceof DeletingState
                                            ? `${detailState.deleteTargetIds.size} 枚を削除対象に選択中`
                                            : `${albumDetail.count} 枚の写真`}
                                    </p>
                                )}
                            </div>
                            <button
                                type="button"
                                onClick={closeModal}
                                className="p-2 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100 transition-colors"
                            >
                                ✕
                            </button>
                        </div>

                        {/* Detail Photos Grid */}
                        <div className="p-4 sm:p-6 overflow-y-auto flex-1 bg-gray-50">
                            {detailLoading ? (
                                <div className="flex justify-center py-20">
                                    <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
                                </div>
                            ) : albumDetail && albumDetail.photos.length > 0 ? (
                                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3 sm:gap-4">
                                    {albumDetail.photos.map((item) => {
                                        const isDeleteTarget = detailState instanceof DeletingState
                                            && item.id !== null
                                            && detailState.deleteTargetIds.has(item.id);
                                        return (
                                            <div
                                                key={item.id}
                                                className="relative aspect-square rounded-xl overflow-hidden shadow-sm"
                                            >
                                                <div
                                                    className={`w-full h-full transition-opacity ${isDeleteTarget ? 'opacity-40' : ''}`}
                                                    onClick={() => {
                                                        if (detailState instanceof DeletingState && item.id !== null) {
                                                            setDetailState(detailState.toggle(item.id));
                                                        } else {
                                                            onPhotoClick(item);
                                                        }
                                                    }}
                                                >
                                                    <FeedPhotoCard
                                                        item={item}
                                                        mode={new View()}
                                                        onClick={() => {}}
                                                    />
                                                </div>
                                                {isDeleteTarget && (
                                                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                                                        <div className="bg-red-500 rounded-full p-1.5">
                                                            <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M6 18L18 6M6 6l12 12" />
                                                            </svg>
                                                        </div>
                                                    </div>
                                                )}
                                                {detailState instanceof DeletingState && !isDeleteTarget && (
                                                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                                                        <div className="bg-white/70 rounded-full p-1">
                                                            <svg className="w-4 h-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                                            </svg>
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : (
                                <p className="text-center py-10 text-gray-500 text-sm">
                                    写真が見つかりませんでした。
                                </p>
                            )}
                        </div>

                        {/* Detail Footer */}
                        {albumDetail && (
                            <div className="p-4 border-t border-gray-200 bg-white flex justify-end gap-3 flex-wrap">
                                {detailState instanceof DeletingState ? (
                                    <>
                                        <button
                                            type="button"
                                            onClick={() => setDetailState(new ViewingDetailState())}
                                            className="px-5 py-2.5 text-sm font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 active:bg-gray-300 rounded-xl transition-colors min-h-[44px]"
                                        >
                                            キャンセル
                                        </button>
                                        <button
                                            type="button"
                                            onClick={handleAddPhotos}
                                            className="px-5 py-2.5 text-sm font-semibold text-green-700 bg-green-50 hover:bg-green-100 active:bg-green-200 rounded-xl transition-colors flex items-center gap-2 min-h-[44px]"
                                        >
                                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                                            </svg>
                                            追加する
                                        </button>
                                        <button
                                            type="button"
                                            onClick={handleCommitDelete}
                                            disabled={submitting || detailState.deleteTargetIds.size === 0}
                                            className="px-5 py-2.5 text-sm font-semibold text-white bg-red-500 hover:bg-red-600 active:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-colors flex items-center gap-2 min-h-[44px]"
                                        >
                                            {submitting ? (
                                                <>
                                                    <span className="animate-spin h-4 w-4 border-2 border-white border-t-transparent rounded-full" />
                                                    <span>処理中...</span>
                                                </>
                                            ) : (
                                                <span>決定（{detailState.deleteTargetIds.size} 枚削除）</span>
                                            )}
                                        </button>
                                    </>
                                ) : (
                                    <>
                                        <button
                                            type="button"
                                            onClick={() => setDetailState(new DeletingState(new Set()))}
                                            className="px-5 py-2.5 text-sm font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 active:bg-gray-300 rounded-xl transition-colors flex items-center gap-2 min-h-[44px]"
                                        >
                                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                            </svg>
                                            編集
                                        </button>
                                        <button
                                            type="button"
                                            onClick={handleAddPhotos}
                                            className="px-5 py-2.5 text-sm font-semibold text-green-700 bg-green-50 hover:bg-green-100 active:bg-green-200 rounded-xl transition-colors flex items-center gap-2 min-h-[44px]"
                                        >
                                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                                            </svg>
                                            追加する
                                        </button>
                                        <button
                                            type="button"
                                            onClick={(e) => handleDownloadZip(e, albumDetail.albumId)}
                                            disabled={downloadingAlbumId === albumDetail.albumId}
                                            className="px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 active:bg-blue-800 rounded-xl shadow-sm transition-colors flex items-center gap-2 min-h-[44px]"
                                        >
                                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                            </svg>
                                            <span>
                                                {downloadingAlbumId === albumDetail.albumId
                                                    ? 'ダウンロード中...'
                                                    : 'このアルバムをZipダウンロード'}
                                            </span>
                                        </button>
                                    </>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
