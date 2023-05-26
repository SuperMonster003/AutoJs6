package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.AbsSpinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AbsSpinnerInflater<V : AbsSpinner>(resourceParser: ResourceParser) : AdapterViewInflater<V>(resourceParser) {
    // Empty inflater.
}
