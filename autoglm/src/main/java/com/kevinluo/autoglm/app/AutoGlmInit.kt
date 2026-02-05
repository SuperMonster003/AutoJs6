package com.kevinluo.autoglm.app

import android.app.Application
import android.content.Context
import com.kevinluo.autoglm.config.SystemPrompts
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.task.TaskExecutionManager
import com.kevinluo.autoglm.ui.FloatingWindowStateManager
import com.kevinluo.autoglm.util.KeepAliveManager
import com.kevinluo.autoglm.util.LogFileManager
import com.kevinluo.autoglm.util.Logger

object AutoGlmInit {
    private const val TAG = "AutoGlmInit"

    @Volatile
    private var initialized = false

    /**
     * Initializes AutoGLM runtime in host app process.
     * Safe to call multiple times.
     */
    fun ensureInitialized(context: Context) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            val appContext = context.applicationContext
            val application = appContext as? Application

            // 1) Logging
            LogFileManager.init(appContext)

            // 2) Import dev profiles (best-effort)
            importDevProfilesIfNeeded(appContext)

            // 3) Load custom system prompts
            loadCustomSystemPrompts(appContext)

            // 4) Keep-alive state
            KeepAliveManager.syncFixState(appContext)

            // 5) Task manager core init
            TaskExecutionManager.initialize(appContext)

            // 6) Foreground/background callbacks (optional but keeps original behavior)
            if (application != null) {
                application.registerActivityLifecycleCallbacks(
                    AutoGlmLifecycleCallbacks(appContext)
                )
            } else {
                Logger.w(TAG, "Application is null; lifecycle callbacks not registered")
            }

            initialized = true
            Logger.i(TAG, "AutoGLM initialized")
        }
    }

    private fun loadCustomSystemPrompts(context: Context) {
        val settingsManager = SettingsManager.getInstance(context)

        settingsManager.getCustomSystemPrompt("cn")?.let { prompt ->
            SystemPrompts.setCustomChinesePrompt(prompt)
            Logger.d(TAG, "Loaded custom Chinese system prompt")
        }

        settingsManager.getCustomSystemPrompt("en")?.let { prompt ->
            SystemPrompts.setCustomEnglishPrompt(prompt)
            Logger.d(TAG, "Loaded custom English system prompt")
        }
    }

    private fun importDevProfilesIfNeeded(context: Context) {
        val settingsManager = SettingsManager.getInstance(context)
        if (settingsManager.hasImportedDevProfiles()) return

        try {
            val json = context.assets.open("dev_profiles.json").bufferedReader().readText()
            val count = settingsManager.importDevProfiles(json)
            if (count > 0) {
                Logger.i(TAG, "Imported $count dev profiles from assets")
            }
        } catch (_: java.io.FileNotFoundException) {
            Logger.d(TAG, "dev_profiles.json not found in assets (expected for release builds)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to import dev profiles", e)
        }
    }
}