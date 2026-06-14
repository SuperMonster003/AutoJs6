package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs6.BuildConfig
import java.util.Locale

object NodeJavaScriptEngineFactory {

    fun create(context: Context): JavaScriptEngine = when (NodeRuntimeBackend.current()) {
        NodeRuntimeBackend.RHINO -> NodeJavaScriptEngine(context)
        NodeRuntimeBackend.NATIVE -> NativeNodeJavaScriptEngine(context)
        NodeRuntimeBackend.EMBEDDED -> NativeNodeEmbeddedJavaScriptEngine(context)
        NodeRuntimeBackend.AUTO -> when {
            NativeNodeRuntimeBridge.isAvailable -> NativeNodeJavaScriptEngine(context)
            else -> {
                Log.i(TAG, "Native Node.js backend is unavailable; using Rhino compatibility backend.")
                NodeJavaScriptEngine(context)
            }
        }
    }

    private const val TAG = "NodeJavaScriptEngineFactory"

}

enum class NodeRuntimeBackend {

    RHINO,
    NATIVE,
    EMBEDDED,
    AUTO,
    ;

    companion object {

        private const val PROPERTY_NAME = "autojs.nodejs.backend"

        fun current(): NodeRuntimeBackend {
            val raw = System.getProperty(PROPERTY_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.NODEJS_RUNTIME_BACKEND
            return from(raw)
        }

        private fun from(value: String?): NodeRuntimeBackend = when (value?.trim()?.lowercase(Locale.ROOT)) {
            "native", "v8", "node", "nodejs" -> NATIVE
            "embedded", "embed" -> EMBEDDED
            "auto" -> AUTO
            else -> RHINO
        }
    }
}
