// noinspection NpmUsedModulesInstalled

const Point = org.opencv.core.Point;
const Rect = org.opencv.core.Rect;
const Scalar = org.opencv.core.Scalar;
const Size = org.opencv.core.Size;
const Core = org.opencv.core.Core;
const Imgproc = org.opencv.imgproc.Imgproc;
const Mat = com.stardust.autojs.core.opencv.Mat;
const Images = com.stardust.autojs.runtime.api.Images;

const DEF_COLOR_THRESHOLD = 4;

module.exports = function (runtime, scope) {

    const ResultAdapter = require('result_adapter');

    const MatchingResult = (function $iiFe() {
        let comparators = {
            'left': (l, r) => l.point.x - r.point.x,
            'top': (l, r) => l.point.y - r.point.y,
            'right': (l, r) => r.point.x - l.point.x,
            'bottom': (l, r) => r.point.y - l.point.y,
        };

        function MatchingResult(list) {
            if (Array.isArray(list)) {
                this.matches = list;
            } else {
                this.matches = runtime.bridges.getBridges().toArray(list);
            }
            Object.defineProperty(this, 'points', {
                get() {
                    if (typeof this.__points__ === 'undefined') {
                        this.__points__ = this.matches.map(m => m.point);
                    }
                    return this.__points__;
                },
            });
        }

        MatchingResult.prototype.first = function () {
            return this.matches.length ? this.matches[0] : null;
        };
        MatchingResult.prototype.last = function () {
            return this.matches.length ? this.matches[this.matches.length - 1] : null;
        };
        MatchingResult.prototype.findMax = function (cmp) {
            if (!this.matches.length) {
                return null;
            }
            let target = this.matches[0];
            this.matches.forEach(m => target = cmp(target, m) > 0 ? m : target);
            return target;
        };
        MatchingResult.prototype.leftmost = function () {
            return this.findMax(comparators.left);
        };
        MatchingResult.prototype.topmost = function () {
            return this.findMax(comparators.top);
        };
        MatchingResult.prototype.rightmost = function () {
            return this.findMax(comparators.right);
        };
        MatchingResult.prototype.bottommost = function () {
            return this.findMax(comparators.bottom);
        };
        MatchingResult.prototype.worst = function () {
            return this.findMax((l, r) => l.similarity - r.similarity);
        };
        MatchingResult.prototype.best = function () {
            return this.findMax((l, r) => r.similarity - l.similarity);
        };
        MatchingResult.prototype.sortBy = function (cmp) {
            let comparatorFn = null;
            if (typeof cmp === 'string') {
                cmp.split('-').forEach((direction) => {
                    let buildInFn = comparators[direction];
                    if (!buildInFn) {
                        throw new Error('unknown direction \'' + direction + '\' in \'' + cmp + '\'');
                    }
                    (function (fn) {
                        if (comparatorFn === null) {
                            comparatorFn = fn;
                        } else {
                            comparatorFn = (function (comparatorFn, fn) {
                                return function (l, r) {
                                    let cmpValue = comparatorFn(l, r);
                                    return cmpValue === 0 ? fn(l, r) : cmpValue;
                                };
                            })(comparatorFn, fn);
                        }
                    })(buildInFn);
                });
            } else {
                comparatorFn = cmp;
            }
            let clone = this.matches.slice();
            clone.sort(comparatorFn);
            return new MatchingResult(clone);
        };

        return MatchingResult;
    })();

    const rtImages = runtime.getImages();

    const colorFinder = rtImages.colorFinder;

    function getColorDetector(color, algorithm, threshold) {
        switch (algorithm) {
            case 'rgb':
                return new com.stardust.autojs.core.image.ColorDetector.RGBDistanceDetector(color, threshold);
            case 'equal':
                return new com.stardust.autojs.core.image.ColorDetector.EqualityDetector(color);
            case 'diff':
                return new com.stardust.autojs.core.image.ColorDetector.DifferenceDetector(color, threshold);
            case 'rgb+':
                return new com.stardust.autojs.core.image.ColorDetector.WeightedRGBDistanceDetector(color, threshold);
            case 'hs':
                return new com.stardust.autojs.core.image.ColorDetector.HSDistanceDetector(color, threshold);
        }
        throw new Error('Unknown algorithm: ' + algorithm);
    }

    function toPointArray(points) {
        let arr = [];
        for (let i = 0; i < points.length; i++) {
            arr.push(points[i]);
        }
        return arr;
    }

    function buildRegion(region, img) {
        if (region === undefined) {
            region = [];
        }
        let x = region[0] === undefined ? 0 : region[0];
        let y = region[1] === undefined ? 0 : region[1];
        let width = region[2] === undefined ? img.getWidth() - x : region[2];
        let height = region[3] === undefined ? (img.getHeight() - y) : region[3];
        let r = new Rect(x, y, width, height);
        if (x < 0 || y < 0 || x + width > img.width || y + height > img.height) {
            throw new Error('out of region: region = [' + [x, y, width, height] + '], image.size = [' + [img.width, img.height] + ']');
        }
        return r;
    }

    function parseColor(color) {
        if (typeof color === 'string') {
            color = colors.parseColor(color);
        }
        return color;
    }

    function newSize(size) {
        if (!Array.isArray(size)) {
            size = [size, size];
        }
        if (size.length === 1) {
            size = [size[0], size[0]];
        }
        return new Size(size[0], size[1]);
    }

    function initIfNeeded() {
        rtImages.initOpenCvIfNeeded();
    }

    const colors = Object.create(runtime.colors, {
        alpha: {
            value(color) {
                color = parseColor(color);
                return color >>> 24;
            },
            enumerable: true,
        },
        red: {
            value(color) {
                color = parseColor(color);
                return (color >> 16) & 0xFF;
            },
            enumerable: true,
        },
        green: {
            value(color) {
                color = parseColor(color);
                return (color >> 8) & 0xFF;
            },
            enumerable: true,
        },
        blue: {
            value(color) {
                color = parseColor(color);
                return color & 0xFF;
            },
            enumerable: true,
        },
        isSimilar: {
            value(c1, c2, threshold, algorithm) {
                c1 = parseColor(c1);
                c2 = parseColor(c2);
                threshold = threshold === undefined ? 4 : threshold;
                algorithm = algorithm === undefined ? 'diff' : algorithm;
                let colorDetector = getColorDetector(c1, algorithm, threshold);
                return colorDetector.detectsColor(colors.red(c2), colors.green(c2), colors.blue(c2));
            },
            enumerable: true,
        },
    });

    const images = () => void 0;

    images.requestScreenCapture = function (landscape) {
        let ScreenCapturer = com.stardust.autojs.core.image.capture.ScreenCapturer;
        let orientation = ScreenCapturer.ORIENTATION_AUTO;
        if (landscape === true) {
            orientation = ScreenCapturer.ORIENTATION_LANDSCAPE;
        }
        if (landscape === false) {
            orientation = ScreenCapturer.ORIENTATION_PORTRAIT;
        }
        return ResultAdapter.wait(rtImages.requestScreenCapture(orientation));
    };

    images.save = function (img, path, format, quality) {
        format = format || 'png';
        quality = quality === undefined ? 100 : quality;
        return rtImages.save(img, path, format, quality);
    };

    images.saveImage = function (img, path, format, quality) {
        return images.save(img, path, format, quality);
    };

    images.grayscale = function (img, dstCn) {
        return images.cvtColor(img, 'BGR2GRAY', dstCn);
    };

    images.threshold = function (img, threshold, maxVal, type) {
        initIfNeeded();
        let mat = new Mat();
        type = type || 'BINARY';
        type = Imgproc['THRESH_' + type];
        Imgproc.threshold(img.mat, mat, threshold, maxVal, type);
        return images.matToImage(mat);
    };

    images.inRange = function (img, lowerBound, upperBound) {
        initIfNeeded();
        let lb = new Scalar(colors.red(lowerBound), colors.green(lowerBound),
            colors.blue(lowerBound), colors.alpha(lowerBound));
        let ub = new Scalar(colors.red(upperBound), colors.green(upperBound),
            colors.blue(upperBound), colors.alpha(lowerBound));
        let bi = new Mat();
        Core.inRange(img.mat, lb, ub, bi);
        return images.matToImage(bi);
    };

    images.interval = function (img, color, threshold) {
        initIfNeeded();
        let lb = new Scalar(colors.red(color) - threshold, colors.green(color) - threshold,
            colors.blue(color) - threshold, colors.alpha(color));
        let ub = new Scalar(colors.red(color) + threshold, colors.green(color) + threshold,
            colors.blue(color) + threshold, colors.alpha(color));
        let bi = new Mat();
        Core.inRange(img.mat, lb, ub, bi);
        return images.matToImage(bi);
    };

    images.adaptiveThreshold = function (img, maxValue, adaptiveMethod, thresholdType, blockSize, C) {
        initIfNeeded();
        let mat = new Mat();
        adaptiveMethod = Imgproc['ADAPTIVE_THRESH_' + adaptiveMethod];
        thresholdType = Imgproc['THRESH_' + thresholdType];
        Imgproc.adaptiveThreshold(img.mat, mat, maxValue, adaptiveMethod, thresholdType, blockSize, C);
        return images.matToImage(mat);

    };

    images.blur = function (img, size, point, type) {
        initIfNeeded();
        let mat = new Mat();
        size = newSize(size);
        type = Core['BORDER_' + (type || 'DEFAULT')];
        if (point === undefined) {
            Imgproc.blur(img.mat, mat, size);
        } else {
            Imgproc.blur(img.mat, mat, size, new Point(point[0], point[1]), type);
        }
        return images.matToImage(mat);
    };

    images.medianBlur = function (img, size) {
        initIfNeeded();
        let mat = new Mat();
        Imgproc.medianBlur(img.mat, mat, size);
        return images.matToImage(mat);
    };

    images.gaussianBlur = function (img, size, sigmaX, sigmaY, type) {
        initIfNeeded();
        let mat = new Mat();
        size = newSize(size);
        sigmaX = sigmaX === undefined ? 0 : sigmaX;
        sigmaY = sigmaY === undefined ? 0 : sigmaY;
        type = Core['BORDER_' + (type || 'DEFAULT')];
        Imgproc.GaussianBlur(img.mat, mat, size, sigmaX, sigmaY, type);
        return images.matToImage(mat);
    };

    images.cvtColor = function (img, code, dstCn) {
        initIfNeeded();
        let mat = new Mat();
        code = Imgproc['COLOR_' + code];
        if (dstCn === undefined) {
            Imgproc.cvtColor(img.mat, mat, code);
        } else {
            Imgproc.cvtColor(img.mat, mat, code, dstCn);
        }
        return images.matToImage(mat);
    };

    images.findCircles = function (grayImg, options) {
        initIfNeeded();
        options = options || {};
        let mat = options.region === undefined ? grayImg.mat : new Mat(grayImg.mat, buildRegion(options.region, grayImg));
        let resultMat = new Mat();
        let dp = options.dp === undefined ? 1 : options.dp;
        let minDst = options.minDst === undefined ? grayImg.height / 8 : options.minDst;
        let param1 = options.param1 === undefined ? 100 : options.param1;
        let param2 = options.param2 === undefined ? 100 : options.param2;
        let minRadius = options.minRadius === undefined ? 0 : options.minRadius;
        let maxRadius = options.maxRadius === undefined ? 0 : options.maxRadius;
        Imgproc.HoughCircles(mat, resultMat, Imgproc.CV_HOUGH_GRADIENT, dp, minDst, param1, param2, minRadius, maxRadius);
        let result = [];
        for (let i = 0; i < resultMat.rows(); i++) {
            for (let j = 0; j < resultMat.cols(); j++) {
                let d = resultMat.get(i, j);
                result.push({
                    x: d[0],
                    y: d[1],
                    radius: d[2],
                });
            }
        }
        if (options.region !== undefined) {
            mat.release();
        }
        resultMat.release();
        return result;
    };

    images.resize = function (img, size, interpolation) {
        initIfNeeded();
        let mat = new Mat();
        interpolation = Imgproc['INTER_' + (interpolation || 'LINEAR')];
        Imgproc.resize(img.mat, mat, newSize(size), 0, 0, interpolation);
        return images.matToImage(mat);
    };

    images.scale = function (img, fx, fy, interpolation) {
        initIfNeeded();
        let mat = new Mat();
        interpolation = Imgproc['INTER_' + (interpolation || 'LINEAR')];
        Imgproc.resize(img.mat, mat, newSize([0, 0]), fx, fy, interpolation);
        return images.matToImage(mat);
    };

    images.rotate = function (img, degree, x, y) {
        initIfNeeded();
        if (x === undefined) {
            x = img.width / 2;
        }
        if (y === undefined) {
            y = img.height / 2;
        }
        return rtImages.rotate(img, x, y, degree);
    };

    images.concat = function (img1, img2, direction) {
        initIfNeeded();
        direction = direction || 'right';
        return Images.concat(img1, img2, android.view.Gravity[direction.toUpperCase()]);
    };

    images.detectsColor = function (img, color, x, y, threshold, algorithm) {
        initIfNeeded();
        color = parseColor(color);
        algorithm = algorithm || 'diff';
        threshold = threshold || DEF_COLOR_THRESHOLD;
        let colorDetector = getColorDetector(color, algorithm, threshold);
        let pixel = images.pixel(img, x, y);
        return colorDetector.detectsColor(colors.red(pixel), colors.green(pixel), colors.blue(pixel));
    };

    images.findColor = function (img, color, options) {
        initIfNeeded();
        color = parseColor(color);
        options = options || {};
        let region = options.region || [];
        let threshold = function $iiFe() {
            if (options.similarity) {
                return parseInt(255 * (1 - options.similarity));
            }
            return options.threshold || DEF_COLOR_THRESHOLD;
        }();

        if (options.region) {
            return colorFinder.findColor(img, color, threshold, buildRegion(region, img));
        } else {
            return colorFinder.findColor(img, color, threshold, null);
        }
    };

    images.findColorInRegion = function (img, color, x, y, width, height, threshold) {
        return findColor(img, color, {
            region: [x, y, width, height],
            threshold: threshold,
        });
    };

    images.findColorEquals = function (img, color, x, y, width, height) {
        return findColor(img, color, {
            region: [x, y, width, height],
            threshold: 0,
        });
    };

    images.findAllPointsForColor = function (img, color, options) {
        initIfNeeded();
        color = parseColor(color);
        options = options || {};
        let threshold = function $iiFe() {
            if (options.similarity) {
                return parseInt(255 * (1 - options.similarity));
            }
            return options.threshold || DEF_COLOR_THRESHOLD;
        }();
        if (options.region) {
            return toPointArray(colorFinder.findAllPointsForColor(img, color, threshold, buildRegion(options.region, img)));
        } else {
            return toPointArray(colorFinder.findAllPointsForColor(img, color, threshold, null));
        }
    };

    images.findMultiColors = function (img, firstColor, paths, options) {
        initIfNeeded();
        options = options || {};
        firstColor = parseColor(firstColor);
        let list = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, paths.length * 3);
        for (let i = 0; i < paths.length; i++) {
            let p = paths[i];
            list[i * 3] = p[0];
            list[i * 3 + 1] = p[1];
            list[i * 3 + 2] = parseColor(p[2]);
        }
        let region = options.region ? buildRegion(options.region, img) : null;
        let threshold = options.threshold === undefined ? DEF_COLOR_THRESHOLD : options.threshold;
        return colorFinder.findMultiColors(img, firstColor, threshold, region, list);
    };

    images.findImage = function (img, template, options) {
        initIfNeeded();
        options = options || {};
        let threshold = options.threshold || 0.9;
        let maxLevel = -1;
        if (typeof options.level === 'number') {
            maxLevel = options.level;
        }
        let weakThreshold = options.weakThreshold || 0.6;
        if (options.region) {
            return rtImages.findImage(img, template, weakThreshold, threshold, buildRegion(options.region, img), maxLevel);
        } else {
            return rtImages.findImage(img, template, weakThreshold, threshold, null, maxLevel);
        }
    };

    images.matchTemplate = function (img, template, options) {
        initIfNeeded();
        options = options || {};
        let threshold = options.threshold || 0.9;
        let maxLevel = -1;
        if (typeof options.level === 'number') {
            maxLevel = options.level;
        }
        let max = options.max || 5;
        let weakThreshold = options.weakThreshold || 0.6;
        let result;
        if (options.region) {
            result = rtImages.matchTemplate(img, template, weakThreshold, threshold, buildRegion(options.region, img), maxLevel, max);
        } else {
            result = rtImages.matchTemplate(img, template, weakThreshold, threshold, null, maxLevel, max);
        }
        return new MatchingResult(result);
    };

    images.findImageInRegion = function (img, template, x, y, width, height, threshold) {
        return images.findImage(img, template, {
            region: [x, y, width, height],
            threshold: threshold,
        });
    };

    images.fromBase64 = function (base64) {
        return rtImages.fromBase64(base64);
    };

    images.toBase64 = function (img, format, quality) {
        format = format || 'png';
        quality = quality === undefined ? 100 : quality;
        return rtImages.toBase64(img, format, quality);
    };

    images.fromBytes = function (bytes) {
        return rtImages.fromBytes(bytes);
    };

    images.toBytes = function (img, format, quality) {
        format = format || 'png';
        quality = quality === undefined ? 100 : quality;
        return rtImages.toBytes(img, format, quality);
    };

    images.readPixels = function (path) {
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
    };

    images.matToImage = function (img) {
        initIfNeeded();
        return Image.ofMat(img);
    };

    util.__assignFunctions__(rtImages, images, ['captureScreen', 'read', 'copy', 'load', 'clip', 'pixel']);

    scope.__asGlobal__(images, ['requestScreenCapture', 'captureScreen', 'findImage', 'findImageInRegion', 'findColor', 'findColorInRegion', 'findColorEquals', 'findMultiColors']);

    scope.colors = colors;

    return images;

};