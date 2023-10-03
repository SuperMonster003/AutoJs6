// noinspection JSUnusedGlobalSymbols

/* Overwritten protection. */

let { util } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Colors}
 */
module.exports = function (scriptRuntime, scope) {
    /**
     * @type {org.autojs.autojs.core.image.Colors}
     */
    const rtColors = scriptRuntime.colors;

    const ColorStateList = android.content.res.ColorStateList;
    const ColorDetector = org.autojs.autojs.core.image.ColorDetector;
    const ThemeColor = org.autojs.autojs.theme.ThemeColor;

    let _ = {
        Color: (/* @IIFE */ () => {
            /**
             * @implements {Internal.Color}
             */
            const Color = function (color) {

                // @Hint by SuperMonster003 on Mar 29, 2023.
                //  ! `new.target` hasn't been support by Rhino yet.
                // let isInvokedAsConstructor = new.target === Color;
                let isInvokedAsConstructor = this instanceof Color;

                if (!isInvokedAsConstructor) {
                    let args = [ Color ].concat(Array.from(arguments));
                    return new (Function.prototype.bind.apply(Color, args));
                }
                this.color = colors.BLACK;
                if (arguments.length === 4) {
                    let [ red, green, blue, alpha ] = Array.from(arguments);
                    this.color = colors.toInt(colors.rgba(red, green, blue, alpha));
                    return this;
                }
                if (arguments.length === 3) {
                    let [ red, green, blue ] = Array.from(arguments);
                    this.color = colors.toInt(colors.rgb(red, green, blue));
                    return this;
                }
                if (arguments.length === 1) {
                    let c = arguments[0];

                    /* Just in case. */
                    if (c instanceof Color) {
                        this.color = c.color;
                    } else {
                        this.color = c instanceof ThemeColor
                            ? colors.toInt(c.getColorPrimary())
                            : colors.toInt(c);
                    }
                }
            };

            Color.prototype = {
                constructor: Color,
                toString() {
                    return this.summary();
                },
                summary() {
                    return colors.summary.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toHex() {
                    return colors.toHex.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toFullHex() {
                    return colors.toFullHex.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toInt() {
                    return colors.toInt.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                setAlpha(alpha) {
                    this.color = colors.setAlpha(this.color, alpha);
                    return this;
                },
                setAlphaRelative(percentage) {
                    return this.setAlpha(this.getAlpha() * _.parseRelativePercentage(percentage));
                },
                getAlpha() {
                    return colors.alpha(this.color);
                },
                alpha() {
                    return this.getAlpha();
                },
                getAlphaDouble() {
                    return colors.alphaDouble(this.color);
                },
                alphaDouble() {
                    return this.getAlphaDouble();
                },
                removeAlpha() {
                    this.color = colors.removeAlpha(this.color);
                    return this;
                },
                setRed(red) {
                    this.color = colors.setRed(this.color, red);
                    return this;
                },
                setRedRelative(percentage) {
                    return this.setRed(this.getRed() * _.parseRelativePercentage(percentage));
                },
                getRed() {
                    return colors.red(this.color);
                },
                red() {
                    return this.getRed();
                },
                getRedDouble() {
                    return colors.redDouble(this.color);
                },
                redDouble() {
                    return this.getRedDouble();
                },
                removeRed() {
                    this.color = colors.removeRed(this.color);
                    return this;
                },
                setGreen(green) {
                    this.color = colors.setGreen(this.color, green);
                    return this;
                },
                setGreenRelative(percentage) {
                    return this.setGreen(this.getGreen() * _.parseRelativePercentage(percentage));
                },
                getGreen() {
                    return colors.green(this.color);
                },
                green() {
                    return this.getGreen();
                },
                getGreenDouble() {
                    return colors.greenDouble(this.color);
                },
                greenDouble() {
                    return this.getGreenDouble();
                },
                removeGreen() {
                    this.color = colors.removeGreen(this.color);
                    return this;
                },
                setBlue(blue) {
                    this.color = colors.setBlue(this.color, blue);
                    return this;
                },
                setBlueRelative(percentage) {
                    return this.setBlue(this.getBlue() * _.parseRelativePercentage(percentage));
                },
                getBlue() {
                    return colors.blue(this.color);
                },
                blue() {
                    return this.getBlue();
                },
                getBlueDouble() {
                    return colors.blueDouble(this.color);
                },
                blueDouble() {
                    return this.getBlueDouble();
                },
                removeBlue() {
                    this.color = colors.removeBlue(this.color);
                    return this;
                },
                setRgb() {
                    let [ r, g, b ] = colors.rgb.apply(colors, arguments);
                    this.color = colors.rgba(r, g, b, colors.alpha(this.color));
                    return this;
                },
                setArgb() {
                    this.color = colors.argb.apply(colors, arguments);
                    return this;
                },
                setRgba() {
                    this.color = colors.rgba.apply(colors, arguments);
                    return this;
                },
                setHsv() {
                    let [ h, s, v ] = colors.hsv.apply(colors, arguments);
                    this.color = colors.hsva(h, s, v, colors.alpha(this.color));
                    return this;
                },
                setHsva() {
                    this.color = colors.hsva.apply(colors, arguments);
                    return this;
                },
                setHsl() {
                    let [ h, s, l ] = colors.hsl.apply(colors, arguments);
                    this.color = colors.hsla(h, s, l, colors.alpha(this.color));
                    return this;
                },
                setHsla() {
                    this.color = colors.hsla.apply(colors, arguments);
                    return this;
                },
                toRgb() {
                    return colors.toRgb.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toRgba() {
                    return colors.toRgba.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toArgb() {
                    return colors.toArgb.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toHsv() {
                    return colors.toHsv.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toHsva() {
                    return colors.toHsva.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toHsl() {
                    return colors.toHsl.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                toHsla() {
                    return colors.toHsla.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                isSimilar() {
                    return colors.isSimilar.apply(colors, [ this.color ].concat(Array.from(arguments)));
                },
                isEqual(other, alphaMatters) {
                    return colors.isEqual(this.color, other, alphaMatters);
                },
                equals(other) {
                    // noinspection JSDeprecatedSymbols
                    return colors.equals(this.color, other);
                },
                toColorStateList() {
                    return colors.toColorStateList(this.color);
                },
                setPaintColor(paint) {
                    colors.setPaintColor(paint, this.color);
                    return this;
                },
                luminance() {
                    return colors.luminance(this.color);
                },
            };

            return Color;
        })(),
        Colors: (/* @IIFE */ () => {
            /**
             * @extends Internal.Colors
             */
            const Colors = function () {
                /* Empty body. */
            };

            Colors.prototype = {
                constructor: Colors,
                android: ColorTable.Android,
                web: ColorTable.Web,
                css: ColorTable.Css,
                material: ColorTable.Material,
                alpha(color, options) {
                    let opt = options || {};
                    if (isNullish(opt.max) || opt.max === 255) {
                        return this.toInt(color) >>> 24;
                    }
                    if (opt.max === 1) {
                        return this.alphaDouble(color);
                    }
                    throw TypeError(`Option "max" specified must be either 1 or 255 instead of ${opt.max} for colors.alpha`);
                },
                getAlpha() {
                    return this.alpha.apply(this, arguments);
                },
                alphaDouble(color) {
                    return _.toDoubleComponent(this.alpha(color));
                },
                getAlphaDouble() {
                    return this.alphaDouble.apply(this, arguments);
                },
                setAlpha(color, alpha) {
                    let [ r, g, b ] = this.toRgb(color);
                    return this.toInt(this.argb(alpha, r, g, b));
                },
                setAlphaRelative(color, percentage) {
                    return this.setAlpha(color, this.getAlpha(color) * _.parseRelativePercentage(percentage));
                },
                removeAlpha(color) {
                    return this.setAlpha(color, 0);
                },
                red(color, options) {
                    let opt = options || {};
                    if (isNullish(opt.max) || opt.max === 255) {
                        return (this.toInt(color) >> 16) & 0xFF;
                    }
                    if (opt.max === 1) {
                        return this.redDouble(color);
                    }
                    throw TypeError(`Option "max" specified must be either 1 or 255 instead of ${opt.max} for colors.red`);
                },
                getRed() {
                    return this.red.apply(this, arguments);
                },
                redDouble(color) {
                    return _.toDoubleComponent(this.red(color));
                },
                getRedDouble() {
                    return this.redDouble.apply(this, arguments);
                },
                setRed(color, red) {
                    let [ a, , g, b ] = this.toArgb(color);
                    return this.toInt(this.argb(a, red, g, b));
                },
                setRedRelative(color, percentage) {
                    return this.setRed(color, this.getRed(color) * _.parseRelativePercentage(percentage));
                },
                removeRed(color) {
                    return this.setRed(color, 0);
                },
                green(color, options) {
                    let opt = options || {};
                    if (isNullish(opt.max) || opt.max === 255) {
                        return (this.toInt(color) >> 8) & 0xFF;
                    }
                    if (opt.max === 1) {
                        return this.greenDouble(color);
                    }
                    throw TypeError(`Option "max" specified must be either 1 or 255 instead of ${opt.max} for colors.green`);
                },
                getGreen() {
                    return this.green.apply(this, arguments);
                },
                greenDouble(color) {
                    return _.toDoubleComponent(this.green(color));
                },
                getGreenDouble() {
                    return this.greenDouble.apply(this, arguments);
                },
                setGreen(color, green) {
                    let [ a, r, , b ] = this.toArgb(color);
                    return this.toInt(this.argb(a, r, green, b));
                },
                setGreenRelative(color, percentage) {
                    return this.getGreen(color, this.getGreen(color) * _.parseRelativePercentage(percentage));
                },
                removeGreen(color) {
                    return this.setGreen(color, 0);
                },
                blue(color, options) {
                    let opt = options || {};
                    if (isNullish(opt.max) || opt.max === 255) {
                        return this.toInt(color) & 0xFF;
                    }
                    if (opt.max === 1) {
                        return this.blueDouble(color);
                    }
                    throw TypeError(`Option "max" specified must be either 1 or 255 instead of ${opt.max} for colors.blue`);
                },
                getBlue() {
                    return this.blue.apply(this, arguments);
                },
                blueDouble(color) {
                    return _.toDoubleComponent(this.blue(color));
                },
                getBlueDouble() {
                    return this.blueDouble.apply(this, arguments);
                },
                setBlue(color, blue) {
                    let [ a, r, g ] = this.toArgb(color);
                    return this.toInt(this.argb(a, r, g, blue));
                },
                setBlueRelative(color, percentage) {
                    return this.setBlue(color, this.getBlue(color) * _.parseRelativePercentage(percentage));
                },
                removeBlue(color) {
                    return this.setBlue(color, 0);
                },
                toInt(color) {
                    if (color instanceof _.Color) {
                        color = color.color;
                    }
                    return ColorUtils.toInt.apply(ColorUtils, [ color ]);
                },
                toHex(color, alphaOrLength) {
                    if (isNullish(alphaOrLength)) {
                        return ColorUtils.toHex.apply(ColorUtils, [ color ]);
                    } else {
                        return ColorUtils.toHex.apply(ColorUtils, [ color, alphaOrLength ]);
                    }
                },
                toFullHex(color) {
                    return ColorUtils.toFullHex.apply(ColorUtils, [ color ]);
                },
                /**
                 * Get hex code string of a color.
                 *
                 * @deprecated
                 * @replaceWith colors.toHex
                 *
                 * @Overwrite by SuperMonster003 on Apr 22, 2022.
                 * Substitution of legacy method.
                 * Signature: colors.toString(color: number): string
                 */
                toString() {
                    return this.toHex.apply(this, arguments);
                },
                rgb() {
                    if (Array.isArray(arguments[0])) {
                        return this.rgb.apply(this, arguments[0]);
                    }
                    if (arguments.length === 3) {
                        let [ r, g, b ] = _.toUnit8RgbList(arguments);
                        return rtColors.rgb(r, g, b);
                    } else /* arguments.length was taken as 1 */ {
                        return this.toInt(this.toHex(arguments[0], 6));
                    }
                },
                argb() {
                    if (Array.isArray(arguments[0])) {
                        return this.argb.apply(this, arguments[0]);
                    }
                    if (arguments.length === 4) {
                        let [ r, g, b ] = _.toUnit8RgbList(Array.from(arguments).slice(1));
                        return rtColors.argb(_.parseDoubleComponent(/* a = */ arguments[0]), r, g, b);
                    } else /* arguments.length was taken as 1 */ {
                        return this.toInt(this.toHex(arguments[0], 8));
                    }
                },
                rgba() {
                    if (Array.isArray(arguments[0])) {
                        if (arguments.length === 1) {
                            let [ r, g, b, a ] = arguments[0];
                            return this.rgba(r, g, b, a);
                        }
                        let [ r, g, b ] = arguments[0];
                        return this.rgba(r, g, b, /* a = */ arguments[1]);
                    }
                    if (arguments.length === 4) {
                        let [ r, g, b ] = _.toUnit8RgbList(Array.from(arguments).slice(0, 3));
                        return rtColors.argb(_.parseDoubleComponent(/* a = */ arguments[3]), r, g, b);
                    } else /* arguments.length was taken as 1 */ {
                        if (typeof arguments[0] === 'string' && arguments[0].startsWith('#')) {
                            let colorString = this.toFullHex(arguments[0]);
                            return this.toInt(colorString.replace(/^(#)(\w{6})(\w{2}$)/, '$1$3$2'));
                        }
                        return this.argb(this.toFullHex(arguments[0]));
                    }
                },
                hsv(h, s, v) {
                    if (Array.isArray(arguments[0])) {
                        let [ h, s, v ] = arguments[0];
                        return this.hsv(h, s, v);
                    }
                    if (arguments.length < 3) {
                        throw TypeError(`Can't convert hsv arguments [${Array.from(arguments)}] to color int.`);
                    }
                    let hsvComponents = [ _.parseHueComponent(h), _.toPercentage(s), _.toPercentage(v) ];
                    return android.graphics.Color.HSVToColor(hsvComponents);
                },
                hsva(h, s, v, a) {
                    if (Array.isArray(arguments[0])) {
                        if (arguments.length === 1) {
                            let [ h, s, v, a ] = arguments[0];
                            return this.hsva(h, s, v, a);
                        }
                        let [ h, s, v ] = arguments[0];
                        return this.hsva(h, s, v, /* a = */ arguments[1]);
                    }
                    if (arguments.length < 4) {
                        throw TypeError(`Can't convert hsva arguments [${Array.from(arguments)}] to color int.`);
                    }
                    let hsvComponents = [ _.parseHueComponent(h), _.toPercentage(s), _.toPercentage(v) ];
                    return android.graphics.Color.HSVToColor(_.parseDoubleComponent(a), hsvComponents);
                },
                /**
                 * @Reference to https://stackoverflow.com/questions/36721830/convert-hsl-to-rgb-and-hex
                 */
                hsl(h, s, l) {
                    if (Array.isArray(arguments[0])) {
                        return this.hsl.apply(this, arguments[0]);
                    }
                    if (arguments.length !== 3) {
                        throw TypeError(`Can't convert hsl arguments [${Array.from(arguments)}] to color int.`);
                    }
                    let hslComponents = [ _.parseHueComponent(h), _.toPercentage(s), _.toPercentage(l) ];
                    return androidx.core.graphics.ColorUtils.HSLToColor(hslComponents);
                },
                hsla(h, s, l, a) {
                    if (Array.isArray(arguments[0])) {
                        if (arguments.length === 1) {
                            let [ h, s, l, a ] = arguments[0];
                            return this.hsla(h, s, l, a);
                        }
                        let [ h, s, l ] = arguments[0];
                        return this.hsla(h, s, l, /* a = */ arguments[1]);
                    }
                    if (arguments.length !== 4) {
                        throw TypeError(`Can't convert hsla arguments [${Array.from(arguments)}] to color int.`);
                    }
                    let cInt = this.hsl(h, s, l);
                    return colors.rgba(colors.red(cInt), colors.green(cInt), colors.blue(cInt), _.parseDoubleComponent(a));
                },
                toRgb(color) {
                    return [ this.red(color), this.green(color), this.blue(color) ];
                },
                toRgba(color, options) {
                    let opt = options || {};
                    return [ this.red(color), this.green(color), this.blue(color), this.alpha(color, { max: opt.maxAlpha }) ];
                },
                toArgb(color, options) {
                    let opt = options || {};
                    return [ this.alpha(color, { max: opt.maxAlpha }), this.red(color), this.green(color), this.blue(color) ];
                },
                toHsv(color) {
                    if (arguments.length === 4) {
                        let [ r, g, b, hsv ] = arguments;
                        return this.toHsv(this.rgb(r, g, b), hsv);
                    }
                    if (arguments.length === 3) {
                        let hsv = util.java.array('float', 3);
                        let [ r, g, b ] = arguments;
                        return this.toHsv(this.rgb(r, g, b), hsv);
                    }
                    if (arguments.length === 2) {
                        let [ , hsv ] = arguments;
                        _.ensureJavaArray(hsv, 3);
                        let r = this.red(color);
                        let g = this.green(color);
                        let b = this.blue(color);
                        rtColors.RGBToHSV(r, g, b, hsv);
                        return Array.from(hsv);
                    } else /* arguments.length taken as 1 . */ {
                        let hsv = util.java.array('float', 3);
                        return this.toHsv(this.rgb(color), hsv);
                    }
                },
                toHsva(color) {
                    if (arguments.length === 5) {
                        let [ r, g, b, a, hsva ] = arguments;
                        return this.toHsva(this.rgba(r, g, b, a), hsva);
                    }
                    if (arguments.length === 4) {
                        let hsva = util.java.array('float', 4);
                        let [ r, g, b, a ] = arguments;
                        return this.toHsva(this.rgba(r, g, b, a), hsva);
                    }
                    if (arguments.length === 2) {
                        let [ , hsva ] = arguments;
                        _.ensureJavaArray(hsva, 4);

                        let r = this.red(color);
                        let g = this.green(color);
                        let b = this.blue(color);
                        let a = this.alpha(color);

                        let hsv = util.java.array('float', 3);
                        rtColors.RGBToHSV(r, g, b, hsv);

                        let newHsva = Array.from(hsv).concat(_.toDoubleComponent(a));
                        newHsva.forEach((val, idx) => hsva[idx] = val);

                        return newHsva;
                    } else /* arguments.length taken as 1 . */ {
                        let hsva = util.java.array('float', 4);
                        return this.toHsva(color, hsva);
                    }
                },
                toHsl(color) {
                    if (arguments.length === 4) {
                        let [ r, g, b, hsl ] = arguments;
                        return this.toHsl(this.rgb(r, g, b), hsl);
                    }
                    if (arguments.length === 3) {
                        let hsv = util.java.array('float', 3);
                        let [ r, g, b ] = arguments;
                        return this.toHsl(this.rgb(r, g, b), hsv);
                    }
                    if (arguments.length === 2) {
                        let [ , hsl ] = arguments;
                        _.ensureJavaArray(hsl, 3);
                        let r = this.red(color);
                        let g = this.green(color);
                        let b = this.blue(color);
                        androidx.core.graphics.ColorUtils.RGBToHSL(r, g, b, hsl);
                        return Array.from(hsl);
                    } else /* arguments.length taken as 1 . */ {
                        let hsv = util.java.array('float', 3);
                        return this.toHsl(this.rgb(color), hsv);
                    }
                },
                toHsla(color) {
                    if (arguments.length === 4) {
                        let [ r, g, b, a ] = arguments;
                        let [ h, s, l ] = this.toHsl(this.rgb(r, g, b));
                        return [ h, s, l, _.toDoubleComponent(a) ];
                    } else /* arguments.length taken as 1 . */ {
                        let r = this.red(color);
                        let g = this.green(color);
                        let b = this.blue(color);
                        let a = this.alpha(color);
                        return this.toHsla(r, g, b, a);
                    }
                },
                isSimilar(colorA, colorB, threshold, algorithm) {
                    if (species.isObject(arguments[2] /* as options */)) {
                        /**
                         * @type {object}
                         */
                        const options = arguments[2];
                        let optThreshold = (/* @IIFE */ () => {
                            if ('threshold' in options) {
                                if ('similarity' in options) {
                                    throw TypeError(`Properties threshold and similarity can't be specified at the same time`);
                                }
                                return options.threshold;
                            }
                            if ('similarity' in options) {
                                return Math.round((1 - options.similarity) * 255);
                            }
                            return _.constants.DEF_COLOR_THRESHOLD;
                        })();
                        return this.isSimilar(colorA, colorB, optThreshold, options.algorithm);
                    }
                    return ColorDetector
                        .get(
                            this.toInt(colorA),
                            algorithm || _.constants.DEF_COLOR_ALGORITHM,
                            _.parseNumber(threshold, _.constants.DEF_COLOR_THRESHOLD))
                        .detectsColor(
                            this.red(colorB),
                            this.green(colorB),
                            this.blue(colorB));
                },
                isEqual(colorA, colorB, alphaMatters) {
                    if (alphaMatters === undefined || alphaMatters === false) {
                        return this.rgb(colorA) === this.rgb(colorB);
                    }
                    if (alphaMatters === true) {
                        return this.toInt(colorA) === this.toInt(colorB);
                    }
                    throw TypeError(`Invalid argument isStrict (value: ${alphaMatters}, type: ${species(alphaMatters)}) for colors.isEqual`);
                },
                /**
                 * Get a ColorStateList containing a single color or more.
                 */
                toColorStateList(color) {
                    if (arguments.length === 0) {
                        throw TypeError(`Method "colors.toColorStateList" must have at least one argument`);
                    }
                    if (arguments.length === 1) {
                        return ColorStateList.valueOf(this.toInt(color));
                    }
                    return new ColorStateList(/* empty states */ [ [] ], Array.from(arguments).map(c => this.toInt(c)));
                },
                setPaintColor(paint, color) {
                    if (util.version.sdkInt >= util.versionCodes.Q) {
                        paint.setARGB.apply(paint, this.toArgb(color));
                    } else {
                        paint.setColor(this.toInt(color));
                    }
                },
                luminance(color) {
                    return rtColors.luminance(this.toInt(color));
                },
                build() {
                    let args = [ _.Color ].concat(Array.from(arguments));
                    return new (Function.prototype.bind.apply(_.Color, args));
                },
                summary(color) {
                    let [ r, g, b, a ] = this.toRgba(color);
                    let niceA = _.toDoubleComponent(a).toFixed(2).replace(/0$/, '');
                    return `hex(${this.toHex(color)}), rgba(${r},${g},${b}/${niceA}), int(${this.toInt(color)})`;
                },
            };

            Object.setPrototypeOf(Colors.prototype, rtColors);

            return Colors;
        })(),
        constants: {
            DEF_COLOR_THRESHOLD: 4,
            DEF_COLOR_ALGORITHM: 'diff',
        },
        selfAugment() {
            ( /* @IIFE(assignColorsFromColorTables) */ () => {
                let cache = {};

                void /* color table names */ [
                    'Android',
                    'Css',
                    'Web',
                    'Material',
                ].forEach((tableName) => {
                    const colorTable = ColorTable[tableName];
                    Object.keys(colorTable).forEach((k) => {
                        if (!(k in cache)) {
                            const color = colorTable[k];
                            if (typeof color === 'number') {
                                cache[k] = color;
                            }
                        }
                    });
                });

                // @Alter by SuperMonster003 on Feb 7, 2023
                //  ! Object.assign(_.Colors.prototype, cache);
                Object.assign(colors, cache);
            })();
        },
        scopeAugment() {
            /**
             * @Caution by SuperMonster003 on Apr 23, 2022.
             * Bind "this" will make bound function lose appended properties.
             *
             * @example
             * let f = function () {}; f.code = 1;
             * let g = f; console.log(g.code); // 1
             * let h = f.bind({}); console.log(h.code); // undefined
             */
            scope.colors = colors;
            scope.Color = _.Color;
        },
        /**
         * [0..255] or [0..100] to [0..1].
         * Number 1 would be taken as 1 itself (100%).
         */
        toDouble(o, by) {
            if (typeof o !== 'number') {
                throw TypeError('Argument o must be of type number');
            }
            if (Numberx.check(0, '<=', o, '<=', 1)) {
                return o;
            }
            if (Numberx.check(1, '<', o, '<=', by)) {
                return o / by;
            }
            throw TypeError('Argument o must be in the range 0..255');
        },
        /**
         * [0..1) or other to [0..255].
         * Number 1 would be taken as 1 itself (0x1).
         *
         * @param {number} o
         * @returns {number}
         */
        toUnit8(o) {
            if (typeof o !== 'number') {
                let num = Numberx.parseAny(o);
                if (!isNaN(num)) {
                    return this.toUnit8(num);
                }
                throw TypeError(`Argument o (${o}) can't be parsed as a number`);
            }
            if (o >= 1) {
                return Math.min(255, Math.round(o));
            }
            if (o < 0) {
                throw TypeError('Number should not be negative.');
            }
            return Math.round(o * 255);
        },
        /**
         * @param {IArguments | number[]} a
         * @returns {number[]}
         */
        toComponents(a) {
            return Array.from(a).map((o) => {
                if (typeof o === 'number') {
                    return o;
                }
                if (typeof o === 'string') {
                    let num = Numberx.parseAny(o);
                    if (isNaN(num)) {
                        throw TypeError(`Can't convert ${a} into a color component`);
                    }
                    return num;
                }
            });
        },
        /**
         * @param {IArguments | number[]} components
         * @returns {number[]}
         */
        toUnit8RgbList(components) {
            let compList = this.toComponents(components);
            let isPercentNums = compList.every(x => x <= 1) && !compList.every(x => x === 1);
            if (isPercentNums) {
                return compList.map(x => x === 1 ? 255 : this.toUnit8(x));
            }
            return compList.map(x => this.toUnit8(x));
        },
        /**
         * [0..1] or [0..255] to hex string like 'FF'.
         * Number 1 would be taken as 1 itself (0x1).
         */
        toUnit8Hex(o, maxLength) {
            return this.toUnit8(o).toString(16).padStart(maxLength || 2, '0');
        },
        /**
         * Number to percentage like 0.8 .
         */
        toPercentage(x) {
            if (typeof x !== 'number') {
                let num = Numberx.parseAny(x);
                if (!isNaN(num)) {
                    x = num;
                }
            }
            return Numberx.check(0, '<=', x, '<=', 1) ? x : x / 100;
        },
        toJavaIntegerRange(x) {
            let t = 2 ** 32;
            let min = -(2 ** 31);
            let max = 2 ** 31 - 1;
            return Numberx.clampTo(x, [ min, max ], t);
        },
        ensureJavaArray(arr, length) {
            if (!/^\[[A-Z]/.test(util.getClassName(arr))) {
                throw TypeError('Param arr must be a Java array');
            }
            if (typeof length === 'number' && arr.length !== length) {
                throw Error(`Param arr must be of length ${length}`);
            }
        },
        parseHueComponent(component) {
            let c = Numberx.parseAny(component);
            if (isNaN(c)) {
                throw TypeError(`Can't convert ${component} into hue component`);
            }
            if (Math.abs(c) < 1) {
                c *= 360;
            }
            return Numberx.clampTo(c, [ 0, 360 ]);
        },
        parseDoubleComponent(component) {
            return component === 1 ? 255 : this.toUnit8(component);
        },
        toDoubleComponent(component) {
            return _.toDouble(component, 255);
        },
        /**
         * @param {any} num
         * @param {number|function():number} [def=0]
         * @returns {number}
         */
        parseNumber(num, def) {
            return typeof num === 'number' ? num : typeof def === 'function' ? def() : def || 0;
        },
        parseRelativePercentage(percentage) {
            let p = Numberx.parseAny(percentage);
            if (isNaN(p) || p < 0) {
                throw TypeError(`Relative percentage must be in range 0..255, instead of ${percentage}`)
            }
            return p;
        },
    };

    /**
     * @type {Internal.Colors}
     */
    const colors = new _.Colors();

    _.scopeAugment();
    _.selfAugment();

    return colors;
};