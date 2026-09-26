type SelectVisualConfig = {
    ariaLabel: string;
    indicatorStyleName: string;
    styleName: string;
};

const SELECT_VISUAL_MAP = new Map<boolean, SelectVisualConfig>([
    [
        true,
        {
            ariaLabel: '写真の選択を解除',
            indicatorStyleName: 'bg-blue-600 text-white shadow-md',
            styleName: 'ring-4 ring-blue-500',
        },
    ],
    [
        false,
        {
            ariaLabel: '写真を選択',
            indicatorStyleName: 'bg-black/40 text-transparent active:border-white',
            styleName: '',
        },
    ],
]);

/**
 * 写真カードの表示モードおよび選択に関する振る舞いを定義するストラテジーインターフェース。
 *
 * `FeedPhotoCard` コンポーネント側でモードの具象型（Select / View）や選択フラグを意識せず、
 * 本インターフェースが提供するプロパティを参照するだけで自己完結して描画できるように設計されています。
 */
export interface CardMode {
    /**
     * カードコンテナ全体に適用するスタイルクラス名（例: 選択時のフォーカスリング等）。
     */
    readonly styleName: string;

    /**
     * 選択状態インジケータ（チェックボックス）の背景色やテキスト色等のスタイルクラス名。
     */
    readonly indicatorStyleName: string;

    /**
     * 選択ボタンのアクセシビリティ用ラベル（スクリーンリーダー向け文言）。
     */
    readonly ariaLabel: string;

    /**
     * 選択状態を切り替えるイベントハンドラ。
     * 選択不可（閲覧モードや上限到達時など）の場合は `null` を設定し、ボタン自体の描画を抑制します。
     */
    readonly onToggle: ((e: React.MouseEvent) => void) | null;
}

export class Select implements CardMode {
    readonly styleName: string;
    readonly indicatorStyleName: string;
    readonly ariaLabel: string;
    readonly onToggle: ((e: React.MouseEvent) => void) | null;

    constructor(selected: boolean, onToggle: ((e: React.MouseEvent) => void) | null = null) {
        this.onToggle = onToggle;
        const config = SELECT_VISUAL_MAP.get(selected)!;
        this.styleName = config.styleName;
        this.indicatorStyleName = config.indicatorStyleName;
        this.ariaLabel = config.ariaLabel;
    }
}

export class View implements CardMode {
    readonly styleName: string = '';
    readonly indicatorStyleName: string = '';
    readonly ariaLabel: string = '';
    readonly onToggle: ((e: React.MouseEvent) => void) | null = null;
}
