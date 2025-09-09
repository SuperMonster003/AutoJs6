// utils/format.mjs

/**
 * @param {number | null} bytes
 * @param {number} [fractionDigits=2]
 */
export function bytes2GiB(bytes, fractionDigits = 2) {
    if (bytes == null) return null;
    const gib = bytes / 1024 ** 3;
    return `${gib.toFixed(fractionDigits)} GiB`;
}