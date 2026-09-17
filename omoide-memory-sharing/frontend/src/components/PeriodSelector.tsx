export interface PeriodRange {
    fromYearMonth: string;
    toYearMonth: string;
}

interface Props {
    range: PeriodRange;
    isActive: boolean;
    onRangeChange: (range: PeriodRange) => void;
    onActivate: () => void;
}

/**
 * 期間（From 〜 To の年月）を選択する汎用カレンダーコンポーネント。
 * - isActive が true のときは活性化（ハイライト表示）。
 * - isActive が false のときは非活性スタイル（薄いグレー表示）。フォーカスや入力で onActivate が発火する。
 */
export function PeriodSelector({
    range,
    isActive,
    onRangeChange,
    onActivate,
}: Props) {
    const handleFromChange = (newFrom: string) => {
        onRangeChange({
            fromYearMonth: newFrom,
            toYearMonth: range.toYearMonth,
        });
        if (!isActive) {
            onActivate();
        }
    };

    const handleToChange = (newTo: string) => {
        onRangeChange({
            fromYearMonth: range.fromYearMonth,
            toYearMonth: newTo,
        });
        if (!isActive) {
            onActivate();
        }
    };

    const handleFocus = () => {
        if (!isActive) {
            onActivate();
        }
    };

    return (
        <div
            className={`flex items-center gap-2 px-3 py-1.5 rounded-xl border transition-colors ${
                isActive
                    ? 'bg-blue-50 border-blue-300 ring-2 ring-blue-500/20'
                    : 'bg-gray-100 border-gray-200 opacity-60'
            }`}
        >
            <span className="text-xs sm:text-sm font-medium text-gray-700 whitespace-nowrap">
                期間:
            </span>
            <input
                type="month"
                aria-label="開始年月"
                value={range.fromYearMonth}
                onChange={e => handleFromChange(e.target.value)}
                onFocus={handleFocus}
                className="px-2 py-1 text-xs sm:text-sm font-semibold border border-gray-300 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <span className="text-xs sm:text-sm text-gray-500">〜</span>
            <input
                type="month"
                aria-label="終了年月"
                value={range.toYearMonth}
                onChange={e => handleToChange(e.target.value)}
                onFocus={handleFocus}
                className="px-2 py-1 text-xs sm:text-sm font-semibold border border-gray-300 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
        </div>
    );
}
