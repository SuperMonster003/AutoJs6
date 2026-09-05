package org.autojs.autojs.core.accessibility;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.AbstractAutoJs;
import org.autojs.autojs.core.activity.ActivityInfoProvider;
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig;

public class AccessibilityBridgeImpl extends AccessibilityBridge {

    private final AbstractAutoJs mAutoJs;

    public AccessibilityBridgeImpl(AbstractAutoJs autoJs) {
        super(autoJs.getApplicationContext(), new AccessibilityConfig(), autoJs.getUiHandler());
        mAutoJs = autoJs;
    }

    @Override
    public void ensureServiceStarted(boolean isForcibleRestart) {
        mAutoJs.getAccessibilityTool().ensureService(isForcibleRestart);
    }

    @Nullable
    @Override
    public AccessibilityService getService() {
        return AccessibilityService.Companion.hasInstance()
                ? AccessibilityService.Companion.getInstance()
                : null;
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
