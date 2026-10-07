import { z } from 'zod';

/**
 * YYYY/MM などのスラッシュ区切りを YYYY-MM に正規化し、前後の空白を除去する。
 */
export function normalizeYearMonth(value: string): string {
    return value.trim().replace('/', '-');
}

/**
 * 有効な ISO 8601 日付文字列を検証する Zod スキーマ
 */
export const isoDateSchema = z
    .string()
    .trim()
    .min(1, { message: '日付を入力してください' })
    .refine((val) => !Number.isNaN(new Date(val).getTime()), {
        message: '有効な日付形式を指定してください',
    });

/**
 * ISO 8601 日付文字列かどうかを判定する型ガード
 */
export function isValidIsoDate(value: string | null | undefined): value is string {
    if (!value) return false;
    return isoDateSchema.safeParse(value).success;
}

/**
 * ISO 期間（開始日 <= 終了日）を検証する Zod スキーマ
 */
export const isoDateRangeSchema = z
    .object({
        startInclusive: isoDateSchema,
        endExclusive: isoDateSchema,
    })
    .refine(
        (data) => new Date(data.startInclusive).getTime() <= new Date(data.endExclusive).getTime(),
        {
            message: '開始日は終了日以前の日付を指定してください',
        }
    );

/**
 * 開始日・終了日（ISO 8601 文字列）で構成される期間パラメータが有効かを検証する。
 * 1. 開始日・終了日がともに有効な ISO 日付文字列であること
 * 2. 開始日 <= 終了日 の時系列順を満たしていること
 */
export function isValidIsoDateRange(startInclusive?: string | null, endExclusive?: string | null): boolean {
    if (!startInclusive || !endExclusive) return false;
    return isoDateRangeSchema.safeParse({ startInclusive, endExclusive }).success;
}

/**
 * YYYY-MM 形式（年4桁、月01〜12）の年月文字列を検証・正規化する Zod スキーマ
 */
export const yearMonthSchema = z
    .string()
    .trim()
    .min(1, { message: '年月を入力してください' })
    .transform((val) => normalizeYearMonth(val))
    .refine(
        (val) => {
            const regex = /^\d{4}-(0[1-9]|1[0-2])$/;
            if (!regex.test(val)) return false;
            const [yearStr, monthStr] = val.split('-');
            const year = Number(yearStr);
            const month = Number(monthStr);
            return year >= 1000 && year <= 9999 && month >= 1 && month <= 12;
        },
        {
            message: 'YYYY-MM 形式で入力してください（例: 2026-09）',
        }
    );

/**
 * YYYY-MM 形式（年4桁、月01〜12）の有効な年月文字列かどうかを判定する。
 */
export function isValidYearMonth(value: string | null | undefined): value is string {
    if (!value) return false;
    return yearMonthSchema.safeParse(value).success;
}

/**
 * 年月による期間（開始年月 <= 終了年月）を検証する Zod スキーマ
 */
export const periodRangeSchema = z
    .object({
        fromYearMonth: yearMonthSchema,
        toYearMonth: yearMonthSchema,
    })
    .refine((data) => data.fromYearMonth <= data.toYearMonth, {
        message: '開始年月は終了年月以前の日付を指定してください',
    });

/**
 * PeriodRange (YYYY-MM) から開始日 (YYYY-MM-01) と終了日 (YYYY-MM-末日) を算出する
 */
export function periodRangeToDates(range: { fromYearMonth: string; toYearMonth: string }): { periodFrom: string; periodTo: string } {
    const [toYear, toMonth] = range.toYearMonth.split('-').map(Number);
    const lastDay = new Date(toYear, toMonth, 0).getDate();
    return {
        periodFrom: `${range.fromYearMonth}-01`,
        periodTo: `${range.toYearMonth}-${String(lastDay).padStart(2, '0')}`,
    };
}

/**
 * periodFrom, periodTo (YYYY-MM-DD) から PhotobookPeriod を復元する
 */
export function datesToPhotobookPeriod(periodFrom: string, periodTo: string): { type: 'MONTH_TAB'; yearMonth: string } | { type: 'DATE_RANGE'; fromYearMonth: string; toYearMonth: string } {
    const fromYm = periodFrom.substring(0, 7);
    const toYm = periodTo.substring(0, 7);
    if (fromYm === toYm) {
        return { type: 'MONTH_TAB', yearMonth: fromYm };
    }
    return { type: 'DATE_RANGE', fromYearMonth: fromYm, toYearMonth: toYm };
}
