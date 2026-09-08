package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet

/**
 * The `<globalconsole>` element of UI mode, equivalent to `<console global="true">`: a view of
 * the process-wide console every script prints into (still without a time prefix by default).
 *
 * zh-CN: UI 模式的 `<globalconsole>` 元素, 等价于 `<console global="true">`: 所有脚本都会打印到的进程级控制台的视图 (默认依然不显示时间前缀).
 */
class JsGlobalConsoleView : JsConsoleView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

}
