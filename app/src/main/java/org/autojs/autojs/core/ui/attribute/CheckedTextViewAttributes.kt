package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.CheckedTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ColorUtils

open class CheckedTextViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : TextViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as CheckedTextView

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("checkMarkDrawable") { view.checkMarkDrawable = drawables.parse(view, it) }
        registerAttrs(arrayOf("checkMarkTintList", "checkMarkTint")) { view.checkMarkTintList = ColorUtils.toColorStateList(view, it) }
    }

}
