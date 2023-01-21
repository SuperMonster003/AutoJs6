package org.autojs.autojs.ui.main.drawer

import android.content.Context

abstract class DrawerMenuItemCustomHelper internal constructor(private val mContext: Context) : DrawerMenuItemHelper {

    override val context: Context
        get() = mContext

    override fun active() {
        if (!isActive) {
            toggle()
        }
    }

}