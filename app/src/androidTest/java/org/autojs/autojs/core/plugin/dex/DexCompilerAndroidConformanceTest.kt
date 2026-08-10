package org.autojs.autojs.core.plugin.dex

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexClassLoader
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexProtocolVersion
import org.autojs.plugin.dexcompiler.api.DexRequestId
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryFingerprint
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryIdentity
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel
import org.autojs.plugin.dexcompiler.api.DexSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Opt-in, cross-APK Phase 1 conformance for the real Android Binder transport and artifact store.
 *
 * The fake provider must be installed with the same dedicated debug/test signer as the host. The
 * suite stays skipped during ordinary connected tests unless [ARG_ENABLED] is explicitly true.
 */
class DexCompilerAndroidConformanceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun normalServicePublishesValidatedDexAndLoadsItThroughDexClassLoader() {
        requireConformanceEnvironment()

        val before = publishedArtifacts()
        val outcome = runScenario(NORMAL_SERVICE)

        assertTrue("Normal fake compiler did not publish", outcome is DexCompilerExperimentalOutcome.Published)
        outcome as DexCompilerExperimentalOutcome.Published
        assertEquals(1, outcome.validation.dexEntryCount)
        assertEquals(1, outcome.validation.classDefinitionCount)
        assertEquals(setOf("037"), outcome.validation.dexVersions)

        val afterPublication = publishedArtifacts()
        val publishedCandidate = publishedArtifactFiles(outcome.cacheKeySha256).singleOrNull()
        assertNotNull("Validated DEX generation was not published in the host-private cache", publishedCandidate)
        val published = publishedCandidate!!
        assertEquals(outcome.artifact.sizeBytes, published.length())
        assertTrue("Normal conformance did not publish a fresh cache generation", published.name in afterPublication - before)

        val optimized = File(context.codeCacheDir, "dex-compiler-conformance-${UUID.randomUUID()}")
        val loadable = File(context.codeCacheDir, "dex-compiler-conformance-${UUID.randomUUID()}.dex.zip")
        assertTrue("Unable to create the DEX optimization directory", optimized.mkdirs())
        try {
            val sourceCandidate = outcome.artifact.openRead()
            assertNotNull("Published artifact capability could not be opened", sourceCandidate)
            val source = sourceCandidate!!
            source.use { input ->
                FileOutputStream(loadable).use { output ->
                    // Android 14 requires dynamically loaded code to be read-only. The already-open
                    // descriptor remains the sole writer until the verified copy is synced/closed.
                    assertTrue("Unable to make adopted DEX read-only", loadable.setReadOnly())
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            assertEquals(outcome.artifact.sizeBytes, loadable.length())
            val loaded = DexClassLoader(
                loadable.absolutePath,
                optimized.absolutePath,
                null,
                javaClass.classLoader,
            ).loadClass(FIXTURE_CLASS_NAME)
            assertEquals(FIXTURE_CLASS_NAME, loaded.name)
            assertEquals(42, loaded.getDeclaredMethod("answer").invoke(null))
        } finally {
            optimized.deleteRecursively()
            loadable.delete()
            assertTrue("Unable to invalidate the exact conformance cache generation", outcome.artifact.invalidate())
        }
        val afterInvalidation = publishedArtifacts()
        assertTrue("Invalidated conformance generation remains published", published.name !in afterInvalidation)
        assertTrue(
            "Invalidated conformance generation left cache metadata behind",
            artifactRoot().listFiles().orEmpty().none { it.name.startsWith("${outcome.cacheKeySha256}.") },
        )
        assertTrue("Normal conformance left an unexpected publication", before.containsAll(afterInvalidation))
        assertNoTransientArtifacts()
    }

    @Test
    fun progressBeforeStartServiceFailsClosed() {
        requireConformanceEnvironment()

        assertRejectedScenario(PROGRESS_BEFORE_START_SERVICE, DexCompilerExperimentalFailure.CALLBACK_VIOLATION)
    }

    @Test
    fun duplicateTerminalServiceFailsClosed() {
        requireConformanceEnvironment()

        assertRejectedScenario(DUPLICATE_TERMINAL_SERVICE, DexCompilerExperimentalFailure.CALLBACK_VIOLATION)
    }

    @Test
    fun invalidOutputIsRejectedByTheIndexedZipValidator() {
        requireConformanceEnvironment()

        assertRejectedScenario(INVALID_OUTPUT_SERVICE, DexCompilerExperimentalFailure.OUTPUT_REJECTED)
    }

    @Test
    fun unnegotiatedDexVersionIsRejectedByTheIndexedZipValidator() {
        requireConformanceEnvironment()

        assertRejectedScenario(UNNEGOTIATED_DEX_VERSION_SERVICE, DexCompilerExperimentalFailure.OUTPUT_REJECTED)
    }

    @Test
    fun stalledServiceTimesOutAndRollsBack() {
        requireConformanceEnvironment()

        val before = publishedArtifacts()
        val outcome = runScenario(STALL_SERVICE, requestTimeoutMillis = STALL_TIMEOUT_MILLIS)

        assertUseLocal(outcome, DexCompilerExperimentalFailure.TIMEOUT)
        assertEquals("Timed-out DEX output reached the published cache", before, publishedArtifacts())
        assertNoTransientArtifacts()
    }

    private fun assertRejectedScenario(
        serviceClassName: String,
        expectedFailure: DexCompilerExperimentalFailure,
    ) {
        val before = publishedArtifacts()
        val outcome = runScenario(serviceClassName)

        assertUseLocal(outcome, expectedFailure)
        assertEquals("Rejected DEX compiler service published an artifact", before, publishedArtifacts())
        assertNoTransientArtifacts()
    }

    private fun requireConformanceEnvironment() {
        assumeTrue(
            "DEX compiler Android conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("DEX compiler conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertTrue("DEX compiler fake package is not installed", isPackageInstalled(FAKE_PACKAGE))
        assertEquals(
            "DEX compiler fake and host must use the same dedicated debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )

        val expected = ALL_SERVICES.map { DexCompilerComponent(FAKE_PACKAGE, it) }.toSet()
        val candidates = AndroidDexCompilerExperimentalTransport(context)
            .discoverExactAction(DexCompilerContract.SERVICE_ACTION)
        val discovered = candidates.map(DexCompilerServiceCandidate::component).toSet()
        assertTrue("DEX compiler fake services are missing from exact-action discovery", discovered.containsAll(expected))
        expected.forEach { component ->
            val candidate = candidates.single { it.component == component }
            assertTrue("DEX compiler fixture is not exported: $component", candidate.exported)
            assertTrue("DEX compiler fixture application is disabled: $component", candidate.applicationEnabled)
            assertTrue("DEX compiler fixture service is disabled: $component", candidate.serviceEnabled)
            assertTrue("DEX compiler fixture has no signer identity: $component", candidate.signerSha256.isNotEmpty())
            assertTrue("DEX compiler fixture signer differs from the host: $component", candidate.sameSignerAsHost)
            assertEquals(DexCompilerContract.PLUGIN_PERMISSION, candidate.requiredPermission)
        }
    }

    private fun runScenario(
        serviceClassName: String,
        requestTimeoutMillis: Long = NORMAL_TIMEOUT_MILLIS,
    ): DexCompilerExperimentalOutcome {
        val worker = boundedExecutor("dex-conformance-control", queueCapacity = 64)
        val blocking = ThreadPoolExecutor(
            0,
            2,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            namedThreadFactory("dex-conformance-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val drain = boundedExecutor("dex-conformance-drain", queueCapacity = 1)
        val drainGate = CountDownLatch(if (serviceClassName == DUPLICATE_TERMINAL_SERVICE) 1 else 0)
        val gatedDrain = Executor { command ->
            drain.execute {
                drainGate.await()
                command.run()
            }
        }
        val scheduler = Executors.newSingleThreadScheduledExecutor(namedThreadFactory("dex-conformance-deadline"))
        val program = programJar()
        val store = AndroidDexCompilerArtifactStore(context, gatedDrain)
        val snapshot = store.snapshot(ByteArrayInputStream(program), MAX_PROGRAM_BYTES)
        val output = store.outputTransaction()
        val request = request(program, requestTimeoutMillis)
        val outcomes = AtomicInteger()
        val outcome = AtomicReference<DexCompilerExperimentalOutcome>()
        val terminal = CountDownLatch(1)
        val harness = DexCompilerExperimentalHarness(
            transport = AndroidDexCompilerExperimentalTransport(context),
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asDeadlineScheduler(),
            threadProbe = DexCompilerThreadProbe { Looper.myLooper() == Looper.getMainLooper() },
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            hostRuntimeApi = Build.VERSION.SDK_INT,
        )
        val handle = harness.start(
            DexCompilerExperimentalInvocation(
                request = request,
                inputSnapshot = snapshot,
                outputTransaction = output,
                releaseBuild = false,
                preferredComponent = DexCompilerComponent(FAKE_PACKAGE, serviceClassName),
            ),
        ) { value ->
            if (outcome.compareAndSet(null, value)) terminal.countDown()
            outcomes.incrementAndGet()
        }

        try {
            val waitMillis = requestTimeoutMillis + OUTCOME_WAIT_ALLOWANCE_MILLIS
            assertTrue(
                "$serviceClassName did not reach a terminal host outcome",
                terminal.await(waitMillis, TimeUnit.MILLISECONDS),
            )
            assertNotNull(outcome.get())
            Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            assertEquals("Host emitted more than one terminal outcome", 1, outcomes.get())
            return outcome.get()
        } finally {
            drainGate.countDown()
            handle.close()
            listOf(worker, blocking, drain).forEach(ThreadPoolExecutor::shutdownNow)
            scheduler.shutdownNow()
            listOf(worker, blocking, drain, scheduler).forEach { executor ->
                executor.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun request(program: ByteArray, timeoutMillis: Long): DexCompileRequest = DexCompileRequest(
        requestId = DexRequestId.fromUuid(UUID.randomUUID()),
        protocolVersion = DexProtocolVersion(DexCompilerContract.PROTOCOL_MAJOR, DexCompilerContract.PROTOCOL_MINOR),
        inputFormat = DexCompilerInputFormat.JAR,
        outputFormat = DexCompilerOutputFormat.DEX_ZIP,
        mode = DexCompilerMode.RELEASE,
        minApi = Build.VERSION.SDK_INT,
        programSizeBytes = program.size.toLong(),
        programSha256 = DexSha256.digest(program),
        maxOutputBytes = MAX_OUTPUT_BYTES,
        runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
        expectedRuntimeLibraryFingerprint = FIXTURE_RUNTIME_FINGERPRINT,
        canonicalizationPolicyVersion = DexCompilerContract.CANONICALIZATION_POLICY_VERSION,
        diagnosticByteLimit = DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
        timeoutMillis = timeoutMillis,
    )

    private fun assertUseLocal(
        outcome: DexCompilerExperimentalOutcome,
        expectedFailure: DexCompilerExperimentalFailure,
    ) {
        assertTrue("Expected local fallback but got $outcome", outcome is DexCompilerExperimentalOutcome.UseLocalCompiler)
        outcome as DexCompilerExperimentalOutcome.UseLocalCompiler
        assertEquals(expectedFailure, outcome.failure)
        assertTrue("Failure occurred before the fake provider was dispatched", outcome.pluginWasDispatched)
        assertTrue("Dispatched fake output did not require rollback", outcome.pluginOutputRollbackRequired)
    }

    private fun assertNoTransientArtifacts() {
        val transient = artifactRoot().listFiles().orEmpty()
            .filter { DexCompilerArtifactRecoveryPolicy.isTransientArtifact(it.name) }
        assertTrue("DEX compiler transaction artifacts leaked: ${transient.joinToString { it.name }}", transient.isEmpty())
    }

    private fun publishedArtifactFiles(cacheKeySha256: String): List<File> = artifactRoot().listFiles().orEmpty()
        .filter { file ->
            file.isFile && file.name.startsWith("$cacheKeySha256.") && file.name.endsWith(".dex.zip")
        }

    private fun publishedArtifacts(): Set<String> = artifactRoot().listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".dex.zip") }
        .map(File::getName)
        .toSet()

    private fun artifactRoot(): File = File(context.noBackupFilesDir, ARTIFACT_DIRECTORY_NAME)

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun programJar(): ByteArray {
        val classBytes = byteArrayOf(
            0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte(),
            0x00, 0x00, 0x00, 0x34,
        )
        val crc = CRC32().apply { update(classBytes) }.value
        val nonce = UUID.randomUUID().toString().toByteArray()
        val nonceCrc = CRC32().apply { update(nonce) }.value
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("fixture/Program.class").apply {
                    method = ZipEntry.STORED
                    size = classBytes.size.toLong()
                    compressedSize = classBytes.size.toLong()
                    this.crc = crc
                    time = ZIP_DOS_EPOCH_MILLIS
                    extra = byteArrayOf()
                    comment = null
                })
                zip.write(classBytes)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("fixture/run-${UUID.randomUUID()}.txt").apply {
                    method = ZipEntry.STORED
                    size = nonce.size.toLong()
                    compressedSize = nonce.size.toLong()
                    this.crc = nonceCrc
                    time = ZIP_DOS_EPOCH_MILLIS
                    extra = byteArrayOf()
                    comment = null
                })
                zip.write(nonce)
                zip.closeEntry()
            }
        }.toByteArray()
    }

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
        const val ARG_ENABLED = "autojs.dexCompiler.conformance.enabled"
        const val FAKE_PACKAGE = "org.autojs.plugin.dexcompiler.fake"
        const val SERVICE_PACKAGE = "$FAKE_PACKAGE.service"
        const val NORMAL_SERVICE = "$SERVICE_PACKAGE.NormalDexCompilerService"
        const val PROGRESS_BEFORE_START_SERVICE = "$SERVICE_PACKAGE.ProgressBeforeStartDexCompilerService"
        const val DUPLICATE_TERMINAL_SERVICE = "$SERVICE_PACKAGE.DuplicateTerminalDexCompilerService"
        const val INVALID_OUTPUT_SERVICE = "$SERVICE_PACKAGE.InvalidOutputDexCompilerService"
        const val UNNEGOTIATED_DEX_VERSION_SERVICE = "$SERVICE_PACKAGE.UnnegotiatedDexVersionCompilerService"
        const val STALL_SERVICE = "$SERVICE_PACKAGE.StallDexCompilerService"
        val ALL_SERVICES = setOf(
            NORMAL_SERVICE,
            PROGRESS_BEFORE_START_SERVICE,
            DUPLICATE_TERMINAL_SERVICE,
            INVALID_OUTPUT_SERVICE,
            UNNEGOTIATED_DEX_VERSION_SERVICE,
            STALL_SERVICE,
        )

        const val FIXTURE_CLASS_NAME = "org.autojs.fixture.Fixture"
        const val FIXTURE_DEX_SIZE_BYTES = 636L
        const val FIXTURE_DEX_SHA256 = "f19638c6edf9226ed67754665c139ae933b387d4013c43ef3e2262fd839e4189"
        val FIXTURE_RUNTIME_FINGERPRINT = DexRuntimeLibraryFingerprint.compute(
            listOf(
                DexRuntimeLibraryIdentity(
                    sizeBytes = FIXTURE_DEX_SIZE_BYTES,
                    contentSha256 = DexSha256.fromBytes(FIXTURE_DEX_SHA256.hexToBytes()),
                ),
            ),
        )

        const val ARTIFACT_DIRECTORY_NAME = "dex-compiler-experimental"
        const val MAX_PROGRAM_BYTES = 8L * 1024L * 1024L
        const val MAX_OUTPUT_BYTES = 2L * 1024L * 1024L
        const val NORMAL_TIMEOUT_MILLIS = 20_000L
        const val STALL_TIMEOUT_MILLIS = 2_000L
        const val OUTCOME_WAIT_ALLOWANCE_MILLIS = 10_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 250L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
        const val ZIP_DOS_EPOCH_MILLIS = 315_532_800_000L

        fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
