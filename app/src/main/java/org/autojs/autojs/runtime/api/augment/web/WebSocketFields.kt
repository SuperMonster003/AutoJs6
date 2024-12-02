package org.autojs.autojs.runtime.api.augment.web

import org.autojs.autojs.annotation.AugmentableSimpleGetterProxyInterface
import org.autojs.autojs.runtime.api.augment.SimpleGetterProxy
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import kotlin.reflect.full.declaredMemberProperties
import org.autojs.autojs.core.web.WebSocket as CoreWebSocket

@Suppress("unused", "UNUSED_PARAMETER")
object WebSocketFields : SimpleGetterProxy {

    private val mMembers by lazy { CoreWebSocket.Companion::class.declaredMemberProperties }

    @JvmStatic
    @AugmentableSimpleGetterProxyInterface
    fun get(scope: Scriptable, key: String): Any = runCatching {
        mMembers.find {
            it.name == key
        }?.call(CoreWebSocket.Companion) ?: NOT_FOUND
    }.getOrDefault(NOT_FOUND)

}
