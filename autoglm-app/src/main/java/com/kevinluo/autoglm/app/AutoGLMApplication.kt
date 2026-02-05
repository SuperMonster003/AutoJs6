package com.kevinluo.autoglm.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.kevinluo.autoglm.config.SystemPrompts
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.task.TaskExecutionManager
import com.kevinluo.autoglm.ui.FloatingWindowStateManager
import com.kevinluo.autoglm.util.KeepAliveManager
import com.kevinluo.autoglm.util.LogFileManager
import com.kevinluo.autoglm.util.Logger

/**
 * Application class that manages app-wide lifecycle events.
 *
 * This class is responsible for:
 * - Tracking activity lifecycle to determine foreground/background state
 * - Managing floating window visibility based on app state
 * - Loading custom system prompts from settings on startup
 *
 * The floating window is automatically hidden when the app is in the foreground
 * and shown when the app goes to the background, providing a seamless user experience.
 *
 */
class AutoGLMApplication : Application() {
    /**
     * Counter tracking the number of started (visible) activities.
     * When this reaches 0, the app is considered to be in the background.
     */
    private var activityCount = 0

    override fun onCreate() {
        super.onCreate()

        // Initialize log file manager for file-based logging
        LogFileManager.init(this)

        // Import dev profiles if available (debug builds only)
        importDevProfilesIfNeeded()

        // Load custom system prompts if set
        loadCustomSystemPrompts()

        // 初始化保活状态
        KeepAliveManager.syncFixState(this)

        // Initialize TaskExecutionManager (after ComponentManager is available)
        TaskExecutionManager.initialize(this)

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    // No action needed
                }

                override fun onActivityStarted(activity: Activity) {
                    activityCount++
                    Logger.d(TAG, "Activity started: ${activity.localClassName}, count: $activityCount")

                    // App came to foreground
                    if (activityCount == 1) {
                        FloatingWindowStateManager.onAppForeground()
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    // 同步修复状态
                    KeepAliveManager.syncFixState(this@AutoGLMApplication)
                }

                override fun onActivityPaused(activity: Activity) {
                    // No action needed
                }

                override fun onActivityStopped(activity: Activity) {
                    activityCount--
                    Logger.d(TAG, "Activity stopped: ${activity.localClassName}, count: $activityCount")

                    // App went to background
                    if (activityCount == 0) {
                        FloatingWindowStateManager.onAppBackground(this@AutoGLMApplication)
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                    // No action needed
                }

                override fun onActivityDestroyed(activity: Activity) {
                    // No action needed
                }
            },
        )
    }

    /**
     * Loads custom system prompts from settings if they exist.
     *
     * Checks for custom Chinese and English system prompts stored in settings
     * and applies them to the SystemPrompts configuration.
     */
    private fun loadCustomSystemPrompts() {
        val settingsManager = SettingsManager.getInstance(this)

        settingsManager.getCustomSystemPrompt("cn")?.let { prompt ->
            SystemPrompts.setCustomChinesePrompt(prompt)
            Logger.d(TAG, "Loaded custom Chinese system prompt")
        }

        settingsManager.getCustomSystemPrompt("en")?.let { prompt ->
            SystemPrompts.setCustomEnglishPrompt(prompt)
            Logger.d(TAG, "Loaded custom English system prompt")
        }
    }

    /**
     * Imports dev profiles from assets if available and not already imported.
     *
     * This is used for debug builds to pre-populate model profiles for testing.
     * The dev_profiles.json file is only included in debug builds.
     */
    private fun importDevProfilesIfNeeded() {
        val settingsManager = SettingsManager.getInstance(this)

        // Skip if already imported
        if (settingsManager.hasImportedDevProfiles()) {
            return
        }

        try {
            val json = assets.open("dev_profiles.json").bufferedReader().readText()
            val count = settingsManager.importDevProfiles(json)
            if (count > 0) {
                Logger.i(TAG, "Imported $count dev profiles from assets")
            }
        } catch (e: java.io.FileNotFoundException) {
            // File not found - this is expected for release builds
            Logger.d(TAG, "dev_profiles.json not found in assets (expected for release builds)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to import dev profiles", e)
        }
    }

    companion object {
        private const val TAG = "AutoGLMApplication"
    }
}
