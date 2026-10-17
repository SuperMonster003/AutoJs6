package org.autojs.autojs.core.image;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.annotation.CodeAuthor;
import org.autojs.autojs.annotation.ScriptInterface;
import org.autojs.autojs.core.opencv.Mat;
import org.autojs.autojs.core.opencv.MatOfPoint;
import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Stardust on May 18, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
@SuppressWarnings("unused")
public class ColorFinder {

    /**
     * Reads ARGB pixels of an image without exposing its backing store.
     * zh-CN: 读取图像 ARGB 像素, 不暴露底层存储.
     */
    public interface PixelReader {
        int width();

        int height();

        int pixel(int x, int y);
    }

    // Below this many first-color candidates, checking every candidate directly is cheaper
    // than the extra full-image passes of the vectorized path pre-filter.
    // zh-CN: 首色候选点少于该数量时, 逐点直接校验比向量化路径预筛的整图扫描更划算.
    static final int PATH_PREFILTER_MIN_CANDIDATES = 4096;

    private final ScreenMetrics mScreenMetrics;

    public ColorFinder(ScreenMetrics screenMetrics) {
        mScreenMetrics = screenMetrics;
    }

    @Nullable
    @ScriptInterface
    public Point findPointByColor(ImageWrapper imageWrapper, int color) {
        return findPointByColor(imageWrapper, color, null);
    }

    @Nullable
    @ScriptInterface
    public Point findPointByColor(ImageWrapper imageWrapper, int color, Rect region) {
        return findPointByColor(imageWrapper, color, 0, region);
    }

    @Nullable
    @ScriptInterface
    public Point findPointByColor(ImageWrapper imageWrapper, int color, int threshold) {
        return findPointByColor(imageWrapper, color, threshold, null);
    }

    @Nullable
    @ScriptInterface
    public Point findPointByColor(ImageWrapper image, int color, int threshold, Rect rect) {
        MatOfPoint matOfPoint;
        try {
            matOfPoint = findColorInner(image, color, threshold, rect);
        } finally {
            image.shoot();
        }
        if (matOfPoint == null) {
            return null;
        }
        try {
            // Read only the first candidate instead of materializing every point.
            // zh-CN: 只读取第一个候选点, 避免生成全部点对象.
            int[] xy = new int[2];
            matOfPoint.get(0, 0, xy);
            Point point = new Point(xy[0], xy[1]);
            if (rect != null) {
                point.x = mScreenMetrics.scaleX((int) (point.x + rect.x));
                point.y = mScreenMetrics.scaleY((int) (point.y + rect.y));
            }
            return point;
        } finally {
            OpenCVHelper.release(matOfPoint);
        }
    }

    @ScriptInterface
    public Point[] findPointsByColor(ImageWrapper image, int color, int threshold, Rect rect) {
        MatOfPoint matOfPoint;
        try {
            matOfPoint = findColorInner(image, color, threshold, rect);
        } finally {
            image.shoot();
        }
        if (matOfPoint == null) {
            return new Point[0];
        }
        Point[] points;
        try {
            points = matOfPoint.toArray();
        } finally {
            OpenCVHelper.release(matOfPoint);
        }
        if (rect != null) {
            for (Point point : points) {
                point.x = mScreenMetrics.scaleX((int) (point.x + rect.x));
                point.y = mScreenMetrics.scaleY((int) (point.y + rect.y));
            }
        }
        return points;
    }

    /**
     * Counts pixels matching the color within the optional region, without materializing the points.
     * zh-CN: 统计区域内匹配颜色的像素数量, 不生成点数组.
     */
    @ScriptInterface
    public int countPointsByColor(ImageWrapper image, int color, int threshold, @Nullable Rect rect) {
        try {
            return countPointsByColor(image.getMat(), color, threshold, rect);
        } finally {
            image.shoot();
        }
    }

    public static int countPointsByColor(Mat source, int color, int threshold, @Nullable Rect rect) {
        Mat mask = createColorMask(source, color, threshold, rect, true);
        try {
            return Core.countNonZero(mask);
        } finally {
            OpenCVHelper.release(mask);
        }
    }

    @Nullable
    private MatOfPoint findColorInner(ImageWrapper image, int color, int threshold, Rect rect) {
        return findColorInner(image.getMat(), color, threshold, rect);
    }

    @Nullable
    private static MatOfPoint findColorInner(Mat source, int color, int threshold, Rect rect) {
        Mat bi = createColorMask(source, color, threshold, rect, true);
        Mat nonZeroPos = new Mat();
        try {
            Core.findNonZero(bi, nonZeroPos);
            if (nonZeroPos.rows() == 0 || nonZeroPos.cols() == 0) {
                return null;
            }
            return OpenCVHelper.newMatOfPoint(nonZeroPos);
        } finally {
            OpenCVHelper.release(bi);
            OpenCVHelper.release(nonZeroPos);
        }
    }

    /**
     * Builds a CV_8UC1 mask of pixels whose RGB channels are all within {@code threshold} of {@code color}.
     * The mask covers {@code rect} when given, otherwise the whole source.
     * zh-CN: 生成 RGB 各通道均在 {@code color} 的 {@code threshold} 范围内的像素掩码 (CV_8UC1),
     * 掩码覆盖 {@code rect} 区域 (若给定), 否则覆盖整图.
     */
    @NonNull
    private static Mat createColorMask(Mat source, int color, int threshold, @Nullable Rect rect, boolean opaqueOnly) {
        Mat mask = new Mat();
        double alphaLower = opaqueOnly ? 255 : 0;
        Scalar lowerBound = new Scalar(Color.red(color) - threshold, Color.green(color) - threshold,
                Color.blue(color) - threshold, alphaLower);
        Scalar upperBound = new Scalar(Color.red(color) + threshold, Color.green(color) + threshold,
                Color.blue(color) + threshold, 255);
        if (rect != null) {
            Mat m = new Mat(source, rect);
            try {
                Core.inRange(m, lowerBound, upperBound, mask);
            } finally {
                OpenCVHelper.release(m);
            }
        } else {
            Core.inRange(source, lowerBound, upperBound, mask);
        }
        return mask;
    }

    /** Uses pixel coordinates for callers which already own the bitmap and Mat, without a Rhino runtime. */
    @Nullable
    public static Point findPointByColors(Bitmap bitmap, Mat source, int color, int threshold, Rect rect, int[] points) {
        List<Point> result = findPointsByColorsInternal(source, color, threshold, rect, points, readerOf(bitmap), 1);
        return result.isEmpty() ? null : result.get(0);
    }

    /** All matches in raw image coordinates, without a ScriptRuntime or screen-metric scaling. */
    public static Point[] findPointsByColors(Bitmap bitmap, Mat source, int color, int threshold, Rect rect, int[] points, int maxResults) {
        return findPointsByColorsInternal(source, color, threshold, rect, points, readerOf(bitmap), maxResults, true).toArray(new Point[0]);
    }

    @Nullable
    @ScriptInterface
    public Point findPointByColors(ImageWrapper image, int firstColor, int threshold, Rect rect, int[] points) {
        List<Point> result;
        try {
            result = findPointsByColorsInternal(image.getMat(), firstColor, threshold, rect, points, readerOf(image), 1);
        } finally {
            image.shoot();
        }
        if (result.isEmpty()) {
            return null;
        }
        return scaleIfNeeded(result.get(0), rect);
    }

    @ScriptInterface
    public Point[] findPointsByColors(ImageWrapper image, int firstColor, int threshold, Rect rect, int[] points) {
        List<Point> result;
        try {
            result = findPointsByColorsInternal(image.getMat(), firstColor, threshold, rect, points, readerOf(image), Integer.MAX_VALUE);
        } finally {
            image.shoot();
        }
        Point[] array = result.toArray(new Point[0]);
        for (Point point : array) {
            scaleIfNeeded(point, rect);
        }
        return array;
    }

    @Deprecated
    @ScriptInterface
    @CodeAuthor(name = "LYS86", homepage = "https://github.com/LYS86")
    public Point[] findAllMultiColors(ImageWrapper image, int firstColor, int threshold, Rect rect, int[] points) {
        return findPointsByColors(image, firstColor, threshold, rect, points);
    }

    public boolean detectMultiColors(ImageWrapper image, int x, int y, int firstColor, int threshold, Rect region, int[] points) {
        int pixel = image.pixel(x, y);
        ColorDetector colorDetector = new ColorDetector.DifferenceDetector(firstColor, threshold);
        if (!colorDetector.detectColor(Color.red(pixel), Color.green(pixel), Color.blue(pixel))) {
            return false;
        }
        return checksPath(readerOf(image), x, y, threshold, points, buildPathDetectors(points, threshold));
    }

    private Point scaleIfNeeded(Point point, @Nullable Rect rect) {
        // Keep the historical behavior: results are scaled by the screen metrics only when a region was given.
        // zh-CN: 保持历史行为, 仅在指定区域时按屏幕度量缩放结果.
        if (rect != null) {
            point.x = mScreenMetrics.scaleX((int) point.x);
            point.y = mScreenMetrics.scaleY((int) point.y);
        }
        return point;
    }

    /**
     * Finds up to {@code limit} points where the first color matches (per-channel box threshold) and every
     * path offset matches its color (mean absolute difference threshold), in raw pixel coordinates.
     * <p>
     * The first-color candidates come from an OpenCV mask. When there are many candidates, the mask is
     * narrowed with shifted per-path masks (a superset of the exact criterion), and the survivors are
     * verified pixel by pixel, so the result never differs from the plain per-candidate check.
     * <p>
     * zh-CN:
     * 查找最多 {@code limit} 个满足首色 (各通道阈值) 且所有路径偏移点均满足对应颜色 (平均绝对差阈值) 的点, 返回原始像素坐标.
     * 首色候选来自 OpenCV 掩码; 候选很多时用按路径偏移的掩码 (精确条件的超集) 缩小候选集, 再逐点精确校验, 结果与逐点校验完全一致.
     */
    @NonNull
    static List<Point> findPointsByColorsInternal(Mat source, int firstColor, int threshold, @Nullable Rect rect, int[] points, PixelReader reader, int limit) {
        return findPointsByColorsInternal(source, firstColor, threshold, rect, points, reader, limit, false);
    }

    private static List<Point> findPointsByColorsInternal(Mat source, int firstColor, int threshold, @Nullable Rect rect, int[] points, PixelReader reader, int limit, boolean requireComplete) {
        if (points.length % 3 != 0) {
            throw new IllegalArgumentException("Color paths must be flat triples of [x, y, color], but got " + points.length + " values");
        }
        if (limit <= 0) {
            return Collections.emptyList();
        }
        int regionX = rect != null ? rect.x : 0;
        int regionY = rect != null ? rect.y : 0;
        Mat mask = createColorMask(source, firstColor, threshold, rect, true);
        Mat nonZero = new Mat();
        try {
            int candidateCount = Core.countNonZero(mask);
            if (candidateCount == 0) {
                return Collections.emptyList();
            }
            if (requireComplete && points.length == 0 && candidateCount > limit) {
                throw new IllegalArgumentException("JSON color results exceed " + limit + " points; choose a smaller region");
            }
            if (points.length > 0 && candidateCount >= PATH_PREFILTER_MIN_CANDIDATES) {
                narrowCandidatesByPaths(source, mask, regionX, regionY, reader.width(), reader.height(), threshold, points);
            }
            Core.findNonZero(mask, nonZero);
            int total = nonZero.rows() * nonZero.cols();
            if (total == 0) {
                return Collections.emptyList();
            }
            int[] coordinates = new int[total * 2];
            nonZero.get(0, 0, coordinates);
            ColorDetector[] detectors = buildPathDetectors(points, threshold);
            List<Point> result = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                int x = coordinates[i * 2] + regionX;
                int y = coordinates[i * 2 + 1] + regionY;
                if (checksPath(reader, x, y, threshold, points, detectors)) {
                    result.add(new Point(x, y));
                    if (requireComplete && result.size() > limit) {
                        throw new IllegalArgumentException("JSON color results exceed " + limit + " points; choose a smaller region");
                    }
                    if (!requireComplete && result.size() >= limit) {
                        break;
                    }
                }
            }
            return result;
        } finally {
            OpenCVHelper.release(mask);
            OpenCVHelper.release(nonZero);
        }
    }

    private static void narrowCandidatesByPaths(Mat source, Mat mask, int regionX, int regionY, int imageWidth, int imageHeight, int threshold, int[] points) {
        int regionWidth = mask.cols();
        int regionHeight = mask.rows();
        for (int i = 0; i < points.length; i += 3) {
            int dx = points[i];
            int dy = points[i + 1];
            int color = points[i + 2];
            int[] overlap = shiftedOverlap(regionX, regionY, regionWidth, regionHeight, imageWidth, imageHeight, dx, dy);
            if (overlap == null) {
                // Every candidate would leave the image at this offset.
                // zh-CN: 该偏移下所有候选点都会越出图像.
                mask.setTo(new Scalar(0));
                return;
            }
            int x0 = overlap[0];
            int y0 = overlap[1];
            int x1 = overlap[2];
            int y1 = overlap[3];

            // The exact check accepts (|dr| + |dg| + |db|) / 3 <= threshold, which implies every channel
            // difference is at most 3 * threshold; alpha is ignored by the exact check.
            // zh-CN: 精确校验条件 (|dr| + |dg| + |db|) / 3 <= threshold 蕴含各通道差不超过 3 * threshold; 精确校验忽略 alpha.
            int boxThreshold = (int) Math.min(255L, 3L * threshold);
            Mat pathMask = createColorMask(source, color, boxThreshold, null, false);
            org.opencv.core.Mat maskPart = null;
            org.opencv.core.Mat pathPart = null;
            try {
                maskPart = mask.submat(y0, y1, x0, x1);
                pathPart = pathMask.submat(y0 + regionY + dy, y1 + regionY + dy, x0 + regionX + dx, x1 + regionX + dx);
                Core.bitwise_and(maskPart, pathPart, maskPart);
            } finally {
                OpenCVHelper.release(maskPart);
                OpenCVHelper.release(pathPart);
                OpenCVHelper.release(pathMask);
            }
            clearOutside(mask, x0, y0, x1, y1);
        }
    }

    /**
     * Computes, in mask coordinates, the sub-rectangle [x0, x1) x [y0, y1) of the candidate region whose
     * points stay inside the image after being shifted by (dx, dy). Returns null when it is empty.
     * zh-CN: 计算候选区域中, 按 (dx, dy) 偏移后仍位于图像内的子矩形 [x0, x1) x [y0, y1) (掩码坐标); 为空时返回 null.
     */
    @Nullable
    static int[] shiftedOverlap(int regionX, int regionY, int regionWidth, int regionHeight, int imageWidth, int imageHeight, int dx, int dy) {
        // Negate in long arithmetic: -Integer.MIN_VALUE overflows in int.
        // zh-CN: 使用 long 取负: int 的 -Integer.MIN_VALUE 会溢出.
        long startX = Math.max(regionX, -(long) dx);
        long endX = Math.min((long) regionX + regionWidth, (long) imageWidth - dx);
        long startY = Math.max(regionY, -(long) dy);
        long endY = Math.min((long) regionY + regionHeight, (long) imageHeight - dy);
        if (startX >= endX || startY >= endY) {
            return null;
        }
        return new int[]{(int) (startX - regionX), (int) (startY - regionY), (int) (endX - regionX), (int) (endY - regionY)};
    }

    private static void clearOutside(Mat mask, int x0, int y0, int x1, int y1) {
        Scalar zero = new Scalar(0);
        clearRows(mask, 0, y0, 0, mask.cols(), zero);
        clearRows(mask, y1, mask.rows(), 0, mask.cols(), zero);
        clearRows(mask, y0, y1, 0, x0, zero);
        clearRows(mask, y0, y1, x1, mask.cols(), zero);
    }

    private static void clearRows(Mat mask, int rowStart, int rowEnd, int colStart, int colEnd, Scalar value) {
        if (rowStart >= rowEnd || colStart >= colEnd) {
            return;
        }
        org.opencv.core.Mat part = mask.submat(new Range(rowStart, rowEnd), new Range(colStart, colEnd));
        try {
            part.setTo(value);
        } finally {
            OpenCVHelper.release(part);
        }
    }

    private static ColorDetector[] buildPathDetectors(int[] points, int threshold) {
        ColorDetector[] detectors = new ColorDetector[points.length / 3];
        for (int i = 0; i < detectors.length; i++) {
            detectors[i] = new ColorDetector.DifferenceDetector(points[i * 3 + 2], threshold);
        }
        return detectors;
    }

    private static boolean checksPath(PixelReader reader, int startX, int startY, int threshold, int[] points, ColorDetector[] detectors) {
        int width = reader.width();
        int height = reader.height();
        for (int i = 0; i < detectors.length; i++) {
            long x = (long) startX + points[i * 3];
            long y = (long) startY + points[i * 3 + 1];
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return false;
            }
            int c = reader.pixel((int) x, (int) y);
            if (!detectors[i].detectColor(Color.red(c), Color.green(c), Color.blue(c))) {
                return false;
            }
        }
        return true;
    }

    private static PixelReader readerOf(ImageWrapper image) {
        return new PixelReader() {
            @Override
            public int width() {
                return image.getWidth();
            }

            @Override
            public int height() {
                return image.getHeight();
            }

            @Override
            public int pixel(int x, int y) {
                return image.pixel(x, y);
            }
        };
    }

    private static PixelReader readerOf(Bitmap bitmap) {
        return new PixelReader() {
            @Override
            public int width() {
                return bitmap.getWidth();
            }

            @Override
            public int height() {
                return bitmap.getHeight();
            }

            @Override
            public int pixel(int x, int y) {
                return bitmap.getPixel(x, y);
            }
        };
    }

    @Nullable
    @Deprecated
    @ScriptInterface
    public Point findColorEquals(ImageWrapper imageWrapper, int color) {
        return findColorEquals(imageWrapper, color, null);
    }

    @Nullable
    @Deprecated
    @ScriptInterface
    public Point findColorEquals(ImageWrapper imageWrapper, int color, Rect region) {
        return findColor(imageWrapper, color, 0, region);
    }

    @Nullable
    @Deprecated
    @ScriptInterface
    public Point findColor(ImageWrapper imageWrapper, int color, int threshold) {
        return findPointByColor(imageWrapper, color, threshold);
    }

    @Nullable
    @Deprecated
    @ScriptInterface
    public Point findColor(ImageWrapper image, int color, int threshold, Rect rect) {
        return findPointByColor(image, color, threshold, rect);
    }

    @Deprecated
    @ScriptInterface
    public Point[] findAllPointsForColor(ImageWrapper image, int color, int threshold, Rect rect) {
        return findPointsByColor(image, color, threshold, rect);
    }

    @Nullable
    @Deprecated
    @ScriptInterface
    public Point findMultiColors(ImageWrapper image, int firstColor, int threshold, Rect rect, int[] points) {
        return findPointByColors(image, firstColor, threshold, rect, points);
    }

}
