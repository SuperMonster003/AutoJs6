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
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
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
 * Opt-in public-Rhino smoke for the deterministic cross-process fake streaming provider.
 *
 * This starts a normal script through ScriptEngineService. It does not invoke a coordinator from
 * the instrumentation process and cleanup is limited to the execution created by this test.
 */
class OnDeviceAiPluginPublicStreamSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Test
    fun publicRhinoAiStreamCompletesThroughExactFakeProvider() {
        val parameters = requireOptInParameters()
        requireFakeProviderPreflight()

        val workDir = File(
            context.cacheDir,
            "ai-public-stream-smoke-${UUID.randomUUID()}",
        ).apply {
            check(mkdirs()) { "Unable to create public AI stream smoke directory: $absolutePath" }
        }
        var primaryFailure: Throwable? = null
        try {
            runPublicStream(workDir, parameters)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailure = runCatching {
                check(workDir.deleteRecursively()) {
                    "Unable to remove public AI stream smoke directory: ${workDir.absolutePath}"
                }
            }.exceptionOrNull()
            cleanupFailure?.let { failure ->
                primaryFailure?.addSuppressed(failure) ?: throw failure
            }
        }
    }

    private fun runPublicStream(
        workDir: File,
        parameters: SmokeParameters,
    ) {
        val marker = File(workDir, MARKER_NAME)
        val sourceFile = File(workDir, SCRIPT_NAME).apply {
            writeText(publicStreamSource(parameters), Charsets.UTF_8)
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
                    "Timed out after ${hardTimeoutMillis}ms waiting for public AI plugin stream script",
                )
            }
            awaitOwnedExecutionRemoved(execution.id)
            ownedExecutionRemovedNaturally = true
            scriptFailure.get()?.let { error ->
                throw AssertionError("Public AI plugin stream script failed", error)
            }
            assertTrue(
                "Public AI plugin stream did not execute through the production Rhino engine",
                startedEngineClass.get()?.let(RhinoJavaScriptEngine::class.java::isAssignableFrom) == true,
            )
            assertTrue("Public AI plugin stream script did not publish its marker", marker.isFile)
            assertTrue(
                "Public AI plugin stream marker exceeded its bounded size",
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
            "done" -> Unit
            "error" -> throw AssertionError(
                "Public AI plugin stream failed " +
                    "route=${result.optString("errorRoute")} " +
                    "code=${result.optString("errorCode")} " +
                    "message=${result.optString("errorMessage")}",
            )
            "cancelled" -> throw AssertionError("Public AI plugin stream was unexpectedly cancelled")
            else -> throw AssertionError("Public AI plugin stream wrote unknown marker status: $status")
        }
        assertEquals(1, result.getInt("openCount"))
        assertEquals(1, result.getInt("terminalCount"))
        assertEquals(1, result.getInt("doneCount"))
        assertEquals(0, result.getInt("errorCount"))
        assertEquals(0, result.getInt("cancelledCount"))
        assertTrue(
            "Public AI plugin stream did not cross the initial chunk-credit window",
            result.getInt("chunkCount") > OnDeviceAiLimits.INITIAL_CHUNK_CREDITS,
        )
        assertEquals(result.getInt("chunkCount"), result.getInt("deltaCount"))
        assertEquals(0, result.getInt("usageCount"))
        assertEquals(0, result.getInt("toolCallCount"))
        assertEquals(0, result.getInt("invalidChunkCount"))
        assertEquals(expectedPrompt, result.getString("chunkText"))
        assertEquals(expectedPrompt, result.getString("deltaText"))
        assertEquals(expectedPrompt, result.getString("doneText"))
        assertEquals("plugin", result.getString("openRoute"))
        assertEquals(FAKE_PROVIDER_ID, result.getString("openProvider"))
        assertEquals(FAKE_MODEL_ID, result.getString("openModel"))
        assertEquals("plugin", result.getString("doneRoute"))
        assertEquals(FAKE_PROVIDER_ID, result.getString("doneProvider"))
        assertEquals(FAKE_MODEL_ID, result.getString("doneModel"))
        assertEquals("done", result.getString("streamState"))
        assertTrue(result.getBoolean("streamIsDone"))
    }

    private fun requireOptInParameters(): SmokeParameters {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Public AI plugin stream smoke is opt-in; pass -e $ARG_ENABLED true",
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
            "Public AI plugin stream timeout must be in " +
                "$MINIMUM_ROUTE_TIMEOUT_MILLIS..$MAXIMUM_ROUTE_TIMEOUT_MILLIS ms",
            timeoutMillis in MINIMUM_ROUTE_TIMEOUT_MILLIS..MAXIMUM_ROUTE_TIMEOUT_MILLIS,
        )
        val prompt = DEFAULT_PROMPT
        assertTrue(
            "Deterministic fake stream prompt must exceed the initial credit window",
            prompt.toByteArray(Charsets.UTF_8).size >
                OnDeviceAiLimits.INITIAL_CHUNK_CREDITS * FAKE_CHUNK_BYTES,
        )
        return SmokeParameters(prompt, timeoutMillis)
    }

    @Suppress("DEPRECATION")
    private fun requireFakeProviderPreflight() {
        assertTrue("Public AI plugin stream smoke requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Public AI plugin stream smoke must target the canonical host", HOST_PACKAGE, context.packageName)
        val serviceInfo = try {
            context.packageManager.getServiceInfo(
                FAKE_COMPONENT,
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The fake on-device AI provider is not installed: $FAKE_COMPONENT", error)
        }
        assertEquals(
            "The fake on-device AI provider and host must use the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PLUGIN_PACKAGE),
        )
        assertEquals(FAKE_PLUGIN_PACKAGE, serviceInfo.packageName)
        assertEquals(FAKE_SERVICE_CLASS, serviceInfo.name)
        assertTrue("The fake on-device AI provider application is disabled", serviceInfo.applicationInfo.enabled)
        assertTrue("The fake on-device AI provider service is disabled", serviceInfo.enabled)
        assertTrue("The fake on-device AI provider service is not exported", serviceInfo.exported)
        assertEquals(PLUGIN_PERMISSION, serviceInfo.permission)
        assertTrue(
            "Public AI stream conformance must use the test-only fake provider",
            serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_TEST_ONLY != 0,
        )
    }

    private fun publicStreamSource(parameters: SmokeParameters): String =
        """
        "rhino";
        const markerPath = ${JSONObject.quote("./$MARKER_NAME")};
        let openCount = 0;
        let chunkCount = 0;
        let deltaCount = 0;
        let usageCount = 0;
        let toolCallCount = 0;
        let terminalCount = 0;
        let doneCount = 0;
        let errorCount = 0;
        let cancelledCount = 0;
        let invalidChunkCount = 0;
        let chunkText = "";
        let deltaText = "";
        let openRoute = "";
        let openProvider = "";
        let openModel = "";
        const stream = ai.stream(
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
        );
        function writeMarker(status, terminal) {
          const value = terminal || {};
          files.write(markerPath, JSON.stringify({
            status: status,
            openCount: openCount,
            chunkCount: chunkCount,
            deltaCount: deltaCount,
            usageCount: usageCount,
            toolCallCount: toolCallCount,
            terminalCount: terminalCount,
            doneCount: doneCount,
            errorCount: errorCount,
            cancelledCount: cancelledCount,
            invalidChunkCount: invalidChunkCount,
            chunkText: chunkText,
            deltaText: deltaText,
            doneText: String(value.text || ""),
            openRoute: openRoute,
            openProvider: openProvider,
            openModel: openModel,
            doneRoute: String(value.route || ""),
            doneProvider: String(value.provider || ""),
            doneModel: String(value.model || ""),
            errorRoute: String(value.route || ""),
            errorCode: String(value.code || ""),
            errorMessage: String(value.message || ""),
            streamState: String(stream.state),
            streamIsDone: Boolean(stream.isDone)
          }));
        }
        stream.on("open", metadata => {
          const value = metadata || {};
          openCount += 1;
          openRoute = String(value.route || "");
          openProvider = String(value.provider || "");
          openModel = String(value.model || "");
        });
        stream.on("chunk", chunk => {
          const value = chunk || {};
          chunkCount += 1;
          chunkText += String(value.text || "");
          if (value.done !== false || value.finishReason != null) invalidChunkCount += 1;
        });
        stream.on("delta", text => {
          deltaCount += 1;
          deltaText += String(text);
        });
        stream.on("usage", () => { usageCount += 1; });
        stream.on("toolCall", () => { toolCallCount += 1; });
        stream.once("done", result => {
          terminalCount += 1;
          doneCount += 1;
          writeMarker("done", result);
        });
        stream.once("error", error => {
          terminalCount += 1;
          errorCount += 1;
          writeMarker("error", error);
        });
        stream.once("cancelled", () => {
          terminalCount += 1;
          cancelledCount += 1;
          writeMarker("cancelled", {});
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
        throw AssertionError("Owned engine $executionId remained registered after public AI stream smoke")
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
        const val ARG_ENABLED = "autojs.onDeviceAi.publicStreamSmoke.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.onDeviceAi.publicStreamSmoke.serial"
        const val ARG_TIMEOUT_MILLIS = "autojs.onDeviceAi.publicStreamSmoke.timeoutMillis"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val FAKE_PLUGIN_PACKAGE = "org.autojs.plugin.ondeviceai.fake"
        const val FAKE_SERVICE_CLASS =
            "org.autojs.plugin.ondeviceai.fake.service.FakeOnDeviceAiProviderService"
        const val FAKE_PROVIDER_ID = "fake.local"
        const val FAKE_MODEL_ID = "fake.stream"
        const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

        const val SCRIPT_NAME = "public-ai-plugin-stream.js"
        const val MARKER_NAME = "public-ai-plugin-stream-result.json"
        const val DEFAULT_PROMPT =
            "UTF-8 public stream credit window: 你好，世界。Καλημέρα κόσμε. مرحبًا بالعالم. " +
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" +
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val DEFAULT_ROUTE_TIMEOUT_MILLIS = 120_000L
        const val MINIMUM_ROUTE_TIMEOUT_MILLIS = 1_000L
        const val MAXIMUM_ROUTE_TIMEOUT_MILLIS = 600_000L
        const val SCRIPT_TERMINAL_MARGIN_MILLIS = 30_000L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 5_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 25L
        const val MAXIMUM_MARKER_BYTES = 256L * 1024L
        const val FAKE_CHUNK_BYTES = 12

        val FAKE_COMPONENT = ComponentName(FAKE_PLUGIN_PACKAGE, FAKE_SERVICE_CLASS)
    }
}
