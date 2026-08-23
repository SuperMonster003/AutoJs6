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
    fun dispatchesEnabledCapabilitiesWithTypedJsonResults() {
        val actions = RecordingActions()
        actions.fileTexts["seed.txt"] = "seed"
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
        assertEquals(
            "seed",
            success(dispatcher.dispatch(request(12, PythonHostCapabilities.FILES_READ_TEXT, "path" to "seed.txt"))),
        )
        assertNull(
            success(
                dispatcher.dispatch(
                    request(
                        13,
                        PythonHostCapabilities.FILES_WRITE_TEXT,
                        "path" to "written.txt",
                        "text" to "written",
                    ),
                ),
            ),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(14, PythonHostCapabilities.FILES_EXISTS, "path" to "written.txt"))),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(15, PythonHostCapabilities.FILES_IS_FILE, "path" to "written.txt"))),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(16, PythonHostCapabilities.FILES_IS_DIR, "path" to "."))),
        )
        assertEquals(
            listOf("seed.txt", "written.txt"),
            success(dispatcher.dispatch(request(17, PythonHostCapabilities.FILES_LIST, "path" to "."))),
        )
        assertNull(
            success(
                dispatcher.dispatch(
                    request(
                        18,
                        PythonHostCapabilities.DIALOGS_ALERT,
                        "title" to "Alert",
                        "text" to "alert text",
                    ),
                ),
            ),
        )
        assertEquals(
            true,
            success(
                dispatcher.dispatch(
                    request(
                        19,
                        PythonHostCapabilities.DIALOGS_CONFIRM,
                        "title" to "Confirm",
                        "text" to "confirm text",
                    ),
                ),
            ),
        )
        assertEquals(
            "typed value",
            success(
                dispatcher.dispatch(
                    request(
                        20,
                        PythonHostCapabilities.DIALOGS_PROMPT,
                        "title" to "Prompt",
                        "text" to "prompt text",
                        "default" to "seed",
                    ),
                ),
            ),
        )
        assertEquals(
            1,
            success(
                dispatcher.dispatch(
                    request(
                        21,
                        PythonHostCapabilities.DIALOGS_SELECT,
                        "title" to "Select",
                        "items" to listOf("first", "second"),
                    ),
                ),
            ),
        )
        assertEquals(
            ENGINE_INFO.toJson(),
            success(dispatcher.dispatch(request(22, PythonHostCapabilities.ENGINES_CURRENT))),
        )
        assertEquals(
            ENGINE_LAUNCH.toJson(),
            success(
                dispatcher.dispatch(
                    request(23, PythonHostCapabilities.ENGINES_RUN, "path" to "child.js"),
                ),
            ),
        )
        assertNull(success(dispatcher.dispatch(request(24, PythonHostCapabilities.ENGINES_STOP_SELF))))

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
        assertEquals(mapOf("seed.txt" to "seed", "written.txt" to "written"), actions.fileTexts)
        assertEquals(listOf("Alert" to "alert text"), actions.alerts)
        assertEquals(listOf("Confirm" to "confirm text"), actions.confirmations)
        assertEquals(listOf(Triple("Prompt", "prompt text", "seed")), actions.prompts)
        assertEquals(listOf("Select" to listOf("first", "second")), actions.selections)
        assertEquals(listOf("child.js"), actions.scriptPaths)
        assertEquals(1, actions.stopSelfRequests)
    }

    @Test
    fun validatesEngineArgumentsAndMapsBoundedLaunchFailures() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)

        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(1, PythonHostCapabilities.ENGINES_CURRENT, "path" to "child.js"),
                ),
            ),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(2, PythonHostCapabilities.ENGINES_STOP_SELF, "path" to "child.js"),
                ),
            ),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(3, PythonHostCapabilities.ENGINES_RUN, "path" to "../outside.js"),
                ),
            ),
        )
        assertEquals(0, actions.stopSelfRequests)
        assertEquals(emptyList<String>(), actions.scriptPaths)

        actions.engineFailure = PythonHostCapabilityNestedPythonException()
        assertEquals(
            "NESTED_PYTHON_NOT_ALLOWED",
            failureCode(
                dispatcher.dispatch(
                    request(4, PythonHostCapabilities.ENGINES_RUN, "path" to "child.py"),
                ),
            ),
        )
        actions.engineFailure = PythonHostCapabilityEngineLaunchLimitException()
        assertEquals(
            "ENGINE_LAUNCH_LIMIT_EXCEEDED",
            failureCode(
                dispatcher.dispatch(
                    request(5, PythonHostCapabilities.ENGINES_RUN, "path" to "child.js"),
                ),
            ),
        )
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
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                invalid.dispatch(
                    request(3, PythonHostCapabilities.FILES_READ_TEXT, "path" to "../outside.txt"),
                ),
            ),
        )

        actions.denyNotice = true
        assertEquals(
            "PERMISSION_DENIED",
            failureCode(invalid.dispatch(request(4, PythonHostCapabilities.NOTICE_SHOW, "text" to "denied"))),
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
    fun mapsBoundedFileFailuresToStableCodes() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)
        val failures = listOf(
            PythonHostCapabilityPathNotFoundException() to "PATH_NOT_FOUND",
            PythonHostCapabilityPathTypeException() to "PATH_TYPE_MISMATCH",
            PythonHostCapabilityFileEncodingException(IllegalArgumentException()) to "FILE_ENCODING_ERROR",
            PythonHostCapabilityResultLimitException() to "RESULT_LIMIT_EXCEEDED",
            SecurityException() to "PERMISSION_DENIED",
        )

        failures.forEachIndexed { index, (failure, expectedCode) ->
            actions.fileFailure = failure
            assertEquals(
                expectedCode,
                failureCode(
                    dispatcher.dispatch(
                        request(
                            (index + 1).toLong(),
                            PythonHostCapabilities.FILES_READ_TEXT,
                            "path" to "seed.txt",
                        ),
                    ),
                ),
            )
        }
        assertTrue(actions.fileTexts.isEmpty())
    }

    @Test
    fun boundsDialogArgumentsAndMapsMissingForegroundAuthority() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(
                        1,
                        PythonHostCapabilities.DIALOGS_ALERT,
                        "title" to "",
                        "text" to "text",
                    ),
                ),
            ),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(
                        2,
                        PythonHostCapabilities.DIALOGS_SELECT,
                        "title" to "Select",
                        "items" to emptyList<String>(),
                    ),
                ),
            ),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(
                        3,
                        PythonHostCapabilities.DIALOGS_SELECT,
                        "title" to "Select",
                        "items" to List(PythonHostDialogPolicy.MAX_ITEMS + 1) { "item" },
                    ),
                ),
            ),
        )
        assertTrue(actions.alerts.isEmpty())
        assertTrue(actions.selections.isEmpty())

        actions.dialogFailure = PythonHostCapabilityInteractiveNotAllowedException()
        assertEquals(
            "INTERACTIVE_NOT_ALLOWED",
            failureCode(
                dispatcher.dispatch(
                    request(
                        4,
                        PythonHostCapabilities.DIALOGS_CONFIRM,
                        "title" to "Confirm",
                        "text" to "foreground only",
                    ),
                ),
            ),
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
            arguments.forEach { (name, value) ->
                when (value) {
                    is Iterable<*> -> add(
                        name,
                        com.google.gson.JsonArray().apply {
                            value.forEach { entry -> add(requireNotNull(entry).toString()) }
                        },
                    )
                    is Boolean -> addProperty(name, value)
                    is Number -> addProperty(name, value)
                    else -> addProperty(name, value.toString())
                }
            }
        })
    }.toString().toByteArray()

    private fun success(encoded: ByteArray): Any? {
        val response = JsonParser.parseString(String(encoded)).asJsonObject
        assertTrue(response.get("ok").asBoolean)
        return when (val value = response.get("value")) {
            null, JsonNull.INSTANCE -> null
            else -> when {
                value.isJsonObject -> value.asJsonObject
                value.isJsonArray -> value.asJsonArray.map { it.asString }
                value.asJsonPrimitive.isBoolean -> value.asBoolean
                value.asJsonPrimitive.isNumber -> value.asInt
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
        val fileTexts = linkedMapOf<String, String>()
        val alerts = mutableListOf<Pair<String, String>>()
        val confirmations = mutableListOf<Pair<String, String>>()
        val prompts = mutableListOf<Triple<String, String, String>>()
        val selections = mutableListOf<Pair<String, List<String>>>()
        val scriptPaths = mutableListOf<String>()
        var stopSelfRequests = 0
        var denyNotice = false
        var fileFailure: RuntimeException? = null
        var dialogFailure: RuntimeException? = null
        var engineFailure: RuntimeException? = null

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

        override fun readFileText(path: String): String {
            fileFailure?.let { throw it }
            return fileTexts[path].orEmpty()
        }

        override fun writeFileText(path: String, text: String) {
            fileFailure?.let { throw it }
            fileTexts[path] = text
        }

        override fun fileExists(path: String): Boolean {
            fileFailure?.let { throw it }
            return path == "." || path in fileTexts
        }

        override fun isFile(path: String): Boolean {
            fileFailure?.let { throw it }
            return path in fileTexts
        }

        override fun isDirectory(path: String): Boolean {
            fileFailure?.let { throw it }
            return path == "."
        }

        override fun listDirectory(path: String): List<String> {
            fileFailure?.let { throw it }
            return fileTexts.keys.sorted()
        }

        override fun showDialogAlert(title: String, text: String) {
            dialogFailure?.let { throw it }
            alerts += title to text
        }

        override fun showDialogConfirm(title: String, text: String): Boolean {
            dialogFailure?.let { throw it }
            confirmations += title to text
            return true
        }

        override fun showDialogPrompt(title: String, text: String, defaultValue: String): String? {
            dialogFailure?.let { throw it }
            prompts += Triple(title, text, defaultValue)
            return "typed value"
        }

        override fun showDialogSelect(title: String, items: List<String>): Int? {
            dialogFailure?.let { throw it }
            selections += title to items
            return 1
        }

        override fun currentEngine(): PythonHostEngineInfo = ENGINE_INFO

        override fun requestStopSelf() {
            engineFailure?.let { throw it }
            stopSelfRequests += 1
        }

        override fun runScript(path: String): PythonHostEngineLaunch {
            engineFailure?.let { throw it }
            scriptPaths += path
            return ENGINE_LAUNCH.copy(path = path)
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
        val ENGINE_INFO = PythonHostEngineInfo(
            id = 17,
            engineName = "python",
            sourceName = "main",
            entryPoint = "main.py",
            project = true,
            startedAtMillis = 123456789L,
        )
        val ENGINE_LAUNCH = PythonHostEngineLaunch(
            id = 18,
            engineName = "org.autojs.autojs.script.JavaScriptSource.Engine",
            sourceName = "child",
            path = "child.js",
        )
    }
}
