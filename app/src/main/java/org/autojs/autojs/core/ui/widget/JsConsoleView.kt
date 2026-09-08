package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.core.console.ConsoleView

/**
 * The `<console>` element of UI mode: a view of the script runtime's own console (or of the
 * global console when `global="true"` is set). The title bar is hidden until a title attribute
 * asks for it and no time prefix is shown by default, matching the classic AutoJs behaviour.
 *
 * zh-CN: UI 模式的 `<console>` 元素: 脚本运行时自身控制台 (设置 `global="true"` 时为全局控制台) 的视图.
 * 标题栏在设置标题属性前保持隐藏, 且默认不显示时间前缀, 与经典的 AutoJs 行为一致.
 */
open class JsConsoleView : ConsoleView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        isTitleBarVisible = false
        isTimeVisible = false
    }

}
