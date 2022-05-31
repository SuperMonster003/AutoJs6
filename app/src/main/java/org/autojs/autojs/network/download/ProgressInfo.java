package org.autojs.autojs.network.download;

/**
 * Created by SuperMonster003 on May 30, 2022.
 */

public class ProgressInfo {
    private long mRead = 0;
    private long mTotal;

    public ProgressInfo(long contentLength) {
        mTotal = contentLength;
    }

    public long getTotalBytes() {
        return mTotal;
    }

    public float getTotalKiloBytes() {
        return (float) (mTotal / Math.pow(2, 10));
    }

    public float getTotalMegaBytes() {
        return (float) (mTotal / Math.pow(2, 20));
    }

    public void setTotal(long total) {
        mTotal = total;
    }

    public void incrementTotal(long i) {
        mTotal += i;
    }

    public long getReadBytes() {
        return mRead;
    }

    public float getReadKiloBytes() {
        return (float) (mRead / Math.pow(2, 10));
    }

    public float getReadMegaBytes() {
        return (float) (mRead / Math.pow(2, 20));
    }

    public void setRead(long read) {
        mRead = read;
    }

    public void incrementRead(long i) {
        mRead += i;
    }

    public int getProgress() {
        return (int) (mRead * 100 / mTotal);
    }
}
