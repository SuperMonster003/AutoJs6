package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.Spinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class SpinnerInflater<V : Spinner>(resourceParser: ResourceParser) : AbsSpinnerInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> Spinner(context) }

}
