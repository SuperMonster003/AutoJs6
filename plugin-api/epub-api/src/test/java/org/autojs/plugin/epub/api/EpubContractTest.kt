package org.autojs.plugin.epub.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class EpubContractTest {

    @Test
    fun identityAndDiscoveryLiteralsAreStable() {
        assertEquals(PluginActions.INFO, EpubActions.INFO)
        assertEquals("org.autojs.plugin.EPUB", EpubActions.SERVICE_ACTION)
        assertEquals("epub", EpubActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", EpubActions.PLUGIN_PERMISSION)
        assertEquals("org.autojs.plugin.EPUB_READER_OPEN", EpubActions.READER_ACTIVITY_ACTION)
        assertEquals("org.autojs.plugin.epub.extra.SESSION_TOKEN", EpubActions.EXTRA_SESSION_TOKEN)

        assertEquals("readium-epub-reader", EpubIds.PLUGIN_ID)
        assertEquals("epub", EpubIds.ENGINE)
        assertEquals("default", EpubIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.readium.epub.reader", EpubIds.DEFAULT_PACKAGE_NAME)
        assertEquals(5282L, EpubIds.REQUIRED_HOST_VERSION_CODE)

        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, EpubCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("epubContractVersion", EpubCapabilityKeys.CONTRACT_VERSION)
        assertEquals("epubMaxContractVersion", EpubCapabilityKeys.MAX_CONTRACT_VERSION)
        assertEquals("epubFeatures", EpubCapabilityKeys.FEATURES)
        assertEquals("epubReadiumVersion", EpubCapabilityKeys.READIUM_VERSION)
    }

    @Test
    fun contractVersionRangeIncludesItsBoundsAndRejectsNeighbours() {
        assertEquals(2, EpubContract.CONTRACT_VERSION)
        assertEquals(1, EpubContract.MIN_CONTRACT_VERSION)
        assertEquals(2, EpubContract.MAX_CONTRACT_VERSION)
        assertEquals(2, EpubContract.CONTRACT_VERSION_ANNOTATIONS)
        assertTrue(EpubContract.MIN_CONTRACT_VERSION <= EpubContract.MAX_CONTRACT_VERSION)
        assertTrue(EpubContract.CONTRACT_VERSION_ANNOTATIONS in EpubContract.MIN_CONTRACT_VERSION..EpubContract.MAX_CONTRACT_VERSION)
        assertTrue(EpubContract.supportsContractVersion(EpubContract.MIN_CONTRACT_VERSION))
        assertTrue(EpubContract.supportsContractVersion(EpubContract.CONTRACT_VERSION))
        assertTrue(EpubContract.supportsContractVersion(EpubContract.MAX_CONTRACT_VERSION))
        assertFalse(EpubContract.supportsContractVersion(EpubContract.MIN_CONTRACT_VERSION - 1))
        assertFalse(EpubContract.supportsContractVersion(EpubContract.MAX_CONTRACT_VERSION + 1))
    }

    @Test
    fun bundleKeysAreUniqueAndMatchAppendixB1() {
        val keys = constants("KEY_")
        assertEquals(keys.size, keys.values.toSet().size)
        assertEquals(
            listOf(
                "contractVersion", "requestId", "displayName", "href", "index", "offset", "limit", "maxChars", "format",
                "query", "locator", "progression", "preferences", "errorCode", "errorMessage", "event", "reason", "change",
                "sessionToken", "visible", "title", "text", "hasMore", "positions", "finish",
                "metadata", "toc", "readingOrder", "results", "bookmarks",
                // Contract version 2.
                "annotations",
            ).sorted(),
            keys.values.sorted(),
        )
        assertEquals("contractVersion", EpubContract.KEY_CONTRACT_VERSION)
        assertEquals("sessionToken", EpubContract.KEY_SESSION_TOKEN)
        assertEquals("errorCode", EpubContract.KEY_ERROR_CODE)
        assertEquals("errorMessage", EpubContract.KEY_ERROR_MESSAGE)
    }

    @Test
    fun jsonFieldNamesAreUnique() {
        val fields = constants("FIELD_")
        assertEquals(fields.size, fields.values.toSet().size)
        assertEquals("totalProgression", EpubContract.FIELD_TOTAL_PROGRESSION)
        assertEquals("readingProgression", EpubContract.FIELD_READING_PROGRESSION)
        assertEquals("cssSelector", EpubContract.FIELD_CSS_SELECTOR)
        assertEquals("createdAt", EpubContract.FIELD_CREATED_AT)
        // Contract version 2: the annotation document.
        assertEquals("id", EpubContract.FIELD_ID)
        assertEquals("style", EpubContract.FIELD_STYLE)
        assertEquals("color", EpubContract.FIELD_COLOR)
        assertEquals("note", EpubContract.FIELD_NOTE)
        assertEquals("quote", EpubContract.FIELD_QUOTE)
        assertEquals("updatedAt", EpubContract.FIELD_UPDATED_AT)
    }

    @Test
    fun closedValueSetsAreFrozen() {
        assertEquals(setOf("open", "progress", "bookmark", "close", "error", "highlight"), EpubContract.EVENTS)
        assertEquals(setOf("user", "host", "replaced", "timeout", "error"), EpubContract.REASONS)
        assertEquals(setOf("added", "removed", "updated"), EpubContract.CHANGES)
        assertEquals(setOf("highlight", "underline"), EpubContract.STYLES)
        assertEquals(setOf(1, 2, 3, 4), EpubContract.DIRECTIONS)
        assertEquals(1, EpubContract.DIRECTION_NEXT_PAGE)
        assertEquals(2, EpubContract.DIRECTION_PREVIOUS_PAGE)
        assertEquals(3, EpubContract.DIRECTION_NEXT_CHAPTER)
        assertEquals(4, EpubContract.DIRECTION_PREVIOUS_CHAPTER)
        assertTrue(EpubContract.isKnownDirection(EpubContract.DIRECTION_PREVIOUS_CHAPTER))
        assertFalse(EpubContract.isKnownDirection(0))
        assertFalse(EpubContract.isKnownDirection(5))
        assertEquals(setOf("text", "markdown"), EpubContract.FORMATS)
        assertEquals(setOf("reflowable", "fixed"), EpubContract.LAYOUTS)
        assertEquals(setOf("ltr", "rtl", "auto"), EpubContract.READING_PROGRESSIONS)
        assertEquals(setOf("search", "tts", "reader-session", "cover", "resource-export", "markdown", "annotations"), EpubContract.FEATURES)
        assertEquals(
            setOf(
                "fontSize", "fontFamily", "lineHeight", "pageMargins", "theme", "scroll",
                "columnCount", "verticalText", "textAlign", "hyphens", "publisherStyles",
            ),
            EpubContract.PREFERENCES,
        )
        assertEquals(setOf("light", "sepia", "dark"), EpubContract.THEMES)
        assertEquals(constants("PREFERENCE_").values.toSet(), EpubContract.PREFERENCES)
        assertEquals(constants("EVENT_").values.toSet(), EpubContract.EVENTS)
        assertEquals(constants("REASON_").values.toSet(), EpubContract.REASONS)
        assertEquals(constants("FEATURE_").values.toSet(), EpubContract.FEATURES)
        assertEquals(constants("CHANGE_").values.toSet(), EpubContract.CHANGES)
        assertEquals(constants("STYLE_").values.toSet(), EpubContract.STYLES)
    }

    @Test
    fun ceilingsMatchAppendixB5AndAreLayered() {
        assertEquals(8, EpubContract.MAX_OPEN_BOOKS)
        assertEquals(300_000L, EpubContract.BOOK_IDLE_TIMEOUT_MS)
        assertEquals(5000, EpubContract.MAX_TOC_ENTRIES)
        assertEquals(1_048_576, EpubContract.MAX_TEXT_CHARS_PER_CALL)
        assertEquals(67_108_864L, EpubContract.MAX_RESOURCE_BYTES)
        assertEquals(500, EpubContract.MAX_SEARCH_RESULTS)
        assertEquals(256, EpubContract.MAX_QUERY_LENGTH)
        assertEquals(16_384, EpubContract.MAX_LOCATOR_BYTES)
        assertEquals(32_768, EpubContract.MAX_EVENT_BYTES)
        assertEquals(60_000L, EpubContract.READER_CLAIM_TIMEOUT_MS)
        assertEquals(500L, EpubContract.PROGRESS_THROTTLE_MS)
        // Contract version 2.
        assertEquals(2000, EpubContract.MAX_ANNOTATIONS)
        assertEquals(100, EpubContract.DEFAULT_ANNOTATIONS_LIMIT)
        assertEquals(200, EpubContract.MAX_ANNOTATIONS_PAGE)
        assertEquals(524_288, EpubContract.MAX_ANNOTATIONS_BYTES)

        val ceilings = listOf(
            EpubContract.MAX_OPEN_BOOKS.toLong(),
            EpubContract.BOOK_IDLE_TIMEOUT_MS,
            EpubContract.MAX_OPTIONS_BYTES.toLong(),
            EpubContract.MAX_HREF_LENGTH.toLong(),
            EpubContract.MAX_METADATA_BYTES.toLong(),
            EpubContract.MAX_TOC_ENTRIES.toLong(),
            EpubContract.MAX_TOC_BYTES.toLong(),
            EpubContract.MAX_READING_ORDER_ENTRIES.toLong(),
            EpubContract.MAX_TEXT_CHARS_PER_CALL.toLong(),
            EpubContract.MAX_RESOURCE_BYTES,
            EpubContract.MIN_QUERY_LENGTH.toLong(),
            EpubContract.MAX_QUERY_LENGTH.toLong(),
            EpubContract.DEFAULT_SEARCH_LIMIT.toLong(),
            EpubContract.MAX_SEARCH_RESULTS.toLong(),
            EpubContract.MAX_RESULTS_BYTES.toLong(),
            EpubContract.MAX_LOCATOR_BYTES.toLong(),
            EpubContract.MAX_PREFERENCES_BYTES.toLong(),
            EpubContract.MAX_BOOKMARKS.toLong(),
            EpubContract.MAX_ANNOTATIONS.toLong(),
            EpubContract.DEFAULT_ANNOTATIONS_LIMIT.toLong(),
            EpubContract.MAX_ANNOTATIONS_PAGE.toLong(),
            EpubContract.MAX_ANNOTATIONS_BYTES.toLong(),
            EpubContract.MAX_EVENT_BYTES.toLong(),
            EpubContract.MAX_ERROR_MESSAGE_BYTES.toLong(),
            EpubContract.READER_CLAIM_TIMEOUT_MS,
            EpubContract.PROGRESS_THROTTLE_MS,
            EpubContract.OPEN_TIMEOUT_MS,
            EpubContract.CALL_TIMEOUT_MS,
        )
        ceilings.forEach { value -> assertTrue("ceiling $value must be positive", value > 0L) }

        assertTrue(EpubContract.MIN_QUERY_LENGTH <= EpubContract.MAX_QUERY_LENGTH)
        assertTrue(EpubContract.DEFAULT_SEARCH_LIMIT <= EpubContract.MAX_SEARCH_RESULTS)
        assertTrue(EpubContract.DEFAULT_ANNOTATIONS_LIMIT <= EpubContract.MAX_ANNOTATIONS_PAGE)
        assertTrue(EpubContract.MAX_ANNOTATIONS_PAGE <= EpubContract.MAX_ANNOTATIONS)
        // One annotations page travels as one Bundle answer, like a search page.
        assertTrue(EpubContract.MAX_ANNOTATIONS_BYTES <= EpubContract.MAX_RESULTS_BYTES)
        assertTrue(EpubContract.MAX_LOCATOR_BYTES < EpubContract.MAX_ANNOTATIONS_BYTES)
        assertTrue(EpubContract.MAX_LOCATOR_BYTES < EpubContract.MAX_EVENT_BYTES)
        assertTrue(EpubContract.MAX_ERROR_MESSAGE_BYTES < EpubContract.MAX_EVENT_BYTES)
        // A reader launch carries a locator and a preferences document inside one options bundle.
        assertTrue(EpubContract.MAX_OPTIONS_BYTES >= EpubContract.MAX_LOCATOR_BYTES + EpubContract.MAX_PREFERENCES_BYTES)
        assertTrue(EpubContract.MAX_METADATA_BYTES < EpubContract.MAX_TOC_BYTES)
        assertTrue(EpubContract.MAX_TOC_BYTES < EpubContract.MAX_RESULTS_BYTES)
        assertTrue(EpubContract.MAX_RESULTS_BYTES < EpubContract.MAX_RESOURCE_BYTES)
        assertTrue(EpubContract.MAX_TEXT_CHARS_PER_CALL <= EpubContract.MAX_RESOURCE_BYTES)
        assertTrue(EpubContract.PROGRESS_THROTTLE_MS < EpubContract.READER_CLAIM_TIMEOUT_MS)
        assertTrue(EpubContract.READER_CLAIM_TIMEOUT_MS < EpubContract.BOOK_IDLE_TIMEOUT_MS)
        assertTrue(EpubContract.OPEN_TIMEOUT_MS <= EpubContract.CALL_TIMEOUT_MS)
        assertTrue(EpubContract.CALL_TIMEOUT_MS < EpubContract.BOOK_IDLE_TIMEOUT_MS)
    }

    @Test
    fun errorCodesMatchAppendixB4Snapshot() {
        assertEquals(
            listOf(
                "PLUGIN_UNAVAILABLE", "PLUGIN_DISABLED", "PLUGIN_INCOMPATIBLE",
                "FILE_NOT_FOUND", "FILE_UNREADABLE", "NOT_EPUB", "PARSE_FAILED", "ENCRYPTED", "RESOURCE_NOT_FOUND",
                "LIMIT_EXCEEDED", "INVALID_ARGUMENT", "SESSION_CLOSED", "SESSION_REPLACED", "READER_NOT_VISIBLE",
                "UNSUPPORTED_PREFERENCE", "CANCELLED", "TIMEOUT", "IO", "INTERNAL",
            ),
            EpubErrorCodes.ALL,
        )
        assertEquals(EpubErrorCodes.ALL.size, EpubErrorCodes.ALL.toSet().size)
        val declared = constantsOf(EpubErrorCodes::class.java, "")
        assertEquals(declared.values.toSet(), EpubErrorCodes.ALL.toSet())
        EpubErrorCodes.ALL.forEach { code ->
            assertEquals(code, code.uppercase())
            assertTrue(code, EpubErrorCodes.isKnown(code))
        }
        assertFalse(EpubErrorCodes.isKnown(null))
        assertFalse(EpubErrorCodes.isKnown("NOT_A_CODE"))
        assertTrue(EpubErrorCodes.HOST_ONLY.all(EpubErrorCodes::isKnown))
        assertEquals(setOf("PLUGIN_UNAVAILABLE", "PLUGIN_DISABLED", "PLUGIN_INCOMPATIBLE"), EpubErrorCodes.HOST_ONLY)
    }

    @Test
    fun exceptionMessagesRoundTripThroughEncodeAndDecode() {
        assertEquals("RESOURCE_NOT_FOUND: OPS/missing.xhtml", EpubErrorCodes.encode(EpubErrorCodes.RESOURCE_NOT_FOUND, "OPS/missing.xhtml"))
        assertEquals("SESSION_CLOSED", EpubErrorCodes.encode(EpubErrorCodes.SESSION_CLOSED))
        assertEquals("SESSION_CLOSED", EpubErrorCodes.encode(EpubErrorCodes.SESSION_CLOSED, "  "))
        assertEquals("RESOURCE_NOT_FOUND" to "OPS/missing.xhtml", EpubErrorCodes.decode("RESOURCE_NOT_FOUND: OPS/missing.xhtml"))
        assertEquals("SESSION_CLOSED" to "", EpubErrorCodes.decode("SESSION_CLOSED"))
        assertEquals("INVALID_ARGUMENT" to "a: b: c", EpubErrorCodes.decode(" INVALID_ARGUMENT: a: b: c "))
        assertNull(EpubErrorCodes.decode(null))
        assertNull(EpubErrorCodes.decode(""))
        assertNull(EpubErrorCodes.decode("Something went wrong"))
        assertNull(EpubErrorCodes.decode("SESSION_CLOSED_LATER: x"))
        val failure = runCatching { EpubErrorCodes.encode("NOT_A_CODE", "x") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private fun constants(prefix: String): Map<String, String> = constantsOf(EpubContract::class.java, prefix)

    private fun constantsOf(type: Class<*>, prefix: String): Map<String, String> {
        return type.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name.startsWith(prefix) }
            .filter { it.name != "INSTANCE" && it.name != "SEPARATOR" }
            .associate { field -> field.name to (field.apply { isAccessible = true }.get(null) as String) }
    }
}
