package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Attributes of the `<console>` family of elements. They mirror the `console.setXxx` script API
 * (`titleBackgroundColor` for `console.setTitleBackgroundColor`, `timeVisible` for
 * `console.setTimeVisible`, ...) so a layout can describe what a script would otherwise configure.
 *
 * zh-CN: `<console>` 系列元素的属性. 它们与脚本 API `console.setXxx` 一一对应 (`titleBackgroundColor` 对应 `console.setTitleBackgroundColor`,
 * `timeVisible` 对应 `console.setTimeVisible` 等), 使布局可以描述脚本本需编码配置的内容.
 */
open class ConsoleViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ConsoleView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        // Content text.
        // zh-CN: 内容文本.
        registerAttrs(arrayOf("textSize", "contentTextSize")) { view.textSize = it.toFloat() }
        registerAttrs(arrayOf("textColors", "contentTextColors", "contentTextColor")) { value -> setContentTextColors(value) }
        registerAttr("textColor") { value -> setTextColor(value) }
        registerAttrs(arrayOf("verboseTextColor", "verboseColor")) { view.setVerboseTextColor(parseColor(it)) }
        registerAttrs(arrayOf("debugTextColor", "debugColor", "logTextColor", "logColor")) { view.setDebugTextColor(parseColor(it)) }
        registerAttrs(arrayOf("infoTextColor", "infoColor")) { view.setInfoTextColor(parseColor(it)) }
        registerAttrs(arrayOf("warnTextColor", "warnColor")) { view.setWarnTextColor(parseColor(it)) }
        registerAttrs(arrayOf("errorTextColor", "errorColor")) { view.setErrorTextColor(parseColor(it)) }
        registerAttrs(arrayOf("assertTextColor", "assertColor")) { view.setAssertTextColor(parseColor(it)) }
        registerAttrs(arrayOf("isPinchToZoomEnabled", "pinchToZoomEnabled", "enablePinchToZoom")) { view.setPinchToZoomEnabled(it.toBoolean()) }

        // Display options.
        // zh-CN: 显示选项.
        registerAttrs(arrayOf("timeVisible", "showTime", "timestampVisible")) { view.isTimeVisible = it.toBoolean() }
        registerAttr("timeFormat") { view.timeFormat = it }
        registerAttrs(arrayOf("colorful", "logColoring")) { view.isColorful = it.toBoolean() }
        registerAttrs(arrayOf("inputVisible", "showInput", "inputBarVisible")) { view.isInputBarVisible = it.toBoolean() }

        // Title bar.
        // zh-CN: 标题栏.
        registerAttr("title") {
            view.setTitle(it)
            view.isTitleBarVisible = true
        }
        registerAttrs(arrayOf("titleBarVisible", "showTitleBar")) { view.isTitleBarVisible = it.toBoolean() }
        registerAttr("titleTextSize") { view.titleTextSize = it.toFloat() }
        registerAttr("titleTextColor") { view.titleTextColor = parseColor(it) }
        registerAttr("titleBackgroundColor") { view.setTitleBackgroundColor(parseColor(it)) }
        registerAttr("titleBackgroundTint") { view.setTitleBackgroundTint(parseColor(it)) }
        registerAttr("titleBackgroundAlpha") { view.setTitleBackgroundAlpha(it.toDouble()) }
        registerAttr("titleIconsTint") { view.setTitleIconsTint(parseColor(it)) }

        // Content background.
        // zh-CN: 内容区背景.
        registerAttr("contentBackgroundColor") { view.setContentBackgroundColor(parseColor(it)) }
        registerAttr("contentBackgroundTint") { view.setContentBackgroundTint(parseColor(it)) }
        registerAttr("contentBackgroundAlpha") { view.setContentBackgroundAlpha(it.toDouble()) }

        // Whole console, like console.setBackgroundColor and friends.
        // zh-CN: 整个控制台, 对应 console.setBackgroundColor 等.
        registerAttrs(arrayOf("backgroundColor", "bgColor")) {
            val color = parseColor(it)
            view.setTitleBackgroundColor(color)
            view.setContentBackgroundColor(color)
        }
        registerAttrs(arrayOf("backgroundTint", "bgTint")) {
            val color = parseColor(it)
            view.setTitleBackgroundTint(color)
            view.setContentBackgroundTint(color)
        }
        registerAttr("backgroundAlpha") {
            val alpha = it.toDouble()
            view.setTitleBackgroundAlpha(alpha)
            view.setContentBackgroundAlpha(alpha)
        }
    }

    private fun setContentTextColors(value: String) {
        val colors = parseAttrValue(value).map(::parseColor)
        when (colors.size) {
            1 -> view.setTextColors(Array(6) { colors[0] })
            else -> view.setTextColors(colors.toTypedArray())
        }
    }

    private fun setTextColor(value: String) {
        val color = parseColor(value)
        view.titleTextColor = color
        view.setTextColors(Array(6) { color })
    }

}
