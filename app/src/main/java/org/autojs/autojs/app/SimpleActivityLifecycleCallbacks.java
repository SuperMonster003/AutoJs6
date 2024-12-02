package org.autojs.autojs.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Created by Stardust on Apr 2, 2017.
 */
public class SimpleActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        /* Empty body. */
    }

    @Override
    public void onActivityStarted(Activity activity) {
        /* Empty body. */
    }

    @Override
    public void onActivityResumed(Activity activity) {
        /* Empty body. */
    }

    @Override
    public void onActivityPaused(Activity activity) {
        /* Empty body. */
    }

    @Override
    public void onActivityStopped(Activity activity) {
        /* Empty body. */
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        /* Empty body. */
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        /* Empty body. */
    }
}