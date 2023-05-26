package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class AppCompatTextViewAttributes(resourceParser: ResourceParser, view: View): TextViewAttributes(resourceParser, view) {

    override val view = super.view as AppCompatTextView

}
