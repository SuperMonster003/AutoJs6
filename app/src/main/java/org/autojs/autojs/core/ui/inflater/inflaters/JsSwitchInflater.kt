package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsSwitch

class JsSwitchInflater(resourceParser: ResourceParser) : SwitchCompatInflater<JsSwitch>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsSwitch> = ViewCreator { context, _ -> JsSwitch(context) }

}