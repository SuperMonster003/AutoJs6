package org.autojs.autojs.core.image;

import android.graphics.Color;

import org.autojs.autojs.annotation.CodeAuthor;
import org.autojs.autojs.annotation.ScriptInterface;
import org.autojs.autojs.core.opencv.Mat;
import org.autojs.autojs.core.opencv.MatOfPoint;
import org.autojs.autojs.core.opencv.OpenCVHelper;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * Created by Stardust on 2017/5/18.
 */
@SuppressWarnings("unused")
public class ColorFinder {

    private final ScreenMetrics mScreenMetrics;

    public ColorFinder(ScreenMetrics screenMetrics) {
        mScreenMetrics = screenMetrics;
    }

    @ScriptInterface
    public Point findPointByColor(ImageWrapper imageWrapper, int color) {
        return findPointByColor(imageWrapper, color, null);
    }

    @ScriptInterface
    public Point findPointByColor(ImageWrapper imageWrapper, int color, Rect region) {
        return findPointByColor(imageWrapper, color, 0, region);
    }

    @ScriptInterface
    public Point findPointByColor(ImageWrapper imageWrapper, int color, int threshold) {
        return findPointByColor(imageWrapper, color, threshold, null);
    }

    @ScriptInterface
    public Point findPointByColor(ImageWrapper image, int color, int threshold, Rect rect) {
        MatOfPoint matOfPoint = findColorInner(image, color, threshold, rect);
        image.shoot();
        if (matOfPoint == null) {
            return null;
        }
        Point point = matOfPoint.toArray()[0];
        if (rect != null) {
            point.x = mScreenMetrics.scaleX((int) (point.x + rect.x));
            point.y = mScreenMetrics.scaleX((int) (point.y + rect.y));
        }
        OpenCVHelper.release(matOfPoint);
        return point;
    }

    @ScriptInterface
    public Point[] findPointsByColor(ImageWrapper image, int color, int threshold, Rect rect) {
        MatOfPoint matOfPoint = findColorInner(image, color, threshold, rect);
        image.shoot();
        if (matOfPoint == null) {
            return new Point[0];
        }
        Point[] points = matOfPoint.toArray();
        OpenCVHelper.release(matOfPoint);
        if (rect != null) {
            for (Point point : points) {
                point.x = mScreenMetrics.scaleX((int) (point.x + rect.x));
                point.y = mScreenMetrics.scaleX((int) (point.y + rect.y));
            }
        }
        return points;
    }

    private MatOfPoint findColorInner(ImageWrapper image, int color, int threshold, Rect rect) {
        Mat bi = new Mat();
        Scalar lowerBound = new Scalar(Color.red(color) - threshold, Color.green(color) - threshold,
                Color.blue(color) - threshold, 255);
        Scalar upperBound = new Scalar(Color.red(color) + threshold, Color.green(color) + threshold,
                Color.blue(color) + threshold, 255);
        if (rect != null) {
            Mat m = new Mat(image.getMat(), rect);
            Core.inRange(m, lowerBound, upperBound, bi);
            OpenCVHelper.release(m);
        } else {
            Core.inRange(image.getMat(), lowerBound, upperBound, bi);
        }
        Mat nonZeroPos = new Mat();
        Core.findNonZero(bi, nonZeroPos);
        MatOfPoint result;
        if (nonZeroPos.rows() == 0 || nonZeroPos.cols() == 0) {
            result = null;
        } else {
            result = OpenCVHelper.newMatOfPoint(nonZeroPos);
        }
        OpenCVHelper.release(bi);
        OpenCVHelper.release(nonZeroPos);
        return result;
    }

    @ScriptInterface
    public Point findPointByColors(ImageWrapper image, int firstColor, int threshold, Rect rect, int[] points) {
        Point[] firstPoints = findPointsByColor(image, firstColor, threshold, rect);
        image.shoot();
        return Arrays.stream(firstPoints)
                .filter(Objects::nonNull)
                .filter(firstPoint -> checksPath(image, firstPoint, threshold, points))
                .findFirst()
                .orElse(null);
    }

    @ScriptInterface
    public Point[] findPointsByColors(ImageWrapper image, int firstColor, int threshold, Rect rect, int[] points) {
        Point[] firstPoints = findPointsByColor(image, firstColor, threshold, rect);
        image.shoot();
        return Arrays.stream(firstPoints)
                .filter(Objects::nonNull)
                .filter(firstPoint -> checksPath(image, firstPoint, threshold, points))
                .toArray(Point[]::new);
    }

    @Deprecated
    @ScriptInterface
    @SuppressWarnings("deprecation")
    @CodeAuthor(name = "LYS86", homepage = "https://github.com/LYS86")
    public Point[] findAllMultiColors(ImageWrapper image, int firstColor, int threshold, Rect rect, int[] points) {
        Point[] firstPoints = findAllPointsForColor(image, firstColor, threshold, rect);
        List<Point> resultPoints = new ArrayList<>();
        for (Point firstPoint : firstPoints) {
            if (firstPoint != null) {
                if (checksPath(image, firstPoint, threshold, points)) {
                    resultPoints.add(firstPoint);
                }
            }
        }
        image.shoot();
        return resultPoints.toArray(new Point[0]);
    }

    private boolean checksPath(ImageWrapper image, Point startingPoint, int threshold, int[] points) {
        for (int i = 0; i < points.length; i += 3) {
            int x = points[i];
            int y = points[i + 1];
            int color = points[i + 2];
            ColorDetector colorDetector = new ColorDetector.DifferenceDetector(color, threshold);
            x += startingPoint.x;
            y += startingPoint.y;
            if (x >= image.getWidth() || y >= image.getHeight() || x < 0 || y < 0) {
                return false;
            }
            int c = image.pixel(x, y);
            if (!colorDetector.detectColor(Color.red(c), Color.green(c), Color.blue(c))) {
                return false;
            }
        }
        return true;
    }

    @Deprecated
    @ScriptInterface
    @SuppressWarnings("deprecation")
    public Point findColorEquals(ImageWrapper imageWrapper, int color) {
        return findColorEquals(imageWrapper, color, null);
    }

    @Deprecated
    @ScriptInterface
    @SuppressWarnings("deprecation")
    public Point findColorEquals(ImageWrapper imageWrapper, int color, Rect region) {
        return findColor(imageWrapper, color, 0, region);
    }

    @Deprecated
    @ScriptInterface
    public Point findColor(ImageWrapper imageWrapper, int color, int threshold) {
        return findPointByColor(imageWrapper, color, threshold);
    }

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

    @Deprecated
    @ScriptInterface
    public Point findMultiColors(ImageWrapper image, int firstColor, int threshold, Rect rect, int[] points) {
        return findPointByColors(image, firstColor, threshold, rect, points);
    }

}