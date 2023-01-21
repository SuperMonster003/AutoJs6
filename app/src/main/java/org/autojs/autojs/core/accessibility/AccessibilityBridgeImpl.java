package org.autojs.autojs.core.accessibility;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.AbstractAutoJs;
import org.autojs.autojs.core.activity.ActivityInfoProvider;

public class AccessibilityBridgeImpl extends AccessibilityBridge {

    private final AbstractAutoJs mAutoJs;

    public AccessibilityBridgeImpl(AbstractAutoJs autoJs) {
        super(autoJs.getContext(), autoJs.createAccessibilityConfig(), autoJs.getUiHandler());
        mAutoJs = autoJs;
    }

    @Override
    public void ensureServiceEnabled() {
        mAutoJs.ensureAccessibilityServiceEnabled();
    }

    @Override
    public void waitForServiceEnabled(long timeout) {
        mAutoJs.waitForAccessibilityServiceEnabled(timeout);
    }

    @Nullable
    @Override
    public AccessibilityService getService() {
        return AccessibilityService.Companion.getInstance();
    }

    @Override
    public ActivityInfoProvider getInfoProvider() {
        return mAutoJs.getInfoProvider();
    }

    @NonNull
    @Override
    public AccessibilityNotificationObserver getNotificationObserver() {
        return mAutoJs.getNotificationObserver();
    }

}
