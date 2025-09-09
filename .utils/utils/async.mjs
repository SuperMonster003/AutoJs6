// utils/async.mjs

/**
 * @param {number} ms
 * @returns {Promise<NodeJS.Timeout>}
 */
export async function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
}
