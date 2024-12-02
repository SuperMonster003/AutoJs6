package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.AdapterView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class AdapterViewInflater<V : AdapterView<*>>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ViewGroupInflater<V>(scriptRuntime, resourceParser) {
    // Empty inflater.
}
