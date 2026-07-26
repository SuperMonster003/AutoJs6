package org.autojs.autojs.core.ui.attribute

import android.content.res.ColorStateList
import android.view.View
import android.widget.CompoundButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class CompoundButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ButtonAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as CompoundButton

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttrs(arrayOf("checked", "check", "isChecked")) { setChecked(it.toBoolean()) }
        registerAttr("buttonDrawable") { view.buttonDrawable = drawables.parse(view, it) }
        registerAttrs(arrayOf("buttonTint", "tint")) { view.buttonTintList = parseTintList(it) }
    }

    private fun setChecked(checked: Boolean) {
        view.isChecked = checked
    }

    protected fun parseTintList(value: String): ColorStateList {
        val colors = parseAttrValue(value)
        return when (colors.size) {
            2 -> ColorStateList(switchStates, colors.map(::parseColor).toIntArray())
            else -> parseColorStateList(colors[0])
        }
    }

    companion object {
        private val switchStates = arrayOf(
            intArrayOf(-android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_checked),
        )
    }

}
