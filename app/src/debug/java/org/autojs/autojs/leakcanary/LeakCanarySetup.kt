package org.autojs.autojs.leakcanary

import android.app.Application
import android.util.Log
import org.autojs.autojs6.R

object LeakCanarySetup {

    fun setup(application: Application) {
        val isEnable = application.resources.getBoolean(R.bool.leak_canary_enabled)
        val leakCanaryClass = findLeakCanaryClass() ?: run {
            Log.d(TAG, "LeakCanary isn't included in this debug build")
            return
        }
        runCatching {
            setDumpHeapEnabled(leakCanaryClass, isEnable)
            showLeakDisplayActivityLauncherIcon(leakCanaryClass, isEnable)
        }.onSuccess {
            Log.d(TAG, "LeakCanary has been set up as ${if (isEnable) "enabled" else "disabled"}")
        }.onFailure {
            Log.w(TAG, "LeakCanary is included but couldn't be set up", it)
        }
    }

    private fun findLeakCanaryClass(): Class<*>? = runCatching {
        Class.forName(LEAK_CANARY_CLASS_NAME)
    }.getOrNull()

    private fun setDumpHeapEnabled(leakCanaryClass: Class<*>, isEnable: Boolean) {
        val config = leakCanaryClass.getMethod("getConfig").invoke(null)
        val builder = config.javaClass.getMethod("newBuilder").invoke(config)
        val builderClass = builder.javaClass
        builderClass.getMethod("dumpHeap", Boolean::class.javaPrimitiveType!!).invoke(builder, isEnable)
        val updatedConfig = builderClass.getMethod("build").invoke(builder)
        leakCanaryClass.getMethod("setConfig", config.javaClass).invoke(null, updatedConfig)
    }

    private fun showLeakDisplayActivityLauncherIcon(leakCanaryClass: Class<*>, isEnable: Boolean) {
        val leakCanary = leakCanaryClass.getField("INSTANCE").get(null)
        leakCanaryClass
            .getMethod("showLeakDisplayActivityLauncherIcon", Boolean::class.javaPrimitiveType!!)
            .invoke(leakCanary, isEnable)
    }

    private const val LEAK_CANARY_CLASS_NAME = "leakcanary.LeakCanary"
    private const val TAG = "LeakCanarySetup"

}
