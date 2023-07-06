package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsTextClock
import org.autojs.autojs6.R

/**
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsTextClockInflater(resourceParser: ResourceParser) : TextClockInflater<JsTextClock>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsTextClock> = object : ViewCreator<JsTextClock> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): JsTextClock {
            return View.inflate(context, R.layout.js_textclock, null) as JsTextClock
        }
    }

}