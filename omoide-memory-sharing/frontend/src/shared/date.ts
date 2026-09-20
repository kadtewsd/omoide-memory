export function isValidIsoDate(value: string | null | undefined): value is string {
    if (!value) return false;
    const date = new Date(value);
    return !Number.isNaN(date.getTime());
}
