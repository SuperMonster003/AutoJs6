package org.autojs.plugin.gallery.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class GalleryContractTest {

    @Test
    fun identityAndDiscoveryLiteralsAreStable() {
        assertEquals(PluginActions.INFO, GalleryActions.INFO)
        assertEquals("org.autojs.plugin.GALLERY", GalleryActions.SERVICE_ACTION)
        assertEquals("gallery", GalleryActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", GalleryActions.PLUGIN_PERMISSION)
        assertEquals("org.autojs.plugin.GALLERY_SESSION_OPEN", GalleryActions.SESSION_ACTIVITY_ACTION)
        assertEquals("org.autojs.plugin.gallery.extra.SESSION_TOKEN", GalleryActions.EXTRA_SESSION_TOKEN)

        assertEquals("three-maple-image", GalleryIds.PLUGIN_ID)
        assertEquals("gallery", GalleryIds.ENGINE)
        assertEquals("default", GalleryIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.three.maple.image", GalleryIds.DEFAULT_PACKAGE_NAME)
        assertEquals(5439L, GalleryIds.REQUIRED_HOST_VERSION_CODE)

        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, GalleryCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("galleryContractVersion", GalleryCapabilityKeys.CONTRACT_VERSION)
        assertEquals("galleryMaxContractVersion", GalleryCapabilityKeys.MAX_CONTRACT_VERSION)
        assertEquals("galleryFeatures", GalleryCapabilityKeys.FEATURES)
    }

    @Test
    fun contractVersionRangeIncludesItsBoundsAndRejectsNeighbours() {
        assertEquals(1, GalleryContract.CONTRACT_VERSION)
        assertEquals(1, GalleryContract.MIN_CONTRACT_VERSION)
        assertEquals(1, GalleryContract.MAX_CONTRACT_VERSION)
        assertTrue(GalleryContract.supportsContractVersion(GalleryContract.CONTRACT_VERSION))
        assertFalse(GalleryContract.supportsContractVersion(GalleryContract.MIN_CONTRACT_VERSION - 1))
        assertFalse(GalleryContract.supportsContractVersion(GalleryContract.MAX_CONTRACT_VERSION + 1))
    }

    @Test
    fun bundleKeysAreUniqueAndFrozen() {
        val keys = constants("KEY_")
        assertEquals(keys.size, keys.values.toSet().size)
        assertEquals(
            listOf(
                "contractVersion", "errorCode", "errorMessage", "event", "reason", "sessionToken", "kind", "visible", "finish",
                "multiple", "maxCount", "mimeTypes", "title", "items",
                "displayNames", "startIndex", "index",
                "displayName", "format", "quality", "lossless", "targetBytes", "sizeMode", "percent", "longEdge", "width", "height",
                "keepAspectRatio", "background", "keepExif", "keepLocation", "mimeType", "size",
            ).sorted(),
            keys.values.sorted(),
        )
    }

    @Test
    fun pickerItemFieldsAreFrozen() {
        val fields = constants("FIELD_")
        assertEquals(fields.size, fields.values.toSet().size)
        assertEquals(listOf("dateTaken", "height", "mimeType", "name", "path", "size", "width"), fields.values.sorted())
    }

    @Test
    fun closedValueSetsAreFrozen() {
        assertEquals(setOf("open", "page", "result", "error", "close"), GalleryContract.EVENTS)
        assertEquals(setOf("user", "host", "done", "timeout", "replaced", "error"), GalleryContract.REASONS)
        assertEquals(setOf("picker", "viewer", "editor"), GalleryContract.KINDS)
        assertEquals(setOf("jpeg", "png", "webp"), GalleryContract.FORMATS)
        assertEquals(setOf("original", "percent", "longEdge", "custom"), GalleryContract.SIZE_MODES)
        assertEquals(setOf("picker", "viewer", "editor", "convert"), GalleryContract.FEATURES)
        assertEquals(constants("EVENT_").values.toSet(), GalleryContract.EVENTS)
        assertEquals(constants("REASON_").values.toSet(), GalleryContract.REASONS)
        assertEquals(constants("KIND_").values.toSet(), GalleryContract.KINDS)
        assertEquals(constants("FORMAT_").values.toSet(), GalleryContract.FORMATS)
        assertEquals(constants("SIZE_").values.toSet(), GalleryContract.SIZE_MODES)
        assertEquals(constants("FEATURE_").values.toSet(), GalleryContract.FEATURES)
    }

    @Test
    fun ceilingsAreFrozenAndLayered() {
        assertEquals(92, GalleryContract.DEFAULT_QUALITY)
        assertEquals(200, GalleryContract.MAX_PICK_COUNT)
        assertEquals(128, GalleryContract.MAX_VIEW_PAGES)
        assertEquals(16_384, GalleryContract.MAX_DIMENSION)
        assertEquals(40_000_000L, GalleryContract.MAX_PIXELS)
        assertEquals(262_144, GalleryContract.MAX_EVENT_BYTES)
        assertEquals(60_000L, GalleryContract.SESSION_CLAIM_TIMEOUT_MS)
        assertEquals(30_000L, GalleryContract.CALL_TIMEOUT_MS)
        assertEquals(180_000L, GalleryContract.CONVERT_TIMEOUT_MS)

        assertTrue(GalleryContract.DEFAULT_QUALITY in GalleryContract.MIN_QUALITY..GalleryContract.MAX_QUALITY)
        assertTrue(GalleryContract.MIN_PERCENT < GalleryContract.MAX_PERCENT)
        assertTrue(GalleryContract.MAX_DIMENSION.toLong() * GalleryContract.MAX_DIMENSION > GalleryContract.MAX_PIXELS)
        assertTrue(GalleryContract.MAX_ERROR_MESSAGE_BYTES < GalleryContract.MAX_EVENT_BYTES)
        assertTrue(GalleryContract.MAX_OPTIONS_BYTES < GalleryContract.MAX_EVENT_BYTES)
        // A oneway transaction must stay well inside the 1 MiB Binder buffer shared by the process.
        assertTrue(GalleryContract.MAX_EVENT_BYTES <= 256 * 1024)
        assertTrue(GalleryContract.CALL_TIMEOUT_MS < GalleryContract.SESSION_CLAIM_TIMEOUT_MS)
        assertTrue(GalleryContract.SESSION_CLAIM_TIMEOUT_MS < GalleryContract.CONVERT_TIMEOUT_MS)
    }

    @Test
    fun errorCodesAreFrozen() {
        assertEquals(
            listOf(
                "PLUGIN_UNAVAILABLE", "PLUGIN_DISABLED", "PLUGIN_INCOMPATIBLE",
                "FILE_NOT_FOUND", "FILE_UNREADABLE", "UNSUPPORTED_FORMAT", "DECODE_FAILED", "LIMIT_EXCEEDED", "INVALID_ARGUMENT",
                "PERMISSION_REQUIRED", "ACCESS_DENIED", "OUTPUT_EXISTS",
                "SESSION_CLOSED", "SESSION_REPLACED", "CANCELLED", "TIMEOUT", "IO", "INTERNAL",
            ),
            GalleryErrorCodes.ALL,
        )
        assertEquals(GalleryErrorCodes.ALL.size, GalleryErrorCodes.ALL.toSet().size)
        assertEquals(constantsOf(GalleryErrorCodes::class.java, "").values.toSet(), GalleryErrorCodes.ALL.toSet())
        GalleryErrorCodes.ALL.forEach { code -> assertTrue(code, GalleryErrorCodes.isKnown(code)) }
        assertFalse(GalleryErrorCodes.isKnown(null))
        assertFalse(GalleryErrorCodes.isKnown("NOT_A_CODE"))
        assertEquals(
            setOf("PLUGIN_UNAVAILABLE", "PLUGIN_DISABLED", "PLUGIN_INCOMPATIBLE", "FILE_NOT_FOUND", "ACCESS_DENIED", "OUTPUT_EXISTS"),
            GalleryErrorCodes.HOST_ONLY,
        )
    }

    @Test
    fun exceptionMessagesRoundTripThroughEncodeAndDecode() {
        assertEquals("DECODE_FAILED: broken.jpg", GalleryErrorCodes.encode(GalleryErrorCodes.DECODE_FAILED, "broken.jpg"))
        assertEquals("SESSION_CLOSED", GalleryErrorCodes.encode(GalleryErrorCodes.SESSION_CLOSED, "  "))
        assertEquals("INVALID_ARGUMENT" to "a: b", GalleryErrorCodes.decode(" INVALID_ARGUMENT: a: b "))
        assertEquals("SESSION_CLOSED" to "", GalleryErrorCodes.decode("SESSION_CLOSED"))
        assertNull(GalleryErrorCodes.decode(null))
        assertNull(GalleryErrorCodes.decode("Something went wrong"))
        assertNull(GalleryErrorCodes.decode("SESSION_CLOSED_LATER: x"))
        assertTrue(runCatching { GalleryErrorCodes.encode("NOT_A_CODE") }.exceptionOrNull() is IllegalArgumentException)
    }

    private fun constants(prefix: String): Map<String, String> = constantsOf(GalleryContract::class.java, prefix)

    private fun constantsOf(type: Class<*>, prefix: String): Map<String, String> {
        return type.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name.startsWith(prefix) }
            .filter { it.name != "INSTANCE" && it.name != "SEPARATOR" }
            .associate { field -> field.name to (field.apply { isAccessible = true }.get(null) as String) }
    }
}
