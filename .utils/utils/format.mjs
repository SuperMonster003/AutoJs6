// utils/format.mjs

/**
 * @param {number | null} bytes
 * @param {number} [fractionDigits=2]
 * @returns {string | null}
 */
export function bytes2GiB(bytes, fractionDigits = 2) {
    if (bytes == null) return null;
    const gib = bytes / 1024 ** 3;
    return `${gib.toFixed(fractionDigits)} GiB`;
}

/**
 * Constructs a URL with query parameters.<br>
 * zh-CN: 使用查询参数构造 URL.
 *
 * @example
 * // https://example.com?a=1&b=2
 * url('https://example.com', { a: 1, b: 2 });
 *
 * @param {string} url
 * @param {Object} query
 * @returns {string}
 */
export function buildUrl(url, query) {
    if (!query) return url;
    const q = Object.entries(query)
        .map(([ key, value ]) => `${key}=${encodeURIComponent(value)}`)
        .join('&');
    return `${url}?${q}`;
}

/**
 * @example
 * "Up | Down ?" -> "Up \| Down \?"
 *
 * @param {string} string
 * @returns {string}
 */
export function escapeRegExp(string) {
    return string.replace(/[.*+?^${}()|\[\]\\]/g, '\\$&');
}

/**
 * Converts an object into a string representation where each key-value pair
 * is formatted as `"key": "value"` and separated by newline characters.<br>
 * zh-CN:<br>
 * 将对象转换为字符串表示形式, 每个键值对, 以 `"key": "value"` 的格式呈现并用换行符分隔.
 *
 * @example string
 * "name": "John"
 * "age": "30"
 * "city": "New York"
 *
 * @param {Object} o 
 * @return {string}
 */
export function objectToLines(o) {
    return Object.entries(o).map(([ key, value ]) => `"${key}": "${value}"`).join(`\n`);
}
