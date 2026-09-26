import { useState, useRef } from 'react';
import DatePicker, { registerLocale, CalendarContainer } from 'react-datepicker';
import { ja } from 'date-fns/locale/ja';
import 'react-datepicker/dist/react-datepicker.css';
import { isValidYearMonth, normalizeYearMonth } from '@/shared/date';

registerLocale('ja', ja);

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

type ActiveTarget = 'FROM' | 'TO';

/**
 * Date オブジェクトを YYYY-MM 文字列に変換するヘルパー
 */
function dateToYearMonth(d: Date): string {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
}

/**
 * YYYY-MM 文字列を Date オブジェクト（ローカル1日）に変換するヘルパー
 */
function yearMonthToDate(ym: string): Date | null {
    if (!isValidYearMonth(ym)) return null;
    const normalized = normalizeYearMonth(ym);
    const [year, month] = normalized.split('-').map(Number);
    return new Date(year, month - 1, 1);
}

/**
 * react-datepicker を活用した期間（From 〜 To の年月）選択・手入力コンポーネント。
 * - 外側クリックや Escape キーによるクローズは react-datepicker の組み込み機能（onClickOutside / onKeyDown）に委任。
 * - ポップオーバー内で [開始年月 (From)] と [終了年月 (To)] を切り替えて選択可能。
 * - デフォルトは From 選択モード。From 選択後は自動的に To 選択へ誘導。
 * - 開始年月・終了年月のテキスト欄は Props (range) に直接バインドされた Controlled Component。
 * - 手入力欄クリック・フォーカス時はテキスト編集のみで、カレンダーは開かない。
 * - 日付フォーマットが不正または開始月 > 終了月のときはエラー表示を行い、フィード取得を抑制する。
 */
export function PeriodSelector({
    range,
    isActive,
    onRangeChange,
    onActivate,
}: Props) {
    const [isCalendarOpen, setIsCalendarOpen] = useState(false);
    const [activeTarget, setActiveTarget] = useState<ActiveTarget>('FROM');
    const buttonRef = useRef<HTMLButtonElement>(null);

    const isFromInvalid = !isValidYearMonth(range.fromYearMonth);
    const isToInvalid = !isValidYearMonth(range.toYearMonth);
    const isRangeReversed = !isFromInvalid && !isToInvalid && range.fromYearMonth > range.toYearMonth;

    // 手入力: 開始年月
    const handleFromTextChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const raw = e.target.value;
        const normalized = normalizeYearMonth(raw);
        onRangeChange({
            fromYearMonth: normalized,
            toYearMonth: range.toYearMonth,
        });
        if (!isActive) {
            onActivate();
        }
    };

    // 手入力: 終了年月
    const handleToTextChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const raw = e.target.value;
        const normalized = normalizeYearMonth(raw);
        onRangeChange({
            fromYearMonth: range.fromYearMonth,
            toYearMonth: normalized,
        });
        if (!isActive) {
            onActivate();
        }
    };

    // カレンダーで月がクリックされた時の処理
    const handleMonthSelect = (selectedDate: Date | null) => {
        if (!selectedDate) return;
        const ym = dateToYearMonth(selectedDate);

        if (activeTarget === 'FROM') {
            onRangeChange({
                fromYearMonth: ym,
                toYearMonth: range.toYearMonth,
            });
            if (!isActive) onActivate();
            // From 選択後は To 選択へ自動遷移
            setActiveTarget('TO');
        } else {
            onRangeChange({
                fromYearMonth: range.fromYearMonth,
                toYearMonth: ym,
            });
            if (!isActive) onActivate();
            setIsCalendarOpen(false);
        }
    };

    const toggleCalendar = () => {
        if (!isActive) onActivate();
        if (!isCalendarOpen) {
            setActiveTarget('FROM');
        }
        setIsCalendarOpen(prev => !prev);
    };

    const startDate = yearMonthToDate(range.fromYearMonth);
    const endDate = yearMonthToDate(range.toYearMonth);

    const currentTargetDate = activeTarget === 'FROM'
        ? (startDate ?? new Date())
        : (endDate ?? new Date());

    return (
        <div className="flex flex-col gap-1 relative">
            <div
                className={`flex items-center gap-2 px-3 py-1.5 rounded-xl border transition-colors ${
                    isActive
                        ? isFromInvalid || isToInvalid || isRangeReversed
                            ? 'bg-red-50/50 border-red-300 ring-2 ring-red-400/20'
                            : 'bg-blue-50 border-blue-300 ring-2 ring-blue-500/20'
                        : 'bg-gray-100 border-gray-200 opacity-60'
                }`}
            >
                {/* カレンダーアイコンボタン */}
                <button
                    ref={buttonRef}
                    type="button"
                    onClick={toggleCalendar}
                    className={`p-1.5 rounded-lg transition-colors cursor-pointer focus:outline-none flex items-center justify-center ${
                        isCalendarOpen
                            ? 'bg-blue-600 text-white shadow-sm'
                            : 'text-gray-500 hover:text-blue-600 hover:bg-white/80'
                    }`}
                    aria-label="カレンダーで期間を選択"
                    title="カレンダーで期間を選択"
                >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                </button>

                <span className="text-xs sm:text-sm font-medium text-gray-700 whitespace-nowrap">
                    期間:
                </span>

                {/* 開始年月手入力欄 */}
                <input
                    type="text"
                    inputMode="numeric"
                    aria-label="開始年月"
                    placeholder="YYYY-MM"
                    maxLength={7}
                    value={range.fromYearMonth}
                    onChange={handleFromTextChange}
                    onFocus={onActivate}
                    className={`px-2.5 py-1 text-xs sm:text-sm font-semibold border rounded-lg bg-white focus:outline-none focus:ring-2 w-24 sm:w-28 text-center transition-colors ${
                        isFromInvalid || isRangeReversed
                            ? 'border-red-400 text-red-700 focus:ring-red-400 bg-red-50/50'
                            : 'border-gray-300 text-gray-900 focus:ring-blue-500'
                    }`}
                />

                <span className="text-xs sm:text-sm text-gray-500">〜</span>

                {/* 終了年月手入力欄 */}
                <input
                    type="text"
                    inputMode="numeric"
                    aria-label="終了年月"
                    placeholder="YYYY-MM"
                    maxLength={7}
                    value={range.toYearMonth}
                    onChange={handleToTextChange}
                    onFocus={onActivate}
                    className={`px-2.5 py-1 text-xs sm:text-sm font-semibold border rounded-lg bg-white focus:outline-none focus:ring-2 w-24 sm:w-28 text-center transition-colors ${
                        isToInvalid || isRangeReversed
                            ? 'border-red-400 text-red-700 focus:ring-red-400 bg-red-50/50'
                            : 'border-gray-300 text-gray-900 focus:ring-blue-500'
                    }`}
                />
            </div>

            {/* react-datepicker ポップオーバー（開閉・クリック外検知・キー操作をライブラリに委譲） */}
            {isCalendarOpen && (
                <div className="absolute top-full left-0 mt-2 z-50 animate-in fade-in zoom-in-95 duration-150">
                    <DatePicker
                        locale="ja"
                        selected={currentTargetDate}
                        onChange={handleMonthSelect}
                        minDate={activeTarget === 'TO' ? (startDate ?? undefined) : undefined}
                        showMonthYearPicker
                        dateFormat="yyyy-MM"
                        inline
                        onClickOutside={(event) => {
                            // アイコンボタン自体のクリックで二重トグルしないようガード
                            if (buttonRef.current && buttonRef.current.contains(event.target as Node)) {
                                return;
                            }
                            setIsCalendarOpen(false);
                        }}
                        onKeyDown={(e) => {
                            if (e.key === 'Escape') {
                                setIsCalendarOpen(false);
                            }
                        }}
                        calendarContainer={({ children }) => (
                            <CalendarContainer className="!bg-white !rounded-2xl !shadow-2xl !border !border-gray-200 !p-3 !w-72 !font-sans">
                                {/* 上部ヘッダー & 閉じるボタン */}
                                <div className="flex items-center justify-between pb-2 mb-2 border-b border-gray-100">
                                    <span className="text-xs font-bold text-gray-700">
                                        {activeTarget === 'FROM' ? '開始年月を選択' : '終了年月を選択'}
                                    </span>
                                    <button
                                        type="button"
                                        onClick={() => setIsCalendarOpen(false)}
                                        className="text-xs text-gray-400 hover:text-gray-600 p-1 rounded"
                                        aria-label="閉じる"
                                    >
                                        ✕
                                    </button>
                                </div>

                                {/* From / To モード切り替えタブ */}
                                <div className="flex bg-gray-100 p-1 rounded-xl gap-1 mb-2.5">
                                    <button
                                        type="button"
                                        onClick={() => setActiveTarget('FROM')}
                                        className={`flex-1 py-1.5 px-2 rounded-lg text-xs font-bold transition-all text-center ${
                                            activeTarget === 'FROM'
                                                ? 'bg-white text-blue-600 shadow-sm'
                                                : 'text-gray-600 hover:text-gray-900'
                                        }`}
                                    >
                                        開始: {range.fromYearMonth || '未指定'}
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setActiveTarget('TO')}
                                        className={`flex-1 py-1.5 px-2 rounded-lg text-xs font-bold transition-all text-center ${
                                            activeTarget === 'TO'
                                                ? 'bg-white text-blue-600 shadow-sm'
                                                : 'text-gray-600 hover:text-gray-900'
                                        }`}
                                    >
                                        終了: {range.toYearMonth || '未指定'}
                                    </button>
                                </div>

                                {children}
                            </CalendarContainer>
                        )}
                    />
                </div>
            )}

            {/* 日付不正時のエラーメッセージ */}
            {isActive && (isFromInvalid || isToInvalid || isRangeReversed) && (
                <div className="text-[11px] text-red-600 font-medium px-1 flex items-center gap-1">
                    <svg className="w-3.5 h-3.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                    </svg>
                    <span>
                        {isFromInvalid || isToInvalid
                            ? 'YYYY-MM 形式で入力してください（例: 2026-09）'
                            : '開始年月は終了年月以前の日付を指定してください'}
                    </span>
                </div>
            )}
        </div>
    );
}
