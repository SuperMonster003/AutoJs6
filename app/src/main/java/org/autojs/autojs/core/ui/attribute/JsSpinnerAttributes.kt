package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.inflaters.TextViewInflater
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.widget.JsSpinner
import org.autojs.autojs.util.ColorUtils

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
class JsSpinnerAttributes(resourceParser: ResourceParser, view: View) : AppCompatSpinnerAttributes(resourceParser, view) {

    override val view = super.view as JsSpinner

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("entries", ::setAdapter)
        registerAttr("entryTextColor") { view.entryTextColor = ColorUtils.parse(view.context, it) }
        registerAttr("entryTextSize") { view.entryTextSize = Dimensions.parseToPixel(it, view) }
        registerAttr("entryTextStyle") { view.entryTextStyle = TextViewInflater.TEXT_STYLES.split(it) }
        registerAttr("textColor") { view.textColor = ColorUtils.parse(view.context, it) }
        registerAttr("textSize") { view.textSize = Dimensions.parseToPixel(it, view) }
        registerAttr("textStyle") { view.textStyle = TextViewInflater.TEXT_STYLES.split(it) }

        registerAttrUnsupported(
            arrayOf(
                "dropDownSelector"
            )
        )
    }

    private fun setAdapter(entries: String) {
        view.adapter = view.Adapter(
            view.context,
            android.R.layout.simple_spinner_dropdown_item,
            entries.split("[|]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
    }

}