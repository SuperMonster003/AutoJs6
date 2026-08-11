package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in public-Rhino smoke for deterministic plugin-backed `ai.chat(...)`.
 *
 * This starts one normal script through ScriptEngineService. Cleanup is scoped to the execution
 * created by this test; it never stops unrelated engines.
 */
class AiTextPluginPublicChatSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Test
    fun publicRhinoAiChatReturnsNormalizedResponseThroughExactFakeProvider() {
        val parameters = requireOptInParameters()
        requireFakeProviderPreflight()

        val workDir = File(
            context.cacheDir,
            "ai-public-chat-smoke-${UUID.randomUUID()}",
        ).apply {
            check(mkdirs()) { "Unable to create public AI chat smoke directory: $absolutePath" }
        }
        var primaryFailure: Throwable? = null
        try {
            runPublicChat(workDir, parameters)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailure = runCatching {
                check(workDir.deleteRecursively()) {
                    "Unable to remove public AI chat smoke directory: ${workDir.absolutePath}"
                }
            }.exceptionOrNull()
            cleanupFailure?.let { failure ->
                primaryFailure?.addSuppressed(failure) ?: throw failure
            }
        }
    }

    private fun runPublicChat(
        workDir: File,
        parameters: SmokeParameters,
    ) {
        val marker = File(workDir, MARKER_NAME)
        val sourceFile = File(workDir, SCRIPT_NAME).apply {
            writeText(publicChatSource(parameters), Charsets.UTF_8)
        }
        val source = JavaScriptFileSource(sourceFile)
        assertEquals(JavaScriptSource.ENGINE, source.engineName)

        val completed = CountDownLatch(1)
        val startedEngineClass = AtomicReference<Class<*>>()
        val scriptFailure = AtomicReference<Throwable?>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                startedEngineClass.set(execution.engine.javaClass)
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                scriptFailure.compareAndSet(null, error)
                completed.countDown()
            }
        }

        val execution = service.execute(
            source,
            listener,
            ExecutionConfig(workingDirectory = workDir.absolutePath),
        )
        var primaryFailure: Throwable? = null
        var ownedExecutionRemovedNaturally = false
        try {
            val hardTimeoutMillis = parameters.timeoutMillis + SCRIPT_TERMINAL_MARGIN_MILLIS
            if (!completed.await(hardTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw AssertionError(
                    "Timed out after ${hardTimeoutMillis}ms waiting for public AI plugin chat script",
                )
            }
            awaitOwnedExecutionRemoved(execution.id)
            ownedExecutionRemovedNaturally = true
            scriptFailure.get()?.let { error ->
                throw AssertionError("Public AI plugin chat script failed", error)
            }
            assertTrue(
                "Public AI plugin chat did not execute through the production Rhino engine",
                startedEngineClass.get()?.let(RhinoJavaScriptEngine::class.java::isAssignableFrom) == true,
            )
            assertTrue("Public AI plugin chat script did not publish its marker", marker.isFile)
            assertTrue(
                "Public AI plugin chat marker exceeded its bounded size",
                marker.length() in 1L..MAXIMUM_MARKER_BYTES,
            )
            assertSuccessfulMarker(JSONObject(marker.readText(Charsets.UTF_8)), parameters.prompt)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            if (!ownedExecutionRemovedNaturally) {
                var cleanupFailure: Throwable? = null
                cleanupFailure = captureCleanupFailure(cleanupFailure) {
                    service.engines.firstOrNull { engine -> engine.id == execution.id }?.forceStop()
                }
                cleanupFailure = captureCleanupFailure(cleanupFailure) {
                    awaitOwnedExecutionRemoved(execution.id)
                }
                cleanupFailure?.let { failure ->
                    primaryFailure?.addSuppressed(failure) ?: throw failure
                }
            }
        }
    }

    private fun assertSuccessfulMarker(result: JSONObject, expectedPrompt: String) {
        when (val status = result.optString("status")) {
            "resolved" -> Unit
            "rejected" -> throw AssertionError(
                "Public AI plugin chat rejected " +
                    "route=${result.optString("errorRoute")} " +
                    "code=${result.optString("errorCode")} " +
                    "name=${result.optString("errorName")} " +
                    "message=${result.optString("errorMessage")}",
            )
            else -> throw AssertionError("Public AI plugin chat wrote unknown marker status: $status")
        }
        assertEquals(1, result.getInt("terminalCount"))

        val responseKeys = result.getJSONArray("responseKeys")
        assertEquals(EXPECTED_RESPONSE_KEYS.size, responseKeys.length())
        assertEquals(
            EXPECTED_RESPONSE_KEYS,
            (0 until responseKeys.length()).map(responseKeys::getString).toSet(),
        )

        assertEquals("string", result.getString("textType"))
        assertEquals(expectedPrompt, result.getString("text"))
        assertEquals("string", result.getString("reasoningType"))
        assertEquals("", result.getString("reasoning"))
        assertTrue(result.getBoolean("toolCallsIsArray"))
        assertEquals(0, result.getInt("toolCallsLength"))
        assertTrue(result.getBoolean("usageIsNull"))
        assertTrue(result.getBoolean("finishReasonIsNull"))
        assertTrue(result.getBoolean("messageIsNull"))
        assertTrue(result.getBoolean("errorIsNull"))
        assertTrue(result.getBoolean("rawIsNull"))
        assertTrue(result.getBoolean("profileIsNull"))
        assertEquals("plugin", result.getString("route"))
        assertEquals(FAKE_PROVIDER_ID, result.getString("provider"))
        assertEquals(FAKE_MODEL_ID, result.getString("model"))
    }

    private fun requireOptInParameters(): SmokeParameters {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Public AI plugin chat smoke is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )

        val expectedSerialProperty = arguments.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue(
            "Pass the target device ro.serialno value through -e $ARG_DEVICE_SERIAL",
            expectedSerialProperty.isNotEmpty(),
        )
        val actualSerialProperty = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertTrue("The device did not expose a serial property", actualSerialProperty.isNotEmpty())
        assertEquals(
            "Instrumentation is running on a different device serial property",
            expectedSerialProperty,
            actualSerialProperty,
        )

        val timeoutArgument = arguments.getString(ARG_TIMEOUT_MILLIS)?.trim().orEmpty()
        val timeoutMillis = when {
            timeoutArgument.isEmpty() -> DEFAULT_ROUTE_TIMEOUT_MILLIS
            else -> timeoutArgument.toLongOrNull()
                ?: throw AssertionError("$ARG_TIMEOUT_MILLIS must be an integer")
        }
        assertTrue(
            "Public AI plugin chat timeout must be in " +
                "$MINIMUM_ROUTE_TIMEOUT_MILLIS..$MAXIMUM_ROUTE_TIMEOUT_MILLIS ms",
            timeoutMillis in MINIMUM_ROUTE_TIMEOUT_MILLIS..MAXIMUM_ROUTE_TIMEOUT_MILLIS,
        )
        return SmokeParameters(DEFAULT_PROMPT, timeoutMillis)
    }

    @Suppress("DEPRECATION")
    private fun requireFakeProviderPreflight() {
        assertTrue("Public AI plugin chat smoke requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Public AI plugin chat smoke must target the canonical host", HOST_PACKAGE, context.packageName)
        val serviceInfo = try {
            context.packageManager.getServiceInfo(
                FAKE_COMPONENT,
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The fake AI text provider is not installed: $FAKE_COMPONENT", error)
        }
        assertEquals(
            "The fake AI text provider and host must use the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PLUGIN_PACKAGE),
        )
        assertEquals(FAKE_PLUGIN_PACKAGE, serviceInfo.packageName)
        assertEquals(FAKE_SERVICE_CLASS, serviceInfo.name)
        assertTrue("The fake AI text provider application is disabled", serviceInfo.applicationInfo.enabled)
        assertTrue("The fake AI text provider service is disabled", serviceInfo.enabled)
        assertTrue("The fake AI text provider service is not exported", serviceInfo.exported)
        assertEquals(PLUGIN_PERMISSION, serviceInfo.permission)
        assertTrue(
            "Public AI chat conformance must use the test-only fake provider",
            serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_TEST_ONLY != 0,
        )
    }

    private fun publicChatSource(parameters: SmokeParameters): String =
        """
        "rhino";
        const markerPath = ${JSONObject.quote("./$MARKER_NAME")};
        let terminalCount = 0;
        function owns(value, key) {
          return Object.prototype.hasOwnProperty.call(value, key);
        }
        function writeMarker(value) {
          files.write(markerPath, JSON.stringify(value));
        }
        ai.chat(
          ${JSONObject.quote(parameters.prompt)},
          {
            plugin: {
              component: {
                packageName: ${JSONObject.quote(FAKE_PLUGIN_PACKAGE)},
                className: ${JSONObject.quote(FAKE_SERVICE_CLASS)}
              },
              providerId: ${JSONObject.quote(FAKE_PROVIDER_ID)},
              modelId: ${JSONObject.quote(FAKE_MODEL_ID)}
            },
            timeout: ${parameters.timeoutMillis}
          }
        ).then(result => {
          terminalCount += 1;
          const value = result || {};
          writeMarker({
            status: "resolved",
            terminalCount: terminalCount,
            responseKeys: Object.keys(value),
            textType: typeof value.text,
            text: String(value.text),
            reasoningType: typeof value.reasoning,
            reasoning: String(value.reasoning),
            toolCallsIsArray: Array.isArray(value.toolCalls),
            toolCallsLength: value.toolCalls == null ? -1 : Number(value.toolCalls.length),
            usageIsNull: owns(value, "usage") && value.usage === null,
            finishReasonIsNull: owns(value, "finishReason") && value.finishReason === null,
            messageIsNull: owns(value, "message") && value.message === null,
            errorIsNull: owns(value, "error") && value.error === null,
            rawIsNull: owns(value, "raw") && value.raw === null,
            profileIsNull: owns(value, "profile") && value.profile === null,
            route: String(value.route),
            provider: String(value.provider),
            model: String(value.model)
          });
        }, error => {
          terminalCount += 1;
          const value = error || {};
          writeMarker({
            status: "rejected",
            terminalCount: terminalCount,
            errorRoute: String(value.route || ""),
            errorCode: String(value.code || ""),
            errorName: String(value.name || ""),
            errorMessage: String(value.message || "")
          });
        });
        """.trimIndent()

    private fun awaitOwnedExecutionRemoved(executionId: Int) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId } &&
                service.scriptExecutions.none { execution -> execution.id == executionId }
            ) {
                return
            }
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("Owned engine $executionId remained registered after public AI chat smoke")
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }

    private fun captureCleanupFailure(
        current: Throwable?,
        action: () -> Unit,
    ): Throwable? {
        val next = runCatching(action).exceptionOrNull() ?: return current
        return current?.also { it.addSuppressed(next) } ?: next
    }

    private data class SmokeParameters(
        val prompt: String,
        val timeoutMillis: Long,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.aiText.publicChatSmoke.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.aiText.publicChatSmoke.serial"
        const val ARG_TIMEOUT_MILLIS = "autojs.aiText.publicChatSmoke.timeoutMillis"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val FAKE_PLUGIN_PACKAGE = "org.autojs.plugin.ai.text.fake"
        const val FAKE_SERVICE_CLASS =
            "org.autojs.plugin.ai.text.fake.service.FakeAiTextProviderService"
        const val FAKE_PROVIDER_ID = "fake.local"
        const val FAKE_MODEL_ID = "fake.echo"
        const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

        const val SCRIPT_NAME = "public-ai-plugin-chat.js"
        const val MARKER_NAME = "public-ai-plugin-chat-result.json"
        const val DEFAULT_PROMPT = "Deterministic public plugin chat echo: 你好，AutoJs6."
        const val DEFAULT_ROUTE_TIMEOUT_MILLIS = 120_000L
        const val MINIMUM_ROUTE_TIMEOUT_MILLIS = 1_000L
        const val MAXIMUM_ROUTE_TIMEOUT_MILLIS = 600_000L
        const val SCRIPT_TERMINAL_MARGIN_MILLIS = 30_000L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 5_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 25L
        const val MAXIMUM_MARKER_BYTES = 64L * 1024L

        val EXPECTED_RESPONSE_KEYS = setOf(
            "text",
            "reasoning",
            "toolCalls",
            "usage",
            "finishReason",
            "message",
            "error",
            "raw",
            "profile",
            "route",
            "provider",
            "model",
        )

        val FAKE_COMPONENT = ComponentName(FAKE_PLUGIN_PACKAGE, FAKE_SERVICE_CLASS)
    }
}
