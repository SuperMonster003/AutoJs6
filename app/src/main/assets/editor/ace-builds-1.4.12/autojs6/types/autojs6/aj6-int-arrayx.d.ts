// Type definitions for AutoJs6 internal module Numberx
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.7.4
//
// Last modified: Oct 24, 2022

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/__Arrayx__.js
 */

declare namespace Internal {

    interface Arrayx {

        /**
         * @throws TypeError
         */
        ensureArray(...o: any[]);

        // assureArray<T extends Array<any>>(o: T): T;
        // assureArray<T>(o: { [prop: Exclude<string, 'length'>]: T } & { length: number }): T[];
        // assureArray<T>(o: T): T[];

        /**
         * Returns a new array containing all elements that are contained by every specified arrays.
         *
         * @example
         * // [ 2 ]
         * console.log(Arrayx.intersect([1, 2, 3, 3], 2, [2, 3, 4], [2, 3, 3]));
         * // With extending enabled.
         * console.log([1, 2, 3, 3].intersect(2, [2, 3, 4], [2, 3, 3]));
         *
         * // []
         * console.log(Arrayx.intersect([1, 2, 3, 3]));
         * // With extending enabled.
         * console.log([1, 2, 3, 3].intersect());
         */
        intersect<T, U>(arr: T[], ...others: U[][]): (T & U)[];

        /**
         * Returns a distinct array containing all distinct elements from every arrays.
         *
         * @example
         * // [ 1, 2, 3, 0, 5, 4 ]
         * console.log(Arrayx.union([1, 2, 3], 0, 5, [2, 3, 4], [2, 3, 4, 2, 2, 2]));
         * // With extending enabled.
         * console.log([1, 2, 3].union(0, 5, [2, 3, 4], [2, 3, 4, 2, 2, 2]));
         *
         * // [ 1, 2, 3 ]
         * console.log(Arrayx.union([1, 2, 3]));
         * // With extending enabled.
         * console.log([1, 2, 3].union());
         */
        union<T, U>(arr: T[], ...others: U[][]): (T | U)[];

        /**
         * Returns a new array containing only distinct elements from the given array.
         *
         * @example
         * // [ 1, 2, 3, 4 ]
         * console.log(Arrayx.distinct([1, 2, 3, 3, 4, 4, 4, 4, 4]));
         * // With extending enabled.
         * console.log([1, 2, 3, 3, 4, 4, 4, 4, 4].distinct());
         */
        distinct<T>(arr: T[]): T[];

        /**
         * Returns a new array containing only elements from the given array having distinct keys returned by the given selector function.
         *
         * @example
         * // [ 'a', 'ab', 'abc' ]
         * console.log(Arrayx.distinctBy(["a", "b", "ab", "ba", "abc"], a => a.length));
         * // With extending enabled.
         * console.log(["a", "b", "ab", "ba", "abc"].distinctBy(a => a.length));
         */
        distinctBy<T, U>(arr: T[], selector: (T) => U): T[];

        /**
         * Sorts elements in the array in-place according to natural sort order of the value returned by specified selector function.
         */
        sortBy<T, U>(arr: T[], selector: (T) => U): T[];

        /**
         * Sorts elements in the array in-place descending according to their natural sort order.
         */
        sortDescending<T, U>(arr: T[]): T[];

        /**
         * Sorts elements in the array in-place descending according to natural sort order of the value returned by specified selector function.
         */
        sortByDescending<T, U>(arr: T[], selector: (T) => U): T[];

        /**
         * Returns a new array of all elements sorted according to their natural sort order.
         */
        sorted<T>(arr: T[]): T[];

        /**
         * Returns a new array of all elements sorted according to natural sort order of the value returned by specified selector function.
         */
        sortedBy<T, U>(arr: T[], selector: (T) => U): T[];

        /**
         * Returns a new array of all elements sorted descending according to their natural sort order.
         */
        sortedDescending<T, U>(arr: T[]): T[];

        /**
         * Returns a list of all elements sorted descending according to natural sort order of the value returned by specified selector function.
         */
        sortedByDescending<T, U>(arr: T[], selector: (T) => U): T[];

        /**
         * Randomly shuffles elements in this array in-place.
         */
        shuffle(arr: any[]);

    }

}