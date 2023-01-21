package org.autojs.autojs.core.accessibility

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Settings.Secure
import android.text.TextUtils
import android.util.Log
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.SettingsUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.util.Locale

/**
 * Created by Stardust on 2017/1/26.
 * Modified by SuperMonster003 as of Feb 15, 2022.
 */
class AccessibilityServiceTool(val context: Context) {

    val tag: String = AccessibilityServiceTool::class.java.simpleName

    fun goToAccessibilitySetting() {
        "${context.getString(R.string.text_please_choose)} ${context.getString(R.string.app_name)}".let {
            ViewUtils.showToast(context, it, true)
        }
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            ViewUtils.showToast(context, R.string.go_to_accessibility_settings, true)
        }
    }

    fun enableAccessibilityService() = autoEnableIfNeeded().also { if (!it) goToAccessibilitySetting() }

    private fun enableAccessibilityServiceWithRootIfNeeded(): Boolean =
        Pref.shouldEnableA11yServiceWithRoot()
        && RootUtils.isRootAvailable()
        && enableAccessibilityServiceWithRoot()

    private fun enableAccessibilityServiceWithRootIfNeededAndWaitFor(timeout: Int): Boolean =
        Pref.shouldEnableA11yServiceWithRoot()
        && RootUtils.isRootAvailable()
        && enableAccessibilityServiceWithRootAndWaitFor(timeout)

    private fun enableAccessibilityServiceBySecureSettingsIfNeeded(): Boolean =
        Pref.shouldEnableA11yServiceWithSecureSettings()
        && SettingsUtils.SecureSettings.isGranted(context)
        && enableAccessibilityServiceBySecureSettings()

    private fun enableAccessibilityServiceBySecureSettingsIfNeededAndWaitFor(timeout: Int): Boolean =
        Pref.shouldEnableA11yServiceWithSecureSettings()
        && SettingsUtils.SecureSettings.isGranted(context)
        && enableAccessibilityServiceBySecureSettingsAndWaitFor(timeout)

    fun disableAccessibilityService() = AccessibilityService.disable().let {
        if (!isAccessibilityServiceEnabled()) {
            true.also { Log.d(tag, "AccessibilityService.disable() succeeded") }
        } else Pref.shouldEnableA11yServiceWithSecureSettings()
               && SettingsUtils.SecureSettings.isGranted(context)
               && disableAccessibilityServiceBySecureSettings()
                   .also { if (!it) goToAccessibilitySetting() }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServicesSetting = Secure.getString(
            context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        TextUtils.SimpleStringSplitter(':').also { colonSplitter ->
            colonSplitter.setString(enabledServicesSetting)
            val expectedComponentName = ComponentName(context, ApplicationAccessibilityService::class.java)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledService = ComponentName.unflattenFromString(componentNameString)
                if (enabledService != null && enabledService == expectedComponentName) {
                    return true
                }
            }
            return false
        }
    }

    private fun enableAccessibilityServiceWithRootAndWaitFor(timeout: Int) =
        enableAccessibilityServiceWithRoot()
        && AccessibilityService.waitForEnabled(timeout.toLong())

    private fun enableAccessibilityServiceBySecureSettingsAndWaitFor(timeout: Int) =
        enableAccessibilityServiceBySecureSettings()
        && AccessibilityService.waitForEnabled(timeout.toLong())

    private fun enableAccessibilityServiceWithRoot(): Boolean {
        val cmd = """
            enabled=$(settings get secure enabled_accessibility_services)
            pkg=%s
            if [[ ${"$"}enabled != *${"$"}pkg* ]]
            then
                enabled=${"$"}pkg:${"$"}enabled
                settings put secure enabled_accessibility_services ${"$"}enabled
            fi
            settings put secure accessibility_enabled 1
        """
        val serviceName = getServiceName()
        return try {
            TextUtils.isEmpty(ProcessShell.execCommand(String.format(Locale.getDefault(), cmd, serviceName), true).error)
        } catch (e: Exception) {
            false
        }
    }

    private fun getServiceName() = "${context.packageName}/${ApplicationAccessibilityService::class.java.name}"

    private fun enableAccessibilityServiceBySecureSettings(): Boolean {
        return try {
            Secure.putString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, getEnabledServicesWithAutoJs())
            Secure.putInt(context.contentResolver, Secure.ACCESSIBILITY_ENABLED, 1)
            return isAccessibilityServiceEnabled()
        } catch (e: Exception) {
            false
        }
    }

    private fun disableAccessibilityServiceBySecureSettings(): Boolean {
        return try {
            Secure.putString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, getEnabledServicesWithoutAutoJs())
            Secure.putInt(context.contentResolver, Secure.ACCESSIBILITY_ENABLED, 0)
            return !isAccessibilityServiceEnabled()
        } catch (e: Exception) {
            false
        }
    }

    private fun getEnabledServices(): String {
        return Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    }

    private fun getEnabledServicesWithAutoJs(): String {
        var enabled: String = getEnabledServices()
        val enabledList: List<String> = enabled.split(":")
        if (enabledList.indexOf(getServiceName()) == -1) {
            enabled = enabledList.joinToString(":") + ":" + getServiceName()
        }
        return enabled
    }

    private fun getEnabledServicesWithoutAutoJs(): String {
        var enabled: String = getEnabledServices()
        val enabledList: List<String> = enabled.split(":")
        val serverName = getServiceName()
        if (enabledList.indexOf(serverName) > 0) {
            enabled = enabledList.filter { s: String -> s != serverName }.joinToString(":")
        }
        return enabled
    }

    fun autoEnableIfNeeded() = when {
        enableAccessibilityServiceWithRootIfNeeded() -> true
        enableAccessibilityServiceBySecureSettingsIfNeeded() -> true
        else -> false
    }

    fun enableAccessibilityServiceAutomaticallyIfNeededAndWaitFor(timeout: Int) = when {
        enableAccessibilityServiceWithRootIfNeededAndWaitFor(timeout) -> true
        enableAccessibilityServiceBySecureSettingsIfNeededAndWaitFor(timeout) -> true
        else -> false
    }

}
