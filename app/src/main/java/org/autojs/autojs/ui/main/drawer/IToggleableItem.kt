package org.autojs.autojs.ui.main.drawer

import android.os.Handler
import android.os.Looper

interface IToggleableItem {

    fun toggle()

    fun toggle(aimState: Boolean)

    fun sync()

    fun syncDelay() {
        Handler(Looper.getMainLooper()).postDelayed({ sync() }, DrawerMenuItemViewHolder.CLICK_TIMEOUT)
    }

}
