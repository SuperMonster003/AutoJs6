package org.autojs.autojs.core.opencv;

import org.opencv.core.Point;

/**
 * Modified by SuperMonster003 as of Jan 21, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 18, 2023.
public class MatOfPoint extends org.opencv.core.MatOfPoint {

    private volatile boolean mReleased = false;

    public MatOfPoint() {
        super();
    }

    public MatOfPoint(long l) {
        super(l);
    }

    public MatOfPoint(org.opencv.core.Mat mat) {
        super(mat);
    }

    public MatOfPoint(Point... pointArray) {
        super(pointArray);
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

}
