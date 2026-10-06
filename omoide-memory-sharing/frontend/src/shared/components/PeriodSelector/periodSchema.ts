import { z } from 'zod';
import { isValidYearMonth, normalizeYearMonth } from '@/shared/date';

const yearMonthFieldSchema = z
    .string()
    .trim()
    .min(1, { message: '年月を入力してください' })
    .transform((val) => normalizeYearMonth(val))
    .refine((val) => isValidYearMonth(val), {
        message: 'YYYY-MM 形式で入力してください（例: 2026-09）',
    });

export const periodRangeSchema = z
    .object({
        fromYearMonth: yearMonthFieldSchema,
        toYearMonth: yearMonthFieldSchema,
    })
    .refine((data) => data.fromYearMonth <= data.toYearMonth, {
        message: '開始年月は終了年月以前の日付を指定してください',
    });
