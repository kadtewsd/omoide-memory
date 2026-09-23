import { useRef, useEffect } from 'react';

interface Props {
    onLoadMore: () => void;
    hasMore: boolean;
    loading: boolean;
}

/**
 * 【コンポーネントの役割】
 * 画面の外にある「見えないアンカー（loaderRef）」を見守り、
 * それが画面内に入った瞬間に次のデータを読み込む「監視役」のコンポーネントです。
 */
export function InfiniteScrollLoader({ onLoadMore, hasMore, loading }: Props) {
    /**
     * 【useRef】: DOM要素を直接参照するための「フック」です。
     * - コンポーネントが再レンダリングされても値がリセットされません。
     * - ここでは、画面の最下部にある「読み込み中」を表示するためのdivを参照します。
     * 
     * ※ useStateとの違い：値を更新しても再レンダリングが発生しません（DOMの取得などに便利）。
     */
    const loaderRef = useRef<HTMLDivElement>(null);

    /**
     * 【useEffect】: 「監視（IntersectionObserver）」の開始と終了を管理します。
     * onLoadMore, hasMore, loading が変わるたびに監視を最新の状態に更新します。
     */
    useEffect(() => {
        /**
         * 【IntersectionObserver】: ブラウザが提供する「交差監視」APIです。
         * 「ある要素が画面内（正確には親要素の領域内）に入ったかどうか」を効率的に監視します。
         */
        const observer = new IntersectionObserver(
            (entries) => {
                // entries[0] は監視対象（loaderRef）の情報です
                // isIntersecting が true のとき、要素が画面内に入った（＝最下部に到達した）ことを意味します
                if (entries[0].isIntersecting && hasMore && !loading) {
                    onLoadMore(); // 次の読み込みを実行！
                }
            },
            { 
                threshold: 0.1, // 10%が見えたらすぐに反応する設定
                rootMargin: '100px' // 要素が実際に画面に入る100px手前で「入った」とみなす（先回りして読み込む）設定
            }
        );

        // 監視を開始するコード
        const currentLoader = loaderRef.current;
        if (currentLoader) {
            observer.observe(currentLoader);
        }

        // 重要！：【クリーンアップ関数】
        // コンポーネントが消えたり、useEffectが再実行される前に、古い監視を解除します。
        // これを忘れると、古い監視が動き続け、不具合やメモリリークの原因になります。
        return () => {
            if (currentLoader) {
                observer.unobserve(currentLoader);
            }
        };
    }, [hasMore, loading, onLoadMore]);

    // 次のデータがない場合は、監視する必要がないので何も表示しません。
    if (!hasMore) return null;

    return (
        // この div が「監視のアンカー」になります。
        <div className="flex justify-center mt-8 pb-8" ref={loaderRef}>
            {loading && (
                <div className="flex items-center gap-2 text-gray-500 text-sm font-medium">
                    {/* SVGによるローディングアニメーション */}
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
