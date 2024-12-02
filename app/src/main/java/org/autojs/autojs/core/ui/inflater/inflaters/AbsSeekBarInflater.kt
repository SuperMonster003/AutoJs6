package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.AbsSeekBar
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class AbsSeekBarInflater<V : AbsSeekBar>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ProgressBarInflater<V>(scriptRuntime, resourceParser) {
    // Empty inflater.
}
