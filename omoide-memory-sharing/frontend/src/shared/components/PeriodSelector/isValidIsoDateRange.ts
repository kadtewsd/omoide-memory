import { isValidIsoDate } from '@/shared/date';

/**
 * 開始日・終了日（ISO 8601 文字列）で構成される期間パラメータが有効かを検証する。
 * 1. 開始日・終了日がともに有効な ISO 日付文字列であること
 * 2. 開始日 <= 終了日 の時系列順を満たしていること
 */
export function isValidIsoDateRange(startInclusive?: string | null, endExclusive?: string | null): boolean {
    if (![startInclusive, endExclusive].every(date => isValidIsoDate(date))) {
        return false;
    }
    return new Date(startInclusive!).getTime() <= new Date(endExclusive!).getTime();
}
