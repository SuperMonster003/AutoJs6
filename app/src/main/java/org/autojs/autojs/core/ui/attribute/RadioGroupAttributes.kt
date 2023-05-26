package org.autojs.autojs.core.ui.attribute

import android.view.View
import android.widget.RadioGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Ids

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
open class RadioGroupAttributes(resourceParser: ResourceParser, view: View) : LinearLayoutAttributes(resourceParser, view) {

    override val view = super.view as RadioGroup

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("checkedButton") { view.check(Ids.parse(it)) }
    }

}