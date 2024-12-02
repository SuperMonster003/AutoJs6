package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.AbsSpinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class AbsSpinnerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : AdapterViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as AbsSpinner

}
