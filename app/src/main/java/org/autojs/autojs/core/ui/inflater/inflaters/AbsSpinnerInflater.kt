package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.AbsSpinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class AbsSpinnerInflater<V : AbsSpinner>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : AdapterViewInflater<V>(scriptRuntime, resourceParser) {
    // Empty inflater.
}
