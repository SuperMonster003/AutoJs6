package org.autojs.autojs.core.accessibility.window

import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo

/**
 * [WindowMatch.Facts] over the framework's windows: the package name is that of the window's root
 * (read on demand, `null` when the window is gone), the display is [Display.DEFAULT_DISPLAY] before
 * API 30, where windows carry no display.
 * zh-CN: 框架窗口之上的 [WindowMatch.Facts]: 包名取自窗口根节点 (按需读取, 窗口已消失时为 `null`), API 30 之前窗口不带显示器,
 * 视为 [Display.DEFAULT_DISPLAY].
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
object A11yWindowFacts : WindowMatch.Facts<AccessibilityWindowInfo> {

    override fun type(window: AccessibilityWindowInfo): Int = window.type

    override fun packageName(window: AccessibilityWindowInfo): String? = try {
        window.root?.packageName?.toString()
    } catch (_: Exception) {
        null
    }

    override fun title(window: AccessibilityWindowInfo): String? = try {
        window.title?.toString()
    } catch (_: Exception) {
        null
    }

    override fun displayId(window: AccessibilityWindowInfo): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> window.displayId
        else -> Display.DEFAULT_DISPLAY
    }

    override fun isActive(window: AccessibilityWindowInfo): Boolean = window.isActive

    override fun isFocused(window: AccessibilityWindowInfo): Boolean = window.isFocused

    override fun id(window: AccessibilityWindowInfo): Int = window.id

    override fun layer(window: AccessibilityWindowInfo): Int = window.layer

}
