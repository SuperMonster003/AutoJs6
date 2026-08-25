package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmClipboardPayload
import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmToastPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class JvmHostAppLaunchBridgeTest {
    @Test
    fun separatelyAllowlistedClipboardGetAndSetRoundTripCanonicalText() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        var clipboard = "before"
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            listOf(
                JvmHostAppLaunchBridge.METHOD_CLIPBOARD_GET,
                JvmHostAppLaunchBridge.METHOD_CLIPBOARD_SET,
            ),
            launchPackage = { false },
            readClipboard = { clipboard },
            writeClipboard = { clipboard = it },
            onViolation = {},
        )

        val get = dispatch(
            bridge,
            JvmHostCall(
                requestId,
                callId = 10,
                method = JvmHostAppLaunchBridge.METHOD_CLIPBOARD_GET,
                payloadJson = JvmClipboardPayload.GET_REQUEST_JSON,
                timeoutMillis = 1_000,
            ),
        )
        assertEquals("before", JvmClipboardPayload.decodeText(requireNotNull(get.payloadJson)))

        val set = dispatch(
            bridge,
            JvmHostCall(
                requestId,
                callId = 11,
                method = JvmHostAppLaunchBridge.METHOD_CLIPBOARD_SET,
                payloadJson = JvmClipboardPayload.encodeText("after 你好"),
                timeoutMillis = 1_000,
            ),
        )
        assertTrue(set.succeeded)
        assertEquals("true", set.payloadJson)
        assertEquals("after 你好", clipboard)
        assertEquals(JvmHostAppLaunchBridge.Stats(2, 0), bridge.stats())
    }

    @Test
    fun clipboardReadAndWriteAreDeniedIndependentlyBeforeHostActions() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        var reads = 0
        var writes = 0
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            allowedMethods = emptyList(),
            launchPackage = { false },
            readClipboard = { reads += 1; "secret" },
            writeClipboard = { writes += 1 },
            onViolation = {},
        )

        val get = dispatch(
            bridge,
            JvmHostCall(
                requestId,
                12,
                JvmHostAppLaunchBridge.METHOD_CLIPBOARD_GET,
                JvmClipboardPayload.GET_REQUEST_JSON,
                1_000,
            ),
        )
        val set = dispatch(
            bridge,
            JvmHostCall(
                requestId,
                13,
                JvmHostAppLaunchBridge.METHOD_CLIPBOARD_SET,
                JvmClipboardPayload.encodeText("blocked"),
                1_000,
            ),
        )

        assertFalse(get.succeeded)
        assertFalse(set.succeeded)
        assertEquals("METHOD_NOT_ALLOWED", get.errorCode)
        assertEquals("METHOD_NOT_ALLOWED", set.errorCode)
        assertEquals(0, reads)
        assertEquals(0, writes)
        assertEquals(JvmHostAppLaunchBridge.Stats(0, 2), bridge.stats())
    }

    @Test
    fun nonCanonicalClipboardPayloadIsRejectedBeforeWrite() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        var writes = 0
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            listOf(JvmHostAppLaunchBridge.METHOD_CLIPBOARD_SET),
            launchPackage = { false },
            writeClipboard = { writes += 1 },
            onViolation = {},
        )

        val response = dispatch(
            bridge,
            JvmHostCall(
                requestId,
                14,
                JvmHostAppLaunchBridge.METHOD_CLIPBOARD_SET,
                "{\"text\":\"safe\",\"extra\":true}",
                1_000,
            ),
        )

        assertFalse(response.succeeded)
        assertEquals("INVALID_PAYLOAD", response.errorCode)
        assertEquals(0, writes)
    }

    @Test
    fun oversizedClipboardReadIsReturnedAsStableRedactedFailure() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        val privateValue = "s".repeat(JvmSourceContract.MAX_CLIPBOARD_TEXT_BYTES + 1)
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            listOf(JvmHostAppLaunchBridge.METHOD_CLIPBOARD_GET),
            launchPackage = { false },
            readClipboard = { privateValue },
            onViolation = {},
        )

        val response = dispatch(
            bridge,
            JvmHostCall(
                requestId,
                15,
                JvmHostAppLaunchBridge.METHOD_CLIPBOARD_GET,
                JvmClipboardPayload.GET_REQUEST_JSON,
                1_000,
            ),
        )

        assertFalse(response.succeeded)
        assertEquals("CLIPBOARD_GET_FAILED", response.errorCode)
        assertEquals("Unable to read clipboard text", response.errorMessage)
        assertFalse(response.errorMessage.orEmpty().contains(privateValue))
    }

    @Test
    fun canonicalToastPayloadInvokesHostAndReturnsSuccess() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        var shown: String? = null
        val violations = mutableListOf<Throwable>()
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            listOf(JvmHostAppLaunchBridge.METHOD_TOAST_SHOW),
            launchPackage = { false },
            showToast = { shown = it },
            onViolation = violations::add,
        )
        var response: ByteArray? = null

        bridge.dispatch(
            JvmSourceCodec.encodeHostCall(
                JvmHostCall(
                    requestId,
                    callId = 4,
                    method = JvmHostAppLaunchBridge.METHOD_TOAST_SHOW,
                    payloadJson = JvmToastPayload.encode("M5 toast 你好"),
                    timeoutMillis = 1_000,
                ),
            ),
        ) { response = it }

        assertEquals("M5 toast 你好", shown)
        assertTrue(violations.isEmpty())
        val decoded = JvmSourceCodec.decodeHostResponse(requireNotNull(response))
        assertTrue(decoded.succeeded)
        assertEquals("true", decoded.payloadJson)
    }

    @Test
    fun nonCanonicalToastPayloadIsRejectedBeforeHostUiCall() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        var shown = false
        val violations = mutableListOf<Throwable>()
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            listOf(JvmHostAppLaunchBridge.METHOD_TOAST_SHOW),
            launchPackage = { false },
            showToast = { shown = true },
            onViolation = violations::add,
        )
        var response: ByteArray? = null

        bridge.dispatch(
            JvmSourceCodec.encodeHostCall(
                JvmHostCall(
                    requestId,
                    callId = 5,
                    method = JvmHostAppLaunchBridge.METHOD_TOAST_SHOW,
                    payloadJson = "{\"message\":\"safe\",\"message\":\"other\"}",
                    timeoutMillis = 1_000,
                ),
            ),
        ) { response = it }

        assertFalse(shown)
        assertTrue(violations.isNotEmpty())
        assertFalse(JvmSourceCodec.decodeHostResponse(requireNotNull(response)).succeeded)
    }

    @Test
    fun exactAppLaunchPayloadReturnsJsonBoolean() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        var launched: String? = null
        val violations = mutableListOf<Throwable>()
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            listOf(JvmHostAppLaunchBridge.METHOD_APP_LAUNCH),
            launchPackage = { packageName -> launched = packageName; true },
            onViolation = violations::add,
        )
        var response: ByteArray? = null
        bridge.dispatch(
            JvmSourceCodec.encodeHostCall(
                JvmHostCall(
                    requestId,
                    callId = 1,
                    method = "app.launch",
                    payloadJson = "{\"packageName\":\"org.example.app\"}",
                    timeoutMillis = 1_000,
                ),
            ),
        ) { response = it }

        assertEquals("org.example.app", launched)
        assertTrue(violations.isEmpty())
        val decoded = JvmSourceCodec.decodeHostResponse(requireNotNull(response))
        assertTrue(decoded.succeeded)
        assertEquals("true", decoded.payloadJson)
        assertEquals(JvmHostAppLaunchBridge.Stats(1, 0), bridge.stats())
    }

    @Test
    fun duplicatePackageNameAuthorityIsRejectedAndReported() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        var launched = false
        val violations = mutableListOf<Throwable>()
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            listOf("app.launch"),
            launchPackage = { launched = true; true },
            onViolation = violations::add,
        )
        var response: ByteArray? = null
        bridge.dispatch(
            JvmSourceCodec.encodeHostCall(
                JvmHostCall(
                    requestId,
                    2,
                    "app.launch",
                    "{\"packageName\":\"org.example.app\",\"packageName\":\"org.evil.app\"}",
                    1_000,
                ),
            ),
        ) { response = it }

        assertFalse(launched)
        assertTrue(violations.isNotEmpty())
        assertFalse(JvmSourceCodec.decodeHostResponse(requireNotNull(response)).succeeded)
        assertEquals(JvmHostAppLaunchBridge.Stats(0, 1), bridge.stats())
    }

    @Test
    fun closeReleasesTheSessionFacadeAndRejectsEveryLaterHostOperation() {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        var launched = false
        val bridge = JvmHostAppLaunchBridge(
            requestId,
            listOf("app.launch"),
            launchPackage = { launched = true; true },
            onViolation = {},
        )
        bridge.close()
        var response: ByteArray? = null

        bridge.dispatch(
            JvmSourceCodec.encodeHostCall(
                JvmHostCall(
                    requestId,
                    3,
                    "app.launch",
                    "{\"packageName\":\"org.example.app\"}",
                    1_000,
                ),
            ),
        ) { response = it }

        assertFalse(launched)
        val decoded = JvmSourceCodec.decodeHostResponse(requireNotNull(response))
        assertFalse(decoded.succeeded)
        assertEquals("BRIDGE_CLOSED", decoded.errorCode)
    }

    private fun dispatch(bridge: JvmHostAppLaunchBridge, call: JvmHostCall) = run {
        var encodedResponse: ByteArray? = null
        bridge.dispatch(JvmSourceCodec.encodeHostCall(call)) { encodedResponse = it }
        JvmSourceCodec.decodeHostResponse(requireNotNull(encodedResponse))
    }
}
