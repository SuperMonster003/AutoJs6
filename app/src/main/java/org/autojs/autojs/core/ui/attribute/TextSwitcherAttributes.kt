package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.TextSwitcher
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.runtime.ScriptRuntime

open class TextSwitcherAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ViewSwitcherAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as TextSwitcher

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("text", "nextText")) { view.setText(Strings.parse(view, it)) }
        registerAttr("currentText") { view.setCurrentText(Strings.parse(view, it)) }
    }

}
