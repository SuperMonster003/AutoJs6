package org.autojs.autojs.runtime.api.modules.notice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeObject

class NoticeCompatibilityTest {

    @Test
    fun oneStringUsesTheContentOverload() {
        assertTrue(Notice.shouldUseContentOverload(arrayOf("content")))
        assertTrue(Notice.shouldUseContentOverload(arrayOf<Any?>("content", NativeObject())))
    }

    @Test
    fun explicitStringOrNullSecondArgumentUsesTheTitleContentOverload() {
        assertFalse(Notice.shouldUseContentOverload(arrayOf("title", "content")))
        assertFalse(Notice.shouldUseContentOverload(arrayOf<Any?>("title", null)))
        assertFalse(Notice.shouldUseContentOverload(emptyArray()))
    }

}
