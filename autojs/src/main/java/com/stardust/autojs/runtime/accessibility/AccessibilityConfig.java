package com.stardust.autojs.runtime.accessibility;

import com.stardust.util.DeveloperUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on 2017/4/29.
 */
public class AccessibilityConfig {

    private static boolean isUnintendedGuardEnabled = false;

    private final List<String> mWhiteList = new ArrayList<>();
    private boolean mSealed = false;

    public AccessibilityConfig() {
        if (isUnintendedGuardEnabled()) {
            mWhiteList.add(DeveloperUtils.selfPackage());
        }
    }

    public static boolean isUnintendedGuardEnabled() {
        return isUnintendedGuardEnabled;
    }

    public static void setIsUnintendedGuardEnabled(boolean isUnintendedGuardEnabled) {
        AccessibilityConfig.isUnintendedGuardEnabled = isUnintendedGuardEnabled;
    }

    public boolean whiteListContains(String packageName) {
        return mWhiteList.contains(packageName);
    }

    public void addWhiteList(String packageName) {
        if (mSealed)
            throw new IllegalStateException("Sealed");
        mWhiteList.add(packageName);
    }

    public final void seal() {
        mSealed = true;
    }
}
