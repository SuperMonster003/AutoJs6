package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.core.ui.widget.JsButton

class JsButtonInflater(resourceParser: ResourceParser) : ButtonInflater<JsButton>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsButton> = ViewCreator { context, attrs ->

        fun hasTrueAttr(name: String) = attrs["android:$name"] == "true"

        attrs["style"]?.let { return@ViewCreator JsButton(context, null, 0, Res.parseStyle(context, it)) }

        if (hasTrueAttr("isBorderlessColored") || hasTrueAttr("isColoredBorderless")) {
            return@ViewCreator JsButton(context, null, 0, androidx.appcompat.R.style.Widget_AppCompat_Button_Borderless_Colored)
        }
        if (hasTrueAttr("isColored")) {
            if (hasTrueAttr("isBorderless")) {
                return@ViewCreator JsButton(context, null, 0, androidx.appcompat.R.style.Widget_AppCompat_Button_Borderless_Colored)
            }
            return@ViewCreator JsButton(context, null, 0, androidx.appcompat.R.style.Widget_AppCompat_Button_Colored)
        }
        if (hasTrueAttr("isBorderless")) {
            return@ViewCreator JsButton(context, null, 0, androidx.appcompat.R.style.Widget_AppCompat_Button_Borderless)
        }
        return@ViewCreator JsButton(context)
    }


}