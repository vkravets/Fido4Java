/** Packet dates are display strings; avoid locale-dependent parsing and timezone shifts. */
export function shortMailDate(value: string): string {
    const match = value.match(/^(\d{1,2})\s+([A-Za-z]{3})\b/);
    return match ? `${match[1]} ${match[2]}` : value.slice(0, 10);
}
