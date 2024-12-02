package org.autojs.autojs.core.opencv;

import org.autojs.autojs.core.cleaner.Cleaner;
import org.autojs.autojs.core.ref.MonitorResource;
import org.autojs.autojs.core.ref.NativeObjectReference;
import org.opencv.core.Point;

/**
 * Modified by SuperMonster003 as of Jan 21, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 18, 2023.
public class MatOfPoint extends org.opencv.core.MatOfPoint implements MonitorResource {

    private volatile boolean mReleased = false;

    private NativeObjectReference<MonitorResource> mReference;

    public MatOfPoint() {
        init();
    }

    public MatOfPoint(long l) {
        super(l);
        init();
    }

    public MatOfPoint(org.opencv.core.Mat mat) {
        super(mat);
        init();
    }

    public MatOfPoint(Point... pointArray) {
        super(pointArray);
        init();
    }

    private void init() {
        Cleaner.instance.cleanup(this, Mat.MAT_CLEANER);
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

    public long getPointer() {
        return nativeObj;
    }

    public void setNativeObjectReference(NativeObjectReference<MonitorResource> reference) {
        mReference = reference;
    }

}
