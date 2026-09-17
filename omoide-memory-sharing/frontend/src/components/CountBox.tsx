interface Props {
    label: string;
    value: number;
    min: number;
    max: number;
    unit: string;
    onChange: (count: number) => void;
}

/**
 * 件数・枚数を指定する汎用「件数ボックス」コンポーネント。
 * min 〜 max の範囲制約を持ち、数値の入力を受け付ける。
 */
export function CountBox({
    label,
    value,
    min,
    max,
    unit,
    onChange,
}: Props) {
    const handleInputChange = (raw: string) => {
        const parsed = parseInt(raw, 10);
        if (isNaN(parsed) || parsed < min) return;
        onChange(Math.min(parsed, max));
    };

    return (
        <div className="flex items-center gap-2">
            <label className="text-sm font-medium text-gray-700 whitespace-nowrap" htmlFor="count-box-input">
                {label}
            </label>
            <input
                id="count-box-input"
                type="number"
                min={min}
                max={max}
                value={value}
                onChange={e => handleInputChange(e.target.value)}
                className="w-20 px-2 py-1 text-sm font-semibold text-center border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <span className="text-sm text-gray-500">
                {unit}（最大 {max} {unit}）
            </span>
        </div>
    );
}
