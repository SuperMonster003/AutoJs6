package org.autojs.autojs.core.ui.attribute

import android.graphics.Typeface
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.runtime.ScriptRuntime

open class SwitchCompatAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : CompoundButtonAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as SwitchCompat

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("showText") { view.showText = it.toBoolean() }
        registerAttr("textOff") { view.textOff = Strings.parse(view, it) }
        registerAttr("textOn") { view.textOn = Strings.parse(view, it) }
        registerAttr("thumbDrawable") { view.thumbDrawable = drawables.parse(view, it) }
        registerAttr("thumbTextPadding") { view.thumbTextPadding = Dimensions.parseToIntPixel(it, view) }
        registerAttr("thumbTint") { view.thumbTintList = parseTintList(view, it) }
        registerAttr("trackDrawable") { view.trackDrawable = drawables.parse(view, it) }
        registerAttr("trackTint") { view.trackTintList = parseTintList(view, it) }
        registerAttr("splitTrack") { view.splitTrack = it.toBoolean() }
        registerAttr("switchMinWidth") { view.switchMinWidth = Dimensions.parseToIntPixel(it, view) }
        registerAttr("switchPadding") { view.switchPadding = Dimensions.parseToIntPixel(it, view) }
        registerAttr("switchTypeface") { view.setSwitchTypeface(Typeface.create(it, view.typeface.style)) }
    }

}
