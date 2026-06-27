// Type definitions for AutoJs6 internal module images
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.1.3
//
// Last modified: Jun 14, 2026

/// <reference path="./index.d.ts" />

/**
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/images/Images.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/images/MatchingResult.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/images/ImageFeatures.kt
 * @Source %AutoJs6%/app/src/main/java/org/autojs/autojs/runtime/api/augment/images/ObjectFrame.kt
 */

declare namespace Internal {

    import ColorInt = Colors.ColorInt;
    import DetectionAlgorithm = Colors.DetectionAlgorithm;
    import ConversionCodes = Colors.ConversionCodes;

    interface Images extends EventEmitter$ {

        initOpenCvIfNeeded(): void;

        /**
         * @see org.autojs.autojs.runtime.api.Images.captureScreen
         */
        captureScreen(): ImageWrapper;

        /**
         * @see org.autojs.autojs.runtime.api.Images.captureScreen
         */
        captureScreen(path: string): boolean;

        /**
         * @see org.autojs.autojs.runtime.api.Images.read
         */
        read(path: string, isStrict?: boolean): ImageWrapper | null;

        imread(path: string): org.autojs.autojs.core.opencv.Mat;

        /**
         * @see org.autojs.autojs.runtime.api.Images.copy
         */
        copy(image: Images.ImageSource): ImageWrapper;

        /**
         * @see org.autojs.autojs.runtime.api.Images.load
         */
        load(src: string): ImageWrapper | null;

        loadAsync(src: string): Promise<ImageWrapper | null>;

        /**
         * @see org.autojs.autojs.runtime.api.Images.clip
         */
        clip(image: Images.ImageSource, x: number, y: number, w: number, h: number): ImageWrapper;
        clip(image: Images.ImageSource, region: OmniRegion): ImageWrapper;

        /**
         * @see org.autojs.autojs.runtime.api.Images.pixel
         */
        pixel(image: ImageWrapper, x: number, y: number): ColorInt;

        /**
         * Applies an adaptive threshold to an array.
         * zh-CN: 自适应阈值化
         * @param src - source 8-bit single-channel image.
         * zh-CN: 输入图像, 8 位单通道浮点类型
         * @param maxValue - non-zero value assigned to the pixels for which the condition is satisfied.
         * zh-CN: 预设满足条件的最大值
         * @param adaptiveMethod - adaptive thresholding algorithm to use (without "ADAPTIVE_THRESH_" prefix).
         * zh-CN: 指定自适应阈值算法 (参数不包含 "ADAPTIVE_THRESH_" 前缀)
         * @param thresholdType - thresholding type that must be either THRESH_BINARY or THRESH_BINARY_INV (without "THRESH_" prefix).
         * zh-CN: 指定阈值类型, 二进制阈值或反二进制阈值 (参数不包含 "THRESH_" 前缀)
         * @param blockSize - size of a pixel neighborhood that is used to calculate a threshold value for the pixel: 3, 5, 7, and so on.
         * zh-CN: 表示邻域块大小, 用来计算区域阈值, 一般选 3, 5, 7 等
         * @param C - constant subtracted from the mean or weighted mean. Normally, it is positive but may be zero or negative as well.
         * zh-CN: 参数 C 表示与算法有关的参数, 它是一个从均值或加权均值提取的常数, 可以是负数
         * @example
         * images.requestScreenCapture(false);
         * images.adaptiveThreshold(
         *     images.grayscale(images.captureScreen()), 255, "GAUSSIAN_C", "BINARY_INV", 9, 6
         * );
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * images.adaptiveThreshold = function (src, maxValue, adaptiveMethod, thresholdType, blockSize, C) {
         *     initIfNeeded();
         *     let mat = new Mat();
         *     adaptiveMethod = Imgproc["ADAPTIVE_THRESH_" + adaptiveMethod];
         *     thresholdType = Imgproc["THRESH_" + thresholdType];
         *     Imgproc.adaptiveThreshold(src.mat, mat, maxValue, adaptiveMethod, thresholdType, blockSize, C);
         *     return images.matToImage(mat);
         * };
         * @see org.opencv.imgproc.Imgproc
         * @see org.autojs.autojs.core.opencv.Mat
         * @see https://blog.csdn.net/sinat_36264666/article/details/77586964
         * @see https://blog.csdn.net/abcvincent/article/details/78822191
         */
        adaptiveThreshold(
            src: ImageWrapper, maxValue: number, adaptiveMethod: Images.AdaptiveThresholdTypes,
            thresholdType: 'BINARY' | 'BINARY_INV', blockSize: number, C: number,
        ): ImageWrapper;

        /**
         * Blurs an image using the normalized box filter.
         * zh-CN: 均值滤波: 使用标准化的盒式过滤器平滑图像
         * @param src - input image; it can have any number of channels,
         * which are processed independently, but the depth should be CV_8U, CV_16U, CV_16S, CV_32F or CV_64F.
         * zh-CN: 源图像: 通道数不限, 但位深应为 CV_8U / CV_16U / CV_16S / CV_32F 或 CV_64F
         * @param ksize - blurring kernel size.
         * zh-CN: 滤波模板尺寸: 用于平滑操作的模板大小
         * @param [anchor=[-1, -1]] - anchor point; default value Point(-1,-1) means that the anchor is at the kernel center.
         * zh-CN: 锚点: 默认值为 Point(-1, -1), 表示锚点在模板中心
         * @param [type="DEFAULT"] - border mode used to extrapolate pixels outside of the image (without "BORDER_" prefix).
         * BORDER_WRAP is not supported.
         * zh-CN: 边界模式: 指定处理边界像素时如何确定图像范围外像素的取值 (参数不包含 "BORDER_" 前缀) , 不支持 BORDER_WRAP
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.blur(images.captureScreen(), [3, 3]);
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.blur = function (src, ksize, anchor, type) {
         *     initIfNeeded();
         *     let mat = new Mat();
         *     ksize = newSize(ksize);
         *     type = Core["BORDER_" + (type || "DEFAULT")];
         *     if (anchor === undefined) {
         *         Imgproc.blur(src.mat, mat, ksize);
         *     } else {
         *         Imgproc.blur(src.mat, mat, ksize, new Point(anchor[0], anchor[1]), type);
         *     }
         *     return images.matToImage(mat);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function newSize(size) {
         *     if (!Array.isArray(size)) {
         *         size = [size, size];
         *     }
         *     if (size.length === 1) {
         *         size = [size[0], size[0]];
         *     }
         *     return new Size(size[0], size[1]);
         * }
         * @see org.autojs.autojs.core.opencv.Mat
         * @see org.opencv.imgproc.Imgproc
         * @see org.opencv.core.Core
         * @see org.opencv.core.Size
         * @see org.opencv.core.Point
         * @see https://docs.opencv.org/3.4/javadoc/org/opencv/imgproc/Imgproc.html#blur(org.opencv.core.Mat,org.opencv.core.Mat,org.opencv.core.Size,org.opencv.core.Point,int)
         * @see https://blog.csdn.net/keith_bb/article/details/54382426
         */
        blur(
            src: ImageWrapper, ksize: Side | [Width, Height?],
            anchor?: [X, Y], type?: Images.BorderTypes,
        ): ImageWrapper;

        /**
         * Concatenate (combine) two images vertically and horizontally.
         * This method does not change the existing images, but instead returns a new concatenated image.
         * zh-CN: 连接两个图像. 若两图像连接处的尺寸不同, 则尺寸较小的图像将适当居中.
         * @param imgA - source image 1
         * @param imgB - source image 2
         * @param [direction="RIGHT"] - direction of concatenation
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.concat(images.captureScreen(), images.read(files.path("./test.png")), "BOTTOM");
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.concat = function (imgA, imgB, direction) {
         *     initIfNeeded();
         *     direction = direction || "right";
         *     return javaImages.concat(imgA, imgB, android.view.Gravity[direction.toUpperCase()]);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * @see org.autojs.autojs.runtime.api.Images.concat
         */
        concat(
            imgA: ImageWrapper, imgB: ImageWrapper,
            direction?: 'LEFT' | 'RIGHT' | 'TOP' | 'BOTTOM',
        ): ImageWrapper;

        /**
         * Converts an image from one color space to another.
         * zh-CN: 颜色空间转换
         * @param src - input image: 8-bit unsigned, 16-bit unsigned (CV_16UC...), or single-precision floating-point.
         * zh-CN: 输入图像, 支持 8 位无符号整形, 16 位无符号整形或单精度浮点数
         * @param code - color space conversion code (without "COLOR_" prefix).
         * zh-CN: 颜色空间转换标识符 (参数不包含 "COLOR_" 前缀)
         * @param dstCn - number of channels in the destination image;
         * if the parameter is 0, the number of the channels is derived automatically from src and code.
         * zh-CN: 目标图像的通道数, 该参数为 0 时, 目标图像根据源图像的通道数和具体操作自动决定
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.cvtColor(images.captureScreen(), "BGR2BGRA");
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.cvtColor = function (src, code, dstCn) {
         *     initIfNeeded();
         *     let mat = new Mat();
         *     code = Imgproc["COLOR_" + code];
         *     if (dstCn === undefined) {
         *         Imgproc.cvtColor(src.mat, mat, code);
         *     } else {
         *         Imgproc.cvtColor(src.mat, mat, code, dstCn);
         *     }
         *     return images.matToImage(mat);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * @see org.opencv.imgproc.Imgproc
         * @see org.opencv.imgproc.Imgproc.cvtColor
         * @see https://docs.opencv.org/3.1.0/d7/d1b/group__imgproc__misc.html#ga4e0972be5de079fed4e3a10e24ef5ef0
         */
        cvtColor(src: ImageWrapper, code: ConversionCodes, dstCn?: number): ImageWrapper;

        /**
         * @param img
         * @param color
         * @param x
         * @param y
         * @param [threshold=4]
         * @param [algorithm="diff"]
         * @example
         * images.requestScreenCapture(false);
         * let like = idMatches(/.*like_icon/).findOnce();
         * if (like) {
         *     let bounds = like.bounds();
         *     let [x, y] = [bounds.centerX(), bounds.centerY()];
         *     if (!images.detectsColor(images.captureScreen(), "#FED9A8", x, y)) {
         *         like.click();
         *     }
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * const defaultColorThreshold = 4;
         * let javaImages = runtime.getImages();
         * images.detectsColor = function (img, color, x, y, threshold, algorithm) {
         *     initIfNeeded();
         *     color = parseColor(color);
         *     algorithm = algorithm || "diff";
         *     threshold = threshold || defaultColorThreshold;
         *     let colorDetector = getColorDetector(color, algorithm, threshold);
         *     let pixel = images.pixel(img, x, y);
         *     return colorDetector.detectsColor(colors.red(pixel), colors.green(pixel), colors.blue(pixel));
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function parseColor(color) {
         *     if (typeof (color) === 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         * function getColorDetector(color, algorithm, threshold) {
         *     switch (algorithm) {
         *         case "rgb":
         *             return new org.autojs.autojs.core.image.ColorDetector.RGBDistanceDetector(color, threshold);
         *         case "equal":
         *             return new org.autojs.autojs.core.image.ColorDetector.EqualityDetector(color);
         *         case "diff":
         *             return new org.autojs.autojs.core.image.ColorDetector.DifferenceDetector(color, threshold);
         *         case "rgb+":
         *             return new org.autojs.autojs.core.image.ColorDetector.WeightedRGBDistanceDetector(color, threshold);
         *         case "hs":
         *             return new org.autojs.autojs.core.image.ColorDetector.HSDistanceDetector(color, threshold);
         *     }
         *     throw new Error("Unknown algorithm: " + algorithm);
         * }
         */
        detectsColor(
            img: ImageWrapper, color: Color$, x: number, y: number,
            threshold?: number, algorithm?: DetectionAlgorithm,
        ): boolean;

        /**
         * @example
         * images.requestScreenCapture(false);
         * let capt = images.captureScreen();
         * let points_1 = images.findAllPointsForColor(capt, "#FF0000", {threshold: 51});
         * let points_2 = images.findAllPointsForColor(capt, "#FF0000", {similarity: 0.8}); // same as above
         * let points_3 = images.findAllPointsForColor(capt, "#00FF00", {
         *     region: [400, 500, 300, 200],
         *     threshold: 4,
         * });
         * if (points_3.length) {
         *     points_3.forEach((point_3) => {
         *         console.log(point_3.x + ", " + point_3.y);
         *     });
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * const defaultColorThreshold = 4;
         * let javaImages = runtime.getImages();
         * let colorFinder = javaImages.colorFinder;
         * images.findAllPointsForColor = function (img, color, options) {
         *     initIfNeeded();
         *     color = parseColor(color);
         *     options = options || {};
         *     let threshold;
         *     if (options.similarity) {
         *         threshold = parseInt(255 * (1 - options.similarity));
         *     } else {
         *         threshold = options.threshold || defaultColorThreshold;
         *     }
         *     if (options.region) {
         *         return toPointArray(colorFinder.findAllPointsForColor(img, color, threshold, buildRegion(options.region, img)));
         *     } else {
         *         return toPointArray(colorFinder.findAllPointsForColor(img, color, threshold, null));
         *     }
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function parseColor(color) {
         *     if (typeof (color) === 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         * function buildRegion(region, img) {
         *     if (region === undefined) {
         *         region = [];
         *     }
         *     let x = region[0] === undefined ? 0 : region[0];
         *     let y = region[1] === undefined ? 0 : region[1];
         *     let width = region[2] === undefined ? img.getWidth() - x : region[2];
         *     let height = region[3] === undefined ? (img.getHeight() - y) : region[3];
         *     let r = new org.opencv.core.Rect(x, y, width, height);
         *     if (x < 0 || y < 0 || x + width > img.width || y + height > img.height) {
         *         throw new Error('out of region: region = [' + [x, y, width, height] + "], image.size = [' + [img.width, img.height] + ']");
         *     }
         *     return r;
         * }
         * function toPointArray(points) {
         *     let arr = [];
         *     for (let i = 0; i < points.length; i++) {
         *         arr.push(points[i]);
         *     }
         *     return arr;
         * }
         * @see findPointsByColor
         */
        findAllPointsForColor(img: ImageWrapper, color: Color$, options?: {
            similarity?: number,
            threshold?: number,
            region?: OmniRegion,
        }): OpenCV.Points;

        /**
         * @param grayImg - 8-bit, single-channel, grayscale input image.
         * zh-CN: 源图像: 单通道 8 位灰度图像
         * @param [options={}]
         * @param [options.region]
         * @param [options.dp=1] - Inverse ratio of the accumulator resolution to the image resolution.
         * For example, if dp=1 , the accumulator has the same resolution as the input image.
         * If dp=2 , the accumulator has half as big width and height.
         * zh-CN: 图像分辨率与累加器 (参数空间) 分辨率的比值 (圆弧圆心的累计分辨率).
         * dp=1, 累加器 (参数空间) 与图像像素空间的分辨率相同.
         * dp=2, 累加器 (参数空间) 的分辨率为像素空间的一半 (即累加器分辨率受影响更小)
         * @param [options.minDst=grayImg.height/8] - Minimum distance between the centers of the detected circles.
         * If the parameter is too small, multiple neighbor circles may be falsely detected in addition to a true one.
         * If it is too large, some circles may be missed.
         * zh-CN: 圆心之间最小距离. 若距离太小会产生很多相交的圆. 若距离太大则会漏掉正确的圆
         * @param [options.param1=100] - First method-specific parameter.
         * It is the higher threshold of the two passed to the Canny edge detector (the lower one is twice smaller).
         * zh-CN: 用于 Canny 的边缘阀值上限. (下限为上限的一半)
         * @param [options.param2=100] - Second method-specific parameter.
         * It is the accumulator threshold for the circle centers at the detection stage.
         * The smaller it is, the more false circles may be detected.
         * Circles, corresponding to the larger accumulator values, will be returned first.
         * zh-CN: 累加器阈值 (基于圆心的最小投票数). 阈值越小, 检测到的圆越多
         * @param [options.minRadius=0] - Minimum circle radius.
         * zh-CN: 最小圆半径
         * @param [options.maxRadius=0] - Maximum circle radius.
         * If <= 0, uses the maximum image dimension.
         * If < 0, returns centers without finding the radius.
         * zh-CN: 最大圆半径
         * @example
         * images.requestScreenCapture(false);
         * let capt = images.captureScreen();
         * let results = images.findCircles(images.grayscale(capt), {
         *     region: [120, 144, 720 - 120 * 2, 1280 - 144 * 2],
         *     dp: 1,
         *     minDst: 720 * 0.091,
         *     param1: 15,
         *     param2: 15,
         *     minRadius: 720 * 0.06,
         *     maxRadius: 720 * 0.078,
         * }).map((o) => {
         *     o.radius = +o.radius.toFixed(2);
         *     return o;
         * });
         * console.log(results);
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.findCircles = function (grayImg, options) {
         *     initIfNeeded();
         *     options = options || {};
         *     let mat = options.region === undefined ? grayImg.mat : new Mat(grayImg.mat, buildRegion(options.region, grayImg));
         *     let resultMat = new Mat();
         *     let dp = options.dp === undefined ? 1 : options.dp;
         *     let minDst = options.minDst === undefined ? grayImg.height / 8 : options.minDst;
         *     let param1 = options.param1 === undefined ? 100 : options.param1;
         *     let param2 = options.param2 === undefined ? 100 : options.param2;
         *     let minRadius = options.minRadius === undefined ? 0 : options.minRadius;
         *     let maxRadius = options.maxRadius === undefined ? 0 : options.maxRadius;
         *     Imgproc.HoughCircles(mat, resultMat, Imgproc.CV_HOUGH_GRADIENT, dp, minDst, param1, param2, minRadius, maxRadius);
         *     let result = [];
         *     for (let i = 0; i < resultMat.rows(); i++) {
         *         for (let j = 0; j < resultMat.cols(); j++) {
         *             let d = resultMat.get(i, j);
         *             result.push({x: d[0], y: d[1], radius: d[2]});
         *         }
         *     }
         *     if (options.region !== undefined) {
         *         mat.release();
         *     }
         *     resultMat.release();
         *     return result;
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function buildRegion(region, img) {
         *     if (region === undefined) {
         *         region = [];
         *     }
         *     let x = region[0] === undefined ? 0 : region[0];
         *     let y = region[1] === undefined ? 0 : region[1];
         *     let width = region[2] === undefined ? img.getWidth() - x : region[2];
         *     let height = region[3] === undefined ? (img.getHeight() - y) : region[3];
         *     let r = new org.opencv.core.Rect(x, y, width, height);
         *     if (x < 0 || y < 0 || x + width > img.width || y + height > img.height) {
         *         throw new Error('out of region: region = [' + [x, y, width, height] + "], image.size = [' + [img.width, img.height] + ']");
         *     }
         *     return r;
         * }
         * @see org.opencv.imgproc.Imgproc.HoughCircles
         * @see org.autojs.autojs.core.opencv.Mat
         * @see org.opencv.imgproc.Imgproc
         * @see https://docs.opencv.org/3.4/javadoc/org/opencv/imgproc/Imgproc.html#HoughCircles(org.opencv.core.Mat,org.opencv.core.Mat,int,double,double,double,double,int,int)
         */
        findCircles(grayImg: ImageWrapper, options?: Images.Circles.Options): Images.Circles.Result;

        /**
         * @example
         * images.requestScreenCapture(false);
         * let capt = images.captureScreen();
         * let point_1 = images.findColor(capt, "#FF0000", {threshold: 51});
         * let point_2 = images.findColor(capt, "#FF0000", {similarity: 0.8}); // same as above
         * let point_3 = images.findColor(capt, "#00FF00", {
         *     region: [400, 500, 300, 200],
         *     threshold: 4,
         * });
         * if (point_3) {
         *     toastLog(point_3.x + ", " + point_3.y);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * const defaultColorThreshold = 4;
         * let javaImages = runtime.getImages();
         * let colorFinder = javaImages.colorFinder;
         * images.findColor = function (img, color, options) {
         *     initIfNeeded();
         *     color = parseColor(color);
         *     options = options || {};
         *     let threshold;
         *     if (options.similarity) {
         *         threshold = parseInt(255 * (1 - options.similarity));
         *     } else {
         *         threshold = options.threshold || defaultColorThreshold;
         *     }
         *     if (options.region) {
         *         return colorFinder.findColor(img, color, threshold, buildRegion(options.region, img));
         *     } else {
         *         return colorFinder.findColor(img, color, threshold, null);
         *     }
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function parseColor(color) {
         *     if (typeof (color) === 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         * function buildRegion(region, img) {
         *     if (region === undefined) {
         *         region = [];
         *     }
         *     let x = region[0] === undefined ? 0 : region[0];
         *     let y = region[1] === undefined ? 0 : region[1];
         *     let width = region[2] === undefined ? img.getWidth() - x : region[2];
         *     let height = region[3] === undefined ? (img.getHeight() - y) : region[3];
         *     let r = new org.opencv.core.Rect(x, y, width, height);
         *     if (x < 0 || y < 0 || x + width > img.width || y + height > img.height) {
         *         throw new Error('out of region: region = [' + [x, y, width, height] + "], image.size = [' + [img.width, img.height] + ']");
         *     }
         *     return r;
         * }
         */
        findColor(img: ImageWrapper, color: Color$, options?: {
            similarity?: number,
            threshold?: number,
            region?: OmniRegion,
        }): OpenCV.Point | null;

        /**
         * @param img
         * @param color
         * @param [x=0]
         * @param [y=0]
         * @param [width=img.getWidth()-x]
         * @param [height=img.getHeight()-y]
         * @example
         * images.requestScreenCapture(false);
         * let capt = images.captureScreen();
         * let point = images.findColorEquals(capt, "#FFFFFF");
         * if (point) {
         *     toastLog(point.x + ", " + point.y);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * images.findColorEquals = function (img, color, x, y, width, height) {
         *     return findColor(img, color, {
         *         region: [x, y, width, height],
         *         threshold: 0
         *     });
         * };
         * @see findColor
         */
        findColorEquals(img: ImageWrapper, color: Color$, x?: X, y?: Y, width?: Width, height?: Height): OpenCV.Point | null;

        /**
         * @param img
         * @param color
         * @param [x=0]
         * @param [y=0]
         * @param [width=img.getWidth()-x]
         * @param [height=img.getHeight()-y]
         * @param [threshold=4]
         * @example
         * images.requestScreenCapture(false);
         * let capt = images.captureScreen();
         * let point = images.findColorInRegion(capt, "#FFFFFF");
         * if (point) {
         *     toastLog(point.x + ", " + point.y);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * images.findColorInRegion = function (img, color, x, y, width, height, threshold) {
         *     return findColor(img, color, {
         *         region: [x, y, width, height],
         *         threshold: threshold
         *     });
         * };
         * @see findColor
         */
        findColorInRegion(img: ImageWrapper, color: Color$, x?: X, y?: Y, width?: Width, height?: Height, threshold?: number): OpenCV.Point | null;

        /**
         * @param img
         * @param template
         * @param [options={}]
         * @param [options.threshold=0.9]
         * @param [options.weakThreshold=0.6]
         * @param [options.level=-1]
         * @param [options.region]
         * @example
         * images.requestScreenCapture(false);
         * let capt = images.captureScreen();
         * let img = images.read("/sdcard/capture.png");
         * let templ = images.read("/sdcard/template.png");
         * let point = images.findImage(img, templ);
         * if (point) {
         *     console.log(point.x + ", " + point.y);
         * }
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.findImage = function (img, template, options) {
         *     initIfNeeded();
         *     options = options || {};
         *     let threshold = options.threshold || 0.9;
         *     let maxLevel = -1;
         *     if (typeof (options.level) === 'number') {
         *         maxLevel = options.level;
         *     }
         *     let weakThreshold = options.weakThreshold || 0.6;
         *     if (options.region) {
         *         return javaImages.findImage(img, template, weakThreshold, threshold, buildRegion(options.region, img), maxLevel);
         *     } else {
         *         return javaImages.findImage(img, template, weakThreshold, threshold, null, maxLevel);
         *     }
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function buildRegion(region, img) {
         *     if (region === undefined) {
         *         region = [];
         *     }
         *     let x = region[0] === undefined ? 0 : region[0];
         *     let y = region[1] === undefined ? 0 : region[1];
         *     let width = region[2] === undefined ? img.getWidth() - x : region[2];
         *     let height = region[3] === undefined ? (img.getHeight() - y) : region[3];
         *     let r = new org.opencv.core.Rect(x, y, width, height);
         *     if (x < 0 || y < 0 || x + width > img.width || y + height > img.height) {
         *         throw new Error('out of region: region = [' + [x, y, width, height] + "], image.size = [' + [img.width, img.height] + ']");
         *     }
         *     return r;
         * }
         * @see org.autojs.autojs.runtime.api.Images.findImage
         */
        findImage(img: ImageWrapper, template: ImageWrapper, options?: {
            threshold?: number,
            weakThreshold?: number,
            level?: number,
            region?: OmniRegion,
        }): OpenCV.Point | null;

        /**
         * @param image
         * @param template
         * @param [x=0]
         * @param [y=0]
         * @param [width=img.getWidth()-x]
         * @param [height=img.getHeight()-y]
         * @param [threshold=4]
         * @example Source code summary (zh-CN: 源代码摘要)
         * images.findImageInRegion = function (img, template, x, y, width, height, threshold) {
         *     return images.findImage(img, template, {
         *         region: [x, y, width, height],
         *         threshold: threshold
         *     });
         * };
         * @see findImage
         */
        findImageInRegion(
            image: ImageWrapper, template: ImageWrapper, x?: X, y?: Y,
            width?: Width, height?: Height, threshold?: number,
        ): OpenCV.Point | null;

        /**
         * @example
         * images.requestScreenCapture(false);
         * let capt = images.captureScreen();
         * let point = images.findMultiColors(
         *     capt, "#123456",
         *     [[10, 20, "#FFFFFF"], [30, 40, "#000000"]],
         *     {region: [0, 960, 1080, 960]}
         * );
         * @example Source code summary (zh-CN: 源代码摘要)
         * const defaultColorThreshold = 4;
         * let javaImages = runtime.getImages();
         * let colorFinder = javaImages.colorFinder;
         * images.findMultiColors = function (img, firstColor, paths, options) {
         *     initIfNeeded();
         *     options = options || {};
         *     firstColor = parseColor(firstColor);
         *     let list = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, paths.length * 3);
         *     for (let i = 0; i < paths.length; i++) {
         *         let p = paths[i];
         *         list[i * 3] = p[0];
         *         list[i * 3 + 1] = p[1];
         *         list[i * 3 + 2] = parseColor(p[2]);
         *     }
         *     let region = options.region ? buildRegion(options.region, img) : null;
         *     let threshold = options.threshold === undefined ? defaultColorThreshold : options.threshold;
         *     return colorFinder.findMultiColors(img, firstColor, threshold, region, list);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function parseColor(color) {
         *     if (typeof (color) === 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         * function buildRegion(region, img) {
         *     if (region === undefined) {
         *         region = [];
         *     }
         *     let x = region[0] === undefined ? 0 : region[0];
         *     let y = region[1] === undefined ? 0 : region[1];
         *     let width = region[2] === undefined ? img.getWidth() - x : region[2];
         *     let height = region[3] === undefined ? (img.getHeight() - y) : region[3];
         *     let r = new org.opencv.core.Rect(x, y, width, height);
         *     if (x < 0 || y < 0 || x + width > img.width || y + height > img.height) {
         *         throw new Error('out of region: region = [' + [x, y, width, height] + "], image.size = [' + [img.width, img.height] + ']");
         *     }
         *     return r;
         * }
         * @see org.autojs.autojs.core.image.ColorFinder.findMultiColors
         */
        findMultiColors(
            img: ImageWrapper, firstColor: Color$, paths: [X, Y, Color$][],
            options?: {
                region?: OmniRegion;
                threshold?: number;
            },
        ): OpenCV.Point | null;

        // noinspection SpellCheckingInspection
        /**
         * @example
         * // "data:\\w+/\\w+;base64," could be omitted
         * let base64 = "data:image/jpg;base64," + "/9j/4AAQSkZJRgABAQEBLAEsAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAAhACEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwB/i3xJfeI9VmubqaQwFz5MO75Y17AD1x1PetHwf4C1bxPAbm28q3swdommJAYjrtAGT/KuSr0F/HTz+CrTw1pNlcQXZWOHzYpOXOeQABn5j/Ov03ExrUKUaWDilra/RLufm2GlSrVZVMW29L26t9iPxJ8LtZ0awkvIpIL2CJS0giyHUDqcEcj6GuR0XWL7Rb2O6025khlQ5wp+VvZh3HtXtfw6F74X8G39z4rd7e2EheOK4bLBccgA+p6L/jXgzkFiQMAngelYZdiKmJdWjXtNRdrpaP8ATQ3zChTwypVaN4OSvZvVfrqezf8AC44v+gf/AOPUV4xRS/sDA/yfix/27jf5vwQV1E3hbXNE0mz8RgRLbgxTxSI4YqTgqSPrjrWNr2lXOi6tc2F5GySwuRyPvDsw9iK7rwT8RodM0YaPr9k17YKCqMoViFP8LK3DD8a6cZVrezjUw0VNdV3Xkc2EpUfaSp4luD6Ps/M634XeILzxlp2qWHiKOK8ijCDe0YAYNn5SAMZ4yCK8W1u1Sx1m/tIW3RwXEkSse4ViB/KvUNQ+KOlafpclr4S0k2sj5wzxpGiE/wAW1Sdx+uK8pt4LjUL1IYEknuZ3wqryzsa48roVKdWrWlD2cJWtH03fkdmZ14VKdKjGftJq95euy8yCivT/APhUOqf8/MH50V0/2zgv+fiOb+x8Z/z7ZsfHv/j30/8AGvGKKKxyH/cYfP8AM2z3/fZfL8gr0/4E/wDIeuf+uRoorbOf9yqehjlH++U/U9zooor80P0c/9k=";
         * let img = images.fromBase64(base64);
         * console.log(img.width + " × " + img.height);
         * @example Source code summary (zh-CN: 源代码摘要)
         * images.fromBase64 = function (base64) {
         *     return javaImages.fromBase64(base64);
         * };
         * @see org.autojs.autojs.runtime.api.Images.fromBase64
         */
        fromBase64(base64: string): ImageWrapper;

        /**
         * images.fromBytes = function (bytes) {
         *     return javaImages.fromBytes(bytes);
         * };
         * @see org.autojs.autojs.runtime.api.Images.fromBytes
         */
        fromBytes(bytes: number[]): ImageWrapper;

        /**
         * Blurs an image using a Gaussian filter.
         * zh-CN: 高斯滤波: 使用高斯滤波器平滑图像 (高斯模糊)
         * @param src - input image; the image can have any number of channels, which are processed independently,
         * but the depth should be CV_8U, CV_16U, CV_16S, CV_32F or CV_64F.
         * zh-CN: 源图像: 通道数不限, 位深需为 CV_8U / CV_16U / CV_16S / CV_32F / CV_64F
         * @param ksize - Gaussian kernel size.
         * zh-CN: 高斯内核大小 (ksize[0] 和 ksize[1] 可以不相同但这值必须均为正奇数, 若两个值为 0, 他们的值将由 sigma 参数计算)
         * @param [sigmaX=0] - Gaussian kernel standard deviation in X direction.
         * zh-CN: 高斯核函数在X方向上的标准偏差
         * @param [sigmaY=0] - Gaussian kernel standard deviation in Y direction;
         * if sigmaY is zero, it is set to be equal to sigmaX,
         * if both sigmas are zeros, they are computed from ksize.width and ksize.height, respectively
         * zh-CN: 高斯核函数在Y方向上的标准偏差 (若 sigmaY 是 0, 值将被设置为 sigmaX 值; 若 sigmaX 和 sigmaY 均为 0, 这两个值将由 ksize 计算而来)
         * @param [type="DEFAULT"] - pixel extrapolation method (without "BORDER_" prefix).
         * zh-CN: 推断图像外部像素的方法
         * @example
         * images.requestScreenCapture();
         * let dst = images.gaussianBlur(images.captureScreen(), [3, 3]);
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.gaussianBlur = function (src, ksize, sigmaX, sigmaY, type) {
         *     initIfNeeded();
         *     let mat = new Mat();
         *     ksize = newSize(ksize);
         *     sigmaX = sigmaX === undefined ? 0 : sigmaX;
         *     sigmaY = sigmaY === undefined ? 0 : sigmaY;
         *     type = Core["BORDER_" + (type || "DEFAULT")];
         *     Imgproc.GaussianBlur(src.mat, mat, ksize, sigmaX, sigmaY, type);
         *     return images.matToImage(mat);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function newSize(size) {
         *     if (!Array.isArray(size)) {
         *         size = [size, size];
         *     }
         *     if (size.length === 1) {
         *         size = [size[0], size[0]];
         *     }
         *     return new Size(size[0], size[1]);
         * }
         * @see org.autojs.autojs.core.opencv.Mat
         * @see org.opencv.imgproc.Imgproc
         * @see org.opencv.core.Size
         * @see org.opencv.core.Core
         * @see https://docs.opencv.org/3.4/javadoc/org/opencv/imgproc/Imgproc.html#GaussianBlur(org.opencv.core.Mat,org.opencv.core.Mat,org.opencv.core.Size,double,double,int)
         * @see https://blog.csdn.net/keith_bb/article/details/54412493
         */
        gaussianBlur(
            src: ImageWrapper, ksize: Side | [Width, Height?],
            sigmaX?: number, sigmaY?: number, type?: Images.BorderTypes,
        ): ImageWrapper;

        /**
         * Applies the bilateral filter to an image
         * @param img - Source 8-bit or floating-point, 1-channel or 3-channel image
         * @param [d=0] - Diameter of each pixel neighborhood that is used during filtering. If it is non-positive, it is computed from sigmaSpace
         * @param [sigma_color=40] - Filter sigma in the color space. A larger value of the parameter means that farther colors within the pixel neighborhood (see sigmaSpace) will be mixed together, resulting in larger areas of semi-equal color
         * @param [sigma_space=20] - Filter sigma in the coordinate space. A larger value of the parameter means that farther pixels will influence each other as long as their colors are close enough (see sigmaColor ). When d>0, it specifies the neighborhood size regardless of sigmaSpace. Otherwise, d is proportional to sigmaSpace
         * @param [border_type='DEFAULT'] - border mode used to extrapolate pixels outside the image
         * @see https://docs.opencv.org/3.4.3/d4/d86/group__imgproc__filter.html#ga9d7064d478c95d60003cf839430737ed
         */
        bilateralFilter(
            img: ImageWrapper, d?: number, sigma_color?: number,
            sigma_space?: number, border_type?: Images.BorderTypes,
        ): ImageWrapper;

        /**
         * Grayscale an image.
         * zh-CN: 灰度化
         * @param src - input image: 8-bit unsigned, 16-bit unsigned (CV_16UC...), or single-precision floating-point.
         * zh-CN: 输入图像, 支持 8 位无符号整形, 16 位无符号整形或单精度浮点数
         * @param dstCn - number of channels in the destination image;
         * if the parameter is 0, the number of the channels is derived automatically from src and code.
         * zh-CN: 目标图像的通道数, 该参数为 0 时, 目标图像根据源图像的通道数和具体操作自动决定
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.grayscale(images.captureScreen());
         * @example Source code summary (zh-CN: 源代码摘要)
         * images.grayscale = function (src, dstCn) {
         *     return images.cvtColor(src, "BGR2GRAY", dstCn);
         * };
         * @see cvtColor
         */
        grayscale(src: ImageWrapper, dstCn?: number): ImageWrapper;

        /**
         * Checks if array elements lie between the elements of two other arrays
         * @example
         * images.inRange(img, "#000000", "#222222");
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.inRange = function (img, lowerBound, upperBound) {
         *     initIfNeeded();
         *     let lb = new Scalar(
         *         colors.red(lowerBound), colors.green(lowerBound),
         *         colors.blue(lowerBound), colors.alpha(lowerBound)
         *     );
         *     let ub = new Scalar(
         *         colors.red(upperBound), colors.green(upperBound),
         *         colors.blue(upperBound), colors.alpha(lowerBound)
         *     );
         *     let bi = new Mat();
         *     Core.inRange(img.mat, lb, ub, bi);
         *     return images.matToImage(bi);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * @see org.opencv.core.Core.inRange
         * @see org.autojs.autojs.core.opencv.Mat
         * @see org.opencv.core.Scalar
         */
        inRange(img: ImageWrapper, lowerBound: Color$, upperBound: Color$): ImageWrapper;

        /**
         * @example
         * // set colors within the range of channel color value ±tolerance into #ffffff,
         * // and set colors out of range above to #000000
         * // zh-CN: 每个通道的颜色值均为 0x88 ，加减偏差 16 之后的范围是 [0x78, 0x98]
         * // 因此此方法将 #787878 - #989898 范围颜色值变成 #ffffff 范围外变成 #000000
         * images.interval(img, "#888888", 16);
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.interval = function (img, color, tolerance) {
         *     initIfNeeded();
         *     let lb = new Scalar(colors.red(color) - tolerance, colors.green(color) - tolerance,
         *         colors.blue(color) - tolerance, colors.alpha(color));
         *     let ub = new Scalar(colors.red(color) + tolerance, colors.green(color) + tolerance,
         *         colors.blue(color) + tolerance, colors.alpha(color));
         *     let bi = new Mat();
         *     Core.inRange(img.mat, lb, ub, bi);
         *     return images.matToImage(bi);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * @see org.opencv.core.Core.inRange
         * @see org.autojs.autojs.core.opencv.Mat
         * @see org.opencv.core.Scalar
         */
        interval(img: ImageWrapper, color: Color$, tolerance: number): ImageWrapper;

        /**
         * @param img
         * @param template
         * @param [options={}]
         * @param [options.threshold=0.9]
         * @param [options.weakThreshold=0.6]
         * @param [options.level=-1]
         * @param [options.region]
         * @param [options.max=5]
         * @example
         * images.requestScreenCapture(false);
         * let capt = images.captureScreen();
         * let img = images.read("/sdcard/capture.png");
         * let templ = images.read("/sdcard/template.png");
         * let matches = images.matchTemplate(img, templ, {threshold: 20, max: 11});
         * matches.points.forEach(point => console.log(point.x + ", " + point.y));
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.matchTemplate = function (img, template, options) {
         *     initIfNeeded();
         *     options = options || {};
         *     let threshold = options.threshold || 0.9;
         *     let maxLevel = -1;
         *     if (typeof (options.level) === 'number') {
         *         maxLevel = options.level;
         *     }
         *     let max = options.max || 5;
         *     let weakThreshold = options.weakThreshold || 0.6;
         *     let result;
         *     if (options.region) {
         *         result = javaImages.matchTemplate(img, template, weakThreshold, threshold, buildRegion(options.region, img), maxLevel, max);
         *     } else {
         *         result = javaImages.matchTemplate(img, template, weakThreshold, threshold, null, maxLevel, max);
         *     }
         *     return new MatchingResult(result);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function buildRegion(region, img) {
         *     if (region === undefined) {
         *         region = [];
         *     }
         *     let x = region[0] === undefined ? 0 : region[0];
         *     let y = region[1] === undefined ? 0 : region[1];
         *     let width = region[2] === undefined ? img.getWidth() - x : region[2];
         *     let height = region[3] === undefined ? (img.getHeight() - y) : region[3];
         *     let r = new org.opencv.core.Rect(x, y, width, height);
         *     if (x < 0 || y < 0 || x + width > img.width || y + height > img.height) {
         *         throw new Error('out of region: region = [' + [x, y, width, height] + "], image.size = [' + [img.width, img.height] + ']");
         *     }
         *     return r;
         * }
         * @see org.autojs.autojs.runtime.api.Images.matchTemplate
         */
        matchTemplate(img: ImageWrapper, template: ImageWrapper, options?: {
            threshold?: number;
            weakThreshold?: number;
            level?: number;
            region?: OmniRegion;
            max?: number;
        }): Images.MatchingResult;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.matToImage = function (mat) {
         *     initIfNeeded();
         *     return Image.ofMat(mat);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         */
        matToImage(mat: org.autojs.autojs.core.opencv.Mat | org.opencv.core.Mat): ImageWrapper;

        /**
         * Blurs an image using the median filter.
         * zh-CN: 中值滤波: 使用中值滤波器平滑图像
         * @param src - input 1-, 3-, or 4-channel image;
         * when ksize is 3 or 5, the image depth should be CV_8U, CV_16U, or CV_32F, for larger aperture sizes, it can only be CV_8U.
         * zh-CN: 输入图像: 图像为 1, 3, 4 通道图像; 当模板尺寸为 3 或 5 时, 图像深度只能为 CV_8U / CV_16U / CV_32F , 对于较大孔径尺寸的图片则只能是 CV_8U
         * @param ksize - aperture linear size; it must be odd and greater than 1, for example: 3, 5, 7 ...
         * zh-CN: 滤波模板尺寸: 需为大于 1 的奇数, 如 3, 5, 7...
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.medianBlur(images.captureScreen(), 9);
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.medianBlur = function (src, ksize) {
         *     initIfNeeded();
         *     let mat = new Mat();
         *     Imgproc.medianBlur(src.mat, mat, ksize);
         *     return images.matToImage(mat);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * @see org.autojs.autojs.core.opencv.Mat
         * @see org.opencv.imgproc.Imgproc
         * @see https://docs.opencv.org/3.4/javadoc/org/opencv/imgproc/Imgproc.html#medianBlur(org.opencv.core.Mat,org.opencv.core.Mat,int)
         * @see https://blog.csdn.net/keith_bb/article/details/54426920
         */
        medianBlur(src: ImageWrapper, ksize: Side | [Side, Side]): ImageWrapper;

        /**
         * @example
         * console.log(images.readPixels("test.png").data.slice(0, 10));
         * @example Source code summary (zh-CN: 源代码摘要)
         * images.readPixels = function (path) {
         *     let img = images.read(path);
         *     let bitmap = img.getBitmap();
         *     let w = bitmap.getWidth();
         *     let h = bitmap.getHeight();
         *     let pixels = util.java.array("int", w * h);
         *     bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
         *     img.recycle();
         *     return {data: pixels, width: w, height: h};
         * };
         * @see read
         * @see ImageWrapper.getBitmap
         */
        readPixels(path: string): { data: number[], width: Width, height: Height };

        /**
         * @example
         * images.requestScreenCapture(); // auto orientation
         * @example
         * images.requestScreenCapture(true); // landscape orientation
         * @example
         * images.requestScreenCapture(false); // portrait orientation
         * @example Source code summary (zh-CN: 源代码摘要)
         * const ResultAdapter = require("result-adapter");
         * let javaImages = runtime.getImages();
         * images.requestScreenCapture = function (landscape) {
         *     let ScreenCapturer = org.autojs.autojs.core.image.capture.ScreenCapturer;
         *     let orientation = ScreenCapturer.ORIENTATION_AUTO;
         *     if (landscape === true) {
         *         orientation = ScreenCapturer.ORIENTATION_LANDSCAPE;
         *     }
         *     if (landscape === false) {
         *         orientation = ScreenCapturer.ORIENTATION_PORTRAIT;
         *     }
         *     return ResultAdapter.wait(javaImages.requestScreenCapture(orientation));
         * };
         * @see runtime.getImages
         * @see org.autojs.autojs.runtime.api.Images.requestScreenCapture
         * @see org.autojs.autojs.core.image.capture.ScreenCapturer
         */
        requestScreenCapture(landscape?: boolean): boolean;
        requestScreenCapture(options: Images.ScreenCaptureOptions): boolean;
        requestScreenCapture(width: number, height: number): boolean;

        requestScreenCaptureAsync(landscape?: boolean): Promise<boolean>;
        requestScreenCaptureAsync(options: Images.ScreenCaptureOptions): Promise<boolean>;
        requestScreenCaptureAsync(width: number, height: number): Promise<boolean>;

        stopScreenCapture(): void;

        getScreenCaptureOptions(): org.autojs.autojs.core.image.capture.ScreenCapturer.Options | null;

        /**
         * Resizes an image.
         * zh-CN: 缩放图像 (按尺寸)
         * @param src - input image.
         * zh-CN: 源图像
         * @param dsize - output image size (Side|[Width,Height]).
         * zh-CN: 输出图像尺寸
         * @param [interpolation="LINEAR"] - interpolation method (without "INTER_" prefix).
         * zh-CN: 插值方法 (参数不包含 “INTER_” 前缀)
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.resize(images.captureScreen(), [720, 1280]);
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.resize = function (src, size, interpolation) {
         *     initIfNeeded();
         *     let mat = new Mat();
         *     interpolation = Imgproc["INTER_" + (interpolation || "LINEAR")];
         *     Imgproc.resize(src.mat, mat, newSize(size), 0, 0, interpolation);
         *     return images.matToImage(mat);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function newSize(size) {
         *     if (!Array.isArray(size)) {
         *         size = [size, size];
         *     }
         *     if (size.length === 1) {
         *         size = [size[0], size[0]];
         *     }
         *     return new Size(size[0], size[1]);
         * }
         * @see org.autojs.autojs.core.opencv.Mat
         * @see org.opencv.imgproc.Imgproc
         * @see org.opencv.core.Size
         * @see https://docs.opencv.org/3.4/javadoc/org/opencv/imgproc/Imgproc.html#resize(org.opencv.core.Mat,org.opencv.core.Mat,org.opencv.core.Size,double,double,int)
         */
        resize(src: ImageWrapper, dsize: Side | [Width, Height?], interpolation?: Images.InterpolationFlags): ImageWrapper;

        /**
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.rotate(images.captureScreen(), 90); // 90° anticlockwise
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.rotate = function (img, degree, ctx, cty) {
         *     initIfNeeded();
         *     if (ctx === undefined) {ctx = img.width / 2}
         *     if (cty === undefined) {cty = img.height / 2}
         *     return javaImages.rotate(img, ctx, cty, degree);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * @see org.autojs.autojs.runtime.api.Images.rotate
         */
        rotate(img: ImageWrapper, degree: number, ctx?: number, cty?: number): ImageWrapper;

        /**
         * @example
         * images.save(images.captureScreen(), files.cwd(), "png", 90);
         * images.save(images.captureScreen(), files.cwd(), undefined, 90); // same as above
         * images.save(images.captureScreen(), files.cwd(), "", 90); // same as above
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.save = function (img, path, format, quality) {
         *     format = format || "png";
         *     quality = quality === undefined ? 100 : quality;
         *     return javaImages.save(img, path, format, quality);
         * };
         * @param img
         * @param path
         * @param [format="png"]
         * @param [quality=100]
         * @see runtime.getImages
         * @see org.autojs.autojs.runtime.api.Images.save
         */
        save(img: ImageWrapper, path: string, format?: Images.Format, quality?: number): boolean;

        /** @see save */
        saveImage(img: ImageWrapper, path: string, format?: Images.Format, quality?: number): boolean;

        /**
         * Resizes an image.
         * zh-CN: 缩放图像 (按比例)
         * @param src - input image.
         * zh-CN: 源图像
         * @param fx - scale factor along the horizontal axis.
         * zh-CN: 水平宽度放缩倍数
         * @param fy - scale factor along the vertical axis.
         * zh-CN: 垂直高度放缩倍数
         * @param [interpolation="LINEAR"] - interpolation method (without "INTER_" prefix).
         * zh-CN: 插值方法 (参数不包含 “INTER_” 前缀)
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.scale(images.captureScreen(), 0.5, 0.5);
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.scale = function (src, fx, fy, interpolation) {
         *     initIfNeeded();
         *     let mat = new Mat();
         *     interpolation = Imgproc["INTER_" + (interpolation || "LINEAR")];
         *     Imgproc.resize(src.mat, mat, newSize([0, 0]), fx, fy, interpolation);
         *     return images.matToImage(mat);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * function newSize(size) {
         *     if (!Array.isArray(size)) {
         *         size = [size, size];
         *     }
         *     if (size.length === 1) {
         *         size = [size[0], size[0]];
         *     }
         *     return new Size(size[0], size[1]);
         * }
         * @see org.autojs.autojs.core.opencv.Mat
         * @see org.opencv.imgproc.Imgproc
         * @see org.opencv.core.Size
         */
        scale(src: ImageWrapper, fx: number, fy: number, interpolation?: Images.InterpolationFlags): ImageWrapper;

        /**
         * Applies a fixed-level threshold to each array element.
         * zh-CN: 阈值化
         * @example
         * images.requestScreenCapture(false);
         * let dst = images.threshold(images.captureScreen(), 100, 255, "BINARY");
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.threshold = function (img, threshold, maxVal, type) {
         *     initIfNeeded();
         *     let mat = new Mat();
         *     type = type || "BINARY";
         *     type = Imgproc["THRESH_" + type];
         *     Imgproc.threshold(img.mat, mat, threshold, maxVal, type);
         *     return images.matToImage(mat);
         * };
         * function initIfNeeded() {
         *     javaImages.initOpenCvIfNeeded();
         * }
         * @param src - input array (multiple-channel, 8-bit or 32-bit floating point)
         * zh-CN: 输入图像, 多通道 8位或32位浮点类型
         * @param threshold - threshold value
         * zh-CN: 阈值设定值
         * @param maxVal - maximum value to use with the THRESH_BINARY and THRESH_BINARY_INV thresholding types
         * zh-CN: 使用THRESH_BINARY及THRESH_BINARY_INV阈值的最大值
         * @param [type="BINARY"] - thresholding type
         * zh-CN: 阈值类型
         * @see org.opencv.imgproc.Imgproc
         * @see https://docs.opencv.org/3.4/javadoc/org/opencv/imgproc/Imgproc.html#threshold(org.opencv.core.Mat,org.opencv.core.Mat,double,double,int)
         */
        threshold(src: ImageWrapper, threshold: number, maxVal: number, type?: Images.ThresholdTypes): ImageWrapper;

        /**
         * @param img
         * @param [format="png"]
         * @param [quality=100]
         * @example
         * images.requestScreenCapture(false);
         * console.log(images.toBase64(images.capt()).slice(0, 20));
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.toBase64 = function (img, format, quality) {
         *     format = format || "png";
         *     quality = quality === undefined ? 100 : quality;
         *     return javaImages.toBase64(img, format, quality);
         * };
         * @see org.autojs.autojs.runtime.api.Images.toBase64
         */
        toBase64(img: Images.ImageSource, format?: Images.Format, quality?: number): string;

        /**
         * @param img
         * @param [format="png"]
         * @param [quality=100]
         * @example
         * images.requestScreenCapture(false);
         * console.log(images.toBytes(images.capt()).slice(0, 20));
         * @example Source code summary (zh-CN: 源代码摘要)
         * let javaImages = runtime.getImages();
         * images.toBytes = function (img, format, quality) {
         *     format = format || "png";
         *     quality = quality === undefined ? 100 : quality;
         *     return javaImages.toBytes(img, format, quality);
         * };
         * @see org.autojs.autojs.runtime.api.Images.toBytes
         */
        toBytes(img: Images.ImageSource, format?: Images.Format, quality?: number): number[];

        /**
         * Check if all images have been recycled.
         */
        isRecycled(...images: ImageWrapper[]): boolean;

        /**
         * Recycle all images.
         */
        recycle(...images: ImageWrapper[]): boolean;

        compress(img: Images.ImageSource, format?: Images.Format, quality?: number): ImageWrapper;

        compressToBytes(img: Images.ImageSource, format?: Images.Format, quality?: number): number[];

        downsample(src: Images.DownsampleSource, reqWidth: number, reqHeight: number, withAlpha?: boolean): ImageWrapper;

        getSize(img: ImageWrapper | android.graphics.Bitmap | org.opencv.core.Mat | string): org.opencv.core.Size;

        getWidth(img: ImageWrapper | android.graphics.Bitmap | org.opencv.core.Mat | string): number;

        getHeight(img: ImageWrapper | android.graphics.Bitmap | org.opencv.core.Mat | string): number;

        buildRegion(img: Images.ImageSource, region: OmniRegion): org.opencv.core.Rect;

        detectAndComputeFeatures(img: Images.ImageSource, options?: Images.DetectAndComputeFeaturesOptions): Images.ImageFeatures;

        /**
         * 特征匹配.
         * @param sceneFeatures 场景图片 (大图) 的特征对象
         * @param objectFeatures 目标图片 (小图) 的特征对象
         * @param options 选项参数
         */
        matchFeatures(sceneFeatures: Images.ImageFeatures, objectFeatures: Images.ImageFeatures, options?: Images.FeatureMatchingOptions): Images.ObjectFrame | null;

        psnr(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;

        ssim(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;

        mssim(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;

        hist(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;

        mse(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;

        ncc(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource): number;

        isEqual(imageA: Images.ImageSource, imageB: Images.ImageSource): boolean;

        getSimilarity(imageA: Images.ComparableImageSource, imageB: Images.ComparableImageSource, options?: Images.SimilarityOptions): number;

    }

}

declare namespace Images {

    type Format = 'png' | 'jpeg' | 'jpg' | 'webp' | 'webp_lossless' | 'webp-lossless' | 'webp_lossy' | 'webp-lossy';
    type Wrapper = ImageWrapper;
    type ImageSource = ImageWrapper | string;
    type ComparableImageSource = ImageWrapper | org.opencv.core.Mat | string;
    type DownsampleSource = ImageWrapper | android.graphics.Bitmap | android.net.Uri | java.net.URL | string | number[];
    type SizeWidth = Percentage$;
    type SizeHeight = Percentage$;
    type Size = SizeWidth | [SizeWidth] | [SizeWidth, SizeHeight];
    type TemplateMatch = org.autojs.autojs.core.image.TemplateMatching.Match;
    type MatchingResultSortToken = 'left' | 'right' | 'top' | 'bottom' | 'best' | 'worst';
    type MatchingResultSortStrategy = MatchingResultSortToken | `${MatchingResultSortToken}-${MatchingResultSortToken}` | string;
    type InterpolationFlags = 'NEAREST' | 'LINEAR' | 'CUBIC' | 'AREA' | 'LANCZOS4' | 'LINEAR_EXACT' | 'MAX';
    type ThresholdTypes = 'BINARY' | 'BINARY_INV' | 'TRUNC' | 'TOZERO' | 'TOZERO_INV' | 'MASK' | 'OTSU' | 'TRIANGLE';
    type AdaptiveThresholdTypes = 'MEAN_C' | 'GAUSSIAN_C';
    type BorderTypes = 'CONSTANT' | 'REPLICATE' | 'REFLECT' | 'WRAP' | 'REFLECT_101' | 'TRANSPARENT' | 'REFLECT101' | 'DEFAULT' | 'ISOLATED';
    type ScreenCaptureOrientation = 'none' | 'auto' | 'portrait' | 'landscape' | number;
    type SimilarityMetric = 'psnr' | 'ssim' | 'mssim' | 'hist' | 'mse' | 'ncc' | string;

    interface ScreenCaptureOptions {
        width?: number;
        height?: number;
        orientation?: ScreenCaptureOrientation;
        isAsync?: boolean;
        async?: boolean;
    }

    interface SimilarityOptions {
        /**
         * @default 'mssim'
         */
        metric?: SimilarityMetric;
    }

    type DetectAndComputeFeaturesOptions = {
        region?: OmniRegion;
        scale?: number;
        /**
         * true: cvtColor will be Imgproc.COLOR_RGBA2GRAY<br>
         * false: cvtColor will be -1
         */
        grayscale?: boolean;
        /**
         * Numbers:<br>
         * ImageFeatureMatching.FEATURE_MATCHING_METHOD_SIFT<br>
         * ImageFeatureMatching.FEATURE_MATCHING_METHOD_ORB
         */
        method?: number | 'SIFT' | 'ORB';
    };

    type FeatureMatchingOptions = {
        /**
         * 特征匹配方式.
         * @default 'FLANNBASED'
         */
        matcher?: 'FLANNBASED' | 'BRUTEFORCE' | 'BRUTEFORCE_L1' | 'BRUTEFORCE_HAMMING' | 'BRUTEFORCE_HAMMINGLUT' | 'BRUTEFORCE_SL2';
        /**
         *  绘制匹配详情的图片路径, 主要用于调试, 通过本地图片查看匹配情况.
         */
        drawMatches?: string;
        /**
         * 匹配阈值.
         * @default 0.7
         */
        threshold?: number;
    };

    class MatchingResult {
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * function MatchingResult(list) {
         *     if (Array.isArray(list)) {
         *         this.matches = list;
         *     } else {
         *         this.matches = runtime.bridges.bridges.toArray(list);
         *     }
         *     this.__defineGetter__("points", () => {
         *         if (typeof (this.__points__) == 'undefined') {
         *             this.__points__ = this.matches.map(m => m.point);
         *         }
         *         return this.__points__;
         *     });
         * }
         */
        constructor(list: Images.TemplateMatch);
        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * function MatchingResult(list) {
         *     if (Array.isArray(list)) {
         *         this.matches = list;
         *     } else {
         *         this.matches = runtime.bridges.bridges.toArray(list);
         *     }
         *     this.__defineGetter__("points", () => {
         *         if (typeof (this.__points__) == 'undefined') {
         *             this.__points__ = this.matches.map(m => m.point);
         *         }
         *         return this.__points__;
         *     });
         * }
         */
        constructor(list: Images.TemplateMatch[]);

        /**
         * @example
         * let result = images.matchTemplate(img, template, {max: 100});
         * result.matches.forEach(match => log("point: " + match.point + ", similarity: " + match.similarity));
         * @example Source code summary (zh-CN: 源代码摘要)
         * function MatchingResult(list) {
         *     if (Array.isArray(list)) {
         *         this.matches = list;
         *     } else {
         *         this.matches = runtime.bridges.bridges.toArray(list);
         *     }
         *     this.__defineGetter__("points", () => {
         *         if (typeof (this.__points__) == 'undefined') {
         *             this.__points__ = this.matches.map(m => m.point);
         *         }
         *         return this.__points__;
         *     });
         * }
         */
        matches: Images.TemplateMatch[];

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * function MatchingResult(list) {
         *     if (Array.isArray(list)) {
         *         this.matches = list;
         *     } else {
         *         this.matches = runtime.bridges.bridges.toArray(list);
         *     }
         *     this.__defineGetter__("points", () => {
         *         if (typeof (this.__points__) == 'undefined') {
         *             this.__points__ = this.matches.map(m => m.point);
         *         }
         *         return this.__points__;
         *     });
         * }
         */
        get points(): OpenCV.Points;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * MatchingResult.prototype.first = function () {
         *     if (this.matches.length === 0) {
         *         return null;
         *     }
         *     return this.matches[0];
         * };
         * @see matches
         */
        first(): Images.TemplateMatch | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * MatchingResult.prototype.last = function () {
         *     if (this.matches.length === 0) {
         *         return null;
         *     }
         *     return this.matches[this.matches.length - 1];
         * };
         * @see matches
         */
        last(): Images.TemplateMatch | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * MatchingResult.prototype.findMax = function (cmp) {
         *     if (this.matches.length === 0) {
         *         return null;
         *     }
         *     let target = this.matches[0];
         *     this.matches.forEach(m => {
         *         if (cmp(target, m) > 0) {
         *             target = m;
         *         }
         *     });
         *     return target;
         * };
         * @see matches
         */
        findMax(compareFn?: (a: Images.TemplateMatch, b: Images.TemplateMatch) => number): Images.TemplateMatch | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let comparators = {
         *     "left": (l, r) => l.point.x - r.point.x,
         *     "top": (l, r) => l.point.y - r.point.y,
         *     "right": (l, r) => r.point.x - l.point.x,
         *     "bottom": (l, r) => r.point.y - l.point.y
         * };
         * MatchingResult.prototype.leftmost = function () {
         *     return this.findMax(comparators.left);
         * };
         * @see findMax
         */
        leftmost(): Images.TemplateMatch | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let comparators = {
         *     "left": (l, r) => l.point.x - r.point.x,
         *     "top": (l, r) => l.point.y - r.point.y,
         *     "right": (l, r) => r.point.x - l.point.x,
         *     "bottom": (l, r) => r.point.y - l.point.y
         * };
         * MatchingResult.prototype.topmost = function () {
         *     return this.findMax(comparators.top);
         * };
         * @see findMax
         */
        topmost(): Images.TemplateMatch | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let comparators = {
         *     "left": (l, r) => l.point.x - r.point.x,
         *     "top": (l, r) => l.point.y - r.point.y,
         *     "right": (l, r) => r.point.x - l.point.x,
         *     "bottom": (l, r) => r.point.y - l.point.y
         * };
         * MatchingResult.prototype.rightmost = function () {
         *     return this.findMax(comparators.right);
         * };
         * @see findMax
         */
        rightmost(): Images.TemplateMatch | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * let comparators = {
         *     "left": (l, r) => l.point.x - r.point.x,
         *     "top": (l, r) => l.point.y - r.point.y,
         *     "right": (l, r) => r.point.x - l.point.x,
         *     "bottom": (l, r) => r.point.y - l.point.y
         * };
         * MatchingResult.prototype.bottommost = function () {
         *     return this.findMax(comparators.bottom);
         * };
         * @see findMax
         */
        bottommost(): Images.TemplateMatch | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * MatchingResult.prototype.worst = function () {
         *     return this.findMax((l, r) => l.similarity - r.similarity);
         * };
         * @see findMax
         */
        worst(): Images.TemplateMatch | null;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * MatchingResult.prototype.best = function () {
         *     return this.findMax((l, r) => r.similarity - l.similarity);
         * };
         * @see findMax
         */
        best(): Images.TemplateMatch | null;

        /**
         * @example
         * let result = images.matchTemplate(img, template, {max: 100});
         * result.sortBy("top-right");
         * @example Source code summary (zh-CN: 源代码摘要)
         * MatchingResult.prototype.sortBy = function (cmp) {
         *     let comparatorFn = null;
         *     if (typeof (cmp) == 'string') {
         *         cmp.split('-').forEach(direction => {
         *             let buildInFn = comparators[direction];
         *             if (!buildInFn) {
         *                 throw new Error("unknown direction '" + direction + "' in '" + cmp + "'");
         *             }
         *             (function (fn) {
         *                 if (comparatorFn == null) {
         *                     comparatorFn = fn;
         *                 } else {
         *                     comparatorFn = (function (comparatorFn, fn) {
         *                         return function (l, r) {
         *                             let cmpValue = comparatorFn(l, r);
         *                             if (cmpValue === 0) {
         *                                 return fn(l, r);
         *                             }
         *                             return cmpValue;
         *                         }
         *                     })(comparatorFn, fn);
         *                 }
         *             })(buildInFn);
         *         });
         *     } else {
         *         comparatorFn = cmp;
         *     }
         *     let clone = this.matches.slice();
         *     clone.sort(comparatorFn);
         *     return new MatchingResult(clone);
         * };
         */
        sortBy(compareFn?: ((a: Images.TemplateMatch, b: Images.TemplateMatch) => number) | Images.MatchingResultSortStrategy): Images.MatchingResult;
    }

    class ImageFeatures {
        public recycled: boolean;
        public javaObject: org.autojs.autojs.runtime.api.ImageFeatureMatching.FeatureMatchingDescriptor;
        public scale: number;
        public region: org.opencv.core.Rect;
        public onRecycled: (features: Images.ImageFeatures) => any;
        public isRecycled(): boolean;
        public recycle(): void;
        public setOneShot(b: boolean): Images.ImageFeatures;
        public shoot(): void;
    }

    /**
     * 特征匹配返回的结果, 表示一个四边形.
     */
    class ObjectFrame {
        constructor(topLeft: org.opencv.core.Point, topRight: org.opencv.core.Point, bottomLeft: org.opencv.core.Point, bottomRight: org.opencv.core.Point);
        public topLeft: org.opencv.core.Point;
        public topRight: org.opencv.core.Point;
        public bottomLeft: org.opencv.core.Point;
        public bottomRight: org.opencv.core.Point;
        public centerX: number;
        public centerY: number;
        public center: org.opencv.core.Point;
        public summary(): string;
    }

    interface BitmapFactoryOptions {
        inBitmap?: android.graphics.Bitmap,
        inDensity?: number,
        inDither?: boolean,
        inInputShareable?: boolean,
        inJustDecodeBounds?: boolean,
        inMutable?: boolean,
        inPreferQualityOverSpeed?: boolean,
        inPreferredColorSpace?: android.graphics.ColorSpace,
        inPreferredConfig?: android.graphics.Bitmap.Config,
        inPremultiplied?: boolean,
        inPurgeable?: boolean,
        inSampleSize?: number,
        inScaled?: boolean,
        inScreenDensity?: number,
        inTargetDensity?: number,
        inTempStorage?: number[],
        mCancel?: boolean,
        outColorSpace?: android.graphics.ColorSpace,
        outConfig?: android.graphics.Bitmap.Config,
        outHeight?: number,
        outMimeType?: string,
        outWidth?: number,
        requestCancelDecode?: () => void,
    }

    namespace Circles {

        type Result = { x: number, y: number, radius: number }[];

        type Options = {
            region?: OmniRegion;
            dp?: number;
            minDst?: number;
            param1?: number;
            param2?: number;
            minRadius?: number;
            maxRadius?: number;
        };

    }

}

declare namespace OpenCV {

    type Rect = org.opencv.core.Rect;

    type Point = org.opencv.core.Point;

    type Points = org.opencv.core.Point[];

}

/**
 * @see images.requestScreenCapture
 */
declare function requestScreenCapture(landscape?: boolean): boolean;
declare function requestScreenCapture(options: Images.ScreenCaptureOptions): boolean;
declare function requestScreenCapture(width: number, height: number): boolean;

/**
 * @see images.requestScreenCaptureAsync
 */
declare function requestScreenCaptureAsync(landscape?: boolean): Promise<boolean>;
declare function requestScreenCaptureAsync(options: Images.ScreenCaptureOptions): Promise<boolean>;
declare function requestScreenCaptureAsync(width: number, height: number): Promise<boolean>;

/**
 * @see images.captureScreen
 */
declare function captureScreen(): ImageWrapper;
declare function captureScreen(path: string): boolean;

/**
 * @param img
 * @param template
 * @param [options={}]
 * @param [options.threshold=0.9]
 * @param [options.weakThreshold=0.6]
 * @param [options.level=-1]
 * @param [options.region]
 * @see images.findImage
 */
declare function findImage(img: Images.ImageSource, template: Images.ImageSource, options?: {
    threshold?: number;
    weakThreshold?: number;
    level?: number;
    region?: OmniRegion;
}): OpenCV.Point | null;

/**
 * @param image
 * @param template
 * @param [x=0]
 * @param [y=0]
 * @param [width=img.getWidth()-x]
 * @param [height=img.getHeight()-y]
 * @param [threshold=4]
 * @see images.findImageInRegion
 */
declare function findImageInRegion(image: Images.ImageSource, template: Images.ImageSource, x?: X, y?: Y, width?: Width, height?: Height, threshold?: number): OpenCV.Point | null;

/**
 * @see images.findColor
 */
declare function findColor(img: Images.ImageSource, color: Color$, options?: {
    similarity?: number;
    threshold?: number;
    region?: OmniRegion;
}): OpenCV.Point | null;

/**
 * @param img
 * @param color
 * @param [x=0]
 * @param [y=0]
 * @param [width=img.getWidth()-x]
 * @param [height=img.getHeight()-y]
 * @see images.findColorEquals
 */
declare function findColorEquals(img: Images.ImageSource, color: Color$, x?: X, y?: Y, width?: Width, height?: Height): OpenCV.Point | null;

/**
 * @param img
 * @param color
 * @param [x=0]
 * @param [y=0]
 * @param [width=img.getWidth()-x]
 * @param [height=img.getHeight()-y]
 * @param [threshold=4]
 * @see images.findColorInRegion
 */
declare function findColorInRegion(img: Images.ImageSource, color: Color$, x?: X, y?: Y, width?: Width, height?: Height, threshold?: number): OpenCV.Point | null;

/**
 * @see images.findMultiColors
 */
declare function findMultiColors(img: Images.ImageSource, firstColor: Color$, paths: [X, Y, Color$][], options?: {
    region?: OmniRegion;
    threshold?: number;
}): OpenCV.Point | null;

declare class ImageWrapper extends org.autojs.autojs.core.image.ImageWrapper {

    /**
     * @see ImageWrapper.getMat
     */
    public mat: org.autojs.autojs.core.opencv.Mat;
    /**
     * @see ImageWrapper.getBitmap
     */
    public bitmap: android.graphics.Bitmap;
    /**
     * @see ImageWrapper.getWidth
     */
    public width: number;
    /**
     * @see ImageWrapper.getHeight
     */
    public height: number;

    public oneShot(): ImageWrapper;

    public setOneShot(b: boolean): ImageWrapper;

    /**
     * @example Source code summary (zh-CN: 源代码摘要)
     * private int mWidth;
     * private int mHeight;
     * private Bitmap mBitmap;
     * protected ImageWrapper(Bitmap bitmap) {
     *     mBitmap = bitmap;
     *     mWidth = bitmap.getWidth();
     *     mHeight = bitmap.getHeight();
     * }
     * public static ImageWrapper ofBitmap(Bitmap bitmap) {
     *     if (bitmap == null) {
     *         return null;
     *     }
     *     return new ImageWrapper(bitmap);
     * }
     * @see android.graphics.Bitmap
     */
    public static ofBitmap(bitmap: android.graphics.Bitmap): ImageWrapper;
    public static ofImage(image: android.media.Image): ImageWrapper;
    public static ofMat(mat: org.autojs.autojs.core.opencv.Mat): ImageWrapper;
    public static ofMat(mat: org.opencv.core.Mat): ImageWrapper;

}
