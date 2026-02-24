import { useRef } from 'react';
import { useFeed } from './hooks/useFeed';
import { useComments } from './hooks/useComments';
import { FeedGrid } from './components/FeedGrid';
import { MemoryModal } from './components/MemoryModal';

function App() {
    const { items, loading, hasMore, loadMore } = useFeed();
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();

    const loaderRef = useRef<HTMLDivElement>(null);

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-0 z-10 bg-white shadow-sm border-b border-gray-200 px-6 py-4">
                <h1 className="text-xl font-bold tracking-wide text-gray-800">
                    思い出のシェア
                </h1>
            </header>

            <main className="p-4 sm:p-6 lg:p-8">
                {items.length > 0 ? (
                    <FeedGrid items={items} onItemClick={openModal} />
                ) : !loading && (
                    <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                        <svg className="w-16 h-16 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        <p className="text-lg font-medium">表示できるおもいではまだありません</p>
                    </div>
                )}

                {hasMore && (
                    <div className="flex justify-center mt-8 pb-8" ref={loaderRef}>
                        <button
                            onClick={loadMore}
                            disabled={loading}
                            className="px-6 py-2 rounded-full bg-blue-50 text-blue-600 font-medium hover:bg-blue-100 transition-colors disabled:opacity-50"
                        >
                            {loading ? '読み込み中...' : 'もっと見る'}
                        </button>
                    </div>
                )}
            </main>

            {/* Modal matching Google Photos style */}
            <MemoryModal
                selectedItem={selectedItem}
                comments={comments}
                commentsLoading={commentsLoading}
                onClose={closeModal}
            />
        </div>
    );
}

export default App;
