// noinspection NpmUsedModulesInstalled,JSUnusedGlobalSymbols

/* Overwritten protection. */

let { files, util, ui, threads } = global;

/**
 * @param {org.autojs.autojs.runtime.ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Images}
 */
module.exports = function (scriptRuntime, scope) {
    const ResultAdapter = require('result-adapter');

    const Point = org.opencv.core.Point;
    const Rect = org.opencv.core.Rect;
    const Scalar = org.opencv.core.Scalar;
    const Size = org.opencv.core.Size;
    const Core = org.opencv.core.Core;
    const Imgproc = org.opencv.imgproc.Imgproc;
    const Gravity = android.view.Gravity;
    const Mat = org.autojs.autojs.core.opencv.Mat;
    const Images = org.autojs.autojs.runtime.api.Images;
    const ColorDetector = org.autojs.autojs.core.image.ColorDetector;
    const ScreenCapturer = org.autojs.autojs.core.image.capture.ScreenCapturer;
    const ColorStateList = android.content.res.ColorStateList;
    const Bitmap = android.graphics.Bitmap;
    const BitmapFactory = android.graphics.BitmapFactory;
    const ByteArrayOutputStream = java.io.ByteArrayOutputStream;

    const RtImages = org.autojs.autojs.runtime.api.Images;
    /**
     * @type {org.autojs.autojs.runtime.api.Images}
     */
    const rtImages = scriptRuntime.getImages();

    /**
     * @type {org.autojs.autojs.core.image.Colors}
     */
    const rtColors = scriptRuntime.colors;

    let _ = {
        Images: ( /* @IIFE */ () => {
            /**
             * @implements {Internal.Images}
             */
            const Images = function () {
                return Object.assign(function () {
                    // Empty interface body.
                }, Images.prototype);
            };

            Images.prototype = {
                constructor: Images,
                captureScreen(path) {
                    return path === undefined
                        ? rtImages.captureScreen()
                        : rtImages.captureScreen(path);
                },
                read(path) {
                    return rtImages.read(path);
                },
                copy(image) {
                    return rtImages.copy(image);
                },
                load(src) {
                    return rtImages.load(src);
                },
                clip(img, x, y, w, h) {
                    return rtImages.clip(img, x, y, w, h);
                },
                /**
                 * @Overwrite by SuperMonster003 on Apr 19, 2022.
                 * Method org.autojs.autojs.runtime.api.Images.pixel is static.
                 *
                 * @example Code snippet before overwrite
                 * util.__assignFunctions__(rtImages, images, [ ... , 'pixel']);
                 */
                pixel() {
                    return RtImages.pixel.apply(RtImages, arguments);
                },
                requestScreenCapture(landscape) {
                    if (ui.isUiThread() && !continuation.enabled) {
                        throw Error('Calling images.requestScreenCapture() in "ui" thread not supported yet');
                    }
                    switch (landscape) {
                        case true:
                            return _.requestScreenCapture(ScreenCapturer.ORIENTATION_LANDSCAPE);
                        case false:
                            return _.requestScreenCapture(ScreenCapturer.ORIENTATION_PORTRAIT);
                        default:
                            return _.requestScreenCapture(ScreenCapturer.ORIENTATION_AUTO);
                    }
                },
                save(img, path, format, quality) {
                    let res = rtImages.save(img, path, format || 'png', _.parseQuality(quality));
                    img.shoot();
                    return res;
                },
                saveImage(img, path, format, quality) {
                    return this.save(img, path, format, quality);
                },
                grayscale(img, dstCn) {
                    return this.cvtColor(img, 'BGR2GRAY', dstCn);
                },
                threshold(img, threshold, maxVal, type) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    Imgproc.threshold(img.mat, mat, threshold, maxVal, Imgproc[`THRESH_${type || 'BINARY'}`]);
                    img.shoot();
                    return this.matToImage(mat);
                },
                inRange(img, lowerBound, upperBound) {
                    _.initIfNeeded();
                    let dst = new Mat();
                    Core.inRange(img.mat, _.parseScalar(lowerBound), _.parseScalar(upperBound), dst);
                    img.shoot();
                    return this.matToImage(dst);
                },
                interval(img, color, threshold) {
                    _.initIfNeeded();
                    let { lowerBound, upperBound } = _.parseScalars(color, threshold);
                    let dst = new Mat();
                    Core.inRange(img.mat, lowerBound, upperBound, dst);
                    img.shoot();
                    return this.matToImage(dst);
                },
                adaptiveThreshold(img, maxValue, adaptiveMethod, thresholdType, blockSize, C) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    Imgproc.adaptiveThreshold(img.mat, mat, maxValue,
                        Imgproc[`ADAPTIVE_THRESH_${adaptiveMethod}`],
                        Imgproc[`THRESH_${thresholdType}`], blockSize, C);
                    img.shoot();
                    return this.matToImage(mat);
                },
                blur(img, size, point, type) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    if (point === undefined) {
                        Imgproc.blur(img.mat, mat, _.parseSize(size));
                    } else {
                        Imgproc.blur(img.mat, mat, _.parseSize(size), _.parsePoint(point), _.parseBorderType(type));
                    }
                    img.shoot();
                    return this.matToImage(mat);
                },
                medianBlur(img, size) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    Imgproc.medianBlur(img.mat, mat, size);
                    img.shoot();
                    return this.matToImage(mat);
                },
                gaussianBlur(img, size, sigmaX, sigmaY, type) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    let x = _.parseNumber(sigmaX);
                    let y = _.parseNumber(sigmaY);
                    Imgproc.GaussianBlur(img.mat, mat, _.parseSize(size), x, y, _.parseBorderType(type));
                    img.shoot();
                    return this.matToImage(mat);
                },
                bilateralFilter(img, d, sigmaColor, sigmaSpace, borderType) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    Imgproc.bilateralFilter(img.mat, mat,
                        _.parseNumber(d, 0),
                        _.parseNumber(sigmaColor, 40),
                        _.parseNumber(sigmaSpace, 20),
                        _.parseBorderType(borderType));
                    img.shoot();
                    return images.matToImage(mat);
                },
                cvtColor(img, code, dstCn) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    if (dstCn === undefined) {
                        Imgproc.cvtColor(img.mat, mat, Imgproc[`COLOR_${code}`]);
                    } else {
                        Imgproc.cvtColor(img.mat, mat, Imgproc[`COLOR_${code}`], dstCn);
                    }
                    img.shoot();
                    return this.matToImage(mat);
                },
                /**
                 * @param {ImageWrapper} grayImg
                 * @param {Images.Circles.Options} options
                 * @return {Images.Circles.Result}
                 */
                findCircles(grayImg, options) {
                    let $ = {
                        getResult() {
                            this.init();
                            this.parseArgs();
                            this.release();

                            return this.results;
                        },
                        init() {
                            _.initIfNeeded();
                        },
                        parseArgs() {
                            this.options = options || {};
                            this.region = this.options.region;
                            this.parseImage();
                            this.parseCircles();
                        },
                        parseImage() {
                            this.image = this.region
                                ? new Mat(grayImg.mat, _.buildRegion(grayImg, this))
                                : grayImg.mat;
                        },
                        parseCircles() {
                            let cir = this.circles = new Mat();

                            Imgproc.HoughCircles(this.image, cir, Imgproc.CV_HOUGH_GRADIENT,
                                _.parseNumber(this.options.dp, 1),
                                _.parseNumber(this.options.minDst, () => grayImg.height / 8),
                                _.parseNumber(this.options.param1, 100),
                                _.parseNumber(this.options.param2, 100),
                                _.parseNumber(this.options.minRadius, 0),
                                _.parseNumber(this.options.maxRadius, 0),
                            );

                            this.results = [];
                            for (let i = 0; i < cir.rows(); i++) {
                                for (let j = 0; j < cir.cols(); j++) {
                                    let [ x, y, radius ] = cir.get(i, j);
                                    this.results.push({ x, y, radius });
                                }
                            }
                        },
                        release() {
                            if (this.region) {
                                this.image.release();
                            }
                            this.circles.release();
                        },
                    };

                    return $.getResult();
                },
                resize(img, size, interpolation) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    _.resize(img, mat, size, 0, 0, interpolation);
                    img.shoot();
                    return this.matToImage(mat);
                },
                scale(img, fx, fy, interpolation) {
                    _.initIfNeeded();
                    let mat = new Mat();
                    _.resize(img, mat, 0, fx, fy, interpolation);
                    img.shoot();
                    return this.matToImage(mat);
                },
                rotate(img, degree, x, y) {
                    _.initIfNeeded();
                    x = _.parseNumber(x, () => img.width / 2);
                    y = _.parseNumber(y, () => img.height / 2);
                    let res = rtImages.rotate(img, x, y, degree);
                    img.shoot();
                    return res;
                },
                concat(imgA, imgB, direction) {
                    _.initIfNeeded();
                    let res = RtImages.concat(imgA, imgB, Gravity[(direction || 'right').toUpperCase()]);
                    imgA.shoot();
                    imgB.shoot();
                    return res;
                },
                detectsColor(img, color, x, y, threshold, algorithm) {
                    _.initIfNeeded();
                    let pixel = images.pixel(img, x, y);
                    img.shoot();
                    return _
                        .getColorDetector(
                            colors.toInt(color),
                            algorithm || _.constants.DEF_COLOR_ALGORITHM,
                            threshold || _.constants.DEF_COLOR_THRESHOLD)
                        .detectsColor(
                            colors.red(pixel),
                            colors.green(pixel),
                            colors.blue(pixel));
                },
                findColor(img, color, options) {
                    _.initIfNeeded();
                    let opt = options || {};
                    let res = rtImages.colorFinder.findColor(img, colors.toInt(color), _.parseThreshold(opt), _.buildRegion(img, opt));
                    img.shoot();
                    return res;
                },
                findColorInRegion(img, color, x, y, width, height, threshold) {
                    return this.findColor(img, color, {
                        region: [ x, y, width, height ],
                        threshold: threshold,
                    });
                },
                findColorEquals(img, color, x, y, width, height) {
                    return this.findColor(img, color, {
                        region: [ x, y, width, height ],
                        threshold: 0,
                    });
                },
                findAllPointsForColor(img, color, options) {
                    _.initIfNeeded();
                    let opt = options || {};
                    let o = rtImages.colorFinder.findAllPointsForColor(img, colors.toInt(color), _.parseThreshold(opt), _.buildRegion(img, opt));
                    let res = _.toPointArray(o);
                    img.shoot();
                    return res;
                },
                findMultiColors(img, firstColor, paths, options) {
                    _.initIfNeeded();
                    let list = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, paths.length * 3);
                    for (let i = 0; i < paths.length; i += 1) {
                        let [ x, y, color ] = paths[i];
                        list[i * 3] = x;
                        list[i * 3 + 1] = y;
                        list[i * 3 + 2] = colors.toInt(color);
                    }
                    let opt = options || {};
                    let res = rtImages.colorFinder.findMultiColors(img, colors.toInt(firstColor), _.parseThreshold(opt), _.buildRegion(img, opt), list);
                    img.shoot();
                    return res;
                },
                findImage(img, template, options) {
                    _.initIfNeeded();
                    let opt = options || {};
                    const res = rtImages.findImage(img, template,
                        _.parseWeakThreshold(opt, 0.6),
                        _.parseThreshold(opt, 0.9),
                        _.buildRegion(img, opt),
                        _.parseNumber(opt.level, -1));
                    img.shoot();
                    template.shoot();
                    return res;
                },
                findImageInRegion(img, template, x, y, width, height, threshold) {
                    return this.findImage(img, template, {
                        region: [ x, y, width, height ],
                        threshold: threshold,
                    });
                },
                matchTemplate(img, template, options) {
                    _.initIfNeeded();
                    let opt = options || {};
                    let list = rtImages.matchTemplate(img, template,
                        _.parseWeakThreshold(opt, 0.6),
                        _.parseThreshold(opt, 0.9),
                        _.buildRegion(img, opt),
                        _.parseNumber(opt.level, -1),
                        _.parseNumber(opt.max, 5));
                    let res = new _.MatchingResult(list);
                    img.shoot();
                    template.shoot();
                    return res;
                },
                fromBase64(base64) {
                    return rtImages.fromBase64(base64);
                },
                toBase64(img, format, quality) {
                    const res = rtImages.toBase64(img, format || 'png', _.parseQuality(quality));
                    img.shoot();
                    return res;
                },
                fromBytes(bytes) {
                    return rtImages.fromBytes(bytes);
                },
                toBytes(img, format, quality) {
                    const res = rtImages.toBytes(img, format || 'png', _.parseQuality(quality));
                    img.shoot();
                    return res;
                },
                readPixels(path) {
                    let img = this.read(path);
                    let bitmap = img.getBitmap();
                    let w = bitmap.getWidth();
                    let h = bitmap.getHeight();
                    let pixels = util.java.array('int', w * h);
                    bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
                    img.recycle();
                    bitmap.recycle();
                    return { data: pixels, width: w, height: h };
                },
                matToImage(mat) {
                    _.initIfNeeded();
                    return Image.ofMat(mat);
                },
                isRecycled(images) {
                    let result = true;

                    /**
                     * @type {ImageWrapper[]}
                     */
                    let imagesList = Array.from(arguments);

                    imagesList.forEach((img) => {
                        try {
                            if (img.isRecycled()) {
                                return /* @forEach */;
                            }
                        } catch (e) {
                            // Ignored.
                        }
                        result = false;
                    });

                    return result;
                },
                recycle(images) {
                    let result = true;

                    /**
                     * @type {ImageWrapper[]}
                     */
                    let imagesList = Array.from(arguments);

                    imagesList.forEach((img) => {
                        try {
                            img.isRecycled() || img.recycle();
                        } catch (e) {
                            result = false;
                        }
                    });

                    return result;
                },
                compress(img, compressLevel /* inSampleSize */) {
                    /* Ensure that level is 2^n, like 1, 2, 4, 8, 16, 32 and so forth. */
                    let level = Math.pow(2, Math.floor(Math.log(Math.max(Number(compressLevel) || 1, 1)) / Math.log(2)));
                    let outputStream = new ByteArrayOutputStream();
                    img.getBitmap().compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    let byteArray = outputStream.toByteArray();
                    let options = new BitmapFactory.Options();
                    options.inSampleSize = level;
                    let bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length, options);
                    let imageWrapper = ImageWrapper.ofBitmap(bitmap);
                    img.shoot();
                    return imageWrapper;
                },
                getSize(img) {
                    if (img instanceof ImageWrapper) {
                        return img.getSize();
                    }
                    if (img instanceof android.graphics.Bitmap) {
                        return new org.opencv.core.Size(this.getWidth(img), this.getHeight(img));
                    }
                    if (img instanceof org.opencv.core.Mat) {
                        return new org.opencv.core.Size(this.getWidth(img), this.getHeight(img));
                    }
                    if (typeof img === 'string') {
                        if (!files.exists(img)) {
                            throw ReferenceError(`Image source doesn't exist: "${img}"`);
                        }
                        let options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(files.path(img), options);
                        return new org.opencv.core.Size(options.outWidth, options.outHeight);
                    }
                    throw TypeError(`Unknown source to parse its size: ${img}`);
                },
                getWidth(img) {
                    if (img instanceof ImageWrapper) {
                        return img.getWidth();
                    }
                    if (img instanceof android.graphics.Bitmap) {
                        return img.getWidth();
                    }
                    if (img instanceof org.opencv.core.Mat) {
                        return img.cols();
                    }
                    if (typeof img === 'string') {
                        return this.getSize(/* path = */ img).width;
                    }
                    throw TypeError(`Unknown source to parse its width: ${img}`);
                },
                getHeight(img) {
                    if (img instanceof ImageWrapper) {
                        return img.getHeight();
                    }
                    if (img instanceof android.graphics.Bitmap) {
                        return img.getHeight();
                    }
                    if (img instanceof org.opencv.core.Mat) {
                        return img.rows();
                    }
                    if (typeof img === 'string') {
                        return this.getSize(/* path = */ img).height;
                    }
                    throw TypeError(`Unknown source to parse its height: ${img}`);
                },
            };

            return Images;
        })(),
        Colors: ( /* @IIFE */ () => {
            /**
             * @extends Internal.Colors
             */
            const Colors = function () {
                // Empty class body.
            };

            Colors.prototype = {
                constructor: Colors,
                android: ColorTable.Android,
                web: ColorTable.Web,
                css: ColorTable.Css,
                material: ColorTable.Material,
                alpha(color) {
                    return this.toInt(color) >>> 24;
                },
                alphaDouble(color) {
                    return _.toDouble(this.alpha(color), 255);
                },
                red(color) {
                    return (this.toInt(color) >> 16) & 0xFF;
                },
                green(color) {
                    return (this.toInt(color) >> 8) & 0xFF;
                },
                blue(color) {
                    return this.toInt(color) & 0xFF;
                },
                isSimilar(colorA, colorB, threshold, algorithm) {
                    return _
                        .getColorDetector(
                            this.toInt(colorA),
                            algorithm || _.constants.DEF_COLOR_ALGORITHM,
                            _.parseNumber(threshold, _.constants.DEF_COLOR_THRESHOLD))
                        .detectsColor(
                            colors.red(this.toInt(colorB)),
                            colors.green(this.toInt(colorB)),
                            colors.blue(this.toInt(colorB)));
                },
                /**
                 * @param {Color$} color
                 * @return {number}
                 */
                toInt(color) {
                    _.ensureColorType(color);
                    try {
                        return _.parseColor(typeof color === 'number' ? _.toJavaIntegerRange(color) : this.toFullHex(color));
                    } catch (e) {
                        scriptRuntime.console.error(`Passed color: ${color}`);
                        throw Error(e + '\n' + e.stack);
                    }
                },
                toHex(color, alphaOrLength) {
                    let [ _ignoredArg0, arg1 /* alpha | length */ ] = arguments;
                    _.ensureColorType(color);

                    if (typeof color === 'number') {
                        color = rtColors.toString(_.toJavaIntegerRange(color));
                    }
                    if (color.startsWith('#')) {
                        if (color.length === 4) {
                            color = color.replace(/(#)(\w)(\w)(\w)/, '$1$2$2$3$3$4$4');
                        }
                    } else {
                        let colorByName = ColorTable.getColorByName(color, true);
                        if (colorByName !== null) {
                            color = this.toHex(colorByName.intValue());
                        }
                    }

                    return ( /* @IIFE(toColorHex) */ () => {
                        if (arg1 /* alpha */ === true || arg1 /* alpha */ === 'keep' || arg1 /* length */ === 8) {
                            if (color.length === 7) {
                                color = `#FF${color.slice(1)}`;
                            }
                            return color;
                        }
                        if (arg1 /* alpha */ === false || arg1 /* alpha */ === 'none' || arg1 /* length */ === 6) {
                            return `#${color.slice(-6)}`;
                        }
                        if (arg1 /* length */ === 3) {
                            if (!/^#(?:([A-F\d]){2})?([A-F\d])\2([A-F\d])\3([A-F\d])\4$/i.test(color)) {
                                throw TypeError(`Can't convert color ${color} to #RGB with unexpected color format.`);
                            }
                            let [ r, g, b ] = [ color.slice(-6, -5), color.slice(-4, -3), color.slice(-2, -1) ];
                            return `#${r}${g}${b}`;
                        }
                        if (arg1 /* alpha */ === undefined || arg1 /* alpha */ === 'auto') {
                            return /^#FF([A-F\d]){6}$/i.test(color) ? `#${color.slice(3)}` : color;
                        }
                        throw TypeError('Unknown type of alpha for colors.toString()');
                    })().toUpperCase();
                },
                /**
                 * Color to full hex like '#BF110523'.
                 */
                toFullHex(color) {
                    return this.toHex(color, 8);
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
                        return rtColors.argb(_.parseAlphaComponent(/* a = */ arguments[0]), r, g, b);
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
                        return rtColors.argb(_.parseAlphaComponent(/* a = */ arguments[3]), r, g, b);
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
                    return android.graphics.Color.HSVToColor(_.parseAlphaComponent(a), hsvComponents);
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
                    return colors.rgba(colors.red(cInt), colors.green(cInt), colors.blue(cInt), _.parseAlphaComponent(a));
                },
                toRgb(color) {
                    return [ this.red(color), this.green(color), this.blue(color) ];
                },
                toRgba(color) {
                    return [ this.red(color), this.green(color), this.blue(color), _.toDoubleAlphaComponent(this.alpha(color)) ];
                },
                toArgb(color) {
                    return [ _.toDoubleAlphaComponent(this.alpha(color)), this.red(color), this.green(color), this.blue(color) ];
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

                        let newHsva = Array.from(hsv).concat(_.toDoubleAlphaComponent(a));
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
                        return [ h, s, l, _.toDoubleAlphaComponent(a) ];
                    } else /* arguments.length taken as 1 . */ {
                        let r = this.red(color);
                        let g = this.green(color);
                        let b = this.blue(color);
                        let a = this.alpha(color);
                        return this.toHsla(r, g, b, a);
                    }
                },
                /**
                 * @param {Color$} color
                 * @return {android.content.res.ColorStateList}
                 */
                toColorStateList(color) {
                    return ColorStateList.valueOf(this.toInt(color));
                },
                /**
                 * @param {Paint} paint
                 * @param {Color$} color
                 */
                setPaintColor(paint, color) {
                    if (util.version.sdkInt >= util.versionCodes.Q) {
                        paint.setARGB(colors.alpha(color),
                            colors.red(color),
                            colors.green(color),
                            colors.blue(color));
                    } else {
                        paint.setColor(colors.toInt(color));
                    }
                },
                luminance(color) {
                    return rtColors.luminance(this.toInt(color));
                },
            };

            ( /* @IIFE(assignAndroidColors) */ () => {
                let androidColorsMap = {};

                void /* androidColorsKeyList = */ [
                    'BLACK', 'BLUE', 'CYAN', 'AQUA', 'DARK_GRAY', 'DARK_GREY', 'DKGRAY', 'GRAY', 'GREY',
                    'GREEN', 'LIME', 'LIGHT_GRAY', 'LIGHT_GREY', 'LTGRAY', 'MAGENTA', 'FUCHSIA', 'MAROON',
                    'NAVY', 'OLIVE', 'PURPLE', 'RED', 'SILVER', 'TEAL', 'WHITE', 'YELLOW', 'TRANSPARENT',
                ].forEach(k => androidColorsMap[k] = ColorTable.Android[k]);

                Object.assign(Colors.prototype, androidColorsMap);
            })();

            Object.setPrototypeOf(Colors.prototype, rtColors);

            return Colors;
        })(),
        MatchingResult: ( /* @IIFE */ () => {
            let comparators = {
                left: (l, r) => l.point.x - r.point.x,
                top: (l, r) => l.point.y - r.point.y,
                right: (l, r) => r.point.x - l.point.x,
                bottom: (l, r) => r.point.y - l.point.y,
            };

            /**
             * @extends Images.MatchingResult
             */
            const MatchingResult = function (list) {
                Object.defineProperties(this, {
                    matches: {
                        value: Array.isArray(list) ? list : scriptRuntime.bridges.getBridges().toArray(list),
                        enumerable: true,
                    },
                    points: {
                        get() {
                            return this.matches.map(m => m.point);
                        },
                        enumerable: false,
                    },
                });
            };

            MatchingResult.prototype = {
                constructor: MatchingResult,
                first() {
                    return this.matches.length ? this.matches[0] : null;
                },
                last() {
                    return this.matches.length ? this.matches[this.matches.length - 1] : null;
                },
                findMax(compareFn) {
                    if (!this.matches.length) {
                        return null;
                    }
                    let target = this.matches[0];
                    this.matches.forEach(m => target = compareFn(target, m) > 0 ? m : target);
                    return target;
                },
                leftmost() {
                    return this.findMax(comparators.left);
                },
                topmost() {
                    return this.findMax(comparators.top);
                },
                rightmost() {
                    return this.findMax(comparators.right);
                },
                bottommost() {
                    return this.findMax(comparators.bottom);
                },
                worst() {
                    return this.findMax((l, r) => l.similarity - r.similarity);
                },
                best() {
                    return this.findMax((l, r) => r.similarity - l.similarity);
                },
                sortBy(compareFn) {
                    let comparatorFn = null;
                    if (typeof compareFn === 'string') {
                        compareFn.split('-').forEach((direction) => {
                            let buildInFn = comparators[direction];
                            if (!buildInFn) {
                                throw Error(`unknown direction '${direction}' in '${compareFn}'`);
                            }
                            if (comparatorFn === null) {
                                comparatorFn = buildInFn;
                            } else {
                                comparatorFn = (function (comparatorFn, fn) {
                                    return function (l, r) {
                                        let cmpValue = comparatorFn(l, r);
                                        return cmpValue === 0 ? fn(l, r) : cmpValue;
                                    };
                                })(comparatorFn, buildInFn);
                            }
                        });
                    } else {
                        comparatorFn = compareFn;
                    }
                    return new MatchingResult(this.matches.slice().sort(comparatorFn));
                },
            };

            return MatchingResult;
        })(),
        constants: {
            DEF_COLOR_THRESHOLD: 4,
            DEF_COLOR_ALGORITHM: 'diff',
        },
        requestScreenCaptureCounter: threads.atomic(0),
        scopeAugment() {
            /**
             * @type {(keyof Internal.Images)[]}
             */
            let methods = [
                'requestScreenCapture', 'captureScreen', 'findImage', 'findImageInRegion',
                'findColor', 'findColorInRegion', 'findColorEquals', 'findMultiColors',
            ];
            __asGlobal__(images, methods);

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
        },
        initIfNeeded() {
            rtImages.initOpenCvIfNeeded();
        },
        /**
         * @param {number} x
         * @param {number} min
         * @param {number} max
         * @param {number} [def=0]
         * @returns {number}
         */
        clamp: (x, min, max, def) => {
            return Math.min(Math.max(_.parseNumber(x, def), min), max);
        },
        // FIXME by SuperMonster003 on May 16, 2022.
        //  ! Orientation will only be applied for the first time.
        requestScreenCapture(orientation) {
            if (_.requestScreenCaptureCounter.get() > 0) {
                // let cache = ScreenCapturer.getInstanceCache();
                // if (cache !== null) {
                // FIXME by SuperMonster003 on May 16, 2022.
                //  ! setScreenCapturerOrientation() will cause IllegalStateException:
                //  ! Image is already closed.
                // cache.setOrientation(orientation);
                // }
                return true;
            }
            _.requestScreenCaptureCounter.incrementAndGet();
            if (ResultAdapter.wait(rtImages.requestScreenCapture(orientation))) {
                return true;
            }
            _.requestScreenCaptureCounter.decrementAndGet();
            return false;
        },
        /**
         * @param {any} num
         * @param {number|function():number} [def=0]
         * @returns {number}
         */
        parseNumber(num, def) {
            return typeof num === 'number' ? num : typeof def === 'function' ? def() : def || 0;
        },
        /**
         * @param {number|number[]} size
         * @returns {org.opencv.core.Size}
         */
        parseSize(size) {
            let width, height;
            if (!Array.isArray(size)) {
                width = height = size;
            } else {
                if (size.length === 1) {
                    width = height = size[0];
                } else {
                    [ width, height ] = size;
                }
            }
            return new Size(width, height);
        },
        /**
         * @param {number|string} color
         * @returns {number}
         */
        parseColor(color) {
            if (typeof color === 'string') {
                return rtColors.parseColor(color);
            }
            return color;
        },
        /**
         * @param {number[]} point
         * @returns {org.opencv.core.Point}
         */
        parsePoint(point) {
            let [ x, y ] = point;
            return new Point(x, y);
        },
        parseQuality(q) {
            return this.clamp(q, 0, 100, 100);
        },
        parseScalar(color, offset) {
            let d = this.clamp(offset, -255, 255, 0);
            return new Scalar(colors.red(color) + d, colors.green(color) + d, colors.blue(color) + d, colors.alpha(color));
        },
        parseScalars(color, threshold) {
            let thd = this.clamp(threshold, 0, 255, 0);
            return {
                lowerBound: _.parseScalar(color, -thd),
                upperBound: _.parseScalar(color, +thd),
            };
        },
        parseBorderType(type) {
            return Core['BORDER_' + (type || 'DEFAULT')];
        },
        /**
         * @param {{
         *     threshold?: number,
         *     similarity?: number,
         * }} options
         * @param {number} [def]
         * @returns {number}
         */
        parseThreshold(options, def) {
            let opt = options || {};
            if (opt.similarity) {
                return Math.trunc(255 * (1 - opt.similarity));
            }
            return opt.threshold || (def === undefined ? _.constants.DEF_COLOR_THRESHOLD : def);
        },
        /**
         * @param {{
         *     weakThreshold?: number,
         * }} options
         * @param {number} [def]
         * @returns {number}
         */
        parseWeakThreshold(options, def) {
            let opt = options || {};
            return opt.weakThreshold || def;
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
        parseAlphaComponent(component) {
            return component === 1 ? 255 : this.toUnit8(component);
        },
        toDoubleAlphaComponent(component) {
            return _.toDouble(component, 255);
        },
        resize(img, mat, size, fx, fy, interpolation) {
            Imgproc.resize(img.mat, mat, this.parseSize(size), fx, fy, Imgproc['INTER_' + (interpolation || 'LINEAR')]);
        },
        /**
         * @param {ImageWrapper} img
         * @param {{region?: number[]}} [o]
         * @returns {?org.opencv.core.Rect}
         */
        buildRegion(img, o) {
            if (!o || !o.region) {
                return null;
            }

            let [ x, y, w, h ] = o.region;
            x = _.parseNumber(x, 0);
            y = _.parseNumber(y, 0);
            w = _.parseNumber(w, () => img.getWidth() - x);
            h = _.parseNumber(h, () => img.getHeight() - y);

            return _.checkAndGetImageRect(new Rect(x, y, w, h), img);
        },
        /**
         * @param {org.opencv.core.Point[]} points - Java Array
         * @returns {org.opencv.core.Point[]}
         */
        toPointArray(points) {
            let arr = [];
            for (let i = 0; i < points.length; i++) {
                arr.push(points[i]);
            }
            return arr;
        },
        /**
         * @param {org.opencv.core.Rect} rect
         * @param {ImageWrapper} img
         * @returns {org.opencv.core.Rect}
         */
        checkAndGetImageRect(rect, img) {
            let { x, y, width, height } = rect;
            if (x < 0) {
                throw Error(`X of region must be non-negative rather than ${x}`);
            }
            if (y < 0) {
                throw Error(`Y of region must be non-negative rather than ${y}`);
            }
            if (x + width > img.width) {
                throw Error(`Width of region overstepped:\n${x} + ${width} > ${img.width}`);
            }
            if (y + height > img.height) {
                throw Error(`Height of region overstepped:\n${y} + ${height} > ${img.height}`);
            }
            return rect;
        },
        getColorDetector(color, algorithm, threshold) {
            switch (algorithm) {
                case 'rgb':
                    return new ColorDetector.RGBDistanceDetector(color, threshold);
                case 'equal':
                    return new ColorDetector.EqualityDetector(color);
                case 'diff':
                    return new ColorDetector.DifferenceDetector(color, threshold);
                case 'rgb+':
                    return new ColorDetector.WeightedRGBDistanceDetector(color, threshold);
                case 'hs':
                    return new ColorDetector.HSDistanceDetector(color, threshold);
            }
            throw Error('Unknown algorithm for detector: ' + algorithm);
        },
        ensureColorType(color) {
            if (typeof color !== 'string' && typeof color !== 'number') {
                throw TypeError('Color must be either a string or number');
            }
            if (typeof color === 'string') {
                if (!color.startsWith('#')) {
                    if (ColorTable.getColorByName(color) !== null) {
                        return;
                    }
                    throw TypeError('Color string must start with "#"');
                }
                if (!/^#[A-F\d]{3}([A-F\d]{3}([A-F\d]{2})?)?$/i.test(color)) {
                    throw TypeError(`Invalid color string format: ${color}`);
                }
            }
        },
        ensureJavaArray(arr, length) {
            if (!/^\[[A-Z]/.test(util.getClassName(arr))) {
                throw TypeError('Param arr must be a Java array');
            }
            if (typeof length === 'number' && arr.length !== length) {
                throw Error(`Param arr must be of length ${length}`);
            }
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
    };

    /**
     * @type {Internal.Images}
     */
    const images = new _.Images();

    /**
     * @type {Internal.Colors}
     */
    const colors = new _.Colors();

    _.scopeAugment();

    return images;
};