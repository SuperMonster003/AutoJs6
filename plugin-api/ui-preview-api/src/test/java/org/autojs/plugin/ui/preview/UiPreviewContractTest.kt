package org.autojs.plugin.ui.preview

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPreviewContractTest {

    @Test
    fun contractHashMatchesItsInput() {
        assertEquals(2, UiPreviewContract.VERSION)
        assertEquals(1, UiPreviewContract.BASE_VERSION)
        assertEquals(UiPreviewContract.HASH, sha256(UiPreviewContract.HASH_INPUT))
        assertEquals(UiPreviewContract.HASH_V1, sha256(UiPreviewContract.HASH_INPUT_V1))
        assertEquals("680f41b5459648ff07bd1d3d9684c5279d9398b78f61c0307862f50f8dcac0b6", UiPreviewContract.HASH_V1)
        // v2 only appends: every v1 transaction keeps its code, so a v1 client works against a v2 host.
        val v1Service = UiPreviewContract.HASH_INPUT_V1.split('|').drop(1).takeWhile { !it.startsWith("callback:") }
        val v2Service = UiPreviewContract.HASH_INPUT.split('|').drop(1).takeWhile { !it.startsWith("callback:") }
        assertEquals(v1Service, v2Service.take(v1Service.size))
        val v1Callbacks = callbacksOf(UiPreviewContract.HASH_INPUT_V1)
        assertEquals(v1Callbacks, callbacksOf(UiPreviewContract.HASH_INPUT).take(v1Callbacks.size))
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun callbacksOf(input: String): List<String> = input.split('|')
        .dropWhile { !it.startsWith("callback:") }
        .takeWhile { !it.startsWith("bundle:") }
        .map { it.removePrefix("callback:").substringBefore('(') }

    @Test
    fun serviceTransactionsAreFrozenInOrder() {
        // Binder transaction codes follow the AIDL method order; reordering breaks every peer.
        val expected = listOf(
            "getContractVersion()->int",
            "getContractHash()->String",
            "getPreviewInfo()->Bundle",
            "createSession(Bundle,IUiPreviewCallback)->Bundle",
            "setLayout(String,Bundle,ParcelFileDescriptor)->Bundle",
            "resize(String,int,int)->Bundle",
            "release(String)->void",
            "reattach(String)->Bundle",
            "setPointerMode(String,int)->Bundle",
            "setHighlight(String,int)->Bundle",
            "runScript(String,Bundle)->Bundle",
            "stopScript(String)->Bundle",
        )
        val described = UiPreviewContract.HASH_INPUT.split('|').drop(1).takeWhile { !it.startsWith("callback:") }
        assertEquals(expected, described)
        assertEquals(expected.map { it.substringBefore('(') }, declaredMethods("IUiPreviewService"))
    }

    @Test
    fun callbackTransactionsAreFrozenInOrder() {
        val expected = listOf("onBounds", "onLayoutFailed", "onSessionLost", "onTap", "onConsole", "onScriptError", "onScriptExit")
        assertEquals(expected, callbacksOf(UiPreviewContract.HASH_INPUT))
        assertEquals(expected, declaredMethods("IUiPreviewCallback"))
        val aidl = File("src/main/aidl/org/autojs/plugin/ui/preview/IUiPreviewCallback.aidl").readText()
        assertTrue("callbacks must be oneway so the host never waits for the client", aidl.contains("oneway interface IUiPreviewCallback"))
    }

    @Test
    fun actionPermissionAndErrorPrefixAreStable() {
        assertEquals("org.autojs.plugin.ui.PREVIEW", UiPreviewContract.ACTION)
        assertEquals("org.autojs.permission.PLUGIN", UiPreviewContract.PLUGIN_PERMISSION)
        val errors = listOf(
            UiPreviewContract.ERROR_INVALID_REQUEST, UiPreviewContract.ERROR_CONTRACT_MISMATCH,
            UiPreviewContract.ERROR_UNSUPPORTED_MODE, UiPreviewContract.ERROR_SESSION_LIMIT,
            UiPreviewContract.ERROR_SESSION_NOT_FOUND, UiPreviewContract.ERROR_XML_INVALID,
            UiPreviewContract.ERROR_INFLATE_FAILED, UiPreviewContract.ERROR_BINDING_FAILED,
            UiPreviewContract.ERROR_TIMEOUT, UiPreviewContract.ERROR_OUTPUT_TRANSPORT,
            UiPreviewContract.ERROR_UNAVAILABLE, UiPreviewContract.ERROR_SCRIPT_FAILED,
        )
        assertEquals(errors.size, errors.toSet().size)
        errors.forEach { assertTrue(it, it.startsWith("ERR_AUTOJS6_UI_PREVIEW_")) }
    }

    @Test
    fun apiLevelsFollowDecisionD50() {
        assertEquals(30, UiPreviewContract.MIN_API_EMBEDDED)
        assertEquals(33, UiPreviewContract.MIN_API_INTERACTIVE)
        assertEquals(35, UiPreviewContract.MIN_API_IME)
        assertEquals(0, UiPreviewContract.POINTER_MODE_EDIT)
        assertEquals(1, UiPreviewContract.POINTER_MODE_INTERACTIVE)
        assertEquals(listOf("embedded", "snapshot"), UiPreviewContract.MODES.toList())
    }

    @Test
    fun limitsAreConsistent() {
        with(UiPreviewContract) {
            // UTF-16 request text of one setLayout stays within three quarters of the 1 MiB Binder buffer.
            assertTrue((MAX_XML_CHARS + MAX_BINDINGS_CHARS) * 2L <= 768L * 1024L)
            assertEquals(MAX_WIDTH_PX.toLong() * MAX_HEIGHT_PX, MAX_SNAPSHOT_PIXELS)
            assertTrue(MIN_SIZE_PX in 1..MAX_WIDTH_PX && MIN_SIZE_PX <= MAX_HEIGHT_PX)
            assertTrue(MAX_SESSIONS_PER_CLIENT in 1..4)
            assertTrue(LAYOUT_TIMEOUT_MS in 1_000L..SESSION_CREATE_TIMEOUT_MS)
            assertTrue(MIN_DENSITY_DPI < MAX_DENSITY_DPI && MIN_FONT_SCALE < MAX_FONT_SCALE)
            assertTrue(MAX_BOUNDS_ENTRIES > 0 && MAX_LABEL_CHARS > 0 && MAX_ERROR_MESSAGE_CHARS > 0)
            assertEquals(3, TERMINAL_OUTCOMES.toSet().size)
            assertEquals(4, SNAPSHOT_BYTES_PER_PIXEL)
            // v2: one runScript text stays within the 1 MiB Binder buffer, and a start waits no longer than a session creation.
            assertTrue(MAX_SCRIPT_CHARS * 2L <= 1024L * 1024L)
            assertTrue(SCRIPT_START_TIMEOUT_MS in LAYOUT_TIMEOUT_MS..SESSION_CREATE_TIMEOUT_MS)
            assertEquals(5, EXIT_REASONS.toSet().size)
        }
    }

    @Test
    fun jsonMemberNamesAreDistinct() {
        val bounds = with(UiPreviewContract.Bounds) { listOf(ELEMENT_INDEX, LEFT, TOP, RIGHT, BOTTOM, CLASS_NAME, ID, INSTANCE, VISIBILITY) }
        val diagnostic = with(UiPreviewContract.Diagnostic) { listOf(CODE, MESSAGE, ELEMENT_INDEX, ATTR_NAME) }
        for (names in listOf(bounds, diagnostic)) assertEquals(names.size, names.toSet().size)
        assertFalse("bounds members are single letters to keep large arrays small", bounds.any { it.length != 1 })
    }

    @Test
    fun bundleKeysAreDistinct() {
        val keys = with(UiPreviewContract) {
            listOf(
                KEY_CONTRACT_VERSION, KEY_MODE, KEY_HOST_TOKEN, KEY_INPUT_TRANSFER_TOKEN, KEY_DISPLAY_ID, KEY_WIDTH_PX, KEY_HEIGHT_PX,
                KEY_DENSITY_DPI, KEY_NIGHT_MODE, KEY_LOCALE_TAG, KEY_FONT_SCALE, KEY_CLIENT_VERSION_CODE, KEY_LABEL,
                KEY_XML, KEY_BINDINGS_JSON, KEY_XML_SHA256,
                KEY_SESSION_ID, KEY_SURFACE_PACKAGE, KEY_OUTCOME, KEY_ERROR_CODE, KEY_ERROR_MESSAGE, KEY_ELEMENT_INDEX, KEY_ATTR_NAME,
                KEY_LINE, KEY_COLUMN, KEY_ELAPSED_MS, KEY_ELEMENT_COUNT, KEY_DIAGNOSTICS_JSON, KEY_REASON, KEY_POINTER_MODE,
                KEY_BOUNDS_JSON, KEY_BOUNDS_COUNT, KEY_BOUNDS_TRUNCATED,
                KEY_SNAPSHOT_BYTE_LENGTH, KEY_SNAPSHOT_SHA256, KEY_SNAPSHOT_FORMAT, KEY_SNAPSHOT_WIDTH_PX, KEY_SNAPSHOT_HEIGHT_PX,
                KEY_X, KEY_Y, KEY_CLASS_NAME,
                KEY_CONTRACT_HASH, KEY_HOST_VERSION_CODE, KEY_HOST_VERSION_NAME, KEY_STATUS, KEY_MODES,
                KEY_INTERACTIVE_POINTER_SUPPORTED, KEY_IME_SUPPORTED, KEY_MAX_SESSIONS_PER_CLIENT, KEY_MAX_WIDTH_PX, KEY_MAX_HEIGHT_PX,
                KEY_MAX_XML_CHARS, KEY_MAX_BINDINGS_CHARS, KEY_MAX_BOUNDS_ENTRIES, KEY_MAX_SNAPSHOT_PIXELS, KEY_LAYOUT_TIMEOUT_MS,
                KEY_SCRIPT_RUN_SUPPORTED, KEY_SCRIPT_TEXT, KEY_SCRIPT_NAME, KEY_RUN_ID, KEY_LEVEL, KEY_TEXT,
            )
        }
        assertEquals(keys.size, keys.toSet().size)
    }

    private fun declaredMethods(interfaceName: String): List<String> {
        val aidl = File("src/main/aidl/org/autojs/plugin/ui/preview/$interfaceName.aidl").readText()
        return Regex("""^\s*(?:\w+)\s+(\w+)\(""", RegexOption.MULTILINE).findAll(aidl).map { it.groupValues[1] }.toList()
    }
}
