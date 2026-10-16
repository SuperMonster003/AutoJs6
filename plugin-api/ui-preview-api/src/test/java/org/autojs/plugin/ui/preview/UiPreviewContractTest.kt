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
        assertEquals(5, UiPreviewContract.VERSION)
        assertEquals(4, UiPreviewContract.VERSION_V4)
        assertEquals(3, UiPreviewContract.VERSION_V3)
        assertEquals(2, UiPreviewContract.VERSION_V2)
        assertEquals(1, UiPreviewContract.BASE_VERSION)
        assertEquals(UiPreviewContract.HASH, sha256(UiPreviewContract.HASH_INPUT))
        assertEquals(UiPreviewContract.HASH_V4, sha256(UiPreviewContract.HASH_INPUT_V4))
        assertEquals(UiPreviewContract.HASH_V3, sha256(UiPreviewContract.HASH_INPUT_V3))
        assertEquals(UiPreviewContract.HASH_V2, sha256(UiPreviewContract.HASH_INPUT_V2))
        assertEquals(UiPreviewContract.HASH_V1, sha256(UiPreviewContract.HASH_INPUT_V1))
        // The v1 and v2 identities are frozen: 1.6.0 compares the first through the getters, 1.7.0 the second through the info.
        assertEquals("680f41b5459648ff07bd1d3d9684c5279d9398b78f61c0307862f50f8dcac0b6", UiPreviewContract.HASH_V1)
        assertEquals("81f7fe8d2338eed197dcc21e3cff7007a681ea89d080b42d1ae132c94688f1bb", UiPreviewContract.HASH_V2)
        // The v3 identity is frozen as well: 3-Block Compose 1.8.0 finds it among the supported versions.
        assertEquals("196e52cd66ff871dca8351a0e9208679ed2bf64af5f733d05312239b1f03a44b", UiPreviewContract.HASH_V3)
        // So is v4: the host of stage P11.5 lists it, and a v4 client compares it.
        assertEquals("b7591e9ee2fe24f475db79a0186c87fd6c36d8de632c3074cc491155d2e881b2", UiPreviewContract.HASH_V4)
        // v2 only appends: every v1 transaction keeps its code, so a v1 client works against a v2 host.
        val v1Service = servicesOf(UiPreviewContract.HASH_INPUT_V1)
        val v2Service = servicesOf(UiPreviewContract.HASH_INPUT_V2)
        assertEquals(v1Service, v2Service.take(v1Service.size))
        val v1Callbacks = callbacksOf(UiPreviewContract.HASH_INPUT_V1)
        assertEquals(v1Callbacks, callbacksOf(UiPreviewContract.HASH_INPUT_V2).take(v1Callbacks.size))
        // v3 appends no transaction at all, only request keys and a diagnostic.
        assertEquals(v2Service, servicesOf(UiPreviewContract.HASH_INPUT_V3))
        assertEquals(callbacksOf(UiPreviewContract.HASH_INPUT_V2), callbacksOf(UiPreviewContract.HASH_INPUT_V3))
        // v4 appends one transaction and one callback after them, and keeps every v3 descriptor in order.
        val v4Service = servicesOf(UiPreviewContract.HASH_INPUT_V4)
        assertEquals(v2Service, v4Service.take(v2Service.size))
        assertEquals(listOf("sendLifecycle(String,Bundle)->Bundle"), v4Service.drop(v2Service.size))
        val v3Callbacks = callbacksOf(UiPreviewContract.HASH_INPUT_V3)
        assertEquals(v3Callbacks + "onLifecycleHandled", callbacksOf(UiPreviewContract.HASH_INPUT_V4))
        val v3Descriptors = descriptorsOf(UiPreviewContract.HASH_INPUT_V3)
        assertEquals(v3Descriptors, descriptorsOf(UiPreviewContract.HASH_INPUT_V4).take(v3Descriptors.size))
        // v5 appends no transaction or callback, only descriptors after every v4 one.
        assertEquals(v4Service, servicesOf(UiPreviewContract.HASH_INPUT))
        assertEquals(callbacksOf(UiPreviewContract.HASH_INPUT_V4), callbacksOf(UiPreviewContract.HASH_INPUT))
        val v4Descriptors = descriptorsOf(UiPreviewContract.HASH_INPUT_V4)
        assertEquals(v4Descriptors, descriptorsOf(UiPreviewContract.HASH_INPUT).take(v4Descriptors.size))
        assertTrue(descriptorsOf(UiPreviewContract.HASH_INPUT).size > v4Descriptors.size)
    }

    private fun descriptorsOf(input: String): List<String> = input.split('|').dropWhile { !it.startsWith("bundle:") }

    private fun servicesOf(input: String): List<String> = input.split('|').drop(1).takeWhile { !it.startsWith("callback:") }

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
            "sendLifecycle(String,Bundle)->Bundle",
        )
        val described = UiPreviewContract.HASH_INPUT.split('|').drop(1).takeWhile { !it.startsWith("callback:") }
        assertEquals(expected, described)
        assertEquals(expected.map { it.substringBefore('(') }, declaredMethods("IUiPreviewService"))
    }

    @Test
    fun callbackTransactionsAreFrozenInOrder() {
        val expected = listOf(
            "onBounds", "onLayoutFailed", "onSessionLost", "onTap", "onConsole", "onScriptError", "onScriptExit", "onLifecycleHandled",
        )
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
            UiPreviewContract.ERROR_ARCHIVE_INVALID, UiPreviewContract.ERROR_ATTRIBUTE_IGNORED,
            UiPreviewContract.ERROR_BUSY_WAIT, UiPreviewContract.ERROR_NOT_RUNNING,
            UiPreviewContract.ERROR_COMPILE_FAILED,
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
            // v3: a zip goes through a file descriptor, so only its own limits bound it.
            assertTrue(MAX_ARCHIVE_BYTES in 1L..MAX_ARCHIVE_UNPACKED_BYTES)
            assertTrue(MAX_ARCHIVE_ENTRIES > 0 && MAX_ARCHIVE_PATH_CHARS in 64..4096)
            assertEquals(3, ATTRIBUTE_OUTCOMES.toSet().size)
            // v4: the busy-wait limit of a run stays above a layout's wait and within a minute.
            assertTrue(MIN_BUSY_WAIT_LIMIT_MS in 1L..DEFAULT_BUSY_WAIT_LIMIT_MS)
            assertTrue(DEFAULT_BUSY_WAIT_LIMIT_MS in LAYOUT_TIMEOUT_MS..MAX_BUSY_WAIT_LIMIT_MS)
            assertEquals(60_000L, MAX_BUSY_WAIT_LIMIT_MS)
            assertEquals(listOf("pause", "resume", "back_pressed"), LIFECYCLE_EVENTS.toList())
            assertTrue(STATUS_BAR_STRIP_DP in 1 until NAVIGATION_BAR_STRIP_DP)
            // v5: the dialects of a run.
            assertEquals(listOf("js", "tsx"), SCRIPT_DIALECTS.toList())
        }
    }

    @Test
    fun jsonMemberNamesAreDistinct() {
        val bounds = with(UiPreviewContract.Bounds) { listOf(ELEMENT_INDEX, LEFT, TOP, RIGHT, BOTTOM, CLASS_NAME, ID, INSTANCE, VISIBILITY) }
        val diagnostic = with(UiPreviewContract.Diagnostic) { listOf(CODE, MESSAGE, ELEMENT_INDEX, ATTR_NAME, OUTCOME) }
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
                KEY_SUPPORTED_CONTRACT_VERSIONS, KEY_CONTRACT_HASHES, KEY_RESOURCES_SUPPORTED, KEY_MAX_ARCHIVE_BYTES,
                KEY_MAX_ARCHIVE_UNPACKED_BYTES, KEY_MAX_ARCHIVE_ENTRIES, KEY_RESOURCES_FD, KEY_RESOURCES_SHA256, KEY_LAYOUT_PATH,
                KEY_ANDROID_LAYOUT, KEY_PROJECT_FD, KEY_PROJECT_SHA256, KEY_SCRIPT_PATH,
                KEY_DIALOGS_IN_CANVAS, KEY_LIFECYCLE_EVENTS, KEY_BUSY_WAIT_LIMIT_SUPPORTED, KEY_WINDOW_FUNCTIONS_VISIBLE,
                KEY_BUSY_WAIT_LIMIT_MS, KEY_LIFECYCLE_EVENT, KEY_CONSUMED,
                KEY_TSX_RUN_SUPPORTED, KEY_SCRIPT_DIALECT,
            )
        }
        assertEquals(keys.size, keys.toSet().size)
    }

    private fun declaredMethods(interfaceName: String): List<String> {
        val aidl = File("src/main/aidl/org/autojs/plugin/ui/preview/$interfaceName.aidl").readText()
        return Regex("""^\s*(?:\w+)\s+(\w+)\(""", RegexOption.MULTILINE).findAll(aidl).map { it.groupValues[1] }.toList()
    }
}
