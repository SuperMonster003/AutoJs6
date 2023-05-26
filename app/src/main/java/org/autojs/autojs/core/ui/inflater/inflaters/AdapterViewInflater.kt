package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.AdapterView
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class AdapterViewInflater<V : AdapterView<*>>(resourceParser: ResourceParser) : ViewGroupInflater<V>(resourceParser) {
    // Empty inflater.
}
