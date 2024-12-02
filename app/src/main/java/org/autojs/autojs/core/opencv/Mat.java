package org.autojs.autojs.core.opencv;

import androidx.annotation.NonNull;
import org.autojs.autojs.core.cleaner.Cleaner;
import org.autojs.autojs.core.cleaner.ICleaner;
import org.autojs.autojs.core.ref.MonitorResource;
import org.autojs.autojs.core.ref.NativeObjectReference;
import org.opencv.core.CvType;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class Mat extends org.opencv.core.Mat implements MonitorResource {

    public static final ICleaner MAT_CLEANER = new MatCleaner(null);

    private volatile boolean mReleased;
    
    private static final Method nClone = findMethod("n_clone", Long.TYPE);
    private static final Method nOnes = findMethod("n_ones", Integer.TYPE, Integer.TYPE, Integer.TYPE);
    private static final Method nRelease = findMethod("n_release", Long.TYPE);
    
    private NativeObjectReference<MonitorResource> mReference;

    public Mat() {
        super();
        init();
    }

    public Mat(long addr) {
        super(addr);
        init();
    }

    public Mat(int rows, int cols, int type) {
        super(rows, cols, type);
        init();
    }

    public Mat(Size size, int type) {
        super(size, type);
        init();
    }

    public Mat(int rows, int cols, int type, Scalar s) {
        super(rows, cols, type, s);
        init();
    }

    public Mat(Size size, int type, Scalar s) {
        super(size, type, s);
        init();
    }

    public Mat(Mat m, Range rowRange, Range colRange) {
        super(m, rowRange, colRange);
        init();
    }

    public Mat(Mat m, Range rowRange) {
        super(m, rowRange);
        init();
    }

    public Mat(Mat m, Rect roi) {
        super(m, roi);
        init();
    }

    public Mat(int rows, int cols, int type, ByteBuffer data) {
        super(rows, cols, type, data);
        init();
    }

    public Mat(int rows, int cols, int type, ByteBuffer data, long step) {
        super(rows, cols, type, data, step);
        init();
    }

    public Mat(org.opencv.core.Mat opencvMat, Range rowRange) {
        super(opencvMat, rowRange);
        init();
    }

    public Mat(org.opencv.core.Mat opencvMat, Range rowRange, Range colRange) {
        super(opencvMat, rowRange, colRange);
        init();
    }

    public Mat(org.opencv.core.Mat opencvMat, Rect rect) {
        super(opencvMat, rect);
        init();
    }

    public Mat(org.opencv.core.Mat opencvMat, Range[] ranges) {
        super(opencvMat, ranges);
        init();
    }

    public Mat(int[] sizes, int type) {
        super(sizes, type);
        init();
    }

    public Mat(int[] sizes, int type, Scalar scalar) {
        super(sizes, type, scalar);
        init();
    }

    private void init() {
        Cleaner.instance.cleanup(this, MAT_CLEANER);
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

    @Override
    public long getPointer() {
        return super.nativeObj;
    }

    @Override
    public void setNativeObjectReference(final NativeObjectReference<MonitorResource> reference) {
        mReference = reference;
    }

    public String getPointerString() {
        return Long.toString(super.nativeObj);
    }

    public boolean isReleased() {
        return mReleased || mReference != null && mReference.pointer == 0L;
    }

    @Override
    public void release() {
        if (mReleased) return;
        synchronized (this) {
            if (mReleased) return;
            mReleased = true;
            if (mReference != null) {
                mReference.pointer = 0L;
            }
            super.release();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        /* Empty body. */
    }

}
