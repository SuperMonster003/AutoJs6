package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.util.ColorUtils

open class ConsoleViewAttributes(resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(resourceParser, view) {

    override val view = super.view as ConsoleView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("textSize") { view.textSize = it.toFloat() }
        registerAttr("textColors") { value -> view.setTextColors(parseAttrValue(value).map { ColorUtils.parse(view, it) }.toTypedArray()) }
        registerAttrs(arrayOf("verboseTextColor", "verboseColor")) { view.setVerboseTextColor(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("debugTextColor", "debugColor")) { view.setDebugTextColor(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("infoTextColor", "infoColor")) { view.setInfoTextColor(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("warnTextColor", "warnColor")) { view.setWarnTextColor(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("errorTextColor", "errorColor")) { view.setErrorTextColor(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("assertTextColor", "assertColor")) { view.setAssertTextColor(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("isPinchToZoomEnabled", "pinchToZoomEnabled", "enablePinchToZoom")) { view.setPinchToZoomEnabled(it.toBoolean()) }
    }

}
