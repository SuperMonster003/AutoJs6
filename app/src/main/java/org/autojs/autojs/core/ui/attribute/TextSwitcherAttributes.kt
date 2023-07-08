package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.TextSwitcher
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings

open class TextSwitcherAttributes(resourceParser: ResourceParser, view: View) : ViewSwitcherAttributes(resourceParser, view) {

    override val view = super.view as TextSwitcher

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("text", "nextText")) { view.setText(Strings.parse(view, it)) }
        registerAttr("currentText") { view.setCurrentText(Strings.parse(view, it)) }
    }

}
