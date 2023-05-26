package org.autojs.autojs.core.ui.inflater.inflaters

import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.JsWebView

/**
 * Created by SuperMonster003 on May 15, 2023.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsWebViewInflater(resourceParser: ResourceParser) : WebViewInflater<JsWebView>(resourceParser) {

    override fun getCreator(): ViewCreator<in JsWebView> = ViewCreator { context, _ -> JsWebView(context) }

}