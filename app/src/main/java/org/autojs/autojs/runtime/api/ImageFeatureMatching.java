package org.autojs.autojs.runtime.api;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.annotation.ScriptInterface;
import org.autojs.autojs.core.cleaner.Cleaner;
import org.autojs.autojs.core.cleaner.ICleaner;
import org.autojs.autojs.core.ref.MonitorResource;
import org.autojs.autojs.core.ref.NativeObjectReference;
import org.opencv.calib3d.Calib3d;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

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

    // FIXME by SuperMonster003 on Nov 23, 2024.
    //  ! This function needs to be corrected or improved.
    //  ! zh-CN: 此函数功能需纠正或完善.
    public static FeatureMatchingDescriptor createFeatureMatchingDescriptor(Mat mat, int cvtColorFlag, float scale, int method) {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Input Mat cannot be null or empty");
        }

        // Add this part to ensure the image is loaded correctly
        if (mat.empty()) {
            throw new RuntimeException("Failed to load image. The image file might be missing or the path is incorrect.");
        }

        // Convert color if needed
        Mat convertedMat = new Mat();
        if (cvtColorFlag != -1) {
            Imgproc.cvtColor(mat, convertedMat, cvtColorFlag);
        } else {
            convertedMat = mat.clone();
        }

        // Resize the image if scaling is required
        Mat scaledMat = new Mat();
        if (scale > 0 && scale != 1.0f) {
            Imgproc.resize(convertedMat, scaledMat, new Size(mat.cols() * scale, mat.rows() * scale));
        } else {
            scaledMat = convertedMat.clone();
        }

        // Select feature detector and descriptor extractor based on method
        Feature2D detector;
        if (method == FEATURE_MATCHING_METHOD_SIFT) {
            detector = SIFT.create();
        } else if (method == FEATURE_MATCHING_METHOD_ORB) {
            detector = ORB.create();
        } else {
            throw new IllegalArgumentException("Unsupported feature matching method: " + method);
        }

        // Detect keypoints and compute descriptors
        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
        Mat descriptors = new Mat();
        detector.detect(scaledMat, keyPoints);
        detector.compute(scaledMat, keyPoints, descriptors);

        return new FeatureMatchingDescriptor(descriptors, keyPoints);
    }

    // FIXME by SuperMonster003 on Nov 23, 2024.
    //  ! This function needs to be corrected or improved.
    //  ! zh-CN: 此函数功能需纠正或完善.
    @ScriptInterface
    public static FeatureMatchingResult featureMatching(
            FeatureMatchingDescriptor sceneDescriptor,
            FeatureMatchingDescriptor objectDescriptor,
            int matcherType,
            @Nullable String matchesImageToDrawPath,
            float threshold
    ) {
        // Step 1: Extract key points and descriptors
        Mat sceneDescriptors = sceneDescriptor.getDescriptors();
        Mat objectDescriptors = objectDescriptor.getDescriptors();
        MatOfKeyPoint sceneKeyPoints = sceneDescriptor.getKeyPoint();
        MatOfKeyPoint objectKeyPoints = objectDescriptor.getKeyPoint();

        List<MatOfDMatch> knnMatches = new LinkedList<>();
        DescriptorMatcher matcher = DescriptorMatcher.create(matcherType);

        // @Reference to Sakura小败狗 (https://blog.csdn.net/qq_42670220) by SuperMonster003 on Feb 26, 2024.
        //  ! https://blog.csdn.net/qq_42670220/article/details/108623752
        //  !
        //  ! knnMatch 方法, 在给定特征描述集合中寻找最佳匹配.
        //  ! 使用 KNN-matching 算法, k = 2, 每个 match 得到 2 个最接近的 descriptor,
        //  ! 最接近距离和次接近距离的比值大于既定值时, 作为最终 match.
        matcher.knnMatch(sceneDescriptors, objectDescriptors, knnMatches, 2);

        LinkedList<DMatch> niceMatches = new LinkedList<>();

        for (MatOfDMatch matOfDMatch : knnMatches) {
            DMatch[] matches = matOfDMatch.toArray();
            if (matches[0].distance < threshold * matches[1].distance) {
                niceMatches.add(matches[0]);
            }
        }

        List<KeyPoint> sceneKeyPointsList = sceneKeyPoints.toList();
        List<Point> scenePoints = new ArrayList<>(niceMatches.size());
        for (DMatch match : niceMatches) {
            scenePoints.add(sceneKeyPointsList.get(match.queryIdx).pt);
        }

        // Optional: Draw matches
        // if (matchesImageToDrawPath != null && !matchesImageToDrawPath.isEmpty()) {
        //     Mat imgMatches = new Mat();
        //     Features2d.drawMatches(new Mat(), sceneKeyPoints, new Mat(), objectKeyPoints, new MatOfDMatch(niceMatches.toArray(new DMatch[0])), imgMatches);
        //     Imgcodecs.imwrite(matchesImageToDrawPath, imgMatches);
        // }

        // Step 2: Compute homography if any good matches found
        Mat homography = null;
        if (!niceMatches.isEmpty()) {
            MatOfPoint2f obj = new MatOfPoint2f();
            MatOfPoint2f scene = new MatOfPoint2f();

            List<Point> objectPoints = new ArrayList<>(niceMatches.size());
            for (DMatch match : niceMatches) {
                objectPoints.add(sceneKeyPointsList.get(match.trainIdx).pt);
            }

            obj.fromList(objectPoints);
            scene.fromList(scenePoints);

            homography = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 3);
        }

        return new FeatureMatchingResult(scenePoints, homography);
    }

    private static void releaseFeatureMatchingDescriptor(long pointer) {
        Log.d(TAG, "Descriptor pointer to release: " + pointer);
        Mat mat = new org.autojs.autojs.core.opencv.Mat(pointer);
        mat.release();
    }

    public static class FeatureMatchingDescriptor implements MonitorResource {

        private final Mat mDescriptors;
        private long mNativePtr;
        private NativeObjectReference<MonitorResource> mRef;
        private final MatOfKeyPoint mKeyPoint;

        public FeatureMatchingDescriptor(Mat descriptors, MatOfKeyPoint keyPoint) {
            mDescriptors = descriptors;
            mNativePtr = descriptors.nativeObj;
            mKeyPoint = keyPoint;
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

    public static class FeatureMatchingResult {

        private final List<Point> mPoints;
        private final Mat mMatches;

        public FeatureMatchingResult(List<Point> points, @Nullable Mat matches) {
            mPoints = points;
            mMatches = matches;
        }

        public Mat getMatches() {
            return mMatches;
        }

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