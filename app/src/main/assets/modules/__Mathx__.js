/* Overwritten protection. */

let { plugins, Arrayx, Numberx } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Mathx}
 */
module.exports = (scriptRuntime, scope) => {
    let _ = {
        MathxCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Mathx
             */
            const MathxCtor = function () {
                /* Empty body. */
            };

            MathxCtor.prototype = {
                constructor: MathxCtor,
                randInt(range) {
                    let args = Array.from(arguments);
                    if (args.length === 0) {
                        return Mathx.randInt(Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER);
                    }
                    if (args.length === 1) {
                        if (Array.isArray(args[0])) {
                            return Mathx.randInt.apply(Mathx, args.flat(Infinity));
                        }
                        util.ensureNumberType(args[0]);
                        return args[0] > 0
                            ? Mathx.randInt(0, args[0])
                            : Mathx.randInt(args[0], 0);
                    }
                    let ranges = Arrayx.distinct(Arrayx.sorted(args.flat(Infinity).map(o => Number(o)).filter(o => !isNaN(o))));
                    let min, max;
                    util.ensureNumberType(min = Math.ceil(ranges.at(0)));
                    util.ensureNumberType(max = Math.floor(ranges.at(-1)));
                    return Math.floor(Math.random() * (max - min + 1)) + min;
                },
                sum(num, fraction) {
                    let [ nums, frac ] = _.parseArgs.apply(_, arguments);
                    if (!nums.length || nums.includes(NaN)) {
                        return NaN;
                    }
                    let sum = nums.reduce((x, y) => Number(x) + Number(y));
                    let fracInt = parseInt(frac);
                    return isNaN(fracInt) ? sum : Numberx.toFixedNum(sum, fracInt);
                },
                avg(num, fraction) {
                    let [ nums, frac ] = _.parseArgs.apply(_, arguments);
                    if (!nums.length || nums.includes(NaN)) {
                        return NaN;
                    }
                    let sum = Mathx.sum(nums);
                    let avg = sum / nums.length;
                    let fracInt = parseInt(frac);
                    return isNaN(fracInt) ? avg : Numberx.toFixedNum(avg, fracInt);
                },
                median(num, fraction) {
                    let [ nums, frac ] = _.parseArgs.apply(_, arguments);
                    Arrayx.sortBy(nums, Number);
                    let len = nums.length;
                    if (!len || nums.includes(NaN)) {
                        return NaN;
                    }
                    let med = len % 2
                        ? nums[Math.floor(len / 2)]
                        : (nums[len / 2 - 1] + nums[len / 2]) / 2;
                    let fracInt = parseInt(frac);
                    return isNaN(fracInt) ? med : Numberx.toFixedNum(med, fracInt);
                },
                var(num, fraction) {
                    let [ nums, frac ] = _.parseArgs.apply(_, arguments);
                    let avg = Mathx.avg(nums);
                    let len = nums.length;
                    if (!len || nums.includes(NaN)) {
                        return NaN;
                    }
                    let acc = 0;
                    for (let i = 0; i < len; i += 1) {
                        acc += (nums[i] - avg) ** 2;
                    }
                    let res = acc / len;
                    let fracInt = parseInt(frac);
                    return isNaN(fracInt) ? res : Numberx.toFixedNum(res, fracInt);
                },
                std(num, fraction) {
                    let [ nums, frac ] = _.parseArgs.apply(_, arguments);
                    if (!nums.length || nums.includes(NaN)) {
                        return NaN;
                    }
                    let std = Math.sqrt(Mathx.var(nums));
                    let fracInt = parseInt(frac);
                    return isNaN(fracInt) ? std : Numberx.toFixedNum(std, fracInt);
                },
                cv(num, fraction) {
                    let [ nums, frac ] = _.parseArgs.apply(_, arguments);
                    let len = nums.length;
                    if (len < 2 || nums.includes(NaN)) {
                        return NaN;
                    }

                    let avg = Mathx.avg(nums);
                    let acc = 0;
                    for (let i = 0; i < len; i += 1) {
                        acc += (nums[i] - avg) ** 2;
                    }
                    /**
                     * Sample Standard Deviation (zh-CN: 样本标准差)
                     */
                    let ssd = Math.pow(acc / (len - 1), 0.5);
                    let cv = ssd / avg;
                    let fracInt = parseInt(frac);
                    return isNaN(fracInt) ? cv : Numberx.toFixedNum(cv, fracInt);
                },
                max(num, fraction) {
                    let [ nums, frac ] = _.parseArgs.apply(_, arguments);
                    let max = Math.max.apply(null, nums);
                    let fracInt = parseInt(frac);
                    return isNaN(fracInt) ? max : Numberx.toFixedNum(max, fracInt);
                },
                min(num, fraction) {
                    let [ nums, frac ] = _.parseArgs.apply(_, arguments);
                    let max = Math.min.apply(null, nums);
                    let fracInt = parseInt(frac);
                    return isNaN(fracInt) ? max : Numberx.toFixedNum(max, fracInt);
                },
                dist(pointA, pointB, fraction) {
                    if (pointA instanceof android.graphics.Rect) {
                        if (arguments.length === 1) {
                            return Mathx.dist({ x: pointA.left, y: pointA.top }, { x: pointA.right, y: pointA.bottom });
                        }
                        if (arguments.length === 2 && typeof arguments[1] === 'number') {
                            return Mathx.dist({ x: pointA.left, y: pointA.top }, { x: pointA.right, y: pointA.bottom }, /* fraction = */ arguments[1]);
                        }
                    }
                    let a = _.toPoint(pointA);
                    let b = _.toPoint(pointB);
                    let fracInt = Math.trunc(fraction);
                    let res = Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2);
                    return isNaN(fracInt) ? res : Numberx.toFixedNum(res, fracInt);
                },
                logMn(base, antilogarithm, fraction) {
                    let _frac = typeof fraction === 'number' ? fraction : 13;
                    let _result = Math.log(antilogarithm) / Math.log(base);
                    if (isNaN(_result) || !isFinite(_result) || _frac !== -1) {
                        return _result;
                    }
                    return Number(_result.toFixed(_frac));
                },
                floorLog(base, antilogarithm) {
                    return Math.floor(Mathx.logMn(base, antilogarithm));
                },
                ceilLog(base, antilogarithm) {
                    return Math.ceil(Mathx.logMn(base, antilogarithm));
                },
                roundLog(base, antilogarithm) {
                    return Math.round(Mathx.logMn(base, antilogarithm));
                },
                floorPow(base, power) {
                    return Math.pow(base, Mathx.floorLog(base, power));
                },
                ceilPow(base, power) {
                    return Math.pow(base, Mathx.ceilLog(base, power));
                },
                roundPow(base, power) {
                    return Math.pow(base, Mathx.roundLog(base, power));
                },
            };

            return MathxCtor;
        })(),
        parseArgs(nums, fraction) {
            if (Array.isArray(nums)) {
                return [ nums.flat(Infinity), fraction ];
            }
            return [ Array.from(arguments).flat(Infinity) ];
        },
        toPoint(o) {
            if (Array.isArray(o)) {
                if (o.length !== 2) {
                    throw Error('Points array must be length of 2');
                }
                return { x: Number(o[0]), y: Number(o[1]) };
            }
            if (o instanceof android.graphics.Rect) {
                // @Hint by SuperMonster003 on Oct 28, 2022.
                //  ! centerX or centerY will lose the precision.
                return { x: o.exactCenterX(), y: o.exactCenterY() };
            }
            if (o instanceof org.opencv.core.Point) {
                return { x: o.x, y: o.y };
            }
            return species.isObject(o) ? o : {};
        },
        registerPluginModule() {
            plugins.extend.registerModule({
                Mathx: {
                    extendJsBuildInObjects() {
                        let mapper = {
                            max: 'maxi',
                            min: 'mini',
                        };
                        Object.keys(_.MathxCtor.prototype).forEach((key) => {
                            Math[key in mapper ? mapper[key] : key] = Mathx[key];
                        });
                    },
                },
            });
        },
    };

    /**
     * @type {Internal.Mathx}
     */
    const Mathx = new _.MathxCtor();

    _.registerPluginModule();

    return Mathx;
};