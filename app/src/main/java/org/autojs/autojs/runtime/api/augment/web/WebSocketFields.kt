package org.autojs.autojs.runtime.api.augment.web

import org.autojs.autojs.runtime.api.augment.SimpleGetterProxy
import org.autojs.autojs.util.CompanionProperties
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.autojs.autojs.core.web.WebSocket as CoreWebSocket

@Suppress("unused", "UNUSED_PARAMETER")
object WebSocketFields : SimpleGetterProxy {

    // Public properties of WebSocket.Companion (close codes and event names), resolved once with Java reflection.
    // zh-CN: WebSocket.Companion 的公开属性 (关闭码与事件名), 用 Java 反射解析一次.
    private val mMembers by lazy { CompanionProperties.publicGetters(CoreWebSocket::class.java, CoreWebSocket.Companion) }

    internal val exportedNames: Set<String> get() = mMembers.keys

    override fun get(scope: Scriptable, key: String): Any = runCatching {
        mMembers[key]?.invoke() ?: NOT_FOUND
    }.getOrDefault(NOT_FOUND)

}
