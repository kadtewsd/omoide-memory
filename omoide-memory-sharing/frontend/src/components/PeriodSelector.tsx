import { useRef } from 'react';

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

interface MonthPickerInputProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
    onActivate: () => void;
}

/**
 * カレンダーアイコン付きの年月ピッカー入力コンポーネント。
 * アイコンまたは入力欄クリックで直接カレンダーピッカーを開く。
 */
function MonthPickerInput({
    label,
    value,
    onChange,
    onActivate,
}: MonthPickerInputProps) {
    const inputRef = useRef<HTMLInputElement>(null);

    const openPicker = () => {
        onActivate();
        try {
            inputRef.current?.showPicker?.();
        } catch {
            inputRef.current?.focus();
        }
    };

    return (
        <div className="relative inline-flex items-center">
            <button
                type="button"
                tabIndex={-1}
                onClick={openPicker}
                className="absolute left-2.5 text-gray-500 hover:text-blue-600 focus:outline-none transition-colors cursor-pointer"
                aria-label={`${label}のカレンダーを開く`}
            >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
            </button>
            <input
                ref={inputRef}
                type="month"
                aria-label={label}
                value={value}
                onChange={e => onChange(e.target.value)}
                onClick={openPicker}
                onFocus={onActivate}
                className="pl-8 pr-2.5 py-1 text-xs sm:text-sm font-semibold border border-gray-300 rounded-lg bg-white cursor-pointer focus:outline-none focus:ring-2 focus:ring-blue-500 [&::-webkit-calendar-picker-indicator]:hidden"
            />
        </div>
    );
}

/**
 * 期間（From 〜 To の年月）を選択する汎用カレンダーコンポーネント。
 * - isActive が true のときは活性化（ハイライト表示）。
 * - isActive が false のときは非活性スタイル（薄いグレー表示）。フォーカスや入力で onActivate が発火する。
 * - カレンダーアイコンまたは入力欄クリックで直接カレンダーピッカーを表示。
 */
export function PeriodSelector({
    range,
    isActive,
    onRangeChange,
    onActivate,
}: Props) {
    const handleFromChange = (newFrom: string) => {
        const nextTo = newFrom > range.toYearMonth ? newFrom : range.toYearMonth;
        onRangeChange({
            fromYearMonth: newFrom,
            toYearMonth: nextTo,
        });
        if (!isActive) {
            onActivate();
        }
    };

    const handleToChange = (newTo: string) => {
        const nextFrom = newTo < range.fromYearMonth ? newTo : range.fromYearMonth;
        onRangeChange({
            fromYearMonth: nextFrom,
            toYearMonth: newTo,
        });
        if (!isActive) {
            onActivate();
        }
    };

    const handleActivate = () => {
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
            <MonthPickerInput
                label="開始年月"
                value={range.fromYearMonth}
                onChange={handleFromChange}
                onActivate={handleActivate}
            />
            <span className="text-xs sm:text-sm text-gray-500">〜</span>
            <MonthPickerInput
                label="終了年月"
                value={range.toYearMonth}
                onChange={handleToChange}
                onActivate={handleActivate}
            />
        </div>
    );
}
