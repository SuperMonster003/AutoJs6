package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsImageButton

/**
 * Created by SuperMonster003 on May 23, 2023.
 */
class JsImageButtonInflater(resourceParser: ResourceParser) : ImageButtonInflater<JsImageButton>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsImageButton> = ViewCreator { context, _ -> JsImageButton(context) }

}