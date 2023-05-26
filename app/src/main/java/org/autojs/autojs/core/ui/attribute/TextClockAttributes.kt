package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.TextClock
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings

open class TextClockAttributes(resourceParser: ResourceParser, view: View) : TextViewAttributes(resourceParser, view) {

    override val view = super.view as TextClock

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("format12Hour") { view.format12Hour = Strings.parse(view, it) }
        registerAttr("format24Hour") { view.format24Hour = Strings.parse(view, it) }
        registerAttr("timeZone") { view.timeZone = Strings.parse(view, it) }
    }

}
