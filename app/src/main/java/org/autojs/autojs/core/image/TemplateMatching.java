package org.autojs.autojs.core.image;

import androidx.annotation.Nullable;
import org.autojs.autojs.core.opencv.Mat;
import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.autojs.autojs.util.MathUtils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Stardust on Nov 25, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
public class TemplateMatching {

    public static final int MAX_LEVEL_AUTO = -1;
    public static final int MATCHING_METHOD_DEFAULT = Imgproc.TM_CCOEFF_NORMED;
    public static final int MATCHING_METHOD_DEFAULT_WITH_TRANSPARENT_MASK = Imgproc.TM_CCORR_NORMED;
    public static final int MATCHING_METHOD_NONE = -1;

    public static class Match {
        public final Point point;
        public final double similarity;

        public Match(Point point, double similarity) {
            this.point = point;
            this.similarity = similarity;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return String.format("Match{point=%s, similarity=%s}", point, similarity);
        }
    }

    public static class Options {
        private final int mLimit;
        private final int mMatchingMethod;
        private final int mMaxLevel;
        private final float mStrictThreshold;
        private final boolean mUseTransparentMask;
        private final float mWeakThreshold;

        public Options(int matchingMethod, float weakThreshold, float strictThreshold, int maxLevel) {
            this(matchingMethod, weakThreshold, strictThreshold, maxLevel, false, 1);
        }

        /**
         * @param matchingMethod     匹配算法
         * @param weakThreshold      弱阈值. 该值用于在每一轮模板匹配中检验是否继续匹配. 如果相似度小于该值, 则不再继续匹配.
         * @param strictThreshold    强阈值. 该值用于检验最终匹配结果, 以及在每一轮匹配中如果相似度大于该值则直接返回匹配结果.
         * @param maxLevel           图像金字塔的层数
         * @param useTransparentMask 是否将模板的透明区域作为匹配掩码 (模板需为 4 通道)
         * @param limit              最多返回的匹配结果数量
         */
        public Options(int matchingMethod, float weakThreshold, float strictThreshold, int maxLevel, boolean useTransparentMask, int limit) {
            mMatchingMethod = matchingMethod;
            mWeakThreshold = weakThreshold;
            mStrictThreshold = strictThreshold;
            mMaxLevel = maxLevel;
            mUseTransparentMask = useTransparentMask;
            mLimit = limit;
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
    }

    /**
     * Builds a matching mask from the alpha channel of a 4-channel template.
     * Returns null for templates without an alpha channel, in which case no mask is applied.
     * zh-CN: 由 4 通道模板的 alpha 通道生成匹配掩码; 模板无 alpha 通道时返回 null (不使用掩码).
     */
    @Nullable
    private static Mat createTransparentMask(Mat mat) {
        if (mat.channels() != 4) {
            return null;
        }
        Mat alpha = new Mat();
        Mat binary = new Mat();
        Mat ones = null;
        try {
            Core.extractChannel(mat, alpha, 3);
            Imgproc.threshold(alpha, binary, 127.0, 1.0, Imgproc.THRESH_BINARY);
            ones = Mat.ones(mat.rows(), mat.cols(), CvType.CV_8UC(3));
            Mat mask = new Mat();
            Core.merge(Collections.unmodifiableList(Arrays.asList(ones, binary)), mask);
            return mask;
        } finally {
            OpenCVHelper.release(alpha);
            OpenCVHelper.release(binary);
            OpenCVHelper.release(ones);
        }
    }

    /**
     * 采用图像金字塔算法快速找图
     *
     * @param img      图片
     * @param template 模板图片
     * @param options  选项
     */
    public static List<Match> fastTemplateMatching(Mat img, Mat template, Options options) {

        int matchingMethod = options.getMatchingMethod();
        float weakThreshold = options.getWeakThreshold();
        float strictThreshold = options.getStrictThreshold();
        int maxLevel = options.getMaxLevel();
        int limit = options.getLimit();
        boolean useTransparentMask = options.getUseTransparentMask();

        List<Match> finalMatchResult = new ArrayList<>();

        // A template larger than the image (or an empty input) can never match.
        // zh-CN: 模板大于图像 (或输入为空) 时不可能匹配.
        if (img.empty() || template.empty() || template.cols() > img.cols() || template.rows() > img.rows() || limit <= 0) {
            return finalMatchResult;
        }

        int selectPyramidLevel = maxLevel;

        if (selectPyramidLevel == MAX_LEVEL_AUTO) {
            // 自动选取金字塔层数
            selectPyramidLevel = selectPyramidLevel(img, template);
        }
        // 保存每一轮匹配到模板图片在原图片的位置
        List<Match> previousMatchResult = Collections.emptyList();
        boolean isFirstMatching = true;
        for (int level = selectPyramidLevel; level >= 0; level--) {
            // 放缩图片
            List<Match> currentMatchResult = new ArrayList<>();
            Mat src = null;
            Mat currentTemplate = null;
            Mat transparentMask = null;
            try {
                src = getPyramidDownAtLevel(img, level);
                currentTemplate = getPyramidDownAtLevel(template, level);
                transparentMask = useTransparentMask ? createTransparentMask(currentTemplate) : null;

                // 如果在上一轮中没有匹配到图片, 则考虑是否退出匹配
                if (previousMatchResult.isEmpty()) {
                    // 如果不是第一次匹配, 并且不满足shouldContinueMatching的条件, 则直接退出匹配
                    if (!isFirstMatching && !shouldContinueMatching(level, selectPyramidLevel)) {
                        break;
                    }
                    Mat matchResult = matchTemplate(src, currentTemplate, matchingMethod, transparentMask);
                    try {
                        getBestMatched(matchResult, currentTemplate, matchingMethod, weakThreshold, currentMatchResult, limit, level, null, finalMatchResult);
                    } finally {
                        OpenCVHelper.release(matchResult);
                    }
                } else {
                    for (Match match : previousMatchResult) {
                        // 根据上一轮的匹配点, 计算本次匹配的区域
                        Rect roi = getROI(match.point, src, currentTemplate);
                        if (roi == null) {
                            continue;
                        }
                        Mat m = new Mat(src, roi);
                        Mat matchResult = null;
                        try {
                            matchResult = matchTemplate(m, currentTemplate, matchingMethod, transparentMask);
                            getBestMatched(matchResult, currentTemplate, matchingMethod, weakThreshold, currentMatchResult, limit, level, roi, finalMatchResult);
                        } finally {
                            OpenCVHelper.release(m);
                            OpenCVHelper.release(matchResult);
                        }
                    }
                }
            } finally {
                releaseIfNeeded(src, img);
                releaseIfNeeded(currentTemplate, template);
                OpenCVHelper.release(transparentMask);
            }

            // 把满足强阈值的点找出来, 加到最终结果列表
            if (!currentMatchResult.isEmpty()) {
                Iterator<Match> iterator = currentMatchResult.iterator();
                while (iterator.hasNext()) {
                    Match match = iterator.next();
                    if (match.similarity >= strictThreshold) {
                        pyrUp(match.point, level);
                        finalMatchResult.add(match);
                        iterator.remove();
                    }
                }
                // 如果所有结果都满足强阈值, 则退出循环, 返回最终结果
                if (currentMatchResult.isEmpty()) {
                    break;
                }
            }
            isFirstMatching = false;
            previousMatchResult = currentMatchResult;
        }
        return finalMatchResult;
    }

    private static void releaseIfNeeded(@Nullable Mat src, Mat ref) {
        if (src != null && src != ref) {
            OpenCVHelper.release(src);
        }
    }

    private static Mat getPyramidDownAtLevel(Mat m, int level) {
        if (level == 0) {
            return m;
        }
        int cols = m.cols();
        int rows = m.rows();
        for (int i = 0; i < level; i++) {
            cols = (cols + 1) / 2;
            rows = (rows + 1) / 2;
        }
        Mat r = new Mat(rows, cols, m.type());
        Imgproc.resize(m, r, new Size(cols, rows));
        return r;
    }

    private static void pyrUp(Point p, int level) {
        for (int i = 0; i < level; i++) {
            p.x *= 2;
            p.y *= 2;
        }
    }

    private static Point pyrDown(Point clone, int n) {
        clone = clone.clone();
        for (int i = 0; i < n; ++i) {
            clone.x /= 2.0;
            clone.y /= 2.0;
        }
        return clone;
    }

    private static boolean shouldContinueMatching(int level, int maxLevel) {
        if (level == maxLevel && level != 0) {
            return true;
        }
        if (maxLevel <= 2) {
            return false;
        }
        return level == maxLevel - 1;
    }

    /**
     * Computes the search window around a match found at the previous pyramid level.
     * The window is 1.5x the template size, slid inside the image so it always contains a full template.
     * Returns null when the template does not fit into the image at this level.
     * zh-CN: 根据上一层的匹配点计算本层搜索窗口 (模板 1.5 倍大小, 并在图像内平移以保证能容纳完整模板);
     * 本层模板无法放入图像时返回 null.
     */
    @Nullable
    private static Rect getROI(Point p, Mat src, Mat currentTemplate) {
        int templateWidth = currentTemplate.cols();
        int templateHeight = currentTemplate.rows();
        int cols = src.cols();
        int rows = src.rows();
        if (templateWidth > cols || templateHeight > rows) {
            return null;
        }
        int w = Math.min((int) (templateWidth * 1.5), cols);
        int h = Math.min((int) (templateHeight * 1.5), rows);
        int x = Math.max(0, Math.min((int) (p.x * 2 - templateWidth / 4), cols - w));
        int y = Math.max(0, Math.min((int) (p.y * 2 - templateHeight / 4), rows - h));
        return new Rect(x, y, w, h);
    }

    private static int selectPyramidLevel(Mat img, Mat template) {
        int minDim = MathUtils.min(img.rows(), img.cols(), template.rows(), template.cols());
        // 这里选取 16 为图像缩小后的最小宽高, 从而用 log(2, minDim / 16) 得到最多可以经过几次缩小.
        int maxLevel = (int) (Math.log(minDim >> 4) / Math.log(2));
        if (maxLevel < 0) {
            return 0;
        }
        // 上限为 6.
        return Math.min(6, maxLevel);
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

    private static boolean isSquaredDifferenceMethod(int matchingMethod) {
        return matchingMethod == Imgproc.TM_SQDIFF || matchingMethod == Imgproc.TM_SQDIFF_NORMED;
    }

    private static void excludeMatch(Mat tmResult, Mat template, int matchingMethod, int level, Match match, Rect roi) {
        Point point;
        if (level == 0) {
            point = match.point;
        } else {
            point = pyrDown(match.point, level);
        }
        Point clone = point;
        if (roi != null) {
            clone = point.clone();
            clone.x -= roi.x;
            clone.y -= roi.y;
        }
        double x = clone.x;
        double width = template.width();
        double i = 1;
        // Fill the excluded area with the worst possible score of the method, so that it can never be picked again.
        // zh-CN: 用该算法的最差得分填充排除区域, 确保不会被再次选中.
        double worst = isSquaredDifferenceMethod(matchingMethod) ? Float.MAX_VALUE : 0.0;
        Imgproc.rectangle(
                tmResult,
                new Point(Math.max(0.0, x - width + i), Math.max(0.0, clone.y - template.height() + i)),
                new Point(Math.min(tmResult.width(), clone.x + template.width()), Math.min(tmResult.height(), clone.y + template.height())),
                new Scalar(worst),
                -1
        );
    }

    @Nullable
    private static Match getBestMatched(Mat tmResult, int matchingMethod, float weakThreshold, Rect roi) {
        Core.MinMaxLocResult minMaxLoc = Core.minMaxLoc(tmResult);
        double value;
        Point pos;
        if (isSquaredDifferenceMethod(matchingMethod)) {
            pos = minMaxLoc.minLoc;
            // TM_SQDIFF_NORMED yields 0 for a perfect match and 1 for the worst one; map it onto a similarity.
            // Plain TM_SQDIFF is unbounded, so its negated value only makes sense with non-positive thresholds.
            // zh-CN: TM_SQDIFF_NORMED 完全匹配为 0, 最差为 1, 转换为相似度; TM_SQDIFF 无界, 其负值仅在阈值非正时有意义.
            value = matchingMethod == Imgproc.TM_SQDIFF_NORMED ? 1 - minMaxLoc.minVal : -minMaxLoc.minVal;
        } else {
            pos = minMaxLoc.maxLoc;
            value = minMaxLoc.maxVal;
        }
        if (value < weakThreshold) {
            return null;
        }
        if (roi != null) {
            pos.x += roi.x;
            pos.y += roi.y;
        }
        return new Match(pos, value);
    }

    private static void getBestMatched(Mat tmResult, Mat template, int matchingMethod, float weakThreshold, List<Match> outResult, int limit, int level, Rect roi, List<Match> finalMatchResult) {
        if (tmResult.empty()) {
            return;
        }
        for (Match match : finalMatchResult) {
            excludeMatch(tmResult, template, matchingMethod, level, match, roi);
        }
        Match bestMatched;
        for (int i = 0; i < limit; ++i) {
            bestMatched = getBestMatched(tmResult, matchingMethod, weakThreshold, roi);
            if (bestMatched == null) {
                break;
            }
            outResult.add(bestMatched);
            excludeMatch(tmResult, template, matchingMethod, 0, bestMatched, roi);
        }
    }

    public static Point singleTemplateMatching(Mat mat, Mat mat2, Options options) {
        List<Match> fastTemplateMatching = fastTemplateMatching(mat, mat2, options);
        return fastTemplateMatching.isEmpty() ? null : fastTemplateMatching.get(0).point;
    }

}
