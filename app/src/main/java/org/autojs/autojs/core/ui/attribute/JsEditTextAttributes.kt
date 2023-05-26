package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsEditText

class JsEditTextAttributes(resourceParser: ResourceParser, view: View) : EditTextAttributes(resourceParser, view) {

    override val view = super.view as JsEditText

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("selectAll") {
            if (view.text.isNotEmpty() && it.toBoolean()) {
                view.requestFocus()
                view.selectAll()
            }
        }
        registerAttr("extendsSelection") {
            if (view.text.isNotEmpty()) {
                view.requestFocus()
                view.extendSelection(it.toInt())
            }
        }
        registerAttr("setSelection") {
            if (view.text.isEmpty()) {
                return@registerAttr
            }
            if (it.matches(Regex("[+-]?\\d+(\\.\\d+)?"))) {
                view.requestFocus()
                return@registerAttr view.setSelection(it.toInt())
            }
            if (it.matches(Regex("[+-]?\\d+(\\.\\d+)?[,\\s]+[+-]?\\d+(\\.\\d+)?"))) {
                view.requestFocus()
                val (startIndex, stopIndex) = it.split(Regex("[,\\s]+"))
                return@registerAttr view.setSelection(startIndex.toInt(), stopIndex.toInt())
            }
            throw Exception("Can't resolve attr { name: setSelection, value: $it }")
        }

    }

}