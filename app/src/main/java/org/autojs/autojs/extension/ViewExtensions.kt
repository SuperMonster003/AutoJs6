package org.autojs.autojs.extension

import android.annotation.SuppressLint
import android.os.Build
import android.widget.PopupMenu

/**
 * Created by SuperMonster003 on May 7, 2025.
 */
object ViewExtensions {

    fun PopupMenu.setForceShowIconCompat() {
        val popup = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true)
        } else {
            runCatching {
                @SuppressLint("DiscouragedPrivateApi")
                val field = PopupMenu::class.java.getDeclaredField("mPopup")
                field.isAccessible = true
                // MenuPopupHelper
                val helper = field.get(popup)
                val method = helper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                method.invoke(helper, true)
            }
        }
    }

}
