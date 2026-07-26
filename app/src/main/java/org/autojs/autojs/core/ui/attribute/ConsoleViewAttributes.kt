package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class ConsoleViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : FrameLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as ConsoleView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("textSize") { view.textSize = it.toFloat() }
        registerAttr("textColors") { value -> view.setTextColors(parseAttrValue(value).map(::parseColor).toTypedArray()) }
        registerAttrs(arrayOf("verboseTextColor", "verboseColor")) { view.setVerboseTextColor(parseColor(it)) }
        registerAttrs(arrayOf("debugTextColor", "debugColor")) { view.setDebugTextColor(parseColor(it)) }
        registerAttrs(arrayOf("infoTextColor", "infoColor")) { view.setInfoTextColor(parseColor(it)) }
        registerAttrs(arrayOf("warnTextColor", "warnColor")) { view.setWarnTextColor(parseColor(it)) }
        registerAttrs(arrayOf("errorTextColor", "errorColor")) { view.setErrorTextColor(parseColor(it)) }
        registerAttrs(arrayOf("assertTextColor", "assertColor")) { view.setAssertTextColor(parseColor(it)) }
        registerAttrs(arrayOf("isPinchToZoomEnabled", "pinchToZoomEnabled", "enablePinchToZoom")) { view.setPinchToZoomEnabled(it.toBoolean()) }
    }

}
