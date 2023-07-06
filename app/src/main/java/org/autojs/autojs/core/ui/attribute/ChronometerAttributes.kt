package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.Chronometer
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Strings

open class ChronometerAttributes(resourceParser: ResourceParser, view: View) : TextViewAttributes(resourceParser, view) {

    override val view = super.view as Chronometer

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("base") { view.base = it.toLong() }
        registerAttrs(arrayOf("isCountDown", "countDown")) { view.isCountDown = it.toBoolean() }
        registerAttr("format") { view.format = Strings.parse(view, it) }
        registerAttrs(arrayOf("autoStart", "isAutoStart")) { if (it.toBoolean()) view.start() }
    }

}
