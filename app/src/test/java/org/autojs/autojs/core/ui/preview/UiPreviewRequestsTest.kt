package org.autojs.autojs.core.ui.preview

import org.autojs.plugin.ui.preview.UiPreviewContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UiPreviewRequestsTest {

    private fun params(
        mode: String = UiPreviewContract.MODE_SNAPSHOT,
        width: Int = 1080,
        height: Int = 1920,
        densityDpi: Int = 0,
        nightMode: Int = 0,
        localeTag: String = "",
        fontScale: Float = 0f,
        displayId: Int = 0,
    ) = UiPreviewSessionParams(mode, width, height, densityDpi, nightMode, localeTag, fontScale, displayId, "label", 1L)

    private fun codeOf(block: () -> Unit): String? = try {
        block()
        null
    } catch (e: UiPreviewRequestException) {
        e.code
    }

    @Test
    fun everyServedContractVersionCreatesSessions() {
        // Versions only append, so 1.6.0 (contractVersion 1), 1.7.0 (2) and 1.8.0 (3) clients keep working beside v4.
        val params = UiPreviewSessionParams(UiPreviewContract.MODE_SNAPSHOT, 100, 100, 0, 0, "", 0f, 0, "label", 1L)
        assertNull(codeOf { UiPreviewRequests.validateSession(params, UiPreviewContract.BASE_VERSION, 24) })
        assertNull(codeOf { UiPreviewRequests.validateSession(params, UiPreviewContract.VERSION_V2, 24) })
        assertNull(codeOf { UiPreviewRequests.validateSession(params, UiPreviewContract.VERSION_V3, 24) })
        assertNull(codeOf { UiPreviewRequests.validateSession(params, UiPreviewContract.VERSION, 24) })
        assertEquals(UiPreviewContract.ERROR_CONTRACT_MISMATCH, codeOf { UiPreviewRequests.validateSession(params, UiPreviewContract.VERSION + 1, 24) })
        assertEquals(UiPreviewContract.ERROR_CONTRACT_MISMATCH, codeOf { UiPreviewRequests.validateSession(params, 0, 24) })
    }

    @Test
    fun scriptRunsNeedAnEmbeddedSessionOnApi33AndAUiScript() {
        val script = "\"ui\";\nui.layout(<vertical/>);\n"
        assertNull(codeOf { UiPreviewRequests.validateScript(script, UiPreviewContract.MODE_EMBEDDED, 33) })
        assertNull(codeOf { UiPreviewRequests.validateScript("// comment\n'ui';\nui.layout(<frame/>);", UiPreviewContract.MODE_EMBEDDED, 35) })
        assertEquals(UiPreviewContract.ERROR_UNSUPPORTED_MODE, codeOf { UiPreviewRequests.validateScript(script, UiPreviewContract.MODE_SNAPSHOT, 33) })
        assertEquals(UiPreviewContract.ERROR_UNSUPPORTED_MODE, codeOf { UiPreviewRequests.validateScript(script, UiPreviewContract.MODE_EMBEDDED, 32) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateScript(null, UiPreviewContract.MODE_EMBEDDED, 33) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateScript("x".repeat(UiPreviewContract.MAX_SCRIPT_CHARS + 1), UiPreviewContract.MODE_EMBEDDED, 33) })
        assertEquals(UiPreviewContract.ERROR_SCRIPT_FAILED, codeOf { UiPreviewRequests.validateScript("console.log(1);", UiPreviewContract.MODE_EMBEDDED, 33) })
    }

    @Test
    fun theConstantScriptCarriesNoRequestContent() {
        // The runtime executes a fixed source; XML and bindings are applied from Kotlin (D47 level 1).
        assertEquals("\"ui\";", UiPreviewScript.SOURCE)
        assertFalse(UiPreviewScript.SOURCE.contains("{"))
        assertFalse(UiPreviewScript.SOURCE.contains("$"))
    }

    @Test
    fun validSessionsPassOnEveryApiLevel() {
        assertNull(codeOf { UiPreviewRequests.validateSession(params(), UiPreviewContract.VERSION, 24) })
        assertNull(codeOf { UiPreviewRequests.validateSession(params(mode = UiPreviewContract.MODE_EMBEDDED), UiPreviewContract.VERSION, 30) })
        assertNull(codeOf { UiPreviewRequests.validateSession(params(densityDpi = 480, nightMode = 2, localeTag = "zh-Hans-CN", fontScale = 1.3f), 1, 33) })
    }

    @Test
    fun contractAndModeMismatchesAreNamed() {
        assertEquals(UiPreviewContract.ERROR_CONTRACT_MISMATCH, codeOf { UiPreviewRequests.validateSession(params(), UiPreviewContract.VERSION + 1, 33) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSession(params(mode = "window"), 1, 33) })
        assertEquals(UiPreviewContract.ERROR_UNSUPPORTED_MODE, codeOf { UiPreviewRequests.validateSession(params(mode = UiPreviewContract.MODE_EMBEDDED), 1, 29) })
    }

    @Test
    fun sizesAndOverridesAreBounded() {
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSession(params(width = 0), 1, 33) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSession(params(height = 5000), 1, 33) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSession(params(densityDpi = 10), 1, 33) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSession(params(nightMode = 3), 1, 33) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSession(params(fontScale = 9f), 1, 33) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSession(params(localeTag = "x".repeat(40)), 1, 33) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSession(params(displayId = -1), 1, 33) })
        assertNull(codeOf { UiPreviewRequests.validateSize(4096, 4096) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateSize(4097, 1) })
    }

    @Test
    fun layoutRequestsCheckLengthDigestAndSnapshotOutput() {
        val xml = "<vertical><text text=\"hi\"/></vertical>"
        assertNull(codeOf { UiPreviewRequests.validateLayout(xml, "{}", null, UiPreviewContract.MODE_EMBEDDED, hasOutput = false) })
        assertNull(codeOf { UiPreviewRequests.validateLayout(xml, null, UiPreviewRequests.sha256Hex(xml).uppercase(), UiPreviewContract.MODE_SNAPSHOT, hasOutput = true) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayout(null, null, null, UiPreviewContract.MODE_EMBEDDED, false) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayout("x".repeat(UiPreviewContract.MAX_XML_CHARS + 1), null, null, UiPreviewContract.MODE_EMBEDDED, false) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayout(xml, "x".repeat(UiPreviewContract.MAX_BINDINGS_CHARS + 1), null, UiPreviewContract.MODE_EMBEDDED, false) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayout(xml, null, "00", UiPreviewContract.MODE_EMBEDDED, false) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayout(xml, null, null, UiPreviewContract.MODE_SNAPSHOT, hasOutput = false) })
    }

    @Test
    fun pointerModesFollowDecisionD50() {
        assertNull(codeOf { UiPreviewRequests.validatePointerMode(UiPreviewContract.POINTER_MODE_EDIT, 30) })
        assertNull(codeOf { UiPreviewRequests.validatePointerMode(UiPreviewContract.POINTER_MODE_INTERACTIVE, 33) })
        assertEquals(UiPreviewContract.ERROR_UNSUPPORTED_MODE, codeOf { UiPreviewRequests.validatePointerMode(UiPreviewContract.POINTER_MODE_INTERACTIVE, 32) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validatePointerMode(2, 35) })
    }

    @Test
    fun labelsAreSanitizedAndLimited() {
        assertEquals("fallback", UiPreviewRequests.sanitizeLabel(null, "fallback"))
        assertEquals("fallback", UiPreviewRequests.sanitizeLabel(" \n\t ", "fallback"))
        assertEquals("my project", UiPreviewRequests.sanitizeLabel("my\u0000project\n", "fallback"))
        assertEquals(UiPreviewContract.MAX_LABEL_CHARS, UiPreviewRequests.sanitizeLabel("a".repeat(500), "fallback").length)
    }

    @Test
    fun sha256IsLowercaseHex() {
        val digest = UiPreviewRequests.sha256Hex("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
        try {
            UiPreviewRequests.validateLayout("abc", null, digest, UiPreviewContract.MODE_EMBEDDED, false)
        } catch (e: UiPreviewRequestException) {
            fail("a matching digest must be accepted: ${e.message}")
        }
        assertTrue(digest.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun v3ResourcesNeedTheirZipAndASafeLayoutPath() {
        val digest = UiPreviewRequests.sha256Hex("zip")
        assertNull(UiPreviewRequests.validateLayoutResources(false, null, null))
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayoutResources(false, digest, null) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayoutResources(false, null, "res/layout/main.xml") })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayoutResources(true, null, null) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayoutResources(true, digest.uppercase(), null) })
        assertNull(UiPreviewRequests.validateLayoutResources(true, digest, null))
        assertEquals("res/layout/main.xml", UiPreviewRequests.validateLayoutResources(true, digest, "res/layout/main.xml"))
        for (path in listOf("", "/res/layout/main.xml", "../main.xml", "res\\main.xml", "res/../x.xml", "C:x.xml", "res//x.xml")) {
            assertEquals(path, UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLayoutResources(true, digest, path) })
        }
    }

    @Test
    fun v3ProjectRunsNeedTheirZipDigestAndScriptPath() {
        val digest = UiPreviewRequests.sha256Hex("project")
        assertNull(UiPreviewRequests.validateProject(false, null, null))
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateProject(false, null, "main.js") })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateProject(true, digest, null) })
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateProject(true, "abc", "main.js") })
        assertEquals("main.js", UiPreviewRequests.validateProject(true, digest, "main.js"))
        assertEquals(UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateProject(true, digest, "../main.js") })
        // Zip entries use their own code.
        assertEquals(UiPreviewContract.ERROR_ARCHIVE_INVALID, codeOf { UiPreviewRequests.archivePath("..", "zip entry", UiPreviewContract.ERROR_ARCHIVE_INVALID) })
    }

    @Test
    fun v4BusyWaitLimitsDefaultTurnOffOrStayInRange() {
        assertEquals(UiPreviewContract.DEFAULT_BUSY_WAIT_LIMIT_MS, UiPreviewRequests.validateBusyWaitLimit(null))
        assertEquals(0L, UiPreviewRequests.validateBusyWaitLimit(0L))
        assertEquals(0L, UiPreviewRequests.validateBusyWaitLimit(0))
        // A client may put an int: it means the same milliseconds.
        assertEquals(3_000L, UiPreviewRequests.validateBusyWaitLimit(3_000))
        assertEquals(UiPreviewContract.MIN_BUSY_WAIT_LIMIT_MS, UiPreviewRequests.validateBusyWaitLimit(UiPreviewContract.MIN_BUSY_WAIT_LIMIT_MS))
        assertEquals(UiPreviewContract.MAX_BUSY_WAIT_LIMIT_MS, UiPreviewRequests.validateBusyWaitLimit(UiPreviewContract.MAX_BUSY_WAIT_LIMIT_MS))
        for (bad in listOf<Any>(999L, 60_001L, -1L, 5_000.0, "5000", true)) {
            assertEquals("$bad", UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateBusyWaitLimit(bad) })
        }
    }

    @Test
    fun v4LifecycleEventsAreTheThreeNamedOnes() {
        for (event in UiPreviewContract.LIFECYCLE_EVENTS) assertEquals(event, UiPreviewRequests.validateLifecycleEvent(event))
        for (bad in listOf(null, "", "create", "BACK_PRESSED", "back")) {
            assertEquals("$bad", UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateLifecycleEvent(bad) })
        }
    }

    @Test
    fun v5DialectsDefaultToJsAndTsxRunsAreNamedAfterTheScript() {
        assertEquals(UiPreviewContract.SCRIPT_DIALECT_JS, UiPreviewRequests.validateScriptDialect(null))
        for (dialect in UiPreviewContract.SCRIPT_DIALECTS) assertEquals(dialect, UiPreviewRequests.validateScriptDialect(dialect))
        for (bad in listOf<Any>("", "ts", "TSX", "jsx", 1, true)) {
            assertEquals("$bad", UiPreviewContract.ERROR_INVALID_REQUEST, codeOf { UiPreviewRequests.validateScriptDialect(bad) })
        }
        // The compiled file is the script's name, which the host's task list shows without the extension.
        assertEquals("todo.tsx", UiPreviewRequests.tsxSourceName("todo"))
        assertEquals("main.tsx", UiPreviewRequests.tsxSourceName("main.tsx"))
        assertEquals("a_b_c.tsx", UiPreviewRequests.tsxSourceName("a/b\\c"))
        assertEquals("v1.2.tsx", UiPreviewRequests.tsxSourceName("v1.2"))
        for (bad in listOf("", " ", ".", "..", ".hidden")) {
            assertEquals("'$bad'", UiPreviewRequests.FALLBACK_TSX_SOURCE_NAME, UiPreviewRequests.tsxSourceName(bad))
        }
    }
}
