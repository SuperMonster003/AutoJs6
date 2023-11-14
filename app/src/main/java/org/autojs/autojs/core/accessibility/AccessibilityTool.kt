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
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
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

    companion object {

        @JvmStatic
        val DEFAULT_A11Y_START_TIMEOUT = 2000L

    }

    open inner class Service {

        fun isRunning() = AccessibilityService.isRunning()

        fun exists(): Boolean {
            val services = Secure.getString(
                context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            TextUtils.SimpleStringSplitter(':').also { colonSplitter ->
                colonSplitter.setString(services)
                val expectedComponentName = ComponentName(context, AccessibilityService::class.java)
                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next()
                    val componentName = ComponentName.unflattenFromString(componentNameString)
                    if (componentName != null && componentName == expectedComponentName) {
                        return true
                    }
                }
                return false
            }
        }

        @JvmOverloads
        fun start(withLaunchSettings: Boolean = true): Boolean {
            if (startWithConvenientWaysIfPossible()) {
                return true
            }
            return false.also { if (withLaunchSettings) launchSettings() }
        }

        @JvmOverloads
        fun stop(withLaunchSettings: Boolean = true): Boolean {
            var result = false
            if (AccessibilityService.stop() && !exists()) {
                result = true.also { Log.d(tag, "Accessibility Service disabled successfully by \"disableSelf\"") }
            }
            if (isRootAccessible() && stopWithRoot()) {
                result = true.also { Log.d(tag, "Accessibility Service disabled successfully by \"root\"") }
            }
            if (isSecureAccessible() && stopWithSecure()) {
                result = true.also { Log.d(tag, "Accessibility Service disabled successfully by \"secure settings\"") }
            }
            return result || false.also { if (withLaunchSettings) launchSettings() }
        }

        @JvmOverloads
        fun startAndWaitFor(timeout: Long = DEFAULT_A11Y_START_TIMEOUT) {
            if (isRunning()) return
            if (startWithConvenientWaysIfPossibleAndWaitFor(DEFAULT_A11Y_START_TIMEOUT)) return
            launchSettings()
            if (!AccessibilityService.waitForStarted(timeout)) {
                throw ScriptInterruptedException()
            }
        }

        private fun startWithConvenientWaysIfPossible() = when {
            startWithRootIfPossible() -> true
            startWithSecureIfPossible() -> true
            else -> false
        }

        private fun startWithConvenientWaysIfPossibleAndWaitFor(timeout: Long = DEFAULT_A11Y_START_TIMEOUT) = when {
            startWithRootIfPossible(timeout) -> true
            startWithSecureIfPossible(timeout) -> true
            else -> false
        }

        fun ensure() {
            if (isRunning()) return
            if (exists()) {
                launchSettings()
                throw ScriptException(context.getString(R.string.text_a11y_service_enabled_but_not_running))
            }
            if (!startWithConvenientWaysIfPossibleAndWaitFor()) {
                throw ScriptException(context.getString(R.string.error_no_accessibility_permission))
            }
        }

        fun launchSettings() {
            this@AccessibilityTool.launchSettings()
        }

        private fun isSecureAccessible() = Pref.shouldStartA11yServiceWithSecureSettings() && SettingsUtils.SecureSettings.isGranted(context)

        private fun isRootAccessible() = Pref.shouldStartA11yServiceWithRoot() && RootUtils.isRootAvailable()

        private fun startWithRoot(timeout: Long? = null): Boolean = when (timeout != null) {
            true -> startWithRoot() && AccessibilityService.waitForStarted(timeout.toLong())
            else -> try {
                stopWithRoot()

                val services = getStartedWithRoot(true)

                val cmdServices = "settings put secure enabled_accessibility_services $services"
                val resultServices = ProcessShell.execCommand(cmdServices, true)

                val cmdState = "settings put secure accessibility_enabled 1"
                val resultState = ProcessShell.execCommand(cmdState, true)

                TextUtils.isEmpty(resultServices.error) && TextUtils.isEmpty(resultState.error)
            } catch (e: Exception) {
                false
            }
        }

        private fun startWithRootIfPossible(timeout: Long? = null) = isRootAccessible() && startWithRoot(timeout)

        private fun startWithSecure(timeout: Long? = null): Boolean = when (timeout != null) {
            true -> startWithSecure() && AccessibilityService.waitForStarted(timeout)
            else -> try {
                stopWithSecure()

                val enabledWithSecure = getStartedWithSecure(true)
                Secure.putString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledWithSecure)
                Secure.putInt(context.contentResolver, Secure.ACCESSIBILITY_ENABLED, 1)

                exists()
            } catch (e: Exception) {
                false
            }
        }

        private fun startWithSecureIfPossible(timeout: Long? = null) = isSecureAccessible() && startWithSecure(timeout)

        private fun stopWithRoot() = try {
            val services = getStartedWithRoot(false)

            val cmdServices = "settings put secure enabled_accessibility_services $services"
            val resultServices = ProcessShell.execCommand(cmdServices, true)

            val cmdState = "settings put secure accessibility_enabled 0"
            val resultState = ProcessShell.execCommand(cmdState, true)

            TextUtils.isEmpty(resultServices.error) && TextUtils.isEmpty(resultState.error)
        } catch (e: Exception) {
            false
        }

        private fun stopWithSecure() = try {
            Secure.putString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES, getStartedWithSecure(false))
            Secure.putInt(context.contentResolver, Secure.ACCESSIBILITY_ENABLED, 0)
            !exists()
        } catch (e: Exception) {
            false
        }

        private fun getStartedWithRoot(withAutoJs: Boolean? = null): String = when (withAutoJs) {
            true -> attachAutoJs(getStartedWithRoot(false))
            false -> detachAutoJs(getStartedWithRoot())
            else -> {
                val cmd = "settings get secure enabled_accessibility_services"
                ProcessShell.execCommand(cmd, true).result.takeUnless { it == "null" }?.replace("\n", "") ?: ""
            }
        }

        private fun getStartedWithSecure(withAutoJs: Boolean? = null): String = when (withAutoJs) {
            true -> attachAutoJs(getStartedWithSecure(false))
            false -> detachAutoJs(getStartedWithSecure())
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