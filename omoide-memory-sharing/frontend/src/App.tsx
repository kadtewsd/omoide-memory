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
        <div className="min-h-screen bg-white">
            <header className="sticky top-0 z-10 bg-white border-b border-gray-200 px-6 py-4">
                <h1 className="text-xl font-medium tracking-wide text-gray-800">
                    Omoide Sharing
                </h1>
            </header>

            <main className="p-4 sm:p-6 lg:p-8">
                <FeedGrid items={items} onItemClick={openModal} />

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
