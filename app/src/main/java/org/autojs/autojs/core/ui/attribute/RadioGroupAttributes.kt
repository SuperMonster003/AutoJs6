package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.RadioGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Ids
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
open class RadioGroupAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : LinearLayoutAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as RadioGroup

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("checkedButton") { view.check(Ids.parse(it)) }
    }

}