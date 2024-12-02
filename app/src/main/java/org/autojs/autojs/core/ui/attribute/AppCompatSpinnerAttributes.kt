package org.autojs.autojs.core.ui.attribute

import android.view.View
import androidx.appcompat.widget.AppCompatSpinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class AppCompatSpinnerAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : SpinnerAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as AppCompatSpinner

}
