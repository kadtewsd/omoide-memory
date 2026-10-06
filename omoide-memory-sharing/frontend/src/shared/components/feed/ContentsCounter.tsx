import { useState, useEffect, useRef } from 'react';
import { fetchContentsCount } from '@/shared/api';
import { ContentType, FilterMode } from '@/shared/types';
import { isValidIsoDateRange } from '@/shared/components/PeriodSelector';

export interface ContentsCounterProps {
    startInclusive?: string;
    endExclusive?: string;
    mode?: FilterMode;
    contentType?: ContentType;
    label: string;
    unit: string;
    className?: string;
    onCountChange?: (count: number) => void;
}

/**
 * フィード・アルバム画面で該当期間のコンテンツ全件数を専用エンドポイントから非同期取得して
 * 「おしゃれでそれとなく」表示する自律型カウンターバッジコンポーネント。
 */
export function ContentsCounter({
    startInclusive,
    endExclusive,
    mode = 'ALL',
    contentType = 'ALL',
    label,
    unit,
    className,
    onCountChange,
}: ContentsCounterProps) {
    const [count, setCount] = useState<number | null>(null);
    const onCountChangeRef = useRef(onCountChange);
    useEffect(() => {
        onCountChangeRef.current = onCountChange;
    });

    const isValid = isValidIsoDateRange(startInclusive, endExclusive);

    useEffect(() => {
        if (!isValid) {
            return;
        }

        let isCancelled = false;

        fetchContentsCount({
            startInclusive,
            endExclusive: endExclusive!,
            mode,
            contentType,
        })
            .then(res => {
                if (!isCancelled) {
                    setCount(res.count);
                    onCountChangeRef.current?.(res.count);
                }
            })
            .catch(err => {
                if (!isCancelled) {
                    console.error('コンテンツ件数の取得に失敗しました:', err);
                    setCount(null);
                }
            });

        return () => {
            isCancelled = true;
        };
    }, [isValid, startInclusive, endExclusive, mode, contentType]);

    if (!isValid || count === null || count <= 0) return null;

    return (
        <div
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 bg-slate-100/90 text-slate-600 rounded-xl text-xs font-medium border border-slate-200/80 shadow-2xs backdrop-blur-xs transition-colors${className ? ` ${className}` : ''}`}
        >
            <svg
                className="w-3.5 h-3.5 text-slate-400 shrink-0"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden="true"
            >
                <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                />
            </svg>
            <span>
                {label}: <strong className="font-bold text-slate-800">{count.toLocaleString()}</strong> {unit}
            </span>
        </div>
    );
}
