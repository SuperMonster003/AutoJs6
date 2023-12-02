package org.autojs.autojs.project;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Stardust on Jan 25, 2018.
 */
public class LaunchConfig {

    @SerializedName("hideLogs")
    private boolean mHideLogs = false;

    public boolean shouldHideLogs() {
        return mHideLogs;
    }

    public void setHideLogs(boolean hideLogs) {
        mHideLogs = hideLogs;
    }

}
