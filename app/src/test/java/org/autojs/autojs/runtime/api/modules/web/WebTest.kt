package org.autojs.autojs.runtime.api.modules.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject

class WebTest {

    @Test
    fun `web socket events do not use the current timer by default`() {
        assertFalse(Web.parseEventThread(null))
        assertFalse(Web.parseEventThread(NativeObject()))
    }

    @Test
    fun `web socket eventThread this selects the current timer`() {
        assertTrue(Web.parseEventThread(options("eventThread" to "this")))
        assertFalse(Web.parseEventThread(options("eventThread" to "main")))
    }

    @Test
    fun `web socket options must be an object`() {
        assertThrows(IllegalArgumentException::class.java) {
            Web.parseEventThread("this")
        }
    }

    private fun options(vararg entries: Pair<String, Any?>) = NativeObject().apply {
        entries.forEach { (key, value) -> put(key, this, value) }
    }
}
