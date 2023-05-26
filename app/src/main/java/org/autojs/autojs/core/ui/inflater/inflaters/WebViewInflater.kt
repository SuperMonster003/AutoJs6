package org.autojs.autojs.core.ui.inflater.inflaters

import android.webkit.WebView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator

open class WebViewInflater<V: WebView>(resourceParser: ResourceParser): ViewGroupInflater<V>(resourceParser) {

    override fun getCreator(): ViewCreator<in V> = ViewCreator { context, _ -> WebView(context) }

}
