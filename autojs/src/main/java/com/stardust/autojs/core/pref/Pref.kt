package com.stardust.autojs.core.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.stardust.app.GlobalAppContext
import com.stardust.autojs.R
import org.autojs.autojs.tool.RootTool.RootMode

object Pref {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(GlobalAppContext.get())
    private val DISPOSABLE_BOOLEAN = GlobalAppContext.get().getSharedPreferences("DISPOSABLE_BOOLEAN", Context.MODE_PRIVATE)

    var rootMode: RootMode
        get() {
            val key = preferences.getString(getString(R.string.key_root_mode), RootMode.AUTO_DETECT.key)
            return RootMode.getRootMode(key!!)
        }
        set(mode) {
            preferences.edit()
                .putString(getString(R.string.key_root_mode), mode.key)
                .apply()
        }

    val isStableModeEnabled: Boolean
        get() {
            return preferences.getBoolean(getString(R.string.key_stable_mode), false)
        }

    val isGestureObservingEnabled: Boolean
        get() {
            return preferences.getBoolean(getString(R.string.key_gesture_observing), false)
        }

    val isHiddenFilesShow: Boolean
        get() {
            val key = getString(R.string.key_hidden_files)
            val def = getString(R.string.value_hidden_files_default)
            val valueShow = getString(R.string.key_hidden_files_show)
            return preferences.getString(key, def) == valueShow
        }

    fun isFirstGoToAccessibilitySetting(): Boolean {
        return getDisposableBoolean("isFirstGoToAccessibilitySetting", false)
    }

    @Suppress("SameParameterValue")
    private fun getDisposableBoolean(key: String, defaultValue: Boolean): Boolean {
        val b: Boolean = DISPOSABLE_BOOLEAN.getBoolean(key, defaultValue)
        if (b == defaultValue) {
            DISPOSABLE_BOOLEAN.edit().putBoolean(key, !defaultValue).apply()
        }
        return b
    }

    fun shouldEnableAccessibilityServiceByRoot(): Boolean {
        return def().getBoolean(getString(R.string.key_enable_a11y_service_with_root_access), true)
    }

    fun shouldEnableAccessibilityServiceBySecureSettings(): Boolean {
        return def().getBoolean(getString(R.string.key_enable_a11y_service_with_secure_settings), true)
    }

    private fun def(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(GlobalAppContext.get())
    }

    private fun getString(id: Int): String {
        return GlobalAppContext.getString(id)
    }
}
