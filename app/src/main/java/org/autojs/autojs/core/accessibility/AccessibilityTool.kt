package org.autojs.autojs.core.accessibility

import android.content.ActivityNotFoundException
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
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.SettingsUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Jan 26, 2017.
 * Modified by SuperMonster003 as of Feb 15, 2022.
 */
class AccessibilityTool(private val context: Context? = null) {

    private val mApplicationContext = GlobalAppContext.get()
    private val mContext: Context
        get() = context ?: mApplicationContext
    private val mServiceNamePrefix = mApplicationContext.packageName
    private val mServiceNameSuffix = AccessibilityServiceUsher::class.java.name
    private val mServiceName = "$mServiceNamePrefix/$mServiceNameSuffix"

    @ScriptInterface
    fun clearCache(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            AccessibilityService.instance?.clearCache() == true
        }
        else -> AccessibilityInteractionClient.clearCache()
    }

    @ScriptInterface
    fun launchSettings() {
        "${mContext.getString(R.string.text_please_choose)} ${mContext.getString(R.string.app_name)}".let {
            ViewUtils.showToast(mContext, it, true)
        }
        try {
            mApplicationContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
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
    fun isServiceRunning() = hasInstance() && serviceExists()

    @ScriptInterface
    fun hasInstance() = AccessibilityService.hasInstance()

    @ScriptInterface
    fun serviceExists(): Boolean {
        val services = Secure.getString(
            mApplicationContext.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.split(SERVICES_DELIMITER).any { it.trim() == mServiceName }
    }

    @JvmOverloads
    @ScriptInterface
    fun startService(withLaunchSettings: Boolean = true): Boolean {
        var result = false
        if (startServiceWithConvenientWaysIfPossible()) {
            result = true.also { Log.d(TAG, "Accessibility service enabled successfully by a certain \"convenient way\"") }
        }
        if (isServiceRunning()) {
            result = true.also { Log.d(TAG, "Accessibility service is running") }
        }
        return result || false.also { if (withLaunchSettings) launchSettings() }
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
            if (AccessibilityService.stop() && !serviceExists()) {
                result = true.also { Log.d(TAG, "Accessibility service disabled successfully by \"disableSelf\"") }
            }
        }

        return result || false.also { if (withLaunchSettings) launchSettings() }
    }

    @JvmOverloads
    @ScriptInterface
    fun restartService(withLaunchSettings: Boolean = false): Boolean {
        var result = true
        result = stopService(false) && result
        result = startService(withLaunchSettings) && result
        return result
    }

    @JvmOverloads
    @ScriptInterface
    fun startServiceAndWaitFor(timeout: Long = DEFAULT_A11Y_SERVICE_START_TIMEOUT) {
        if (isServiceRunning()) return
        if (startServiceWithConvenientWaysIfPossibleAndWaitFor()) return
        launchSettings()
        if (!AccessibilityService.waitForStarted(timeout)) {
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
        if (isServiceRunning()) return
        if (startServiceWithConvenientWaysIfPossibleAndWaitFor()) return
        launchSettings()
        if (AccessibilityService.waitForStarted()) return
        when {
            !serviceExists() -> throw ScriptException(mContext.getString(R.string.text_a11y_service_enabled_but_not_running))
            else -> throw ScriptException(mContext.getString(R.string.error_no_accessibility_permission))
        }
    }

    private fun isSecureAccessible() = Pref.shouldStartA11yServiceWithSecureSettings() && SettingsUtils.SecureSettings.isGranted(mApplicationContext)

    private fun isRootAccessible() = Pref.shouldStartA11yServiceWithRoot() && RootUtils.isRootAvailable()

    private fun startServiceWithRoot(timeout: Long? = null): Boolean = when (timeout != null) {
        true -> startServiceWithRoot() && AccessibilityService.waitForStarted(timeout.toLong())
        else -> try {
            stopServiceWithRoot()

            val services = getServicesWithRoot(true)

            val cmdServices = "settings put secure enabled_accessibility_services $services"
            val resultServices = ProcessShell.execCommand(cmdServices, true)

            val cmdState = "settings put secure accessibility_enabled 1"
            val resultState = ProcessShell.execCommand(cmdState, true)

            TextUtils.isEmpty(resultServices.error) && TextUtils.isEmpty(resultState.error)
        } catch (e: Exception) {
            false
        }
    }

    private fun startServiceWithRootIfPossible(timeout: Long? = null) = isRootAccessible() && startServiceWithRoot(timeout)

    private fun startServiceWithSecure(timeout: Long? = null): Boolean = when (timeout != null) {
        true -> startServiceWithSecure() && AccessibilityService.waitForStarted(timeout)
        else -> try {
            stopServiceWithSecure()

            val services = getServicesWithSecure(true)
            Secure.putString(mApplicationContext.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
            Secure.putInt(mApplicationContext.contentResolver, Secure.ACCESSIBILITY_ENABLED, 1)

            serviceExists()
        } catch (e: Exception) {
            false
        }
    }

    private fun startServiceWithSecureIfPossible(timeout: Long? = null) = isSecureAccessible() && startServiceWithSecure(timeout)

    private fun stopServiceWithRoot() = try {
        val services = getServicesWithRoot(false)

        val cmdServices = "settings put secure enabled_accessibility_services $services"
        val resultServices = ProcessShell.execCommand(cmdServices, true)

        val cmdState = "settings put secure accessibility_enabled 0"
        val resultState = ProcessShell.execCommand(cmdState, true)

        TextUtils.isEmpty(resultServices.error) && TextUtils.isEmpty(resultState.error)
    } catch (e: Exception) {
        false
    }

    private fun stopServiceWithSecure() = try {
        val services = getServicesWithSecure(false)

        Secure.putString(mApplicationContext.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
        Secure.putInt(mApplicationContext.contentResolver, Secure.ACCESSIBILITY_ENABLED, 0)

        !serviceExists()
    } catch (e: Exception) {
        false
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
        val SERVICES_DELIMITER = ":"

    }

}