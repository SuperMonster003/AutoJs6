// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols

// Copyright Joyent, Inc. and other Node contributors.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the
// following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
// NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Util}
 */
module.exports = function (scriptRuntime, scope) {
    const Build = android.os.Build;
    const Arrays = java.util.Arrays;
    const HashMap = java.util.HashMap;
    const JavaUtils = org.autojs.autojs.util.JavaUtils;

    let _ = {
        Util: (/* @IIFE */ () => {
            /**
             * @implements Internal.Util
             */
            const Util = function () {
                /* Empty body. */
            };

            Util.prototype = {
                constructor: Util,

                /**
                 * NOTE: These type checking functions (isXXX) intentionally don't use `instanceof`
                 * because it is fragile and can be easily faked with `Object.create()`.
                 */

                /**
                 * @param arr
                 * @return {arg is any[]}
                 */
                isArray(arr) {
                    return Array.isArray(arr);
                },
                isBoolean(arg) {
                    return typeof arg === 'boolean';
                },
                isNull(arg) {
                    return arg === null;
                },
                isNullOrUndefined(arg) {
                    return this.isNull(arg) || this.isUndefined((arg));
                },
                isNumber(arg) {
                    return typeof arg === 'number';
                },
                isString(arg) {
                    return typeof arg === 'string';
                },
                isSymbol(arg) {
                    return typeof arg === 'symbol';
                },
                isUndefined(arg) {
                    return arg === void 0;
                },
                isRegExp(re) {
                    return this.isObject(re) && species(re) === 'RegExp';
                },
                isObject(arg) {
                    return typeof arg === 'object' && arg !== null;
                },
                isDate(d) {
                    return this.isObject(d) && species(d) === 'Date';
                },
                isError(e) {
                    return this.isObject(e) && (species(e) === 'Error' || e instanceof Error);
                },
                isFunction(arg) {
                    return typeof arg === 'function';
                },
                isBigInt(arg) {
                    return typeof arg === 'bigint';
                },
                isPrimitive(arg) {
                    return isPrimitive(arg);
                },
                isJavaArray(o) {
                    try {
                        return isJavaObject(o) && o.getClass().isArray();
                    } catch (e) {
                        return false;
                    }
                },

                java: {
                    instanceOf(obj, clazz) {
                        return java.lang.Class.forName(clazz).isAssignableFrom(obj.getClass());
                    },
                    array(type) {
                        return java.lang.reflect.Array.newInstance
                            .apply(null, [ _.typeToClass(type) ].concat(Array.from(arguments).slice(1)));
                    },
                    toJsArray(list, nullListToEmptyArray) {
                        if (isNullish(list)) {
                            return nullListToEmptyArray ? [] : null;
                        }
                        let arr = [];
                        for (let i = 0; i < list.size(); i += 1) {
                            arr[i] = list.get(i);
                        }
                        return arr;
                    },
                    objectToMap(obj) {
                        if (isNullish(obj)) {
                            return null;
                        }
                        let map = new HashMap();
                        Object.entries(obj).forEach((entries) => {
                            let [ key, value ] = entries;
                            map.put(key, value);
                        });
                        return map;
                    },
                    mapToObject(map) {
                        if (isNullish(map)) {
                            return null;
                        }
                        let iter = map.entrySet().iterator();
                        let obj = {};
                        while (iter.hasNext()) {
                            let entry = iter.next();
                            obj[entry.key] = entry.value;
                        }
                        return obj;
                    },
                },
                version: {
                    sdkInt: Build.VERSION.SDK_INT,
                },
                versionCodes: (/* @IIFE */ () => {
                    let _ = {
                        // @Semantic {versionCode: [releaseName, internalCodename, platformVersion, apiLevel, releaseDate]}
                        raw: {
                            TIRAMISU: [ 'Android 13', 'Tiramisu', '13', 33, 'Q3 2022' ],
                            S_V2: [ 'Android 12L', 'Snow Cone v2', '12.1', 32, 'March 7, 2022' ],
                            S: [ 'Android 12', 'Snow Cone', '12', 31, 'October 4, 2021' ],
                            R: [ 'Android 11', 'Red Velvet Cake', '11', 30, 'September 8, 2020' ],
                            Q: [ 'Android 10', 'Quince Tart', '10', 29, 'September 3, 2019' ],
                            P: [ 'Android Pie', 'Pistachio Ice Cream', '9', 28, 'August 6, 2018' ],
                            O_MR1: [ 'Android Oreo', 'Oatmeal Cookie', '8.1', 27, 'December 5, 2017' ],
                            O: [ 'Android Oreo', 'Oatmeal Cookie', '8.0', 26, 'August 21, 2017' ],
                            N_MR1: [ 'Android Nougat', 'New York Cheesecake', '7.1-7.1.2', 25, 'October 4, 2016' ],
                            N: [ 'Android Nougat', 'New York Cheesecake', '7.0', 24, 'August 22, 2016' ],
                            M: [ 'Android Marshmallow', 'Macadamia Nut Cookie', '6.0-6.0.1', 23, 'October 2, 2015' ],
                            LOLLIPOP_MR1: [ 'Android Lollipop', 'Lemon Meringue Pie', '5.1-5.1.1', 22, 'March 2, 2015' ],
                            LOLLIPOP: [ 'Android Lollipop', 'Lemon Meringue Pie', '5.0-5.0.2', 21, 'November 4, 2014' ],
                            KITKAT_WATCH: [ 'Android KitKat', 'Key Lime Pie', '4.4W-4.4W.2', 20, 'June 25, 2014' ],
                            KITKAT: [ 'Android KitKat', 'Key Lime Pie', '4.4-4.4.4', 19, 'October 31, 2013' ],
                            JELLY_BEAN_MR2: [ 'Android Jelly Bean', 'Jelly Bean', '4.3-4.3.1', 18, 'July 24, 2013' ],
                            JELLY_BEAN_MR1: [ 'Android Jelly Bean', 'Jelly Bean', '4.2-4.2.2', 17, 'November 13, 2012' ],
                            JELLY_BEAN: [ 'Android Jelly Bean', 'Jelly Bean', '4.1-4.1.2', 16, 'July 9, 2012' ],
                            ICE_CREAM_SANDWICH_MR1: [ 'Android Ice Cream Sandwich', 'Ice Cream Sandwich', '4.0.3-4.0.4', 15, 'December 16, 2011' ],
                            ICE_CREAM_SANDWICH: [ 'Android Ice Cream Sandwich', 'Ice Cream Sandwich', '4.0-4.0.2', 14, 'October 18, 2011' ],
                            HONEYCOMB_MR2: [ 'Android Honeycomb', 'Honeycomb', '3.2-3.2.6', 13, 'July 15, 2011' ],
                            HONEYCOMB_MR1: [ 'Android Honeycomb', 'Honeycomb', '3.1', 12, 'May 10, 2011' ],
                            HONEYCOMB: [ 'Android Honeycomb', 'Honeycomb', '3.0', 11, 'February 22, 2011' ],
                            GINGERBREAD_MR1: [ 'Android Gingerbread', 'Gingerbread', '2.3.3-2.3.7', 10, 'February 9, 2011' ],
                            GINGERBREAD: [ 'Android Gingerbread', 'Gingerbread', '2.3-2.3.2', 9, 'December 6, 2010' ],
                            FROYO: [ 'Android Froyo', 'Froyo', '2.2-2.2.3', 8, 'May 20, 2010' ],
                            ECLAIR_MR1: [ 'Android Eclair', 'Eclair', '2.1', 7, 'January 11, 2010' ],
                            ECLAIR_0_1: [ 'Android Eclair', 'Eclair', '2.0.1', 6, 'December 3, 2009' ],
                            ECLAIR: [ 'Android Eclair', 'Eclair', '2.0', 5, 'October 27, 2009' ],
                            DONUT: [ 'Android Donut', 'Donut', '1.6', 4, 'September 15, 2009' ],
                            CUPCAKE: [ 'Android Cupcake', 'Cupcake', '1.5', 3, 'April 27, 2009' ],
                            BASE_1_1: [ 'Android 1.1', 'Petit Four', '1.1', 2, 'February 9, 2009' ],
                            BASE: [ 'Android 1.0', '', '1.0', 1, 'September 23, 2008' ],
                        },
                        monthMap: {
                            jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6,
                            jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12,
                        },
                        init() {
                            /**
                             * @type {Object.<Util.VersionCodes.CodeName, Util.VersionCodes.Info[]>}
                             */
                            this.versionCodesObject = this.toVersionCodesObject();
                            /**
                             * @type {Util.VersionCodes.Info[]}
                             */
                            this.versionCodesList = this.toVersionCodesList();
                        },
                        toVersionCodesObject() {
                            let result = {};
                            Object.entries(this.raw).forEach((entry) => {
                                let [ versionCode, infoList ] = entry;
                                let [ releaseName, internalCodename, platformVersion, apiLevel, releaseDate ] = infoList;
                                let releaseTimestamp = this.parseTimestamp(releaseDate);
                                result[versionCode] = {
                                    valueOf: () => apiLevel,
                                    apiLevel, versionCode, releaseName, internalCodename,
                                    releaseDate, releaseTimestamp, platformVersion,
                                };
                            });
                            return result;
                        },
                        toVersionCodesList() {
                            return Object.values(this.versionCodesObject);
                        },
                        /**
                         * @param {string} s
                         * @return {number}
                         */
                        parseTimestamp(s) {
                            let replacer = ($, $1, $2, $3) => `${$3},${(_.monthMap)[$1.toLowerCase().slice(0, 3)] - 1},${$2}`;

                            // noinspection JSCheckFunctionSignatures
                            let args = [ Date ].concat(s.replace(/(\w{3})\w* (\d\d?), (\d+)/, replacer).split(','));

                            // May be NaN
                            return (new (Function.prototype.bind.apply(Date, args))).getTime();
                        },
                        generateInfoGroupsIfNeeded() {
                            if (_.group) {
                                return;
                            }
                            /**
                             * @type {Object.<keyof Util.VersionCodes.Info, Object.<string, Util.VersionCodes.Info | Util.VersionCodes.Info[]>>}
                             */
                            _.group = {
                                versionCode: {},
                                releaseName: {},
                                internalCodename: {},
                                platformVersion: {},
                                apiLevel: {},
                                releaseDate: {},
                                releaseTimestamp: {},
                            };
                            this.versionCodesList.forEach((info) => {
                                Object.keys(info).forEach(k => {
                                    let e = info[k];
                                    if (e in _.group[k]) {
                                        // releaseName and internalCodename may trigger
                                        if (Array.isArray(_.group[k][e])) {
                                            _.group[k][e].push(info);
                                        } else {
                                            _.group[k][e] = [ _.group[k][e], info ];
                                        }
                                    } else {
                                        _.group[k][e] = info;
                                    }
                                });
                            });
                        },
                        getVerWeight(s) {
                            let matched = s.match(/^(\d+?)(?:\.(\d+?))?(?:\.(\d+))?$/);
                            if (!matched) {
                                return -1;
                            }
                            return matched.slice(1).map((o, i) => {
                                return o ? Math.pow(1e3, (2 - i)) * o : 0;
                            }).reduce((a, b) => a + b, 0);
                        },
                        getNextVerWeight(s) {
                            return this.getVerWeight(s.replace(/(\d+)$/, ($0, $1) => {
                                return (Number($1) + 1).toString();
                            }));
                        },
                    };

                    let $ = {
                        getVersionCodes() {
                            _.init();

                            return Object.assign(_.versionCodesObject, {
                                search: $.search.bind($),
                                toString: $.toString.bind($),
                            });
                        },
                        /**
                         * @param {Util.VersionCodes.CodeName | string | number} o
                         * @return {Util.VersionCodes.Info|Util.VersionCodes.Info[]|null}
                         */
                        search(o) {
                            /**
                             * @type {Util.VersionCodes.Info[]}
                             */
                            let results = [];

                            this.searchFor(o, results);
                            this.removeDuplicated(results);

                            return results.length > 1 ? results : results.length ? results[0] : null;
                        },
                        /**
                         * @param {Util.VersionCodes.IsInDetail} isInDetail
                         */
                        toString(isInDetail) {
                            return _.versionCodesList.map((info) => {
                                if (!isInDetail) {
                                    const { platformVersion, apiLevel, releaseName, versionCode } = info;
                                    return `${versionCode}: ${apiLevel} / ${releaseName} / ${platformVersion}`;
                                }
                                return Object.entries(info).map((entry) => {
                                    const [ key, value ] = entry;
                                    return `${key}: ${value}`;
                                }).join(', ');
                            }).join('\n');
                        },
                        searchFor(o, results) {
                            _.generateInfoGroupsIfNeeded();

                            if (o instanceof Date) {
                                o = o.getTime();
                            }
                            if (typeof o === 'number') {
                                o = String(o);
                            }
                            if (typeof o === 'string') {
                                this.searchForApiLevel(o, results);
                                this.searchForVersionCode(o, results);
                                this.searchForReleaseName(o, results);
                                this.searchForInternalCodename(o, results);
                                this.searchForReleaseTimestamp(o, results);
                                this.searchForReleaseDate(o, results);
                                this.searchForPlatformVersion(o, results);
                            }
                        },
                        removeDuplicated(results) {
                            results.sort((a, b) => b.apiLevel - a.apiLevel);
                            for (let i = 0; i < results.length - 1; i += 1) {
                                if (results[i].apiLevel === results[i + 1].apiLevel) {
                                    results.splice(i--, 1);
                                }
                            }
                        },
                        searchForApiLevel(lv, results) {
                            if (lv in _.group.apiLevel) {
                                results.push(_.group.apiLevel[lv]);
                            }
                        },
                        searchForReleaseTimestamp(ts, results) {
                            if (ts.length >= 4 && ts in _.group.releaseTimestamp) {
                                results.push(_.group.releaseTimestamp[ts]);
                            }
                        },
                        searchForPlatformVersion(ver, results) {
                            Object.entries(_.group.platformVersion).forEach((entry) => {
                                let [ platformVersion, info ] = entry;
                                if (!/^[\d.-]+$/.test(platformVersion)) {
                                    return;
                                }
                                let weight = _.getVerWeight(ver);
                                if (platformVersion.includes('-')) {
                                    let [ min, max ] = platformVersion.split('-').map(_.getVerWeight);
                                    if (weight >= min && weight <= max) {
                                        results.push(info);
                                    }
                                } else if (weight === _.getVerWeight(platformVersion)) {
                                    results.push(info);
                                }
                            });
                        },
                        searchForVersionCode(verCode, results) {
                            verCode = verCode.toUpperCase();
                            if (verCode in _.group.versionCode) {
                                results.push(_.group.versionCode[verCode]);
                            }
                        },
                        searchForReleaseName(name, results) {
                            this.searchForWordMatch(name, results, _.group.releaseName);
                        },
                        searchForInternalCodename(name, results) {
                            this.searchForWordMatch(name, results, _.group.internalCodename);
                        },
                        searchForWordMatch(name, results, group) {
                            name = name.toUpperCase();
                            Object.entries(group).forEach((entry) => {
                                let [ groupName, info ] = entry;
                                if (name.split(/\s+/).every(n => groupName.toUpperCase().split(/\s+/).includes(n))) {
                                    if (Array.isArray(info)) {
                                        info.forEach(o => results.push(o));
                                    } else {
                                        results.push(info);
                                    }
                                }
                            });
                        },
                        searchForReleaseDate(date, results) {
                            if (date instanceof Date) {
                                this.searchForReleaseTimestamp(date.getTime(), results);
                            } else {
                                this.searchForReleaseDateString(date, results);
                            }
                        },
                        searchForReleaseDateString(date, results) {
                            Object.entries(_.group.releaseDate).forEach((entry) => {
                                let [ releaseDate, info ] = entry;
                                if (date.length === 4) {
                                    if (releaseDate.includes(date)) {
                                        results.push(info);
                                    }
                                } else {
                                    let y, m, d = NaN;
                                    let splitSymbol = '\ubfbf';
                                    let rex = {
                                        year: /^\d{4}$/,
                                        month: /^0?\d|1[0-2]$/,
                                        day: /^0?\d|[12]\d|3[01]$/,
                                    };

                                    let s = date.split(/[^A-Za-z\d]+/).join(splitSymbol);

                                    if ((y = s.slice(0, 4)).match(rex.year)) {
                                        s = s.slice(5);
                                    } else if ((y = s.slice(-4)).match(rex.year)) {
                                        s = s.slice(0, -4);
                                    }

                                    let mm = s.split(splitSymbol)[0] || String();
                                    if (mm.match(rex.month)) {
                                        m = Number(mm) - 1;
                                    } else {
                                        mm = mm.toLowerCase().slice(0, 3);
                                        if (mm in _.monthMap) {
                                            m = _.monthMap[mm] - 1;
                                        }
                                    }

                                    let dd = s.split(splitSymbol)[1];
                                    if (dd && dd.match(rex.day)) {
                                        d = Number(dd);
                                    }

                                    let infoDate = new Date(info.releaseTimestamp);

                                    if (isNaN(d)) {
                                        let inputDate = new Date(y, m);
                                        if (releaseDate.includes(y)) {
                                            if (inputDate.getMonth() === infoDate.getMonth()) {
                                                results.push(info);
                                            }
                                        }
                                    } else {
                                        let inputDate = new Date(y, m, d);
                                        if (inputDate.getTime() === infoDate.getTime()) {
                                            results.push(info);
                                        }
                                    }
                                }
                            });
                        },
                    };

                    return $.getVersionCodes();
                })(),
                /**
                 * @param C - Child "class"
                 * @param P - Parent "class"
                 */
                extend(C, P) {
                    _.extendStatics(C, P);
                    _.setPrototype(C, P);
                },
                inspect: ({
                    getMethod() {
                        this.selfAugment();

                        return this.inspect.bind(this);
                    },
                    /**
                     * Echos the value of a value. Trys to print the value out
                     * in the best way possible given the different types.
                     *
                     * Legacy params: [ obj, showHidden, depth, colors ]
                     *
                     * @param {Object} obj - The object to print out.
                     * @param {{
                     *      showHidden: boolean;
                     *      depth: number;
                     *      colors: boolean;
                     * } | boolean} [options] - Optional options object that alters the output.
                     * @see Internal.Util.inspect
                     * @return {string}
                     */
                    inspect(obj, options) {
                        let ctx = Object.assign({
                            seen: [],
                            showHidden: typeof arguments[1] === 'boolean' ? arguments[1] : false,
                            depth: typeof arguments[2] === 'number' ? arguments[2] : 2,
                            colors: typeof arguments[3] === 'boolean' ? arguments[3] : false,
                            customInspect: true,
                            stylize: /* @SatisfyIDE */ () => void 0,
                        }, species.isObject(options) ? options : {});

                        if (ctx.colors) {
                            ctx.stylize = this.stylizeWithColor.bind(this);
                        } else {
                            ctx.stylize = this.stylizeWithoutColor.bind(this);
                        }

                        return _.formatValue(ctx, obj, ctx.depth);
                    },
                    selfAugment() {
                        // noinspection SpellCheckingInspection
                        /**
                         * SGR (Select Graphic Rendition) parameters
                         * @see https://en.wikipedia.org/wiki/ANSI_escape_code#SGR_(Select_Graphic_Rendition)_parameters
                         */
                        let srg = {
                            /**
                             * Foreground colors
                             */
                            fg: {
                                BLACK: 30,
                                RED: 31,
                                GREEN: 32,
                                YELLOW: 33,
                                BLUE: 34,
                                MAGENTA: 35,
                                CYAN: 36,
                                WHITE: 37,
                                GRAY: 90,
                                BRIGHT_BLACK: 90,
                                BRIGHT_RED: 91,
                                BRIGHT_GREEN: 92,
                                BRIGHT_YELLOW: 93,
                                BRIGHT_BLUE: 94,
                                BRIGHT_MAGENTA: 95,
                                BRIGHT_CYAN: 96,
                                BRIGHT_WHITE_: 97,
                            },
                            /**
                             * Background colors
                             */
                            bg: {
                                BLACK: 40,
                                RED: 41,
                                GREEN: 42,
                                YELLOW: 43,
                                BLUE: 44,
                                MAGENTA: 45,
                                CYAN: 46,
                                WHITE: 47,
                                GRAY: 100,
                                BRIGHT_BLACK: 100,
                                BRIGHT_RED: 101,
                                BRIGHT_GREEN: 102,
                                BRIGHT_YELLOW: 103,
                                BRIGHT_BLUE: 104,
                                BRIGHT_MAGENTA: 105,
                                BRIGHT_CYAN: 106,
                                BRIGHT_WHITE_: 107,
                            },
                            RESET: 0,
                            BOLD: 1,
                            DIM: 2,
                            ITALIC: 3,
                            UNDERLINE: 4,
                            SLOW_BLINK: 5,
                            RAPID_BLINK: 6,
                            INVERT: 7,
                            CONCEAL: 8,
                            STRIKE: 9,
                            PRIMARY_FONT: 10,
                            ALTERNATIVE_FONT_1: 11,
                            ALTERNATIVE_FONT_2: 12,
                            ALTERNATIVE_FONT_3: 13,
                            ALTERNATIVE_FONT_4: 14,
                            ALTERNATIVE_FONT_5: 15,
                            ALTERNATIVE_FONT_6: 16,
                            ALTERNATIVE_FONT_7: 17,
                            ALTERNATIVE_FONT_8: 18,
                            ALTERNATIVE_FONT_9: 19,
                            FRAKTUR: 20,
                            DOUBLY_UNDERLINED: 21,
                            NORMAL_INTENSITY: 22,
                            NEITHER_ITALIC_NOR_BLACKLETTER: 23,
                            NOT_UNDERLINED: 24,
                            NOT_BLINKING: 25,
                            PROPORTIONAL_SPACING: 26,
                            NOT_REVERSED: 27,
                            REVEAL: 28,
                            NOT_CROSSED_OUT: 29,
                            SET_FOREGROUND_COLOR: 38,
                            DEFAULT_FOREGROUND_COLOR: 39,
                            SET_BACKGROUND_COLOR: 48,
                            DEFAULT_BACKGROUND_COLOR: 49,
                            DISABLE_PROPORTIONAL_SPACING: 50,
                            FRAMED: 51,
                            ENCIRCLED: 52,
                            OVERLINED: 53,
                            NEITHER_FRAMED_NOR_ENCIRCLED: 54,
                            NOT_OVERLINED: 55,
                            SET_UNDERLINE_COLOR: 58,
                            DEFAULT_UNDERLINE_COLOR: 59,
                            IDEOGRAM_UNDERLINE_OR_RIGHT_SIDE_LINE: 60,
                            IDEOGRAM_DOUBLE_UNDERLINE: 61,
                            IDEOGRAM_OVERLINE_OR_LEFT_SIDE_LINE: 62,
                            IDEOGRAM_DOUBLE_OVERLINE: 63,
                            IDEOGRAM_STRESS_MARKING: 64,
                            NO_IDEOGRAM_ATTRIBUTES: 65,
                            SUPERSCRIPT: 73,
                            SUBSCRIPT: 74,
                            NEITHER_SUPERSCRIPT_NOR_SUBSCRIPT: 75,
                        };

                        Object.assign(this.inspect, {
                            srg: srg,
                            /**
                             * @see http://en.wikipedia.org/wiki/ANSI_escape_code#graphics
                             */
                            colors: {
                                bold: [ srg.BOLD, srg.NORMAL_INTENSITY ],
                                italic: [ srg.ITALIC, srg.NEITHER_ITALIC_NOR_BLACKLETTER ],
                                underline: [ srg.UNDERLINE, srg.NOT_UNDERLINED ],
                                inverse: [ srg.INVERT, srg.NOT_REVERSED ],
                                white: [ srg.fg.WHITE, srg.DEFAULT_FOREGROUND_COLOR ],
                                gray: [ srg.fg.GRAY, srg.DEFAULT_FOREGROUND_COLOR ],
                                grey: [ srg.fg.GRAY, srg.DEFAULT_FOREGROUND_COLOR ],
                                black: [ srg.fg.BLACK, srg.DEFAULT_FOREGROUND_COLOR ],
                                blue: [ srg.fg.BLUE, srg.DEFAULT_FOREGROUND_COLOR ],
                                cyan: [ srg.fg.CYAN, srg.DEFAULT_FOREGROUND_COLOR ],
                                green: [ srg.fg.GREEN, srg.DEFAULT_FOREGROUND_COLOR ],
                                magenta: [ srg.fg.MAGENTA, srg.DEFAULT_FOREGROUND_COLOR ],
                                red: [ srg.fg.RED, srg.DEFAULT_FOREGROUND_COLOR ],
                                yellow: [ srg.fg.YELLOW, srg.DEFAULT_FOREGROUND_COLOR ],
                            },
                            /**
                             * Avoid using 'blue' when printing on a certain platform
                             * like cmd.exe on Microsoft Windows.
                             */
                            styles: {
                                regexp: 'red',
                                date: 'magenta',
                                special: 'cyan',
                                number: 'yellow',
                                boolean: 'yellow',
                                string: 'green',
                                undefined: 'gray',
                                null: 'bold',
                            },
                        });
                    },
                    stylizeWithoutColor(str) {
                        return str;
                    },
                    stylizeWithColor(str, styleType) {
                        let style = this.inspect.styles[styleType];
                        return !style ? str : this.inspect.colors[style]
                            .slice(0, 2) // just in case
                            .map(c => '\x1b[' + c + 'm').join(str);
                    },
                }).getMethod(),
                morseCode: (/* @IIFE */ () => {
                    let _2_ = {
                        morse: {
                            defaultUnit: 100,
                            /* a.k.a short mark or dot */
                            dit: '1',
                            /* a.k.a longer mark or dash */
                            dah: '111',
                            /* between the dots and dashes within a character */
                            intraCharacterGap: '0',
                            /* between letters */
                            shortGap: '000',
                            /* between words */
                            mediumGap: '0000000',
                            charsString: {
                                'A': '·-', 'B': '-···', 'C': '-·-·', 'D': '-··', 'E': '·', 'F': '··-·', 'G': '--·', 'H': '····', 'I': '··',
                                'J': '·---', 'K': '-·-', 'L': '·-··', 'M': '--', 'N': '-·', 'O': '---', 'P': '·--·', 'Q': '--·-', 'R': '·-·',
                                'S': '···', 'T': '-', 'U': '··-', 'V': '···-', 'W': '·--', 'X': '-··-', 'Y': '-·--', 'Z': '--··', '1': '·----',
                                '2': '··---', '3': '···--', '4': '····-', '5': '·····', '6': '-····', '7': '--···', '8': '---··', '9': '----·',
                                '0': '-----', '.': '·-·-·-', ':': '---···', ',': '--··--', ';': '-·-·-·', '?': '··--··', '=': '-···-',
                                '\'': '·----·', '/': '-··-·', '!': '-·-·--', '-': '-····-', '_': '··--·-', '"': '·-··-·', '(': '-·--·',
                                ')': '-·--·-', '$': '···-··-', '&': '·-···', '@': '·--·-·', '+': '·-·-·',
                            },
                            charsPattern: { /* to be parsed by parseMorse() */ },
                        },
                        parseSource(source, timeUnit) {
                            util.ensureStringType(source);

                            this.source = source;
                            this.words = source.split(/\s+/);

                            let defaultUnit = this.morse.defaultUnit;
                            let parsedUnit = typeof timeUnit === 'number' ? timeUnit : defaultUnit;
                            this.timeUnit = Math.max(parsedUnit, defaultUnit);
                        },
                        // @Cache
                        parseMorse() {
                            if (this._morseCharsStringParsed) {
                                return;
                            }

                            Object.entries(this.morse.charsString).forEach((entry) => {
                                let [ char, code ] = entry;
                                this.morse.charsPattern[char] = (/* @IIFE */ () => {
                                    let res = [];
                                    code.split('').forEach((s, i) => {
                                        if (s === '·') {
                                            res.push(this.morse.dit.length * this.timeUnit);
                                        } else if (s === '-') {
                                            res.push(this.morse.dah.length * this.timeUnit);
                                        } else {
                                            throw Error(`Invalid internal morse code: ${code}`);
                                        }
                                        if (i !== code.length - 1) {
                                            res.push(this.morse.intraCharacterGap.length * this.timeUnit);
                                        }
                                    });
                                    return res;
                                })();
                            });

                            this._morseCharsStringParsed = true;
                        },
                        parsePattern() {
                            this.pattern = [];
                            this.code = String();
                            this.words.forEach((word, i) => {
                                word.split('').forEach((letter, j) => {
                                    this.appendVibrationForLetter(letter);
                                    if (j !== word.length - 1) {
                                        this.appendGapForLetters();
                                    }
                                });
                                if (i !== this.words.length - 1) {
                                    this.appendGapForWords();
                                }
                            });
                        },
                        vibrate(delay) {
                            if (this.pattern.length > 0) {
                                scriptRuntime.device.vibrate([ this.parseVibrationDelay(delay) ].concat(this.pattern));
                            }
                        },
                        parseVibrationDelay(delay) {
                            if (typeof delay !== 'number') {
                                return 0;
                            }
                            return Math.max(0, delay);
                        },
                        appendVibrationForLetter(letter) {
                            letter = String(letter).toUpperCase();
                            if (letter.length !== 1) {
                                throw Error(`Invalid letter length: ${letter.length}`);
                            }
                            if (!(letter in this.morse.charsPattern)) {
                                throw Error(`Letter ${letter} is not in the internal dictionary`);
                            }
                            this.pattern = this.pattern.concat(this.morse.charsPattern[letter]);
                            this.code = this.code.concat(this.morse.charsString[letter]);
                        },
                        appendGapForLetters() {
                            this.pattern.push(this.morse.shortGap.length * this.timeUnit);
                            this.code = this.code.concat(this.morse.shortGap.replace(/./g, ' '));
                        },
                        appendGapForWords() {
                            this.pattern.push(this.morse.mediumGap.length * this.timeUnit);
                            this.code = this.code.concat(this.morse.mediumGap.replace(/./g, ' '));
                        },
                    };

                    let morseCode = function (source, timeUnit) {
                        _2_.parseSource(source, timeUnit);
                        _2_.parseMorse();
                        _2_.parsePattern();

                        return {
                            vibrate: _2_.vibrate.bind(_2_),
                            get pattern() {
                                return _2_.pattern;
                            },
                            get code() {
                                return _2_.code;
                            },
                            getPattern() {
                                return this.pattern;
                            },
                            getCode() {
                                return this.code;
                            },
                            toString() {
                                return `MorseCode{code='${this.code}', pattern=[${this.pattern.join(', ')}]}`;
                            },
                        };
                    };

                    Object.assign(morseCode, {
                        getPattern: source => morseCode(source).pattern,
                        getCode: source => morseCode(source).code,
                        vibrate: (source, delay) => morseCode(source).vibrate(delay),
                    });

                    return morseCode;
                })(),
                /**
                 * @param {object} src
                 * @param {object} target
                 * @param {string[]} funcNames
                 */
                __assignFunctions__(src, target, funcNames) {
                    funcNames.forEach(name => target[name] = src[name].bind(src));
                },
                /**
                 * @see Internal.Util.format
                 */
                format() {
                    try {
                        // @Hint by SuperMonster003 on Oct 19, 2022.
                        //  ! typeof is not safe for some proxy object like runtime.ui.layoutInflater
                        if (typeof arguments[0] !== 'string') {
                            return Array.from(arguments).map(o => this.inspect(o)).join('\x20');
                        }
                    } catch (e) {
                        return util.getClass(arguments[0]);
                    }
                    let index = 1;
                    let len = arguments.length;
                    let str = arguments[0].replace(/%[sdj%]/g, (x) => {
                        if (x === '%%') return '%';
                        if (index >= len) return x;
                        switch (x) {
                            case '%s':
                                return String(arguments[index++]);
                            case '%d':
                                return Number(arguments[index++]);
                            case '%j':
                                try {
                                    return JSON.stringify(arguments[index++]);
                                } catch (_) {
                                    return '[Circular]';
                                }
                            default:
                                return x;
                        }
                    });
                    for (let x = arguments[index]; index < len; x = arguments[++index]) {
                        if (x === null || !this.isObject(x)) {
                            str += ' ' + x;
                        } else {
                            str += ' ' + this.inspect(x);
                        }
                    }
                    return str;
                },
                /**
                 * Mark that a method should not be used.
                 * Returns a modified function which warns once by default.
                 * If --no-deprecation is set, then it is a no-op.
                 *
                 * @Overwrite by SuperMonster003 on Apr 21, 2022.
                 * This method is designed for Node.js and not suitable for AutoJs6.
                 *
                 * @example Code before overwrite
                 * function deprecate(fn, msg) {
                 *     if (isUndefined(global.process)) {
                 *         return function () {
                 *             return me.deprecate(fn, msg).apply(this, arguments);
                 *         };
                 *     }
                 *     ...
                 * }
                 */
                deprecate() {
                    _.warn('This method is not designed for AutoJs6.');
                },
                /**
                 * @Overwrite by SuperMonster003 on Apr 21, 2022.
                 * This method is designed for Node.js and not suitable for AutoJs6.
                 *
                 * @example Code before overwrite
                 * function debuglog(set) {
                 *     if (isUndefined(debugEnviron))
                 *         debugEnviron = process.env.NODE_DEBUG || '';
                 *     set = set.toUpperCase();
                 *     if (!debugs[set]) {
                 *         if (new RegExp('\\b' + set + '\\b', 'i').test(debugEnviron)) {
                 *             let pid = process.pid;
                 *             debugs[set] = function () {
                 *                 var msg = me.format.apply(me, arguments);
                 *                 console.error('%s %d: %s', set, pid, msg);
                 *             };
                 *         } else {
                 *             debugs[set] = function () {
                 *             };
                 *         }
                 *     }
                 *     return debugs[set];
                 * }
                 */
                debuglog() {
                    _.warn('This method is not designed for AutoJs6.');
                },
                /**
                 * Method log is just a thin wrapper to console.log that prepends a timestamp
                 */
                log() {
                    _.log('%s - %s', _.getTimestamp(), this.format.apply(this, arguments));
                },
                getClass(o) {
                    return JavaUtils.getClass(o);
                },
                getClassName(o) {
                    return JavaUtils.getClassName(o);
                },
                /**
                 * @param {string | {toString(): string}} src
                 * @param {string | RegExp} pattern
                 * @return {boolean}
                 */
                checkStringParam(src, pattern) {
                    if (typeof pattern === 'string') {
                        pattern = this.assureStringSurroundsWith(pattern, '^', '$');
                    } else if (pattern instanceof RegExp) {
                        pattern = pattern.source;
                    } else {
                        throw Error(`Unknown pattern (${pattern}) for checkStringParam()`);
                    }
                    if (typeof src !== 'string') {
                        if (isNullish(src)) {
                            throw Error(`Param src must be non-nullish`);
                        }
                        if (isPrimitive(src)) {
                            src = Object(src);
                        }
                        if ('toString' in src && typeof src.toString === 'function') {
                            src = src.toString();
                        } else {
                            throw Error(`Param src (non-string) must have the method toString`);
                        }
                    }
                    return new RegExp(pattern, 'i').test(src.trim());
                },
                assureStringStartsWith(s, start) {
                    this.ensureStringType(s, start);
                    if (s.startsWith(start)) {
                        return s;
                    }
                    return start + s;
                },
                assureStringEndsWith(s, end) {
                    this.ensureStringType(s, end);
                    if (s.endsWith(end)) {
                        return s;
                    }
                    return s + end;
                },
                assureStringSurroundsWith(s, start, end) {
                    if (arguments.length === 2) {
                        end = start;
                    } else if (arguments.length !== 3) {
                        throw Error(`Arguments should be at least 2 and not more than 3`);
                    }
                    return this.assureStringEndsWith(this.assureStringStartsWith(s, start), end);
                },
                ensureType(o, type) {
                    if (typeof o !== type) {
                        throw TypeError(`Param ${o} must be type of ${type} instead of ${species(o)}`);
                    }
                },
                ensureStringType() {
                    Array.from(arguments).forEach((o) => {
                        this.ensureType(o, 'string');
                    });
                },
                ensureNumberType() {
                    Array.from(arguments).forEach((o) => {
                        this.ensureType(o, 'number');
                    });
                },
                ensureUndefinedType() {
                    Array.from(arguments).forEach((o) => {
                        this.ensureType(o, 'undefined');
                    });
                },
                ensureBooleanType() {
                    Array.from(arguments).forEach((o) => {
                        this.ensureType(o, 'boolean');
                    });
                },
                ensureSymbolType() {
                    Array.from(arguments).forEach((o) => {
                        this.ensureType(o, 'symbol');
                    });
                },
                ensureBigintType() {
                    Array.from(arguments).forEach((o) => {
                        this.ensureType(o, 'bigint');
                    });
                },
                ensureObjectType() {
                    Array.from(arguments).forEach((o) => {
                        this.ensureType(o, 'object');
                    });
                },
                ensureNonNullObjectType() {
                    Array.from(arguments).forEach((o) => {
                        this.ensureObjectType(o);
                        if (o === null) {
                            throw Error(`Param must be type of object and non-null`);
                        }
                    });
                },
                ensureArrayType() {
                    Array.from(arguments).forEach((o) => {
                        if (!Array.isArray(o)) {
                            throw TypeError(`Param ${o} must be type of Array instead of ${species(o)}`);
                        }
                    });
                },
                toRegular(f) {
                    if (typeof f !== 'function') {
                        throw TypeError('Param f must be a function');
                    }
                    return typeof f.prototype === 'object' ? f : function () {
                        return f.apply(null, arguments);
                    };
                },
                toRegularAndCall(f, o) {
                    return this.toRegular(f).apply(null, Array.from(arguments).slice(1));
                },
                toRegularAndApply(f, args) {
                    if (!Array.isArray(args)) {
                        throw Error('Param args must be an Array');
                    }
                    return this.toRegular(f).apply(null, args);
                },
            };

            return Util;
        })(),
        extendStatics(C, P) {
            return (this.extendStatics = (/* @IIFE */ () => /* @AXR */ ({
                getMethod() {
                    return this.getSetPrototypeOf()
                        || this.getInternalProto()
                        || this.getCopyOwnProperty();
                },
                getSetPrototypeOf() {
                    if (typeof Object.setPrototypeOf === 'function') {
                        return Object.setPrototypeOf;
                    }
                },
                getInternalProto() {
                    if (this.hasInternalProtoCharacter())
                        return function (C, P) {
                            C.__proto__ = P;
                        };
                },
                getCopyOwnProperty() {
                    return function (C, P) {
                        for (let key in P) {
                            if (P.hasOwnProperty(key)) {
                                C[key] = P[key];
                            }
                        }
                    };
                },
                hasInternalProtoCharacter() {
                    return { __proto__: Array.prototype } instanceof Array;
                },
            }).getMethod())()).call(this, C, P);
        },
        setPrototype(C, P) {
            if (P === null) {
                C.prototype = Object.create(null);
            } else {
                let T = function () {
                    this.constructor = C;
                };
                T.prototype = P.prototype;
                C.prototype = new T();
            }
        },
        getTimestamp() {
            let d = new Date();
            let month = [
                'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
            ][d.getMonth()];
            let time = [
                d.getHours().toString().padStart(2, '0'),
                d.getMinutes().toString().padStart(2, '0'),
                d.getSeconds().toString().padStart(2, '0'),
            ].join(':');
            // e.g. "26 Feb 16:19:34"
            return [ d.getDate(), month, time ].join(' ');
        },
        arrayToHash(array) {
            let hash = {};
            array.forEach(o => hash[o] = true);
            return hash;
        },
        /**
         * @param {{
         *     showHidden: boolean;
         *     depth: number;
         *     colors: boolean;
         *     customInspect: boolean;
         *     seen: *[];
         *     stylize: (str: string, styleType?: string) => string;
         * }} ctx
         * @param {{
         *     inspect?: function,
         * } | any} value
         * @param recurseTimes
         * @return {string}
         */
        formatValue(ctx, value, recurseTimes) {
            // Provide a hook for user-specified inspect functions.
            // Check that value is an object with an inspect function on it

            // noinspection JSIncompatibleTypesComparison
            if (ctx.customInspect
                && species.isObject(value)
                && typeof value.inspect === 'function'
                // Filter out the util module, it's inspect function is special
                && value.inspect !== util.inspect
                // Also filter out any prototype objects using the circular check.
                && !(value.constructor && value.constructor.prototype === value)
            ) {
                let ret = value.inspect(recurseTimes, ctx);
                if (typeof ret !== 'string') {
                    ret = this.formatValue(ctx, ret, recurseTimes);
                }
                return ret;
            }

            if (isJavaClass(value)) {
                return `[JavaClass ${util.getClassName(value)}]`;
            }

            if (isJavaObject(value)) {
                if (value.getClass().isArray()) {
                    return this.formatJavaArray(value);
                }
            }

            // Primitive types cannot have properties
            let primitive = this.formatPrimitive(ctx, value);
            if (primitive) {
                return primitive;
            }

            // Look up the keys of the object.
            let keys = Object.keys(value);
            let visibleKeys = this.arrayToHash(keys);

            if (ctx.showHidden) {
                keys = Object.getOwnPropertyNames(value);
            }

            // IE doesn't make error fields non-enumerable
            // http://msdn.microsoft.com/en-us/library/ie/dww52sbt(v=vs.94).aspx
            if (util.isError(value) && (keys.includes('message') || keys.includes('description'))) {
                return this.formatError(value);
            }

            // Some type of object without properties can be shortcut.
            if (keys.length === 0) {
                if (typeof value === 'function') {
                    let n = 'name' in value && value.name ? ` ${value.name}()` : '';
                    return ctx.stylize(`[Function${n}]`, 'special');
                }
                if (util.isRegExp(value)) {
                    return ctx.stylize(RegExp.prototype.toString.call(value), 'regexp');
                }
                if (util.isDate(value)) {
                    return ctx.stylize(Date.prototype.toString.call(value), 'date');
                }
                if (util.isError(value)) {
                    return this.formatError(value);
                }
            }

            let base = '';
            let array = false;
            let braces = [ '{', '}' ];

            // Make Array say that they are Array
            if (util.isArray(value)) {
                array = true;
                braces = [ '[', ']' ];
            }

            // Make functions say that they are functions
            if (typeof value === 'function') {
                let n = 'name' in value && value.name ? ` ${value.name}()` : '';
                base = ` [Function${n}]`;
            }

            // Make RegExps say that they are RegExps
            if (util.isRegExp(value)) {
                base = ` ${RegExp.prototype.toString.call(value)}`;
            }

            // Make dates with properties first say the date
            if (util.isDate(value)) {
                base = ` ${Date.prototype.toUTCString.call(value)}`;
            }

            // Make error with message first say the error
            if (util.isError(value)) {
                base = ` ${this.formatError(value)}`;
            }

            if (keys.length === 0 && (!array || value.length === 0)) {
                return braces[0] + base + braces[1];
            }

            if (recurseTimes < 0) {
                if (util.isRegExp(value)) {
                    return ctx.stylize(RegExp.prototype.toString.call(value), 'regexp');
                } else {
                    return ctx.stylize('[Object]', 'special');
                }
            }

            ctx.seen.push(value);

            let output;
            if (array) {
                output = this.formatArray(ctx, value, recurseTimes, visibleKeys, keys);
            } else {
                try {
                    output = keys.map((key) => {
                        return this.formatProperty(ctx, value, recurseTimes, visibleKeys, key, array);
                    });
                } catch (err) {
                    if (isJavaObject(value)) {
                        let unwrapped = unwrapJavaObject(value);
                        if (unwrapped !== value) {
                            return `[JavaObject: ${unwrapped}]`;
                        }
                        if ('toString' in value && typeof value.toString === 'function') {
                            return value.toString();
                        }
                        return value.getClass().getName() + '@' + java.lang.Integer.toHexString(value.hashCode());
                    }
                    try {
                        return String(value).length > 0 ? String(value) : String();
                    } catch (e) {
                        return Object.prototype.toString.call(value);
                    }
                }
            }

            ctx.seen.pop();

            return this.reduceToSingleString(output, base, braces);
        },
        formatPrimitive(ctx, value) {
            if (util.isUndefined(value)) {
                return ctx.stylize('undefined', 'undefined');
            }
            if (util.isString(value)) {
                let simple = '\'' + JSON.stringify(value).replace(/^"|"$/g, '')
                    .replace(/'/g, '\\\'')
                    .replace(/\\"/g, '"') + '\'';
                return ctx.stylize(simple, 'string');
            }
            if (util.isNumber(value)) {
                return ctx.stylize('' + value, 'number');
            }
            if (util.isBoolean(value)) {
                return ctx.stylize('' + value, 'boolean');
            }
            // For some reason typeof null is "object", so special case here.
            if (util.isNull(value)) {
                return ctx.stylize('null', 'null');
            }
        },
        formatJavaArray(javaArray) {
            return Arrays.toString(javaArray);
        },
        formatError(value) {
            return '[' + Error.prototype.toString.call(value) + ']';
        },
        formatArray(ctx, value, recurseTimes, visibleKeys, keys) {
            let output = [];
            for (let i = 0, l = value.length; i < l; ++i) {
                if (value.hasOwnProperty(String(i))) {
                    output.push(this.formatProperty(ctx, value, recurseTimes, visibleKeys, String(i), true));
                } else {
                    output.push('');
                }
            }
            keys.forEach((key) => {
                if (!key.match(/^\d+$/)) {
                    output.push(this.formatProperty(ctx, value, recurseTimes, visibleKeys, key, true));
                }
            });
            return output;
        },
        formatProperty(ctx, value, recurseTimes, visibleKeys, key, array) {
            let name, str;
            let desc = Object.getOwnPropertyDescriptor(value, key) || { value: value[key] };
            if (desc.get) {
                if (desc.set) {
                    str = ctx.stylize('[Getter/Setter]', 'special');
                } else {
                    str = ctx.stylize('[Getter]', 'special');
                }
            } else {
                if (desc.set) {
                    str = ctx.stylize('[Setter]', 'special');
                }
            }
            if (!visibleKeys.hasOwnProperty(key)) {
                name = '[' + key + ']';
            }
            if (!str) {
                if (ctx.seen.indexOf(desc.value) < 0) {
                    if (util.isNull(recurseTimes)) {
                        str = this.formatValue(ctx, desc.value, null);
                    } else {
                        str = this.formatValue(ctx, desc.value, recurseTimes - 1);
                    }
                    if (str.indexOf('\n') > -1) {
                        if (array) {
                            str = str.split('\n').map(function (line) {
                                return '  ' + line;
                            }).join('\n').slice(2);
                        } else {
                            str = '\n' + str.split('\n').map(function (line) {
                                return '   ' + line;
                            }).join('\n');
                        }
                    }
                } else {
                    str = ctx.stylize('[Circular]', 'special');
                }
            }
            if (util.isUndefined(name)) {
                if (array && key.match(/^\d+$/)) {
                    return str;
                }
                name = JSON.stringify('' + key);
                if (name.match(/^"([a-zA-Z_]\w*)"$/)) {
                    name = name.slice(1, name.length - 1);
                    name = ctx.stylize(name, 'name');
                } else {
                    name = name.replace(/'/g, '\\\'')
                        .replace(/\\"/g, '"')
                        .replace(/(^"|"$)/g, '\'');
                    name = ctx.stylize(name, 'string');
                }
            }

            return name + ': ' + str;
        },
        reduceToSingleString(output, base, braces) {
            let numLinesEst = 0;
            let length = output.reduce(function (prev, cur) {
                numLinesEst++;
                if (cur.includes('\n')) {
                    numLinesEst++;
                }
                return prev + cur.replace(/\x1b\[\d+?m/g, '').length + 1;
            }, 0);

            if (length > 60) {
                return braces[0] +
                    (base === '' ? '' : base + '\n ') + ' ' +
                    output.join(',\n  ') + ' ' +
                    braces[1];
            }
            return braces[0] + base + ' ' + output.join(', ') + ' ' + braces[1];
        },
        typeToClass(type) {
            switch (type) {
                case 'string':
                    return java.lang.String;
                case 'int':
                    return java.lang.Integer.TYPE;
                case 'long':
                    return java.lang.Long.TYPE;
                case 'double':
                    return java.lang.Double.TYPE;
                case 'char':
                    return java.lang.Character.TYPE;
                case 'byte':
                    return java.lang.Byte.TYPE;
                case 'float':
                    return java.lang.Float.TYPE;
                default:
                    return typeof type === 'string' ? Packages[type] : type;
            }
        },
        log() {
            scriptRuntime.console.log(util.format.apply(util, arguments));
        },
        warn() {
            scriptRuntime.console.warn(util.format.apply(util, arguments));
        },
    };

    /**
     * @type {Internal.Util}
     */
    const util = new _.Util();

    return util;
};