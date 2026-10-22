package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.FLOATING
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain

/**
 * The remembered positions of the floating windows (docs/settings/ROADMAP.md appendix A.1 `floating`, appendix C Q18):
 * the floating button (`CircularMenuWindow`) and the floating controller (`FloatingControllerWindow`), as fractions of
 * the screen so that they fit another screen size. They were states (`other`) until Q18 made them settings that a
 * backup carries to another device. Names are unchanged (R1); the orientation the floating button was last placed in
 * stays a state (`MiscStateKeys.CIRCULAR_MENU_LAST_ORIENTATION`).
 *
 * zh-CN: 悬浮窗记住的位置 (ROADMAP 附录 A.1 `floating`, 附录 C Q18): 悬浮按钮 (`CircularMenuWindow`) 与悬浮控制器
 * (`FloatingControllerWindow`), 以占屏幕的比例存储, 换到其他屏幕尺寸也适用. 它们原为状态 (`other`), Q18 改为设置, 由备份带到其他设备.
 * 名称不变 (R1); 悬浮按钮上次放置时的屏幕方向仍是状态 (`MiscStateKeys.CIRCULAR_MENU_LAST_ORIENTATION`).
 */
object FloatingKeys : SettingsDomain {

    /** Position of the floating button as fractions of the screen (`CircularMenuWindow`). */
    @JvmField
    val CIRCULAR_MENU_POSITION_X_PERCENT = SettingKey.Float(
        name = "org.autojs.autojs.ui.floating.CircularMenuWindow.position.x_percent", category = FLOATING, default = SettingDefault.of(0f),
    )

    @JvmField
    val CIRCULAR_MENU_POSITION_Y_PERCENT = SettingKey.Float(
        name = "org.autojs.autojs.ui.floating.CircularMenuWindow.position.y_percent", category = FLOATING, default = SettingDefault.of(1 - 0.618f),
    )

    /** Position of the floating controller as fractions of the screen (`FloatingControllerWindow`); -1 when not placed yet. */
    @JvmField
    val FLOATING_CONTROLLER_POSITION_X_PERCENT = SettingKey.Float(
        name = "org.autojs.autojs.ui.floating.controller.FloatingControllerWindow.position.x_percent", category = FLOATING, default = SettingDefault.of(-1f),
    )

    @JvmField
    val FLOATING_CONTROLLER_POSITION_Y_PERCENT = SettingKey.Float(
        name = "org.autojs.autojs.ui.floating.controller.FloatingControllerWindow.position.y_percent", category = FLOATING, default = SettingDefault.of(-1f),
    )

    override val keys: List<PrefKey<*>> = listOf(
        CIRCULAR_MENU_POSITION_X_PERCENT, CIRCULAR_MENU_POSITION_Y_PERCENT,
        FLOATING_CONTROLLER_POSITION_X_PERCENT, FLOATING_CONTROLLER_POSITION_Y_PERCENT,
    )
}
