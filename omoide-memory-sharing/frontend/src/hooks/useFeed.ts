import { useState, useEffect, useCallback } from 'react';
import { fetchFeed } from '../api';
import { MemoryFeedItem } from '../types';

/**
 * 【カスタムフックの役割】
 * インフィニットスクロールのためのデータ取得ロジックを一元管理します。
 */
export function useFeed() {
    /**
     * 【useState】: コンポーネントの状態を保持します。
     * - items: 現在表示している全アイテムのリスト
     * - loading: 現在APIリクエスト中かどうか（二重リクエスト防止に利用）
     * - hasMore: 次のページがあるかどうか
     */
    const [items, setItems] = useState<MemoryFeedItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [hasMore, setHasMore] = useState(true);

    /**
     * 【useCallback】: 関数の「参照」を固定します。
     * 通常の関数はレンダリングのたびに新しく作られますが、useCallbackを使うと
     * 依存配列（[items, loading, hasMore]）が変わらない限り、同じ関数を使い回します。
     * 
     * ※ なぜ必要か？：
     * この関数を別のコンポーネント（InfiniteScrollLoaderなど）の useEffect に渡す際、
     * 毎回新しい関数が渡されると「中身は同じでも別物」と判断され、リタイア（無限ループ）の原因になります。
     */
    const loadMore = useCallback(async () => {
        // すでにロード中、またはこれ以上データがない場合は何もしない（ガード句）
        if (loading || !hasMore) return;
        
        setLoading(true);
        try {
            // 現在の最後のアイテムのID（カーソル）を取得して、その次からを取得する
            const cursor = items.length > 0 ? items[items.length - 1].id : undefined;
            const newItems = await fetchFeed(cursor, 20);

            if (newItems.length === 0) {
                // 返ってきたデータが空なら、もう「次はない」とマークする
                setHasMore(false);
            } else {
                // 既存のアイテムの後ろに、新しく取得したアイテムを追加する
                setItems(prev => [...prev, ...newItems]);
            }
        } catch (err) {
            console.error('データの取得に失敗しました:', err);
        } finally {
            setLoading(false);
        }
    }, [items, loading, hasMore]);

    /**
     * 【useEffect】: レンダリングのあとに実行したい「副作用」を定義します。
     * 第二引数の [loadMore, items.length] が変わるたびに実行されます。
     * ここでは「初期表示時にアイテムが0件なら最初のページを取得する」という役割を担っています。
     */
    useEffect(() => {
        if (items.length === 0) {
            loadMore();
        }
    }, [loadMore, items.length]);

    return { items, loading, hasMore, loadMore };
}
