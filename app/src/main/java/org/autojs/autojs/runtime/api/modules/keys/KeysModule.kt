package org.autojs.autojs.runtime.api.modules.keys

import android.view.KeyEvent
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty

/**
 * The `keys` module on the new script module layer (roadmap P3.3): the key codes of the keys a script listens to with
 * `events.observeKey` / `events.onKeyDown` (the names of the current module, in its order).
 *
 * zh-CN: 新脚本模块层上的 keys 模块 (P3.3): 脚本以 events.observeKey / events.onKeyDown 监听的按键的键码 (名称与顺序同现有模块).
 */
@ScriptModule(name = "keys")
object KeysModule {

    @ScriptProperty(name = "home")
    val home = KeyEvent.KEYCODE_HOME

    @ScriptProperty(name = "HOME")
    val homeUpper = KeyEvent.KEYCODE_HOME

    @ScriptProperty(name = "menu")
    val menu = KeyEvent.KEYCODE_MENU

    @ScriptProperty(name = "MENU")
    val menuUpper = KeyEvent.KEYCODE_MENU

    @ScriptProperty(name = "back")
    val back = KeyEvent.KEYCODE_BACK

    @ScriptProperty(name = "BACK")
    val backUpper = KeyEvent.KEYCODE_BACK

    @ScriptProperty(name = "volumeUp")
    val volumeUp = KeyEvent.KEYCODE_VOLUME_UP

    @ScriptProperty(name = "volume_up")
    val volumeUpSnake = KeyEvent.KEYCODE_VOLUME_UP

    @ScriptProperty(name = "VOLUME_UP")
    val volumeUpUpper = KeyEvent.KEYCODE_VOLUME_UP

    @ScriptProperty(name = "volumeDown")
    val volumeDown = KeyEvent.KEYCODE_VOLUME_DOWN

    @ScriptProperty(name = "volume_down")
    val volumeDownSnake = KeyEvent.KEYCODE_VOLUME_DOWN

    @ScriptProperty(name = "VOLUME_DOWN")
    val volumeDownUpper = KeyEvent.KEYCODE_VOLUME_DOWN

}
