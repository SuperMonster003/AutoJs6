package org.autojs.autojs.leakcanary

import android.app.Application
import android.util.Log
import leakcanary.LeakCanary
import org.autojs.autojs6.R

object LeakCanarySetup {

    fun setup(application: Application) {
        val isEnable = application.resources.getBoolean(R.bool.leak_canary_enabled)
        LeakCanary.config = LeakCanary.config.copy(dumpHeap = isEnable)
        LeakCanary.showLeakDisplayActivityLauncherIcon(isEnable)
        Log.d(LeakCanarySetup::class.java.simpleName, "LeakCanary has been set up as ${if (isEnable) "enabled" else "disabled"}")
    }

}