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

    fun active()

    fun toggle()

    fun toggle(aimState: Boolean) {
        if (aimState != isActive) {
            toggle()
        }
    }
}