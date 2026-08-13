package org.autojs.autojs.external.shortcut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ScriptShortcutDirectPathPolicyTest {

    @Test
    fun directPathShapeAcceptsLegacyPath() {
        assertEquals(
            "/sdcard/scripts/legacy.js",
            ScriptShortcutDirectPathPolicy.requireDirectPath(
                hasData = false,
                hasJsonExtra = false,
                hasPreExecuteExtra = false,
                path = "/sdcard/scripts/legacy.js",
            ),
        )
    }

    @Test
    fun uriJsonInlineAndPreExecuteShapesFailClosed() {
        assertRejected(ScriptShortcutDirectPathError.URI_UNSUPPORTED) {
            requireDirectPath(hasData = true, path = "/sdcard/scripts/a.lua")
        }
        assertRejected(ScriptShortcutDirectPathError.PAYLOAD_CONFLICT) {
            requireDirectPath(hasJsonExtra = true, path = "/sdcard/scripts/a.lua")
        }
        assertRejected(ScriptShortcutDirectPathError.PAYLOAD_CONFLICT) {
            requireDirectPath(hasPreExecuteExtra = true, path = "/sdcard/scripts/a.lua")
        }
        assertRejected(ScriptShortcutDirectPathError.PATH_REQUIRED) {
            requireDirectPath(path = null)
        }
        assertRejected(ScriptShortcutDirectPathError.PATH_REQUIRED) {
            requireDirectPath(path = "  ")
        }
    }

    private fun requireDirectPath(
        hasData: Boolean = false,
        hasJsonExtra: Boolean = false,
        hasPreExecuteExtra: Boolean = false,
        path: String?,
    ) = ScriptShortcutDirectPathPolicy.requireDirectPath(
        hasData = hasData,
        hasJsonExtra = hasJsonExtra,
        hasPreExecuteExtra = hasPreExecuteExtra,
        path = path,
    )

    private fun assertRejected(expected: ScriptShortcutDirectPathError, block: () -> Unit) {
        try {
            block()
            fail("Expected ScriptShortcutDirectPathException")
        } catch (failure: ScriptShortcutDirectPathException) {
            assertSame(expected, failure.error)
        }
    }
}
