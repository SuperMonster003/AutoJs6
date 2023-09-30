package org.autojs.autojs.inrt

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/12/8.
 */
object Pref {

    private const val KEY_FIRST_USING = "key_first_using"
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
}
