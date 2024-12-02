package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsTextClock
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 23, 2023.
 */
class JsTextClockAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : TextClockAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsTextClock

}