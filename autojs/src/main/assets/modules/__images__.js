// noinspection NpmUsedModulesInstalled

const ResultAdapter = require('result_adapter');

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

const DEF_COLOR_THRESHOLD = 4;

module.exports = function (runtime, scope) {

    const colors = Object.assign(Object.create(runtime.colors), {
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
            let t = _.parseNumber(threshold, DEF_COLOR_THRESHOLD);
            let c = _.parseColor(c2);
            return _.getColorDetector(_.parseColor(c1), algorithm || 'diff', t)
                .detectsColor(colors.red(c), colors.green(c), colors.blue(c));
        },
    });

    const MatchingResult = (function $iiFe() {
        let comparators = {
            left: (l, r) => l.point.x - r.point.x,
            top: (l, r) => l.point.y - r.point.y,
            right: (l, r) => r.point.x - l.point.x,
            bottom: (l, r) => r.point.y - l.point.y,
        };

        function MatchingResult(list) {
            this.matches = Array.isArray(list) ? list : runtime.bridges.getBridges().toArray(list);

            // @LazyGetter
            Object.defineProperty(this, 'points', {
                set(v) {
                    Object.defineProperty(this, 'points', {value: v});
                },
                get() {
                    return this.points = this.matches.map(m => m.point);
                },
                enumerable: true,
                configurable: true,
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
                            throw new Error(`unknown direction '${direction}' in '${cmp}'`);
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

    const rtImages = runtime.getImages();

    const colorFinder = rtImages.colorFinder;

    const _ = {
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
        requestScreenCapture(orientation) {
            return ResultAdapter.wait(rtImages.requestScreenCapture(orientation));
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
                return colors.parseColor(color);
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
            return opt.threshold || (def === undefined ? DEF_COLOR_THRESHOLD : def);
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
         * @param {com.stardust.autojs.core.image.ImageWrapper} img
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
         * @param {com.stardust.autojs.core.image.ImageWrapper} img
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
    };

    const images = () => void 0;

    const images_ext = {
        requestScreenCapture(landscape) {
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
            return rtImages.save(img, path, format || 'png', _.parseQuality(quality));
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
            Imgproc.threshold(img.mat, mat, threshold, maxVal, Imgproc['THRESH_' + (type || 'BINARY')]);
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
                Imgproc['ADAPTIVE_THRESH_' + adaptiveMethod],
                Imgproc['THRESH_' + thresholdType], blockSize, C);
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
                Imgproc.cvtColor(img.mat, mat, Imgproc['COLOR_' + code]);
            } else {
                Imgproc.cvtColor(img.mat, mat, Imgproc['COLOR_' + code], dstCn);
            }
            return this.matToImage(mat);
        },
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
                        _.parseNumber(this.options['dp'], 1),
                        _.parseNumber(this.options['minDst'], () => grayImg.height / 8),
                        _.parseNumber(this.options['param1'], 100),
                        _.parseNumber(this.options['param2'], 100),
                        _.parseNumber(this.options['minRadius'], 0),
                        _.parseNumber(this.options['maxRadius'], 0),
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
            return rtImages.rotate(img, x, y, degree);
        },
        concat(img1, img2, direction) {
            _.initIfNeeded();
            return Images.concat(img1, img2, Gravity[(direction || 'right').toUpperCase()]);
        },
        detectsColor(img, color, x, y, threshold, algorithm) {
            _.initIfNeeded();
            let pixel = images.pixel(img, x, y);
            return _.getColorDetector(_.parseColor(color), algorithm || 'diff', threshold || DEF_COLOR_THRESHOLD)
                .detectsColor(colors.red(pixel), colors.green(pixel), colors.blue(pixel));
        },
        findColor(img, color, options) {
            _.initIfNeeded();
            let opt = options || {};
            return colorFinder.findColor(img, _.parseColor(color), _.parseThreshold(opt), _.buildRegion(img, opt));
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
            return _.toPointArray(colorFinder.findAllPointsForColor(img, _.parseColor(color), _.parseThreshold(opt), _.buildRegion(img, opt)));
        },
        findMultiColors(img, firstColor, paths, options) {
            _.initIfNeeded();
            let list = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, paths.length * 3);
            for (let i = 0; i < paths.length; i++) {
                let [x, y, color] = paths[i];
                list[i * 3] = x;
                list[i * 3 + 1] = y;
                list[i * 3 + 2] = _.parseColor(color);
            }
            let opt = options || {};
            return colorFinder.findMultiColors(img, _.parseColor(firstColor), _.parseThreshold(opt), _.buildRegion(img, opt), list);
        },
        findImage(img, template, options) {
            _.initIfNeeded();
            let opt = options || {};
            return rtImages.findImage(img, template,
                _.parseWeakThreshold(opt, 0.6),
                _.parseThreshold(opt, 0.9),
                _.buildRegion(img, opt),
                _.parseNumber(opt.level, -1));
        },
        matchTemplate(img, template, options) {
            _.initIfNeeded();
            let opt = options || {};
            return new MatchingResult(rtImages.matchTemplate(img, template,
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
            return rtImages.fromBase64(base64);
        },
        toBase64(img, format, quality) {
            return rtImages.toBase64(img, format || 'png', _.parseQuality(quality));
        },
        fromBytes(bytes) {
            return rtImages.fromBytes(bytes);
        },
        toBytes(img, format, quality) {
            return rtImages.toBytes(img, format || 'png', _.parseQuality(quality));
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
    };

    Object.assign(images, images_ext);

    util.__assignFunctions__(rtImages, images, ['captureScreen', 'read', 'copy', 'load', 'clip', 'pixel']);

    scope.__asGlobal__(images, ['requestScreenCapture', 'captureScreen', 'findImage', 'findImageInRegion', 'findColor', 'findColorInRegion', 'findColorEquals', 'findMultiColors']);

    scope.colors = colors;

    return images;

};