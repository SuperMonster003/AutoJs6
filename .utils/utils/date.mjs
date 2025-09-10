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

/**
 * 生成 .properties 文件头部注释时间戳.
 * 形如: "#Thu Aug 28 12:05:55 CST 2025" (Properties.store 风格, en-US + short tz)
 * 注: 时区缩写依赖运行环境, 可能显示为 GMT+08/PDT 等.
 * @param {Date} [date=new Date()]
 * @param {string} [timeZone="Asia/Shanghai"] 可选时区, 如 "Asia/Shanghai"
 * @returns {string}
 */
export function generatePropertiesFileTimestamp(date = new Date(), timeZone) {
    const fmt = new Intl.DateTimeFormat('en-US', {
        weekday: 'short',
        month: 'short',
        day: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
        timeZoneName: 'short',
        ...(timeZone ? { timeZone } : { timeZone: 'Asia/Shanghai' }),
    });
    const parts = fmt.formatToParts(date).reduce((acc, p) => {
        acc[p.type] = p.value;
        return acc;
    }, /** @type {Object<string, string>} */ ({}));
    // parts 示例: { weekday: 'Thu', month:'Aug', day:'28', hour:'12', minute:'05', second:'55', timeZoneName:'CST', year:'2025' }
    const stamp = `${parts.weekday} ${parts.month} ${parts.day} ${parts.hour}:${parts.minute}:${parts.second} ${parts.timeZoneName} ${parts.year}`;
    return `#${stamp}`;
}
