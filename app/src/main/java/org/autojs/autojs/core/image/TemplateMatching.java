package org.autojs.autojs.core.image;

import android.util.TimingLogger;

import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.autojs.autojs.util.MathUtils;

import org.autojs.autojs.core.opencv.Mat;
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
 */
public class TemplateMatching {

    private static final String LOG_TAG = "TemplateMatching";

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
         * @param useTransparentMask ...
         * @param limit              ...
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

    private static Mat createTransparentMask(Mat mat) {
        Mat mat2 = new Mat();
        Core.extractChannel(mat, mat2, 3);
        Mat mat3 = new Mat();
        Imgproc.threshold(mat2, mat3, 127.0, 1.0, 0);
        Mat ones = Mat.ones(mat.rows(), mat.cols(), CvType.CV_8UC(3));
        mat = new Mat();
        List<Mat> list = Arrays.asList(ones, mat3);
        Core.merge(Collections.unmodifiableList(list), mat);
        OpenCVHelper.release(mat2);
        OpenCVHelper.release(mat3);
        OpenCVHelper.release(ones);
        return mat;
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

        TimingLogger logger = new TimingLogger(LOG_TAG, "fast_tm");

        int selectPyramidLevel = maxLevel;

        if (selectPyramidLevel == MAX_LEVEL_AUTO) {
            // 自动选取金字塔层数
            selectPyramidLevel = selectPyramidLevel(img, template);
            logger.addSplit("selectPyramidLevel:" + selectPyramidLevel);
        }
        // 保存每一轮匹配到模板图片在原图片的位置
        List<Match> finalMatchResult = new ArrayList<>();
        List<Match> previousMatchResult = Collections.emptyList();
        boolean isFirstMatching = true;
        for (int level = selectPyramidLevel; level >= 0; level--) {
            // 放缩图片
            List<Match> currentMatchResult = new ArrayList<>();
            Mat src = getPyramidDownAtLevel(img, level);
            Mat currentTemplate = getPyramidDownAtLevel(template, level);
            Mat transparentMask = useTransparentMask ? createTransparentMask(currentTemplate) : null;

            boolean shouldStop;

            Label_Check_Should_Stop:
            {
                // 如果在上一轮中没有匹配到图片, 则考虑是否退出匹配
                if (previousMatchResult.isEmpty()) {
                    // 如果不是第一次匹配, 并且不满足shouldContinueMatching的条件, 则直接退出匹配
                    if (!isFirstMatching && !shouldContinueMatching(level, selectPyramidLevel)) {
                        shouldStop = true;
                        break Label_Check_Should_Stop;
                    }
                    Mat matchResult = matchTemplate(src, currentTemplate, matchingMethod, transparentMask);
                    getBestMatched(matchResult, currentTemplate, matchingMethod, weakThreshold, currentMatchResult, limit, level, null, finalMatchResult);
                    OpenCVHelper.release(matchResult);
                } else {
                    for (Match match : previousMatchResult) {
                        // 根据上一轮的匹配点, 计算本次匹配的区域
                        Rect roi = getROI(match.point, src, currentTemplate);
                        Mat m = new Mat(src, roi);
                        Mat matchResult = matchTemplate(m, currentTemplate, matchingMethod, transparentMask);
                        getBestMatched(matchResult, currentTemplate, matchingMethod, weakThreshold, currentMatchResult, limit, level, roi, finalMatchResult);
                        OpenCVHelper.release(m);
                        OpenCVHelper.release(matchResult);
                    }
                }
                shouldStop = false;
            }
            releaseIfNeeded(src, img);
            releaseIfNeeded(currentTemplate, template);
            OpenCVHelper.release(transparentMask);

            logger.addSplit("level:" + level + ", result:" + previousMatchResult);

            if (!shouldStop) {
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
                previousMatchResult = currentMatchResult;
                isFirstMatching = false;
            }
        }
        logger.addSplit("result:" + finalMatchResult);
        logger.dumpToLog();
        return finalMatchResult;
    }

    private static void releaseIfNeeded(Mat src, Mat ref) {
        if (src != ref) {
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

    private static Rect getROI(Point p, Mat src, Mat currentTemplate) {
        int x = (int) (p.x * 2 - currentTemplate.cols() / 4);
        x = Math.max(0, x);
        int y = (int) (p.y * 2 - currentTemplate.rows() / 4);
        y = Math.max(0, y);
        int w = (int) (currentTemplate.cols() * 1.5);
        int h = (int) (currentTemplate.rows() * 1.5);
        if (x + w >= src.cols()) {
            w = src.cols() - x - 1;
        }
        if (y + h >= src.rows()) {
            h = src.rows() - y - 1;
        }
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

    public static Mat matchTemplate(Mat img, Mat template, int matchingMethod, Mat transparentMast) {
        Mat result = new Mat(img.rows() - template.rows() + 1, img.cols() - template.cols() + 1, CvType.CV_32FC1);
        if (transparentMast == null) {
            Imgproc.matchTemplate(img, template, result, matchingMethod);
        } else {
            Imgproc.matchTemplate(img, template, result, matchingMethod, transparentMast);
        }
        return result;
    }

    private static void excludeMatch(Mat tmResult, Mat template, int level, Match match, Rect roi) {
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
        Imgproc.rectangle(
                tmResult,
                new Point(Math.max(0.0, x - width + i), Math.max(0.0, clone.y - template.height() + i)),
                new Point(Math.min(tmResult.width(), clone.x + template.width()), Math.min(tmResult.height(), clone.y + template.height())),
                new Scalar(0.0, 255.0, 0.0),
                -1
        );
    }

    private static Match getBestMatched(Mat tmResult, int matchingMethod, float weakThreshold, Rect roi) {
        TimingLogger logger = new TimingLogger(LOG_TAG, "best_matched_point");
        Core.MinMaxLocResult minMaxLoc = Core.minMaxLoc(tmResult);
        logger.addSplit("minMaxLoc");
        double value;
        Point pos;
        if (matchingMethod == Imgproc.TM_SQDIFF || matchingMethod == Imgproc.TM_SQDIFF_NORMED) {
            pos = minMaxLoc.minLoc;
            value = -minMaxLoc.minVal;
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
        logger.addSplit("value:" + value);
        return new Match(pos, value);
    }

    private static void getBestMatched(Mat tmResult, Mat template, int matchingMethod, float weakThreshold, List<Match> outResult, int limit, int level, Rect roi, List<Match> finalMatchResult) {
        for (Match match : finalMatchResult) {
            excludeMatch(tmResult, template, level, match, roi);
        }
        Match bestMatched;
        for (int i = 0; i < limit; ++i) {
            bestMatched = getBestMatched(tmResult, matchingMethod, weakThreshold, roi);
            if (bestMatched == null) {
                break;
            }
            outResult.add(bestMatched);
            excludeMatch(tmResult, template, 0, bestMatched, roi);
        }
    }

    public static Point singleTemplateMatching(Mat mat, Mat mat2, Options options) {
        List<Match> fastTemplateMatching = fastTemplateMatching(mat, mat2, options);
        return fastTemplateMatching.isEmpty() ? null : fastTemplateMatching.get(0).point;
    }

}
