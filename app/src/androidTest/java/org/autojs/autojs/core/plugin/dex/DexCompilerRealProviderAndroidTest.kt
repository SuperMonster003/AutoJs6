package org.autojs.autojs.core.plugin.dex

import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexClassLoader
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.rhino.AndroidClassLoader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnosticSeverity
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.autojs.plugin.dexcompiler.api.DexCompilerValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Explicitly opted-in happy-path integration with the independently installed, real D8 plugin.
 *
 * This deliberately does not stand in for the fake-provider failure matrix: it proves only exact
 * package/service/signer selection, one real publication, immediate semantic-cache reuse, and
 * execution of the returned generation capability through [DexClassLoader]. It does not claim
 * BUSY, Binder-death, cancellation, timeout, or a complete device/API matrix.
 */
class DexCompilerRealProviderAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Test
    fun realProviderPublishesThenCacheHitsAndLoadsFixture() {
        val identity = requireRealProvider()
        val worker = boundedExecutor("dex-real-provider-control", queueCapacity = 64)
        val blocking = ThreadPoolExecutor(
            0,
            2,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            namedThreadFactory("dex-real-provider-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val drain = boundedExecutor("dex-real-provider-drain", queueCapacity = 1)
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("dex-real-provider-deadline"),
        )
        val store = AndroidDexCompilerArtifactStore(context, drain)
        val harness = DexCompilerExperimentalHarness(
            transport = AndroidDexCompilerExperimentalTransport(context),
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asDeadlineScheduler(),
            threadProbe = DexCompilerThreadProbe { Looper.myLooper() == Looper.getMainLooper() },
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            hostRuntimeApi = Build.VERSION.SDK_INT,
            verifiedCache = DexCompilerVerifiedCache(store::lookupVerified),
        )

        var exactGeneration: DexCompilerVerifiedArtifact? = null
        var primaryFailure: Throwable? = null
        try {
            // The random resource is part of the input digest, so this test owns one fresh semantic
            // key without enumerating or deleting unrelated cache entries.
            val program = uniqueProgramJar()
            val first = runAttempt(harness, store, identity, program)
            outcomeArtifact(first)?.let { exactGeneration = it }
            assertTrue(
                "The unique real-provider input did not publish a DEX generation: $first",
                first is DexCompilerExperimentalOutcome.Published,
            )
            first as DexCompilerExperimentalOutcome.Published
            assertEquals(identity, first.identity)
            assertEquals(1, first.validation.dexEntryCount)
            assertEquals(1, first.validation.classDefinitionCount)

            val second = runAttempt(harness, store, identity, program)
            outcomeArtifact(second)?.let { exactGeneration = it }
            assertTrue(
                "The second identical input did not return the verified-cache capability: $second",
                second is DexCompilerExperimentalOutcome.CacheHit,
            )
            second as DexCompilerExperimentalOutcome.CacheHit
            assertEquals(identity, second.identity)
            assertEquals(first.cacheKeySha256, second.cacheKeySha256)
            assertEquals(first.artifact.outputSha256, second.artifact.outputSha256)
            assertEquals(first.artifact.sizeBytes, second.artifact.sizeBytes)

            assertFixtureLoads(second.artifact)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailure = exactGeneration?.let { artifact ->
                runCatching {
                    assertTrue(
                        "Unable to invalidate the exact real-provider cache generation",
                        artifact.invalidate(),
                    )
                    assertTrue(
                        "The invalidated real-provider generation can still be opened",
                        artifact.openRead() == null,
                    )
                }.exceptionOrNull()
            }
            listOf(worker, blocking, drain).forEach(ThreadPoolExecutor::shutdownNow)
            scheduler.shutdownNow()
            listOf(worker, blocking, drain, scheduler).forEach { executor ->
                executor.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            }
            if (cleanupFailure != null) {
                val failure = primaryFailure
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure)
                } else {
                    throw cleanupFailure
                }
            }
        }
    }

    /**
     * Sequential production-wiring evidence only. This does not establish concurrent single-flight,
     * BUSY, process-death, Binder-death, cancellation, or timeout behavior.
     */
    @Test
    fun productionAndroidClassLoaderPublishesThenCoordinatorGetsExactCacheHit() {
        val identity = requireRealProvider()
        val savedOptIn = snapshotOptInStore()
        val token = UUID.randomUUID()
        val program = File(context.cacheDir, "dex-real-production-$token.jar")
        val loaderCache = File(context.codeCacheDir, "dex-real-production-$token")
        val executor = Executors.newSingleThreadExecutor(
            namedThreadFactory("dex-real-production-wiring"),
        )
        var primaryFailure: Throwable? = null
        try {
            FileOutputStream(program).use { output ->
                output.write(uniqueProgramJar())
                output.fd.sync()
            }
            assertEquals(
                DexCompilerOptInOptions(enabled = true, selectedComponent = identity.component),
                DexCompilerOptInStore.select(identity.component),
            )
            AndroidDexCompilerRuntime.initialize(context, eligibleProcess = true)
            val coordinator = requireNotNull(AndroidDexCompilerRuntime.coordinatorOrNull()) {
                "The production DEX compiler runtime was not initialized"
            }

            val future = executor.submit {
                val classLoader = AndroidClassLoader(
                    parent = requireNotNull(javaClass.classLoader),
                    cacheDir = loaderCache,
                ).loadJarWithExperimentalCompiler(program)
                val fixture = classLoader.loadClass(FIXTURE_CLASS_NAME)
                assertEquals(FIXTURE_CLASS_NAME, fixture.name)
                assertEquals(42, fixture.getDeclaredMethod("answer").invoke(null))

                when (val decision = coordinator.compile(program)) {
                    is DexCompilerRuntimeDecision.Published -> decision.fallbackInput.use {
                        val cacheHit = decision.cacheHit
                        val invalidated = decision.artifact.invalidate()
                        val reopened = decision.artifact.openRead()
                        reopened?.close()
                        assertTrue("The production repeat did not use the persistent cache", cacheHit)
                        assertTrue("Unable to invalidate the exact production cache generation", invalidated)
                        assertTrue("The invalidated production generation can still be opened", reopened == null)
                    }
                    is DexCompilerRuntimeDecision.UseLocal -> {
                        decision.fallbackInput?.close()
                        throw AssertionError("The production repeat unexpectedly used the local compiler: $decision")
                    }
                    DexCompilerRuntimeDecision.Cancelled ->
                        throw AssertionError("The production repeat was unexpectedly cancelled")
                }
            }
            try {
                future.get(PRODUCTION_WIRING_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (timeout: java.util.concurrent.TimeoutException) {
                future.cancel(true)
                throw AssertionError("Production DEX compiler wiring did not finish in time").also {
                    it.initCause(timeout)
                }
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            executor.shutdownNow()
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching {
                assertTrue(
                    "The production-wiring executor did not terminate",
                    executor.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { restoreOptInStore(savedOptIn) }
                .exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                assertTrue("Unable to delete the production input JAR", !program.exists() || program.delete())
            }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                assertTrue(
                    "Unable to delete the production class-loader cache",
                    !loaderCache.exists() || loaderCache.deleteRecursively(),
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                val failure = primaryFailure
                if (failure != null) failure.addSuppressed(cleanupFailure) else throw cleanupFailure
            }
        }
    }

    /**
     * Representative device-matrix coverage for the public Rhino entry. The program extends a
     * Host class that is present in the ordered compile classpath but deliberately absent from the
     * emitted DEX. The same script also exercises the independent V1.0 runtime.loadJar entry.
     *
     * The external acceptance runner owns the authorized-device/API/ABI matrix. Keeping that
     * policy outside this test lets the same production-path assertions cover both the API 24/25
     * CLI-compatible D8 path and the API 26+ command path without weakening either one.
     */
    @Test
    fun representativeRhinoClasspathEntryUsesRealProviderAndRetainsLegacyLoadJar() {
        val identity = requireRealProvider()
        val savedOptIn = snapshotOptInStore()
        val token = UUID.randomUUID()
        val workDir = File(context.cacheDir, "dex-real-rhino-classpath-$token")
        assertTrue("Unable to create the Rhino classpath fixture directory", workDir.mkdirs())
        val program = File(workDir, CLASSPATH_PROGRAM_NAME)
        val classpath = File(workDir, CLASSPATH_NAME)
        val legacyProgram = File(workDir, LEGACY_PROGRAM_NAME)
        val marker = File(workDir, RHINO_MARKER_NAME)
        val script = File(workDir, RHINO_SCRIPT_NAME)
        val executor = Executors.newSingleThreadExecutor(
            namedThreadFactory("dex-real-rhino-classpath"),
        )
        var classpathGeneration: DexCompilerVerifiedArtifact? = null
        var legacyGeneration: DexCompilerVerifiedArtifact? = null
        var primaryFailure: Throwable? = null
        try {
            listOf(
                program to classpathProgramJar(),
                classpath to classpathStubJar(),
                legacyProgram to uniqueProgramJar(),
            ).forEach { (file, content) ->
                FileOutputStream(file).use { output ->
                    output.write(content)
                    output.fd.sync()
                }
            }
            script.writeText(RHINO_CLASSPATH_SOURCE)
            assertEquals(
                DexCompilerOptInOptions(enabled = true, selectedComponent = identity.component),
                DexCompilerOptInStore.select(identity.component),
            )
            AndroidDexCompilerRuntime.initialize(context, eligibleProcess = true)
            val coordinator = requireNotNull(AndroidDexCompilerRuntime.coordinatorOrNull()) {
                "The production DEX compiler runtime was not initialized"
            }

            service.stopAll()
            awaitNoRegisteredEngines("before real-provider Rhino classpath execution")
            val completed = CountDownLatch(1)
            val startedEngineClass = AtomicReference<Class<*>>()
            val scriptFailure = AtomicReference<Throwable>()
            val source = JavaScriptFileSource(script)
            assertEquals(JavaScriptSource.ENGINE, source.engineName)
            service.execute(
                source,
                object : SimpleScriptExecutionListener() {
                    override fun onStart(execution: ScriptExecution) {
                        startedEngineClass.set(execution.engine.javaClass)
                    }

                    override fun onSuccess(execution: ScriptExecution, result: Any?) {
                        completed.countDown()
                    }

                    override fun onException(execution: ScriptExecution, error: Throwable) {
                        scriptFailure.set(error)
                        completed.countDown()
                    }
                },
                ExecutionConfig(workingDirectory = workDir.absolutePath),
            )
            if (!completed.await(RHINO_SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                service.stopAll()
                throw AssertionError("Timed out waiting for the real-provider Rhino classpath script")
            }
            awaitNoRegisteredEngines("after real-provider Rhino classpath execution")
            scriptFailure.get()?.let { failure ->
                throw AssertionError("Real-provider Rhino classpath script failed", failure)
            }
            val started = requireNotNull(startedEngineClass.get()) {
                "Script execution did not report its engine"
            }
            assertTrue(
                "The public classpath entry did not execute on a Rhino engine: ${started.name}",
                RhinoJavaScriptEngine::class.java.isAssignableFrom(started),
            )
            assertEquals("42,42", marker.takeIf(File::isFile)?.readText())

            val future = executor.submit {
                when (val decision = coordinator.compileWithClasspath(program, listOf(classpath))) {
                    is DexCompilerRuntimeDecision.Published -> {
                        classpathGeneration = decision.artifact
                        decision.fallbackInput.use {
                            assertTrue(
                                "The Rhino classpath entry did not populate the exact provider cache",
                                decision.cacheHit,
                            )
                            val output = requireNotNull(decision.artifact.openRead()) {
                                "The exact Rhino classpath generation could not be opened"
                            }.use { it.readBytes() }
                            assertEquals(decision.artifact.sizeBytes, output.size.toLong())
                            assertEquals(
                                "The compile-only classpath class leaked into provider output",
                                1,
                                DexIndexedZipValidator.validate(output).classDefinitionCount,
                            )
                        }
                    }
                    is DexCompilerRuntimeDecision.UseLocal -> {
                        decision.fallbackInput?.close()
                        throw AssertionError("The Rhino classpath repeat unexpectedly used local D8: $decision")
                    }
                    DexCompilerRuntimeDecision.Cancelled ->
                        throw AssertionError("The Rhino classpath repeat was unexpectedly cancelled")
                }

                when (val decision = coordinator.compile(legacyProgram)) {
                    is DexCompilerRuntimeDecision.Published -> {
                        legacyGeneration = decision.artifact
                        decision.fallbackInput.use {
                            assertTrue(
                                "The V1.0 runtime.loadJar call did not populate the exact provider cache",
                                decision.cacheHit,
                            )
                        }
                    }
                    is DexCompilerRuntimeDecision.UseLocal -> {
                        decision.fallbackInput?.close()
                        throw AssertionError("The V1.0 loadJar repeat unexpectedly used local D8: $decision")
                    }
                    DexCompilerRuntimeDecision.Cancelled ->
                        throw AssertionError("The V1.0 loadJar repeat was unexpectedly cancelled")
                }
            }
            try {
                future.get(PRODUCTION_WIRING_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (timeout: java.util.concurrent.TimeoutException) {
                future.cancel(true)
                throw AssertionError("Real-provider Rhino cache verification did not finish in time").also {
                    it.initCause(timeout)
                }
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching { service.stopAll() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { awaitNoRegisteredEngines("after real-provider Rhino classpath cleanup") }
                .exceptionOrNull()?.let(cleanupFailures::add)
            executor.shutdownNow()
            runCatching {
                assertTrue(
                    "The Rhino classpath verification executor did not terminate",
                    executor.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)
            listOfNotNull(classpathGeneration, legacyGeneration).forEach { artifact ->
                runCatching {
                    assertTrue("Unable to invalidate an exact Rhino generation", artifact.invalidate())
                    assertTrue(
                        "An invalidated Rhino generation can still be opened",
                        artifact.openRead() == null,
                    )
                }.exceptionOrNull()?.let(cleanupFailures::add)
            }
            runCatching { restoreOptInStore(savedOptIn) }
                .exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                assertTrue(
                    "Unable to delete the Rhino classpath fixture directory",
                    !workDir.exists() || workDir.deleteRecursively(),
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)
            val failure = primaryFailure
            if (failure != null) {
                cleanupFailures.forEach(failure::addSuppressed)
            } else if (cleanupFailures.isNotEmpty()) {
                val first = cleanupFailures.first()
                cleanupFailures.drop(1).forEach(first::addSuppressed)
                throw first
            }
        }
    }

    /**
     * Representative R5 diagnostic evidence on the production coordinator and class-loader paths.
     * It is deliberately one focused failure/recovery case per target, not a full failure matrix.
     */
    @Test
    fun realProviderFailureCarriesR5DiagnosticsAndNextProductionLoadRecordsRoute() {
        val identity = requireRealProvider()
        val savedOptIn = snapshotOptInStore()
        val token = UUID.randomUUID()
        val invalidProgram = File(context.cacheDir, "dex-real-diagnostic-invalid-$token.jar")
        val validProgram = File(context.cacheDir, "dex-real-diagnostic-valid-$token.jar")
        val loaderCache = File(context.codeCacheDir, "dex-real-diagnostic-loader-$token")
        val executor = Executors.newSingleThreadExecutor(
            namedThreadFactory("dex-real-diagnostic-wiring"),
        )
        var exactGeneration: DexCompilerVerifiedArtifact? = null
        var primaryFailure: Throwable? = null
        try {
            FileOutputStream(invalidProgram).use { output ->
                output.write(invalidProgramJar())
                output.fd.sync()
            }
            FileOutputStream(validProgram).use { output ->
                output.write(uniqueProgramJar())
                output.fd.sync()
            }
            assertEquals(
                DexCompilerOptInOptions(enabled = true, selectedComponent = identity.component),
                DexCompilerOptInStore.select(identity.component),
            )
            AndroidDexCompilerRuntime.initialize(context, eligibleProcess = true)
            val coordinator = requireNotNull(AndroidDexCompilerRuntime.coordinatorOrNull()) {
                "The production DEX compiler runtime was not initialized"
            }
            DexCompilerRuntimeDiagnostics.resetForTest()
            val cacheRoot = File(context.noBackupFilesDir, "dex-compiler-experimental")

            val future = executor.submit {
                assertNull(
                    "Reset diagnostics unexpectedly retained a prior adopted route",
                    DexCompilerRuntimeDiagnostics.latest(),
                )
                val failed = coordinator.compile(invalidProgram)
                assertTrue(
                    "Malformed class unexpectedly produced a remote artifact: $failed",
                    failed is DexCompilerRuntimeDecision.UseLocal,
                )
                failed as DexCompilerRuntimeDecision.UseLocal
                failed.fallbackInput?.close()
                assertEquals(DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED, failed.reason)
                val remote = requireNotNull(failed.remoteFailure) {
                    "Real D8 failure did not retain its structured remote failure"
                }
                assertEquals(DexCompilerErrorCode.COMPILATION_FAILED, remote.code)
                assertEquals(DexCompilerFailurePhase.COMPILATION, remote.phase)
                assertTrue("Real D8 failure did not retain diagnostics", remote.diagnostics.isNotEmpty())
                DexCompilerValidation.validateDiagnostics(remote.diagnostics)
                assertTrue(
                    "Real D8 failure retained no error diagnostic",
                    remote.diagnostics.any { it.severity == DexCompilerDiagnosticSeverity.ERROR },
                )
                assertTrue(
                    "Real D8 diagnostic category is not stable/bounded",
                    remote.diagnostics.all { it.code.startsWith("D8.") && it.code.length <= 128 },
                )
                val retainedBytes = diagnosticRetainedBytes(remote.diagnostics)
                assertTrue(
                    "Real D8 diagnostics exceeded the retained-byte ceiling: $retainedBytes",
                    retainedBytes <= DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
                )
                assertTrue(
                    "Real D8 failure did not expose the logical program origin",
                    remote.diagnostics.any { it.origin == "program" },
                )
                assertTrue(
                    "Real D8 failure did not expose the canonical broken archive entry",
                    remote.diagnostics.any {
                        it.entry == "org/autojs/fixture/dexcompiler/Broken.class"
                    },
                )
                remote.diagnostics.forEach { diagnostic ->
                    assertTrue(
                        "Real D8 diagnostic leaked an Android filesystem path: ${diagnostic.code}",
                        !ANDROID_PRIVATE_PATH.containsMatchIn(diagnostic.message),
                    )
                    assertTrue(
                        "Real D8 diagnostic leaked the host input path: ${diagnostic.code}",
                        !diagnostic.message.contains(invalidProgram.absolutePath),
                    )
                    assertTrue(
                        "Real D8 diagnostic retained control characters: ${diagnostic.code}",
                        diagnostic.message.codePoints().noneMatch { Character.isISOControl(it) },
                    )
                }
                assertTrue(
                    "RemoteFailure.toString leaked the private input path",
                    !remote.toString().contains(invalidProgram.absolutePath),
                )
                val processSummary = requireNotNull(DexCompilerRuntimeDiagnostics.latestFailureDetails()) {
                    "The shared producer did not record its process-local fallback summary"
                }
                assertTrue(processSummary.contains("REMOTE_FAILED"))
                assertTrue(processSummary.contains("COMPILATION_FAILED/COMPILATION"))
                assertTrue("Process summary leaked the private input path", !processSummary.contains(invalidProgram.absolutePath))
                assertNull(
                    "A failed provider attempt was recorded as a successfully adopted route",
                    DexCompilerRuntimeDiagnostics.latest(),
                )
                assertTrue(
                    "A failed D8 session leaked a host transaction",
                    cacheRoot.directChildNames().none { name ->
                        name.startsWith("input-") || name.startsWith("output-") || name.startsWith("verified-")
                    },
                )

                val classLoader = AndroidClassLoader(
                    parent = requireNotNull(javaClass.classLoader),
                    cacheDir = loaderCache,
                ).loadJarWithExperimentalCompiler(validProgram)
                val fixture = classLoader.loadClass(FIXTURE_CLASS_NAME)
                assertEquals(FIXTURE_CLASS_NAME, fixture.name)
                assertEquals(42, fixture.getDeclaredMethod("answer").invoke(null))
                val adoptedRoute = requireNotNull(DexCompilerRuntimeDiagnostics.latest()) {
                    "The recovered production load did not record its adopted route"
                }
                assertEquals(DexCompilerRuntimePath.PLUGIN, adoptedRoute.path)
                assertNull("A direct plugin adoption unexpectedly retained a fallback hint", adoptedRoute.hint)
                assertEquals(
                    "A successful route update overwrote the earlier R2 provider failure",
                    processSummary,
                    DexCompilerRuntimeDiagnostics.latestFailureDetails(),
                )

                when (val cached = coordinator.compile(validProgram)) {
                    is DexCompilerRuntimeDecision.Published -> {
                        exactGeneration = cached.artifact
                        cached.fallbackInput.close()
                        assertTrue("The recovered production repeat did not hit cache", cached.cacheHit)
                    }
                    is DexCompilerRuntimeDecision.UseLocal -> {
                        cached.fallbackInput?.close()
                        throw AssertionError("The session after a real D8 recovery did not cache: $cached")
                    }
                    DexCompilerRuntimeDecision.Cancelled ->
                        throw AssertionError("The cached session after a real D8 recovery was cancelled")
                }
                Log.i(
                    R5_DIAGNOSTIC_LOG_TAG,
                    "PASS sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.firstOrNull()} " +
                        "diagnostics=${remote.diagnostics.size} bytes=$retainedBytes " +
                        "origins=${remote.diagnostics.count { it.origin != null }} " +
                        "entries=${remote.diagnostics.count { it.entry != null }} " +
                        "positions=${remote.diagnostics.count { it.position != null }} " +
                        "route=${adoptedRoute.path}",
                )
            }
            try {
                future.get(PRODUCTION_WIRING_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (timeout: java.util.concurrent.TimeoutException) {
                future.cancel(true)
                throw AssertionError("Real D8 diagnostic/recovery evidence did not finish in time").also {
                    it.initCause(timeout)
                }
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            executor.shutdownNow()
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching {
                assertTrue(
                    "The real diagnostic executor did not terminate",
                    executor.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)
            exactGeneration?.let { artifact ->
                runCatching {
                    assertTrue("Unable to invalidate the recovered exact generation", artifact.invalidate())
                    assertTrue("The recovered invalidated generation can still be opened", artifact.openRead() == null)
                }.exceptionOrNull()?.let(cleanupFailures::add)
            }
            DexCompilerRuntimeDiagnostics.resetForTest()
            runCatching { restoreOptInStore(savedOptIn) }
                .exceptionOrNull()?.let(cleanupFailures::add)
            listOf(invalidProgram, validProgram).forEach { program ->
                runCatching { assertTrue("Unable to delete $program", !program.exists() || program.delete()) }
                    .exceptionOrNull()?.let(cleanupFailures::add)
            }
            runCatching {
                assertTrue(
                    "Unable to delete the real diagnostic class-loader cache",
                    !loaderCache.exists() || loaderCache.deleteRecursively(),
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                val failure = primaryFailure
                if (failure != null) failure.addSuppressed(cleanupFailure) else throw cleanupFailure
            }
        }
    }

    private fun requireRealProvider(): DexCompilerPinnedIdentity {
        assumeTrue(
            "Real DEX compiler instrumentation is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assertTrue("Real DEX compiler instrumentation requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals(
            "The real plugin authorizes only the canonical AutoJs6 host package",
            HOST_PACKAGE,
            context.packageName,
        )

        val packageManager = context.packageManager
        val applicationInfo = try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(REAL_PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real DEX compiler package is not installed: $REAL_PLUGIN_PACKAGE").also {
                it.initCause(error)
            }
        }
        assertEquals(REAL_PLUGIN_PACKAGE, applicationInfo.packageName)
        assertEquals(
            "The real DEX compiler and host must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )

        val transport = AndroidDexCompilerExperimentalTransport(context)
        val packageCandidates = transport.discoverExactAction(DexCompilerContract.SERVICE_ACTION)
            .filter { candidate -> candidate.component.packageName == REAL_PLUGIN_PACKAGE }
        assertEquals(
            "The real package must expose exactly the expected DEX compiler service",
            listOf(REAL_COMPONENT),
            packageCandidates.map(DexCompilerServiceCandidate::component),
        )
        val candidate = packageCandidates.single()
        assertEquals(DexCompilerContract.SERVICE_ACTION, candidate.declaredAction)
        assertTrue("The real DEX compiler service is not exported", candidate.exported)
        assertTrue("The real DEX compiler application is disabled", candidate.applicationEnabled)
        assertTrue("The real DEX compiler service is disabled", candidate.serviceEnabled)
        assertEquals(DexCompilerContract.PLUGIN_PERMISSION, candidate.requiredPermission)
        assertTrue("The real DEX compiler has no signer identity", candidate.signerSha256.isNotEmpty())
        assertTrue("The transport did not confirm the host/plugin signer match", candidate.sameSignerAsHost)
        assertTrue(
            "The real DEX compiler candidate is rejected by the host policy",
            DexCompilerExperimentalPolicy.rejection(candidate, releaseBuild = !BuildConfig.DEBUG) == null,
        )

        val identity = candidate.pin()
        assertEquals(REAL_COMPONENT, identity.component)
        assertEquals(
            "Exact reinspection changed the real DEX compiler identity",
            identity,
            transport.inspectExact(REAL_COMPONENT)?.pin(),
        )
        return identity
    }

    private fun runAttempt(
        harness: DexCompilerExperimentalHarness,
        store: AndroidDexCompilerArtifactStore,
        identity: DexCompilerPinnedIdentity,
        program: ByteArray,
    ): DexCompilerExperimentalOutcome {
        val snapshot = store.snapshot(
            input = ByteArrayInputStream(program),
            maximumBytes = DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES,
        )
        val output = try {
            store.outputTransaction()
        } catch (error: Throwable) {
            snapshot.close()
            throw error
        }
        val request = try {
            DexCompilerRuntimeRequestFactory.create(
                snapshot = snapshot,
                minApi = Build.VERSION.SDK_INT,
            )
        } catch (error: Throwable) {
            runCatching { output.rollback() }
            runCatching { output.close() }
            snapshot.close()
            throw error
        }
        val terminal = CountDownLatch(1)
        val outcome = AtomicReference<DexCompilerExperimentalOutcome?>()
        var handle: Closeable? = null
        try {
            handle = harness.start(
                DexCompilerExperimentalInvocation(
                    request = request,
                    inputSnapshot = snapshot,
                    outputTransaction = output,
                    releaseBuild = !BuildConfig.DEBUG,
                    preferredComponent = REAL_COMPONENT,
                    requestFinalization = DexCompilerRequestFinalization.CAPABILITY_BOUND,
                    requiredIdentity = identity,
                ),
            ) { value ->
                if (outcome.compareAndSet(null, value)) terminal.countDown()
            }
            val waitMillis = request.timeoutMillis + OUTCOME_WAIT_ALLOWANCE_MILLIS
            assertTrue(
                "The real DEX compiler did not reach a terminal host outcome",
                terminal.await(waitMillis, TimeUnit.MILLISECONDS),
            )
            return checkNotNull(outcome.get()) { "The real DEX compiler returned no terminal outcome" }
        } finally {
            handle?.close()
            if (handle == null) {
                runCatching { output.rollback() }
                runCatching { output.close() }
                runCatching { snapshot.close() }
            } else if (outcome.get() == null) {
                terminal.await(TERMINAL_CLEANUP_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun outcomeArtifact(outcome: DexCompilerExperimentalOutcome): DexCompilerVerifiedArtifact? =
        when (outcome) {
            is DexCompilerExperimentalOutcome.Published -> outcome.artifact
            is DexCompilerExperimentalOutcome.CacheHit -> outcome.artifact
            else -> null
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

    private fun snapshotOptInStore(): OptInStoreSnapshot {
        val preferences = Pref.get()
        val values = preferences.all
        val enabledPresent = preferences.contains(OPT_IN_ENABLED_KEY)
        val componentPresent = preferences.contains(OPT_IN_COMPONENT_KEY)
        val enabled = values[OPT_IN_ENABLED_KEY]
        val component = values[OPT_IN_COMPONENT_KEY]
        check(!enabledPresent || enabled is Boolean) { "Unexpected DEX compiler opt-in enabled value" }
        check(!componentPresent || component is String) { "Unexpected DEX compiler opt-in component value" }
        return OptInStoreSnapshot(
            options = DexCompilerOptInStore.read(),
            enabledPresent = enabledPresent,
            enabled = enabled as? Boolean,
            componentPresent = componentPresent,
            component = component as? String,
        )
    }

    private fun restoreOptInStore(snapshot: OptInStoreSnapshot) {
        val editor = Pref.get().edit()
            .remove(OPT_IN_ENABLED_KEY)
            .remove(OPT_IN_COMPONENT_KEY)
        if (snapshot.enabledPresent) editor.putBoolean(OPT_IN_ENABLED_KEY, checkNotNull(snapshot.enabled))
        if (snapshot.componentPresent) editor.putString(OPT_IN_COMPONENT_KEY, checkNotNull(snapshot.component))
        check(editor.commit()) { "Unable to restore DEX compiler opt-in preferences" }
        check(DexCompilerOptInStore.read() == snapshot.options) {
            "Restored DEX compiler opt-in state does not match its saved value"
        }
    }

    private fun assertFixtureLoads(artifact: DexCompilerVerifiedArtifact) {
        val token = UUID.randomUUID()
        val optimized = File(context.codeCacheDir, "dex-real-provider-$token")
        val loadable = File(context.codeCacheDir, "dex-real-provider-$token.dex.zip")
        assertTrue("Unable to create the real-provider optimization directory", optimized.mkdirs())
        try {
            val source = requireNotNull(artifact.openRead()) {
                "The real-provider artifact capability could not be opened"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            source.use { input ->
                FileOutputStream(loadable).use { output ->
                    // Android 14 requires dynamically loaded code to be read-only. Change the
                    // pathname before writing any content; this already-open descriptor remains
                    // the sole writer until fsync/close completes.
                    assertTrue("Unable to make the adopted DEX read-only", loadable.setReadOnly())
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        require(copied <= artifact.sizeBytes) { "The artifact exceeded its declared size" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            assertEquals(artifact.sizeBytes, copied)
            assertEquals(artifact.outputSha256, digest.digest().toLowerHex())

            val fixture = DexClassLoader(
                loadable.absolutePath,
                optimized.absolutePath,
                null,
                javaClass.classLoader,
            ).loadClass(FIXTURE_CLASS_NAME)
            assertEquals(FIXTURE_CLASS_NAME, fixture.name)
            assertEquals(42, fixture.getDeclaredMethod("answer").invoke(null))
        } finally {
            loadable.delete()
            optimized.deleteRecursively()
        }
    }

    private fun uniqueProgramJar(): ByteArray {
        val classBytes = fixtureClassBytes()
        val nonce = UUID.randomUUID().toString()
        val nonceBytes = nonce.toByteArray(StandardCharsets.US_ASCII)
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeStoredEntry(FIXTURE_CLASS_ENTRY, classBytes)
                zip.writeStoredEntry("org/autojs/fixture/dexcompiler/run-$nonce.txt", nonceBytes)
            }
            output.toByteArray()
        }
    }

    private fun classpathProgramJar(): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.writeStoredEntry(
                CLASSPATH_PROGRAM_CLASS_ENTRY,
                fixtureClassBytes(
                    internalName = CLASSPATH_PROGRAM_INTERNAL_NAME,
                    superInternalName = HOST_PARENT_INTERNAL_NAME,
                    sourceFileName = "ClasspathProgramFixture.java",
                ),
            )
            zip.writeStoredEntry(
                "org/autojs/fixture/dexcompiler/classpath-run-${UUID.randomUUID()}.txt",
                byteArrayOf(1),
            )
        }
        output.toByteArray()
    }

    private fun classpathStubJar(): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.writeStoredEntry(
                HOST_PARENT_CLASS_ENTRY,
                fixtureClassBytes(
                    internalName = HOST_PARENT_INTERNAL_NAME,
                    superInternalName = "java/lang/Object",
                    sourceFileName = "Objects.java",
                    answer = null,
                    finalClass = false,
                ),
            )
        }
        output.toByteArray()
    }

    private fun invalidProgramJar(): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            // The provider's bounded archive validator intentionally checks only the class magic;
            // the truncated class therefore reaches the real D8 diagnostic handler.
            zip.writeStoredEntry(
                "org/autojs/fixture/dexcompiler/Broken.class",
                byteArrayOf(0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte()),
            )
        }
        output.toByteArray()
    }

    private fun File.directChildNames(): Set<String> =
        listFiles()?.mapTo(sortedSetOf()) { child -> child.name } ?: emptySet()

    private fun diagnosticRetainedBytes(
        diagnostics: Collection<org.autojs.plugin.dexcompiler.api.DexCompilerDiagnostic>,
    ): Long = diagnostics.sumOf { diagnostic ->
        diagnostic.code.toByteArray(StandardCharsets.UTF_8).size.toLong() +
            diagnostic.message.toByteArray(StandardCharsets.UTF_8).size.toLong() +
            (diagnostic.origin?.toByteArray(StandardCharsets.UTF_8)?.size?.toLong() ?: 0L) +
            (diagnostic.entry?.toByteArray(StandardCharsets.UTF_8)?.size?.toLong() ?: 0L) +
            (diagnostic.position?.let { position ->
                listOf(position.line, position.column, position.endLine, position.endColumn)
                    .count { it != null } * Int.SIZE_BYTES.toLong() +
                    listOf(position.offset, position.endOffset)
                        .count { it != null } * Long.SIZE_BYTES.toLong()
            } ?: 0L)
    }

    private fun ZipOutputStream.writeStoredEntry(name: String, bytes: ByteArray) {
        val checksum = CRC32().apply { update(bytes) }.value
        putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = checksum
            time = ZIP_DOS_EPOCH_MILLIS
            extra = byteArrayOf()
            comment = null
        })
        write(bytes)
        closeEntry()
    }

    /** A deterministic Java 8 class with a public constructor and optional answer() method. */
    private fun fixtureClassBytes(
        internalName: String = FIXTURE_INTERNAL_NAME,
        superInternalName: String = "java/lang/Object",
        sourceFileName: String = "RealProviderFixture.java",
        answer: Int? = 42,
        finalClass: Boolean = true,
    ): ByteArray = ByteArrayOutputStream().use { bytes ->
        require(answer == null || answer in Byte.MIN_VALUE..Byte.MAX_VALUE)
        DataOutputStream(bytes).use { output ->
            output.writeInt(0xcafebabe.toInt())
            output.writeShort(0)
            output.writeShort(52)
            output.writeShort(14)
            output.writeUtf8Constant(internalName) // #1
            output.writeByte(7); output.writeShort(1) // #2 Class
            output.writeUtf8Constant(superInternalName) // #3
            output.writeByte(7); output.writeShort(3) // #4 Class
            output.writeUtf8Constant("<init>") // #5
            output.writeUtf8Constant("()V") // #6
            output.writeUtf8Constant("Code") // #7
            output.writeByte(12); output.writeShort(5); output.writeShort(6) // #8 NameAndType
            output.writeByte(10); output.writeShort(4); output.writeShort(8) // #9 Methodref
            output.writeUtf8Constant("answer") // #10
            output.writeUtf8Constant("()I") // #11
            output.writeUtf8Constant("SourceFile") // #12
            output.writeUtf8Constant(sourceFileName) // #13

            output.writeShort(if (finalClass) 0x0031 else 0x0021) // public [final] super
            output.writeShort(2)
            output.writeShort(4)
            output.writeShort(0) // interfaces
            output.writeShort(0) // fields
            output.writeShort(if (answer == null) 1 else 2) // methods

            output.writeShort(0x0001) // public <init>()V
            output.writeShort(5)
            output.writeShort(6)
            output.writeShort(1)
            output.writeShort(7)
            output.writeInt(17)
            output.writeShort(1)
            output.writeShort(1)
            output.writeInt(5)
            output.write(byteArrayOf(0x2a, 0xb7.toByte(), 0x00, 0x09, 0xb1.toByte()))
            output.writeShort(0)
            output.writeShort(0)

            if (answer != null) {
                output.writeShort(0x0009) // public static answer()I
                output.writeShort(10)
                output.writeShort(11)
                output.writeShort(1)
                output.writeShort(7)
                output.writeInt(15)
                output.writeShort(1)
                output.writeShort(0)
                output.writeInt(3)
                output.write(byteArrayOf(0x10, answer.toByte(), 0xac.toByte()))
                output.writeShort(0)
                output.writeShort(0)
            }

            output.writeShort(1) // class attributes
            output.writeShort(12)
            output.writeInt(2)
            output.writeShort(13)
        }
        bytes.toByteArray()
    }

    private fun DataOutputStream.writeUtf8Constant(value: String) {
        writeByte(1)
        writeUTF(value)
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun boundedExecutor(name: String, queueCapacity: Int): ThreadPoolExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queueCapacity),
        namedThreadFactory(name),
        ThreadPoolExecutor.AbortPolicy(),
    )

    private fun namedThreadFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun ScheduledExecutorService.asDeadlineScheduler(): DexCompilerDeadlineScheduler =
        DexCompilerDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private companion object {
        data class OptInStoreSnapshot(
            val options: DexCompilerOptInOptions,
            val enabledPresent: Boolean,
            val enabled: Boolean?,
            val componentPresent: Boolean,
            val component: String?,
        )

        const val ARG_ENABLED = "autojs.dexCompiler.realProvider.enabled"
        const val R5_DIAGNOSTIC_LOG_TAG = "DexCompilerR5"
        const val OPT_IN_ENABLED_KEY = "dex_compiler_experimental_enabled"
        const val OPT_IN_COMPONENT_KEY = "dex_compiler_experimental_component"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.dexcompiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.DexCompilerService"
        val REAL_COMPONENT = DexCompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)

        const val FIXTURE_INTERNAL_NAME = "org/autojs/fixture/dexcompiler/RealProviderFixture"
        const val FIXTURE_CLASS_ENTRY = "$FIXTURE_INTERNAL_NAME.class"
        const val FIXTURE_CLASS_NAME = "org.autojs.fixture.dexcompiler.RealProviderFixture"
        const val CLASSPATH_PROGRAM_NAME = "program-with-classpath.jar"
        const val CLASSPATH_NAME = "compile-only-classpath.jar"
        const val LEGACY_PROGRAM_NAME = "legacy-program.jar"
        const val RHINO_MARKER_NAME = "classpath-result.marker"
        const val RHINO_SCRIPT_NAME = "runtime-load-jar-with-classpath.js"
        const val CLASSPATH_PROGRAM_INTERNAL_NAME =
            "org/autojs/fixture/dexcompiler/ClasspathProgramFixture"
        const val CLASSPATH_PROGRAM_CLASS_ENTRY = "$CLASSPATH_PROGRAM_INTERNAL_NAME.class"
        const val CLASSPATH_PROGRAM_CLASS_NAME =
            "org.autojs.fixture.dexcompiler.ClasspathProgramFixture"
        const val HOST_PARENT_INTERNAL_NAME = "com/stardust/util/Objects"
        const val HOST_PARENT_CLASS_ENTRY = "$HOST_PARENT_INTERNAL_NAME.class"
        const val ZIP_DOS_EPOCH_MILLIS = 315_532_800_000L
        const val OUTCOME_WAIT_ALLOWANCE_MILLIS = 30_000L
        const val TERMINAL_CLEANUP_WAIT_MILLIS = 5_000L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
        const val RHINO_SCRIPT_TIMEOUT_SECONDS = 90L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 5_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 25L
        const val PRODUCTION_WIRING_WAIT_MILLIS =
            DexCompilerContract.DEFAULT_TIMEOUT_MILLIS * 2L + OUTCOME_WAIT_ALLOWANCE_MILLIS

        val ANDROID_PRIVATE_PATH = Regex("(?i)(?:file:/+|/(?:data|storage|sdcard|mnt)/)")

        val RHINO_CLASSPATH_SOURCE =
            """
            "rhino";
            runtime.loadJarWithClasspath("./$CLASSPATH_PROGRAM_NAME", "./$CLASSPATH_NAME");
            var classpathAnswer = Packages.$CLASSPATH_PROGRAM_CLASS_NAME.answer();
            runtime.loadJar("./$LEGACY_PROGRAM_NAME");
            var legacyAnswer = Packages.$FIXTURE_CLASS_NAME.answer();
            files.write("./$RHINO_MARKER_NAME", String(classpathAnswer) + "," + String(legacyAnswer));
            """.trimIndent()
    }
}
