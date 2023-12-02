package org.autojs.autojs.ui.main.drawer

import android.os.Handler
import android.os.Looper

interface IToggleableItem {

    fun toggle(aimState: Boolean)

    fun sync()

    fun sync(callback: Runnable) {
        sync()
        callback.run()
    }

    fun syncDelay(callback: Runnable) {
        Handler(Looper.getMainLooper()).postDelayed({ sync(callback) }, DrawerMenuItemViewHolder.CLICK_TIMEOUT)
    }

}
