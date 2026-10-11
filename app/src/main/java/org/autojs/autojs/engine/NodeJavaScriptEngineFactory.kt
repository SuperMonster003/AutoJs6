package org.autojs.autojs.engine

import android.content.Context

object NodeJavaScriptEngineFactory {
    fun create(context: Context): JavaScriptEngine = NodePluginJavaScriptEngine(context)
}
