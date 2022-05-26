package org.autojs.autojs.network.entity;

import androidx.annotation.NonNull;

import org.autojs.autojs6.BuildConfig;

/**
 * Created by Stardust on 2017/9/20.
 */
public class VersionInfo {

    public int appVersionCode;
    public String appVersionName;

    public boolean isNewer() {
        return appVersionCode > BuildConfig.VERSION_CODE;
    }

    @NonNull
    @Override
    public String toString() {
        return "UpdateInfo{" +
                "appVersionCode=" + appVersionCode +
                ", appVersionName='" + appVersionName + '\'' +
                '}';
    }

}
