package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsWebView
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on May 20, 2023.
 */
class JsWebViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : WebViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsWebView

}