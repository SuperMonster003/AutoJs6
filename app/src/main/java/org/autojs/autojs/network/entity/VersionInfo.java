package org.autojs.autojs.network.entity;

import androidx.annotation.NonNull;

import com.stardust.app.GlobalAppContext;

import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;

import java.text.MessageFormat;

/**
 * Created by Stardust on 2017/9/20.
 * Modified by SuperMonster003 as of May 29, 2022.
 */
public class VersionInfo {

    private String mVersionName;
    private int mVersionCode;

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
        // TODO by SuperMonster003 on May 30, 2022.
        return true;
    }

    @NonNull
    @Override
    public String toString() {
        return MessageFormat.format("{0}: {1} ({2})", GlobalAppContext.getString(R.string.text_version), mVersionName, mVersionCode);
    }

    public String getVersionName() {
        return mVersionName;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

}
