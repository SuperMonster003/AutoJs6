package org.autojs.autojs.core.ui.inflater.inflaters

import androidx.appcompat.widget.AppCompatSpinner
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class AppCompatSpinnerInflater<V : AppCompatSpinner>(resourceParser: ResourceParser) : SpinnerInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> AppCompatSpinner(context) }

}
