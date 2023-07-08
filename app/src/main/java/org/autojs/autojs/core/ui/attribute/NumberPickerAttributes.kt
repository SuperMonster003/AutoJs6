package org.autojs.autojs.core.ui.attribute

import android.os.Build
import android.view.View
import android.widget.NumberPicker
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.util.ColorUtils

open class NumberPickerAttributes(resourceParser: ResourceParser, view: View) : LinearLayoutAttributes(resourceParser, view) {

    override val view = super.view as NumberPicker

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("maxValue", "max")) { view.maxValue = it.toInt() }
        registerAttrs(arrayOf("minValue", "min")) { view.minValue = it.toInt() }
        registerAttrs(arrayOf("onLongPressUpdateInterval", "longPressUpdateInterval")) { view.setOnLongPressUpdateInterval(it.toLong()) }
        registerAttr("wrapSelectorWheel") { view.wrapSelectorWheel = it.toBoolean() }
        registerAttrs(arrayOf("value", "selectedIndex", "currentIndex")) { view.value = it.toInt() }

        registerAttrs(arrayOf("textColor", "color")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.textColor = ColorUtils.parse(view, it)
            }
        }

        registerAttrs(arrayOf("textSize", "size")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.textSize = Dimensions.parseToPixel(it, view)
            }
        }

        registerAttr("selectionDividerHeight") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.selectionDividerHeight = it.toInt()
            }
        }

        registerAttrs(arrayOf("displayedValues", "values")) {
            val strings = parseAttrValue(Strings.parse(view, it)).toTypedArray()
            val actualLength = strings.size
            if (actualLength == 0) {
                return@registerAttrs
            }
            val expectedLength = view.maxValue - view.minValue + 1
            if (expectedLength != actualLength) {
                view.minValue = 0
                view.maxValue = actualLength - 1
            }
            view.displayedValues = strings
        }

        registerAttrs(arrayOf("distinctDisplayedValues", "distinctValues")) {
            val strings = parseAttrValue(Strings.parse(view, it)).toSet().toTypedArray()
            val actualLength = strings.size
            if (actualLength == 0) {
                return@registerAttrs
            }
            val expectedLength = view.maxValue - view.minValue + 1
            if (expectedLength != actualLength) {
                view.minValue = 0
                view.maxValue = actualLength - 1
            }
            view.displayedValues = strings
        }
    }

}
