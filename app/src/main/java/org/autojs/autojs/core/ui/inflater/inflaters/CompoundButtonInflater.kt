package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.CompoundButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class CompoundButtonInflater<V : CompoundButton>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser) : ButtonInflater<V>(scriptRuntime, resourceParser) {
    // Empty inflater.
}
