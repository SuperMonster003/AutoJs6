package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.AutoCompleteTextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class AutoCompleteTextViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : EditTextAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as AutoCompleteTextView

}
