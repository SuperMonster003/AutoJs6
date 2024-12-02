package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.RadioButton
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 21, 2023.
 */
open class RadioButtonAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : CompoundButtonAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as RadioButton

}