package org.autojs.autojs.network.entity;

import static org.autojs.autojs.util.StringUtils.str;

import androidx.annotation.NonNull;

import org.autojs.autojs.util.UpdateUtils;
import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;

import java.text.MessageFormat;

/**
 * Created by Stardust on 2017/9/20.
 * Modified by SuperMonster003 as of May 29, 2022.
 */
public class VersionInfo implements ExtendedVersionInfo {

    private String mVersionName;
    private int mVersionCode;

    private long mSize = -1;
    private String mDownloadUrl;
    private String mAbi;
    private String mFileName;

    public VersionInfo(@NonNull String propertiesFileRawString) {
        String regexVersionName = "VERSION_NAME=.+";
        String regexVersionCode = "VERSION_BUILD=.+";
        for (String string : propertiesFileRawString.split("\n")) {
            if (string.matches(regexVersionName)) {
                mVersionName = string.split("=")[1];
            } else if (string.matches(regexVersionCode)) {
                mVersionCode = Integer.parseInt(string.split("=")[1]);
            }
            if (mVersionName != null && mVersionCode > 0) {
                break;
            }
        }
    }

    public VersionInfo(@NonNull String versionName, int versionCode) {
        mVersionName = versionName;
        mVersionCode = versionCode;
    }

    public boolean isNewer() {
        return mVersionCode > BuildConfig.VERSION_CODE;
    }

    public boolean isNotIgnored() {
        return !UpdateUtils.isVersionIgnored(this);
    }

    @NonNull
    @Override
    public String toString() {
        if (mAbi == null) {
            return MessageFormat.format("{0}: {1} ({2})", str(R.string.text_version), mVersionName, mVersionCode);
        }
        return MessageFormat.format("{0}: {1} ({2}) [{3}]", str(R.string.text_version), mVersionName, mVersionCode, mAbi);
    }

    public String toSummary() {
        return MessageFormat.format("{0} ({1})", mVersionName, mVersionCode);
    }

    public static SimpleVersionInfo parseSummary(CharSequence summary) {
        int indexForVersionName = 0;
        int indexForVersionCode = 1;

        String versionName = null;
        int versionCode = -1;

        String[] split = summary.toString().split(" ");

        for (int i = 0; i < split.length; i++) {
            if (i == indexForVersionName) {
                versionName = split[i];
            } else if (i == indexForVersionCode) {
                versionCode = Integer.parseInt(split[i].replaceAll("[()]", ""));
            }
        }
        return new SimpleVersionInfo(versionName, versionCode);
    }

    public String getVersionName() {
        return mVersionName;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    public void setFileName(String fileName) {
        this.mFileName = fileName;
    }

    public void setSize(long size) {
        mSize = size;
    }

    public void setDownloadUrl(String downloadUrl) {
        mDownloadUrl = downloadUrl;
    }

    public String getFileName() {
        return mFileName;
    }

    public void setAbi(String abi) {
        mAbi = abi;
    }

    public long getSize() {
        return mSize;
    }

    public String getDownloadUrl() {
        return mDownloadUrl;
    }

    @Override
    public String getAbi() {
        return mAbi;
    }

}
