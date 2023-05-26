package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.appcompat.widget.AppCompatSpinner
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class AppCompatSpinnerAttributes(resourceParser: ResourceParser, view: View) : SpinnerAttributes(resourceParser, view) {

    override val view = super.view as AppCompatSpinner

}
