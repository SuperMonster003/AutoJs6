package org.autojs.autojs.inrt

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R

/**
 * Created by Stardust on Dec 8, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
object Pref {

    private const val KEY_FIRST_USING = "key_first_using"
    private const val KEY_APPLIED_BUILD_ID = "key_inrt_applied_build_id"
    private var sPreferences: SharedPreferences? = null

    private val preferences: SharedPreferences
        get() {
            return sPreferences ?: run {
                val pref = PreferenceManager.getDefaultSharedPreferences(GlobalAppContext.get())
                sPreferences = pref
                pref
            }
        }

    val isFirstUsing: Boolean
        get() {
            val firstUsing = preferences.getBoolean(KEY_FIRST_USING, true)
            if (firstUsing) {
                preferences.edit().putBoolean(KEY_FIRST_USING, false).apply()
            }
            return firstUsing
        }

    private fun getString(res: Int): String {
        return GlobalAppContext.get().getString(res)
    }

    fun shouldEnableAccessibilityServiceByRoot(): Boolean {
        return preferences.getBoolean(getString(R.string.key_enable_a11y_service_with_root_access), true)
    }

    fun shouldHideLogs(): Boolean {
        return preferences.getBoolean(getString(R.string.key_not_showing_main_activity), false)
    }

    fun shouldStopAllScriptsWhenVolumeUp(): Boolean {
        return preferences.getBoolean(getString(R.string.key_use_volume_control_running), true)
    }

    fun shouldRunOnBoot(): Boolean {
        return preferences.getBoolean(getString(R.string.key_run_on_boot_inrt), false)
    }

    @JvmStatic
    fun syncLaunchConfigWithBuild(force: Boolean = false) {
        val context = GlobalAppContext.get()
        val projectConfig = ProjectConfig.fromAssets(context, ProjectConfig.configFileOfDir("project")) ?: return
        val launchConfig = projectConfig.launchConfig
        val buildId = projectConfig.buildInfo.buildId.orEmpty()
        val appliedBuildId = preferences.getString(KEY_APPLIED_BUILD_ID, null)

        val shouldApply = force || buildId != appliedBuildId
        if (!shouldApply) {
            return
        }

        preferences.edit()
            .putBoolean(getString(R.string.key_not_showing_main_activity), !launchConfig.isLogsVisible)
            .putBoolean(getString(R.string.key_run_on_boot_inrt), launchConfig.isRunOnBoot)
            .putString(KEY_APPLIED_BUILD_ID, buildId)
            .apply()

        applyLauncherVisibility(launchConfig.isLauncherVisible)
    }

    private fun applyLauncherVisibility(visible: Boolean) {
        val state = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val context = GlobalAppContext.get()
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, SplashActivity::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}
