import { useRef, useEffect } from 'react';

interface Props {
    onLoadMore: () => void;
    hasMore: boolean;
    loading: boolean;
}

/**
 * 画面下部のアンカー要素を監視し、可視になった瞬間に次ページの読み込みを発火するコンポーネント。
 *
 * 【IntersectionObserver の再生成が引き起こす多重発火問題】
 * useEffect の deps に onLoadMore を含めると、loadMore の useCallback 参照が変わるたびに
 * observer が unobserve → 再 observe される。
 * observe した直後にアンカーが画面内にあれば isIntersecting=true がすぐに発火するため、
 * 1回のスクロールで loadMore が複数回呼ばれてしまう。
 *
 * 【解決策: useRef でコールバックを安定化】
 * onLoadMoreRef に常に最新の onLoadMore を保持しておき、
 * observer のコールバック内では ref 経由で呼び出す。
 * こうすることで observer の useEffect deps から onLoadMore を除外でき、
 * observer は hasMore / loading が変わった時だけ再生成される。
 */
export function InfiniteScrollLoader({ onLoadMore, hasMore, loading }: Props) {
    const loaderRef = useRef<HTMLDivElement>(null);

    // 最新の onLoadMore を ref に同期する。
    // observer のクロージャは常にこの ref 経由で最新のコールバックを参照する。
    const onLoadMoreRef = useRef(onLoadMore);
    useEffect(() => {
        onLoadMoreRef.current = onLoadMore;
    }, [onLoadMore]);

    /**
     * hasMore / loading が変わった時だけ observer を再生成する。
     * onLoadMore の参照変化では再生成しないため、loadMore が更新されても
     * observer が無駄に再起動して多重発火することがない。
     */
    useEffect(() => {
        const observer = new IntersectionObserver(
            (entries) => {
                // アンカーが画面内に入り、次ページがあり、読込中でなければ発火する
                if (entries[0].isIntersecting && hasMore && !loading) {
                    onLoadMoreRef.current();
                }
            },
            {
                threshold: 0.1,      // 10% 見えたら反応
                rootMargin: '100px', // 実際に入る 100px 前から「入った」とみなして先読みする
            }
        );

        const currentLoader = loaderRef.current;
        if (currentLoader) {
            observer.observe(currentLoader);
        }

        return () => {
            if (currentLoader) {
                observer.unobserve(currentLoader);
            }
        };
    }, [hasMore, loading]); // onLoadMore は deps 不要（ref 経由で常に最新を参照）

    // 次ページがない場合はアンカーをアンマウントして監視を終了する
    if (!hasMore) return null;

    return (
        <div className="flex justify-center mt-8 pb-8" ref={loaderRef}>
            {loading && (
                <div className="flex items-center gap-2 text-gray-500 text-sm font-medium">
                    <svg className="animate-spin h-5 w-5 text-blue-600" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    読み込み中...
                </div>
            )}
        </div>
    );
}
