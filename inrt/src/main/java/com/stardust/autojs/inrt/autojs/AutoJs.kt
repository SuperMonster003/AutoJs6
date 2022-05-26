package com.stardust.autojs.inrt.autojs

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.inrt.LogActivity
import com.stardust.autojs.inrt.R
import com.stardust.autojs.inrt.SettingsActivity
import com.stardust.autojs.core.accessibility.AccessibilityServiceTool
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.api.AppUtils
import com.stardust.autojs.runtime.exception.ScriptException
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.autojs.script.JavaScriptSource
import com.stardust.view.accessibility.AccessibilityService


/**
 * Created by Stardust on 2017/4/2.
 */
class AutoJs private constructor(application: Application) : com.stardust.autojs.AutoJs(application) {

    init {
        scriptEngineService.registerGlobalScriptExecutionListener(ScriptExecutionGlobalListener())
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
            errorMessage = GlobalAppContext.getString(R.string.text_a11y_service_enabled_but_not_running)
        } else if (!getAccessibilityServiceTool().enableAccessibilityServiceAutomaticallyIfNeededAndWaitFor(2000)) {
            errorMessage = GlobalAppContext.getString(R.string.text_no_accessibility_permission)
        }
        if (errorMessage != null) {
            getAccessibilityServiceTool().goToAccessibilitySetting()
            throw ScriptException(errorMessage)
        }
    }

    override fun waitForAccessibilityServiceEnabled(timeout: Long) {
        if (AccessibilityService.instance != null) {
            return
        }
        var errorMessage: String? = null
        if (isAccessibilityServiceEnabled()) {
            errorMessage = GlobalAppContext.getString(R.string.text_a11y_service_enabled_but_not_running)
        } else if (!getAccessibilityServiceTool().enableAccessibilityServiceAutomaticallyIfNeededAndWaitFor(2000)) {
            errorMessage = GlobalAppContext.getString(R.string.text_no_accessibility_permission)
        }
        if (errorMessage != null) {
            getAccessibilityServiceTool().goToAccessibilitySetting()
            if (!AccessibilityService.waitForEnabled(timeout)) {
                throw ScriptInterruptedException()
            }
        }
    }

    override fun initScriptEngineManager() {
        super.initScriptEngineManager()
        scriptEngineManager.registerEngine(JavaScriptSource.ENGINE) {
            val engine = XJavaScriptEngine(application)
            engine.runtime = createRuntime()
            engine
        }
    }

    override fun createRuntime(): ScriptRuntime {
        val runtime = super.createRuntime()
        runtime.putProperty("class.settings", SettingsActivity::class.java)
        runtime.putProperty("class.console", LogActivity::class.java)
        return runtime
    }

    private fun getAccessibilityServiceTool(): AccessibilityServiceTool {
        return AccessibilityServiceTool(application)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return getAccessibilityServiceTool().isAccessibilityServiceEnabled()
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: AutoJs
            private set

        fun initInstance(application: Application) {
            instance = AutoJs(application)
        }
    }
}
