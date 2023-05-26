package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import androidx.appcompatlegacy.widget.AppCompatTextView

open class AppCompatTextViewLegacyAttributes(resourceParser: ResourceParser, view: View) : TextViewAttributes(resourceParser, view) {

    override val view = super.view as AppCompatTextView

}
