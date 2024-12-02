package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.SeekBar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class SeekBarAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : AbsSeekBarAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as SeekBar

}
