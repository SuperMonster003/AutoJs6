package org.autojs.autojs.core.accessibility

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Settings.Secure
import android.text.TextUtils
import android.util.Log
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.service.AccessibilityInteractionClient
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.SettingsUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Jan 26, 2017.
 * Modified by SuperMonster003 as of Sep 5, 2026.
 */
class AccessibilityTool(private val context: Context? = null) {

    private val mApplicationContext = GlobalAppContext.get()
    private val mContext: Context
        get() = context ?: mApplicationContext
    private val mServiceNamePrefix = mApplicationContext.packageName
    private val mServiceNameSuffix = AccessibilityServiceUsher::class.java.name
    private val mServiceName = "$mServiceNamePrefix/$mServiceNameSuffix"

    @ScriptInterface
    fun clearCache(): Boolean = runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                AccessibilityService.instance?.clearCache() == true
            }
            else -> AccessibilityInteractionClient.clearCache()
        }
    }.getOrDefault(false)

    @ScriptInterface
    @JvmOverloads
    fun launchSettings(showGuideMessage: Boolean = true, showExceptionHint: Boolean = true) {
        if (showGuideMessage) {
            val msg = "${mContext.getString(R.string.text_please_choose)} ${mContext.getString(R.string.app_name)}"
            ViewUtils.showToast(mContext, msg, true)
        }
        val result = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).startSafely(mApplicationContext)
        if (!result && showExceptionHint) {
            ViewUtils.showToast(mContext, R.string.go_to_accessibility_settings, true)
        }
    }

    @ScriptInterface
    fun getServices() = when {
        isSecureAccessible() -> getServicesWithSecure().split(SERVICES_DELIMITER).toTypedArray()
        isRootAccessible() -> getServicesWithRoot().split(SERVICES_DELIMITER).toTypedArray()
        else -> emptyArray()
    }

    @ScriptInterface
    fun hasInstance() = AccessibilityService.hasInstance()

    // Means the service is enabled in system settings.
    // zh-CN: 表示服务在系统设置中处于已启用状态.
    @ScriptInterface
    fun hasService(): Boolean {
        val services = Secure.getString(
            mApplicationContext.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.split(SERVICES_DELIMITER).any { it.trim() == mServiceName }
    }

    @ScriptInterface
    fun isRunning() = hasService() && hasInstance()

    // Means the service is enabled and confirmed workable in current process.
    // zh-CN: 表示服务已启用, 且在当前进程中已确认可用.
    @ScriptInterface
    fun isOperational() = isRunning() && AccessibilityService.hasOperationalState

    @ScriptInterface
    fun isMalfunctioning() = hasService() && !hasInstance()

    @JvmOverloads
    @ScriptInterface
    fun startService(withLaunchSettings: Boolean = true): Boolean {
        // A running service is left alone; the root / secure-settings ways below re-enable the service
        // (disable + enable), which would needlessly restart it.
        // zh-CN: 已运行的服务不做处理; 下方的 root / 安全设置方式会重新启用服务 (先禁用再启用), 相当于无谓的重启.
        if (isRunning()) {
            return true.also { Log.d(TAG, "Accessibility service is running") }
        }
        if (startServiceWithConvenientWaysIfPossible()) {
            return true.also { Log.d(TAG, "Accessibility service enabled successfully by a certain \"convenient way\"") }
        }
        if (isRunning()) {
            return true.also { Log.d(TAG, "Accessibility service is running") }
        }
        if (withLaunchSettings) launchSettings()
        return false
    }

    @JvmOverloads
    @ScriptInterface
    fun stopService(withLaunchSettings: Boolean = true): Boolean {
        var result = false

        when {
            isRootAccessible() && stopServiceWithRoot() -> {
                result = true.also { Log.d(TAG, "Accessibility service disabled successfully by \"root\"") }
            }
            isSecureAccessible() && stopServiceWithSecure() -> {
                result = true.also { Log.d(TAG, "Accessibility service disabled successfully by \"secure settings\"") }
            }
        }

        if (!result) {
            if (AccessibilityService.stop() && !hasService()) {
                result = true.also { Log.d(TAG, "Accessibility service disabled successfully by \"disableSelf\"") }
            }
        }

        return result || false.also { if (withLaunchSettings) launchSettings() }
    }

    @JvmOverloads
    @ScriptInterface
    fun restartService(withLaunchSettings: Boolean = false): Boolean {
        var result = stopService(false)
        // The system unbinds asynchronously. Re-enabling the service before that happened is coalesced
        // into "still enabled" by the settings observer and no restart takes place at all.
        // zh-CN: 系统的解绑是异步的. 若在解绑完成前重新启用, 设置观察者会把两次写入合并为 "仍然启用", 实际不会重启.
        if (result && !AccessibilityService.waitForStopped()) {
            Log.w(TAG, "Accessibility service was not unbound within $DEFAULT_A11Y_SERVICE_STOP_TIMEOUT ms after being disabled")
        }
        result = startService(withLaunchSettings) && result
        return result
    }

    @JvmOverloads
    @ScriptInterface
    fun startServiceAndWaitFor(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT) {
        if (isRunning()) return
        if (startServiceWithConvenientWaysIfPossibleAndWaitFor()) return
        launchSettings()
        if (!AccessibilityService.waitForStarted(timeout)) {
            throw ScriptInterruptedException()
        }
    }

    // Start service and wait until it becomes operational.
    // zh-CN: 启动服务并等待其进入可工作状态.
    @JvmOverloads
    @ScriptInterface
    fun startServiceAndWaitForOperational(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT) {
        if (isOperational()) return
        if (startServiceWithConvenientWaysIfPossibleAndWaitFor(timeout)) {
            AccessibilityService.waitForOperational(timeout)
            if (isOperational()) return
        } else {
            launchSettings()
        }
        if (!AccessibilityService.waitForOperational(timeout)) {
            throw ScriptInterruptedException()
        }
    }

    private fun startServiceWithConvenientWaysIfPossible() = when {
        startServiceWithRootIfPossible() -> true
        startServiceWithSecureIfPossible() -> true
        else -> false
    }

    private fun startServiceWithConvenientWaysIfPossibleAndWaitFor(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT) = when {
        startServiceWithRootIfPossible(timeout) -> true
        startServiceWithSecureIfPossible(timeout) -> true
        else -> false
    }

    @ScriptInterface
    fun ensureService() {
        if (isRunning()) return
        if (startServiceWithConvenientWaysIfPossibleAndWaitFor()) return
        launchSettings()
        if (AccessibilityService.waitForStarted()) return
        when {
            !hasService() -> throw ScriptException(mContext.getString(R.string.text_a11y_service_enabled_but_not_running))
            else -> throw ScriptException(mContext.getString(R.string.error_no_accessibility_permission))
        }
    }

    // Ensure service is operational, otherwise throw a concrete error.
    // zh-CN: 确保服务处于可工作状态, 否则抛出明确的错误信息.
    @ScriptInterface
    fun ensureServiceOperational(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT) {
        if (isOperational()) return
        startServiceAndWaitForOperational(timeout)
        if (isOperational()) return
        when {
            !hasService() -> throw ScriptException(mContext.getString(R.string.text_a11y_service_enabled_but_not_running))
            !hasInstance() -> throw ScriptException(mContext.getString(R.string.text_a11y_service_enabled_but_not_running))
            else -> throw ScriptException(mContext.getString(R.string.error_no_accessibility_permission))
        }
    }

    private fun isSecureAccessible() = Pref.shouldStartA11yServiceWithSecureSettings() && SettingsUtils.SecureSettings.isGranted(mApplicationContext)

    private fun isRootAccessible() = Pref.shouldStartA11yServiceWithRoot() && RootUtils.isRootAvailable()

    // The root and secure-settings ways report whether the enabled-services setting really changed
    // (read back through hasService), not merely whether the commands ran without throwing.
    // zh-CN: root 与安全设置方式以 hasService 回读结果为准, 而不是仅以 "命令未抛异常" 作为成功.

    private fun startServiceWithRoot(timeout: Long? = null): Boolean = when (timeout != null) {
        true -> startServiceWithRoot() && AccessibilityService.waitForStarted(timeout)
        else -> runCatching {
            stopServiceWithRoot()
            putSecureSettingWithRoot(Secure.ENABLED_ACCESSIBILITY_SERVICES, getServicesWithRoot(true))
            putSecureSettingWithRoot(Secure.ACCESSIBILITY_ENABLED, "1")
            hasService()
        }.getOrDefault(false)
    }

    private fun startServiceWithRootIfPossible(timeout: Long? = null) = isRootAccessible() && startServiceWithRoot(timeout)

    private fun startServiceWithSecure(timeout: Long? = null): Boolean = when (timeout != null) {
        true -> startServiceWithSecure() && AccessibilityService.waitForStarted(timeout)
        else -> runCatching {
            stopServiceWithSecure()

            val services = getServicesWithSecure(true)
            Secure.putString(mApplicationContext.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
            Secure.putInt(mApplicationContext.contentResolver, Secure.ACCESSIBILITY_ENABLED, 1)

            hasService()
        }.getOrDefault(false)
    }

    private fun startServiceWithSecureIfPossible(timeout: Long? = null) = isSecureAccessible() && startServiceWithSecure(timeout)

    private fun stopServiceWithRoot() = runCatching {
        putSecureSettingWithRoot(Secure.ENABLED_ACCESSIBILITY_SERVICES, getServicesWithRoot(false))
        putSecureSettingWithRoot(Secure.ACCESSIBILITY_ENABLED, "0")
        !hasService()
    }.getOrDefault(false)

    private fun stopServiceWithSecure() = runCatching {
        val services = getServicesWithSecure(false)

        Secure.putString(mApplicationContext.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
        Secure.putInt(mApplicationContext.contentResolver, Secure.ACCESSIBILITY_ENABLED, 0)

        !hasService()
    }.getOrDefault(false)

    // The value is single-quoted so that an empty string (AutoJs6 was the only enabled service)
    // is still passed as an argument; an unquoted empty value used to be dropped by the shell,
    // "settings put" then rejected the command and the service silently stayed enabled.
    // zh-CN: 值以单引号包裹, 使空字符串 (AutoJs6 是唯一启用的服务) 仍能作为参数传递;
    // 未加引号的空值会被 shell 丢弃, "settings put" 因而拒绝执行, 服务静默地保持启用状态.
    private fun putSecureSettingWithRoot(name: String, value: String) {
        val quoted = "'${value.replace("'", "'\\''")}'"
        val result = ProcessShell.execCommand("settings put secure $name $quoted", true)
        if (!TextUtils.isEmpty(result.error)) {
            Log.w(TAG, "settings put secure $name: ${result.error.trim()}")
        }
    }

    private fun getServicesWithRoot(withAutoJs: Boolean? = null): String = when (withAutoJs) {
        true -> attachAutoJsService(getServicesWithRoot(false))
        false -> detachAutoJsService(getServicesWithRoot())
        else -> {
            val cmd = "settings get secure enabled_accessibility_services"
            ProcessShell.execCommand(cmd, true).result.takeUnless { it == "null" }?.replace("\n", "") ?: ""
        }
    }

    private fun getServicesWithSecure(withAutoJs: Boolean? = null): String = when (withAutoJs) {
        true -> attachAutoJsService(getServicesWithSecure(false))
        false -> detachAutoJsService(getServicesWithSecure())
        else -> Secure.getString(mApplicationContext.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES)?.replace("\n", "") ?: ""
    }

    private fun attachAutoJsService(services: String) = when (services.isEmpty()) {
        true -> mServiceName
        else -> "$services$SERVICES_DELIMITER$mServiceName"
    }

    private fun detachAutoJsService(services: String) = services
        .split(SERVICES_DELIMITER)
        .filterNot { it.startsWith(mServiceNamePrefix) || it.matches(Regex("(null)?\\s*")) }
        .joinToString(SERVICES_DELIMITER)

    companion object {

        private val TAG = AccessibilityTool::class.java.simpleName

        @JvmStatic
        val DEFAULT_A11Y_SERVICE_START_TIMEOUT = 2000L

        @JvmStatic
        val DEFAULT_A11Y_SERVICE_STOP_TIMEOUT = 2000L

        @JvmStatic
        val SERVICES_DELIMITER = ":"

    }

}
