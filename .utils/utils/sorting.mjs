// utils/sorting.mjs

import { compareVersionStrings } from './versioning.mjs';

/**
 * @param {[string, string][] | MapIterator<[string, string]> | Map<string, string>} src
 * @param {MapSortable['sort'] | null} [pattern=null]
 * @returns {[string, string][]}
 */
export function sortByMap(src, pattern = null) {
    const entries = src instanceof Map ? Array.from(src.entries()) : Array.from(src);
    switch (pattern) {
        case null:
            return entries;
        case 'key.ascending':
        case 'key.ascending.as.string':
            return entries.sort((a, b) => a[0].localeCompare(b[0]));
        case 'key.ascending.as.number':
            return entries.sort((a, b) => Number(a[0]) - Number(b[0]));
        case 'key.ascending.as.version':
            return entries.sort((a, b) => compareVersionStrings(a[0], b[0]));
        case 'key.descending':
        case 'key.descending.as.string':
            return entries.sort((a, b) => b[0].localeCompare(a[0]));
        case 'key.descending.as.number':
            return entries.sort((a, b) => Number(b[0]) - Number(a[0]));
        case 'key.descending.as.version':
            return entries.sort((a, b) => compareVersionStrings(b[0], a[0]));
        case 'value.ascending':
        case 'value.ascending.as.string':
            return entries.sort((a, b) => a[1].localeCompare(b[1]));
        case 'value.ascending.as.number':
            return entries.sort((a, b) => Number(a[1]) - Number(b[1]));
        case 'value.ascending.as.version':
            return entries.sort((a, b) => compareVersionStrings(a[1], b[1]));
        case 'value.descending':
        case 'value.descending.as.string':
            return entries.sort((a, b) => b[1].localeCompare(a[1]));
        case 'value.descending.as.number':
            return entries.sort((a, b) => Number(b[1]) - Number(a[1]));
        case 'value.descending.as.version':
            return entries.sort((a, b) => compareVersionStrings(b[1], a[1]));
        default:
            throw new Error(`Unknown sorting pattern: ${pattern}`);
    }
}
