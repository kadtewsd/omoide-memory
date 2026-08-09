import { useFeed, formatYearMonthDisplay } from './hooks/useFeed';
import { useComments } from './hooks/useComments';
import { FeedGrid } from './components/FeedGrid';
import { MemoryModal } from './components/MemoryModal';

function App() {
    const {
        items,
        loading,
        filterMode,
        currentYearMonth,
        monthTabs,
        selectMonthTab,
        changeFilterMode,
    } = useFeed();
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-0 z-10 bg-white shadow-sm border-b border-gray-200 px-6 py-4 space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <h1 className="text-xl font-bold tracking-wide text-gray-800">
                        思い出のシェア
                    </h1>

                    {/* Mode Selection */}
                    <div className="inline-flex rounded-lg border border-gray-200 bg-gray-100 p-1">
                        <button
                            type="button"
                            onClick={() => changeFilterMode('ALL')}
                            className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors ${
                                filterMode === 'ALL'
                                    ? 'bg-white text-gray-900 shadow-sm'
                                    : 'text-gray-600 hover:text-gray-900'
                            }`}
                        >
                            すべて
                        </button>
                        <button
                            type="button"
                            onClick={() => changeFilterMode('COMMENT_ONLY')}
                            className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors ${
                                filterMode === 'COMMENT_ONLY'
                                    ? 'bg-white text-gray-900 shadow-sm'
                                    : 'text-gray-600 hover:text-gray-900'
                            }`}
                        >
                            コメントのみ
                        </button>
                    </div>
                </div>

                {/* Month Navigation Tabs */}
                <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
                    <div className="flex items-center gap-2">
                        {monthTabs.map(ym => {
                            const isSelected = ym === currentYearMonth;
                            return (
                                <button
                                    key={ym}
                                    type="button"
                                    onClick={() => selectMonthTab(ym)}
                                    className={`px-4 py-1.5 text-sm font-medium rounded-full whitespace-nowrap transition-colors ${
                                        isSelected
                                            ? 'bg-blue-600 text-white shadow-sm'
                                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                    }`}
                                >
                                    {formatYearMonthDisplay(ym)}
                                </button>
                            );
                        })}
                    </div>
                </div>
            </header>

            <main className="p-4 sm:p-6 lg:p-8">
                {loading ? (
                    <div className="flex justify-center py-20">
                        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600" />
                    </div>
                ) : items.length > 0 ? (
                    <FeedGrid items={items} filterMode={filterMode} onItemClick={openModal} />
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                        <svg className="w-16 h-16 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        <p className="text-lg font-medium">表示できるおもいではまだありません</p>
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

