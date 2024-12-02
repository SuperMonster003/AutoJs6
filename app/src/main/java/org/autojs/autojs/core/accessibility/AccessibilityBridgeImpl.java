package org.autojs.autojs.core.accessibility;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.AbstractAutoJs;
import org.autojs.autojs.core.activity.ActivityInfoProvider;
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig;

public class AccessibilityBridgeImpl extends AccessibilityBridge {

    private final AbstractAutoJs mAutoJs;
    private final AccessibilityTool mA11yTool = new AccessibilityTool();
    private final String TAG = AccessibilityBridgeImpl.class.getSimpleName();

    public AccessibilityBridgeImpl(AbstractAutoJs autoJs) {
        super(autoJs.getApplicationContext(), new AccessibilityConfig(), autoJs.getUiHandler());
        mAutoJs = autoJs;
    }

    @Override
    public void ensureServiceStarted(boolean isForcibleRestart) {
        if (isForcibleRestart && mA11yTool.serviceExists()) {
            mA11yTool.stopService(true);
            Log.d(TAG, "isForcibleRestart");
        }
        mA11yTool.ensureService();
    }

    public void ensureServiceStarted() {
        ensureServiceStarted(false);
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
