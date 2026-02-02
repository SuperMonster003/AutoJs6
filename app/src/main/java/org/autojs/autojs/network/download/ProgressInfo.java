package org.autojs.autojs.network.download;

/**
 * Created by SuperMonster003 on May 30, 2022.
 * Modified by SuperMonster003 as of Feb 1, 2026.
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

    public void setTotal(long total) {
        mTotal = total;
    }

    public void incrementTotal(long i) {
        mTotal += i;
    }

    public long getReadBytes() {
        return mRead;
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
