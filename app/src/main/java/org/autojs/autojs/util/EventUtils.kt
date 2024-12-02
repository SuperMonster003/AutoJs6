package org.autojs.autojs.util

import android.view.KeyEvent

object EventUtils {

    fun isKeyBackAndActionUp(e: KeyEvent) = e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP

    fun isKeyVolumeDownAndActionDown(e: KeyEvent) = e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && e.action == KeyEvent.ACTION_DOWN

    fun isKeyVolumeUpAndActionDown(e: KeyEvent) = e.keyCode == KeyEvent.KEYCODE_VOLUME_UP && e.action == KeyEvent.ACTION_DOWN

}