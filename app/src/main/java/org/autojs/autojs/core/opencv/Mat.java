package org.autojs.autojs.core.opencv;

import androidx.annotation.NonNull;
import org.opencv.core.CvType;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class Mat extends org.opencv.core.Mat {

    private volatile boolean mReleased;

    private static final Method nClone = findMethod("n_clone", Long.TYPE);
    private static final Method nOnes = findMethod("n_ones", Integer.TYPE, Integer.TYPE, Integer.TYPE);
    private static final Method nRelease = findMethod("n_release", Long.TYPE);

    public Mat() {
        super();
    }

    public Mat(long addr) {
        super(addr);
    }

    public Mat(int rows, int cols, int type) {
        super(rows, cols, type);
    }

    public Mat(Size size, int type) {
        super(size, type);
    }

    public Mat(int rows, int cols, int type, Scalar s) {
        super(rows, cols, type, s);
    }

    public Mat(Size size, int type, Scalar s) {
        super(size, type, s);
    }

    public Mat(Mat m, Range rowRange, Range colRange) {
        super(m, rowRange, colRange);
    }

    public Mat(Mat m, Range rowRange) {
        super(m, rowRange);
    }

    public Mat(Mat m, Rect roi) {
        super(m, roi);
    }

    public Mat(int rows, int cols, int type, ByteBuffer data) {
        super(rows, cols, type, data);
    }

    public Mat(int rows, int cols, int type, ByteBuffer data, long step) {
        super(rows, cols, type, data, step);
    }

    public Mat(org.opencv.core.Mat opencvMat, Range rowRange) {
        super(opencvMat, rowRange);
    }

    public Mat(org.opencv.core.Mat opencvMat, Range rowRange, Range colRange) {
        super(opencvMat, rowRange, colRange);
    }

    public Mat(org.opencv.core.Mat opencvMat, Rect rect) {
        super(opencvMat, rect);
    }

    public Mat(org.opencv.core.Mat opencvMat, Range[] ranges) {
        super(opencvMat, ranges);
    }

    public Mat(int[] sizes, int type) {
        super(sizes, type);
    }

    public Mat(int[] sizes, int type, Scalar scalar) {
        super(sizes, type, scalar);
    }

    private static Method findMethod(final String s, final Class<?>... array) {
        try {
            final Method declaredMethod = org.opencv.core.Mat.class.getDeclaredMethod(s, array);
            declaredMethod.setAccessible(true);
            return declaredMethod;
        } catch (final NoSuchMethodException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public Mat convertTo(int type) {
        return convertTo(type, false);
    }

    public Mat convertTo(int type, boolean isRelease) {
        Mat tmp = new Mat();
        convertTo(tmp, type);
        if (type() == CvType.CV_8UC4 && type == CvType.CV_8UC3) {
            Imgproc.cvtColor(this, tmp, Imgproc.COLOR_BGRA2BGR);
        } else if (type() == CvType.CV_8UC3 && type == CvType.CV_8UC4) {
            Imgproc.cvtColor(this, tmp, Imgproc.COLOR_BGR2BGRA);
        }
        if (isRelease) {
            release();
        }
        return tmp;
    }

    private static <T> T invokeMethod(final Method method, final Object... array) {
        try {
            return (T) method.invoke(null, array);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Mat ones(final int size_width, final int size_height, final int type) {
        return new Mat(invokeMethod(nOnes, size_width, size_height, type));
    }

    @NonNull
    @Override
    public Mat clone() {
        return new Mat(n_clone(super.nativeObj));
    }

    protected long n_clone(long addr) {
        try {
            Object cloned = nClone.invoke(this, addr);
            if (cloned != null) {
                return (long) cloned;
            }
            throw new Exception("Cloned is null");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static Method n_release() {
        return nRelease;
    }

    public boolean isReleased() {
        return mReleased;
    }

    @Override
    public void release() {
        if (!mReleased) {
            synchronized (this) {
                if (!mReleased) {
                    super.release();
                    mReleased = true;
                }
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        /* Empty body. */
    }

}
