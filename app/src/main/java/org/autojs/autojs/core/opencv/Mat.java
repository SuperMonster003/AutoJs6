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
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link org.opencv.core.Mat} whose lifecycle is owned by AutoJs6.
 * <p>
 * Invariant: every instance owns its own native {@code cv::Mat} header, it never shares the header
 * with another Java object. Data buffers may still be shared through OpenCV's reference counting,
 * which is exactly what {@link #from(org.opencv.core.Mat)} relies on. This invariant makes it safe
 * for {@link #finalize()} to delete the header when the Java object becomes unreachable, so that
 * a wrapper the script forgot to release does not leak native memory for the rest of the process.
 * <p>
 * zh-CN:
 * <p>
 * 由 AutoJs6 管理生命周期的 {@link org.opencv.core.Mat}.
 * 不变式: 每个实例都拥有独立的原生 {@code cv::Mat} 头部, 绝不与其他 Java 对象共享同一个头部
 * (数据缓冲区仍可通过 OpenCV 引用计数共享, {@link #from(org.opencv.core.Mat)} 正是基于此).
 * 因此 {@link #finalize()} 可以在对象不可达时安全删除头部, 避免脚本忘记 release 时原生内存永久泄漏.
 */
public class Mat extends org.opencv.core.Mat {

    // Process-wide lifecycle counters for memory diagnostics (docs/memory/ROADMAP.md P3.1). They are only
    // ever incremented and never change how a Mat behaves.
    // zh-CN: 供内存诊断使用的进程级生命周期计数器 (docs/memory/ROADMAP.md P3.1). 只做递增, 从不改变 Mat 的行为.
    private static final AtomicLong sCreated = new AtomicLong();
    private static final AtomicLong sReleased = new AtomicLong();
    private static final AtomicLong sFinalized = new AtomicLong();
    private static final AtomicLong sFinalizedUnreleased = new AtomicLong();

    private volatile boolean mReleased;

    {
        sCreated.incrementAndGet();
    }

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
                    sReleased.incrementAndGet();
                }
            }
        }
    }

    /**
     * Creates a Mat that owns its own native header but shares pixel data with {@code source}
     * through OpenCV reference counting. Use this instead of {@code new Mat(source.nativeObj)},
     * which would let two Java objects delete the same header.
     * <p>
     * zh-CN: 创建拥有独立原生头部但与 {@code source} 共享像素数据 (引用计数) 的 Mat.
     * 请使用此方法替代 {@code new Mat(source.nativeObj)}, 后者会让两个 Java 对象删除同一个头部.
     */
    @NonNull
    public static Mat from(@NonNull org.opencv.core.Mat source) {
        if (source instanceof Mat) {
            return (Mat) source;
        }
        Mat result = new Mat();
        if (source.nativeObj != 0L) {
            source.assignTo(result);
        }
        return result;
    }

    @Override
    protected void finalize() throws Throwable {
        // Delete the native header (and drop our data reference) when the Java object is unreachable.
        // The header is private to this instance (see class comment), so this cannot double-free.
        // zh-CN: Java 对象不可达时删除原生头部 (并释放数据引用). 头部为本实例独占 (见类注释), 不会重复释放.
        sFinalized.incrementAndGet();
        if (!mReleased) {
            sFinalizedUnreleased.incrementAndGet();
        }
        super.finalize();
    }

    /**
     * Instances created so far in this process (every constructor, {@link #clone()}, {@link #from(org.opencv.core.Mat)}
     * when it wraps, {@link #ones(int, int, int)}).
     * zh-CN: 本进程至今创建的实例数 (全部构造方法, {@link #clone()}, 需要包装时的 {@link #from(org.opencv.core.Mat)}, {@link #ones(int, int, int)}).
     */
    public static long getCreatedCount() {
        return sCreated.get();
    }

    /** Instances whose {@link #release()} ran. zh-CN: 已执行 {@link #release()} 的实例数. */
    public static long getReleasedCount() {
        return sReleased.get();
    }

    /** Instances finalized (native header deleted). zh-CN: 已终结 (原生头部已删除) 的实例数. */
    public static long getFinalizedCount() {
        return sFinalized.get();
    }

    /**
     * Instances neither released nor finalized: their pixel data (unless shared) is still held.
     * zh-CN: 既未 release 也未终结的实例数: 其像素数据 (除非与他者共享) 仍被持有.
     */
    public static long getLiveCount() {
        return sCreated.get() - sReleased.get() - sFinalizedUnreleased.get();
    }

}
