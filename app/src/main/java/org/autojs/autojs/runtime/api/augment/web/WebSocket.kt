package org.autojs.autojs.runtime.api.augment.web

import org.autojs.autojs.core.http.MutableOkHttp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Constructable
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.autojs.autojs.core.web.WebSocket as CoreWebSocket

class WebSocket(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Constructable {

    init {
        originateKeyName()
    }

    override fun construct(vararg args: Any?): Scriptable = ensureArgumentsLengthInRange(args, 1..2) {
        val javaObject = when (it.size) {
            2 -> {
                val clientArg = it[0]
                val client = if (clientArg is MutableOkHttp) clientArg else scriptRuntime.http.okhttp
                val url = coerceString(it[1])
                CoreWebSocket(scriptRuntime, client, url)
            }
            1 -> {
                val url = coerceString(it[0])
                CoreWebSocket(scriptRuntime, scriptRuntime.http.okhttp, url)
            }
            else -> throw ShouldNeverHappenException()
        }
        Context.javaToJS(javaObject, scriptRuntime.topLevelScope) as Scriptable
    }

}