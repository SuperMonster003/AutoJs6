package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
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
 * Opt-in device smoke for the public Rhino multi-message `ai.ask(..., { plugin: ... })` route.
 *
 * This deliberately starts a normal script through [org.autojs.autojs.engine.ScriptEngineService]
 * instead of invoking an AI coordinator or Binder transport from the test process. The exact real
 * provider is selected in JavaScript, and the Promise handlers publish one bounded marker in the
 * script working directory. Ordinary connected tests skip before touching the script service.
 */
class OnDeviceAiPluginPublicAskSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Test
    fun publicRhinoAiAskWithSystemPromptCompletesThroughExactRealPlugin() {
        val parameters = requireOptInParameters()
        requireRealProviderPreflight()

        awaitNoRegisteredEngines("before public AI plugin ask smoke")

        val workDir = File(
            context.cacheDir,
            "ai-public-ask-smoke-${UUID.randomUUID()}",
        ).apply {
            check(mkdirs()) { "Unable to create public AI plugin ask smoke directory: $absolutePath" }
        }
        var primaryFailure: Throwable? = null
        try {
            runPublicAsk(workDir, parameters)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            cleanupFailure = captureCleanupFailure(cleanupFailure) {
                awaitNoRegisteredEngines("after public AI plugin ask smoke")
            }
            cleanupFailure = captureCleanupFailure(cleanupFailure) {
                check(workDir.deleteRecursively()) {
                    "Unable to remove public AI plugin ask smoke directory: ${workDir.absolutePath}"
                }
            }
            cleanupFailure?.let { failure ->
                primaryFailure?.addSuppressed(failure) ?: throw failure
            }
        }
    }

    private fun runPublicAsk(
        workDir: File,
        parameters: SmokeParameters,
    ) {
        val marker = File(workDir, MARKER_NAME)
        val sourceFile = File(workDir, SCRIPT_NAME).apply {
            writeText(publicAskSource(parameters), Charsets.UTF_8)
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
        var engineRemovedNaturally = false
        try {
            val hardTimeoutMillis = parameters.timeoutMillis + SCRIPT_TERMINAL_MARGIN_MILLIS
            if (!completed.await(hardTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw AssertionError(
                    "Timed out after ${hardTimeoutMillis}ms waiting for public AI plugin ask script",
                )
            }
            awaitEngineRemoved(execution.id)
            engineRemovedNaturally = true
            scriptFailure.get()?.let { error ->
                throw AssertionError("Public AI plugin ask script failed", error)
            }
            assertTrue(
                "Public AI plugin ask did not execute through the production Rhino engine",
                startedEngineClass.get()?.let(RhinoJavaScriptEngine::class.java::isAssignableFrom) == true,
            )
            assertTrue("Public AI plugin ask script did not publish its marker", marker.isFile)
            assertTrue(
                "Public AI plugin ask marker exceeded its bounded size",
                marker.length() in 1L..MAXIMUM_MARKER_BYTES,
            )

            val result = JSONObject(marker.readText(Charsets.UTF_8))
            when (val status = result.optString("status")) {
                "resolved" -> {
                    assertEquals("string", result.optString("type"))
                    assertTrue(
                        "Public AI plugin ask completed with empty text",
                        result.optString("text").isNotBlank(),
                    )
                }
                "rejected" -> throw AssertionError(
                    "Public AI plugin ask rejected " +
                        "route=${result.optString("route")} " +
                        "code=${result.optString("code")} " +
                        "name=${result.optString("name")} " +
                        "message=${result.optString("message")}",
                )
                else -> throw AssertionError("Public AI plugin ask wrote unknown marker status: $status")
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            if (!engineRemovedNaturally) {
                var cleanupFailure: Throwable? = null
                cleanupFailure = captureCleanupFailure(cleanupFailure) {
                    checkNotNull(execution.engine) {
                        "Public AI plugin ask execution never created its owned engine"
                    }.forceStop()
                }
                cleanupFailure = captureCleanupFailure(cleanupFailure) {
                    awaitEngineRemoved(execution.id)
                }
                cleanupFailure?.let { failure ->
                    primaryFailure?.addSuppressed(failure) ?: throw failure
                }
            }
        }
    }

    private fun requireOptInParameters(): SmokeParameters {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Public AI plugin ask smoke is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )

        val expectedSerial = arguments.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue("Pass the explicit adb target serial through -e $ARG_DEVICE_SERIAL", expectedSerial.isNotEmpty())
        val actualSerial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertTrue("The device did not expose a serial to instrumentation", actualSerial.isNotEmpty())
        assertEquals("Instrumentation is running on a different device", expectedSerial, actualSerial)

        val modelId = arguments.getString(ARG_MODEL_ID).orEmpty().trim()
        assertTrue(
            "Pass the exact imported model ID through -e $ARG_MODEL_ID",
            REAL_MODEL_ID.matches(modelId),
        )

        val prompt = arguments.getString(ARG_PROMPT)?.takeIf(String::isNotEmpty)
            ?: DEFAULT_PROMPT
        assertTrue(
            "Public AI plugin ask prompt must contain 1..$MAXIMUM_PROMPT_BYTES UTF-8 bytes",
            prompt.toByteArray(Charsets.UTF_8).size in 1..MAXIMUM_PROMPT_BYTES,
        )

        val timeoutArgument = arguments.getString(ARG_TIMEOUT_MILLIS)?.trim().orEmpty()
        val timeoutMillis = when {
            timeoutArgument.isEmpty() -> DEFAULT_ROUTE_TIMEOUT_MILLIS
            else -> timeoutArgument.toLongOrNull()
                ?: throw AssertionError("$ARG_TIMEOUT_MILLIS must be an integer")
        }
        assertTrue(
            "Public AI plugin ask timeout must be in $MINIMUM_ROUTE_TIMEOUT_MILLIS..$MAXIMUM_ROUTE_TIMEOUT_MILLIS ms",
            timeoutMillis in MINIMUM_ROUTE_TIMEOUT_MILLIS..MAXIMUM_ROUTE_TIMEOUT_MILLIS,
        )
        return SmokeParameters(modelId, prompt, timeoutMillis)
    }

    @Suppress("DEPRECATION")
    private fun requireRealProviderPreflight() {
        assertTrue("Public AI plugin ask smoke requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Public AI plugin ask smoke must target the canonical host", HOST_PACKAGE, context.packageName)
        val serviceInfo = try {
            context.packageManager.getServiceInfo(
                REAL_COMPONENT,
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real on-device AI provider is not installed: $REAL_COMPONENT", error)
        }
        assertEquals(
            "The real on-device AI plugin and host must use the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        assertEquals(REAL_PLUGIN_PACKAGE, serviceInfo.packageName)
        assertEquals(REAL_SERVICE_CLASS, serviceInfo.name)
        assertTrue("The real on-device AI provider application is disabled", serviceInfo.applicationInfo.enabled)
        assertTrue("The real on-device AI provider service is disabled", serviceInfo.enabled)
        assertTrue("The real on-device AI provider service is not exported", serviceInfo.exported)
        assertEquals(PLUGIN_PERMISSION, serviceInfo.permission)
    }

    private fun publicAskSource(parameters: SmokeParameters): String =
        """
        "rhino";
        const markerPath = ${JSONObject.quote("./$MARKER_NAME")};
        ai.ask(
          [
            { role: "system", content: ${JSONObject.quote(DEFAULT_SYSTEM_PROMPT)} },
            { role: "user", content: ${JSONObject.quote(parameters.prompt)} }
          ],
          {
            plugin: {
              component: {
                packageName: ${JSONObject.quote(REAL_PLUGIN_PACKAGE)},
                className: ${JSONObject.quote(REAL_SERVICE_CLASS)}
              },
              providerId: ${JSONObject.quote(REAL_PROVIDER_ID)},
              modelId: ${JSONObject.quote(parameters.modelId)}
            },
            timeout: ${parameters.timeoutMillis},
            temperature: 0.7,
            topK: 40,
            topP: 0.9,
            maxTokens: 32
          }
        ).then(
          text => files.write(markerPath, JSON.stringify({
            status: "resolved",
            type: typeof text,
            text: String(text)
          })),
          error => {
            const safe = error || {};
            files.write(markerPath, JSON.stringify({
              status: "rejected",
              route: String(safe.route || ""),
              code: String(safe.code || ""),
              name: String(safe.name || ""),
              message: String(safe.message || "")
            }));
          }
        );
        """.trimIndent()

    private fun awaitEngineRemoved(executionId: Int) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId } &&
                service.scriptExecutions.none { execution -> execution.id == executionId }
            ) {
                return
            }
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("Engine $executionId remained registered after public AI plugin ask")
    }

    private fun awaitNoRegisteredEngines(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.isEmpty() && service.scriptExecutions.isEmpty()) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError(
            "$label left engines=${service.engines.map { it.id }} executions=${service.scriptExecutions}",
        )
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
        val modelId: String,
        val prompt: String,
        val timeoutMillis: Long,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.onDeviceAi.publicAskSmoke.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.onDeviceAi.publicAskSmoke.serial"
        const val ARG_MODEL_ID = "autojs.onDeviceAi.publicAskSmoke.modelId"
        const val ARG_PROMPT = "autojs.onDeviceAi.publicAskSmoke.prompt"
        const val ARG_TIMEOUT_MILLIS = "autojs.onDeviceAi.publicAskSmoke.timeoutMillis"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.ondeviceai"
        const val REAL_SERVICE_CLASS =
            "io.github.supermonster003.autojs6.plugin.ondeviceai.provider.OnDeviceAiProviderService"
        const val REAL_PROVIDER_ID = "autojs6.on-device-ai"
        const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

        const val SCRIPT_NAME = "public-ai-plugin-ask.js"
        const val MARKER_NAME = "public-ai-plugin-ask-result.json"
        const val DEFAULT_PROMPT = "Reply with exactly OK."
        const val DEFAULT_SYSTEM_PROMPT = "Answer the user's request directly and concisely."
        const val DEFAULT_ROUTE_TIMEOUT_MILLIS = 180_000L
        const val MINIMUM_ROUTE_TIMEOUT_MILLIS = 1_000L
        const val MAXIMUM_ROUTE_TIMEOUT_MILLIS = 600_000L
        const val SCRIPT_TERMINAL_MARGIN_MILLIS = 30_000L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 5_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 25L
        const val MAXIMUM_PROMPT_BYTES = 64 * 1024
        const val MAXIMUM_MARKER_BYTES = 128L * 1024L

        val REAL_COMPONENT = ComponentName(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)
        val REAL_MODEL_ID = Regex("^litertlm\\.[0-9a-f]{32}$")
    }
}
