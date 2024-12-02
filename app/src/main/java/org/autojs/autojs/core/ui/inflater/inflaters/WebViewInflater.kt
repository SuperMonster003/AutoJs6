package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.runtime.ScriptRuntime

open class WebViewInflater<V: WebView>(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser): ViewGroupInflater<V>(scriptRuntime, resourceParser) {

    override fun getCreator(): ViewCreator<in V> = object : ViewCreator<WebView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?): WebView {
            return WebView(context)
        }
    }

}