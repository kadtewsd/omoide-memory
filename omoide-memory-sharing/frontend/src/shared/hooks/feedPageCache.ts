/**
 * フィードページのセッションストレージキャッシュ。
 *
 * 【なぜキャッシュが必要か】
 * フィードのアイテム（MemoryFeedItem）には画像・動画のバイナリは含まれていない。
 * コンテンツ本体は各カードが個別に /content/{id}/image などへ非同期リクエストして取得する。
 * このため、フィードアイテムのリストさえ保持できれば、コンテンツの再フェッチは発生しない。
 *
 * 一方、年月タブ切り替えや React の再レンダリングで useFeedPagination が再初期化されると
 * items ステートがリセットされ、「写真がありません」が一時的に表示されてしまう。
 * そこで、APIから取得した items + ページネーション状態を sessionStorage に保存しておき、
 * 同じ検索条件で再初期化された際はキャッシュから即座に復元する。
 *
 * 【スコープ】
 * sessionStorage はタブを閉じると消えるため、ページリロードや別タブでは新規フェッチされる。
 * 「今のブラウジングセッション中は再フェッチしない」という適切な有効期限になっている。
 */

import { FeedCursor, MemoryFeedItem } from '@/shared/types';

/** sessionStorage に保存する1エントリの形。累積アイテム + 次ページ情報を一体で保持する。 */
export interface FeedPageCacheEntry {
    items: MemoryFeedItem[];
    nextCursor: FeedCursor | null;
    hasNext: boolean;
}

/** APIレスポンスを正規化した結果。loadPage に渡すためのページ単位のデータ。 */
export interface FeedPageResult {
    feedItems: MemoryFeedItem[];
    nextCursor: FeedCursor | null;
    hasNext: boolean;
}

const SESSION_STORAGE_KEY_PREFIX = 'feed_cache:';

/**
 * 検索条件の組み合わせをキャッシュキーに変換する。
 * 期間・フィルターモード・コンテンツ種別のすべてが一致した場合のみヒットする。
 */
export function buildCacheKey(
    startInclusive: string,
    endExclusive: string,
    mode: string | undefined,
    contentType: string | undefined,
): string {
    return `${SESSION_STORAGE_KEY_PREFIX}${startInclusive}|${endExclusive}|${mode ?? ''}|${contentType ?? ''}`;
}

/** sessionStorage からキャッシュエントリを読み込む。存在しない・破損している場合は null を返す。 */
export function readCache(cacheKey: string): FeedPageCacheEntry | null {
    try {
        const raw = sessionStorage.getItem(cacheKey);
        if (!raw) return null;
        return JSON.parse(raw) as FeedPageCacheEntry;
    } catch {
        return null;
    }
}

/**
 * sessionStorage にキャッシュエントリを書き込む。
 * loadMore のたびに累積アイテムごと上書きすることで、常に「現時点での全件」を保持する。
 * 容量超過などで書き込みに失敗してもエラーを上位に伝播させず、通常動作を継続する。
 */
export function writeCache(cacheKey: string, entry: FeedPageCacheEntry): void {
    try {
        sessionStorage.setItem(cacheKey, JSON.stringify(entry));
    } catch {
        // 容量超過等は無視して通常動作を継続する
    }
}
