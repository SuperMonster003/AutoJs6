/* Overwritten protection. */

let { util, plugins } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Numberx}
 */
module.exports = (scriptRuntime, scope) => {
    let _ = {
        compareOperators: {
            '<': (a, b) => a < b,
            '<=': (a, b) => a <= b,
            '>': (a, b) => a > b,
            '>=': (a, b) => a >= b,
            '=': (a, b) => a === b,
        },
        NumberxCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Numberx
             */
            const NumberxCtor = function () {
                return Object.assign(Number.bind(Number), NumberxCtor.prototype);
            };

            NumberxCtor.prototype = {
                constructor: NumberxCtor,
                ICU: (/* @IIFE */ () => {
                    const workdays = 5;
                    const weekends = 2;
                    const health = 'Your health';
                    const evil = 'Hard working only';

                    return Math.round(evil
                        .split(new RegExp(`[${health.toLowerCase()}]`))
                        .map(x => x ? x.codePointAt(0) : 996 / workdays / weekends - weekends)
                        .reduce((x, y) => x + y));
                })(),
                prototype: Number.prototype,
                ensureNumber() {
                    Array.from(arguments).forEach(o => util.ensureNumberType(o));
                },
                check() {
                    if (arguments.length === 0) {
                        return false;
                    }
                    if (arguments.length === 1) {
                        return typeof arguments[0] === 'number';
                    }
                    if (arguments.length === 2) {
                        let numA = arguments[0];
                        let numB = arguments[1];
                        return this.check(numA) && this.check(numB) && numA === numB;
                    }
                    for (let i = 1; i < arguments.length; i += 2) {
                        let opr = arguments[i]; // operator string
                        if (typeof opr !== 'string' || !(opr in _.compareOperators)) {
                            throw Error(`arguments[${i}] for Numberx.check must be an operator rather than ${typeof opr === 'string' ? `"${opr}"` : opr}`);
                        }
                        let b = arguments[i + 1];
                        if (typeof b !== 'number') {
                            throw Error(`arguments[${i + 1}] for Numberx.check must be a number rather than ${typeof b === 'string' ? `"${b}"` : b}`);
                        }
                        let a = arguments[i - 1];
                        if (!_.compareOperators[opr](a, b)) {
                            return false;
                        }
                    }
                    return true;
                },
                clamp(num, clamps) {
                    Numberx.ensureNumber(num);
                    if (!Array.isArray(clamps)) {
                        clamps = Array.from(arguments).slice(1) || [];
                    }
                    let sortedClamps = clamps
                        .flat()
                        .filter(x => !isNaN(Number(x)))
                        .sort((x, y) => x - y);
                    if (sortedClamps.length > 0) {
                        let min = sortedClamps.at(0);
                        let max = sortedClamps.at(-1);
                        if (num < min) return min;
                        if (num > max) return max;
                    }
                    return num;
                },
                clampTo(num, range, cycle) {
                    let sortedClamps = range
                        .flat()
                        .filter(x => !isNaN(Number(x)))
                        .sort((x, y) => x - y);
                    if (sortedClamps.length > 0) {
                        let min = sortedClamps.at(0);
                        let max = sortedClamps.at(-1);
                        let t = typeof cycle === 'number' ? cycle : max - min;
                        if (t <= 0) {
                            throw RangeError(`Cycle must be a positive number`);
                        }
                        if (num < min) {
                            num += Math.ceil((min - num) / t) * t;
                        } else if (num > max) {
                            num -= Math.ceil((num - max) / t) * t;
                        }
                    }
                    return num;
                },
                toFixedNum(num, fraction) {
                    Numberx.ensureNumber(num);
                    return Number(num.toFixed(fraction));
                },
                padStart(num, targetLength, pad) {
                    Numberx.ensureNumber(num);
                    let s = num.toString();
                    return s.padStart.call(s, targetLength, pad || 0);
                },
                padEnd(num, targetLength, pad) {
                    Numberx.ensureNumber(num);
                    let s = num.toString();
                    return s.padEnd.call(s, targetLength, pad || 0);
                },
                parseFloat(string, radix) {
                    if (radix === undefined) {
                        return _.oriParseFloat(string);
                    }
                    if (typeof string !== 'string') {
                        if ('toString' in string && typeof string.toString === 'function') {
                            string = string.toString();
                        } else {
                            string = String(string);
                        }
                    }
                    // @Reference by SuperMonster003 on Nov 1, 2022.
                    //  ! to https://stackoverflow.com/questions/37109968/how-to-convert-binary-fraction-to-decimal
                    return Number.parseInt(string.replace('.', ''), radix) / radix ** (string.split('.')[1] || '').length;
                },
                /**
                 * @example
                 * Numberx.parsePercent('1%'); // 0.01
                 * Numberx.parsePercent('1%%'); // 0.0001
                 */
                parsePercent(percent) {
                    if (typeof percent === 'number') {
                        return percent;
                    }
                    let matchArray = String(percent).replace(/\s*/g, '').match(/^([+-]?\d+(?:\.\d+)?)(%*)$/);
                    return matchArray ? matchArray[1] / 100 ** matchArray[2].length : NaN;
                },
                /**
                 * @example
                 * Numberx.parseRatio('3:2'); // 1.5
                 */
                parseRatio(ratio) {
                    let [ x, y ] = String(ratio).split(':').map(s => s.trim());
                    return parseFloat(x) / parseFloat(y);
                },
                parseAny(s) {
                    if (typeof s === 'number') {
                        return s;
                    }
                    if (typeof s !== 'string') {
                        s = String(s);
                    }
                    s = s.trim();
                    if (s.includes(':')) {
                        return this.parseRatio(s);
                    }
                    if (s.includes('%')) {
                        return this.parsePercent(s);
                    }
                    return Number(s);
                },
            };

            return NumberxCtor;
        })(),
        oriParseFloat: Number.parseFloat.bind(Number),
        registerPluginModule() {
            plugins.extend.registerModule({
                Numberx: {
                    protoKeys: {
                        clamp: 0,
                        clampTo: 0,
                        toFixedNum: 0,
                        padStart: 0,
                        padEnd: 0,
                        parseFloat() {
                            global.parseFloat = Number.parseFloat = Numberx.parseFloat;
                        },
                    },
                    extendJsBuildInObjects() {
                        let that = this;
                        Object.keys(_.NumberxCtor.prototype).forEach((key) => {
                            if (!(key in that.protoKeys)) {
                                Number[key] = Numberx[key];
                                return;
                            }
                            if (typeof that.protoKeys[key] === 'function') {
                                that.protoKeys[key].call(that.protoKeys);
                                return;
                            }
                            if (typeof that.protoKeys[key] === 'number') {
                                Number.prototype[key] = function () {
                                    const args = Array.from(arguments);
                                    args.splice(that.protoKeys[key], 0, this.valueOf());
                                    return Numberx[key].apply(Numberx, args);
                                };
                                return;
                            }
                            Number.prototype[key] = Numberx[key];
                        });
                    },
                },
            });
        },
    };

    /**
     * @type {Internal.Numberx}
     */
    const Numberx = new _.NumberxCtor();

    _.registerPluginModule();

    return Numberx;
};