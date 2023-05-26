package org.autojs.autojs.core.ui.inflater.inflaters

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTextClock
import org.autojs.autojs6.R

/**
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsTextClockInflater(resourceParser: ResourceParser) : TextClockInflater<JsTextClock>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsTextClock> = ViewCreator { context, _ -> View.inflate(context, R.layout.js_textclock, null) as JsTextClock }

}