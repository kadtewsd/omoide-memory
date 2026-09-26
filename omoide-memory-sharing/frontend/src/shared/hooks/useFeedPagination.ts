/**
 * カーソルベースのページネーションでフィードを取得・管理するカスタムフック。
 *
 * ─────────────────────────────────────────────
 * 【全体の流れ】
 *
 * 1. 初回ロード（loadInitial）
 *    年月タブが確定した時点で loadInitial が呼ばれる。
 *    セッションストレージにキャッシュがあれば即座に復元してAPIコールしない。
 *    なければサーバーへ第1ページを取得し、結果をキャッシュに保存する。
 *
 * 2. 無限スクロール（loadMore）
 *    InfiniteScrollLoader が画面下部のアンカー要素の可視を検知すると loadMore を呼ぶ。
 *    サーバーへ nextCursor を使って次のページを取得し、既存アイテムに追加（accumulate）する。
 *    追加後の全アイテムをそのままキャッシュに上書き保存する。
 *
 * 3. コンテンツの非同期一斉取得
 *    フィードアイテム（MemoryFeedItem）には画像・動画のバイナリは含まれていない。
 *    各カードコンポーネントが /content/{id}/image 等へ個別に非同期リクエストを発行し、
 *    ブラウザが並列でコンテンツを取得する（遅延ロード）。
 *    サーバーは WebFlux（Reactive Streams）なので複数リクエストを待たずに順次レスポンスできる。
 *    フィードとコンテンツを分離することでリスト表示の初期レスポンスを高速に保てる。
 *
 * 4. セッションストレージキャッシュ（feedPageCache）
 *    年月タブを切り替えて戻ったり、スクロールで上に戻った際に items がリセットされて
 *    「写真がありません」が表示される問題を解消するためのキャッシュ層。
 *    キャッシュキーは「期間 + mode + contentType」の組み合わせ。
 *    同一条件ならキャッシュから復元し、APIコールをスキップする。
 * ─────────────────────────────────────────────
 */

import { useState, useCallback, useEffect } from 'react';
import { fetchFeed } from '@/shared/api';
import { ContentType, FeedCursor, FeedPageResponse, FilterMode, MemoryFeedItem } from '@/shared/types';
import { isValidIsoDate } from '@/shared/date';
import { buildCacheKey, FeedPageResult, readCache, writeCache } from '@/shared/hooks/feedPageCache';

export interface UseFeedPaginationParams {
    startInclusive?: string;
    endExclusive?: string;
    mode?: FilterMode;
    contentType?: ContentType;
    limit?: number;
}

export interface UseFeedPaginationResult {
    items: MemoryFeedItem[];
    hasNext: boolean;
    loadingInitial: boolean;
    loadingMore: boolean;
    loadMore: () => Promise<void>;
    refresh: () => Promise<void>;
}

export function useFeedPagination({
    startInclusive,
    endExclusive,
    mode,
    contentType,
    limit = 25,
}: UseFeedPaginationParams): UseFeedPaginationResult {
    const [items, setItems] = useState<MemoryFeedItem[]>([]);
    const [nextCursor, setNextCursor] = useState<FeedCursor | null>(null);
    const [hasNext, setHasNext] = useState(false);
    const [loadingInitial, setLoadingInitial] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);

    /**
     * サーバーへ1ページ分のフィードをリクエストする。
     * cursor を渡すと続きのページ、null なら先頭ページを取得する。
     * 期間が未確定の場合は null を返して呼び出し元がスキップできるようにする。
     */
    const executeFetch = useCallback(
        async (cursor: FeedCursor | null): Promise<FeedPageResponse | null> => {
            if (!startInclusive || !endExclusive) return null;
            if (!isValidIsoDate(startInclusive) || !isValidIsoDate(endExclusive)) return null;

            return fetchFeed({
                startInclusive,
                endExclusive,
                mode,
                contentType,
                cursorCaptureTime: cursor?.captureTime,
                cursorId: cursor?.id,
                limit,
            });
        },
        [startInclusive, endExclusive, mode, contentType, limit]
    );

    /**
     * APIレスポンスを正規化して FeedPageResult に変換する。
     * レスポンスが配列形式（旧仕様）とオブジェクト形式の両方に対応する。
     */
    const toEntry = (res: FeedPageResponse | null): FeedPageResult => {
        if (!res) {
            return { feedItems: [], nextCursor: null, hasNext: false };
        }
        return {
            feedItems: res.items ?? [],
            nextCursor: res.nextCursor ?? null,
            hasNext: res.hasNext ?? false,
        };
    };

    /**
     * フィードの1ページを取得してステートとキャッシュに反映する共通処理。
     *
     * mergeItems は呼び出し元が渡す高階関数で、アイテムの結合戦略を決定する。
     * - loadInitial: (_prev, fetched) => fetched            先頭ページで全件置き換え
     * - loadMore:    (prev, fetched) => [...prev, ...fetched] 既存アイテムに追加
     *
     * setItems の updater 内でキャッシュを書き込むことで、
     * React が updater に渡す prev が必ず最新の items 値になる。
     * これにより loadMore の useCallback deps に items を含める必要がなくなり、
     * items が変わるたびに loadMore 参照が変わって IntersectionObserver が
     * 多重再生成される問題を根本から解消する。
     */
    const loadPage = useCallback(
        async (
            cursor: FeedCursor | null,
            cacheKey: string,
            mergeItems: (prev: MemoryFeedItem[], fetched: MemoryFeedItem[]) => MemoryFeedItem[],
        ): Promise<void> => {
            const res = await executeFetch(cursor);
            const { feedItems, nextCursor: newCursor, hasNext: newHasNext } = toEntry(res);
            setItems(prev => {
                const merged = mergeItems(prev, feedItems);
                // setItems updater 内でキャッシュを書き込む。
                // prev が必ず最新値なので merged も正確な全件になる。
                writeCache(cacheKey, { items: merged, nextCursor: newCursor, hasNext: newHasNext });
                return merged;
            });
            setNextCursor(newCursor);
            setHasNext(newHasNext);
        },
        [executeFetch]
    );

    /**
     * 先頭ページを取得する。年月タブ確定時や条件変更時に呼ばれる。
     *
     * セッションストレージにキャッシュが残っていれば APIコールなしで即座に復元する。
     * これにより：
     * - 年月タブを行き来しても「写真がありません」が出ない
     * - 同じ条件で再マウントされても余計なリクエストが発生しない
     */
    const loadInitial = useCallback(async () => {
        if (!startInclusive || !endExclusive || !isValidIsoDate(startInclusive) || !isValidIsoDate(endExclusive)) {
            setItems([]);
            setNextCursor(null);
            setHasNext(false);
            setLoadingInitial(false);
            return;
        }

        // キャッシュヒット → APIをスキップして即復元
        const cacheKey = buildCacheKey(startInclusive, endExclusive, mode, contentType);
        const cached = readCache(cacheKey);
        if (cached) {
            setItems(cached.items);
            setNextCursor(cached.nextCursor);
            setHasNext(cached.hasNext);
            return;
        }

        // キャッシュミス → サーバーへ先頭ページをリクエスト
        setLoadingInitial(true);
        setItems([]);
        setNextCursor(null);
        setHasNext(false);
        try {
            // 先頭ページは全件置き換えなので fetched をそのまま返す
            await loadPage(null, cacheKey, (_prev, fetched) => fetched);
        } catch (err) {
            console.error('フィードの取得に失敗しました:', err);
            setItems([]);
        } finally {
            setLoadingInitial(false);
        }
    }, [startInclusive, endExclusive, mode, contentType, loadPage]);

    /**
     * 次のページを追加取得する。InfiniteScrollLoader が画面下部のアンカーを検知した時に呼ばれる。
     *
     * 【無限スクロールの仕組み】
     * InfiniteScrollLoader は IntersectionObserver でアンカー要素の可視を監視する。
     * アンカーが画面内に入ると onLoadMore（= この loadMore）が呼ばれる。
     * 取得した新ページは既存アイテムに追加され、同時にキャッシュも全件で更新される。
     * hasNext が false になった時点でアンカーがアンマウントされ、監視が自動停止する。
     *
     * 【items を deps に含めない理由】
     * items を deps に入れると setItems のたびに loadMore の参照が変わり、
     * InfiniteScrollLoader が IntersectionObserver を再生成してしまう。
     * 再生成のたびにアンカーが画面内なら即座に発火するため loadMore が多重呼出しされる。
     * setItems updater 内で prev を受け取ることで items を deps 不要にしている。
     */
    const loadMore = useCallback(async () => {
        if (!hasNext || loadingMore || loadingInitial || !nextCursor) return;
        if (!startInclusive || !endExclusive) return;

        setLoadingMore(true);
        try {
            const cacheKey = buildCacheKey(startInclusive, endExclusive, mode, contentType);
            // 続きページは既存アイテムに追加するので [...prev, ...fetched] で結合する
            await loadPage(nextCursor, cacheKey, (prev, fetched) => [...prev, ...fetched]);
        } catch (err) {
            console.error('追加フィードの取得に失敗しました:', err);
        } finally {
            setLoadingMore(false);
        }
        // items は loadPage の updater 内で prev として取得するため deps 不要
    }, [hasNext, loadingMore, loadingInitial, nextCursor, startInclusive, endExclusive, mode, contentType, loadPage]);

    // 期間・条件が確定したら先頭ページを取得する
    useEffect(() => {
        loadInitial();
    }, [loadInitial]);

    return {
        items,
        hasNext,
        loadingInitial,
        loadingMore,
        loadMore,
        refresh: loadInitial,
    };
}
