package org.autojs.autojs.core.plugin.python

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonHostCapabilityDispatcherTest {
    @Test
    fun dispatchesTheCompleteFirstLowRiskCapabilitySliceWithTypedJsonResults() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)

        assertNull(success(dispatcher.dispatch(request(1, PythonHostCapabilities.TOAST_SHOW, "text" to "hello"))))
        assertNull(success(dispatcher.dispatch(request(2, PythonHostCapabilities.CLIP_SET, "text" to "copy"))))
        assertEquals("copy", success(dispatcher.dispatch(request(3, PythonHostCapabilities.CLIP_GET))))
        assertEquals(
            true,
            success(dispatcher.dispatch(request(4, PythonHostCapabilities.APP_LAUNCH, "package" to "org.example.app"))),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(5, PythonHostCapabilities.APP_LAUNCH_APP, "name" to "Example"))),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(6, PythonHostCapabilities.APP_OPEN_URL, "url" to "https://example.org"))),
        )
        assertEquals(
            DEVICE_INFO.toJson(),
            success(dispatcher.dispatch(request(7, PythonHostCapabilities.DEVICE_INFO))),
        )
        assertNull(success(dispatcher.dispatch(request(8, PythonHostCapabilities.CONSOLE_LOG, "text" to "log"))))
        assertNull(success(dispatcher.dispatch(request(9, PythonHostCapabilities.CONSOLE_WARN, "text" to "warn"))))
        assertNull(success(dispatcher.dispatch(request(10, PythonHostCapabilities.CONSOLE_ERROR, "text" to "error"))))
        assertNull(success(dispatcher.dispatch(request(11, PythonHostCapabilities.NOTICE_SHOW, "text" to "notice"))))

        assertEquals(listOf("hello"), actions.toasts)
        assertEquals("copy", actions.clipboard)
        assertEquals(listOf("org.example.app"), actions.packages)
        assertEquals(listOf("Example"), actions.apps)
        assertEquals(listOf("https://example.org"), actions.urls)
        assertEquals(
            listOf(
                PythonHostConsoleLevel.LOG to "log",
                PythonHostConsoleLevel.WARN to "warn",
                PythonHostConsoleLevel.ERROR to "error",
            ),
            actions.console,
        )
        assertEquals(listOf("notice"), actions.notices)
    }

    @Test
    fun bindsExecutionIdRejectsReplayAndRejectsEveryCallAfterClose() {
        val dispatcher = dispatcher(RecordingActions())
        val request = request(1, PythonHostCapabilities.CLIP_GET)
        assertEquals("", success(dispatcher.dispatch(request)))
        assertEquals("REPLAYED_CALL", failureCode(dispatcher.dispatch(request)))

        assertTrue(dispatcher.close())
        assertFalse(dispatcher.close())
        assertEquals(
            "BROKER_CLOSED",
            failureCode(dispatcher.dispatch(request(2, PythonHostCapabilities.CLIP_GET))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch(request(3, PythonHostCapabilities.CLIP_GET, executionId = "other"))
        }
    }

    @Test
    fun enforcesGrantArgumentAndLifetimeQuotasWithoutInvokingActions() {
        val actions = RecordingActions()
        val denied = PythonHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = setOf(PythonHostCapabilities.CLIP_GET),
            actions = actions,
        )
        assertEquals(
            "CAPABILITY_UNAVAILABLE",
            failureCode(denied.dispatch(request(1, PythonHostCapabilities.TOAST_SHOW, "text" to "no"))),
        )
        assertTrue(actions.toasts.isEmpty())

        val invalid = dispatcher(actions)
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(invalid.dispatch(request(1, PythonHostCapabilities.APP_LAUNCH, "package" to "bad package"))),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(invalid.dispatch(request(2, PythonHostCapabilities.NOTICE_SHOW, "text" to ""))),
        )

        actions.denyNotice = true
        assertEquals(
            "PERMISSION_DENIED",
            failureCode(invalid.dispatch(request(3, PythonHostCapabilities.NOTICE_SHOW, "text" to "denied"))),
        )
        assertTrue(actions.notices.isEmpty())

        val bounded = PythonHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = PythonHostCapabilities.ENABLED,
            actions = actions,
            maxCalls = 1,
        )
        assertEquals("", success(bounded.dispatch(request(1, PythonHostCapabilities.CLIP_GET))))
        assertEquals(
            "CALL_LIMIT_EXCEEDED",
            failureCode(bounded.dispatch(request(2, PythonHostCapabilities.CLIP_GET))),
        )
    }

    @Test
    fun rejectsMalformedOrOversizedJsonBeforeDispatch() {
        val dispatcher = dispatcher(RecordingActions())
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch("{}".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch(byteArrayOf(0x80.toByte()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch(ByteArray(64 * 1024 + 1))
        }
    }

    private fun dispatcher(actions: RecordingActions) = PythonHostCapabilityDispatcher(
        executionId = EXECUTION_ID,
        grantedCapabilities = PythonHostCapabilities.ENABLED,
        actions = actions,
    )

    private fun request(
        callId: Long,
        capability: String,
        vararg arguments: Pair<String, Any>,
        executionId: String = EXECUTION_ID,
    ): ByteArray = JsonObject().apply {
        addProperty("version", 1)
        addProperty("executionId", executionId)
        addProperty("callId", callId)
        addProperty("capability", capability)
        add("arguments", JsonObject().apply {
            arguments.forEach { (name, value) -> addProperty(name, value.toString()) }
        })
    }.toString().toByteArray()

    private fun success(encoded: ByteArray): Any? {
        val response = JsonParser.parseString(String(encoded)).asJsonObject
        assertTrue(response.get("ok").asBoolean)
        return when (val value = response.get("value")) {
            null, JsonNull.INSTANCE -> null
            else -> when {
                value.isJsonObject -> value.asJsonObject
                value.asJsonPrimitive.isBoolean -> value.asBoolean
                value.asJsonPrimitive.isString -> value.asString
                else -> error("Unexpected test response type")
            }
        }
    }

    private fun failureCode(encoded: ByteArray): String {
        val response = JsonParser.parseString(String(encoded)).asJsonObject
        assertFalse(response.get("ok").asBoolean)
        return response.getAsJsonObject("error").get("code").asString
    }

    private class RecordingActions : PythonHostCapabilityActions {
        val toasts = mutableListOf<String>()
        var clipboard = ""
        val packages = mutableListOf<String>()
        val apps = mutableListOf<String>()
        val urls = mutableListOf<String>()
        val console = mutableListOf<Pair<PythonHostConsoleLevel, String>>()
        val notices = mutableListOf<String>()
        var denyNotice = false

        override fun showToast(text: String) {
            toasts += text
        }

        override fun getClipboardText(): String = clipboard

        override fun setClipboardText(text: String) {
            clipboard = text
        }

        override fun launchPackage(packageName: String): Boolean {
            packages += packageName
            return true
        }

        override fun launchApp(label: String): Boolean {
            apps += label
            return true
        }

        override fun openUrl(url: String): Boolean {
            urls += url
            return true
        }

        override fun getDeviceInfo(): PythonHostDeviceInfo = DEVICE_INFO

        override fun writeConsole(level: PythonHostConsoleLevel, text: String) {
            console += level to text
        }

        override fun postNotice(text: String) {
            if (denyNotice) throw PythonHostCapabilityPermissionDeniedException()
            notices += text
        }
    }

    private companion object {
        const val EXECUTION_ID = "12345678-1234-5678-9abc-def012345678"
        val DEVICE_INFO = PythonHostDeviceInfo(
            batteryPercent = 88.5,
            charging = true,
            screenOn = true,
            brightness = 127,
            musicVolume = 7,
            musicMaxVolume = 15,
            notificationVolume = 4,
            notificationMaxVolume = 7,
            alarmVolume = 5,
            alarmMaxVolume = 7,
        )
    }
}
