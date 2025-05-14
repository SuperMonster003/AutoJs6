package org.autojs.autojs.runtime.api;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.annotation.ScriptInterface;
import org.autojs.autojs.core.cleaner.Cleaner;
import org.autojs.autojs.core.cleaner.ICleaner;
import org.autojs.autojs.core.ref.MonitorResource;
import org.autojs.autojs.core.ref.NativeObjectReference;
import org.autojs.autojs.util.ImageUtils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Feature2D;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.features2d.SIFT;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Created by SuperMonster003 on Jan 7, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Jan 7, 2024.
public final class ImageFeatureMatching {

    private static final String TAG = ImageFeatureMatching.class.getSimpleName();

    @ScriptInterface
    public static int FEATURE_MATCHING_METHOD_SIFT = 1;

    @ScriptInterface
    public static int FEATURE_MATCHING_METHOD_ORB = 2;

    // @Hint by SuperMonster003 on May 14, 2025.
    //  ! This method was corrected and improved by JetBrains AI Assistant.
    //  ! zh-CN: 此方法由 JetBrains AI Assistant 纠正并完善.
    /**
     * Pre-computes a feature-matching descriptor for an image. The pipeline:
     * <ol>
     *   <li>Optional color-space conversion controlled by {@code cvtColorFlag}</li>
     *   <li>Optional uniform scaling specified by {@code scale}</li>
     *   <li>Key-point detection and descriptor extraction according to {@code method}
     *       (SIFT / ORB)</li>
     *   <li>Wrap everything into a {@link FeatureMatchingDescriptor} so that it can be
     *       reused later by {@link #featureMatching}</li>
     * </ol></p>
     *
     * <p><b>zh-CN</b><br>
     *
     * 创建 (预计算) 特征匹配描述符.<br>
     * 典型流程: <pre>
     * 1. (可选) 颜色空间转换 {@code cvtColorFlag}
     * 2. (可选) 缩放 {@code scale}
     * 3. 依据 {@code method} (SIFT / ORB) 检测关键点并计算描述子
     * 4. 封装为 {@link FeatureMatchingDescriptor} 供后续匹配复用
     * </pre>
     *
     * <p>典型使用场景: 模板图/场景图分别调用一次当前方法, 随后在多次 {@link #featureMatching} 中复用, 以避免重复提特征带来的性能损耗.</p>
     *
     * @param src          The source Mat. The content will not be modified.<br>
     *                     zh-CN: 原始 {@link Mat}. 输入图像内容不会被修改.
     * @param cvtColorFlag OpenCV cvtColor flag; -1 means "no conversion".<br>
     *                     zh-CN: OpenCV 颜色空间转换标志; 传 -1 表示跳过转换.
     * @param scale        Scale factor; &gt;0 to resize, ≤0 or 1 to keep original size.<br>
     *                     zh-CN: 缩放因子; >0 表示按比例缩放, ≤0 或 1 表示保持原尺寸.
     * @param method       Extraction method, see {@code FEATURE_MATCHING_METHOD_*}.<br>
     *                     zh-CN: 特征提取方法; 取值见 {@code FEATURE_MATCHING_METHOD_*}.
     *
     * @return The generated descriptor. Release via
     *         {@link FeatureMatchingDescriptor#release()} or rely on GC.<br>
     *         zh-CN: 生成的 {@link FeatureMatchingDescriptor}; 仅当显式调用
     *         {@link FeatureMatchingDescriptor#release()} 或对象被 GC 时才会释放其底层资源.
     *
     * @throws IllegalArgumentException Thrown if {@code src} is empty or {@code method} is unsupported.<br>
     *                                  zh-CN: 当 {@code src} 为空或不支持的 {@code method} 时抛出.
     */
    @ScriptInterface
    public static FeatureMatchingDescriptor createFeatureMatchingDescriptor(
            @NonNull Mat src,
            int cvtColorFlag,
            float scale,
            int method
    ) {
        if (src.empty()) {
            throw new IllegalArgumentException("Input Mat is null or empty.");
        }

        // Color space conversion (zh-CN: 颜色空间转换)
        Mat processed = src;
        if (cvtColorFlag >= 0) {
            processed = new Mat();
            Imgproc.cvtColor(src, processed, cvtColorFlag);
        }

        // Scaling (zh-CN: 缩放)
        if (scale > 0f && Math.abs(scale - 1f) > 1e-3) {
            Size newSize = new Size(processed.cols() * scale, processed.rows() * scale);
            Mat resized = new Mat();
            Imgproc.resize(processed, resized, newSize);
            processed = resized;
        }

        // Choose detector (zh-CN: 选择算子)
        Feature2D detector;
        if (method == FEATURE_MATCHING_METHOD_SIFT) {
            detector = SIFT.create();
        } else if (method == FEATURE_MATCHING_METHOD_ORB) {
            detector = ORB.create(
                    /* nFeatures = */ 5000, /* 500 -> 5000, increase feature count (zh-CN: 增加特征数量) */
                    /* scaleFactor = */ 1.2f,
                    /* nLevels = */ 8,
                    /* edgeThreshold = */ 31,
                    /* firstLevel = */ 0,
                    /* WTA_K = */ 4, /* 2 -> 4, produces 256 bit descriptors, increases discrimination (zh-CN: 产生 256 bit 描述子, 增加区分度) */
                    /* scoreType = */ ORB.HARRIS_SCORE,
                    /* patchSize = */ 31,
                    /* fastThreshold = */ 20);
        } else {
            throw new IllegalArgumentException("Unsupported feature matching method: " + method);
        }

        // Calculate KeyPoint & Descriptor (zh-CN: 计算 KeyPoint & Descriptor)
        MatOfKeyPoint kps = new MatOfKeyPoint();
        Mat descriptors = new Mat();
        detector.detectAndCompute(processed, new Mat(), kps, descriptors);

        // Build reference image corner points (zh-CN: 构建参考图像的四角点)
        int w = src.width();
        int h = src.height();
        MatOfPoint2f corners = new MatOfPoint2f(
                new Point(0, 0),
                new Point(w, 0),
                new Point(0, h),
                new Point(w, h)
        );

        return new FeatureMatchingDescriptor(descriptors, kps, corners);
    }
    // @Hint by SuperMonster003 on May 14, 2025.
    //  ! This method was corrected and improved by JetBrains AI Assistant.
    //  ! zh-CN: 此方法由 JetBrains AI Assistant 纠正并完善.
    /**
     * It matches two pre-computed feature descriptors and optionally estimates a homography
     * to locate where the <i>object</i> image appears inside the <i>scene</i> image.<br>
     *
     * <p>Internal steps: <br>
     * <ol>
     *   <li>Create a suitable {@link org.opencv.features2d.DescriptorMatcher} based on {@code matcherType}</li> 
     *   <li>Perform KNN matching (k=2) and filter using Lowe's ratio test with threshold {@code threshold}</li>
     *   <li>If good matches ≥4, use {@code Calib3d.findHomography(..., RANSAC)} to further eliminate outliers
     *       while calculating projection (quad) of object image corners in scene image</li>
     *   <li>If caller sets {@code debugMatchesImagePath}, save visualization of match lines to that path</li>
     * </ol></p>
     * 
     * <p><b>zh-CN</b><br>
     * 
     * 在两张图的特征描述符之间执行匹配, 并 (可选) 估算单应矩阵以获得被 object 图在 scene 图中的投影区域.<br>
     * <p>内部流程: <br>
     * <ol>
     *   <li>根据 {@code matcherType} 创建合适的 {@link org.opencv.features2d.DescriptorMatcher}</li>
     *   <li>执行 KNN 匹配 (k=2) 并用 Lowe 比例测试 (阈值为 {@code threshold}) 过滤</li>
     *   <li>若 good matches ≥4, 则通过 {@code Calib3d.findHomography(..., RANSAC)} 进一步剔除离群点,
     *       同时计算 object 图四角在 scene 图中的投影 (quad)</li>
     *   <li>如调用者设置了 {@code debugMatchesImagePath}, 会将匹配连线可视化保存到该路径</li>
     * </ol></p>
     *
     * @param sceneDesc             Descriptor for the scene image.<br>
     *                              zh-CN: 目标 / 场景图的描述符.
     * @param objectDesc            Descriptor for the object image.<br>
     *                              zh-CN: 模板 / 待检测对象图的描述符.
     * @param matcherType           Type constant of DescriptorMatcher.<br>
     *                              zh-CN: {@link org.opencv.features2d.DescriptorMatcher} 的类型常量.
     * @param debugMatchesImagePath Optional path to save a debug image showing matches; {@code null} to skip.<br>
     *                              zh-CN: 可选调试路径; 非空时将匹配结果绘制到该文件.
     * @param threshold             Lowe ratio threshold (0,1); higher = looser filtering.<br>
     *                              zh-CN: Lowe Ratio 测试阈值 (0,1). 值越大匹配越宽松.
     *
     * @return {@link FeatureMatchingResult}: <br>
     *         • {@code getPoints()}      - Filtered matching point pairs (in scene coordinates)<br>
     *         • {@code getQuad()}        - 4-point quadrilateral if homography estimation succeeded, otherwise {@code null}<br>
     *         • {@code getMatches()}     - Rendered Mat if debug enabled, otherwise {@code null}<br>
     *         • zh-CN:<br>
     *         • {@code getPoints()}      - 过滤后的匹配点对 (scene 坐标系)<br>
     *         • {@code getQuad()}        - 若成功估算单应矩阵则为 4 点四边形, 否则为 {@code null}<br>
     *         • {@code getMatches()}     - 若开启调试则为绘制后的 Mat, 否则为 {@code null}<br>
     *
     * @throws IllegalStateException Thrown if descriptors are incompatible or already released.<br>
     *                               zh-CN: 当两侧描述子维度不兼容或资源已释放时抛出.
     */
    @ScriptInterface
    public static FeatureMatchingResult featureMatching(
            @NonNull FeatureMatchingDescriptor sceneDesc,
            @NonNull FeatureMatchingDescriptor objectDesc,
            int matcherType,
            @Nullable String debugMatchesImagePath,
            float threshold
    ) {
        DescriptorMatcher matcher = DescriptorMatcher.create(matcherType);
        List<MatOfDMatch> knnMatches = new ArrayList<>();

        // @Reference to Sakura小败狗 (https://blog.csdn.net/qq_42670220) by SuperMonster003 on Feb 26, 2024.
        //  ! https://blog.csdn.net/qq_42670220/article/details/108623752
        //  !
        //  ! knnMatch method finds the best matches in given feature descriptor sets.
        //  ! Using KNN-matching algorithm with k = 2, each match gets 2 closest descriptors, 
        //  ! keeps match as final when ratio of closest distance to second closest is greater than threshold.
        //  !
        //  ! zh-CN:
        //  !
        //  ! knnMatch 方法, 在给定特征描述集合中寻找最佳匹配.
        //  ! 使用 KNN-matching 算法, k = 2, 每个 match 得到 2 个最接近的 descriptor,
        //  ! 最接近距离和次接近距离的比值大于既定值时, 作为最终 match.
        matcher.knnMatch(objectDesc.getDescriptors(), sceneDesc.getDescriptors(), knnMatches, 2);

        // Lowe's ratio test
        // zh-CN: Lowe 比例测试
        List<DMatch> goodMatches = new ArrayList<>();
        for (MatOfDMatch matOfDMatch : knnMatches) {
            DMatch[] matches = matOfDMatch.toArray();
            if (matches.length < 2) {
                continue;
            }
            if (matches[0].distance < threshold * matches[1].distance) {
                goodMatches.add(matches[0]);
            }
        }

        // Draw matches visualization
        // zh-CN: 绘制匹配图
        Mat matchesImg = null;
        if (debugMatchesImagePath != null && !debugMatchesImagePath.isEmpty()) {
            matchesImg = new Mat();
            Features2d.drawMatches(
                    /* img1 = */ ImageUtils.to8UC3(objectDesc.getDescriptors()), objectDesc.getKeyPoint(),
                    /* img2 = */ ImageUtils.to8UC3(sceneDesc.getDescriptors()), sceneDesc.getKeyPoint(),
                    new MatOfDMatch(goodMatches.toArray(new DMatch[0])),
                    matchesImg);
            Imgcodecs.imwrite(debugMatchesImagePath, matchesImg);
        }

        // Extract matched object / scene coordinates
        // zh-CN: 提取配对的 object / scene 坐标
        List<Point> objPts = new ArrayList<>();
        List<Point> scenePts = new ArrayList<>();

        KeyPoint[] objKpsArr = objectDesc.getKeyPoint().toArray();
        KeyPoint[] sceneKpsArr = sceneDesc.getKeyPoint().toArray();

        for (DMatch gm : goodMatches) {
            objPts.add(objKpsArr[gm.queryIdx].pt);
            scenePts.add(sceneKpsArr[gm.trainIdx].pt);
        }

        // Calculate homography matrix (requires at least 4 matched coordinate pairs)
        // zh-CN: 计算单应矩阵 (需至少 4 个配对的坐标点)
        List<Point> quad = null;
        Log.d(TAG, "objPts: " + objPts.size() + ", scenePts: " + scenePts.size() + ", goodMatches: " + goodMatches.size());
        if (objPts.size() >= 4) {
            MatOfPoint2f objMat = new MatOfPoint2f();
            MatOfPoint2f sceneMat = new MatOfPoint2f();
            objMat.fromList(objPts);
            sceneMat.fromList(scenePts);

            Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, 3);

            if (!H.empty()) {
                MatOfPoint2f objCorners = objectDesc.getCorners();
                MatOfPoint2f sceneCorners = new MatOfPoint2f();
                Core.perspectiveTransform(objCorners, sceneCorners, H);
                quad = sortClockwise(sceneCorners.toList());
            }
        }

        return new FeatureMatchingResult(scenePts, quad, matchesImg);
    }

    // Sort four points clockwise as TL, TR, BL, BR
    // zh-CN: 四点按 TL, TR, BL, BR 顺时针排序
    private static List<Point> sortClockwise(List<Point> pts) {
        if (pts.size() != 4) return pts;
        // Use centroid as reference (zh-CN: 以质心为参考)
        double cx = pts.stream().mapToDouble(p -> p.x).average().orElse(0);
        double cy = pts.stream().mapToDouble(p -> p.y).average().orElse(0);
        // Sort by polar angle (counter-clockwise), then manually adjust to start from TL
        // zh-CN: 按极角排序 (逆时针), 然后手动调到 TL 开头
        Stream<Point> sorted = pts.stream()
                .sorted(Comparator.comparingDouble(a -> Math.atan2(a.y - cy, a.x - cx)));
        return List.of(sorted.toArray(Point[]::new));
    }

    private static void releaseFeatureMatchingDescriptor(long pointer) {
        Log.d(TAG, "Descriptor pointer to release: " + pointer);
        Mat mat = new org.autojs.autojs.core.opencv.Mat(pointer);
        mat.release();
    }

    /**
     * A reusable bundle of feature-extraction results for a single image.
     * zh-CN: 单张图片的特征提取结果集合.
     */
    public static class FeatureMatchingDescriptor implements MonitorResource {

        private final Mat mDescriptors;
        private long mNativePtr;
        private NativeObjectReference<MonitorResource> mRef;
        private final MatOfKeyPoint mKeyPoint;
        private final MatOfPoint2f mCorners;

        /**
         * Constructs a descriptor object. Usually created internally by
         * {@link #createFeatureMatchingDescriptor(Mat, int, float, int)}.
         * Encapsulates feature detection results (keypoints and descriptors) of a single image
         * for later matching operations.
         * <p>
         * <b>zh-CN</b><br>
         * 构造一个描述符对象. 通常由 {@link #createFeatureMatchingDescriptor(Mat, int, float, int)}
         * 内部调用创建. 封装单个图像的特征检测结果 (关键点和描述子) 以供后续匹配.
         *
         * @param descriptors Feature descriptor matrix computed by feature detector.<br>
         *                    zh-CN: 特征检测器计算出的描述子矩阵.
         * @param keyPoint    Detected key points in the image.<br>
         *                    zh-CN: 在图像中检测到的关键点集.
         * @param corners     Virtual corners of the image after scale, always [TL, TR, BL, BR]. Used for homography calculation.<br>
         *                    zh-CN: 缩放后图像的虚拟四角点坐标, 按 [左上,右上,左下,右下] 顺序, 用于单应矩阵计算.
         */
        public FeatureMatchingDescriptor(Mat descriptors, MatOfKeyPoint keyPoint, MatOfPoint2f corners) {
            mDescriptors = descriptors;
            mNativePtr = descriptors.nativeObj;
            mKeyPoint = keyPoint;
            mCorners = corners;
            Cleaner.instance.cleanup(this, SelfCleaner.INSTANCE);
        }

        public long getNativePtr() {
            return mNativePtr;
        }

        @Override
        public long getPointer() {
            return mNativePtr;
        }

        public MatOfKeyPoint getKeyPoint() {
            return mKeyPoint;
        }

        public Mat getDescriptors() {
            return mDescriptors;
        }

        public void release() {
            synchronized (this) {
                if (mNativePtr != 0L) {
                    SelfCleaner.INSTANCE.cleanup(mNativePtr);
                    mNativePtr = 0L;
                    if (mRef != null) {
                        mRef.pointer = 0L;
                    }
                }
            }
        }

        @Override
        public void setNativeObjectReference(NativeObjectReference<MonitorResource> ref) {
            mRef = ref;
        }

        public void setNativePtr(long nativePtr) {
            mNativePtr = nativePtr;
        }

        public MatOfPoint2f getCorners() {
            return mCorners;
        }

        public static class SelfCleaner implements ICleaner {

            public static SelfCleaner INSTANCE;

            static {
                INSTANCE = new SelfCleaner();
            }

            private SelfCleaner() {
                /* Empty body. */
            }

            @Override
            public void cleanup(long pointer) {
                releaseFeatureMatchingDescriptor(pointer);
            }
        }
    }

    /**
     * Immutable container holding the output of a single {@link #featureMatching} operation.
     * <p>
     * Depending on whether homography estimation succeeds, the {@code quad} field may be {@code null}.
     * The {@code matches} image is only generated when the caller passes a non-null debug path.
     * <p>
     * <b>zh-CN</b><br>
     * 表示一次 {@link #featureMatching} 调用结果的不可变对象.  
     * 若单应矩阵估算失败, {@code quad} 为 {@code null}.  
     * 若未开启 debug 输出, {@code matches} 为 {@code null}.
     */
    public static class FeatureMatchingResult {

        private final List<Point> mPoints;
        private final List<Point> mQuad;
        private final Mat mMatches;

        /**
         * @param pts     Inlier points in scene image after ratio test and RANSAC.<br>
         *                zh-CN: 终态内点 (场景座标系).
         * @param quad    Projected quadrilateral of the object image in scene image.<br>
         *                zh-CN: 物体投影四边形.
         * @param matches Debug visualization image.<br>
         *                zh-CN: 匹配连接图 (仅 debug 时生成).
         */
        public FeatureMatchingResult(@NonNull List<Point> pts, @Nullable List<Point> quad, @Nullable Mat matches) {
            mPoints = pts;
            mQuad = quad;
            mMatches = matches;
        }

        @Nullable
        public Mat getMatches() {
            return mMatches != null ? ImageUtils.to8UC3(mMatches) : null;
        }

        @Nullable
        public List<Point> getQuad() {
            return mQuad;
        }

        @NonNull
        public List<Point> getPoints() {
            return mPoints;
        }

        @Override
        public boolean equals(Object o) {
            return o == this || o instanceof FeatureMatchingResult featureMatchingResult
                                && Objects.equals(mPoints, featureMatchingResult.mPoints)
                                && Objects.equals(mMatches, featureMatchingResult.mMatches);
        }

        @Override
        public int hashCode() {
            return mPoints.hashCode() * 31 + (mMatches == null ? 0 : mMatches.hashCode());
        }

        @NonNull
        @Override
        public String toString() {
            return "FeatureMatchingResult(points=" + mPoints + ", matches=" + mMatches + ')';
        }

    }

}