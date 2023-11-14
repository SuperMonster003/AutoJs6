package org.autojs.autojs.core.accessibility;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.AbstractAutoJs;
import org.autojs.autojs.core.activity.ActivityInfoProvider;
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig;

public class AccessibilityBridgeImpl extends AccessibilityBridge {

    private final AbstractAutoJs mAutoJs;
    private final AccessibilityTool.Service mToolService;
    private final String TAG = AccessibilityBridgeImpl.class.getSimpleName();

    public AccessibilityBridgeImpl(AbstractAutoJs autoJs) {
        super(autoJs.getApplicationContext(), new AccessibilityConfig(), autoJs.getUiHandler());
        mAutoJs = autoJs;
        mToolService = new AccessibilityTool(autoJs.getApplicationContext()).getService();
    }

    @Override
    public void ensureServiceStarted(boolean isForcibleRestart) {
        if (isForcibleRestart && mToolService.exists()) {
            mToolService.stop();
            Log.d(TAG, "isForcibleRestart");
        }
        mToolService.ensure();
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
