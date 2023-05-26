package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.CheckBox
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class CheckBoxInflater<V: CheckBox>(resourceParser: ResourceParser): CompoundButtonInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> CheckBox(context) }

}
