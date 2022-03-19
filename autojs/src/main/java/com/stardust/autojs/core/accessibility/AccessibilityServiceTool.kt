package com.stardust.autojs.core.accessibility

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Settings.Secure
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.R
import com.stardust.autojs.core.pref.Pref
import com.stardust.autojs.core.util.ProcessShell
import com.stardust.view.accessibility.AccessibilityService.Companion
import org.autojs.autojs.tool.RootTool
import org.autojs.autojs.tool.SettingsTool
import java.util.*

/**
 * Created by Stardust on 2017/1/26.
 * Modified by SuperMonster003 as of Feb 15, 2022.
 */

class AccessibilityServiceTool(val context: Context) {

    val tag: String = AccessibilityServiceTool::class.java.simpleName

    fun goToAccessibilitySetting() {
        if (Pref.isFirstGoToAccessibilitySetting()) {
            GlobalAppContext.toast(getString(R.string.text_please_choose) + " " + getString(R.string.app_name), Toast.LENGTH_LONG)
        }
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            GlobalAppContext.toast(getString(R.string.go_to_accessibility_settings), Toast.LENGTH_LONG)
        }
    }

    private fun getString(resId: Int): String {
        val context = GlobalAppContext.get()
        return context.getString(resId)
    }

    private fun getComponentName(): ComponentName {
        return ComponentName(context, getAccessibilityServiceJavaClass())
    }

    fun enableAccessibilityService(): Boolean {
        return when {
            enableAccessibilityServiceAutomaticallyIfNeeded() -> true
            else -> {
                goToAccessibilitySetting()
                false
            }
        }
    }

    private fun enableAccessibilityServiceByRootIfNeeded(): Boolean =
        Pref.shouldEnableAccessibilityServiceByRoot()
                && RootTool.isRootAvailable()
                && enableAccessibilityServiceByRoot()

    private fun enableAccessibilityServiceByRootIfNeededAndWaitFor(timeout: Int): Boolean =
        Pref.shouldEnableAccessibilityServiceByRoot()
                && RootTool.isRootAvailable()
                && enableAccessibilityServiceByRootAndWaitFor(timeout)

    private fun enableAccessibilityServiceBySecureSettingsIfNeeded(): Boolean =
        Pref.shouldEnableAccessibilityServiceBySecureSettings()
                && SettingsTool.SecureSettings.isGranted(context)
                && enableAccessibilityServiceBySecureSettings()

    private fun enableAccessibilityServiceBySecureSettingsIfNeededAndWaitFor(timeout: Int): Boolean =
        Pref.shouldEnableAccessibilityServiceBySecureSettings()
                && SettingsTool.SecureSettings.isGranted(context)
                && enableAccessibilityServiceBySecureSettingsAndWaitFor(timeout)

    fun disableAccessibilityService(): Boolean {
        Companion.instance?.disableSelf()
        when {
            !isAccessibilityServiceEnabled() -> {
                Log.d(tag, "disableAccessibilityService: " + "disableSelf succeeded")
                return true
            }
            Pref.shouldEnableAccessibilityServiceBySecureSettings() && SettingsTool.SecureSettings.isGranted(context) -> when {
                disableAccessibilityServiceBySecureSettings() -> return true
            }
        }
        goToAccessibilitySetting()
        return false
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = getComponentName()

        val enabledServicesSetting = Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)

            if (enabledService != null && enabledService == expectedComponentName)
                return true
        }

        return false
    }

    private fun enableAccessibilityServiceByRootAndWaitFor(timeout: Int): Boolean {
        return when {
            enableAccessibilityServiceByRoot() -> {
                Companion.waitForEnabled(timeout.toLong())
            }
            else -> false
        }
    }

    private fun enableAccessibilityServiceBySecureSettingsAndWaitFor(timeout: Int): Boolean {
        return when {
            enableAccessibilityServiceBySecureSettings() -> {
                Companion.waitForEnabled(timeout.toLong())
            }
            else -> false
        }
    }

    private fun enableAccessibilityServiceByRoot(): Boolean {
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

    private fun getServiceName(): String {
        return context.packageName + "/" + getAccessibilityServiceJavaClass().name
    }

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

    private fun getAccessibilityServiceJavaClass(): Class<AccessibilityService> {
        return AccessibilityService::class.java
    }

    fun enableAccessibilityServiceAutomaticallyIfNeeded(): Boolean {
        return when {
            enableAccessibilityServiceByRootIfNeeded() -> true
            enableAccessibilityServiceBySecureSettingsIfNeeded() -> true
            else -> false
        }
    }

    fun enableAccessibilityServiceAutomaticallyIfNeededAndWaitFor(timeout: Int): Boolean {
        return when {
            enableAccessibilityServiceByRootIfNeededAndWaitFor(timeout) -> true
            enableAccessibilityServiceBySecureSettingsIfNeededAndWaitFor(timeout) -> true
            else -> false
        }
    }

}
