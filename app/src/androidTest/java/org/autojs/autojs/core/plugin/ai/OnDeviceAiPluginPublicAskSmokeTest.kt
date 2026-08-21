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
import org.autojs.plugin.ondeviceai.api.OnDeviceAiBackendProfile
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
 * Opt-in device smoke for native structured JSON through the public Rhino plugin route.
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
    fun publicRhinoAiChatReturnsNativeSchemaConstrainedJsonThroughExactRealPlugin() {
        val parameters = requireOptInParameters()
        requireRealProviderPreflight()

        awaitNoRegisteredEngines("before public AI plugin chat smoke")

        val workDir = File(
            context.cacheDir,
            "ai-public-chat-smoke-${UUID.randomUUID()}",
        ).apply {
            check(mkdirs()) { "Unable to create public AI plugin chat smoke directory: $absolutePath" }
        }
        var primaryFailure: Throwable? = null
        try {
            runPublicChat(workDir, parameters)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            cleanupFailure = captureCleanupFailure(cleanupFailure) {
                awaitNoRegisteredEngines("after public AI plugin chat smoke")
            }
            cleanupFailure = captureCleanupFailure(cleanupFailure) {
                check(workDir.deleteRecursively()) {
                    "Unable to remove public AI plugin chat smoke directory: ${workDir.absolutePath}"
                }
            }
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
        var engineRemovedNaturally = false
        try {
            val hardTimeoutMillis = parameters.timeoutMillis + SCRIPT_TERMINAL_MARGIN_MILLIS
            if (!completed.await(hardTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw AssertionError(
                    "Timed out after ${hardTimeoutMillis}ms waiting for public AI plugin chat script",
                )
            }
            awaitEngineRemoved(execution.id)
            engineRemovedNaturally = true
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

            val result = JSONObject(marker.readText(Charsets.UTF_8))
            when (val status = result.optString("status")) {
                "resolved" -> {
                    assertEquals("object", result.optString("type"))
                    assertTrue(
                        "Public AI plugin chat completed with empty text",
                        result.optString("text").isNotBlank(),
                    )
                    listOf("inputTokens", "outputTokens", "totalTokens", "durationMillis").forEach { key ->
                        assertTrue("Public AI plugin chat omitted $key", result.has(key) && !result.isNull(key))
                    }
                    val inputTokens = result.getLong("inputTokens")
                    val outputTokens = result.getLong("outputTokens")
                    val totalTokens = result.getLong("totalTokens")
                    assertTrue("Public AI plugin chat reported no input tokens", inputTokens > 0L)
                    assertTrue("Public AI plugin chat reported no output tokens", outputTokens > 0L)
                    assertEquals(inputTokens + outputTokens, totalTokens)
                    assertTrue(
                        "Public AI plugin chat reported a negative generation duration",
                        result.getLong("durationMillis") >= 0L,
                    )
                    assertEquals("object", result.optString("jsonType"))
                    assertEquals("string", result.optString("answerType"))
                    assertEquals("boolean", result.optString("okType"))
                    assertEquals(parameters.backend, result.optString("backendId"))
                    assertEquals("available", result.optString("backendAvailability"))
                    assertEquals("unavailable", result.optString("npuAvailability"))
                    assertEquals("npu-runtime-not-packaged", result.optString("npuUnavailableReason"))
                    assertEquals("MODEL_CAPABILITY_MISMATCH", result.optString("npuRejectionCode"))
                    val parsed = JSONObject(result.getString("text"))
                    assertTrue("Structured JSON omitted answer", parsed.has("answer"))
                    assertTrue("Structured JSON omitted ok", parsed.has("ok"))
                    assertTrue("Structured JSON answer is not a string", parsed.get("answer") is String)
                    assertTrue("Structured JSON ok is not a boolean", parsed.get("ok") is Boolean)
                }
                "rejected" -> throw AssertionError(
                    "Public AI plugin chat rejected " +
                        "route=${result.optString("route")} " +
                        "code=${result.optString("code")} " +
                        "name=${result.optString("name")} " +
                        "message=${result.optString("message")}",
                )
                else -> throw AssertionError("Public AI plugin chat wrote unknown marker status: $status")
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            if (!engineRemovedNaturally) {
                var cleanupFailure: Throwable? = null
                cleanupFailure = captureCleanupFailure(cleanupFailure) {
                    checkNotNull(execution.engine) {
                        "Public AI plugin chat execution never created its owned engine"
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
            "Public AI plugin chat smoke is opt-in; pass -e $ARG_ENABLED true",
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
            "Public AI plugin chat prompt must contain 1..$MAXIMUM_PROMPT_BYTES UTF-8 bytes",
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
        val backend = arguments.getString(ARG_BACKEND)?.trim()?.lowercase()
            ?.takeIf(String::isNotEmpty)
            ?: OnDeviceAiBackendProfile.CPU
        assertTrue(
            "$ARG_BACKEND must be one of ${OnDeviceAiBackendProfile.VALUES.sorted().joinToString()}",
            backend in OnDeviceAiBackendProfile.VALUES,
        )
        return SmokeParameters(modelId, prompt, timeoutMillis, backend)
    }

    @Suppress("DEPRECATION")
    private fun requireRealProviderPreflight() {
        assertTrue("Public AI plugin chat smoke requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Public AI plugin chat smoke must target the canonical host", HOST_PACKAGE, context.packageName)
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

    private fun publicChatSource(parameters: SmokeParameters): String =
        """
        "rhino";
        const markerPath = ${JSONObject.quote("./$MARKER_NAME")};
        const plugin = {
          component: {
            packageName: ${JSONObject.quote(REAL_PLUGIN_PACKAGE)},
            className: ${JSONObject.quote(REAL_SERVICE_CLASS)}
          },
          providerId: ${JSONObject.quote(REAL_PROVIDER_ID)},
          modelId: ${JSONObject.quote(parameters.modelId)}
        };
        let selectedBackendProfile;
        let npuBackendProfile;
        let npuRejectionCode;
        ai.models({ plugin: plugin, timeout: ${parameters.timeoutMillis} }).then(
          models => {
            const model = models.find(item => item.modelId === ${JSONObject.quote(parameters.modelId)});
            if (!model) throw new Error("Selected model was absent from ai.models()");
            const profiles = Array.isArray(model.backendProfiles) ? model.backendProfiles : [];
            selectedBackendProfile = profiles.find(item => item.id === ${JSONObject.quote(parameters.backend)});
            npuBackendProfile = profiles.find(item => item.id === "npu");
            if (!selectedBackendProfile || selectedBackendProfile.availability !== "available") {
              throw new Error("Selected backend was not reported available by ai.models()");
            }
            if (!npuBackendProfile || npuBackendProfile.availability !== "unavailable") {
              throw new Error("NPU backend was not reported unavailable by ai.models()");
            }
            return ai.chat("This NPU request must be rejected before generation.", {
              plugin: plugin,
              timeout: ${parameters.timeoutMillis},
              maxTokens: 1,
              backend: "npu"
            }).then(
              () => {
                throw new Error("Unavailable NPU backend unexpectedly generated a response");
              },
              error => {
                npuRejectionCode = String(error && error.code || "");
                if (npuRejectionCode !== "MODEL_CAPABILITY_MISMATCH") {
                  throw new Error("Unexpected NPU rejection code: " + npuRejectionCode);
                }
                return ai.chat(
                  [
                    { role: "system", content: ${JSONObject.quote(DEFAULT_SYSTEM_PROMPT)} },
                    { role: "user", content: ${JSONObject.quote(parameters.prompt)} }
                  ],
                  {
                    plugin: plugin,
                    timeout: ${parameters.timeoutMillis},
                    temperature: 0.7,
                    topK: 40,
                    topP: 0.9,
                    maxTokens: 64,
                    backend: ${JSONObject.quote(parameters.backend)},
                    structuredJson: true,
                    responseSchema: {
                      type: "object",
                      properties: {
                        answer: { type: "string" },
                        ok: { type: "boolean" }
                      },
                      required: [ "answer", "ok" ]
                    }
                  }
                );
              }
            );
          }
        ).then(
          response => {
            const usage = response && response.usage || {};
            const rawUsage = usage.raw || {};
            const text = String(response && response.text || "");
            const parsed = JSON.parse(text);
            files.write(markerPath, JSON.stringify({
              status: "resolved",
              type: typeof response,
              text: text,
              jsonType: parsed !== null && !Array.isArray(parsed) ? typeof parsed : "invalid",
              answerType: typeof parsed.answer,
              okType: typeof parsed.ok,
              backendId: selectedBackendProfile.id,
              backendAvailability: selectedBackendProfile.availability,
              npuAvailability: npuBackendProfile.availability,
              npuUnavailableReason: npuBackendProfile.unavailableReason,
              npuRejectionCode: npuRejectionCode,
              inputTokens: usage.inputTokens,
              outputTokens: usage.outputTokens,
              totalTokens: usage.totalTokens,
              durationMillis: rawUsage.durationMillis
            }));
          },
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
        val backend: String,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.onDeviceAi.publicAskSmoke.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.onDeviceAi.publicAskSmoke.serial"
        const val ARG_MODEL_ID = "autojs.onDeviceAi.publicAskSmoke.modelId"
        const val ARG_PROMPT = "autojs.onDeviceAi.publicAskSmoke.prompt"
        const val ARG_TIMEOUT_MILLIS = "autojs.onDeviceAi.publicAskSmoke.timeoutMillis"
        const val ARG_BACKEND = "autojs.onDeviceAi.publicAskSmoke.backend"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.ondeviceai"
        const val REAL_SERVICE_CLASS =
            "io.github.supermonster003.autojs6.plugin.ondeviceai.provider.OnDeviceAiProviderService"
        const val REAL_PROVIDER_ID = "autojs6.on-device-ai"
        const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

        const val SCRIPT_NAME = "public-ai-plugin-chat.js"
        const val MARKER_NAME = "public-ai-plugin-chat-result.json"
        const val DEFAULT_PROMPT =
            "Return an object whose answer field is exactly OK and whose ok field is true."
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
