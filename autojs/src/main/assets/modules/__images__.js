// noinspection NpmUsedModulesInstalled,JSUnusedGlobalSymbols

const ResultAdapter = require('result-adapter');

/* Here, importClass() is not recommended for intelligent code completion in IDE like WebStorm. */
/* The same is true of destructuring assignment syntax (like `let {Uri} = android.net`). */

const Point = org.opencv.core.Point;
const Rect = org.opencv.core.Rect;
const Scalar = org.opencv.core.Scalar;
const Size = org.opencv.core.Size;
const Core = org.opencv.core.Core;
const Imgproc = org.opencv.imgproc.Imgproc;
const Gravity = android.view.Gravity;
const Mat = com.stardust.autojs.core.opencv.Mat;
const Images = com.stardust.autojs.runtime.api.Images;
const ColorDetector = com.stardust.autojs.core.image.ColorDetector;
const ScreenCapturer = com.stardust.autojs.core.image.capture.ScreenCapturer;
const ColorStateList = android.content.res.ColorStateList;

let _ = {
    constants: {
        DEF_COLOR_THRESHOLD: 4,
    },
    requestScreenCaptureCounter: threads.atomic(0),
    init(__runtime__, scope) {
        this.runtime = __runtime__;
        this.scope = scope;

        /**
         * @type {com.stardust.autojs.runtime.api.Images}
         */
        this.rtImages = __runtime__.getImages();

        /**
         * @type {com.stardust.autojs.core.image.Colors}
         */
        this.rtColors = __runtime__.colors;
        this.colors = Object.create(this.rtColors);

        /**
         * @type {Internal.Images}
         */
        this.images = () => {
            // Empty module body
        };

        this.MatchingResult = ( /* @IIFE */ () => {
            let comparators = {
                left: (l, r) => l.point.x - r.point.x,
                top: (l, r) => l.point.y - r.point.y,
                right: (l, r) => r.point.x - l.point.x,
                bottom: (l, r) => r.point.y - l.point.y,
            };

            function MatchingResult(list) {
                let cachedPoints = null;

                this.matches = Array.isArray(list) ? list : runtime.bridges.getBridges().toArray(list);

                Object.defineProperty(this, 'points', {
                    get() {
                        if (cachedPoints === null) {
                            cachedPoints = this.matches.map(m => m.point);
                        }
                        return cachedPoints;
                    },
                    enumerable: true,
                });
            }

            Object.assign(MatchingResult.prototype, {
                first() {
                    return this.matches.length ? this.matches[0] : null;
                },
                last() {
                    return this.matches.length ? this.matches[this.matches.length - 1] : null;
                },
                findMax(cmp) {
                    if (!this.matches.length) {
                        return null;
                    }
                    let target = this.matches[0];
                    this.matches.forEach(m => target = cmp(target, m) > 0 ? m : target);
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
                sortBy(cmp) {
                    let comparatorFn = null;
                    if (typeof cmp === 'string') {
                        cmp.split('-').forEach((direction) => {
                            let buildInFn = comparators[direction];
                            if (!buildInFn) {
                                throw Error(`unknown direction '${direction}' in '${cmp}'`);
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
                        comparatorFn = cmp;
                    }
                    return new MatchingResult(this.matches.slice().sort(comparatorFn));
                },
            });

            return MatchingResult;
        })();
    },
    getModule() {
        return this.images;
    },
    selfAugment() {
        Object.assign(this.images, {
            captureScreen: _.rtImages.captureScreen.bind(_.rtImages),
            read: _.rtImages.read.bind(_.rtImages),
            copy: _.rtImages.copy.bind(_.rtImages),
            load: _.rtImages.load.bind(_.rtImages),
            clip: _.rtImages.clip.bind(_.rtImages),
            /**
             * @Overwrite by SuperMonster003 on Apr 19, 2022.
             * Method com.stardust.autojs.runtime.api.Images.pixel is static.
             *
             * @example Code snippet before overwrite
             * util.__assignFunctions__(rtImages, images, [ ... , 'pixel']);
             */
            pixel: Images.pixel,
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
                return _.rtImages.save(img, path, format || 'png', _.parseQuality(quality));
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
                return this.matToImage(mat);
            },
            inRange(img, lowerBound, upperBound) {
                _.initIfNeeded();
                let dst = new Mat();
                Core.inRange(img.mat, _.parseScalar(lowerBound), _.parseScalar(upperBound), dst);
                return this.matToImage(dst);
            },
            interval(img, color, threshold) {
                _.initIfNeeded();
                let {lowerBound, upperBound} = _.parseScalars(color, threshold);
                let dst = new Mat();
                Core.inRange(img.mat, lowerBound, upperBound, dst);
                return this.matToImage(dst);
            },
            adaptiveThreshold(img, maxValue, adaptiveMethod, thresholdType, blockSize, C) {
                _.initIfNeeded();
                let mat = new Mat();
                Imgproc.adaptiveThreshold(img.mat, mat, maxValue,
                    Imgproc[`ADAPTIVE_THRESH_${adaptiveMethod}`],
                    Imgproc[`THRESH_${thresholdType}`], blockSize, C);
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
                return this.matToImage(mat);
            },
            medianBlur(img, size) {
                _.initIfNeeded();
                let mat = new Mat();
                Imgproc.medianBlur(img.mat, mat, size);
                return this.matToImage(mat);
            },
            gaussianBlur(img, size, sigmaX, sigmaY, type) {
                _.initIfNeeded();
                let mat = new Mat();
                let x = _.parseNumber(sigmaX);
                let y = _.parseNumber(sigmaY);
                Imgproc.GaussianBlur(img.mat, mat, _.parseSize(size), x, y, _.parseBorderType(type));
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
                return this.matToImage(mat);
            },
            /**
             * @param {ImageWrapper$} grayImg
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
                                let [x, y, radius] = cir.get(i, j);
                                this.results.push({x, y, radius});
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
                return this.matToImage(mat);
            },
            scale(img, fx, fy, interpolation) {
                _.initIfNeeded();
                let mat = new Mat();
                _.resize(img, mat, 0, fx, fy, interpolation);
                return this.matToImage(mat);
            },
            rotate(img, degree, x, y) {
                _.initIfNeeded();
                x = _.parseNumber(x, () => img.width / 2);
                y = _.parseNumber(y, () => img.height / 2);
                return _.rtImages.rotate(img, x, y, degree);
            },
            concat(img1, img2, direction) {
                _.initIfNeeded();
                return Images.concat(img1, img2, Gravity[(direction || 'right').toUpperCase()]);
            },
            detectsColor(img, color, x, y, threshold, algorithm) {
                _.initIfNeeded();
                let pixel = images.pixel(img, x, y);
                return _
                    .getColorDetector(_.parseColor(color), algorithm || 'diff', threshold || _.constants.DEF_COLOR_THRESHOLD)
                    .detectsColor(colors.red(pixel), colors.green(pixel), colors.blue(pixel));
            },
            findColor(img, color, options) {
                _.initIfNeeded();
                let opt = options || {};
                return _.rtImages.colorFinder.findColor(img, _.parseColor(color), _.parseThreshold(opt), _.buildRegion(img, opt));
            },
            findColorInRegion(img, color, x, y, width, height, threshold) {
                return this.findColor(img, color, {
                    region: [x, y, width, height],
                    threshold: threshold,
                });
            },
            findColorEquals(img, color, x, y, width, height) {
                return this.findColor(img, color, {
                    region: [x, y, width, height],
                    threshold: 0,
                });
            },
            findAllPointsForColor(img, color, options) {
                _.initIfNeeded();
                let opt = options || {};
                return _.toPointArray(_.rtImages.colorFinder.findAllPointsForColor(img, _.parseColor(color), _.parseThreshold(opt), _.buildRegion(img, opt)));
            },
            findMultiColors(img, firstColor, paths, options) {
                _.initIfNeeded();
                let list = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, paths.length * 3);
                for (let i = 0; i < paths.length; i += 1) {
                    let [x, y, color] = paths[i];
                    list[i * 3] = x;
                    list[i * 3 + 1] = y;
                    list[i * 3 + 2] = _.parseColor(color);
                }
                let opt = options || {};
                return _.rtImages.colorFinder.findMultiColors(img, _.parseColor(firstColor), _.parseThreshold(opt), _.buildRegion(img, opt), list);
            },
            findImage(img, template, options) {
                _.initIfNeeded();
                let opt = options || {};
                return _.rtImages.findImage(img, template,
                    _.parseWeakThreshold(opt, 0.6),
                    _.parseThreshold(opt, 0.9),
                    _.buildRegion(img, opt),
                    _.parseNumber(opt.level, -1));
            },
            matchTemplate(img, template, options) {
                _.initIfNeeded();
                let opt = options || {};
                return new _.MatchingResult(_.rtImages.matchTemplate(img, template,
                    _.parseWeakThreshold(opt, 0.6),
                    _.parseThreshold(opt, 0.9),
                    _.buildRegion(img, opt),
                    _.parseNumber(opt.level, -1),
                    _.parseNumber(opt.max, 5)));
            },
            findImageInRegion(img, template, x, y, width, height, threshold) {
                return this.findImage(img, template, {
                    region: [x, y, width, height],
                    threshold: threshold,
                });
            },
            fromBase64(base64) {
                return _.rtImages.fromBase64(base64);
            },
            toBase64(img, format, quality) {
                return _.rtImages.toBase64(img, format || 'png', _.parseQuality(quality));
            },
            fromBytes(bytes) {
                return _.rtImages.fromBytes(bytes);
            },
            toBytes(img, format, quality) {
                return _.rtImages.toBytes(img, format || 'png', _.parseQuality(quality));
            },
            readPixels(path) {
                let img = images.read(path);
                let bitmap = img.getBitmap();
                let w = bitmap.getWidth();
                let h = bitmap.getHeight();
                let pixels = util.java.array('int', w * h);
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
                img.recycle();
                return {
                    data: pixels,
                    width: w,
                    height: h,
                };
            },
            matToImage(img) {
                _.initIfNeeded();
                return Image.ofMat(img);
            },
        });

        Object.assign(this.colors, {
            alpha(color) {
                return _.parseColor(color) >>> 24;
            },
            red(color) {
                return (_.parseColor(color) >> 16) & 0xFF;
            },
            green(color) {
                return (_.parseColor(color) >> 8) & 0xFF;
            },
            blue(color) {
                return _.parseColor(color) & 0xFF;
            },
            isSimilar(c1, c2, threshold, algorithm) {
                let t = _.parseNumber(threshold, _.constants.DEF_COLOR_THRESHOLD);
                let c = _.parseColor(c2);
                return _
                    .getColorDetector(_.parseColor(c1), algorithm || 'diff', t)
                    .detectsColor(colors.red(c), colors.green(c), colors.blue(c));
            },
            /**
             * @param {Color$} color
             * @return {number}
             */
            toInt(color) {
                _.ensureColorType(color);

                if (typeof color === 'string') {
                    if (Number(color).toString() === color) {
                        color = Number(color);
                    }
                }
                try {
                    return _.parseColor(color);
                } catch (e) {
                    console.error('Passed color: ' + color);
                    throw Error(e + '\n' + e.stack);
                }
            },
            /**
             * @param {string} colorString
             * @return {number}
             */
            rgba(colorString) {
                if (arguments.length === 4) {
                    let [r, g, b, a] = arguments;
                    return _.rtColors.argb(a, r, g, b);
                }

                colorString = _.getFullColorString(colorString);
                return this.toInt('#' + colorString.slice(-2) + colorString.slice(1, -2));
            },
            /**
             * @param {Color$} color
             * @return {android.content.res.ColorStateList}
             */
            toColorStateList(color) {
                return ColorStateList.valueOf(this.toInt(color));
            },
        });

        // @Caution by SuperMonster003 on May 4, 2022.
        //  ! Object.assign will cause Error:
        //  ! Java method "xxx" cannot be assigned to.
        Object.defineProperties(this.colors, {
            toString: {
                /**
                 * @Overwrite by SuperMonster003 on Apr 22, 2022.
                 * Substitution of legacy method.
                 * Signature: colors.toString(color: number): string
                 *
                 * @param {Color$} color
                 * @param {boolean|number|'auto'|'none'|'keep'} [alpha=8]
                 * @return {string}
                 */
                value(color, alpha) {
                    _.ensureColorType(color);

                    if (typeof color === 'string') {
                        if (Number(color).toString() === color) {
                            color = Number(color);
                        }
                    }
                    let colorString = typeof color === 'number' ? _.rtColors.toString(color) : color;
                    let colorAlpha = this.alpha(colorString);
                    let colorRed = this.red(colorString);
                    let colorGreen = this.green(colorString);
                    let colorBlue = this.blue(colorString);

                    return ( /* @IIFE */ () => {
                        if (alpha === undefined) {
                            return colorString;
                        }
                        if (alpha === true || alpha === 'keep' || alpha === 8) {
                            return _.getFullColorString(colorString);
                        }
                        if (alpha === false || alpha === 'none' || alpha === 6) {
                            return '#' + _.rtColors.toString(this.rgb(colorRed, colorGreen, colorBlue)).slice(-6);
                        }
                        if (alpha === 'auto') {
                            return colorAlpha < 255 ? colorString : _.rtColors.toString(this.rgb(colorRed, colorGreen, colorBlue));
                        }
                        throw TypeError('Unknown type of alpha for colors.toString()');
                    })().toUpperCase();
                },
            },
            argb: {
                value(colorString) {
                    if (arguments.length === 4) {
                        return _.rtColors.argb.apply(_.rtColors, arguments);
                    }

                    colorString = _.getFullColorString(colorString);
                    return this.toInt(colorString);
                },
            },
        });
    },
    scopeAugment() {
        /**
         * @type {(keyof Internal.Images)[]}
         */
        let methods = [
            'requestScreenCapture', 'captureScreen', 'findImage', 'findImageInRegion',
            'findColor', 'findColorInRegion', 'findColorEquals', 'findMultiColors',
        ];
        __asGlobal__(this.images, methods);

        /**
         * @Caution by SuperMonster003 on Apr 23, 2022.
         * Bind "this" will make bound function lose appended properties.
         *
         * @example
         * let f = function () {}; f.code = 1;
         * let g = f; console.log(g.code); // 1
         * let h = f.bind({}); console.log(h.code); // undefined
         */
        this.scope.colors = this.colors;
    },
    initIfNeeded() {
        _.rtImages.initOpenCvIfNeeded();
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
    //  ! Orientation will only by applied for the first time.
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
        if (ResultAdapter.wait(_.rtImages.requestScreenCapture(orientation))) {
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
                [width, height] = size;
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
            return _.colors.parseColor(color);
        }
        return color;
    },
    /**
     * @param {number[]} point
     * @returns {org.opencv.core.Point}
     */
    parsePoint(point) {
        let [x, y] = point;
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
    resize(img, mat, size, fx, fy, interpolation) {
        Imgproc.resize(img.mat, mat, this.parseSize(size), fx, fy, Imgproc['INTER_' + (interpolation || 'LINEAR')]);
    },
    /**
     * @param {ImageWrapper$} img
     * @param {{region?: number[]}} [o]
     * @returns {?org.opencv.core.Rect}
     */
    buildRegion(img, o) {
        if (!o || !o.region) {
            return null;
        }

        let [x, y, w, h] = o.region;
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
     * @param {ImageWrapper$} img
     * @returns {org.opencv.core.Rect}
     */
    checkAndGetImageRect(rect, img) {
        let {x, y, width, height} = rect;
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
            throw TypeError('Param color must be either a string or number');
        }
    },
    getFullColorString(colorString) {
        if (typeof colorString !== 'string') {
            throw Error('Param colorString must be a string type');
        }
        colorString = colorString.trim();
        if (colorString[0] !== '#') {
            throw Error('Param colorString must started with hash symbol');
        }
        if (colorString.length !== 7 && colorString.length !== 9) {
            throw Error('Length of param colorString must be 7 or 9');
        }
        if (colorString.length === 7) {
            colorString = '#FF' + colorString.slice(1);
        }
        return colorString;
    },
};

let $ = {
    getModule(__runtime__, scope) {
        _.init(__runtime__, scope);

        _.selfAugment();
        _.scopeAugment();

        return _.getModule();
    },
};

/**
 * @param {com.stardust.autojs.runtime.ScriptRuntime} __runtime__
 * @param {org.mozilla.javascript.Scriptable} scope
 * @return {Internal.Images}
 */
module.exports = function (__runtime__, scope) {
    return $.getModule(__runtime__, scope);
};