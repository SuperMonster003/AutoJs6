package org.autojs.autojs.runtime.api.modules.web

import org.autojs.autojs.runtime.api.augment.web.WebSocketFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable.NOT_FOUND

/**
 * Freezes the static fields scripts can read from `WebSocket` (`web.newWebSocket` result class): the public
 * constants of `WebSocket.Companion`, nothing else. A change here is a change of the script surface.
 * zh-CN: 固化脚本可从 `WebSocket` (`web.newWebSocket` 返回值的类) 读取的静态字段: `WebSocket.Companion` 的公开常量,
 * 仅此而已. 这里的变化就是脚本表面的变化.
 */
class WebSocketFieldsTest {

    private val scope = NativeObject()

    private val expected = mapOf(
        "CODE_CLOSE_NORMAL" to 1000,
        "CODE_CLOSE_GOING_AWAY" to 1001,
        "CODE_CLOSE_PROTOCOL_ERROR" to 1002,
        "CODE_CLOSE_UNSUPPORTED" to 1003,
        "CODE_CLOSED_NO_STATUS" to 1005,
        "CODE_CLOSE_ABNORMAL" to 1006,
        "CODE_UNSUPPORTED_PAYLOAD" to 1007,
        "CODE_POLICY_VIOLATION" to 1008,
        "CODE_CLOSE_TOO_LARGE" to 1009,
        "CODE_MANDATORY_EXTENSION" to 1010,
        "CODE_SERVER_ERROR" to 1011,
        "CODE_SERVICE_RESTART" to 1012,
        "CODE_TRY_AGAIN_LATER" to 1013,
        "CODE_BAD_GATEWAY" to 1014,
        "CODE_TLS_HANDSHAKE_FAIL" to 1015,
        "EVENT_CLOSED" to "closed",
        "EVENT_CLOSING" to "closing",
        "EVENT_FAILURE" to "failure",
        "EVENT_TEXT" to "text",
        "EVENT_MESSAGE" to "message",
        "EVENT_BYTES" to "bytes",
        "EVENT_OPEN" to "open",
        "EVENT_MAX_REBUILDS" to "max_rebuilds",
    )

    @Test
    fun `exports exactly the public companion constants`() {
        assertEquals(expected.keys, WebSocketFields.exportedNames)
        expected.forEach { (name, value) -> assertEquals(name, value, WebSocketFields.get(scope, name)) }
    }

    @Test
    fun `hides private members, functions and the companion holder`() {
        listOf("TAG", "instances", "DEFAULT_EXIT_ON_CLOSE_TIMEOUT", "Companion", "onExit", "missing").forEach {
            assertSame(it, NOT_FOUND, WebSocketFields.get(scope, it))
        }
    }

}
