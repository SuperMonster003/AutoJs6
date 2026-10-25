package org.autojs.autojs.runtime.api.modules.tasks

import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.TopLevel

class WorkManagerCompatibilityTest {

    private lateinit var scope: TopLevel

    @Before
    fun enterContext() {
        scope = with(RhinoUtils) { Context.enter().getOrCreateStandardObjects() }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun proFacadeIgnoresLegacyCompletionOptions() {
        val options = NativeObject().apply {
            put("path", this, "script.js")
            put("callback", this, "callback")
            put("isAsync", this, true)
        }

        val copied = WorkManager.synchronousTaskArguments(scope, arrayOf(options)).single() as NativeObject

        assertEquals("script.js", copied.get("path", copied))
        assertFalse(copied.has("callback", copied))
        assertFalse(copied.has("isAsync", copied))
        assertEquals(Scriptable.NOT_FOUND, copied.get("async", copied))
    }
}
