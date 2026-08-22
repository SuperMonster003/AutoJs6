package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmToastPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class JvmHostAppLaunchBridgeTest {
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
}
