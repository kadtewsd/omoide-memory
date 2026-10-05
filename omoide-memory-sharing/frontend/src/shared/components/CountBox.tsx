import { useState } from 'react';
import { z, ZodType } from 'zod';

interface Props {
    label: string;
    value: number;
    min: number;
    max: number;
    unit: string;
    onChange: (count: number) => void;
}

interface CountSchemaParams {
    min: number;
    max: number;
}

const createCountSchema = ({ min, max }: CountSchemaParams): ZodType<number, string> =>
    z.string()
        .min(1, { message: '入力してください' })
        .refine((val) => /^\d+$/.test(val), { message: '半角数字で入力してください' })
        .transform((val) => parseInt(val, 10))
        .refine((val) => val >= min, { message: `${min} 以上の数値を入力してください` })
        .refine((val) => val <= max, { message: `${max} 以下の数値を入力してください` });

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
    const [inputValue, setInputValue] = useState<string>(String(value));
    const [errorMessage, setErrorMessage] = useState<string>('');
    const [prevValue, setPrevValue] = useState<number>(value);

    if (value !== prevValue) {
        setPrevValue(value);
        if (Number(inputValue) !== value) {
            setInputValue(String(value));
            setErrorMessage('');
        }
    }

    const schema = createCountSchema({ min, max });

    const handleInputChange = (raw: string) => {
        setInputValue(raw);
        const result = schema.safeParse(raw);
        if (result.success) {
            setErrorMessage('');
            onChange(result.data);
        }
    };

    const handleBlur = () => {
        const result = schema.safeParse(inputValue);
        if (!result.success) {
            setErrorMessage(result.error.issues[0]?.message ?? '入力内容を確認してください');
            return;
        }
        setErrorMessage('');
        setInputValue(String(result.data));
        onChange(result.data);
    };

    return (
        <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
                <label className="text-sm font-medium text-gray-700 whitespace-nowrap" htmlFor="count-box-input">
                    {label}
                </label>
                <input
                    id="count-box-input"
                    type="number"
                    min={min}
                    max={max}
                    value={inputValue}
                    onChange={(e) => handleInputChange(e.target.value)}
                    onBlur={handleBlur}
                    className={`w-20 px-2 py-1 text-sm font-semibold text-center border rounded-lg focus:outline-none focus:ring-2 ${
                        errorMessage.length > 0
                            ? 'border-red-500 focus:ring-red-500 text-red-600'
                            : 'border-gray-300 focus:ring-blue-500 text-gray-900'
                    }`}
                />
                <span className="text-sm text-gray-500">
                    {unit}（最大 {max} {unit}）
                </span>
            </div>
            {errorMessage.length > 0 && (
                <p className="text-xs text-red-600 font-medium">
                    {errorMessage}
                </p>
            )}
        </div>
    );
}
