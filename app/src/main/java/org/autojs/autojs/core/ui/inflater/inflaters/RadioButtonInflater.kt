package org.autojs.autojs.core.ui.inflater.inflaters

import android.widget.RadioButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

/**
 * Created by SuperMonster003 on May 21, 2023.
 */
open class RadioButtonInflater(resourceParser: ResourceParser) : CompoundButtonInflater<RadioButton>(resourceParser) {

    override fun getCreator(): ViewCreator<in RadioButton> = ViewCreator { context, _ -> RadioButton(context) }

}