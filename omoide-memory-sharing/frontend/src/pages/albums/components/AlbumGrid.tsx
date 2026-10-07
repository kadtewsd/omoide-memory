import { useState, useEffect } from 'react';
import { AlbumSummary, AlbumDetail, MemoryFeedItem } from '@/shared/types';
import { fetchAlbums, fetchAlbumDetail, getImageUrl, updateAlbum } from '@/shared/api';
import { useAlbumDownloadJob } from '@/shared/hooks/useAlbumDownloadJob';
import { AlbumDetailState } from './types';
import { AlbumDetailModal } from './AlbumDetailModal';

export interface AlbumGridProps {
    /** 写真クリック時に詳細モーダルを開くコールバック */
    onPhotoClick: (item: MemoryFeedItem) => void;
    /** アルバム編集（写真追加等）画面へ遷移するコールバック */
    onEditAlbum: (albumDetail: AlbumDetail) => void;
}

/**
 * 作成済みアルバム一覧をカード形式でグリッド表示し、
 * アルバム詳細の閲覧・写真の削除・編集・ZIPダウンロードを統括するコンポーネント。
 */
export function AlbumGrid({ onPhotoClick, onEditAlbum }: AlbumGridProps) {
    // アルバム一覧データおよび取得状態
    const [albums, setAlbums] = useState<AlbumSummary[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // アルバム詳細モーダルの状態
    const [selectedAlbumId, setSelectedAlbumId] = useState<string | null>(null);
    const [albumDetail, setAlbumDetail] = useState<AlbumDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState<boolean>(false);
    const [detailState, setDetailState] = useState<AlbumDetailState>({ value: 'view' });

    // ZIPダウンロード実行中のアルバムID
    const [downloadingAlbumId, setDownloadingAlbumId] = useState<string | null>(null);

    // 写真削除などの更新処理中フラグ
    const [submitting, setSubmitting] = useState(false);

    const { startDownload } = useAlbumDownloadJob();

    /** アルバム一覧の再読み込み */
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

    // 初期マウント時にアルバム一覧を取得
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

    /** アルバム詳細モーダルを閉じる */
    const closeModal = () => {
        setSelectedAlbumId(null);
        setAlbumDetail(null);
        setDetailState({ value: 'view' });
    };

    /** アルバムカードクリック時に詳細モーダルを開き、詳細データを取得する */
    const handleAlbumClick = async (albumId: string) => {
        setSelectedAlbumId(albumId);
        setDetailLoading(true);
        setDetailState({ value: 'view' });
        try {
            const detail = await fetchAlbumDetail(albumId);
            setAlbumDetail(detail);
        } catch (err) {
            console.error('Failed to load album detail:', err);
        } finally {
            setDetailLoading(false);
        }
    };

    /** アルバムへの写真追加（編集画面への切り替え） */
    const handleAddPhotos = () => {
        if (!albumDetail) return;
        closeModal();
        onEditAlbum(albumDetail);
    };

    /** 削除対象写真の選択状態をトグル */
    const handleToggleDeleteTarget = (photoId: string) => {
        switch (detailState.value) {
            case 'view':
                return;
            case 'delete': {
                const next = new Set(detailState.deleteTargetIds);
                if (next.has(photoId)) {
                    next.delete(photoId);
                } else {
                    next.add(photoId);
                }
                setDetailState({ value: 'delete', deleteTargetIds: next });
                break;
            }
        }
    };

    /** 選択した写真をアルバムから除外（更新API呼び出し） */
    const handleCommitDelete = async () => {
        if (!albumDetail) return;
        switch (detailState.value) {
            case 'view':
                return;
            case 'delete': {
                if (submitting) return;

                const remainingPhotoIds = albumDetail.photos
                    .map((p) => p.id)
                    .filter((id): id is string => id !== null && !detailState.deleteTargetIds.has(id));

                setSubmitting(true);
                try {
                    await updateAlbum({
                        albumId: albumDetail.albumId,
                        resource: { albumName: albumDetail.albumName, photoIds: remainingPhotoIds, status: 'CONFIRMED' },
                    });
                    closeModal();
                    await handleReload();
                } catch (err) {
                    console.error('Failed to update album:', err);
                } finally {
                    setSubmitting(false);
                }
                break;
            }
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
            {/* アルバムサマリーカード一覧 */}
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 sm:gap-6">
                {albums.map((album) => (
                    <div
                        key={album.albumId}
                        onClick={() => handleAlbumClick(album.albumId)}
                        className="group bg-white rounded-2xl border border-gray-200 overflow-hidden shadow-sm hover:shadow-md transition-all cursor-pointer flex flex-col justify-between"
                    >
                        {/* カバー写真 / プレースホルダー */}
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

                        {/* カードフッター（アルバム名、作成日、Zipダウンロード） */}
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
                                onClick={async (e) => {
                                    e.stopPropagation();
                                    if (downloadingAlbumId === album.albumId) return;
                                    setDownloadingAlbumId(album.albumId);
                                    try {
                                        await startDownload({ albumId: album.albumId });
                                    } catch (err) {
                                        console.error('Failed to download album zip:', err);
                                    } finally {
                                        setDownloadingAlbumId(null);
                                    }
                                }}
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

            {/* アルバム詳細モーダル */}
            {selectedAlbumId && (
                <AlbumDetailModal
                    albumDetail={albumDetail}
                    state={detailState}
                    loading={detailLoading}
                    isSubmitting={submitting}
                    isDownloading={albumDetail ? downloadingAlbumId === albumDetail.albumId : false}
                    onClose={closeModal}
                    onPhotoClick={onPhotoClick}
                    onToggleDeleteTarget={handleToggleDeleteTarget}
                    onStartDelete={() => setDetailState({ value: 'delete', deleteTargetIds: new Set() })}
                    onCancelDelete={() => setDetailState({ value: 'view' })}
                    onAddPhotos={handleAddPhotos}
                    onCommitDelete={handleCommitDelete}
                    onDownloadZip={async () => {
                        if (!albumDetail || downloadingAlbumId === albumDetail.albumId) return;
                        setDownloadingAlbumId(albumDetail.albumId);
                        try {
                            await startDownload({ albumId: albumDetail.albumId });
                        } catch (err) {
                            console.error('Failed to download album zip:', err);
                        } finally {
                            setDownloadingAlbumId(null);
                        }
                    }}
                />
            )}
        </div>
    );
}
