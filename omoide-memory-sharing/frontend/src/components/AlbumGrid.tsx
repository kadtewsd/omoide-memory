import { useState, useEffect } from 'react';
import { AlbumSummary, AlbumDetail } from '../types';
import { fetchAlbums, fetchAlbumDetail, downloadAlbumZip } from '../api';
import { MemoryFeedItem } from '../types';
import { FeedPhotoCard } from './FeedPhotoCard';

interface Props {
    onPhotoClick: (item: MemoryFeedItem) => void;
}

export function AlbumGrid({ onPhotoClick }: Props) {
    const [albums, setAlbums] = useState<AlbumSummary[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const [selectedAlbumId, setSelectedAlbumId] = useState<string | null>(null);
    const [albumDetail, setAlbumDetail] = useState<AlbumDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState<boolean>(false);
    const [downloadingId, setDownloadingId] = useState<string | null>(null);

    const loadAlbums = async () => {
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
        loadAlbums();
    }, []);

    const handleAlbumClick = async (albumId: string) => {
        setSelectedAlbumId(albumId);
        setDetailLoading(true);
        try {
            const detail = await fetchAlbumDetail(albumId);
            setAlbumDetail(detail);
        } catch (err) {
            console.error('Failed to load album detail:', err);
        } finally {
            setDetailLoading(false);
        }
    };

    const handleDownloadZip = async (e: React.MouseEvent, albumName: string, photoIds: string[]) => {
        e.stopPropagation();
        if (downloadingId) return;
        setDownloadingId(albumName);
        try {
            const blob = await downloadAlbumZip(albumName, photoIds);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${albumName}.zip`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        } catch (err) {
            console.error('Failed to download album zip:', err);
        } finally {
            setDownloadingId(null);
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
                    onClick={loadAlbums}
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
                            {album.coverPhotoBase64 ? (
                                <img
                                    src={`data:image/jpeg;base64,${album.coverPhotoBase64}`}
                                    alt={album.albumName}
                                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                                />
                            ) : (
                                <div className="text-gray-400 text-xs font-semibold">カバー写真なし</div>
                            )}
                            <div className="absolute top-2 right-2 bg-black/60 backdrop-blur-md text-white text-xs font-bold px-2.5 py-1 rounded-full">
                                {album.count} 枚
                            </div>
                        </div>

                        {/* Info & Action Footer */}
                        <div className="p-4 space-y-2">
                            <div className="flex items-start justify-between gap-2">
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
                            </div>

                            <button
                                type="button"
                                onClick={(e) => handleDownloadZip(e, album.albumName, [])}
                                disabled={downloadingId === album.albumName}
                                className="w-full mt-2 px-3 py-2 text-xs font-bold text-blue-600 bg-blue-50 hover:bg-blue-100 active:bg-blue-200 rounded-xl transition-colors flex items-center justify-center gap-1.5 min-h-[36px]"
                            >
                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                </svg>
                                <span>{downloadingId === album.albumName ? 'ダウンロード中...' : 'Zip再ダウンロード'}</span>
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
                                        {albumDetail.count} 枚の写真
                                    </p>
                                )}
                            </div>
                            <button
                                type="button"
                                onClick={() => {
                                    setSelectedAlbumId(null);
                                    setAlbumDetail(null);
                                }}
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
                                    {albumDetail.photos.map((item) => (
                                        <div key={item.id} className="aspect-square rounded-xl overflow-hidden shadow-sm">
                                            <FeedPhotoCard
                                                item={item}
                                                isSelected={false}
                                                onToggleSelect={() => {}}
                                                onClick={() => onPhotoClick(item)}
                                            />
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <p className="text-center py-10 text-gray-500 text-sm">
                                    写真が見つかりませんでした。
                                </p>
                            )}
                        </div>

                        {/* Detail Footer */}
                        {albumDetail && (
                            <div className="p-4 border-t border-gray-200 bg-white flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={(e) =>
                                        handleDownloadZip(
                                            e,
                                            albumDetail.albumName,
                                            albumDetail.photos.flatMap((p) => (p.id ? [p.id] : []))
                                        )
                                    }
                                    disabled={downloadingId === albumDetail.albumName}
                                    className="px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 active:bg-blue-800 rounded-xl shadow-sm transition-colors flex items-center gap-2"
                                >
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                                    </svg>
                                    <span>
                                        {downloadingId === albumDetail.albumName
                                            ? 'ダウンロード中...'
                                            : 'このアルバムをZipダウンロード'}
                                    </span>
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
