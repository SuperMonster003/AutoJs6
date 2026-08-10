package org.autojs.autojs.core.plugin.dex

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexClassLoader
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerCapabilities
import org.autojs.plugin.dexcompiler.api.DexCompilerCodec
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerFamily
import org.autojs.plugin.dexcompiler.api.DexCompilerInfo
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerNegotiation
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerValidation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/**
 * One opt-in, real-provider row for the append-only R1 device matrix.
 *
 * This test intentionally owns no repository/APK/ADB identity claims. The matrix runner adds those
 * host-side facts to the canonical receipt. The successful instrumentation status contains the
 * device-observable input, request, provider, output, execution, and resource-cleanup fields under
 * [STATUS_KEY]. Ordinary instrumentation runs skip the test unless [ARG_ENABLED] is exactly true.
 */
class DexCompilerR1MatrixAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun canonicalRealProviderCompilesValidatesAndExecutesDeterministicJava8Jar() {
        requireExplicitOptIn()

        val program = deterministicProgramJar()
        assertTrue(
            "The R1 Java 8 corpus generator is not byte deterministic",
            program.contentEquals(deterministicProgramJar()),
        )
        val inputSha256 = r1Sha256(program)
        val fdCountBefore = openFileDescriptorCount()
        val transientBefore = transientArtifactNames()
        val threadPrefix = "dex-r1-matrix-${UUID.randomUUID()}"
        val trackingTransport = TrackingTransport(AndroidDexCompilerExperimentalTransport(context))
        val worker = boundedExecutor("$threadPrefix-control", queueCapacity = 64)
        val blocking = ThreadPoolExecutor(
            0,
            2,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            namedThreadFactory("$threadPrefix-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val drain = boundedExecutor("$threadPrefix-drain", queueCapacity = 1)
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("$threadPrefix-deadline"),
        )
        val executors = listOf<ExecutorService>(worker, blocking, drain, scheduler)
        val store = AndroidDexCompilerArtifactStore(context, drain)
        val dispatchCommitted = AtomicBoolean(false)
        val harness = DexCompilerExperimentalHarness(
            transport = trackingTransport,
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asDeadlineScheduler(),
            threadProbe = DexCompilerThreadProbe { Looper.myLooper() == Looper.getMainLooper() },
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            hostRuntimeApi = Build.VERSION.SDK_INT,
        )

        var exactArtifact: DexCompilerVerifiedArtifact? = null
        var exactCacheKey: String? = null
        var evidence: MatrixEvidence? = null
        var cleanup: CleanupEvidence? = null
        var primaryFailure: Throwable? = null
        try {
            val identity = requirePinnedRealProvider(trackingTransport)
            val handshake = readAndValidateHandshake(trackingTransport, identity)
            val attempt = runAttempt(
                harness = harness,
                store = store,
                identity = identity,
                handshake = handshake,
                program = program,
                dispatchObserver = DexCompilerDispatchObserver { dispatchCommitted.set(true) },
            )
            assertTrue("The real provider dispatch boundary was not committed", dispatchCommitted.get())
            val published = attempt.outcome
            exactArtifact = published.artifact
            exactCacheKey = published.cacheKeySha256

            assertEquals(identity, published.identity)
            assertEquals(inputSha256, attempt.request.programSha256.toHexString())
            assertEquals(Build.VERSION.SDK_INT, attempt.request.minApi)
            assertEquals(DexCompilerMode.RELEASE, attempt.request.mode)
            assertEquals(DexCompilerOutputFormat.DEX_ZIP, attempt.request.outputFormat)
            assertEquals(handshake.protocol, attempt.request.protocolVersion)
            assertEquals(
                handshake.capabilities.runtimeLibraryFingerprint,
                attempt.request.expectedRuntimeLibraryFingerprint,
            )
            assertEquals(handshake.capabilities.compilerFamily, published.result.compilerFamily)
            assertEquals(handshake.capabilities.compilerVersion, published.result.compilerVersion)
            assertEquals(
                handshake.capabilities.runtimeLibraryFingerprint,
                published.result.runtimeLibraryFingerprint,
            )
            assertEquals(attempt.request.minApi, published.result.minApi)
            assertEquals(attempt.request.mode, published.result.mode)

            val outputBytes = readExactArtifact(published.artifact)
            val outputSha256 = r1Sha256(outputBytes)
            assertEquals(published.artifact.sizeBytes, outputBytes.size.toLong())
            assertEquals(published.artifact.outputSha256, outputSha256)
            assertEquals(published.result.outputSizeBytes, outputBytes.size.toLong())
            assertEquals(published.result.outputSha256.toHexString(), outputSha256)

            val maximumOutputBytes = minOf(
                attempt.request.maxOutputBytes,
                handshake.capabilities.limits.maxOutputBytes,
                DexCompilerExperimentalHostLimits.MAX_IN_MEMORY_OUTPUT_BYTES,
            )
            val secondValidation = DexIndexedZipValidator.validate(
                outputBytes,
                DexIndexedZipLimits(
                    maxContainerBytes = maximumOutputBytes,
                    maxDexEntries = minOf(
                        handshake.capabilities.limits.maxDexEntries,
                        DexCompilerContract.MAX_DEX_ENTRIES,
                    ),
                    maxDexEntryBytes = maximumOutputBytes,
                    maxTotalDexBytes = maximumOutputBytes,
                    allowedDexVersions = dexVersionsCompatibleWithMinApi(attempt.request.minApi),
                ),
            )
            assertEquals(published.validation, secondValidation)
            assertEquals(published.result.dexEntryCount, secondValidation.dexEntryCount)

            val entryManifest = entryManifest(outputBytes)
            assertEquals(secondValidation.dexEntryCount, entryManifest.dexEntryCount)
            val execution = loadAndExecute(outputBytes)
            assertEquals(EXPECTED_RESULT, execution.result)
            assertEquals(
                "Provider identity drifted after the real D8 publication",
                identity,
                trackingTransport.inspectExact(REAL_COMPONENT)?.pin(),
            )

            evidence = MatrixEvidence(
                inputSizeBytes = program.size.toLong(),
                inputSha256 = inputSha256,
                request = attempt.request,
                identity = identity,
                handshake = handshake,
                outputSizeBytes = outputBytes.size.toLong(),
                outputSha256 = outputSha256,
                dexEntryCount = secondValidation.dexEntryCount,
                entryManifestSha256 = entryManifest.sha256,
                execution = execution,
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            var generationInvalidated = exactArtifact == null
            var exactGenerationAbsent = exactCacheKey == null
            runCatching {
                exactArtifact?.let { artifact ->
                    assertTrue("Unable to invalidate the exact R1 matrix generation", artifact.invalidate())
                    generationInvalidated = true
                    assertTrue(
                        "The invalidated R1 matrix generation can still be opened",
                        artifact.openRead() == null,
                    )
                }
                exactCacheKey?.let { key ->
                    assertTrue(
                        "The exact R1 matrix cache generation left files behind",
                        exactCacheFiles(key).isEmpty(),
                    )
                    exactGenerationAbsent = true
                }
            }.exceptionOrNull()?.let(cleanupFailures::add)

            executors.forEach(ExecutorService::shutdownNow)
            var executorsTerminated = true
            executors.forEach { executor ->
                runCatching {
                    assertTrue(
                        "An R1 matrix executor did not terminate",
                        executor.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                    )
                }.onFailure {
                    executorsTerminated = false
                    cleanupFailures += it
                }
            }

            var bindingsClosed = false
            runCatching {
                assertTrue("The R1 matrix test did not open both expected exact bindings", trackingTransport.opened >= 2)
                assertEquals(
                    "An exact DEX compiler binding was not closed",
                    trackingTransport.opened,
                    trackingTransport.closed,
                )
                bindingsClosed = true
            }.exceptionOrNull()?.let(cleanupFailures::add)

            var ownedThreadsAlive = -1
            runCatching {
                ownedThreadsAlive = ownedLiveThreadCount(threadPrefix)
                assertEquals("An R1 matrix-owned thread remains alive", 0, ownedThreadsAlive)
            }.exceptionOrNull()?.let(cleanupFailures::add)

            var workspaceRestored = false
            runCatching {
                val transientAfter = transientArtifactNames()
                assertTrue(
                    "The R1 matrix test left a new transient artifact: ${transientAfter - transientBefore}",
                    transientAfter.subtract(transientBefore).isEmpty(),
                )
                workspaceRestored = true
            }.exceptionOrNull()?.let(cleanupFailures::add)

            var fdCountAfter = -1
            runCatching {
                fdCountAfter = awaitFileDescriptorCountAtMost(fdCountBefore)
                assertTrue(
                    "The R1 matrix test leaked a host file descriptor: before=$fdCountBefore after=$fdCountAfter",
                    fdCountAfter <= fdCountBefore,
                )
            }.exceptionOrNull()?.let(cleanupFailures::add)

            if (cleanupFailures.isEmpty()) {
                cleanup = CleanupEvidence(
                    generationInvalidated = generationInvalidated,
                    exactGenerationAbsent = exactGenerationAbsent,
                    executorsTerminated = executorsTerminated,
                    bindingsOpened = trackingTransport.opened,
                    bindingsClosed = trackingTransport.closed,
                    allBindingsClosed = bindingsClosed,
                    ownedThreadsAlive = ownedThreadsAlive,
                    workspaceRestored = workspaceRestored,
                    fdCountBefore = fdCountBefore,
                    fdCountAfter = fdCountAfter,
                )
            } else {
                val original = primaryFailure
                if (original != null) {
                    cleanupFailures.forEach(original::addSuppressed)
                } else {
                    val cleanupFailure = cleanupFailures.removeAt(0)
                    cleanupFailures.forEach(cleanupFailure::addSuppressed)
                    throw cleanupFailure
                }
            }
        }

        val completedEvidence = checkNotNull(evidence) { "R1 matrix evidence was not produced" }
        val completedCleanup = checkNotNull(cleanup) { "R1 matrix cleanup evidence was not produced" }
        instrumentation.sendStatus(
            STATUS_CODE,
            Bundle().apply {
                putString(STATUS_KEY, completedEvidence.toJson(completedCleanup).toString())
            },
        )
    }

    private fun requireExplicitOptIn() {
        assumeTrue(
            "R1 matrix instrumentation is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assertTrue("R1 DEX compiler matrix requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertTrue("R1 DEX compiler matrix is defined only through API 36", Build.VERSION.SDK_INT <= 36)
        assertEquals(
            "The real plugin authorizes only the canonical AutoJs6 host package",
            HOST_PACKAGE,
            context.packageName,
        )
    }

    private fun requirePinnedRealProvider(
        transport: DexCompilerExperimentalTransport,
    ): DexCompilerPinnedIdentity {
        val packageManager = context.packageManager
        val packageInfo = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(REAL_PLUGIN_PACKAGE, 0)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real DEX compiler package is not installed: $REAL_PLUGIN_PACKAGE").also {
                it.initCause(error)
            }
        }
        assertEquals(
            "The real DEX compiler and host must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        val candidates = transport.discoverExactAction(DexCompilerContract.SERVICE_ACTION)
        val packageCandidates = candidates.filter { candidate ->
            candidate.component.packageName == REAL_PLUGIN_PACKAGE
        }
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
        assertTrue("The real DEX compiler signer differs from the host", candidate.sameSignerAsHost)
        assertEquals(requireNotNull(packageInfo.applicationInfo).uid, candidate.packageUid)

        val decision = DexCompilerOptInPolicy.decide(
            options = DexCompilerOptInOptions(enabled = true, selectedComponent = REAL_COMPONENT),
            releaseBuild = !BuildConfig.DEBUG,
            discoverCandidates = { candidates },
        )
        assertTrue("The exact real DEX compiler was rejected by the opt-in gate: $decision", decision is DexCompilerOptInDecision.Selected)
        val identity = (decision as DexCompilerOptInDecision.Selected).identity
        assertEquals(REAL_COMPONENT, identity.component)
        assertEquals(
            "Exact reinspection changed the real DEX compiler identity",
            identity,
            transport.inspectExact(REAL_COMPONENT)?.pin(),
        )
        return identity
    }

    private fun readAndValidateHandshake(
        transport: DexCompilerExperimentalTransport,
        identity: DexCompilerPinnedIdentity,
    ): HandshakeEvidence {
        val connected = CountDownLatch(1)
        val connection = AtomicReference<DexCompilerRemoteConnection?>()
        val failure = AtomicReference<Throwable?>()
        val binding = transport.bindExact(identity, object : DexCompilerBindingCallback {
            override fun onConnected(value: DexCompilerRemoteConnection) {
                if (!connection.compareAndSet(null, value)) {
                    failure.compareAndSet(null, IllegalStateException("Provider connected more than once"))
                }
                connected.countDown()
            }

            override fun onBindingFailure(cause: Throwable?) {
                failure.compareAndSet(null, cause ?: IllegalStateException("Provider binding failed"))
                connected.countDown()
            }

            override fun onBinderDeath() {
                failure.compareAndSet(null, IllegalStateException("Provider Binder died during handshake"))
                connected.countDown()
            }
        })
        try {
            assertTrue(
                "The exact real provider did not connect for the R1 handshake",
                connected.await(HANDSHAKE_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            failure.get()?.let { error ->
                throw AssertionError("The exact real provider handshake failed").also {
                    it.initCause(error)
                }
            }
            val remote = checkNotNull(connection.get()) { "The real provider connected without a remote interface" }
            val encodedInfo = remote.getCompilerInfo()
            val encodedCapabilities = remote.getCapabilities()
            assertTrue(
                "Encoded compiler info exceeds the protocol metadata limit",
                encodedInfo.isNotEmpty() && encodedInfo.size <= DexCompilerContract.MAX_METADATA_BYTES,
            )
            assertTrue(
                "Encoded compiler capabilities exceed the protocol metadata limit",
                encodedCapabilities.isNotEmpty() && encodedCapabilities.size <= DexCompilerContract.MAX_METADATA_BYTES,
            )
            val info = DexCompilerCodec.decodeInfo(encodedInfo)
            val capabilities = DexCompilerCodec.decodeCapabilities(encodedCapabilities)
            DexCompilerValidation.validateInfo(info)
            DexCompilerValidation.validateCapabilities(capabilities)
            val protocol = DexCompilerNegotiation.requireNegotiated(info)
            val minimumHostVersionCode = info.minHostVersionCode
            val maximumHostVersionCode = info.maxHostVersionCode
            assertEquals(identity.versionCode, info.providerVersionCode)
            assertTrue(
                "Host version is below the provider minimum",
                minimumHostVersionCode == null || BuildConfig.VERSION_CODE.toLong() >= minimumHostVersionCode,
            )
            assertTrue(
                "Host version exceeds the provider maximum",
                maximumHostVersionCode == null || BuildConfig.VERSION_CODE.toLong() <= maximumHostVersionCode,
            )
            assertTrue(Build.VERSION.SDK_INT in capabilities.minApi..capabilities.maxApi)
            assertTrue(DexCompilerInputFormat.JAR in capabilities.inputFormats)
            assertTrue(DexCompilerOutputFormat.DEX_ZIP in capabilities.outputFormats)
            assertTrue(DexCompilerMode.RELEASE in capabilities.modes)
            assertEquals(DexCompilerFamily.D8, capabilities.compilerFamily)
            assertTrue(capabilities.supportsMultiDex)

            @Suppress("DEPRECATION")
            val packageVersionName = context.packageManager
                .getPackageInfo(REAL_PLUGIN_PACKAGE, 0)
                .versionName
            assertEquals(packageVersionName, info.providerVersionName)
            return HandshakeEvidence(
                info = info,
                capabilities = capabilities,
                protocol = protocol,
                capabilityDigestSha256 = r1Sha256(encodedCapabilities),
            )
        } finally {
            binding.close()
        }
    }

    private fun runAttempt(
        harness: DexCompilerExperimentalHarness,
        store: AndroidDexCompilerArtifactStore,
        identity: DexCompilerPinnedIdentity,
        handshake: HandshakeEvidence,
        program: ByteArray,
        dispatchObserver: DexCompilerDispatchObserver,
    ): PublishedAttempt {
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
        val effectiveRequest = request.copy(
            protocolVersion = handshake.protocol,
            expectedRuntimeLibraryFingerprint = handshake.capabilities.runtimeLibraryFingerprint,
        )
        DexCompilerValidation.validateRequestAgainst(
            effectiveRequest,
            handshake.capabilities,
            handshake.protocol,
        )
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
                    dispatchObserver = dispatchObserver,
                ),
            ) { value ->
                if (outcome.compareAndSet(null, value)) terminal.countDown()
            }
            assertTrue(
                "The real DEX compiler did not reach a terminal R1 matrix outcome",
                terminal.await(request.timeoutMillis + OUTCOME_WAIT_ALLOWANCE_MILLIS, TimeUnit.MILLISECONDS),
            )
            val terminalOutcome = checkNotNull(outcome.get()) { "The real provider returned no terminal outcome" }
            assertTrue("The real provider did not publish a fresh DEX generation: $terminalOutcome", terminalOutcome is DexCompilerExperimentalOutcome.Published)
            return PublishedAttempt(
                request = effectiveRequest,
                outcome = terminalOutcome as DexCompilerExperimentalOutcome.Published,
            )
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

    private fun readExactArtifact(artifact: DexCompilerVerifiedArtifact): ByteArray {
        assertTrue("Published artifact is too large to materialize", artifact.sizeBytes <= Int.MAX_VALUE)
        val bytes = ByteArray(artifact.sizeBytes.toInt())
        val input = checkNotNull(artifact.openRead()) { "Published artifact capability could not be opened" }
        input.use { source ->
            var offset = 0
            while (offset < bytes.size) {
                val count = source.read(bytes, offset, bytes.size - offset)
                if (count < 0) throw AssertionError("Published artifact ended before its declared size")
                if (count > 0) offset += count
            }
            assertEquals("Published artifact exceeded its declared size", -1, source.read())
        }
        return bytes
    }

    private fun entryManifest(output: ByteArray): EntryManifestEvidence {
        val manifestDigest = MessageDigest.getInstance("SHA-256")
        manifestDigest.update(ENTRY_MANIFEST_DOMAIN)
        var index = 0
        ZipInputStream(ByteArrayInputStream(output)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val expectedName = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
                assertEquals("Validated DEX entries are not canonical and contiguous", expectedName, entry.name)
                val contentDigest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    size = Math.addExact(size, count.toLong())
                    contentDigest.update(buffer, 0, count)
                }
                val name = entry.name.toByteArray(StandardCharsets.UTF_8)
                manifestDigest.update(intBytes(index))
                manifestDigest.update(intBytes(name.size))
                manifestDigest.update(name)
                manifestDigest.update(longBytes(size))
                manifestDigest.update(contentDigest.digest())
                zip.closeEntry()
                index++
            }
        }
        assertTrue("The validated DEX ZIP has no entries", index > 0)
        return EntryManifestEvidence(index, r1LowerHex(manifestDigest.digest()))
    }

    private fun loadAndExecute(output: ByteArray): ExecutionEvidence {
        val token = UUID.randomUUID()
        val optimized = File(context.codeCacheDir, "dex-r1-matrix-$token")
        val loadable = File(context.codeCacheDir, "dex-r1-matrix-$token.dex.zip")
        assertTrue("Unable to create the R1 matrix optimization directory", optimized.mkdirs())
        var primaryFailure: Throwable? = null
        try {
            FileOutputStream(loadable).use { destination ->
                // Android 14 requires dynamically loaded code to be read-only. The already-open
                // descriptor remains the sole writer until fsync/close completes.
                assertTrue("Unable to make the R1 matrix DEX read-only", loadable.setReadOnly())
                destination.write(output)
                destination.fd.sync()
            }
            val fixture = DexClassLoader(
                loadable.absolutePath,
                optimized.absolutePath,
                null,
                javaClass.classLoader,
            ).loadClass(FIXTURE_CLASS_NAME)
            assertEquals(FIXTURE_CLASS_NAME, fixture.name)
            val result = fixture.getDeclaredMethod(FIXTURE_METHOD_NAME).invoke(null) as? Int
                ?: throw AssertionError("R1 matrix fixture returned a non-integer result")
            assertEquals(EXPECTED_RESULT, result)
            val resultText = "$EXECUTION_RESULT_DOMAIN$FIXTURE_CLASS_NAME#$FIXTURE_METHOD_NAME()I=$result"
            return ExecutionEvidence(
                result = result,
                resultSha256 = r1Sha256(resultText.toByteArray(StandardCharsets.UTF_8)),
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailure = runCatching {
                assertTrue("Unable to delete the R1 matrix loadable DEX", !loadable.exists() || loadable.delete())
                assertTrue(
                    "Unable to delete the R1 matrix optimization directory",
                    !optimized.exists() || optimized.deleteRecursively(),
                )
            }.exceptionOrNull()
            if (cleanupFailure != null) {
                val original = primaryFailure
                if (original != null) original.addSuppressed(cleanupFailure) else throw cleanupFailure
            }
        }
    }

    /** A platform-independent, stored ZIP containing one deterministic Java 8 class file. */
    private fun deterministicProgramJar(): ByteArray {
        val name = FIXTURE_CLASS_ENTRY.toByteArray(StandardCharsets.US_ASCII)
        val content = fixtureClassBytes()
        val crc = CRC32().apply { update(content) }.value
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeLeInt(ZIP_LOCAL_FILE_HEADER_SIGNATURE)
                output.writeLeShort(ZIP_VERSION_20)
                output.writeLeShort(0)
                output.writeLeShort(ZIP_STORED)
                output.writeLeShort(ZIP_DOS_TIME)
                output.writeLeShort(ZIP_DOS_DATE_1980_01_01)
                output.writeLeInt(crc)
                output.writeLeInt(content.size.toLong())
                output.writeLeInt(content.size.toLong())
                output.writeLeShort(name.size)
                output.writeLeShort(0)
                output.write(name)
                output.write(content)

                val centralOffset = bytes.size()
                output.writeLeInt(ZIP_CENTRAL_DIRECTORY_SIGNATURE)
                output.writeLeShort(ZIP_VERSION_20)
                output.writeLeShort(ZIP_VERSION_20)
                output.writeLeShort(0)
                output.writeLeShort(ZIP_STORED)
                output.writeLeShort(ZIP_DOS_TIME)
                output.writeLeShort(ZIP_DOS_DATE_1980_01_01)
                output.writeLeInt(crc)
                output.writeLeInt(content.size.toLong())
                output.writeLeInt(content.size.toLong())
                output.writeLeShort(name.size)
                output.writeLeShort(0)
                output.writeLeShort(0)
                output.writeLeShort(0)
                output.writeLeShort(0)
                output.writeLeInt(0)
                output.writeLeInt(0)
                output.write(name)
                val centralSize = bytes.size() - centralOffset

                output.writeLeInt(ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE)
                output.writeLeShort(0)
                output.writeLeShort(0)
                output.writeLeShort(1)
                output.writeLeShort(1)
                output.writeLeInt(centralSize.toLong())
                output.writeLeInt(centralOffset.toLong())
                output.writeLeShort(0)
            }
            bytes.toByteArray()
        }
    }

    /** Java 8: public final class R1MatrixFixture { public static int answer() { return 42; } }. */
    private fun fixtureClassBytes(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(0xcafebabe.toInt())
            output.writeShort(0)
            output.writeShort(52)
            output.writeShort(14)
            output.writeUtf8Constant(FIXTURE_INTERNAL_NAME) // #1
            output.writeByte(7); output.writeShort(1) // #2 Class
            output.writeUtf8Constant("java/lang/Object") // #3
            output.writeByte(7); output.writeShort(3) // #4 Class
            output.writeUtf8Constant("<init>") // #5
            output.writeUtf8Constant("()V") // #6
            output.writeUtf8Constant("Code") // #7
            output.writeByte(12); output.writeShort(5); output.writeShort(6) // #8 NameAndType
            output.writeByte(10); output.writeShort(4); output.writeShort(8) // #9 Methodref
            output.writeUtf8Constant(FIXTURE_METHOD_NAME) // #10
            output.writeUtf8Constant("()I") // #11
            output.writeUtf8Constant("SourceFile") // #12
            output.writeUtf8Constant("R1MatrixFixture.java") // #13

            output.writeShort(0x0031) // public final super
            output.writeShort(2)
            output.writeShort(4)
            output.writeShort(0)
            output.writeShort(0)
            output.writeShort(2)

            output.writeShort(0x0001)
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

            output.writeShort(0x0009)
            output.writeShort(10)
            output.writeShort(11)
            output.writeShort(1)
            output.writeShort(7)
            output.writeInt(15)
            output.writeShort(1)
            output.writeShort(0)
            output.writeInt(3)
            output.write(byteArrayOf(0x10, 0x2a, 0xac.toByte()))
            output.writeShort(0)
            output.writeShort(0)

            output.writeShort(1)
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

    private fun DataOutputStream.writeLeShort(value: Int) {
        require(value in 0..0xffff)
        writeByte(value and 0xff)
        writeByte((value ushr 8) and 0xff)
    }

    private fun DataOutputStream.writeLeInt(value: Long) {
        require(value in 0L..0xffff_ffffL)
        repeat(Int.SIZE_BYTES) { index ->
            writeByte(((value ushr (index * Byte.SIZE_BITS)) and 0xffL).toInt())
        }
    }

    private fun exactCacheFiles(cacheKeySha256: String): List<File> = artifactRoot().listFiles().orEmpty()
        .filter { file -> file.name.startsWith("$cacheKeySha256.") }

    private fun transientArtifactNames(): Set<String> = artifactRoot().listFiles().orEmpty()
        .filter { file -> DexCompilerArtifactRecoveryPolicy.isTransientArtifact(file.name) }
        .mapTo(linkedSetOf(), File::getName)

    private fun artifactRoot(): File = File(context.noBackupFilesDir, ARTIFACT_DIRECTORY_NAME)

    private fun openFileDescriptorCount(): Int = checkNotNull(File("/proc/self/fd").list()) {
        "Unable to inspect the host process file descriptors"
    }.size

    private fun awaitFileDescriptorCountAtMost(limit: Int): Int {
        val deadline = SystemClock.uptimeMillis() + FD_SETTLE_WAIT_MILLIS
        var count = openFileDescriptorCount()
        while (count > limit && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(FD_SETTLE_POLL_MILLIS)
            count = openFileDescriptorCount()
        }
        return count
    }

    private fun ownedLiveThreadCount(prefix: String): Int = Thread.getAllStackTraces().keys.count { thread ->
        thread.isAlive && thread.name.startsWith(prefix)
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

    private fun namedThreadFactory(prefix: String): ThreadFactory {
        val sequence = AtomicInteger()
        return ThreadFactory { runnable ->
            Thread(runnable, "$prefix-${sequence.incrementAndGet()}").apply { isDaemon = true }
        }
    }

    private fun ScheduledExecutorService.asDeadlineScheduler(): DexCompilerDeadlineScheduler =
        DexCompilerDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(value)
        .array()

    private fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value)
        .array()

    private data class HandshakeEvidence(
        val info: DexCompilerInfo,
        val capabilities: DexCompilerCapabilities,
        val protocol: org.autojs.plugin.dexcompiler.api.DexProtocolVersion,
        val capabilityDigestSha256: String,
    )

    private data class PublishedAttempt(
        val request: DexCompileRequest,
        val outcome: DexCompilerExperimentalOutcome.Published,
    )

    private data class EntryManifestEvidence(
        val dexEntryCount: Int,
        val sha256: String,
    )

    private data class ExecutionEvidence(
        val result: Int,
        val resultSha256: String,
    )

    private data class CleanupEvidence(
        val generationInvalidated: Boolean,
        val exactGenerationAbsent: Boolean,
        val executorsTerminated: Boolean,
        val bindingsOpened: Int,
        val bindingsClosed: Int,
        val allBindingsClosed: Boolean,
        val ownedThreadsAlive: Int,
        val workspaceRestored: Boolean,
        val fdCountBefore: Int,
        val fdCountAfter: Int,
    )

    private data class MatrixEvidence(
        val inputSizeBytes: Long,
        val inputSha256: String,
        val request: DexCompileRequest,
        val identity: DexCompilerPinnedIdentity,
        val handshake: HandshakeEvidence,
        val outputSizeBytes: Long,
        val outputSha256: String,
        val dexEntryCount: Int,
        val entryManifestSha256: String,
        val execution: ExecutionEvidence,
    ) {
        fun toJson(cleanup: CleanupEvidence): JSONObject = JSONObject()
            .put("schemaVersion", STATUS_SCHEMA_VERSION)
            .put("corpusId", CORPUS_ID)
            .put("input", JSONObject()
                .put("corpusId", CORPUS_ID)
                .put("sizeBytes", inputSizeBytes)
                .put("sha256", inputSha256))
            .put("request", JSONObject()
                .put("compilerPath", compilerPath())
                .put("mode", request.mode.name)
                .put("minApi", request.minApi)
                .put("multiDexExpected", false)
                .put("outputFormat", request.outputFormat.name))
            .put("provider", JSONObject()
                .put("kind", "REAL")
                .put("component", ComponentName(
                    identity.component.packageName,
                    identity.component.className,
                ).flattenToShortString())
                .put("packageName", identity.component.packageName)
                .put("uid", identity.packageUid)
                .put("versionName", handshake.info.providerVersionName)
                .put("versionCode", handshake.info.providerVersionCode)
                .put("signerCertificateSha256", JSONArray().apply {
                    identity.signerSha256.forEach { signer -> put(signer) }
                })
                .put("protocolMajor", handshake.protocol.major)
                .put("protocolMinor", handshake.protocol.minor)
                .put(
                    "runtimeLibraryFingerprintSha256",
                    handshake.capabilities.runtimeLibraryFingerprint.toHexString(),
                )
                .put("compilerFamily", handshake.capabilities.compilerFamily.name)
                .put("compilerVersion", handshake.capabilities.compilerVersion)
                .put("capabilityDigestSha256", handshake.capabilityDigestSha256))
            .put("output", JSONObject()
                .put("present", true)
                .put("sizeBytes", outputSizeBytes)
                .put("sha256", outputSha256)
                .put("dexEntryCount", dexEntryCount)
                .put("entryManifestSha256", entryManifestSha256))
            .put("execution", JSONObject()
                .put("attempted", true)
                .put("passed", execution.result == EXPECTED_RESULT)
                .put("className", FIXTURE_CLASS_NAME)
                .put("resultSha256", execution.resultSha256))
            .put("device", JSONObject()
                .put("apiLevel", Build.VERSION.SDK_INT)
                .put("abi", Build.SUPPORTED_ABIS.first())
                .put("allAbis", JSONArray().apply {
                    Build.SUPPORTED_ABIS.forEach { abi -> put(abi) }
                })
                .put("buildFingerprintSha256", r1Sha256(Build.FINGERPRINT.toByteArray(StandardCharsets.UTF_8))))
            .put("cleanup", JSONObject()
                .put("generationInvalidated", cleanup.generationInvalidated)
                .put("exactGenerationAbsent", cleanup.exactGenerationAbsent)
                .put("executorsTerminated", cleanup.executorsTerminated)
                .put("bindingsOpened", cleanup.bindingsOpened)
                .put("bindingsClosed", cleanup.bindingsClosed)
                .put("allBindingsClosed", cleanup.allBindingsClosed)
                .put("ownedThreadsAlive", cleanup.ownedThreadsAlive)
                .put("workspaceRestored", cleanup.workspaceRestored)
                .put("fdCountBefore", cleanup.fdCountBefore)
                .put("fdCountAfter", cleanup.fdCountAfter))

        companion object {
            private fun compilerPath(): String = if (Build.VERSION.SDK_INT <= 25) {
                "D8_CLI_FALLBACK"
            } else {
                "D8_COMMAND"
            }
        }
    }

    private class TrackingTransport(
        private val delegate: DexCompilerExperimentalTransport,
    ) : DexCompilerExperimentalTransport {
        private val openedCount = AtomicInteger()
        private val closedCount = AtomicInteger()

        val opened: Int
            get() = openedCount.get()

        val closed: Int
            get() = closedCount.get()

        override fun discoverExactAction(action: String): List<DexCompilerServiceCandidate> =
            delegate.discoverExactAction(action)

        override fun inspectExact(component: DexCompilerComponent): DexCompilerServiceCandidate? =
            delegate.inspectExact(component)

        override fun bindExact(
            identity: DexCompilerPinnedIdentity,
            callback: DexCompilerBindingCallback,
        ): DexCompilerBinding {
            val binding = delegate.bindExact(identity, callback)
            openedCount.incrementAndGet()
            return object : DexCompilerBinding {
                private val closed = AtomicBoolean(false)

                override fun close() {
                    if (!closed.compareAndSet(false, true)) return
                    try {
                        binding.close()
                    } finally {
                        closedCount.incrementAndGet()
                    }
                }
            }
        }
    }

    private companion object {
        const val ARG_ENABLED = "autojs.dexCompiler.r1Matrix.enabled"
        const val STATUS_KEY = "autojs.dex.r1.matrix.evidence"
        const val STATUS_CODE = 0
        const val STATUS_SCHEMA_VERSION = "autojs6.dex.r1.matrix-instrumentation-evidence/v1"
        const val CORPUS_ID = "r1-real-d8-load-v1"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.dexcompiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.DexCompilerService"
        val REAL_COMPONENT = DexCompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)

        const val FIXTURE_INTERNAL_NAME = "org/autojs/fixture/dexcompiler/R1MatrixFixture"
        const val FIXTURE_CLASS_ENTRY = "$FIXTURE_INTERNAL_NAME.class"
        const val FIXTURE_CLASS_NAME = "org.autojs.fixture.dexcompiler.R1MatrixFixture"
        const val FIXTURE_METHOD_NAME = "answer"
        const val EXPECTED_RESULT = 42
        const val EXECUTION_RESULT_DOMAIN = "AutoJs6:DexCompilerR1ExecutionResult:v1\u0000"
        val ENTRY_MANIFEST_DOMAIN =
            "AutoJs6:DexCompilerR1EntryManifest:v1\u0000".toByteArray(StandardCharsets.UTF_8)

        const val ARTIFACT_DIRECTORY_NAME = "dex-compiler-experimental"
        const val HANDSHAKE_WAIT_MILLIS = 15_000L
        const val OUTCOME_WAIT_ALLOWANCE_MILLIS = 30_000L
        const val TERMINAL_CLEANUP_WAIT_MILLIS = 5_000L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 3_000L
        const val FD_SETTLE_WAIT_MILLIS = 3_000L
        const val FD_SETTLE_POLL_MILLIS = 25L

        const val ZIP_LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
        const val ZIP_CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
        const val ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
        const val ZIP_VERSION_20 = 20
        const val ZIP_STORED = 0
        const val ZIP_DOS_TIME = 0
        const val ZIP_DOS_DATE_1980_01_01 = 0x21

    }
}

private fun r1Sha256(bytes: ByteArray): String =
    r1LowerHex(MessageDigest.getInstance("SHA-256").digest(bytes))

private fun r1LowerHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    bytes.forEach { byte ->
        append("0123456789abcdef"[(byte.toInt() ushr 4) and 0x0f])
        append("0123456789abcdef"[byte.toInt() and 0x0f])
    }
}
