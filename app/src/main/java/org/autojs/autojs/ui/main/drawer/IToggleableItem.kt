package org.autojs.autojs.ui.main.drawer

import android.os.Handler
import android.os.Looper
import org.autojs.autojs.theme.preference.Syncable

interface IToggleableItem : Syncable {

    fun toggle(aimState: Boolean)

    override fun sync()

    fun sync(callback: Runnable) {
        sync()
        callback.run()
    }

    fun syncDelay(callback: Runnable) {
        Handler(Looper.getMainLooper()).postDelayed({ sync(callback) }, DrawerMenuItemViewHolder.CLICK_TIMEOUT)
    }

}
