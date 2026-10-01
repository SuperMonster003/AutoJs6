package org.autojs.autojs.runtime.api.augment.web

import org.autojs.autojs.runtime.api.augment.SimpleGetterProxy
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import org.autojs.autojs.core.web.WebSocket as CoreWebSocket

@Suppress("unused", "UNUSED_PARAMETER")
object WebSocketFields : SimpleGetterProxy {

    private val mMembers by lazy {
        CoreWebSocket.Companion::class.declaredMemberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .associateBy { it.name }
    }

    internal val exportedNames: Set<String> get() = mMembers.keys

    override fun get(scope: Scriptable, key: String): Any = runCatching {
        mMembers[key]?.call(CoreWebSocket.Companion) ?: NOT_FOUND
    }.getOrDefault(NOT_FOUND)

}
