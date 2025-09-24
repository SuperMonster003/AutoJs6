// utils/date.mjs

/**
 * @example string
 * "Aug 23, 2025"
 * @param {Date} [date=new Date()]
 * @returns {string}
 */
export function toUpdatedStamp(date = new Date()) {
    return date.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

/**
 * @example string
 * "2025/09/20"
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
 * Generate timestamp for .properties file header comment.<br>
 * Note: The timezone abbreviation depends on runtime environment, may display as GMT+08/PDT etc.<br>
 * zh-CN:<br>
 * 生成 .properties 文件头部注释时间戳.<br>
 * 注: 时区缩写依赖运行环境, 可能显示为 GMT+08/PDT 等.
 *
 * @example string
 * "#ThuAug 28 12:05:55 CST 2025"
 *
 * @param {Date} [date=new Date()]
 * @param {string} [timeZone="Asia/Shanghai"]
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
    /**
     * @example Intl.DateTimeFormatOptions
     * { weekday: 'Thu', month:'Aug', day:'28', hour:'12', minute:'05', second:'55', timeZoneName:'CST', year:'2025' }
     * @type {Intl.DateTimeFormatOptions}
     */
    const parts = fmt.formatToParts(date).reduce((acc, p) => {
        acc[p.type] = p.value;
        return acc;
    }, {});
    const stamp = `${parts.weekday} ${parts.month} ${parts.day} ${parts.hour}:${parts.minute}:${parts.second} ${parts.timeZoneName} ${parts.year}`;
    return `#${stamp}`;
}
