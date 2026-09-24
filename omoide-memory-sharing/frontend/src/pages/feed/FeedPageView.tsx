import { Feed, FeedProps } from '@/shared/components/Feed';

export type FeedPageViewProps = FeedProps;

/**
 * フィード画面のページビューコンポーネント。
 * shared 配下の Feed コンポーネントに委譲してフィードを描画する。
 */
export function FeedPageView(props: FeedPageViewProps) {
    return <Feed {...props} />;
}
