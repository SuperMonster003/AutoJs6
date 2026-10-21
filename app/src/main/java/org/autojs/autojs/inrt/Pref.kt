package org.autojs.autojs.inrt

import android.content.ComponentName
import android.content.pm.PackageManager
import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.project.LaunchConfig
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R

/**
 * Created by Stardust on Dec 8, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
object Pref {

    private const val KEY_FIRST_USING = "key_first_using"
    private const val KEY_APPLIED_BUILD_ID = "key_inrt_applied_build_id"
    // The default preferences through the settings store layer (docs/settings/ROADMAP.md P2.6, T13); keys, defaults and
    // write times stay those of inrt.
    // zh-CN: 经设置存储层访问默认偏好 (ROADMAP P2.6, T13); 键, 默认值与写入时机保持 inrt 原样.
    private val store: SettingsStore
        get() = SettingsStores.default

    val isFirstUsing: Boolean
        get() {
            val firstUsing = store.getBoolean(KEY_FIRST_USING, true)
            if (firstUsing) {
                store.put(KEY_FIRST_USING, false)
            }
            return firstUsing
        }

    private fun getString(res: Int): String {
        return GlobalAppContext.get().getString(res)
    }

    fun shouldEnableAccessibilityServiceByRoot(): Boolean {
        return store.getBoolean(getString(R.string.key_enable_a11y_service_with_root_access), true)
    }

    fun shouldHideLogs(): Boolean {
        return store.getBoolean(getString(R.string.key_not_showing_main_activity), false)
    }

    fun shouldStopAllScriptsWhenVolumeUp(): Boolean {
        return store.getBoolean(getString(R.string.key_use_volume_control_running), true)
    }

    fun shouldRunOnBoot(): Boolean {
        return store.getBoolean(getString(R.string.key_run_on_boot_inrt), false)
    }

    @JvmStatic
    fun syncLaunchConfigWithBuild(force: Boolean = false) {
        val context = GlobalAppContext.get()
        val projectConfig = ProjectConfig.fromAssets(context, ProjectConfig.configFileOfDir("project")) ?: return
        val launchConfig = projectConfig.launchConfig
        val buildId = projectConfig.buildInfo.buildId.orEmpty()
        val appliedBuildId = store.getString(KEY_APPLIED_BUILD_ID, null)

        val shouldApply = force || buildId != appliedBuildId
        updateLauncherVisibility(launchConfig)
        if (!shouldApply) {
            return
        }

        store.edit {
            putBoolean(getString(R.string.key_not_showing_main_activity), !launchConfig.isLogsVisible)
            putBoolean(getString(R.string.key_run_on_boot_inrt), launchConfig.isRunOnBoot)
            putString(KEY_APPLIED_BUILD_ID, buildId)
        }

    }

    fun updateLauncherVisibility(config: LaunchConfig) {
        val context = GlobalAppContext.get()
        // Keep a recovery entry until a project that requires broad file access can run.
        val visible = config.isLauncherVisible || (config.requiresSharedStorage && !InrtStorageAccess.has(context))
        val state = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, SplashActivity::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}
