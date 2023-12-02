package org.autojs.autojs.network.download;

import androidx.annotation.NonNull;

/**
 * Created by Stardust on Oct 20, 2017.
 */
public class DownloadFailedException extends Throwable {
    private final String mUrl;
    private final String mPath;

    public DownloadFailedException(String url, String path) {
        mUrl = url;
        mPath = path;
    }

    @NonNull
    @Override
    public String toString() {
        return "DownloadFailedException{" +
                "url='" + mUrl + '\'' +
                ", path='" + mPath + '\'' +
                "} " + super.toString();
    }
}
