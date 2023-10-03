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
import org.autojs.autojs.runtime.api.ProcessShell.execCommand
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.SettingsUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/1/26.
 * Modified by SuperMonster003 as of Feb 15, 2022.
 */
class AccessibilityTool(val context: Context) {

    private val tag: String = AccessibilityTool::class.java.simpleName
    private val serviceName = "${context.packageName}/${AccessibilityService::class.java.name}"

    val service: Service = Service()

    fun launchSettings() {
        "${context.getString(R.string.text_please_choose)} ${context.getString(R.string.app_name)}".let {
            ViewUtils.showToast(context, it, true)
        }
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            ViewUtils.showToast(context, R.string.go_to_accessibility_settings, true)
        }
    }

    inner class Service {

        fun enable() {
            enableIfNeeded().also { if (!it) launchSettings() }
        }

        fun launchSettings() {
            this@AccessibilityTool.launchSettings()
        }

        fun enableIfNeeded() = when {
            enableWithRootIfNeeded() -> true
            enableWithSecureIfNeeded() -> true
            else -> false
        }

        fun enableIfNeededAndWaitFor(timeout: Long) = when {
            enableWithRootIfNeeded(timeout) -> true
            enableWithSecureIfNeeded(timeout) -> true
            else -> false
        }

        fun disable(): Boolean {
            if (AccessibilityService.disable() && !isEnabled()) {
                return true.also { Log.d(tag, "Accessibility Service disabled successfully by \"disableSelf\"") }
            }
            if (isRootAccessible() && disableWithRoot()) {
                return true.also { Log.d(tag, "Accessibility Service disabled successfully by \"root\"") }
            }
            if (isSecureAccessible() && disableWithSecure()) {
                return true.also { Log.d(tag, "Accessibility Service disabled successfully by \"secure settings\"") }
            }
            return false.also { launchSettings() }
        }

        fun isEnabled(): Boolean {
            val enabledServicesSetting = Secure.getString(
                context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            TextUtils.SimpleStringSplitter(':').also { colonSplitter ->
                colonSplitter.setString(enabledServicesSetting)
                val expectedComponentName = ComponentName(context, AccessibilityService::class.java)
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

        private fun isSecureAccessible() = Pref.shouldEnableA11yServiceWithSecureSettings() && SettingsUtils.SecureSettings.isGranted(context)

        private fun isRootAccessible() = Pref.shouldEnableA11yServiceWithRoot() && RootUtils.isRootAvailable()

        private fun enableWithRoot(timeout: Long? = null): Boolean = when (timeout != null) {
            true -> enableWithRoot() && AccessibilityService.waitForEnabled(timeout.toLong())
            else -> try {
                disableWithRoot()

                val services = getEnabledWithRoot(true)

                val cmdServices = "settings put secure enabled_accessibility_services $services"
                val resultServices = execCommand(cmdServices, true)

                val cmdState = "settings put secure accessibility_enabled 1"
                val resultState = execCommand(cmdState, true)

                TextUtils.isEmpty(resultServices.error) && TextUtils.isEmpty(resultState.error)
            } catch (e: Exception) {
                false
            }
        }

        private fun enableWithRootIfNeeded(timeout: Long? = null) = isRootAccessible() && enableWithRoot(timeout)

        private fun enableWithSecure(timeout: Long? = null): Boolean = when (timeout != null) {
            true -> enableWithSecure() && AccessibilityService.waitForEnabled(timeout)
            else -> try {
                disableWithSecure()

                val enabledWithSecure = getEnabledWithSecure(true)
                Secure.putString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledWithSecure)
                Secure.putInt(context.contentResolver, Secure.ACCESSIBILITY_ENABLED, 1)

                isEnabled()
            } catch (e: Exception) {
                false
            }
        }

        private fun enableWithSecureIfNeeded(timeout: Long? = null) = isSecureAccessible() && enableWithSecure(timeout)

        private fun disableWithRoot() = try {
            val services = getEnabledWithRoot(false)

            val cmdServices = "settings put secure enabled_accessibility_services $services"
            val resultServices = execCommand(cmdServices, true)

            val cmdState = "settings put secure accessibility_enabled 0"
            val resultState = execCommand(cmdState, true)

            TextUtils.isEmpty(resultServices.error) && TextUtils.isEmpty(resultState.error)
        } catch (e: Exception) {
            false
        }

        private fun disableWithSecure() = try {
            Secure.putString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, getEnabledWithSecure(false))
            Secure.putInt(context.contentResolver, Secure.ACCESSIBILITY_ENABLED, 0)
            !isEnabled()
        } catch (e: Exception) {
            false
        }

        private fun getEnabledWithRoot(withAutoJs: Boolean? = null): String = when (withAutoJs) {
            true -> attachAutoJs(getEnabledWithRoot(false))
            false -> detachAutoJs(getEnabledWithRoot())
            else -> {
                val cmd = "settings get secure enabled_accessibility_services"
                execCommand(cmd, true).result.takeUnless { it == "null" }?.replace("\n", "") ?: ""
            }
        }

        private fun getEnabledWithSecure(withAutoJs: Boolean? = null): String = when (withAutoJs) {
            true -> attachAutoJs(getEnabledWithSecure(false))
            false -> detachAutoJs(getEnabledWithSecure())
            else -> Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES)?.replace("\n", "") ?: ""
        }

        private fun attachAutoJs(services: String) = when (services.isEmpty()) {
            true -> serviceName
            else -> "$services:$serviceName"
        }

        private fun detachAutoJs(services: String) = services
            .split(":")
            .filter { it != serviceName && !it.matches(Regex("(null)?\\s*")) }
            .joinToString(":")

    }

}