package org.autojs.autojs.inrt.autojs

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.inrt.LogActivity
import org.autojs.autojs.inrt.SettingsActivity
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs6.R


/**
 * Created by Stardust on 2017/4/2.
 */
class AutoJs private constructor(application: Application) : org.autojs.autojs.AutoJs(application) {

    init {
        scriptEngineService.registerGlobalScriptExecutionListener(ScriptExecutionGlobalListener())
        scriptEngineManager.registerEngine(JavaScriptSource.ENGINE) {
            val engine = XJavaScriptEngine(application)
            engine.runtime = createRuntime()
            engine
        }
    }

    override fun createAppUtils(context: Context): AppUtils {
        return AppUtils(context, context.packageName + ".fileprovider")
    }

    override fun ensureAccessibilityServiceEnabled() {
        if (AccessibilityService.instance != null) {
            return
        }
        var errorMessage: String? = null
        if (isAccessibilityServiceEnabled()) {
            errorMessage = GlobalAppContext.get().getString(R.string.text_a11y_service_enabled_but_not_running)
        } else if (!getAccessibilityServiceTool().service.enableIfNeededAndWaitFor(2000)) {
            errorMessage = GlobalAppContext.get().getString(R.string.error_no_accessibility_permission)
        }
        if (errorMessage != null) {
            getAccessibilityServiceTool().launchSettings()
            throw ScriptException(errorMessage)
        }
    }

    override fun waitForAccessibilityServiceEnabled(timeout: Long) {
        if (AccessibilityService.instance != null) {
            return
        }
        var errorMessage: String? = null
        if (isAccessibilityServiceEnabled()) {
            errorMessage = GlobalAppContext.get().getString(R.string.text_a11y_service_enabled_but_not_running)
        } else if (!getAccessibilityServiceTool().service.enableIfNeededAndWaitFor(2000)) {
            errorMessage = GlobalAppContext.get().getString(R.string.error_no_accessibility_permission)
        }
        if (errorMessage != null) {
            getAccessibilityServiceTool().launchSettings()
            if (!AccessibilityService.waitForEnabled(timeout)) {
                throw ScriptInterruptedException()
            }
        }
    }

    override fun createRuntime(): ScriptRuntime {
        val runtime = super.createRuntime()
        runtime.putProperty("class.settings", SettingsActivity::class.java)
        runtime.putProperty("class.console", LogActivity::class.java)
        return runtime
    }

    private fun getAccessibilityServiceTool(): AccessibilityTool {
        return AccessibilityTool(application)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return getAccessibilityServiceTool().service.isEnabled()
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        lateinit var instance: AutoJs
            private set

        @Synchronized
        fun initInstance(application: Application) {
            instance = AutoJs(application)
        }
    }
}
