// noinspection NpmUsedModulesInstalled,JSUnusedGlobalSymbols,JSUnusedLocalSymbols

/* Overwritten protection. */

let { files, util, ui, threads } = global;

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Images}
 */
module.exports = function (scriptRuntime, scope) {
    const ResultAdapter = require('result-adapter');

    const Point = org.opencv.core.Point;
    const Scalar = org.opencv.core.Scalar;
    const Size = org.opencv.core.Size;
    const Core = org.opencv.core.Core;
    const Imgproc = org.opencv.imgproc.Imgproc;
    const Gravity = android.view.Gravity;
    const Mat = org.autojs.autojs.core.opencv.Mat;
    const Images = org.autojs.autojs.runtime.api.Images;
    const ColorDetector = org.autojs.autojs.core.image.ColorDetector;
    const ScreenCapturer = org.autojs.autojs.core.image.capture.ScreenCapturer;
    const Bitmap = android.graphics.Bitmap;
    const BitmapFactory = android.graphics.BitmapFactory;
    const ByteArrayOutputStream = java.io.ByteArrayOutputStream;

    const RtImages = org.autojs.autojs.runtime.api.Images;
    /**
     * @type {org.autojs.autojs.runtime.api.Images}
     */
    const rtImages = scriptRuntime.getImages();

    let _ = {
        /**
         * @implements {Internal.Images}
         */
        Images: (/* @IIFE */ () => {
            /**
             * @implements {Internal.Images}
             */
            const Images = function () {
                return Object.assign(function () {
                    /* Empty body. */
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
                    if (arguments.length === 2) {
                        if (isNullish(arguments[1])) {
                            img.shoot();
                            return img;
                        }
                        let rect = this.buildRegion(img, /* region = */ arguments[1]);
                        return this.clip(img, rect.x, rect.y, rect.width, rect.height);
                    }
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
                    return rtImages.save(img, path, _.parseFormat(format), _.parseQuality(quality));
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
                    Imgproc.threshold(img.mat, mat, threshold, maxVal, _.parseThresholdType(type));
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
                    if (Array.isArray(size)) {
                        if (size.length !== 2) {
                            throw TypeError(`Argument size for images.medianBlur must be either a number or an array with same TWO number elements`);
                        }
                        if (size.every(x => typeof x === 'number')) {
                            throw TypeError(`Argument size for images.medianBlur must be either a number or an array with same two NUMBER elements`);
                        }
                        if (size[0] !== size[1]) {
                            throw TypeError(`Argument size for images.medianBlur must be either a number or an array with SAME two number elements`);
                        }
                    }
                    let ksize = Array.isArray(size) ? [ size ][0] : size;
                    let mat = new Mat();
                    Imgproc.medianBlur(img.mat, mat, ksize);
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
                                ? new Mat(grayImg.mat, _.buildRegion(grayImg, this.region))
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
                    return rtImages.rotate(img, x, y, degree);
                },
                concat(imgA, imgB, direction) {
                    _.initIfNeeded();
                    return RtImages.concat(imgA, imgB, _.directionToGravity(direction));
                },
                detectColor(img, color, x, y, threshold, algorithm) {
                    _.initIfNeeded();
                    let pixel = this.pixel(img, x, y);
                    return ColorDetector
                        .get(
                            colors.toInt(color),
                            algorithm || _.constants.DEF_COLOR_ALGORITHM,
                            _.parseNumber(threshold, _.constants.DEF_COLOR_THRESHOLD))
                        .detectColor(
                            colors.red(pixel),
                            colors.green(pixel),
                            colors.blue(pixel));
                },
                /**
                 * @deprecated
                 */
                detectsColor(img, color, x, y, threshold, algorithm) {
                    return this.detectColor(img, color, x, y, threshold, algorithm);
                },
                findPointByColor(img, color, options) {
                    if (arguments.length === 7) {
                        let [ img, color, x, y, width, height, threshold ] = arguments;
                        return this.findPointByColor(img, color, {
                            region: [ x, y, width, height ],
                            threshold: threshold,
                        });
                    }
                    _.initIfNeeded();
                    let opt = options || {};
                    let res = rtImages.colorFinder.findPointByColor(img,
                        colors.toInt(color),
                        _.parseThreshold(opt),
                        'region' in opt ? _.buildRegion(img, opt.region) : null);
                    img.shoot();
                    return res;
                },
                /**
                 * @deprecated
                 */
                findColor(img, color, options) {
                    return this.findPointByColor(img, color, options);
                },
                /**
                 * @deprecated
                 */
                findColorInRegion(img, color, x, y, width, height, threshold) {
                    return this.findPointByColor(img, color, x, y, width, height);
                },
                findPointByColorExactly(img, color, x, y, width, height) {
                    return this.findPointByColor(img, color, {
                        region: [ x, y, width, height ],
                        threshold: 0,
                    });
                },
                /**
                 * @deprecated
                 */
                findColorEquals(img, color, x, y, width, height) {
                    return this.findPointByColorExactly(img, color, x, y, width, height);
                },
                findPointsByColor(img, color, options) {
                    _.initIfNeeded();
                    let opt = options || {};
                    let o = rtImages.colorFinder.findPointsByColor(img,
                        colors.toInt(color),
                        _.parseThreshold(opt),
                        'region' in opt ? _.buildRegion(img, opt.region) : null);
                    return _.toPointArray(o);
                },
                findPointByColors(img, firstColor, paths, options) {
                    _.initIfNeeded();
                    let list = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, paths.length * 3);
                    for (let i = 0; i < paths.length; i += 1) {
                        let [ x, y, color ] = paths[i];
                        list[i * 3] = x;
                        list[i * 3 + 1] = y;
                        list[i * 3 + 2] = colors.toInt(color);
                    }
                    let opt = options || {};
                    let niceFirstColor = colors.toInt(firstColor);
                    let niceThreshold = _.parseThreshold(opt);
                    let rect = 'region' in opt ? _.buildRegion(img, opt.region) : null;
                    if (opt.all) {
                        return Array.from(rtImages.colorFinder.findPointsByColors(
                            img, niceFirstColor, niceThreshold, rect, list,
                        ));
                    } else {
                        return rtImages.colorFinder.findPointByColors(
                            img, niceFirstColor, niceThreshold, rect, list,
                        );
                    }
                },
                /**
                 * @deprecated
                 */
                findAllPointsForColor(img, color, options) {
                    return this.findPointsByColor(img, color, options);
                },
                findPointsByColors(img, firstColor, paths, options) {
                    let niceOptions = Object.assign(options || {}, { all: true });
                    return this.findPointByColors(img, firstColor, paths, niceOptions);
                },
                /**
                 * @deprecated
                 */
                findMultiColors(img, firstColor, paths, options) {
                    return this.findPointByColors(img, firstColor, paths, options);
                },
                findPointByImage(img, template, options) {
                    if (arguments.length === 7) {
                        let [ img, template, x, y, width, height, threshold ] = arguments;
                        return this.findPointByImage(img, template, {
                            region: [ x, y, width, height ],
                            threshold: threshold,
                        });
                    }
                    _.initIfNeeded();
                    let opt = options || {};
                    return rtImages.findImage(img, template,
                        _.parseWeakThreshold(opt, 0.6),
                        _.parseThreshold(opt, 0.9),
                        'region' in opt ? _.buildRegion(img, opt.region) : null,
                        _.parseNumber(opt.level, -1));
                },
                /**
                 * @deprecated
                 */
                findImage(img, template, options) {
                    return this.findPointByImage(img, template, options);
                },
                /**
                 * @deprecated
                 */
                findImageInRegion(img, template, x, y, width, height, threshold) {
                    return this.findPointByImage(img, template, x, y, width, height, threshold);
                },
                matchTemplate(img, template, options) {
                    _.initIfNeeded();
                    let opt = options || {};
                    let list = rtImages.matchTemplate(img, template,
                        _.parseWeakThreshold(opt, 0.6),
                        _.parseThreshold(opt, 0.9),
                        'region' in opt ? _.buildRegion(img, opt.region) : null,
                        _.parseNumber(opt.level, -1),
                        _.parseNumber(opt.max, 5));
                    return new _.MatchingResult(list);
                },
                fromBase64(base64) {
                    return rtImages.fromBase64(base64);
                },
                toBase64(img, format, quality) {
                    return rtImages.toBase64(img, _.parseFormat(format), _.parseQuality(quality));
                },
                fromBytes(bytes) {
                    return rtImages.fromBytes(bytes);
                },
                toBytes(img, format, quality) {
                    return rtImages.toBytes(img, _.parseFormat(format), _.parseQuality(quality));
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
                buildRegion(img, region) {
                    if (isNullish(region)
                        || Array.isArray(region)
                        || region instanceof org.opencv.core.Rect
                        || region instanceof android.graphics.Rect
                    ) return _.buildRegion(img, region);
                    else throw TypeError(`Argument region with species "${species(region)}" is invalid for images.buildRegion`);
                },
            };

            return Images;
        })(),
        MatchingResult: (/* @IIFE */ () => {
            let comparators = {
                left: (l, r) => l.point.x - r.point.x,
                top: (l, r) => l.point.y - r.point.y,
                right: (l, r) => r.point.x - l.point.x,
                bottom: (l, r) => r.point.y - l.point.y,
            };

            /**
             * @class
             * @extends Images.MatchingResult
             * @param {Images.TemplateMatch[] | java.lang.Iterable<Images.TemplateMatch>} list
             */
            const MatchingResult = function (list) {
                Object.assign(this, {
                    matches: (() => {
                        if (Array.isArray(list)) {
                            return list;
                        }
                        // noinspection UnnecessaryLocalVariableJS
                        /** @type Images.TemplateMatch[] */
                        let results = scriptRuntime.bridges.getBridges().toArray(list);
                        return results;
                    })(),
                    get points() {
                        return this.matches.map(m => m.point);
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
                    /**
                     * @type Images.TemplateMatch
                     */
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
            __asGlobal__(images, methods, scope);
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
                return scriptRuntime.colors.parseColor(color);
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
        /**
         * @param {Images.Format} fmt
         */
        parseFormat(fmt) {
            if (typeof fmt === 'string') {
                // noinspection JSValidateTypes
                fmt = fmt.toLowerCase();
            }
            switch (fmt) {
                case undefined:
                    return 'png';
                case 'png':
                case 'jpg':
                case 'jpeg':
                case 'webp':
                    return fmt;
                default:
                    throw TypeError(`Unknown image format: ${fmt}`);
            }
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
            const PREFIX = 'BORDER_';
            if (typeof type === 'string') {
                type = type.toUpperCase();
                if (!type.startsWith(PREFIX)) {
                    type = PREFIX + type;
                }
            }
            return type === undefined ? Core.BORDER_DEFAULT : Core[type];
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
            if ('similarity' in opt) {
                if ('threshold' in opt) {
                    throw Error(`Options can't hold both 'similarity' and 'threshold' properties`);
                }
                return Math.round(255 * (1 - opt.similarity));
            }
            if (typeof opt.threshold === 'number') {
                return opt.threshold;
            }
            return def === undefined ? _.constants.DEF_COLOR_THRESHOLD : def;
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
        resize(img, mat, size, fx, fy, interpolation) {
            Imgproc.resize(img.mat, mat, this.parseSize(size), fx, fy, _.parseInterpolation(interpolation));
        },
        /**
         * @param {ImageWrapper} img
         * @param {[X?, Y?, Width?, Height?] | org.opencv.core.Rect | android.graphics.Rect} [region]
         * @returns {org.opencv.core.Rect}
         */
        buildRegion(img, region) {
            let [ x, y, w, h ] = region instanceof org.opencv.core.Rect
                ? [ region.x, region.y, region.width, region.height ]
                : region instanceof android.graphics.Rect
                    ? [ region.left, region.top, region.width(), region.height() ]
                    : Array.isArray(region) ? region : [];

            x = _.parseNumber(x, 0);
            x = x === -1 ? WIDTH : x > 0 && x < 1 ? cX(x) : x;

            y = _.parseNumber(y, 0);
            y = y === -1 ? HEIGHT : y > 0 && y < 1 ? cY(y) : y;

            w = _.parseNumber(w, () => img.getWidth() - x);
            w = w === -1 ? WIDTH : w > 0 && w < 1 ? cX(w) : w;

            h = _.parseNumber(h, () => img.getHeight() - y);
            h = h === -1 ? HEIGHT : h > 0 && h < 1 ? cY(h) : h;

            return _.checkAndGetImageRect(new org.opencv.core.Rect(x, y, w, h), img);
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
        directionToGravity(direction) {
            if (typeof direction === 'string') {
                direction = direction.toLowerCase();
            }
            switch (direction) {
                case undefined:
                case 'right':
                    return Gravity.RIGHT;
                case 'left':
                    return Gravity.LEFT;
                case 'top':
                    return Gravity.TOP;
                case 'bottom':
                    return Gravity.BOTTOM;
                default:
                    throw TypeError(`Unknown image concat direction: ${direction}`);
            }
        },
        parseThresholdType(type) {
            const PREFIX = 'THRESH_';
            if (typeof type === 'string') {
                type = type.toUpperCase();
                if (!type.startsWith(PREFIX)) {
                    type = PREFIX + type;
                }
            }
            return type === undefined ? Imgproc.THRESH_BINARY : Imgproc[type];
        },
        parseInterpolation(it) {
            const PREFIX = 'INTER_';
            if (typeof it === 'string') {
                it = it.toUpperCase();
                if (!it.startsWith(PREFIX)) {
                    it = PREFIX + it;
                }
            }
            return it === undefined ? Imgproc.INTER_LINEAR : Imgproc[it];
        },
    };

    /**
     * @type {Internal.Images}
     */
    const images = new _.Images();

    _.scopeAugment();

    return images;
};