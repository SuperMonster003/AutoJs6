package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.AutoCompleteTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class AutoCompleteTextViewAttributes(resourceParser: ResourceParser, view: View) : EditTextAttributes(resourceParser, view) {

    override val view = super.view as AutoCompleteTextView

}
