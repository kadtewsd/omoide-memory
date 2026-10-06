import { useFeed } from '@/shared/hooks/useFeed';
import { useComments } from '@/shared/hooks/useComments';
import { ContentsCounter, Feed, FeedGrid, FeedHeader, FeedMonthTabs } from '@/shared/components/feed';
import { MemoryModal } from '@/shared/components/MemoryModal';

export function ContentWithCommentPage() {
    const {
        items,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
        currentYearMonth,
        startInclusive,
        endExclusive,
        monthTabs,
        selectMonthTab,
    } = useFeed('COMMENT_ONLY');
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <FeedHeader>
                <div className="flex items-center justify-between gap-3 flex-wrap">
                    <ContentsCounter
                        startInclusive={startInclusive}
                        endExclusive={endExclusive}
                        mode="COMMENT_ONLY"
                        label="コメント付き思い出"
                        unit="件"
                    />
                </div>

                {monthTabs.length > 0 && (
                    <FeedMonthTabs
                        monthTabs={monthTabs}
                        selectedYearMonth={currentYearMonth}
                        onSelectMonthTab={selectMonthTab}
                    />
                )}
            </FeedHeader>

            <Feed
                items={items}
                hasNext={hasNext}
                loadingInitial={loadingInitial}
                loadingMore={loadingMore}
                loadMore={loadMore}
            >
                <FeedGrid
                    items={items || []}
                    filterMode="COMMENT_ONLY"
                    selectedPhotoIds={new Set()}
                    onItemClick={openModal}
                />
            </Feed>

            <MemoryModal
                selectedItem={selectedItem}
                comments={comments}
                commentsLoading={commentsLoading}
                onClose={closeModal}
            />
        </div>
    );
}
