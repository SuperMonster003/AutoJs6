package org.autojs.autojs.core.image;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.autojs.autojs.util.MathUtils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pyramid template matching.
 * <p>
 * Every match is refined down to the full-resolution image, results are free of overlapping
 * duplicates, sorted by similarity and trimmed to the requested limit. Operands with different
 * channel counts are converted to a common layout, a solid-color template falls back to a
 * squared-difference similarity, and the transparent area of a 4-channel template can be masked out.
 * <p>
 * zh-CN: 图像金字塔模板匹配. 所有匹配结果均在原始分辨率上精确定位, 去除重叠的重复项, 按相似度降序排列并按数量上限截断;
 * 通道数不同的图片与模板自动转换为相同布局, 纯色模板自动退化为平方差相似度, 4 通道模板的透明区域可作为遮罩排除.
 * <p>
 * Created by Stardust on Nov 25, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
public class TemplateMatching {

    public static final int MAX_LEVEL_AUTO = -1;
    public static final int MATCHING_METHOD_DEFAULT = Imgproc.TM_CCOEFF_NORMED;
    /**
     * OpenCV 4.5.2 and later accept a mask for every matching method, so masked matching keeps the
     * normalized correlation coefficient instead of the weaker TM_CCORR_NORMED used before.
     * zh-CN: OpenCV 4.5.2 起所有匹配方法均支持遮罩, 因此遮罩匹配沿用归一化相关系数, 不再退化为 TM_CCORR_NORMED.
     */
    public static final int MATCHING_METHOD_DEFAULT_WITH_TRANSPARENT_MASK = Imgproc.TM_CCOEFF_NORMED;
    public static final int MATCHING_METHOD_NONE = -1;
    public static final double[] DEFAULT_SCALES = {1.0};

    /** Search window factor (relative to the template size) around a match found at the coarser level. */
    private static final double ROI_FACTOR = 1.5;
    /** Smallest template side allowed at the top pyramid level when the level is chosen by the caller. */
    private static final int MIN_TEMPLATE_SIDE_AT_TOP = 2;
    /** Smallest candidate pool at the top level; small limits still get a few candidates to refine. */
    private static final int MIN_CANDIDATE_POOL = 4;
    /** A template whose channels all vary by less than one gray level is treated as solid-colored. */
    private static final double FLAT_STDDEV_THRESHOLD = 1.0;
    /** Correlation scores above this value can only come from numeric blow-ups (e.g. +Infinity). */
    private static final double MAX_VALID_CORRELATION = 1.001;

    public static class Match {
        /** Top-left corner of the matched area in image coordinates. zh-CN: 匹配区域左上角坐标. */
        public final Point point;
        /** Similarity in the range of the matching method (1 is a perfect match for the defaults). zh-CN: 相似度. */
        public final double similarity;
        /** Width of the matched area (template width at the matched scale). zh-CN: 匹配区域宽度. */
        public final int width;
        /** Height of the matched area (template height at the matched scale). zh-CN: 匹配区域高度. */
        public final int height;
        /** Template scale factor that produced this match. zh-CN: 产生该匹配的模板缩放比例. */
        public final double scale;

        public Match(Point point, double similarity) {
            this(point, similarity, 0, 0, 1.0);
        }

        public Match(Point point, double similarity, int width, int height, double scale) {
            this.point = point;
            this.similarity = similarity;
            this.width = width;
            this.height = height;
            this.scale = scale;
        }

        /** Center of the matched area. zh-CN: 匹配区域的中心点. */
        public Point getCenter() {
            return new Point(point.x + width / 2.0, point.y + height / 2.0);
        }

        /** Matched area as a rectangle. zh-CN: 匹配区域对应的矩形. */
        public Rect getRect() {
            return new Rect((int) Math.round(point.x), (int) Math.round(point.y), width, height);
        }

        boolean intersects(Match other) {
            return point.x < other.point.x + other.width && other.point.x < point.x + width
                    && point.y < other.point.y + other.height && other.point.y < point.y + height;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Match{point=%s, similarity=%s, width=%d, height=%d, scale=%s}", point, similarity, width, height, scale);
        }
    }

    public static class Options {
        private final int mLimit;
        private final int mMatchingMethod;
        private final int mMaxLevel;
        private final float mStrictThreshold;
        private final boolean mUseTransparentMask;
        private final float mWeakThreshold;
        private final double[] mScales;

        public Options(int matchingMethod, float weakThreshold, float strictThreshold, int maxLevel) {
            this(matchingMethod, weakThreshold, strictThreshold, maxLevel, false, 1);
        }

        public Options(int matchingMethod, float weakThreshold, float strictThreshold, int maxLevel, boolean useTransparentMask, int limit) {
            this(matchingMethod, weakThreshold, strictThreshold, maxLevel, useTransparentMask, limit, null);
        }

        /**
         * @param matchingMethod     匹配算法, MATCHING_METHOD_NONE 表示自动选择
         * @param weakThreshold      弱阈值. 每一层金字塔中相似度低于该值的候选点不再继续精化.
         * @param strictThreshold    强阈值. 在原始分辨率上相似度不低于该值的候选点才作为最终结果.
         * @param maxLevel           图像金字塔的层数, MAX_LEVEL_AUTO 表示自动选择
         * @param useTransparentMask 是否将模板的透明区域作为匹配掩码 (模板需为 4 通道)
         * @param limit              最多返回的匹配结果数量
         * @param scales             依次尝试的模板缩放比例, null 或空数组表示仅使用原始尺寸
         */
        public Options(int matchingMethod, float weakThreshold, float strictThreshold, int maxLevel, boolean useTransparentMask, int limit, @Nullable double[] scales) {
            mMatchingMethod = matchingMethod;
            mWeakThreshold = weakThreshold;
            mStrictThreshold = strictThreshold;
            mMaxLevel = maxLevel;
            mUseTransparentMask = useTransparentMask;
            mLimit = limit;
            mScales = normalizeScales(scales);
        }

        /**
         * Validates the scale list: every factor must be a positive finite number; null or empty means the original size.
         * zh-CN: 校验缩放比例列表, 每个比例须为正有限数; null 或空数组表示仅使用原始尺寸.
         */
        static double[] normalizeScales(@Nullable double[] scales) {
            if (scales == null || scales.length == 0) {
                return DEFAULT_SCALES.clone();
            }
            double[] result = scales.clone();
            for (double scale : result) {
                if (!(scale > 0) || Double.isInfinite(scale)) {
                    throw new IllegalArgumentException("Template scale must be a positive finite number, got " + scale);
                }
            }
            return result;
        }

        public int getLimit() {
            return mLimit;
        }

        public int getMatchingMethod() {
            int matchingMethod = mMatchingMethod;
            if (matchingMethod == MATCHING_METHOD_NONE) {
                matchingMethod = mUseTransparentMask ? MATCHING_METHOD_DEFAULT_WITH_TRANSPARENT_MASK : MATCHING_METHOD_DEFAULT;
            }
            return matchingMethod;
        }

        boolean isMatchingMethodAutomatic() {
            return mMatchingMethod == MATCHING_METHOD_NONE;
        }

        public int getMaxLevel() {
            return mMaxLevel;
        }

        public float getStrictThreshold() {
            return mStrictThreshold;
        }

        public boolean getUseTransparentMask() {
            return mUseTransparentMask;
        }

        public float getWeakThreshold() {
            return mWeakThreshold;
        }

        public double[] getScales() {
            return mScales.clone();
        }
    }

    /**
     * Image, template and optional mask brought to a common channel layout.
     * zh-CN: 转换为相同通道布局的图片, 模板与可选遮罩.
     */
    private static final class Operands {
        final Mat image;
        final Mat template;
        @Nullable
        final Mat mask;
        private final Mat mImageSource;
        private final Mat mTemplateSource;

        private Operands(Mat image, Mat template, @Nullable Mat mask, Mat imageSource, Mat templateSource) {
            this.image = image;
            this.template = template;
            this.mask = mask;
            mImageSource = imageSource;
            mTemplateSource = templateSource;
        }

        static Operands prepare(Mat img, Mat template, boolean useTransparentMask) {
            Mat mask = useTransparentMask ? createTransparentMask(template) : null;
            Mat image = img;
            Mat tpl = template;
            if (img.channels() != template.channels()) {
                int targetChannels = img.channels() == 1 || template.channels() == 1 ? 1 : 3;
                image = convertChannels(img, targetChannels);
                tpl = convertChannels(template, targetChannels);
            }
            return new Operands(image, tpl, mask, img, template);
        }

        void release() {
            releaseIfNotSame(image, mImageSource);
            releaseIfNotSame(template, mTemplateSource);
            OpenCVHelper.release(mask);
        }
    }

    /**
     * Converts a 1-, 3- or 4-channel (RGB order) matrix to the requested channel count; returns the input when nothing changes.
     * zh-CN: 将 1, 3 或 4 通道 (RGB 顺序) 矩阵转换为目标通道数; 无需转换时返回原矩阵.
     */
    static Mat convertChannels(Mat src, int targetChannels) {
        int channels = src.channels();
        if (channels == targetChannels) {
            return src;
        }
        int code;
        if (targetChannels == 1) {
            code = channels == 4 ? Imgproc.COLOR_RGBA2GRAY : channels == 3 ? Imgproc.COLOR_RGB2GRAY : -1;
        } else if (targetChannels == 3) {
            code = channels == 4 ? Imgproc.COLOR_RGBA2RGB : channels == 1 ? Imgproc.COLOR_GRAY2RGB : -1;
        } else {
            code = -1;
        }
        if (code < 0) {
            throw new IllegalArgumentException("Cannot convert a " + channels + "-channel matrix to " + targetChannels + " channels for template matching");
        }
        Mat result = new Mat();
        Imgproc.cvtColor(src, result, code);
        return result;
    }

    /**
     * Builds a binary (0 / 255) single-channel mask from the alpha channel of a 4-channel template.
     * Returns null for templates without an alpha channel, in which case no mask is applied.
     * zh-CN: 由 4 通道模板的 alpha 通道生成二值 (0 / 255) 单通道遮罩; 模板无 alpha 通道时返回 null (不使用遮罩).
     */
    @Nullable
    static Mat createTransparentMask(Mat template) {
        if (template.channels() != 4) {
            return null;
        }
        Mat alpha = new Mat();
        try {
            Core.extractChannel(template, alpha, 3);
            Mat mask = new Mat();
            Imgproc.threshold(alpha, mask, 127.0, 255.0, Imgproc.THRESH_BINARY);
            return mask;
        } finally {
            OpenCVHelper.release(alpha);
        }
    }

    /**
     * 采用图像金字塔算法快速找图.
     * <p>
     * Results are sorted by similarity (best first), contain no overlapping duplicates and hold at most {@code limit} items.
     * zh-CN: 结果按相似度降序排列, 不含相互重叠的重复项, 数量不超过 limit.
     *
     * @param img      图片
     * @param template 模板图片
     * @param options  选项
     */
    /**
     * The Auto.js 4 signatures of {@code com.stardust.autojs.core.image.TemplateMatching.fastTemplateMatching}; that
     * class name resolves to this class through {@link org.autojs.autojs.rhino.StardustClassAliases} (roadmap P7.3).
     * The six-argument form returns the best match point (or null), the seven-argument form up to {@code limit} matches.
     * zh-CN: Auto.js 4 {@code com.stardust.autojs.core.image.TemplateMatching.fastTemplateMatching} 的签名; 该类名经
     * {@link org.autojs.autojs.rhino.StardustClassAliases} 解析到本类 (roadmap P7.3). 六参数形式返回最佳匹配点 (或 null),
     * 七参数形式最多返回 {@code limit} 个匹配.
     */
    @Nullable
    public static Point fastTemplateMatching(Mat img, Mat template, int matchMethod, float weakThreshold, float strictThreshold, int maxLevel) {
        List<Match> result = fastTemplateMatching(img, template, matchMethod, weakThreshold, strictThreshold, maxLevel, 1);
        return result.isEmpty() ? null : result.get(0).point;
    }

    public static List<Match> fastTemplateMatching(Mat img, Mat template, int matchMethod, float weakThreshold, float strictThreshold, int maxLevel, int limit) {
        return fastTemplateMatching(img, template, new Options(matchMethod, weakThreshold, strictThreshold, maxLevel, false, limit));
    }

    public static List<Match> fastTemplateMatching(Mat img, Mat template, Options options) {
        int limit = options.getLimit();
        if (img == null || template == null || img.empty() || template.empty() || limit <= 0) {
            return new ArrayList<>();
        }
        Operands operands = Operands.prepare(img, template, options.getUseTransparentMask());
        try {
            List<Match> all = new ArrayList<>();
            for (double scale : options.getScales()) {
                Mat scaledTemplate = null;
                Mat scaledMask = null;
                try {
                    scaledTemplate = scale == 1.0 ? operands.template : resizeTemplate(operands.template, scale);
                    if (scaledTemplate.empty() || scaledTemplate.cols() > operands.image.cols() || scaledTemplate.rows() > operands.image.rows()) {
                        // This scale can never fit into the image.
                        // zh-CN: 该比例下的模板无法放入图片.
                        continue;
                    }
                    scaledMask = operands.mask == null ? null : scale == 1.0 ? operands.mask : resizeMask(operands.mask, scaledTemplate.size());
                    all.addAll(matchAtScale(operands.image, scaledTemplate, scaledMask, options, scale));
                } finally {
                    releaseIfNotSame(scaledTemplate, operands.template);
                    releaseIfNotSame(scaledMask, operands.mask);
                }
            }
            List<Match> result = suppressOverlapping(all);
            return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
        } finally {
            operands.release();
        }
    }

    /**
     * Runs the pyramid search for one template scale; the returned matches use full-resolution coordinates.
     * zh-CN: 对单个模板比例执行金字塔搜索, 返回的匹配使用原始分辨率坐标.
     */
    private static List<Match> matchAtScale(Mat image, Mat template, @Nullable Mat mask, Options options, double scale) {
        int method = resolveMatchingMethod(options, template, mask);
        float weakThreshold = options.getWeakThreshold();
        float strictThreshold = options.getStrictThreshold();
        int limit = options.getLimit();
        int maxLevel = options.getMaxLevel() == MAX_LEVEL_AUTO
                ? selectPyramidLevel(image.rows(), image.cols(), template.rows(), template.cols())
                : clampLevel(options.getMaxLevel(), image.rows(), image.cols(), template.rows(), template.cols());
        int templateWidth = template.cols();
        int templateHeight = template.rows();

        // Candidates carry full-resolution coordinates so that they survive across levels.
        // zh-CN: 候选点使用原始分辨率坐标, 以便跨层传递.
        List<Match> candidates = null;
        for (int level = maxLevel; level >= 0; level--) {
            Mat src = null;
            Mat currentTemplate = null;
            Mat currentMask = null;
            try {
                src = getPyramidDownAtLevel(image, level);
                currentTemplate = getPyramidDownAtLevel(template, level);
                currentMask = mask == null ? null : getPyramidDownMask(mask, level);
                double area = maskedArea(currentTemplate, currentMask);
                List<Match> current;
                if (candidates == null) {
                    int pool = Math.max(limit, MIN_CANDIDATE_POOL);
                    current = searchFull(src, currentTemplate, currentMask, method, weakThreshold, pool, level, area, templateWidth, templateHeight, scale);
                } else {
                    current = new ArrayList<>(candidates.size());
                    for (Match candidate : candidates) {
                        Rect roi = getROI(candidate.point.x / (1 << level), candidate.point.y / (1 << level), src.cols(), src.rows(), currentTemplate.cols(), currentTemplate.rows());
                        if (roi == null) {
                            continue;
                        }
                        Match refined = searchBest(src, roi, currentTemplate, currentMask, method, weakThreshold, level, area, templateWidth, templateHeight, scale);
                        if (refined != null) {
                            current.add(refined);
                        }
                    }
                    // Windows of neighbouring candidates overlap, so the same location can be found twice.
                    // zh-CN: 相邻候选点的搜索窗口相互重叠, 同一位置可能被找到两次.
                    current = suppressOverlapping(current);
                }
                if (current.isEmpty()) {
                    if (candidates == null && level == maxLevel && maxLevel > 2) {
                        // Nothing at the coarsest level: try once more with a full search one level finer.
                        // zh-CN: 最粗层级无结果时, 在下一层再做一次全图搜索.
                        continue;
                    }
                    return new ArrayList<>();
                }
                candidates = current;
            } finally {
                releaseIfNotSame(src, image);
                releaseIfNotSame(currentTemplate, template);
                releaseIfNotSame(currentMask, mask);
            }
        }
        List<Match> result = new ArrayList<>();
        if (candidates != null) {
            for (Match candidate : candidates) {
                if (candidate.similarity >= strictThreshold) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    /**
     * Full search of one pyramid level: repeatedly takes the best peak and blanks its neighbourhood.
     * zh-CN: 单层全图搜索: 反复取最佳峰值并屏蔽其邻域.
     */
    private static List<Match> searchFull(Mat src, Mat template, @Nullable Mat mask, int method, float weakThreshold, int pool, int level, double area, int fullWidth, int fullHeight, double scale) {
        List<Match> result = new ArrayList<>();
        Mat scores = matchTemplate(src, template, method, mask);
        try {
            if (scores.empty()) {
                return result;
            }
            sanitizeScores(scores, method);
            for (int i = 0; i < pool; i++) {
                Peak peak = bestPeak(scores, method, area);
                if (peak == null || peak.similarity < weakThreshold) {
                    break;
                }
                result.add(toFullResolution(peak, level, fullWidth, fullHeight, scale));
                excludePeak(scores, peak, template.cols(), template.rows(), method);
            }
        } finally {
            OpenCVHelper.release(scores);
        }
        return result;
    }

    /**
     * Searches the best peak inside a window of one pyramid level.
     * zh-CN: 在单层的搜索窗口内寻找最佳峰值.
     */
    @Nullable
    private static Match searchBest(Mat src, Rect roi, Mat template, @Nullable Mat mask, int method, float weakThreshold, int level, double area, int fullWidth, int fullHeight, double scale) {
        Mat window = new Mat(src, roi);
        Mat scores = null;
        try {
            scores = matchTemplate(window, template, method, mask);
            if (scores.empty()) {
                return null;
            }
            sanitizeScores(scores, method);
            Peak peak = bestPeak(scores, method, area);
            if (peak == null || peak.similarity < weakThreshold) {
                return null;
            }
            return toFullResolution(new Peak(peak.x + roi.x, peak.y + roi.y, peak.similarity), level, fullWidth, fullHeight, scale);
        } finally {
            OpenCVHelper.release(window);
            OpenCVHelper.release(scores);
        }
    }

    private static Match toFullResolution(Peak peak, int level, int fullWidth, int fullHeight, double scale) {
        double factor = 1 << level;
        return new Match(new Point(peak.x * factor, peak.y * factor), peak.similarity, fullWidth, fullHeight, scale);
    }

    private static final class Peak {
        final int x;
        final int y;
        final double similarity;

        Peak(int x, int y, double similarity) {
            this.x = x;
            this.y = y;
            this.similarity = similarity;
        }
    }

    /**
     * Picks the matching method: an explicit method wins, a solid-color template uses the squared difference
     * (the normalized correlation coefficient of a constant template is undefined), otherwise the default.
     * zh-CN: 选择匹配方法: 显式指定的方法优先; 纯色模板使用平方差 (常量模板的归一化相关系数无定义); 否则使用默认方法.
     */
    private static int resolveMatchingMethod(Options options, Mat template, @Nullable Mat mask) {
        if (!options.isMatchingMethodAutomatic()) {
            return options.getMatchingMethod();
        }
        return isFlat(template, mask) ? Imgproc.TM_SQDIFF : options.getMatchingMethod();
    }

    /**
     * Whether every color channel of the (masked) template varies by less than one gray level.
     * zh-CN: (遮罩范围内的) 模板各颜色通道的变化是否都小于一个灰度级.
     */
    static boolean isFlat(Mat template, @Nullable Mat mask) {
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        try {
            if (mask != null) {
                Core.meanStdDev(template, mean, stddev, mask);
            } else {
                Core.meanStdDev(template, mean, stddev);
            }
            double[] values = stddev.toArray();
            int colorChannels = Math.min(3, values.length);
            for (int i = 0; i < colorChannels; i++) {
                if (values[i] >= FLAT_STDDEV_THRESHOLD) {
                    return false;
                }
            }
            return true;
        } finally {
            OpenCVHelper.release(mean);
            OpenCVHelper.release(stddev);
        }
    }

    /** Number of compared sample values: (masked) template pixels times channels. zh-CN: 参与比较的样本数. */
    private static double maskedArea(Mat template, @Nullable Mat mask) {
        double pixels = mask != null ? Core.countNonZero(mask) : template.total();
        return Math.max(1.0, pixels) * template.channels();
    }

    private static void releaseIfNotSame(@Nullable Mat mat, @Nullable Mat ref) {
        if (mat != null && mat != ref) {
            OpenCVHelper.release(mat);
        }
    }

    /** Side length after {@code level} halvings (same rounding as the pyramid). zh-CN: 缩小 level 次后的边长. */
    static int sideAtLevel(int side, int level) {
        for (int i = 0; i < level; i++) {
            side = (side + 1) / 2;
        }
        return side;
    }

    private static Mat getPyramidDownAtLevel(Mat m, int level) {
        if (level == 0) {
            return m;
        }
        int cols = sideAtLevel(m.cols(), level);
        int rows = sideAtLevel(m.rows(), level);
        Mat r = new Mat();
        // Area interpolation averages every source pixel, which keeps texture instead of aliasing it away.
        // zh-CN: 区域插值对全部源像素取平均, 保留纹理而不产生混叠.
        Imgproc.resize(m, r, new Size(cols, rows), 0, 0, Imgproc.INTER_AREA);
        return r;
    }

    private static Mat getPyramidDownMask(Mat mask, int level) {
        if (level == 0) {
            return mask;
        }
        Mat resized = getPyramidDownAtLevel(mask, level);
        try {
            Mat binary = new Mat();
            Imgproc.threshold(resized, binary, 127.0, 255.0, Imgproc.THRESH_BINARY);
            return binary;
        } finally {
            OpenCVHelper.release(resized);
        }
    }

    private static Mat resizeTemplate(Mat template, double scale) {
        int cols = Math.max(1, (int) Math.round(template.cols() * scale));
        int rows = Math.max(1, (int) Math.round(template.rows() * scale));
        Mat result = new Mat();
        Imgproc.resize(template, result, new Size(cols, rows), 0, 0, scale < 1 ? Imgproc.INTER_AREA : Imgproc.INTER_LINEAR);
        return result;
    }

    private static Mat resizeMask(Mat mask, Size size) {
        Mat resized = new Mat();
        try {
            Imgproc.resize(mask, resized, size, 0, 0, Imgproc.INTER_AREA);
            Mat binary = new Mat();
            Imgproc.threshold(resized, binary, 127.0, 255.0, Imgproc.THRESH_BINARY);
            return binary;
        } finally {
            OpenCVHelper.release(resized);
        }
    }

    /**
     * Computes the search window around a match found at the previous pyramid level.
     * The window is 1.5x the template size, slid inside the image so it always contains a full template.
     * Returns null when the template does not fit into the image at this level.
     * zh-CN: 根据上一层的匹配点计算本层搜索窗口 (模板 1.5 倍大小, 并在图像内平移以保证能容纳完整模板);
     * 本层模板无法放入图像时返回 null.
     *
     * @param px template top-left x at this level
     * @param py template top-left y at this level
     */
    @Nullable
    static Rect getROI(double px, double py, int cols, int rows, int templateWidth, int templateHeight) {
        if (templateWidth > cols || templateHeight > rows) {
            return null;
        }
        int w = Math.min((int) (templateWidth * ROI_FACTOR), cols);
        int h = Math.min((int) (templateHeight * ROI_FACTOR), rows);
        int x = Math.max(0, Math.min((int) Math.round(px - templateWidth / 4.0), cols - w));
        int y = Math.max(0, Math.min((int) Math.round(py - templateHeight / 4.0), rows - h));
        return new Rect(x, y, w, h);
    }

    /**
     * Picks the pyramid depth so that the smaller side of image and template stays at least 16 pixels, capped at 6.
     * zh-CN: 选取金字塔层数, 使图像与模板的较短边不小于 16 像素, 上限为 6.
     */
    static int selectPyramidLevel(int imageRows, int imageCols, int templateRows, int templateCols) {
        int minDim = MathUtils.min(imageRows, imageCols, templateRows, templateCols);
        int maxLevel = 0;
        while (maxLevel < 6 && (minDim >> (maxLevel + 1)) >= 16) {
            maxLevel++;
        }
        return maxLevel;
    }

    /**
     * Limits a caller-supplied level so that the template keeps at least two pixels per side and fits into the image.
     * zh-CN: 限制调用方指定的层数, 保证模板每边至少两像素且能放入图像.
     */
    static int clampLevel(int level, int imageRows, int imageCols, int templateRows, int templateCols) {
        if (level < 0) {
            return selectPyramidLevel(imageRows, imageCols, templateRows, templateCols);
        }
        while (level > 0) {
            int tw = sideAtLevel(templateCols, level);
            int th = sideAtLevel(templateRows, level);
            if (tw >= MIN_TEMPLATE_SIDE_AT_TOP && th >= MIN_TEMPLATE_SIDE_AT_TOP && tw <= sideAtLevel(imageCols, level) && th <= sideAtLevel(imageRows, level)) {
                break;
            }
            level--;
        }
        return level;
    }

    public static Mat matchTemplate(Mat img, Mat template, int matchingMethod) {
        return matchTemplate(img, template, matchingMethod, null);
    }

    /**
     * Runs OpenCV template matching. Returns an empty Mat when the template does not fit into the image.
     * zh-CN: 执行 OpenCV 模板匹配; 模板无法放入图像时返回空 Mat.
     */
    public static Mat matchTemplate(Mat img, Mat template, int matchingMethod, @Nullable Mat transparentMask) {
        int resultRows = img.rows() - template.rows() + 1;
        int resultCols = img.cols() - template.cols() + 1;
        if (resultRows <= 0 || resultCols <= 0) {
            return new Mat();
        }
        Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
        if (transparentMask == null) {
            Imgproc.matchTemplate(img, template, result, matchingMethod);
        } else {
            Imgproc.matchTemplate(img, template, result, matchingMethod, transparentMask);
        }
        return result;
    }

    static boolean isSquaredDifferenceMethod(int matchingMethod) {
        return matchingMethod == Imgproc.TM_SQDIFF || matchingMethod == Imgproc.TM_SQDIFF_NORMED;
    }

    private static boolean isNormalizedCorrelationMethod(int matchingMethod) {
        return matchingMethod == Imgproc.TM_CCOEFF_NORMED || matchingMethod == Imgproc.TM_CCORR_NORMED;
    }

    /** Worst possible score of the method, used to blank excluded areas. zh-CN: 该方法的最差得分, 用于屏蔽区域. */
    private static double worstScore(int matchingMethod) {
        return isSquaredDifferenceMethod(matchingMethod) ? Float.MAX_VALUE : -Float.MAX_VALUE;
    }

    /**
     * Removes NaN and blown-up scores that a degenerate window (zero variance) can produce.
     * zh-CN: 去除退化窗口 (方差为零) 可能产生的 NaN 与溢出得分.
     */
    private static void sanitizeScores(Mat scores, int matchingMethod) {
        Core.patchNaNs(scores, worstScore(matchingMethod));
        if (isNormalizedCorrelationMethod(matchingMethod)) {
            Imgproc.threshold(scores, scores, MAX_VALID_CORRELATION, 0, Imgproc.THRESH_TOZERO_INV);
        }
    }

    /**
     * Maps a raw OpenCV score onto a similarity where 1 is a perfect match:
     * TM_SQDIFF_NORMED is inverted, TM_SQDIFF becomes 1 minus the normalized root-mean-square difference,
     * every other method is returned as is.
     * zh-CN: 将 OpenCV 原始得分映射为相似度 (1 为完全匹配): TM_SQDIFF_NORMED 取反, TM_SQDIFF 取 1 减去归一化均方根差, 其余方法原样返回.
     *
     * @param area number of compared sample values (pixels times channels)
     */
    static double toSimilarity(int matchingMethod, double value, double area) {
        if (matchingMethod == Imgproc.TM_SQDIFF_NORMED) {
            return 1 - value;
        }
        if (matchingMethod == Imgproc.TM_SQDIFF) {
            return 1 - Math.sqrt(Math.max(0, value) / Math.max(1.0, area)) / 255.0;
        }
        return value;
    }

    @Nullable
    private static Peak bestPeak(Mat scores, int matchingMethod, double area) {
        Core.MinMaxLocResult minMaxLoc = Core.minMaxLoc(scores);
        Point pos = isSquaredDifferenceMethod(matchingMethod) ? minMaxLoc.minLoc : minMaxLoc.maxLoc;
        double raw = isSquaredDifferenceMethod(matchingMethod) ? minMaxLoc.minVal : minMaxLoc.maxVal;
        if (pos == null || Double.isNaN(raw)) {
            return null;
        }
        return new Peak((int) pos.x, (int) pos.y, toSimilarity(matchingMethod, raw, area));
    }

    /**
     * Blanks every score whose template area would overlap the given peak, so that it cannot be picked again.
     * zh-CN: 屏蔽所有与该峰值的模板区域重叠的得分, 使其不会被再次选中.
     */
    private static void excludePeak(Mat scores, Peak peak, int templateWidth, int templateHeight, int matchingMethod) {
        int x0 = Math.max(0, peak.x - templateWidth + 1);
        int y0 = Math.max(0, peak.y - templateHeight + 1);
        int x1 = Math.min(scores.cols(), peak.x + templateWidth);
        int y1 = Math.min(scores.rows(), peak.y + templateHeight);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        Mat area = scores.submat(y0, y1, x0, x1);
        try {
            area.setTo(new Scalar(worstScore(matchingMethod)));
        } finally {
            OpenCVHelper.release(area);
        }
    }

    /**
     * Non-maximum suppression: keeps the better of any two matches whose areas overlap.
     * The result is sorted by similarity, best first.
     * zh-CN: 非极大值抑制: 两个区域重叠的匹配只保留相似度较高者, 结果按相似度降序排列.
     */
    static List<Match> suppressOverlapping(List<Match> matches) {
        List<Match> sorted = new ArrayList<>(matches);
        Collections.sort(sorted, (a, b) -> Double.compare(b.similarity, a.similarity));
        List<Match> kept = new ArrayList<>(sorted.size());
        for (Match match : sorted) {
            boolean overlapped = false;
            for (Match other : kept) {
                if (match.intersects(other)) {
                    overlapped = true;
                    break;
                }
            }
            if (!overlapped) {
                kept.add(match);
            }
        }
        return kept;
    }

    @Nullable
    public static Point singleTemplateMatching(Mat mat, Mat mat2, Options options) {
        List<Match> matches = fastTemplateMatching(mat, mat2, options);
        return matches.isEmpty() ? null : matches.get(0).point;
    }

}
