// utils/date.mjs

/**
 * @param {Date} [date=new Date()]
 * @returns {string}
 */
export function toUpdatedStamp(date = new Date()) {
    /* e.g. "Aug 23, 2025". */
    return date.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

/**
 * @param {string} [dateText='']
 * @returns {string | null}
 */
export function toYYYYMMDD(dateText = '') {
    const d = dateText ? new Date(dateText) : new Date();
    if (Number.isNaN(d.getTime())) return null;
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}/${m}/${day}`;
}