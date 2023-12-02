package org.autojs.autojs.ui.main.drawer

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog

interface DrawerMenuItemHelper {

    val prompt: MaterialDialog?
        get() = null

    val context: Context

    val isActive: Boolean

    val isInMainThread: Boolean
        get() = false

    fun active(): Boolean

    fun toggle(): Boolean

    fun toggle(aimState: Boolean): Boolean {
        if (aimState == isActive) {
            return true
        }
        return toggle()
    }

    fun refreshSubtitle(aimState: Boolean) {
        /* Nothing to do by default. */
    }

    fun callback(aimState: Boolean) {
        when (aimState == isActive) {
            true -> onToggleSuccess()
            else -> onToggleFailure()
        }
    }

    fun onToggleSuccess() {
        /* Nothing to do by default. */
    }

    fun onToggleFailure() {
        /* Nothing to do by default. */
    }

}