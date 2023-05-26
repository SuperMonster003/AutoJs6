package org.autojs.autojs.core.ui.attribute

import android.content.res.ColorStateList
import android.view.View
import android.widget.CompoundButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.util.ColorUtils

open class CompoundButtonAttributes(resourceParser: ResourceParser, view: View) : ButtonAttributes(resourceParser, view) {

    override val view = super.view as CompoundButton

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttrs(arrayOf("checked", "check", "isChecked")) { setChecked(it.toBoolean()) }
        registerAttr("buttonDrawable") { view.buttonDrawable = drawables.parse(view, it) }
        registerAttrs(arrayOf("buttonTint", "tint")) { view.buttonTintList = parseTintList(view, it) }
    }

    private fun setChecked(checked: Boolean) {
        view.isChecked = checked
    }

    companion object {

        private val switchStates = arrayOf(
            intArrayOf(-android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_checked),
        )

        fun parseTintList(view: View, value: String): ColorStateList {
            val colors = parseAttrValue(value)
            return when (colors.size) {
                2 -> ColorStateList(switchStates, colors.map { ColorUtils.parse(view, it) }.toIntArray())
                else -> ColorUtils.parse(view, colors[0]).let { ColorStateList(switchStates, intArrayOf(it, it)) }
            }
        }

    }

}