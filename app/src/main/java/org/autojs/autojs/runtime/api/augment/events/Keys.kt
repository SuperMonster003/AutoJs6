package org.autojs.autojs.runtime.api.augment.events

import android.view.KeyEvent
import org.autojs.autojs.runtime.api.augment.Augmentable

object Keys : Augmentable() {

    override val selfAssignmentProperties = listOf(

        "home" to KeyEvent.KEYCODE_HOME,
        "HOME" to KeyEvent.KEYCODE_HOME,

        "menu" to KeyEvent.KEYCODE_MENU,
        "MENU" to KeyEvent.KEYCODE_MENU,

        "back" to KeyEvent.KEYCODE_BACK,
        "BACK" to KeyEvent.KEYCODE_BACK,

        "volumeUp" to KeyEvent.KEYCODE_VOLUME_UP,
        "volume_up" to KeyEvent.KEYCODE_VOLUME_UP,
        "VOLUME_UP" to KeyEvent.KEYCODE_VOLUME_UP,

        "volumeDown" to KeyEvent.KEYCODE_VOLUME_DOWN,
        "volume_down" to KeyEvent.KEYCODE_VOLUME_DOWN,
        "VOLUME_DOWN" to KeyEvent.KEYCODE_VOLUME_DOWN,

    )

}