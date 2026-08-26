package org.autojs.autojs.core.plugin.jvm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.jvmsource.api.JvmDexRuntimeProfile
import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmObservationCacheOutcome
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.autojs.plugin.jvmsource.api.JvmSourceNegotiation
import org.autojs.plugin.jvmsource.api.JvmSourceObservation
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Optional on-device JVM source regression. It must never report a skipped pass. */
class JvmSourceR1EndToEndInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Before
    fun stopExistingScripts() {
        service.stopAll()
        awaitNoExecutions("before R1 selector")
    }

    @After
    fun stopSelectorScripts() {
        service.stopAll()
        awaitNoExecutions("after R1 selector")
    }

    @Test
    fun explicitJavaMainRunsThroughPinnedProductionProvider() {
        emitCanonicalRuntimeRecord(runCanonicalProductionPipeline())
    }

    @Test
    fun explicitJavaArgumentsRoundTripThroughPinnedProductionProvider() {
        assertTrue(
            "M9-2 JVM argument selector requires -Pautojs.jvmSource.experimental=true",
            BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
        )
        requireProductionProviderBoundary()

        val priorSelection = JvmSourceProviderSelection.getExact(context)
        val directory = File(context.cacheDir, "jvm-source-m9-args-${UUID.randomUUID()}").apply {
            assertTrue("Unable to create the M9-2 source directory", mkdir())
        }.canonicalFile
        val sourceBytes = argumentJavaSource().toByteArray(Charsets.UTF_8)
        val sourceFile = File(directory, "Main.java").apply { writeBytes(sourceBytes) }
        val config = ExecutionConfig(workingDirectory = directory.absolutePath).apply {
            setArgument("tags", listOf("java", "m9"))
            setArgument("nested", linkedMapOf<String, Any?>("nullable" to null, "count" to 3))
            setArgument("name", "AutoJs6")
            setArgument("enabled", true)
        }

        try {
            JvmSourceProviderSelection.setExact(context, PRODUCTION_PROVIDER)
            val outcome = executeAndAwait { listener ->
                JvmSourceExplicitRunner.runJvmSourceExplicit(
                    service = service,
                    file = sourceFile,
                    listener = listener,
                    config = config,
                )
            }
            outcome.error?.let { failure ->
                val handshakeFailure = diagnoseProviderHandshakeAfterExecutionFailure()
                if (handshakeFailure != null) {
                    throw AssertionError("M9-2 provider handshake diagnosis failed", handshakeFailure)
                }
                val directHostFailure = diagnoseDirectHostAfterExecutionFailure(sourceFile, SCRIPT_ARGS_JSON)
                throw AssertionError(
                    "M9-2 argument execution failed after a verified provider handshake",
                    directHostFailure ?: failure,
                )
            }
            val result = outcome.result as? RemoteJvmScriptResult
                ?: throw AssertionError("M9-2 result did not use RemoteJvmScriptResult")

            assertEquals(EXPECTED_SCRIPT_ARGS_RESULT_JSON, result.resultJson)
            assertEquals(sha256(EXPECTED_SCRIPT_ARGS_RESULT_JSON.toByteArray()), result.resultSha256)
            assertEquals(JvmSourceContract.ENTRY_API_VERSION, result.entryApiVersion)
            assertEquals(PRODUCTION_PROVIDER.flattenToString(), result.providerComponent)
            assertEquals(0, result.allowlistedBridgeOperationCount)
            assertEquals(0, result.rejectedBridgeOperationCount)
            assertEquals(0, result.internalBridgeCallCount)
            assertTrue(result.hostBridgeCallerPids.isEmpty())
            assertEquals(sourceBytes.size.toLong(), result.sourceSizeBytes)
            assertEquals(sha256(sourceBytes), result.sourceSha256)

            emitArgumentRuntimeRecord(result)
        } finally {
            JvmSourceProviderSelection.setExact(context, priorSelection)
            directory.deleteRecursively()
        }
    }

    @Test
    fun runtimeExceptionShowsOnlyTheSafeClassAndRequestedSourceLine() {
        assertTrue(
            "M9-3 runtime diagnostic selector requires -Pautojs.jvmSource.experimental=true",
            BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
        )
        requireProductionProviderBoundary()

        val priorSelection = JvmSourceProviderSelection.getExact(context)
        val directory = File(context.cacheDir, "jvm-source-m9-runtime-${UUID.randomUUID()}").apply {
            assertTrue("Unable to create the M9-3 source directory", mkdir())
        }.canonicalFile
        val sourceBytes = runtimeExceptionJavaSource().toByteArray(Charsets.UTF_8)
        val sourceFile = File(directory, "Main.java").apply { writeBytes(sourceBytes) }
        val publishedDiagnostics = mutableListOf<JvmSourceDiagnostic>()
        val sink = object : JvmSourceExecutionSink {
            override fun publishDiagnostics(diagnostics: List<JvmSourceDiagnostic>) {
                publishedDiagnostics += diagnostics
            }

            override fun publishOutput(stdout: String, stderr: String) = Unit
        }
        val host = RemoteJvmSourceHost.createAndroid(
            context = context,
            featureEnabled = true,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            launchPackage = { true },
            hasResumedActivity = { true },
        )
        val control = JvmSourceExecutionControl()

        try {
            JvmSourceProviderSelection.setExact(context, PRODUCTION_PROVIDER)

            val failure = try {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    control,
                    sink,
                )
                throw AssertionError("M9-3 runtime exception source unexpectedly completed")
            } catch (expected: RemoteJvmSourceException) {
                expected
            }

            assertEquals(JvmSourceUserFailureCode.RUNTIME_FAILED, failure.userFailure.code)
            assertEquals(1, publishedDiagnostics.size)
            val diagnostic = publishedDiagnostics.single()
            assertEquals(JvmDiagnosticSeverity.ERROR, diagnostic.severity)
            assertEquals("JAVA_RUNTIME_EXCEPTION", diagnostic.code)
            assertEquals("Main.java", diagnostic.sourceFileName)
            assertEquals(4, diagnostic.line)
            assertEquals(1, diagnostic.column)
            assertEquals("java.lang.IllegalStateException", diagnostic.runtimeExceptionClassName)
            assertEquals("Java execution failed", diagnostic.message)

            val projected = JvmSourcePublicDiagnosticPolicy.project(diagnostic)
            val publicText = JvmSourcePublicDiagnosticFormatter.format(projected)
            assertEquals(
                "Main.java:4:1: JAVA_RUNTIME_EXCEPTION [java.lang.IllegalStateException]: " +
                    "Java execution failed",
                publicText,
            )
            listOf(diagnostic.toString(), publicText, failure.toString()).forEach { visible ->
                assertFalse(visible.contains(RUNTIME_SECRET))
                assertFalse(visible.contains("classes.dex"))
                assertFalse(visible.contains("uid="))
                assertFalse(visible.contains("pid="))
            }

            emitRuntimeDiagnosticRecord(
                sourceSha256 = sha256(sourceBytes),
                diagnostic = diagnostic,
                publicText = publicText,
            )
        } finally {
            control.close()
            host.close()
            JvmSourceProviderSelection.setExact(context, priorSelection)
            directory.deleteRecursively()
        }
    }

    @Test
    fun negotiatedObservationIsVisibleAfterProductionProviderCleanup() {
        assertTrue(
            "M9-4 observation selector requires -Pautojs.jvmSource.experimental=true",
            BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
        )
        requireProductionProviderBoundary()

        val priorSelection = JvmSourceProviderSelection.getExact(context)
        val directory = File(context.cacheDir, "jvm-source-m9-observation-${UUID.randomUUID()}").apply {
            assertTrue("Unable to create the M9-4 source directory", mkdir())
        }.canonicalFile
        val sourceBytes = observationJavaSource().toByteArray(Charsets.UTF_8)
        val sourceFile = File(directory, "Main.java").apply { writeBytes(sourceBytes) }
        val publishedObservations = mutableListOf<JvmSourceObservation>()
        val sink = object : JvmSourceExecutionSink {
            override fun publishDiagnostics(diagnostics: List<JvmSourceDiagnostic>) = Unit
            override fun publishOutput(stdout: String, stderr: String) = Unit
            override fun publishObservation(observation: JvmSourceObservation) {
                publishedObservations += observation
            }
        }
        val host = RemoteJvmSourceHost.createAndroid(
            context = context,
            featureEnabled = true,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            launchPackage = { true },
            hasResumedActivity = { true },
        )
        val control = JvmSourceExecutionControl()

        try {
            JvmSourceProviderSelection.setExact(context, PRODUCTION_PROVIDER)
            val result = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                control,
                sink,
            )

            assertEquals("42", result.resultJson)
            assertEquals(1, publishedObservations.size)
            val observation = publishedObservations.single()
            assertEquals(result.observation, observation)
            assertEquals(result.requestId, observation.requestId.toString())
            assertTrue(observation.sessionElapsedMillis >= 0L)
            assertNotNull(observation.workerStartupDurationMillis)
            assertNotNull(observation.workerStartProfile)
            assertNotNull(observation.loadDurationMillis)
            assertNotNull(observation.runDurationMillis)
            assertNotNull(observation.terminationDurationMillis)
            assertTrue(observation.cacheOutcome != JvmObservationCacheOutcome.NOT_EVALUATED)
            if (observation.cacheOutcome == JvmObservationCacheOutcome.HIT) {
                assertNull(observation.compileDurationMillis)
                assertNull(observation.d8DurationMillis)
                assertNull(observation.cacheMissReason)
            } else {
                assertEquals(JvmObservationCacheOutcome.MISS, observation.cacheOutcome)
                assertNotNull(observation.cacheMissReason)
            }
            assertTrue(observation.resources.isNotEmpty())
            assertTrue(observation.resources.size <= JvmSourceContract.MAX_OBSERVATION_RESOURCE_SAMPLES)
            assertEquals(
                observation.resources.size,
                observation.resources.distinctBy { it.process to it.phase }.size,
            )
            JvmSourceValidation.validateObservation(observation)
            val encoded = JvmSourceCodec.encodeObservation(observation)
            assertTrue(encoded.size <= JvmSourceContract.MAX_OBSERVATION_BYTES)
            assertEquals(observation, JvmSourceCodec.decodeObservation(encoded))

            val publicText = JvmSourcePublicObservationFormatter.format(
                JvmSourcePublicObservationPolicy.project(observation),
            )
            assertTrue(publicText.contains("compile="))
            assertTrue(publicText.contains("run="))
            assertTrue(publicText.contains("cache="))
            assertTrue(publicText.contains("samples="))
            listOf(
                result.requestId,
                directory.absolutePath,
                PRODUCTION_PROVIDER.flattenToString(),
            ).forEach { secret -> assertFalse(publicText.contains(secret)) }

            emitObservationRuntimeRecord(result, observation, encoded.size, publicText)
        } finally {
            control.close()
            host.close()
            JvmSourceProviderSelection.setExact(context, priorSelection)
            directory.deleteRecursively()
        }
    }

    /** Shared by the R1 selector and the R2 API-24 outer selector without forging a nested JUnit result. */
    internal fun runCanonicalProductionPipeline(): JSONObject {
        assertTrue(
            "Canonical R1 selector requires -Pautojs.jvmSource.experimental=true",
            BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED,
        )
        requireProductionProviderBoundary()

        val priorSelection = JvmSourceProviderSelection.getExact(context)
        val requestedDirectory = File(context.cacheDir, "jvm-source-r1-${UUID.randomUUID()}").apply {
            assertTrue("Unable to create the R1 source directory", mkdir())
        }
        val directory = requestedDirectory.canonicalFile
        assertTrue("R1 selector canonical directory is unavailable", directory.isDirectory)
        assertEquals(directory.absolutePath, directory.canonicalPath)
        val sourceBytes = javaSource().toByteArray(Charsets.UTF_8)
        val sourceFile = File(directory, "Main.java").apply { writeBytes(sourceBytes) }
        assertEquals(
            "R1 selector source must use its canonical path",
            sourceFile.absolutePath,
            sourceFile.canonicalPath,
        )

        return try {
            JvmSourceProviderSelection.setExact(context, PRODUCTION_PROVIDER)
            assertEquals(PRODUCTION_PROVIDER, JvmSourceProviderSelection.getExact(context))

            val generic = executeAndAwait { listener ->
                service.execute(
                    JvmFileSource(sourceFile),
                    listener,
                    ExecutionConfig(workingDirectory = directory.absolutePath),
                )
            }
            assertNull("Generic .java execution unexpectedly succeeded", generic.result)
            assertNotNull("Generic .java execution did not report a failure", generic.error)
            val genericFailure = checkNotNull(generic.error)
            val publicFailure = genericFailure as? JvmSourceUserFacingException
                ?: throw AssertionError("Generic .java rejection was not a stable user-facing failure", genericFailure)
            assertEquals(JvmSourceUserFailureCode.HOST_UNAVAILABLE, publicFailure.failure.code)
            assertEquals(JvmSourceContainment.NOT_DISPATCHED, publicFailure.failure.containment)
            assertFalse(publicFailure.failure.scriptEngineFallbackAllowed)

            val explicit = executeAndAwait { listener ->
                JvmSourceExplicitRunner.runJvmSourceExplicit(
                    service = service,
                    file = sourceFile,
                    listener = listener,
                    config = ExecutionConfig(workingDirectory = directory.absolutePath),
                )
            }
            explicit.error?.let { publicFailure ->
                val handshakeFailure = diagnoseProviderHandshakeAfterExecutionFailure()
                if (handshakeFailure != null) {
                    throw AssertionError("Explicit R1 Java provider handshake diagnosis failed", handshakeFailure)
                }
                val directHostFailure = diagnoseDirectHostAfterExecutionFailure(sourceFile)
                throw AssertionError(
                    "Explicit R1 Java execution failed after a verified provider handshake; " +
                        "the direct-host failure is attached",
                    directHostFailure ?: publicFailure,
                )
            }
            val result = explicit.result as? RemoteJvmScriptResult
                ?: throw AssertionError("Explicit R1 result did not use RemoteJvmScriptResult")

            val sourceSha256 = sha256(sourceBytes)
            assertEquals(sourceBytes.size.toLong(), result.sourceSizeBytes)
            assertEquals(sourceSha256, result.sourceSha256)
            assertTrue(SHA256.matches(result.requestSha256))
            assertEquals("false", result.resultJson)
            assertEquals(EXPECTED_RESULT_SHA256, result.resultSha256)
            assertEquals(sha256(result.resultJson.toByteArray(Charsets.UTF_8)), result.resultSha256)
            assertEquals(STDOUT_TEXT, result.stdout)
            assertEquals(STDERR_TEXT, result.stderr)
            assertEquals(STDOUT_TEXT.toByteArray().size.toLong(), result.stdoutSizeBytes)
            assertEquals(STDERR_TEXT.toByteArray().size.toLong(), result.stderrSizeBytes)
            assertEquals(sha256(STDOUT_TEXT.toByteArray()), result.stdoutSha256)
            assertEquals(sha256(STDERR_TEXT.toByteArray()), result.stderrSha256)
            assertEquals(PRODUCTION_PROVIDER.flattenToString(), result.providerComponent)
            assertEquals(PRODUCTION_PROVIDER.packageName, result.providerPackageName)
            assertEquals("ecj", result.sourceCompilerFamily)
            assertEquals("3.26.0", result.sourceCompilerVersion)
            assertEquals("8.13.17", result.d8Version)
            assertEquals(24, result.minApi)
            assertTrue(SHA256.matches(result.runtimeLibrarySha256))
            assertTrue(SHA256.matches(result.toolchainSha256))
            assertTrue(result.providerSignerSha256.isNotEmpty())
            assertTrue(result.providerSignerSha256.all(SHA256::matches))
            assertTrue(result.classArtifactSizeBytes > 0L)
            assertTrue(result.dexArtifactSizeBytes > 0L)
            assertTrue(SHA256.matches(result.classArtifactSha256))
            assertTrue(SHA256.matches(result.dexArtifactSha256))
            assertTrue(result.dexVersion in JvmDexRuntimeProfile.admittedVersions(result.minApi))
            assertTrue(result.dexVersion in JvmDexRuntimeProfile.admittedVersions(Build.VERSION.SDK_INT))
            assertEquals(
                checkNotNull(JvmDexRuntimeProfile.loaderKindForApi(Build.VERSION.SDK_INT)).wireName,
                result.loaderKind,
            )
            assertEquals(Process.myPid(), result.hostPid)
            assertEquals(Process.myUid(), result.hostUid)
            assertTrue(result.hostUid != result.providerUid)
            assertEquals(result.providerUid, result.compilerUid)
            assertEquals(result.providerUid, result.workerUid)
            assertEquals("${PROVIDER_PACKAGE}:compiler", result.compilerProcessName)
            assertEquals("${PROVIDER_PACKAGE}:worker", result.workerProcessName)
            assertEquals(3, setOf(result.hostPid, result.compilerPid, result.workerPid).size)
            assertEquals(3, setOf(result.hostProcessName, result.compilerProcessName, result.workerProcessName).size)
            assertTrue(result.workerGeneration > 0L)
            assertEquals(1, result.allowlistedBridgeOperationCount)
            assertEquals(0, result.rejectedBridgeOperationCount)
            assertEquals(listOf(result.compilerPid), result.hostBridgeCallerPids)
            assertEquals(result.workerPid, result.internalBridgeCallerPid)
            assertEquals(result.workerUid, result.internalBridgeCallerUid)
            assertEquals(1, result.internalBridgeCallCount)

            val rejectedRawInjection = verifyRawAuthorityInjectionIsRejected()
            runtimeRecord(result, sourceSha256, rejectedRawInjection)
        } finally {
            JvmSourceProviderSelection.setExact(context, priorSelection)
            directory.deleteRecursively()
        }
    }

    internal fun emitCanonicalRuntimeRecord(record: JSONObject) {
        val evidenceLine = "$RUNTIME_EVIDENCE_PREFIX$record"
        Log.i(TAG, evidenceLine)
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString(RUNTIME_EVIDENCE_STATUS_KEY, record.toString()) },
        )
    }

    private fun emitArgumentRuntimeRecord(result: RemoteJvmScriptResult) {
        val record = JSONObject()
            .put("schemaVersion", 1)
            .put("phase", "M9-2")
            .put("recordType", "production-provider-script-arguments")
            .put("protocolVersion", "${JvmSourceContract.PROTOCOL_MAJOR}.${JvmSourceContract.PROTOCOL_MINOR}")
            .put("entryApiVersion", result.entryApiVersion)
            .put("providerComponent", result.providerComponent)
            .put("providerVersionName", result.providerVersionName)
            .put("providerVersionCode", result.providerVersionCode)
            .put("requestSha256", result.requestSha256)
            .put("sourceSha256", result.sourceSha256)
            .put("resultJson", result.resultJson)
            .put("resultSha256", result.resultSha256)
            .put("hostPid", result.hostPid)
            .put("compilerPid", result.compilerPid)
            .put("workerPid", result.workerPid)
            .put("hostBridgeOperationCount", result.allowlistedBridgeOperationCount)
            .put("internalBridgeCallCount", result.internalBridgeCallCount)
        Log.i(TAG, "$ARGS_EVIDENCE_PREFIX$record")
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString(ARGS_EVIDENCE_STATUS_KEY, record.toString()) },
        )
    }

    private fun emitRuntimeDiagnosticRecord(
        sourceSha256: String,
        diagnostic: JvmSourceDiagnostic,
        publicText: String,
    ) {
        val record = JSONObject()
            .put("schemaVersion", 1)
            .put("phase", "M9-3")
            .put("recordType", "production-provider-runtime-diagnostic")
            .put("protocolVersion", "${JvmSourceContract.PROTOCOL_MAJOR}.${JvmSourceContract.PROTOCOL_MINOR}")
            .put("entryApiVersion", JvmSourceContract.ENTRY_API_VERSION)
            .put("providerComponent", PRODUCTION_PROVIDER.flattenToString())
            .put("sourceSha256", sourceSha256)
            .put("severity", diagnostic.severity.name)
            .put("code", diagnostic.code)
            .put("sourceFileName", diagnostic.sourceFileName)
            .put("line", diagnostic.line)
            .put("column", diagnostic.column)
            .put("runtimeExceptionClassName", diagnostic.runtimeExceptionClassName)
            .put("message", diagnostic.message)
            .put("publicText", publicText)
            .put("secretAbsent", !publicText.contains(RUNTIME_SECRET))
            .put("hostPid", Process.myPid())
            .put("deviceApi", Build.VERSION.SDK_INT)
        Log.i(TAG, "$RUNTIME_DIAGNOSTIC_EVIDENCE_PREFIX$record")
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString(RUNTIME_DIAGNOSTIC_EVIDENCE_STATUS_KEY, record.toString()) },
        )
    }

    private fun emitObservationRuntimeRecord(
        result: RemoteJvmScriptResult,
        observation: JvmSourceObservation,
        encodedBytes: Int,
        publicText: String,
    ) {
        val resources = JSONArray().apply {
            observation.resources.forEach { resource ->
                put(
                    JSONObject()
                        .put("process", resource.process.name)
                        .put("phase", resource.phase.name)
                        .put("rssBytes", resource.rssBytes ?: JSONObject.NULL)
                        .put("openFileDescriptorCount", resource.openFileDescriptorCount ?: JSONObject.NULL)
                        .put("temporaryStorageBytes", resource.temporaryStorageBytes ?: JSONObject.NULL)
                        .put("outputBytes", resource.outputBytes ?: JSONObject.NULL),
                )
            }
        }
        val record = JSONObject()
            .put("schemaVersion", 1)
            .put("phase", "M9-4")
            .put("recordType", "production-provider-terminal-observation")
            .put("protocolVersion", "${JvmSourceContract.PROTOCOL_MAJOR}.${JvmSourceContract.PROTOCOL_MINOR}")
            .put("entryApiVersion", result.entryApiVersion)
            .put("hostVersionCode", BuildConfig.VERSION_CODE)
            .put("providerComponent", result.providerComponent)
            .put("providerVersionName", result.providerVersionName)
            .put("providerVersionCode", result.providerVersionCode)
            .put("sessionElapsedMillis", observation.sessionElapsedMillis)
            .put("workerStartupDurationMillis", observation.workerStartupDurationMillis ?: JSONObject.NULL)
            .put("compilerStartProfile", observation.compilerStartProfile.name)
            .put("workerStartProfile", observation.workerStartProfile?.name ?: JSONObject.NULL)
            .put("compileDurationMillis", observation.compileDurationMillis ?: JSONObject.NULL)
            .put("d8DurationMillis", observation.d8DurationMillis ?: JSONObject.NULL)
            .put("loadDurationMillis", observation.loadDurationMillis ?: JSONObject.NULL)
            .put("runDurationMillis", observation.runDurationMillis ?: JSONObject.NULL)
            .put("terminationDurationMillis", observation.terminationDurationMillis ?: JSONObject.NULL)
            .put("cacheOutcome", observation.cacheOutcome.name)
            .put("cacheMissReason", observation.cacheMissReason?.name ?: JSONObject.NULL)
            .put("resources", resources)
            .put("encodedBytes", encodedBytes)
            .put("publicText", publicText)
            .put("hostPid", result.hostPid)
            .put("compilerPid", result.compilerPid)
            .put("workerPid", result.workerPid)
            .put("deviceApi", Build.VERSION.SDK_INT)
        Log.i(TAG, "$OBSERVATION_EVIDENCE_PREFIX$record")
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString(OBSERVATION_EVIDENCE_STATUS_KEY, record.toString()) },
        )
    }

    private fun requireProductionProviderBoundary() {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val serviceInfo = packageManager.getServiceInfo(PRODUCTION_PROVIDER, PackageManager.GET_META_DATA)
        assertTrue(serviceInfo.exported)
        assertTrue(serviceInfo.enabled)
        assertEquals(JvmSourceContract.PLUGIN_PERMISSION, serviceInfo.permission)
        assertEquals("${PROVIDER_PACKAGE}:compiler", serviceInfo.processName)
        assertEquals(
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, PROVIDER_PACKAGE),
        )
        @Suppress("DEPRECATION")
        val providerUid = packageManager.getApplicationInfo(PROVIDER_PACKAGE, 0).uid
        assertTrue("Production provider must use a UID distinct from the host", providerUid != Process.myUid())
        @Suppress("DEPRECATION")
        val matches = packageManager.queryIntentServices(
            Intent(JvmSourceContract.SERVICE_ACTION),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        ).filter { resolved ->
            val info = resolved.serviceInfo
            ComponentName(info.packageName, info.name) == PRODUCTION_PROVIDER
        }
        assertEquals("Dedicated production service action did not resolve exactly once", 1, matches.size)
        assertEquals(
            PRODUCTION_PROVIDER,
            ComponentName(matches.single().serviceInfo.packageName, matches.single().serviceInfo.name),
        )
    }

    private fun executeAndAwait(start: (ScriptExecutionListener) -> ScriptExecution): ExecutionOutcome {
        val completed = CountDownLatch(1)
        val result = AtomicReference<Any?>()
        val error = AtomicReference<Throwable?>()
        val listener = object : ScriptExecutionListener {
            override fun onStart(execution: ScriptExecution) = Unit

            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                result.set(value)
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, failure: Throwable) {
                error.set(failure)
                completed.countDown()
            }
        }
        val execution = try {
            start(listener)
        } catch (failure: Throwable) {
            return ExecutionOutcome(null, failure)
        }
        assertTrue("R1 script execution timed out", completed.await(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        awaitExecutionRemoved(execution)
        return ExecutionOutcome(result.get(), error.get())
    }

    /**
     * Failure-only, read-only diagnosis. The canonical path above always runs first, so this cannot
     * warm or mask a successful first attempt. It stops at capability validation and never opens a
     * provider session or dispatches user source.
     */
    private fun diagnoseProviderHandshakeAfterExecutionFailure(): Throwable? {
        val transport = AndroidJvmSourceTransport(context)
        val selected = JvmSourceProviderSelection.getComponent(context)
            ?: return IllegalStateException("R1 diagnostic selection disappeared")
        val identity = runCatching {
            JvmSourceProviderPolicy.selectAndPin(
                transport.discoverExactAction(JvmSourceContract.SERVICE_ACTION),
                selected,
            )
        }.getOrElse { return it }
            ?: return IllegalStateException("R1 diagnostic provider admission failed")
        val completed = CountDownLatch(1)
        val remoteConnection = AtomicReference<JvmSourceRemoteConnection?>()
        val bindingFailure = AtomicReference<Throwable?>()
        var binding: JvmSourceBinding? = null
        return try {
            binding = transport.bindExact(identity, object : JvmSourceBindingCallback {
                override fun onConnected(connection: JvmSourceRemoteConnection) {
                    remoteConnection.set(connection)
                    completed.countDown()
                }

                override fun onBindingFailure(cause: Throwable) {
                    bindingFailure.set(cause)
                    completed.countDown()
                }

                override fun onBinderDeath(loss: JvmSourceProviderTransportLoss) {
                    bindingFailure.set(IllegalStateException("R1 diagnostic provider transport retired [$loss]"))
                    completed.countDown()
                }
            })
            if (!completed.await(HANDSHAKE_DIAGNOSTIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw AssertionError("R1 diagnostic provider bind timed out")
            }
            bindingFailure.get()?.let { throw it }
            val remote = checkNotNull(remoteConnection.get()) { "R1 diagnostic provider did not connect" }
            val info = JvmSourceCodec.decodeProviderInfo(remote.getProviderInfo())
            val capabilities = JvmSourceCodec.decodeCapabilities(remote.getCapabilities())
            JvmSourceNegotiation.requireNegotiated(info, BuildConfig.VERSION_CODE.toLong())
            JvmSourceValidation.validateProviderInfo(info)
            JvmSourceValidation.validateCapabilities(capabilities)
            check(JvmSourceProviderPolicy.revalidate(identity, transport.inspectExact(identity.component))) {
                "R1 diagnostic provider identity drifted after handshake"
            }
            null
        } catch (failure: Throwable) {
            failure
        } finally {
            runCatching { binding?.close() }
        }
    }

    /**
     * Failure-only replay through the same production transport while retaining the internal host
     * exception for the private runner log. A successful canonical first attempt never reaches it.
     */
    private fun diagnoseDirectHostAfterExecutionFailure(
        sourceFile: File,
        argsJson: String = "{}",
    ): Throwable? {
        val host = RemoteJvmSourceHost.createAndroid(
            context = context,
            featureEnabled = true,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            launchPackage = { true },
            hasResumedActivity = { true },
        )
        val control = JvmSourceExecutionControl()
        return try {
            host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                control,
                argsJson = argsJson,
            )
            IllegalStateException("R1 direct-host failure replay unexpectedly succeeded")
        } catch (failure: Throwable) {
            failure
        } finally {
            control.close()
            host.close()
        }
    }

    private fun awaitExecutionRemoved(execution: ScriptExecution) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_CLEANUP_TIMEOUT_MILLIS
        while (!execution.engine.isDestroyed && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20)
        }
        assertTrue("R1 script engine was not destroyed after terminal callback", execution.engine.isDestroyed)
        assertNull("R1 script engine was not removed after terminal callback", service.getScriptExecution(execution.id))
    }

    private fun awaitNoExecutions(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_CLEANUP_TIMEOUT_MILLIS
        while (service.scriptExecutions.isNotEmpty() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20)
        }
        assertTrue("Script executions remained $label", service.scriptExecutions.isEmpty())
    }

    private fun verifyRawAuthorityInjectionIsRejected(): Boolean {
        val requestId = JvmRequestId.fromUuid(UUID.randomUUID())
        val violations = ArrayList<Throwable>()
        val bridge = JvmHostAppLaunchBridge(
            requestId = requestId,
            allowedMethods = listOf(JvmHostAppLaunchBridge.METHOD_APP_LAUNCH),
            launchPackage = { true },
            onViolation = violations::add,
        )
        var response: ByteArray? = null
        bridge.dispatch(
            JvmSourceCodec.encodeHostCall(
                JvmHostCall(
                    requestId = requestId,
                    callId = 1,
                    method = JvmHostAppLaunchBridge.METHOD_APP_LAUNCH,
                    payloadJson = "{\"packageName\":\"org.example.app\",\"path\":\"/data/local/tmp/payload.dex\"}",
                    timeoutMillis = 1_000,
                ),
            ),
        ) { encoded -> response = encoded }
        val rejected = response?.let(JvmSourceCodec::decodeHostResponse)?.succeeded == false &&
            violations.isNotEmpty() && bridge.stats().rejectedOperationCount == 1
        bridge.close()
        assertTrue("Raw path authority injection was not rejected", rejected)
        return rejected
    }

    private fun runtimeRecord(
        result: RemoteJvmScriptResult,
        sourceSha256: String,
        rejectedRawInjection: Boolean,
    ): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("phase", "R1")
        .put("recordType", "production-provider-runtime-selector")
        .put("experimentalEffective", BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED)
        .put("providerKind", "production")
        .put("provider", JSONObject()
            .put("packageName", result.providerPackageName)
            .put("component", result.providerComponent)
            .put("versionName", result.providerVersionName)
            .put("versionCode", result.providerVersionCode)
            .put("signerSha256", JSONArray(result.providerSignerSha256)))
        .put("device", JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .put("buildFingerprint", Build.FINGERPRINT))
        .put("source", JSONObject()
            .put("language", "java")
            .put("sizeBytes", result.sourceSizeBytes)
            .put("sha256", sourceSha256)
            .put("requestId", result.requestId)
            .put("requestSha256", result.requestSha256)
            .put("sessionId", result.requestId)
            .put("entryClass", "Main")
            .put("resultJson", result.resultJson)
            .put("expectedResultSha256", EXPECTED_RESULT_SHA256)
            .put("resultSha256", result.resultSha256))
        .put("pipeline", JSONObject()
            .put("ecjReal", result.sourceCompilerFamily == "ecj")
            .put("ecjVersion", result.sourceCompilerVersion)
            .put("javaRelease", 8)
            .put("annotationProcessing", false)
            .put("sourceCompilerFamily", result.sourceCompilerFamily)
            .put("requestSha256", result.requestSha256)
            .put("sourceSha256", result.sourceSha256)
            .put("resultSha256", result.resultSha256)
            .put("sourceCompilerVersion", result.sourceCompilerVersion)
            .put("d8Version", result.d8Version)
            .put("minApi", result.minApi)
            .put("classArtifactSizeBytes", result.classArtifactSizeBytes)
            .put("classArtifactSha256", result.classArtifactSha256)
            .put("d8Real", result.dexArtifactSizeBytes > 0L && SHA256.matches(result.dexArtifactSha256))
            .put("dexArtifactSizeBytes", result.dexArtifactSizeBytes)
            .put("dexArtifactSha256", result.dexArtifactSha256)
            .put("dexVersion", result.dexVersion)
            .put("loaderKind", result.loaderKind)
            .put("toolchainSha256", result.toolchainSha256)
            .put("runtimeLibrarySha256", result.runtimeLibrarySha256)
            .put("entryApiVersion", result.entryApiVersion)
            .put("compilationElapsedMillis", result.compilationElapsedMillis)
            .put("executionElapsedMillis", result.executionElapsedMillis)
            .put("elapsedMillis", result.elapsedMillis)
            .put(
                "isolatedWorker",
                result.workerPid > 0 && result.workerPid != result.hostPid && result.workerUid != result.hostUid,
            )
            .put("entryAbiVerified", true)
            .put("entryInvoked", true)
            .put("hostLoadedGeneratedDex", false))
        .put("processes", JSONObject()
            .put("hostUid", result.hostUid)
            .put("providerUid", result.providerUid)
            .put("compilerUid", result.compilerUid)
            .put("workerUid", result.workerUid)
            .put("hostPid", result.hostPid)
            .put("compilerPid", result.compilerPid)
            .put("workerPid", result.workerPid)
            .put("hostProcess", result.hostProcessName)
            .put("compilerProcess", result.compilerProcessName)
            .put("workerProcess", result.workerProcessName)
            .put("workerGeneration", result.workerGeneration)
            .put("internalBridgeCallerPid", result.internalBridgeCallerPid)
            .put("internalBridgeCallerUid", result.internalBridgeCallerUid))
        .put("bridge", JSONObject()
            .put("allowlistedOperationCount", result.allowlistedBridgeOperationCount)
            .put("rejectedOperationCount", result.rejectedBridgeOperationCount)
            .put("hostBridgeCallerPids", JSONArray(result.hostBridgeCallerPids))
            .put("internalBridgeCallerPid", result.internalBridgeCallerPid)
            .put("internalBridgeCallerUid", result.internalBridgeCallerUid)
            .put("internalBridgeCallCount", result.internalBridgeCallCount)
            .put("rejectedRawObjectInjection", rejectedRawInjection))
        .put("output", JSONObject()
            .put("stdoutSizeBytes", result.stdoutSizeBytes)
            .put("stdoutSha256", result.stdoutSha256)
            .put("stderrSizeBytes", result.stderrSizeBytes)
            .put("stderrSha256", result.stderrSha256))

    private fun javaSource(): String = """
        public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
            @Override
            public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) throws Exception {
                System.out.print("$STDOUT_TEXT");
                System.err.print("$STDERR_TEXT");
                return Boolean.valueOf(context.app().launch("org.autojs.jvm.source.r1.missing"));
            }
        }
    """.trimIndent()

    private fun argumentJavaSource(): String = """
        import java.util.LinkedHashMap;
        import java.util.List;
        import java.util.Map;

        public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
            @Override
            public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) {
                Map<String, Object> args = context.args();
                Map<?, ?> nested = (Map<?, ?>) args.get("nested");
                List<?> tags = (List<?>) args.get("tags");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("name", args.get("name"));
                result.put("enabled", args.get("enabled"));
                result.put("count", nested.get("count"));
                result.put("firstTag", tags.get(0));
                result.put("tagCount", tags.size());
                result.put("nullValue", nested.get("nullable"));
                return result;
            }
        }
    """.trimIndent()

    private fun runtimeExceptionJavaSource(): String = """
        public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
            @Override
            public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) {
                throw new IllegalStateException("$RUNTIME_SECRET");
            }
        }
    """.trimIndent()

    private fun observationJavaSource(): String = """
        public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
            @Override
            public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) {
                return 42;
            }
        }
    """.trimIndent()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class ExecutionOutcome(val result: Any?, val error: Throwable?)

    private companion object {
        const val TAG = "JvmSourceR1E2E"
        const val PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.java.runtime"
        const val PROVIDER_SERVICE = "org.autojs.plugin.jvmsource.java.service.JavaSourceCompilerService"
        const val STDOUT_TEXT = "r1-java-stdout"
        const val STDERR_TEXT = "r1-java-stderr"
        const val EXECUTION_TIMEOUT_SECONDS = 45L
        const val HANDSHAKE_DIAGNOSTIC_TIMEOUT_SECONDS = 10L
        const val ENGINE_CLEANUP_TIMEOUT_MILLIS = 5_000L
        const val RUNTIME_EVIDENCE_STATUS_KEY = "JVM_SOURCE_R1_RUNTIME_EVIDENCE"
        const val RUNTIME_EVIDENCE_PREFIX = "JVM_SOURCE_R1_RUNTIME_EVIDENCE="
        const val ARGS_EVIDENCE_STATUS_KEY = "JVM_SOURCE_M9_ARGS_EVIDENCE"
        const val ARGS_EVIDENCE_PREFIX = "JVM_SOURCE_M9_ARGS_EVIDENCE="
        const val RUNTIME_DIAGNOSTIC_EVIDENCE_STATUS_KEY = "JVM_SOURCE_M9_RUNTIME_DIAGNOSTIC_EVIDENCE"
        const val RUNTIME_DIAGNOSTIC_EVIDENCE_PREFIX = "JVM_SOURCE_M9_RUNTIME_DIAGNOSTIC_EVIDENCE="
        const val OBSERVATION_EVIDENCE_STATUS_KEY = "JVM_SOURCE_M9_OBSERVATION_EVIDENCE"
        const val OBSERVATION_EVIDENCE_PREFIX = "JVM_SOURCE_M9_OBSERVATION_EVIDENCE="
        const val RUNTIME_SECRET = "M9-3 secret must never cross"
        const val SCRIPT_ARGS_JSON =
            "{\"enabled\":true,\"name\":\"AutoJs6\",\"nested\":{\"count\":3,\"nullable\":null}," +
                "\"tags\":[\"java\",\"m9\"]}"
        const val EXPECTED_SCRIPT_ARGS_RESULT_JSON =
            "{\"name\":\"AutoJs6\",\"enabled\":true,\"count\":3,\"firstTag\":\"java\"," +
                "\"tagCount\":2,\"nullValue\":null}"
        const val EXPECTED_RESULT_SHA256 =
            "fcbcf165908dd18a9e49f7ff27810176db8e9f63b4352213741664245224f8aa"
        val SHA256 = Regex("[0-9a-f]{64}")
        val PRODUCTION_PROVIDER = ComponentName(PROVIDER_PACKAGE, PROVIDER_SERVICE)
    }
}
