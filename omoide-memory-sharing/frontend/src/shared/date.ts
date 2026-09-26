export function isValidIsoDate(value: string | null | undefined): value is string {
    if (!value) return false;
    const date = new Date(value);
    return !Number.isNaN(date.getTime());
}

/**
 * YYYY/MM などのスラッシュ区切りを YYYY-MM に正規化し、前後の空白を除去する。
 */
export function normalizeYearMonth(value: string): string {
    return value.trim().replace('/', '-');
}

/**
 * YYYY-MM 形式（年4桁、月01〜12）の有効な年月文字列かどうかを判定する。
 */
export function isValidYearMonth(value: string | null | undefined): value is string {
    if (!value) return false;
    const normalized = normalizeYearMonth(value);
    const regex = /^\d{4}-(0[1-9]|1[0-2])$/;
    if (!regex.test(normalized)) return false;

    const [yearStr, monthStr] = normalized.split('-');
    const year = Number(yearStr);
    const month = Number(monthStr);
    return year >= 1000 && year <= 9999 && month >= 1 && month <= 12;
}

