package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.AbsSpinner
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class AbsSpinnerAttributes(resourceParser: ResourceParser, view: View) : AdapterViewAttributes(resourceParser, view) {

    override val view = super.view as AbsSpinner

}
