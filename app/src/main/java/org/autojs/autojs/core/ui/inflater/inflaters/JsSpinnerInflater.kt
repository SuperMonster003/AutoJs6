package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.widget.Spinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.inflater.util.ValueMapper
import org.autojs.autojs.core.ui.widget.JsSpinner
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by Stardust on Nov 29, 2017.
 * Transformed by SuperMonster003 on Apr 12, 2023.
 */
class JsSpinnerInflater(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : AppCompatSpinnerInflater<JsSpinner>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<JsSpinner> = object : ViewCreator<JsSpinner> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsSpinner {
            return attrs.remove("android:spinnerMode")?.let { JsSpinner(context, SPINNER_MODES[it]) } ?: JsSpinner(context)
        }
    }

    companion object {

        val SPINNER_MODES: ValueMapper<Int> = ValueMapper<Int>("spinnerMode")
            .map("dialog", Spinner.MODE_DIALOG)
            .map("dropdown", Spinner.MODE_DROPDOWN)

    }

}