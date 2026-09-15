package org.autojs.autojs.runtime.api;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.annotation.ScriptInterface;
import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Feature2D;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.features2d.SIFT;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Feature detection (SIFT / ORB) and feature matching with homography estimation.
 * <p>
 * Descriptors keep a grayscale copy of the (scaled) image they were computed on, so that match
 * visualizations can be drawn later, and their corners are expressed in that scaled coordinate
 * system so that the projected quad matches the key points.
 * <p>
 * zh-CN: 特征检测 (SIFT / ORB) 与特征匹配 (含单应矩阵估计). 描述符保留计算时所用 (缩放后) 图像的灰度副本以便随后绘制匹配图,
 * 其四角点使用同一缩放坐标系, 与关键点保持一致.
 * <p>
 * Created by SuperMonster003 on Jan 7, 2024.
 * Modified by SuperMonster003 as of Sep 15, 2026.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Jan 7, 2024.
public final class ImageFeatureMatching {

    private static final String TAG = ImageFeatureMatching.class.getSimpleName();

    @ScriptInterface
    public static int FEATURE_MATCHING_METHOD_SIFT = 1;

    @ScriptInterface
    public static int FEATURE_MATCHING_METHOD_ORB = 2;

    /** Default feature cap for ORB (0 would mean the OpenCV default of 500). zh-CN: ORB 默认特征数上限. */
    public static final int DEFAULT_ORB_FEATURES = 20000;
    /** Default feature cap for SIFT: unlimited. zh-CN: SIFT 默认不限制特征数. */
    public static final int DEFAULT_SIFT_FEATURES = 0;
    /** Default RANSAC reprojection threshold in pixels. zh-CN: 默认 RANSAC 重投影阈值 (像素). */
    public static final double DEFAULT_RANSAC_REPROJECTION_THRESHOLD = 3.0;
    /** Minimum number of point pairs a homography can be estimated from. zh-CN: 估计单应矩阵所需的最少点对数. */
    public static final int MIN_POINTS_FOR_HOMOGRAPHY = 4;

    /** Options of {@link #createFeatureMatchingDescriptor(Mat, DetectionOptions)}. zh-CN: 特征检测选项. */
    public static final class DetectionOptions {
        public final int method;
        public final float scale;
        public final int cvtColorFlag;
        public final int maxFeatures;

        /**
         * @param method       FEATURE_MATCHING_METHOD_SIFT or FEATURE_MATCHING_METHOD_ORB
         * @param scale        scale factor applied before detection; values <= 0 or 1 keep the original size
         * @param cvtColorFlag OpenCV cvtColor flag applied first, or -1; the result is always converted to grayscale afterwards
         * @param maxFeatures  feature cap, <= 0 for the per-method default (unlimited for SIFT, 20000 for ORB)
         */
        public DetectionOptions(int method, float scale, int cvtColorFlag, int maxFeatures) {
            this.method = method;
            this.scale = scale;
            this.cvtColorFlag = cvtColorFlag;
            this.maxFeatures = maxFeatures;
        }
    }

    /** Options of {@link #featureMatching(FeatureMatchingDescriptor, FeatureMatchingDescriptor, MatchingOptions)}. zh-CN: 特征匹配选项. */
    public static final class MatchingOptions {
        public final int matcherType;
        public final float ratioThreshold;
        public final double ransacReprojectionThreshold;
        public final int minInliers;
        public final boolean drawMatches;

        /**
         * @param matcherType                 DescriptorMatcher constant, or -1 to pick one that fits the descriptor type
         * @param ratioThreshold              Lowe ratio threshold in (0, 1); higher keeps more matches
         * @param ransacReprojectionThreshold RANSAC reprojection threshold in pixels of the scaled scene
         * @param minInliers                  minimum RANSAC inliers for a frame to be reported (at least 4)
         * @param drawMatches                 whether to render the match visualization
         */
        public MatchingOptions(int matcherType, float ratioThreshold, double ransacReprojectionThreshold, int minInliers, boolean drawMatches) {
            this.matcherType = matcherType;
            this.ratioThreshold = ratioThreshold;
            this.ransacReprojectionThreshold = ransacReprojectionThreshold;
            this.minInliers = minInliers;
            this.drawMatches = drawMatches;
        }
    }

    /**
     * Pre-computes a feature-matching descriptor for an image (legacy signature).
     * zh-CN: 创建 (预计算) 特征匹配描述符 (旧签名).
     *
     * @param src          The source Mat. The content will not be modified.
     * @param cvtColorFlag OpenCV cvtColor flag applied first; -1 means "no conversion". The image is converted to grayscale afterwards anyway.
     * @param scale        Scale factor; &gt;0 to resize, &lt;=0 or 1 to keep original size.
     * @param method       Extraction method, see {@code FEATURE_MATCHING_METHOD_*}.
     */
    @ScriptInterface
    public static FeatureMatchingDescriptor createFeatureMatchingDescriptor(@NonNull Mat src, int cvtColorFlag, float scale, int method) {
        return createFeatureMatchingDescriptor(src, new DetectionOptions(method, scale, cvtColorFlag, 0));
    }

    /**
     * Pre-computes a feature-matching descriptor for an image. The pipeline:
     * <ol>
     *   <li>Optional color-space conversion controlled by {@code cvtColorFlag}, then conversion to 8-bit grayscale
     *       (both SIFT and ORB work on intensity only, and ORB rejects 4-channel input)</li>
     *   <li>Optional uniform scaling specified by {@code scale} (area interpolation when shrinking)</li>
     *   <li>Key-point detection and descriptor extraction according to {@code method} (SIFT / ORB)</li>
     *   <li>The grayscale working image and its corners (in the scaled coordinate system) are kept for matching and drawing</li>
     * </ol>
     * <p>
     * zh-CN: 创建 (预计算) 特征匹配描述符: 可选颜色空间转换后统一转为 8 位灰度 (SIFT 与 ORB 均只使用亮度, 且 ORB 不接受 4 通道输入),
     * 可选缩放 (缩小时使用区域插值), 按 method 检测关键点并计算描述子, 并保留灰度工作图及其 (缩放坐标系下的) 四角点供匹配与绘图使用.
     *
     * @throws IllegalArgumentException if {@code src} is empty or {@code method} is unsupported
     */
    public static FeatureMatchingDescriptor createFeatureMatchingDescriptor(@NonNull Mat src, @NonNull DetectionOptions options) {
        if (src.empty()) {
            throw new IllegalArgumentException("Input Mat is null or empty.");
        }
        Feature2D detector = createDetector(options.method, options.maxFeatures);

        Mat converted = null;
        Mat gray = null;
        Mat working = null;
        boolean ok = false;
        try {
            Mat current = src;
            if (options.cvtColorFlag >= 0) {
                converted = new Mat();
                Imgproc.cvtColor(src, converted, options.cvtColorFlag);
                current = converted;
            }
            gray = toGray(current);
            current = gray;
            float scale = options.scale;
            if (scale > 0f && Math.abs(scale - 1f) > 1e-3) {
                int cols = Math.max(1, Math.round(current.cols() * scale));
                int rows = Math.max(1, Math.round(current.rows() * scale));
                working = new Mat();
                Imgproc.resize(current, working, new Size(cols, rows), 0, 0, scale < 1 ? Imgproc.INTER_AREA : Imgproc.INTER_LINEAR);
            } else {
                // The descriptor owns its working image, so never keep a reference to the caller's matrix.
                // zh-CN: 描述符独占工作图, 不能持有调用方矩阵的引用.
                working = current == src ? src.clone() : current;
            }

            MatOfKeyPoint keyPoints = new MatOfKeyPoint();
            Mat descriptors = new Mat();
            Mat noMask = new Mat();
            try {
                detector.detectAndCompute(working, noMask, keyPoints, descriptors);
            } finally {
                OpenCVHelper.release(noMask);
            }
            if (options.maxFeatures > 0 && descriptors.rows() > options.maxFeatures) {
                // SIFT also keeps key points tying with the last retained response; enforce the cap strictly
                // (rows are already ordered best-first by the detector).
                // zh-CN: SIFT 还会保留与末位响应值相同的关键点; 这里严格截断到上限 (检测器已按响应值从高到低排列).
                Mat truncated = descriptors.rowRange(0, options.maxFeatures).clone();
                OpenCVHelper.release(descriptors);
                descriptors = truncated;
                keyPoints.fromList(new ArrayList<>(keyPoints.toList().subList(0, options.maxFeatures)));
            }

            int w = working.cols();
            int h = working.rows();
            MatOfPoint2f corners = new MatOfPoint2f(new Point(0, 0), new Point(w, 0), new Point(0, h), new Point(w, h));
            FeatureMatchingDescriptor descriptor = new FeatureMatchingDescriptor(descriptors, keyPoints, corners, working, options.method, scale > 0f ? scale : 1f);
            ok = true;
            return descriptor;
        } finally {
            if (converted != null && converted != working) {
                OpenCVHelper.release(converted);
            }
            if (gray != null && gray != working && gray != converted && gray != src) {
                OpenCVHelper.release(gray);
            }
            if (!ok && working != null && working != src) {
                OpenCVHelper.release(working);
            }
        }
    }

    private static Feature2D createDetector(int method, int maxFeatures) {
        if (method == FEATURE_MATCHING_METHOD_SIFT) {
            return SIFT.create(maxFeatures > 0 ? maxFeatures : DEFAULT_SIFT_FEATURES);
        }
        if (method == FEATURE_MATCHING_METHOD_ORB) {
            // WTA_K stays 2 so that the descriptors are compared with the plain Hamming norm of BRUTEFORCE_HAMMING;
            // 3 or 4 would require NORM_HAMMING2, which the matcher factory does not offer.
            // zh-CN: WTA_K 保持为 2, 使描述子可用 BRUTEFORCE_HAMMING 的普通汉明距离比较; 3 或 4 需要 NORM_HAMMING2, 匹配器工厂不提供.
            return ORB.create(
                    /* nFeatures = */ maxFeatures > 0 ? maxFeatures : DEFAULT_ORB_FEATURES,
                    /* scaleFactor = */ 1.2f,
                    /* nLevels = */ 8,
                    // The effective border is max(edgeThreshold, patchSize / 2) + 1, so 15 is the smallest value that
                    // still keeps every descriptor patch inside the image; the OpenCV default of 31 discards every key
                    // point of objects smaller than 63 px and most of a 160 px one.
                    // zh-CN: 实际边界为 max(edgeThreshold, patchSize / 2) + 1, 15 是仍能保证描述子块位于图内的最小值;
                    // OpenCV 默认的 31 会丢弃小于 63 px 的目标的全部关键点及 160 px 目标的大部分关键点.
                    /* edgeThreshold = */ 15,
                    /* firstLevel = */ 0,
                    /* WTA_K = */ 2,
                    /* scoreType = */ ORB.HARRIS_SCORE,
                    /* patchSize = */ 31,
                    /* fastThreshold = */ 20);
        }
        throw new IllegalArgumentException("Unsupported feature matching method: " + method);
    }

    /**
     * Converts a 1-, 3- or 4-channel matrix to 8-bit grayscale; returns the input when it already is.
     * zh-CN: 将 1, 3 或 4 通道矩阵转换为 8 位灰度; 已是灰度时返回原矩阵.
     */
    static Mat toGray(Mat src) {
        int channels = src.channels();
        int code;
        if (channels == 1) {
            if (src.depth() == CvType.CV_8U) {
                return src;
            }
            Mat result = new Mat();
            src.convertTo(result, CvType.CV_8U);
            return result;
        } else if (channels == 3) {
            code = Imgproc.COLOR_RGB2GRAY;
        } else if (channels == 4) {
            code = Imgproc.COLOR_RGBA2GRAY;
        } else {
            throw new IllegalArgumentException("Unsupported channel count for feature detection: " + channels);
        }
        Mat result = new Mat();
        Imgproc.cvtColor(src, result, code);
        if (result.depth() != CvType.CV_8U) {
            Mat converted = new Mat();
            result.convertTo(converted, CvType.CV_8U);
            OpenCVHelper.release(result);
            return converted;
        }
        return result;
    }

    /**
     * Matches two pre-computed descriptors (legacy signature): the matcher is validated against the descriptor type,
     * Lowe's ratio test uses {@code threshold}, and the visualization is rendered when {@code debugMatchesImagePath}
     * is non-empty (the caller saves {@link FeatureMatchingResult#getMatches()}).
     * zh-CN: 匹配两个描述符 (旧签名): 匹配器按描述子类型校验, Lowe 比例测试使用 threshold, debugMatchesImagePath 非空时渲染匹配图 (由调用方保存).
     */
    @ScriptInterface
    public static FeatureMatchingResult featureMatching(
            @NonNull FeatureMatchingDescriptor sceneDesc,
            @NonNull FeatureMatchingDescriptor objectDesc,
            int matcherType,
            @Nullable String debugMatchesImagePath,
            float threshold
    ) {
        boolean draw = debugMatchesImagePath != null && !debugMatchesImagePath.isEmpty();
        return featureMatching(sceneDesc, objectDesc, new MatchingOptions(matcherType, threshold, DEFAULT_RANSAC_REPROJECTION_THRESHOLD, MIN_POINTS_FOR_HOMOGRAPHY, draw));
    }

    /**
     * Matches two pre-computed feature descriptors and estimates a homography to locate the <i>object</i>
     * image inside the <i>scene</i> image.
     * <ol>
     *   <li>KNN matching (k = 2) with a matcher that fits the descriptor type, followed by Lowe's ratio test</li>
     *   <li>With at least 4 good matches, {@code findHomography(RANSAC)} separates inliers from outliers</li>
     *   <li>The object corners are projected into the scene; the quad is reported only when the inlier count
     *       reaches {@code minInliers} and the projection is a convex, non-degenerate quadrilateral</li>
     *   <li>Optionally the good matches are drawn onto the two working images</li>
     * </ol>
     * <p>
     * zh-CN: 匹配两个描述符并估计单应矩阵以定位 object 图在 scene 图中的位置: 使用与描述子类型匹配的匹配器做 KNN (k = 2) 匹配并经 Lowe 比例测试;
     * 良好匹配不少于 4 对时用 RANSAC 估计单应矩阵并区分内点; 仅当内点数达到 minInliers 且投影为凸的非退化四边形时返回 quad; 可选绘制匹配图.
     *
     * @return points are RANSAC inliers in scaled scene coordinates (ratio-test matches when no homography could be
     *         estimated), quad is [TL, TR, BL, BR] of the object in scaled scene coordinates or null
     * @throws IllegalStateException    if a descriptor has been released
     * @throws IllegalArgumentException if the descriptors were computed with different methods
     */
    public static FeatureMatchingResult featureMatching(@NonNull FeatureMatchingDescriptor sceneDesc, @NonNull FeatureMatchingDescriptor objectDesc, @NonNull MatchingOptions options) {
        if (sceneDesc.isReleased() || objectDesc.isReleased()) {
            throw new IllegalStateException("Feature descriptors have already been released");
        }
        Mat sceneDescriptors = sceneDesc.getDescriptors();
        Mat objectDescriptors = objectDesc.getDescriptors();
        if (sceneDescriptors.empty() || objectDescriptors.empty()) {
            // An image without any key point (e.g. a flat color) can never be matched.
            // zh-CN: 没有任何关键点的图片 (如纯色) 无法匹配.
            return new FeatureMatchingResult(new ArrayList<>(), null, null, 0, 0);
        }
        if (sceneDescriptors.type() != objectDescriptors.type() || sceneDescriptors.cols() != objectDescriptors.cols()) {
            throw new IllegalArgumentException(String.format("Incompatible feature descriptors: scene %s (%d columns) vs object %s (%d columns); detect both images with the same method",
                    CvType.typeToString(sceneDescriptors.type()), sceneDescriptors.cols(), CvType.typeToString(objectDescriptors.type()), objectDescriptors.cols()));
        }

        int matcherType = resolveMatcherType(options.matcherType, objectDescriptors.type());
        DescriptorMatcher matcher = DescriptorMatcher.create(matcherType);
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        List<DMatch> goodMatches = new ArrayList<>();
        try {
            // @Reference to Sakura小败狗 (https://blog.csdn.net/qq_42670220) by SuperMonster003 on Feb 26, 2024.
            //  ! https://blog.csdn.net/qq_42670220/article/details/108623752
            //  ! k = 2: every object descriptor gets its two nearest scene descriptors, and a match is kept when the
            //  ! nearest one is clearly closer than the second (Lowe's ratio test).
            //  ! zh-CN: k = 2, 每个 object 描述子取最近的两个 scene 描述子, 最近距离明显小于次近距离时保留 (Lowe 比例测试).
            matcher.knnMatch(objectDescriptors, sceneDescriptors, knnMatches, 2);
            for (MatOfDMatch matOfDMatch : knnMatches) {
                DMatch[] matches = matOfDMatch.toArray();
                if (matches.length >= 2) {
                    if (matches[0].distance < options.ratioThreshold * matches[1].distance) {
                        goodMatches.add(matches[0]);
                    }
                } else if (matches.length == 1 && sceneDescriptors.rows() == 1) {
                    // A single scene descriptor cannot be ratio-tested; keep the match as is.
                    // zh-CN: scene 只有一个描述子时无法做比例测试, 直接保留.
                    goodMatches.add(matches[0]);
                }
            }
        } finally {
            for (MatOfDMatch matOfDMatch : knnMatches) {
                OpenCVHelper.release(matOfDMatch);
            }
        }

        KeyPoint[] objectKeyPoints = objectDesc.getKeyPoint().toArray();
        KeyPoint[] sceneKeyPoints = sceneDesc.getKeyPoint().toArray();
        List<Point> objectPoints = new ArrayList<>(goodMatches.size());
        List<Point> scenePoints = new ArrayList<>(goodMatches.size());
        for (DMatch match : goodMatches) {
            objectPoints.add(objectKeyPoints[match.queryIdx].pt);
            scenePoints.add(sceneKeyPoints[match.trainIdx].pt);
        }

        List<Point> quad = null;
        List<Point> inlierPoints = scenePoints;
        List<DMatch> inlierMatches = goodMatches;
        int inlierCount = 0;
        Log.d(TAG, "goodMatches: " + goodMatches.size());
        if (goodMatches.size() >= MIN_POINTS_FOR_HOMOGRAPHY) {
            MatOfPoint2f objectMat = new MatOfPoint2f();
            MatOfPoint2f sceneMat = new MatOfPoint2f();
            MatOfPoint2f sceneCorners = new MatOfPoint2f();
            Mat inlierMask = new Mat();
            Mat homography = null;
            try {
                objectMat.fromList(objectPoints);
                sceneMat.fromList(scenePoints);
                homography = Calib3d.findHomography(objectMat, sceneMat, Calib3d.RANSAC, options.ransacReprojectionThreshold, inlierMask);
                if (!homography.empty()) {
                    inlierPoints = new ArrayList<>();
                    inlierMatches = new ArrayList<>();
                    if (inlierMask.rows() == scenePoints.size()) {
                        byte[] flags = new byte[(int) inlierMask.total()];
                        inlierMask.get(0, 0, flags);
                        for (int i = 0; i < flags.length; i++) {
                            if (flags[i] != 0) {
                                inlierPoints.add(scenePoints.get(i));
                                inlierMatches.add(goodMatches.get(i));
                            }
                        }
                    } else {
                        inlierPoints.addAll(scenePoints);
                        inlierMatches.addAll(goodMatches);
                    }
                    inlierCount = inlierPoints.size();
                    if (inlierCount >= Math.max(MIN_POINTS_FOR_HOMOGRAPHY, options.minInliers)) {
                        Core.perspectiveTransform(objectDesc.getCorners(), sceneCorners, homography);
                        List<Point> projected = sceneCorners.toList();
                        if (isValidQuad(projected)) {
                            quad = projected;
                        } else {
                            Log.d(TAG, "Rejected degenerate quad: " + projected);
                        }
                    }
                }
            } finally {
                OpenCVHelper.release(objectMat);
                OpenCVHelper.release(sceneMat);
                OpenCVHelper.release(sceneCorners);
                OpenCVHelper.release(inlierMask);
                OpenCVHelper.release(homography);
            }
        }

        Mat matchesImage = null;
        if (options.drawMatches) {
            // Draw the inliers when a homography was found, otherwise every ratio-test match.
            // zh-CN: 找到单应矩阵时绘制内点, 否则绘制全部通过比例测试的匹配.
            matchesImage = drawMatches(objectDesc, sceneDesc, inlierMatches);
        }
        return new FeatureMatchingResult(inlierPoints, quad, matchesImage, goodMatches.size(), inlierCount);
    }

    /**
     * Picks a matcher that can compare the given descriptor type: binary descriptors (ORB) need a Hamming
     * matcher, float descriptors (SIFT) need FLANN or an L2 / L1 brute-force matcher. A requested matcher that
     * cannot handle the type is replaced by the fitting default.
     * zh-CN: 选择能比较该描述子类型的匹配器: 二进制描述子 (ORB) 需要汉明匹配器, 浮点描述子 (SIFT) 需要 FLANN 或 L2 / L1 暴力匹配器;
     * 请求的匹配器不适用时替换为对应默认值.
     */
    static int resolveMatcherType(int requested, int descriptorType) {
        boolean binary = CvType.depth(descriptorType) == CvType.CV_8U;
        boolean hamming = requested == DescriptorMatcher.BRUTEFORCE_HAMMING || requested == DescriptorMatcher.BRUTEFORCE_HAMMINGLUT;
        boolean known = requested == DescriptorMatcher.FLANNBASED || requested == DescriptorMatcher.BRUTEFORCE
                || requested == DescriptorMatcher.BRUTEFORCE_L1 || requested == DescriptorMatcher.BRUTEFORCE_SL2 || hamming;
        if (binary) {
            return hamming ? requested : DescriptorMatcher.BRUTEFORCE_HAMMING;
        }
        return known && !hamming ? requested : DescriptorMatcher.FLANNBASED;
    }

    /**
     * A projected quad is usable when all corners are finite, the polygon TL-TR-BR-BL is convex and has a
     * positive area; RANSAC can still produce twisted or collapsed quads from too few or wrong inliers.
     * zh-CN: 四角点均为有限值, 多边形 TL-TR-BR-BL 为凸且面积为正时才可用; 内点过少或错误时 RANSAC 仍可能给出扭曲或塌陷的四边形.
     *
     * @param quad corners in the order TL, TR, BL, BR
     */
    static boolean isValidQuad(@Nullable List<Point> quad) {
        if (quad == null || quad.size() != 4) {
            return false;
        }
        for (Point p : quad) {
            if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y)) {
                return false;
            }
        }
        // Traverse the polygon boundary: TL -> TR -> BR -> BL.
        Point[] ring = {quad.get(0), quad.get(1), quad.get(3), quad.get(2)};
        double area = 0;
        int sign = 0;
        for (int i = 0; i < 4; i++) {
            Point a = ring[i];
            Point b = ring[(i + 1) % 4];
            Point c = ring[(i + 2) % 4];
            double cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x);
            if (cross == 0) {
                return false;
            }
            int s = cross > 0 ? 1 : -1;
            if (sign == 0) {
                sign = s;
            } else if (sign != s) {
                return false;
            }
            area += a.x * b.y - b.x * a.y;
        }
        return Math.abs(area) / 2 >= 1;
    }

    @Nullable
    private static Mat drawMatches(FeatureMatchingDescriptor objectDesc, FeatureMatchingDescriptor sceneDesc, List<DMatch> goodMatches) {
        Mat objectImage = objectDesc.getImage();
        Mat sceneImage = sceneDesc.getImage();
        if (objectImage == null || sceneImage == null || objectImage.empty() || sceneImage.empty()) {
            return null;
        }
        MatOfDMatch matches = new MatOfDMatch(goodMatches.toArray(new DMatch[0]));
        MatOfByte noMask = new MatOfByte();
        Mat output = new Mat();
        try {
            // Only matched key points are drawn (random colors), so the picture stays readable on textured scenes.
            // zh-CN: 仅绘制匹配到的关键点 (随机颜色), 纹理丰富的场景下图片仍保持可读.
            Features2d.drawMatches(objectImage, objectDesc.getKeyPoint(), sceneImage, sceneDesc.getKeyPoint(), matches, output,
                    Scalar.all(-1), Scalar.all(-1), noMask, Features2d.DrawMatchesFlags_NOT_DRAW_SINGLE_POINTS);
            return output;
        } catch (RuntimeException e) {
            OpenCVHelper.release(output);
            Log.w(TAG, "Failed to draw matches", e);
            return null;
        } finally {
            OpenCVHelper.release(matches);
            OpenCVHelper.release(noMask);
        }
    }

    /**
     * A reusable bundle of feature-extraction results for a single image.
     * zh-CN: 单张图片的特征提取结果集合.
     */
    public static class FeatureMatchingDescriptor {

        private final Mat mDescriptors;
        private final MatOfKeyPoint mKeyPoint;
        private final MatOfPoint2f mCorners;
        @Nullable
        private final Mat mImage;
        private final int mMethod;
        private final float mScale;
        private boolean mReleased;

        /**
         * @param descriptors Feature descriptor matrix computed by the feature detector.<br>
         *                    zh-CN: 特征检测器计算出的描述子矩阵.
         * @param keyPoint    Detected key points in the (scaled) working image.<br>
         *                    zh-CN: 在 (缩放后的) 工作图中检测到的关键点集.
         * @param corners     Corners of the working image, always [TL, TR, BL, BR], used for homography projection.<br>
         *                    zh-CN: 工作图的四角点, 按 [左上, 右上, 左下, 右下] 顺序, 用于单应矩阵投影.
         */
        public FeatureMatchingDescriptor(Mat descriptors, MatOfKeyPoint keyPoint, MatOfPoint2f corners) {
            this(descriptors, keyPoint, corners, null, 0, 1f);
        }

        /**
         * @param image  Grayscale working image the key points refer to (owned by the descriptor), or null.<br>
         *               zh-CN: 关键点所对应的灰度工作图 (由描述符独占), 可为 null.
         * @param method FEATURE_MATCHING_METHOD_* used for detection, 0 if unknown.
         * @param scale  Scale factor applied to the source image before detection.
         */
        public FeatureMatchingDescriptor(Mat descriptors, MatOfKeyPoint keyPoint, MatOfPoint2f corners, @Nullable Mat image, int method, float scale) {
            mDescriptors = descriptors;
            mKeyPoint = keyPoint;
            mCorners = corners;
            mImage = image;
            mMethod = method;
            mScale = scale;
        }

        public MatOfKeyPoint getKeyPoint() {
            return mKeyPoint;
        }

        public Mat getDescriptors() {
            return mDescriptors;
        }

        public MatOfPoint2f getCorners() {
            return mCorners;
        }

        /** Grayscale working image the key points refer to, or null. zh-CN: 关键点对应的灰度工作图, 可为 null. */
        @Nullable
        public Mat getImage() {
            return mImage;
        }

        /** Detection method (FEATURE_MATCHING_METHOD_*), 0 if unknown. zh-CN: 检测方法. */
        public int getMethod() {
            return mMethod;
        }

        /** Scale factor applied before detection. zh-CN: 检测前应用的缩放比例. */
        public float getScale() {
            return mScale;
        }

        /** Number of detected key points. zh-CN: 检测到的关键点数量. */
        public int getKeyPointCount() {
            return mReleased ? 0 : mKeyPoint.rows();
        }

        public synchronized boolean isReleased() {
            return mReleased;
        }

        public void release() {
            synchronized (this) {
                if (!mReleased) {
                    mReleased = true;
                    OpenCVHelper.release(mDescriptors);
                    OpenCVHelper.release(mKeyPoint);
                    OpenCVHelper.release(mCorners);
                    OpenCVHelper.release(mImage);
                }
            }
        }

    }

    /**
     * Immutable container holding the output of a single {@link #featureMatching} operation.
     * zh-CN: 单次特征匹配的结果.
     */
    public static class FeatureMatchingResult {

        private final List<Point> mPoints;
        private final List<Point> mQuad;
        private final Mat mMatches;
        private final int mMatchCount;
        private final int mInlierCount;

        /**
         * @param pts     Inlier points in the scaled scene image (ratio-test matches when no homography was estimated).<br>
         *                zh-CN: 缩放后 scene 坐标系中的内点 (未能估计单应矩阵时为比例测试通过的匹配点).
         * @param quad    Projected object corners [TL, TR, BL, BR] in the scaled scene image, or null.<br>
         *                zh-CN: object 四角点在缩放后 scene 中的投影 [左上, 右上, 左下, 右下], 可为 null.
         * @param matches Debug visualization image, or null.<br>
         *                zh-CN: 匹配连接图, 可为 null.
         */
        public FeatureMatchingResult(@NonNull List<Point> pts, @Nullable List<Point> quad, @Nullable Mat matches) {
            this(pts, quad, matches, pts.size(), quad != null ? pts.size() : 0);
        }

        /**
         * @param matchCount  Number of matches that passed the ratio test.
         * @param inlierCount Number of RANSAC inliers (0 when no homography was estimated).
         */
        public FeatureMatchingResult(@NonNull List<Point> pts, @Nullable List<Point> quad, @Nullable Mat matches, int matchCount, int inlierCount) {
            mPoints = Collections.unmodifiableList(new ArrayList<>(pts));
            mQuad = quad != null ? Collections.unmodifiableList(new ArrayList<>(quad)) : null;
            mMatches = matches;
            mMatchCount = matchCount;
            mInlierCount = inlierCount;
        }

        /** Rendered match visualization (8UC3), or null. The caller owns and releases it. zh-CN: 匹配图 (8UC3), 由调用方释放. */
        @Nullable
        public Mat getMatches() {
            return mMatches;
        }

        @Nullable
        public List<Point> getQuad() {
            return mQuad;
        }

        @NonNull
        public List<Point> getPoints() {
            return mPoints;
        }

        /** Matches that passed the ratio test. zh-CN: 通过比例测试的匹配数. */
        public int getMatchCount() {
            return mMatchCount;
        }

        /** RANSAC inliers, 0 without a homography. zh-CN: RANSAC 内点数, 无单应矩阵时为 0. */
        public int getInlierCount() {
            return mInlierCount;
        }

        /** Releases the visualization image, if any. zh-CN: 释放匹配图 (若有). */
        public void release() {
            OpenCVHelper.release(mMatches);
        }

        @Override
        public boolean equals(Object o) {
            return o == this || o instanceof FeatureMatchingResult other
                                && Objects.equals(mPoints, other.mPoints)
                                && Objects.equals(mQuad, other.mQuad)
                                && Objects.equals(mMatches, other.mMatches);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPoints, mQuad, mMatches);
        }

        @NonNull
        @Override
        public String toString() {
            return "FeatureMatchingResult(points=" + mPoints.size() + ", quad=" + mQuad + ", matches=" + mMatchCount + ", inliers=" + mInlierCount + ')';
        }

    }

}
