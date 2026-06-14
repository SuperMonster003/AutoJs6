// Type definitions for AutoJs6 internal module Mathx
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.7.4
//
// Last modified: Oct 12, 2022

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/__Mathx__.js
 */

declare namespace Internal {

    interface Mathx {

        /**
         * Returns an integer number selected from the specified range.
         *
         * @param range - An integer array containing numbers indicating the range (both start and stop position are included).
         *
         * @example
         * randInt([1, 10]); // e.g. 2
         */
        randInt(range?: number[]): number;
        /**
         * Equals to `randInt(0, stop)`.
         *
         * @example
         * randInt(5); // e.g. 4
         */
        randInt(stop: number): number;
        /**
         * Returns an integer number selected from the specified range.
         * If start is less than stop, a swap will be done automatically.
         *
         * @param start - An integer specifying at which position to start (included).
         * @param stop - An integer specifying at which position to end (included).
         *
         * @example
         * randInt(1, 10); // e.g. 8
         */
        randInt(start: number, stop: number): number;
        /**
         * Returns an integer number selected from the specified range.
         * All number params will be taken as elements for generating the range.
         *
         * @param numbers - Numbers indicating the range (both start and stop position are included).
         *
         * @example
         * randInt(1, 10, 5, 2, 4, 13); // e.g. 6
         */
        randInt(...numbers: number[]): number;

        /**
         * Sum (zh-CN: 求和).
         * Returns a sum of numbers with (or without) fraction digits.
         * @param [num] - numbers needed to be summed up.
         * @param [fraction] - number of digits after the decimal point and must be in the range [0, 20].
         * @example
         * Mathx.sum(); // 0
         * Mathx.sum(1); // 1
         * Mathx.sum(1, 2); // 3
         * Mathx.sum(1, 2, 3); // 6
         * Mathx.sum(1, '2', [3]); // 6
         * Mathx.sum(1, [2], [[3, 4], 5]); // 15
         * Mathx.sum(1.01, 2.02); // 3.0300000000000002
         * Mathx.sum([1.01, 2.02], 2); // 3.03
         * Mathx.sum([1.01, 2.02], 1); // 3 (won't be '3.0' which must be a number)
         * Mathx.sum([1.01, 2.02], 0); // 3
         * Mathx.sum('ABC', 3, '5.2'); // NaN
         */
        sum(num: number[], fraction?: number): number;
        sum(...nums: number[]): number;

        /**
         * Arithmetic Mean (zh-CN: 算术平均数).
         * Returns the average of numbers with (or without) fraction digits.
         * @param [num] - numbers needed to be averaged.
         * @param [fraction] - number of digits after the decimal point and must be in the range [0, 20].
         * @example
         * Mathx.avg(); // NaN
         * Mathx.avg(1); // 1
         * Mathx.avg(1, 2); // 1.5
         * Mathx.avg(1, 2, 3); // 2 (6 / 3)
         * Mathx.avg(1, '2', [3]); // 2 (6 / 3)
         * Mathx.avg(1, [2], [[3, 4], 5]); // 3 (15 / 5)
         * Mathx.avg(1.01, 2.02); // 1.5150000000000001
         * Mathx.avg([1.01, 2.02], 2); // 1.52
         * Mathx.avg([1.01, 2.02], 1); // 1.5
         * Mathx.avg([1.01, 2.02], 0); // 2
         */
        avg(num: number[], fraction?: number): number;
        avg(...nums: number[]): number;

        /**
         * Median (zh-CN: 中位数).
         * Returns the middle number in a sorted list of numbers with (or without) fraction digits.
         * @param [num]
         * @param [fraction] - number of digits after the decimal point and must be in the range [0, 20].
         * @example
         * Mathx.median(); // NaN
         * Mathx.median(1); // 1
         * Mathx.median(1, 2); // 1.5
         * Mathx.median(1, 2, 3); // 2
         * Mathx.median(5, 3, 1, 4); // 3.5
         */
        median(num?: number[], fraction?: number): number;
        median(...nums: number[]): number;

        /**
         * Variance (zh-CN: 方差).
         * Population Variance (zh-CN: 总体方差).
         * @param [num]
         * @param [fraction] - number of digits after the decimal point and must be in the range [0, 20].
         * @example
         * Mathx.var(1,2,3,4,5); // 2
         * Mathx.var(97,98,98,99,100,101,103,104); // 5.5
         * Mathx.var([97,98,98,99,100,101,103,104], 0); // 6
         * @see Mathx.avg
         */
        var(num: number[], fraction?: number): number;
        var(...nums: number[]): number;

        /**
         * Standard Deviation / Mean-Square Deviation (zh-CN: 标准差/均方差).
         * Population Standard Deviation (zh-CN: 总体标准差).
         * @param [num]
         * @param [fraction] - number of digits after the decimal point and must be in the range [0, 20].
         * @example
         * Mathx.std(1,2,3,4,5); // 1.4142135623730951
         * Mathx.std([1,2,3,4,5], 1); // 1.4
         * Mathx.std(97,98,98,99,100,101,103,104); // 2.345207879911715
         * Mathx.std([97,98,98,99,100,101,103,104], 3); // 2.345
         * @see Mathx.var
         */
        std(num: number[], fraction?: number): number;
        std(...nums: number[]): number;

        /**
         * Coefficient of Variation (zh-CN: 变异系数/离散系数).
         * @param [num]
         * @param [fraction] - number of digits after the decimal point and must be in the range [0, 20].
         * @example
         * Mathx.cv(1); // NaN
         * Mathx.cv(1, 1); // 0
         * Mathx.cv(22, 985, 3654, 98474, 698); // 2.092865807313618
         * Mathx.cv([22, 985, 3654, 98474, 698], 2); // 2.09
         * @see Mathx.avg
         */
        cv(num: number[], fraction?: number): number;
        cv(...nums: number[]): number;

        /**
         * Returns the maximum element in a numeric array.
         * For engines like Rhino which doesn't support spread syntax like Math.max(...number[]).
         * @param [num] - numbers needed to be calculated.
         * @param [fraction] - number of digits after the decimal point and must be in the range [0, 20].
         * @example
         * // Examples whose results are same as Math.max().
         *
         * Mathx.max(); // -Infinity (negative)
         * Mathx.max(1, 2, 3, 4); // 4
         * // false can be converted to 0
         * // '3' and [2] can be converted to 3 and 2
         * Mathx.max([3], '2', 1, false); // 3
         *
         * // Examples available for Mathx.max() but NaN for Math.max().
         *
         * // Array cannot be converted to a number when array was the first parameter of Mathx.max(),
         * // elements inside will be spread as arguments for Math.max().
         * Mathx.max([1, 2, 3, 4]); // 4
         * Mathx.max([0.123, 0.234]); // 0.234
         * Mathx.max([0.123, 0.234], 1); // 0.2
         * // 'false' isn't false which cannot be converted to a number.
         * Mathx.max(3, '2', [1], 'false'); // 3
         * // The last parameter 2 is the argument for 'fraction',
         * // meaning not participating the calculation.
         * Mathx.max([1, {}, [1000 / 3], '4', '55'], 2); // 333.33
         * // As most customized Math functions does,
         * // calculation was supported by nesting array.
         * Mathx.max(1, [[[10], 20], 83], 20, [-9], [27, 72]); // 83
         *
         * // Examples whose results are not same as Math.max().
         *
         * // Mathx.max([])
         * // -> Math.max.apply(null, [])
         * // -> Math.max() -> -Infinity
         * // Math.max([])
         * // -> Math.max(+[])
         * // -> Math.max(0) -> 0
         * Mathx.max([]); // -Infinity vs 0
         * // Mathx.max([3], 4)
         * // -> +Math.max(3).toFixed(4) -> 3
         * // Math.max([3], 4)
         * // -> Math.max(+[3], 4)
         * // -> Math.max(3, 4) -> 4
         * Mathx.max([3], 4); // 3 vs 4
         * // Mathx.max([3], '5', 1, false)
         * // -> Mathx.max([3], '5') // [3] is nums, '5' is fraction
         * // -> Mathx.max([3], 5) -> 3
         * // Math.max([3], '5', 1, false)
         * // -> Math.max(+[3], +'5', 1, +false)
         * // -> Math.max(3, 5, 1, 0) -> 5
         * Mathx.max([3], '5', 1, false); // 3 vs 5
         */
        max(num: number[], fraction?: number): number;
        max(...nums: number[]): number;

        /**
         * Returns the minimum element in a numeric array.
         * For engines like Rhino which doesn't support spread syntax like Math.min(...number[])
         * @param [num] - numbers needed to be calculated
         * @param [fraction] - number of digits after the decimal point and must be in the range [0, 20]
         * @example
         * // examples whose results are same as Math.min()
         *
         * Mathx.min(); // Infinity (positive)
         * Mathx.min(1, 2, 3, 4); // 1
         * // false can be converted to 0
         * // '3' and [2] can be converted to 3 and 2
         * Mathx.min('3', [2], 1, false); // 0
         *
         * // Examples available for Mathx.min() but NaN for Math.min()
         *
         * // Array cannot be converted to a number when array was the first parameter of Mathx.min(),
         * // elements inside will be spread as arguments for Math.min()
         * Mathx.min([1, 2, 3, 4]); // 1
         * Mathx.min([0.123, 0.234]); // 0.123
         * Mathx.min([0.123, 0.234], 1); // 0.1
         * // 'false' isn't false which cannot be converted to a number.
         * Mathx.min(3, '2', [1], 'false'); // 1
         * // The last parameter 2 is the argument for 'fraction',
         * // meaning not participating the calculation
         * Mathx.min([1, {}, [1000 / 3], '4', '55'], 2); // 1
         * // As most customized Math functions does,
         * // calculation was supported by nesting array.
         * Mathx.min(1, [[[10], 20], 83], 20, [-9], [27, 72]); // -9
         *
         * // Examples whose results are not same as Math.min().
         *
         * // Mathx.min([])
         * // -> Math.min.apply(null, [])
         * // -> Math.min() -> Infinity
         * // Math.min([])
         * // -> Math.min(+[])
         * // -> Math.min(0) -> 0
         * Mathx.min([]); // Infinity vs 0
         * // Mathx.min([3], 2)
         * // -> +Math.min(3).toFixed(2) -> 3
         * // Math.min([3], 2)
         * // -> Math.min(+[3], 2)
         * // -> Math.min(3, 2) -> 2
         * Mathx.min([3], 2); // 3 vs 2
         * // Mathx.min([3], '2', 1, false)
         * // -> Mathx.min([3], '2') // [3] is nums, '2' is fraction
         * // -> Mathx.min([3], 2) -> 3
         * // Math.min([3], '2', 1, false)
         * // -> Math.min(+[3], +'2', 1, +false)
         * // -> Math.min(3, 2, 1, 0) -> 0
         * Mathx.min([3], '2', 1, false); // 3 vs 0
         */
        min(num: number[], fraction?: number): number;
        min(...nums: number[]): number;

        /**
         * Distance between two points (zh-CN: 两点间距).
         * Returns distance value between two points.
         * @example
         * Mathx.dist(); // NaN
         * Mathx.dist([0, 0], [3, 4]); // 5
         * Mathx.dist({x: 0, y: 0}, {x: 3, y: 4}); // 5
         * Mathx.dist([6, 7], [12, 15]); // 10
         * Mathx.dist({x: 6, y: 7}, {x: 12, y: 15}); // 10
         */
        dist(
            point1: [number, number] | { x: number, y: number } | org.opencv.core.Point | android.graphics.Rect,
            point2: [number, number] | { x: number, y: number } | org.opencv.core.Point | android.graphics.Rect,
            fraction?: number,
        ): number;
        dist(rect: android.graphics.Rect, fraction?: number): number;

        /**
         * Returns the logarithm (with both base and antilogarithm) of two numbers.
         * @param {number} base - zh-CN: 底数
         * @param {number} antilogarithm - zh-CN: 真数
         * @param {number} [fraction=13] - zh-CN: 尾数
         * @example
         * console.log(Mathx.logMn(10, 100)); // 2 -- 10 ^ (2) = 100
         * console.log(Mathx.logMn(2, 1024)); // 10 -- 2 ^ (10) = 1024
         * console.log(Mathx.logMn(81, 9)); // 0.5 -- 81 ^ (0.5) = 9
         * @example
         * console.log(Mathx.logMn(3, 2187)); // 7 (with default fraction: 13)
         * console.log(Mathx.logMn(3, 2187, -1)); // 7.000000000000001
         * console.log(Mathx.logMn(3, 2187, 18)); // 7.000000000000001
         * console.log(Mathx.logMn(3, 2187, 5)); // 7
         * console.log(Mathx.logMn(3, 2187, 0)); // 7
         */
        logMn(base: number, antilogarithm: number, fraction?: number): number;

        /**
         * @example
         * console.log(Mathx.floorLog(2, 1500)); // 10
         */
        floorLog(base: number, antilogarithm: number): number;

        /**
         * @example
         * console.log(Mathx.ceilLog(2, 1500)); // 11
         */
        ceilLog(base: number, antilogarithm: number): number;

        /**
         * @example
         * console.log(Mathx.roundLog(2, 1500)); // 11
         */
        roundLog(base: number, antilogarithm: number): number;

        /**
         * @example
         * console.log(Mathx.floorPow(2, 1500)); // 1024
         */
        floorPow(base: number, power: number): number;

        /**
         * @example
         * console.log(Mathx.ceilPow(2, 1500)); // 1024
         */
        ceilPow(base: number, power: number): number;

        /**
         * @example
         * console.log(Mathx.roundPow(2, 1500)); // 2048
         */
        roundPow(base: number, power: number): number;

    }

}