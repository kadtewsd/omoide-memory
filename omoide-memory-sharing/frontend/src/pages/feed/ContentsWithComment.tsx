import { useFeed } from '@/shared/hooks/useFeed';
import { useComments } from '@/shared/hooks/useComments';
import { Feed, FeedGrid } from '@/shared/components/feed';
import { MemoryModal } from '@/shared/components/MemoryModal';

export function ContentWithCommentPage() {
    const {
        items,
        hasNext,
        totalCount,
        loadingInitial,
        loadingMore,
        loadMore,
        currentYearMonth,
        monthTabs,
        selectMonthTab,
    } = useFeed('COMMENT_ONLY');
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();

    return (
        <>
            <Feed
                items={items}
                hasNext={hasNext}
                totalCount={totalCount}
                totalCountLabel="コメント付き思い出"
                totalCountUnit="件"
                loadingInitial={loadingInitial}
                loadingMore={loadingMore}
                loadMore={loadMore}
                monthTabs={monthTabs}
                selectedYearMonth={currentYearMonth}
                onSelectMonthTab={selectMonthTab}
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
        </>
    );
}
