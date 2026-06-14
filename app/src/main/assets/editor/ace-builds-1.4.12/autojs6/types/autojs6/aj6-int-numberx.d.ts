// Type definitions for AutoJs6 internal module Numberx
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jan 11, 2023

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/__Numberx__.js
 */

declare namespace Internal {

    interface Numberx {

        (): number;

        /**
         * 996.ICU - Developers' lives matter.
         * @constant
         */
        ICU: 996;

        /**
         * @throws TypeError
         */
        ensureNumber(...o: any[]);

        /**
         * Check numbers with or without logic operators.
         * zh-CN: 检查数字与操作符字符串的逻辑关系.
         *
         * @example
         * let a = 0.5;
         * let b = 5;
         * let c = 23;
         * let d = 2011;
         *
         * console.log(Numberx.check(a, '<', b)); // true
         * console.log(Numberx.check(a, '<', b, '<=', c)); // true
         * console.log(Numberx.check(a, '<', d, '>', b, '<', c, '>', a)); // true
         * console.log(Numberx.check(a, c, d)); // throw Exception.
         */
        check(...numOrOpr: (number | '<' | '<=' | '>' | '>=' | '=')[]): boolean;

        /**
         * Returns a number clamped to inclusive range of min and max.
         * @param num
         * @param [clamps] - inclusive range indicated by numbers or values could be converted to numbers.
         * @example
         * // 'clamp' often used for an alterable or random number.
         * Numberx.clamp(Mathx.rand([-5, 9]), [0, 10]);
         *
         * // different from Mathx.rand([0.3, 0.5]).
         * Numberx.clamp(Math.random(), [0.3, 0.5]);
         *
         * // always returns the source number itself.
         * Numberx.clamp(9); // 9
         *
         * // always returns the numeric value of param.
         * Numberx.clamp(9, [80]); // 80 -- same as Numberx.clamp(9, [80, 80])
         *
         * // Numberx.clamp(10, [false, 'Hi', [-1], '7'])
         * // -> Numberx.clamp(10, [+false, +'Hi', +[-1], +'7'])
         * // -> Numberx.clamp(10, [0, NaN, -1, 7]) // numeric converted
         * // -> Numberx.clamp(10, [0, -1, 7]) // filtered
         * // -> Numberx.clamp(10, [-1, 0, 7]) // sorted, range: [-1, 7]
         * // -> 7
         * Numberx.clamp(10, [false, 'Hi', [-1], '7']); // 7
         */
        clamp(num: number, ...clamps: (any | any[])[]): number;

        /**
         * @example
         * Numberx.clampTo(361, [0, 360]); // 1
         * Numberx.clampTo(3600, [0, 360]); // 0
         * Numberx.clampTo(372, [0, 360], 10); // 352
         */
        clampTo(num: number, range: number[], cycle?: number): number;

        /**
         * Returns a number value of Number.prototype.toFixed().
         * @param num
         * @param [fraction=0] - max digits after the decimal point.
         * @example
         * let numA = 9;
         * numA.toFixed();              // '9'    -- string
         * Numberx.toFixedNum(numA);    //  9     -- number
         * numA.toFixed(1);             // '9.0'  -- string
         * Numberx.toFixedNum(numA, 1); //  9     -- number
         * numA.toFixed(2);             // '9.00' -- string
         * Numberx.toFixedNum(numA, 2); //  9     -- number
         *
         * let numB = 9.04;
         * numB.toFixed();              // '9'    -- string
         * Numberx.toFixedNum(numB);    //  9     -- number
         * numB.toFixed(1);             // '9.0'  -- string
         * Numberx.toFixedNum(numB, 1); //  9     -- number
         * numB.toFixed(2);             // '9.04' -- string
         * Numberx.toFixedNum(numB, 2); //  9.04  -- number
         *
         * let numC = 9.5995;
         * numC.toFixed();              // '10'   -- string
         * Numberx.toFixedNum(numC);    //  10    -- number
         * numC.toFixed(1);             // '9.6'  -- string
         * Numberx.toFixedNum(numC, 1); //  9.6   -- number
         * numC.toFixed(2);             // '9.60' -- string
         * Numberx.toFixedNum(numC, 2); //  9.6   -- number
         * @see Number.prototype.toFixed
         */
        toFixedNum(num: number, fraction?: number): number;

        /**
         * Pads the current number with a given string to reach a given length (right padding).
         * @param num
         * @param targetLength
         * @param [pad='0']
         * @example
         * console.log(Numberx.padStart(9, 4)); // '0009'
         * console.log(Numberx.padStart(9, 4, '*')); // '***9'
         * @see String.prototype.padStart
         */
        padStart(num: number, targetLength: number, pad?: number | string): string;

        /**
         * Pads the current number with a given string to reach a given length (right padding).
         * @param num
         * @param targetLength
         * @param [pad='0']
         * @example
         * console.log(Numberx.padEnd(9, 4)); // '9000'
         * console.log(Numberx.padEnd(9, 4, '*')); // '9***'
         * @see String.prototype.padEnd
         */
        padEnd(num: number, targetLength: number, pad?: number | string): string;

        /**
         * Number.parseFloat(string) with radix supported.
         *
         * @example
         * console.log(parseFloat("0.8", 16)); // 0.5
         * console.log(parseFloat("0.101", 2)); // 0.625
         */
        parseFloat(string: string, radix?: number): number;

        /**
         * Convert percentage string into a number.
         * @example
         * console.log(Number.parsePercent('100%')); // 1
         * console.log(Number.parsePercent('50%')); // 0.5
         * console.log(Number.parsePercent('99.95%')); // 0.9995
         * console.log(Number.parsePercent('1%')); // 0.01
         * console.log(Number.parsePercent('1%%')); // 0.0001
         * console.log(Number.parsePercent('100%%%')); // 0.0001
         */
        parsePercent(percentage: Percentage$): number;

        /**
         * Convert ratio string into a number.
         * @example
         * console.log(Number.parseRatio('3:2')); // 1.5
         */
        parseRatio(ratio: string): number;

        /**
         * @example
         * console.log(Numberx.parseAny('9')); // 9
         * console.log(Numberx.parseAny('30.05')); // 30.05
         * console.log(Numberx.parseAny('0xFF')); // 255
         *
         * console.log(Numberx.parseAny('20%')); // 0.2
         * console.log(Numberx.parseAny('20%%')); // 0.002
         *
         * console.log(Numberx.parseAny('18:9')); // 2
         */
        parseAny(o: any): number;

    }

}