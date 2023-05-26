package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.SeekBar
import org.autojs.autojs.core.ui.inflater.ResourceParser

open class SeekBarAttributes<V : SeekBar>(resourceParser: ResourceParser, view: View) : AbsSeekBarAttributes<V>(resourceParser, view) {

    override val view = super.view as SeekBar

}
