package org.autojs.autojs.core.plugin.jvm

import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs.script.JvmSourceKind
import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmCancellationReason
import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmDexLoaderKind
import org.autojs.plugin.jvmsource.api.JvmIsolationCapability
import org.autojs.plugin.jvmsource.api.JvmObservationCacheMissReason
import org.autojs.plugin.jvmsource.api.JvmObservationCacheOutcome
import org.autojs.plugin.jvmsource.api.JvmObservationPhase
import org.autojs.plugin.jvmsource.api.JvmObservationProcess
import org.autojs.plugin.jvmsource.api.JvmObservationStartProfile
import org.autojs.plugin.jvmsource.api.JvmProviderInfo
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceCapabilities
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceCompilerFamily
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceContractException
import org.autojs.plugin.jvmsource.api.JvmSourceCancellation
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.autojs.plugin.jvmsource.api.JvmSourceError
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourceNegotiation
import org.autojs.plugin.jvmsource.api.JvmSourceObservation
import org.autojs.plugin.jvmsource.api.JvmSourcePayloadKind
import org.autojs.plugin.jvmsource.api.JvmSourceResourceObservation
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.api.JvmSourceResult
import org.autojs.plugin.jvmsource.api.JvmSourceStarted
import org.autojs.plugin.jvmsource.api.JvmToolchainFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

class RemoteJvmSourceHostTest {
    @Test
    fun sourcePackageRequestBindsCanonicalArchiveCountAndContentClaims() {
        val directory = Files.createTempDirectory("jvm-source-package-request-test").toFile()
        val packageDirectory = directory.resolve("demo").apply { check(mkdir()) }
        val entry = packageDirectory.resolve("Main.java").apply {
            writeText("package demo; public final class Main {}")
        }
        packageDirectory.resolve("Helper.java").writeText("package demo; final class Helper {}")
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport()
        try {
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(entry.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )

            host.execute(
                JvmSourceExplicitRunner.createAuthorizedSourcePackage(directory, entry),
                JvmSourceExecutionControl(),
            )

            val request = requireNotNull(transport.lastRequest)
            assertEquals(JvmSourcePayloadKind.SOURCE_ARCHIVE, request.sourcePayloadKind)
            assertEquals(2, request.sourceFileCount)
            assertEquals(
                entry.readBytes().size.toLong() +
                    packageDirectory.resolve("Helper.java").readBytes().size.toLong(),
                request.sourceContentBytes,
            )
            assertTrue(request.sourceSizeBytes > request.sourceContentBytes)
            assertEquals("Main.java", request.sourceFileName)
            assertEquals("demo.Main", request.entryClassName)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun sourcePackageRequestCarriesCallerSelectedNonMainEntry() {
        val directory = Files.createTempDirectory("jvm-source-package-entry-test").toFile()
        val packageDirectory = directory.resolve("demo").apply { check(mkdir()) }
        val entry = packageDirectory.resolve("ScriptEntry.java").apply {
            writeText("package demo; public final class ScriptEntry {}")
        }
        packageDirectory.resolve("Helper.java").writeText("package demo; final class Helper {}")
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport()
        try {
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(entry.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )

            val result = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSourcePackage(
                    directory,
                    entry,
                    "ScriptEntry",
                ),
                JvmSourceExecutionControl(),
            )

            assertEquals("ScriptEntry.java", transport.lastRequest?.sourceFileName)
            assertEquals("demo.ScriptEntry", transport.lastRequest?.entryClassName)
            assertEquals("ScriptEntry.java", result.sourceFileName)
            assertEquals("demo.ScriptEntry", result.entryClassName)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun callerSelectedNonMainEntryFlowsThroughSnapshotRequestAndResult() {
        val directory = Files.createTempDirectory("jvm-source-non-main-request-test").toFile()
        val sourceFile = File(directory, "arbitrary-physical-name.java").apply {
            writeText("package demo.entries; public final class ScriptEntry {}")
        }
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport()
        try {
            val host = newEnabledHost(transport, sourceFile, executor)

            val result = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile, "ScriptEntry"),
                JvmSourceExecutionControl(),
            )

            val request = requireNotNull(transport.lastRequest)
            assertEquals("ScriptEntry.java", request.sourceFileName)
            assertEquals("demo.entries.ScriptEntry", request.entryClassName)
            assertEquals("ScriptEntry.java", result.sourceFileName)
            assertEquals("demo.entries.ScriptEntry", result.entryClassName)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidArgumentJsonFailsBeforeProviderInspectionOrBinding() {
        val directory = Files.createTempDirectory("jvm-source-invalid-args-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport()
        try {
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )

            assertThrows(JvmSourceContractException::class.java) {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                    argsJson = "{\"value\":NaN}",
                )
            }
            assertEquals(0, transport.inspections)
            assertEquals(0, transport.binds)
            assertEquals(null, transport.lastRequest)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun closeIsIdempotentAndReleasesOwnedArtifactStoreAndExecutor() {
        val executor = Executors.newSingleThreadExecutor()
        val store = FakeArtifactStore("public final class Main {}".toByteArray())
        val host = RemoteJvmSourceHost(
            transport = FakeTransport(),
            artifactStore = store,
            blockingExecutor = executor,
            featureEnabled = false,
            selectedComponent = { _ -> null },
            hostVersionCode = HOST_VERSION,
            hostRuntimeApi = 24,
            hostIdentity = HOST_IDENTITY,
            launchPackage = { true },
            ownedExecutors = listOf(executor),
        )

        host.close()
        host.close()

        assertEquals(1, store.closeCount)
        assertTrue(executor.isShutdown)
    }

    @Test
    fun rejectsAProviderSharingTheHostUidBeforeBinding() {
        val directory = Files.createTempDirectory("jvm-source-shared-uid-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        val candidate = CANDIDATE.copy(packageUid = HOST_IDENTITY.uid)
        var bound = false
        val transport = object : JvmSourceTransport {
            override fun discoverExactAction(action: String) = listOf(candidate)
            override fun inspectExact(component: JvmSourceComponent) = candidate
            override fun bindExact(
                identity: JvmSourcePinnedIdentity,
                callback: JvmSourceBindingCallback,
            ): JvmSourceBinding {
                bound = true
                error("shared-UID provider must be rejected before binding")
            }
        }
        try {
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )
            val failure = runCatching {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            }.exceptionOrNull()

            assertTrue(failure is RemoteJvmSourceException)
            assertTrue(failure?.message.orEmpty().contains("UID distinct"))
            assertFalse(bound)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun fakeProviderCompletesJavaAndUsesOnlyAllowlistedBridge() {
        val directory = Files.createTempDirectory("jvm-source-host-test").toFile()
        val sourceFile = File(directory, "Main.java").apply {
            writeText("public final class Main {}")
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val transport = FakeTransport()
            var launchedPackage: String? = null
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = JvmSourceContract.MIN_ANDROID_API,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { packageName -> launchedPackage = packageName; true },
            )

            val result = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                JvmSourceExecutionControl(),
                argsJson = ARGS_JSON,
            )

            assertEquals("{\"ok\":true}", result.resultJson)
            assertEquals(JvmSha256.digest(result.resultJson.toByteArray()).toHexString(), result.resultSha256)
            assertEquals("hello stdout", result.stdout)
            assertEquals("warning stderr", result.stderr)
            assertEquals("org.example.target", launchedPackage)
            assertEquals(ARGS_JSON, transport.lastRequest?.argsJson)
            assertTrue(result.requestSha256.matches(Regex("[0-9a-f]{64}")))
            assertEquals(1, result.allowlistedBridgeOperationCount)
            assertEquals(0, result.rejectedBridgeOperationCount)
            assertEquals(COMPILER_PID, result.compilerPid)
            assertEquals(WORKER_PID, result.workerPid)
            assertEquals(HOST_IDENTITY.pid, result.hostPid)
            assertEquals(listOf(COMPILER_PID), result.hostBridgeCallerPids)
            assertEquals(WORKER_PID, result.internalBridgeCallerPid)
            assertEquals(CANDIDATE.packageUid, result.internalBridgeCallerUid)
            assertEquals(1, result.internalBridgeCallCount)
            assertEquals("037", result.dexVersion)
            assertEquals("PRIVATE_DEX_CLASS_LOADER", result.loaderKind)
            assertEquals(3L, result.observation.sessionElapsedMillis)
            assertEquals(JvmObservationCacheOutcome.MISS, result.observation.cacheOutcome)
            assertEquals(2, result.observation.resources.size)
            assertTrue(transport.sessionClosed)
            assertEquals(3, transport.inspections)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun packagedSourceUsesDynamicCompilationUnitAndQualifiedEntryInRequest() {
        val directory = Files.createTempDirectory("jvm-source-packaged-host-test").toFile()
        val sourceFile = File(directory, "test-17.java").apply {
            writeText(
                """
                    package com.example.scripts;
                    import java.util.List;
                    public final class Main { List<String> values; }
                """.trimIndent(),
            )
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val transport = FakeTransport()
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = JvmSourceContract.MIN_ANDROID_API,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )

            host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                JvmSourceExecutionControl(),
            )

            assertEquals("Main.java", transport.lastRequest?.sourceFileName)
            assertEquals("com.example.scripts.Main", transport.lastRequest?.entryClassName)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun kotlinSourceSelectsTheKotlinProviderAndBuildsAKotlinRequest() {
        val directory = Files.createTempDirectory("jvm-source-kotlin-host-test").toFile()
        val sourceFile = File(directory, "packaged-test.kt").apply {
            writeText(
                """
                    package com.example.scripts
                    import java.util.Locale
                    class Main { val locale = Locale.ROOT }
                """.trimIndent(),
            )
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val kotlinCandidate = CANDIDATE.copy(declaredLanguage = JvmSourceLanguage.KOTLIN)
            val transport = FakeTransport(
                capabilities = KOTLIN_CAPABILITIES,
                discoveredCandidates = listOf(kotlinCandidate),
            )
            var selectedLanguage: JvmSourceLanguage? = null
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { language ->
                    selectedLanguage = language
                    COMPONENT
                },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = JvmSourceContract.MIN_ANDROID_API,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )

            val result = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                JvmSourceExecutionControl(),
            )

            assertEquals(JvmSourceLanguage.KOTLIN, selectedLanguage)
            assertEquals(JvmSourceLanguage.KOTLIN, transport.lastRequest?.language)
            assertEquals("Main.kt", transport.lastRequest?.sourceFileName)
            assertEquals("com.example.scripts.Main", transport.lastRequest?.entryClassName)
            assertEquals(JvmSourceCompilerFamily.KOTLIN_JVM.wireName, result.sourceCompilerFamily)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidKotlinUtf8IsASourceFailureBeforeProviderBinding() {
        val directory = Files.createTempDirectory("jvm-source-invalid-kotlin-test").toFile()
        val sourceFile = File(directory, "invalid.kt").apply {
            writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val transport = FakeTransport(
                capabilities = KOTLIN_CAPABILITIES,
                discoveredCandidates = listOf(CANDIDATE.copy(declaredLanguage = JvmSourceLanguage.KOTLIN)),
            )
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = JvmSourceContract.MIN_ANDROID_API,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )

            val failure = runCatching {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            }.exceptionOrNull() as? RemoteJvmSourceException

            assertEquals(JvmSourceUserFailureCode.COMPILE_FAILED, failure?.userFailure?.code)
            assertEquals(JvmSourceRecoveryAction.FIX_SOURCE, failure?.userFailure?.recoveryAction)
            assertTrue(failure?.userFailure?.message.orEmpty().startsWith("Kotlin source"))
            assertEquals(0, transport.binds)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun freshRunWithoutSelectionSurfacesMissingDisabledAndUntrustedProviderStates() {
        val directory = Files.createTempDirectory("jvm-source-unselected-provider-test").toFile()
        val sourceFile = File(directory, "test.java").apply { writeText("public final class Main {}") }
        val cases = listOf(
            emptyList<JvmSourceServiceCandidate>() to JvmSourceUserFailureCode.PROVIDER_MISSING,
            listOf(CANDIDATE.copy(pluginEnabled = false)) to JvmSourceUserFailureCode.PROVIDER_DISABLED,
            listOf(CANDIDATE.copy(providerAuthorized = false)) to JvmSourceUserFailureCode.PROVIDER_UNTRUSTED,
        )
        try {
            cases.forEach { (candidates, expected) ->
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val transport = FakeTransport(discoveredCandidates = candidates)
                    val host = RemoteJvmSourceHost(
                        transport = transport,
                        artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                        blockingExecutor = executor,
                        featureEnabled = true,
                        selectedComponent = { _ -> null },
                        hostVersionCode = HOST_VERSION,
                        hostRuntimeApi = JvmSourceContract.MIN_ANDROID_API,
                        hostIdentity = HOST_IDENTITY,
                        launchPackage = { true },
                    )

                    val failure = runCatching {
                        host.execute(
                            JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                            JvmSourceExecutionControl(),
                        )
                    }.exceptionOrNull() as? RemoteJvmSourceException

                    assertEquals(expected, failure?.userFailure?.code)
                    assertEquals(0, transport.binds)
                } finally {
                    executor.shutdownNow()
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun ordinaryJvmFileSourceCannotEnterRemoteEnginePath() {
        val directory = Files.createTempDirectory("jvm-source-generic-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val transport = FakeTransport()
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )
            val failure = runCatching {
                host.execute(JvmFileSource(sourceFile), JvmSourceExecutionControl())
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertEquals(0, transport.inspections)
            assertEquals(0, transport.binds)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun providerOutputMetadataMustMatchHostBoundedDrain() {
        val directory = Files.createTempDirectory("jvm-source-output-integrity-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val host = RemoteJvmSourceHost(
                transport = FakeTransport(corruptStdoutMetadata = true),
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )
            var rejected = false
            try {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            } catch (expected: RemoteJvmSourceException) {
                rejected = expected.allMessages().contains("stdout size metadata")
            }
            assertTrue(rejected)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun hostBridgeCallerMustBeThePinnedCompilerProxyProcess() {
        val directory = Files.createTempDirectory("jvm-source-bridge-pid-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val host = RemoteJvmSourceHost(
                transport = FakeTransport(bridgeCallerPids = setOf(WORKER_PID)),
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )
            var rejected = false
            try {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            } catch (expected: RemoteJvmSourceException) {
                rejected = expected.allMessages().contains("compiler proxy process")
            }
            assertTrue(rejected)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun providerCapabilityShortensHostTerminalDeadline() {
        val directory = Files.createTempDirectory("jvm-source-provider-timeout-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport(
            capabilities = CAPABILITIES.copy(maxTimeoutMillis = 20),
            omitTerminal = true,
            acknowledgeCancellation = true,
        )
        try {
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )
            var timedOut = false
            val elapsed = measureTimeMillis {
                try {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                } catch (expected: RemoteJvmSourceException) {
                    timedOut = expected.message.orEmpty().contains("timed out")
                }
            }
            assertTrue(timedOut)
            assertTrue("provider timeout was not honored: ${elapsed}ms", elapsed < 2_000)
            assertTrue(transport.sessionCancelled)
            assertTrue(transport.sessionClosed)
            assertTrue(transport.retirementAcknowledged)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun timeoutKeepsHostSlotUntilRetirementTerminalThenFreshRunRecovers() {
        val directory = Files.createTempDirectory("jvm-source-retirement-ack-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val binderExecutor = Executors.newSingleThreadExecutor()
        val callExecutor = Executors.newSingleThreadExecutor()
        val retirementBarrier = CountDownLatch(1)
        val transport = FakeTransport(
            capabilities = CAPABILITIES.copy(maxTimeoutMillis = 20),
            omitTerminalOnce = true,
            acknowledgeCancellation = true,
            cancellationRetirementBarrier = retirementBarrier,
        )
        try {
            val host = newEnabledHost(transport, sourceFile, binderExecutor)
            val first = callExecutor.submit<Throwable?> {
                runCatching {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                }.exceptionOrNull()
            }
            assertTrue(transport.sessionCancelObserved.await(1, TimeUnit.SECONDS))
            assertFalse(first.isDone)

            val busy = runCatching {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            }.exceptionOrNull()
            assertTrue(busy is RemoteJvmSourceException)
            assertTrue(busy?.message.orEmpty().contains("busy"))

            retirementBarrier.countDown()
            val timeout = first.get(1, TimeUnit.SECONDS)
            assertTrue(timeout is RemoteJvmSourceException)
            assertTrue(timeout?.message.orEmpty().contains("timed out"))
            assertTrue(transport.retirementAcknowledged)

            val recovered = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                JvmSourceExecutionControl(),
            )
            assertEquals("{\"ok\":true}", recovered.resultJson)
        } finally {
            retirementBarrier.countDown()
            callExecutor.shutdownNow()
            binderExecutor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellationBeforeStartedAcceptsStartedCancelledAndCleansUpTheLateSession() {
        val directory = Files.createTempDirectory("jvm-source-cancellation-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val binderExecutor = Executors.newSingleThreadExecutor()
        val control = JvmSourceExecutionControl()
        val transport = FakeTransport(
            omitTerminal = true,
            acknowledgeCancellation = true,
            beforeStarted = control::close,
        )
        try {
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = binderExecutor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )
            val error: Throwable? = try {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    control,
                )
                null
            } catch (failure: Throwable) {
                failure
            }

            assertTrue(error is RemoteJvmSourceException)
            assertTrue(error?.message.orEmpty().contains("cancelled"))
            assertTrue(
                "Late fake session was not cleaned up",
                transport.sessionCleanup.await(1, TimeUnit.SECONDS),
            )
            assertTrue(transport.sessionCancelled)
            assertTrue(transport.sessionClosed)
            assertTrue(transport.retirementAcknowledged)
        } finally {
            control.close()
            binderExecutor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun callbackAfterTerminalIsRejectedBeforeResultReturns() {
        val directory = Files.createTempDirectory("jvm-source-late-callback-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val host = RemoteJvmSourceHost(
                transport = FakeTransport(lateCallbackAfterTerminal = true),
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )
            var rejected = false
            try {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            } catch (expected: RemoteJvmSourceException) {
                rejected = expected.allMessages().contains("after terminal")
            }
            assertTrue(rejected)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun binderDeathAfterConnectionCannotBeLostBeforeCollectorInstallation() {
        val directory = Files.createTempDirectory("jvm-source-binder-death-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport(dieAfterConnect = true)
        try {
            val host = RemoteJvmSourceHost(
                transport = transport,
                artifactStore = FakeArtifactStore(sourceFile.readBytes()),
                blockingExecutor = executor,
                featureEnabled = true,
                selectedComponent = { _ -> COMPONENT },
                hostVersionCode = HOST_VERSION,
                hostRuntimeApi = 24,
                hostIdentity = HOST_IDENTITY,
                launchPackage = { true },
            )
            var rejected = false
            try {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            } catch (expected: RemoteJvmSourceException) {
                rejected = expected.message.orEmpty().contains("became unavailable")
            }
            assertTrue(rejected)
            assertTrue(transport.bindingClosed)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun compileRuntimeAndWorkerDeathTerminalsReleaseTheSlotForTheNextSession() {
        val failures = listOf(
            JvmSourceErrorCode.COMPILATION_FAILED to JvmSourceFailurePhase.COMPILATION,
            JvmSourceErrorCode.EXECUTION_FAILED to JvmSourceFailurePhase.EXECUTION,
            JvmSourceErrorCode.WORKER_DIED to JvmSourceFailurePhase.EXECUTION,
        )
        failures.forEach { (code, phase) ->
            val directory = Files.createTempDirectory("jvm-source-terminal-recovery-test").toFile()
            val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
            val executor = Executors.newSingleThreadExecutor()
            val transport = FakeTransport(failureCode = code, failurePhase = phase, failureOnce = true)
            try {
                val host = newEnabledHost(transport, sourceFile, executor)
                val failure = runCatching {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                }.exceptionOrNull()

                assertTrue(failure is RemoteJvmSourceException)
                assertTrue(failure?.message.orEmpty().contains(code.name))
                val recovered = host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
                assertEquals("{\"ok\":true}", recovered.resultJson)
            } finally {
                executor.shutdownNow()
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun compilationFailurePublishesCompilerDiagnosticBeforeThrowingTerminalError() {
        val directory = Files.createTempDirectory("jvm-source-compile-diagnostic-test").toFile()
        val sourceFile = File(directory, "selected-entry-source.java").apply {
            writeText("package demo.entries; public final class ScriptEntry {}")
        }
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport(
            failureCode = JvmSourceErrorCode.COMPILATION_FAILED,
            failurePhase = JvmSourceFailurePhase.COMPILATION,
            emitDiagnosticBeforeFailure = true,
        )
        val published = mutableListOf<JvmSourceDiagnostic>()
        val publishedObservations = mutableListOf<JvmSourceObservation>()
        val sink = object : JvmSourceExecutionSink {
            override fun publishDiagnostics(diagnostics: List<JvmSourceDiagnostic>) {
                published += diagnostics
            }

            override fun publishOutput(stdout: String, stderr: String) = Unit

            override fun publishObservation(observation: JvmSourceObservation) {
                publishedObservations += observation
            }
        }
        try {
            val host = newEnabledHost(transport, sourceFile, executor)

            val failure = runCatching {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile, "ScriptEntry"),
                    JvmSourceExecutionControl(),
                    sink,
                )
            }.exceptionOrNull()

            assertTrue(failure is RemoteJvmSourceException)
            assertEquals(JvmSourceUserFailureCode.COMPILE_FAILED, (failure as RemoteJvmSourceException).userFailure.code)
            assertEquals(1, published.size)
            assertEquals("ScriptEntry.java", transport.lastRequest?.sourceFileName)
            assertEquals("demo.entries.ScriptEntry", transport.lastRequest?.entryClassName)
            assertEquals(JvmDiagnosticSeverity.ERROR, published.single().severity)
            assertEquals("ECJ_ERROR", published.single().code)
            assertEquals("ScriptEntry.java", published.single().sourceFileName)
            assertEquals(4, published.single().line)
            assertEquals(28, published.single().column)
            assertTrue(published.single().message.contains("println2"))
            assertEquals(
                "ScriptEntry.java",
                JvmSourcePublicDiagnosticPolicy.project(published.single()).sourceFileName,
            )
            assertEquals(1, publishedObservations.size)
            assertEquals(JvmObservationCacheOutcome.MISS, publishedObservations.single().cacheOutcome)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun runtimeFailurePublishesOnlyTheSafeExceptionClassAndLocationBeforeTerminalError() {
        val directory = Files.createTempDirectory("jvm-source-runtime-diagnostic-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport(
            failureCode = JvmSourceErrorCode.EXECUTION_FAILED,
            failurePhase = JvmSourceFailurePhase.EXECUTION,
            emitRuntimeDiagnosticBeforeFailure = true,
        )
        val published = mutableListOf<JvmSourceDiagnostic>()
        val sink = object : JvmSourceExecutionSink {
            override fun publishDiagnostics(diagnostics: List<JvmSourceDiagnostic>) {
                published += diagnostics
            }

            override fun publishOutput(stdout: String, stderr: String) = Unit
        }
        try {
            val host = newEnabledHost(transport, sourceFile, executor)

            val failure = runCatching {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                    sink,
                )
            }.exceptionOrNull()

            assertTrue(failure is RemoteJvmSourceException)
            assertEquals(JvmSourceUserFailureCode.RUNTIME_FAILED, (failure as RemoteJvmSourceException).userFailure.code)
            assertEquals(1, published.size)
            val diagnostic = published.single()
            assertEquals(JvmDiagnosticSeverity.ERROR, diagnostic.severity)
            assertEquals("JAVA_RUNTIME_EXCEPTION", diagnostic.code)
            assertEquals("Main.java", diagnostic.sourceFileName)
            assertEquals(4, diagnostic.line)
            assertEquals(1, diagnostic.column)
            assertEquals("java.lang.IllegalStateException", diagnostic.runtimeExceptionClassName)
            assertEquals("Java execution failed", diagnostic.message)
            assertFalse(diagnostic.toString().contains("M9-3 secret"))
            assertFalse(diagnostic.toString().contains("private"))
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun binderServiceAndBindingDeathAreTerminalThenAFreshSessionCanRecover() {
        JvmSourceProviderTransportLoss.entries.forEach { loss ->
            val directory = Files.createTempDirectory("jvm-source-provider-loss-recovery-test").toFile()
            val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
            val executor = Executors.newSingleThreadExecutor()
            val transport = FakeTransport(dieAfterConnectOnce = true, transportLoss = loss)
            try {
                val host = newEnabledHost(transport, sourceFile, executor)
                val failure = runCatching {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                }.exceptionOrNull()

                assertTrue(failure is RemoteJvmSourceException)
                assertTrue(failure?.message.orEmpty().contains(loss.name))
                assertTrue(transport.bindingClosed)
                val recovered = host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
                assertEquals("{\"ok\":true}", recovered.resultJson)
            } finally {
                executor.shutdownNow()
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun updateDisableAndUninstallFailIdentityRevalidationThenNextSessionCanRecover() {
        listOf(
            JvmSourceProviderLifecyclePolicy.Loss.UPDATED,
            JvmSourceProviderLifecyclePolicy.Loss.DISABLED,
            JvmSourceProviderLifecyclePolicy.Loss.UNINSTALLED,
        ).forEach { loss ->
            val directory = Files.createTempDirectory("jvm-source-provider-drift-recovery-test").toFile()
            val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
            val executor = Executors.newSingleThreadExecutor()
            val transport = FakeTransport(identityLossOnce = loss)
            try {
                val host = newEnabledHost(transport, sourceFile, executor)
                val failure = runCatching {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                }.exceptionOrNull()

                assertTrue(failure is RemoteJvmSourceException)
                assertTrue(failure?.message.orEmpty().contains(loss.name))
                assertEquals(1, transport.binds)
                val recovered = host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
                assertEquals("{\"ok\":true}", recovered.resultJson)
                assertEquals(2, transport.binds)
            } finally {
                executor.shutdownNow()
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun hostRuntimeApiFailsClosedOnWrongLoaderOrRequestIncompatibleDexVersion() {
        data class Case(
            val runtimeApi: Int,
            val transport: FakeTransport,
            val expectedMessage: String,
        )
        val cases = listOf(
            Case(
                24,
                FakeTransport(
                    loaderKind = JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
                    acknowledgeCancellation = true,
                ),
                "loader kind",
            ),
            Case(
                26,
                FakeTransport(
                    loaderKind = JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
                    acknowledgeCancellation = true,
                ),
                "loader kind",
            ),
            Case(
                26,
                FakeTransport(
                    dexVersion = "038",
                    loaderKind = JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
                    acknowledgeCancellation = true,
                ),
                "minApi profile",
            ),
        )
        cases.forEach { case ->
            val directory = Files.createTempDirectory("jvm-source-loader-telemetry-test").toFile()
            val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
            val executor = Executors.newSingleThreadExecutor()
            try {
                val host = newEnabledHost(case.transport, sourceFile, executor, case.runtimeApi)
                val failure = runCatching {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                }.exceptionOrNull()
                assertTrue(failure is RemoteJvmSourceException)
                assertTrue(failure?.allMessages().orEmpty().contains(case.expectedMessage))
            } finally {
                executor.shutdownNow()
                directory.deleteRecursively()
            }
        }

        val directory = Files.createTempDirectory("jvm-source-loader-telemetry-success-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val host = newEnabledHost(
                FakeTransport(loaderKind = JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER),
                sourceFile,
                executor,
                runtimeApi = 26,
            )
            assertEquals(
                "IN_MEMORY_DEX_CLASS_LOADER",
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                ).loaderKind,
            )
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun wrongLoaderKeepsHostBusyUntilCancellationRetirementThenFreshRunRecovers() {
        val directory = Files.createTempDirectory("jvm-source-wrong-loader-retirement-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val binderExecutor = Executors.newSingleThreadExecutor()
        val callExecutor = Executors.newSingleThreadExecutor()
        val retirementBarrier = CountDownLatch(1)
        val transport = FakeTransport(
            loaderKindOnce = JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
            acknowledgeCancellation = true,
            cancellationRetirementBarrier = retirementBarrier,
        )
        try {
            val host = newEnabledHost(transport, sourceFile, binderExecutor)
            val first = callExecutor.submit<Throwable?> {
                runCatching {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                }.exceptionOrNull()
            }
            assertTrue(transport.sessionCancelObserved.await(1, TimeUnit.SECONDS))
            assertFalse(first.isDone)

            val busy = runCatching {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            }.exceptionOrNull()
            assertTrue(busy is RemoteJvmSourceException)
            assertTrue(busy?.message.orEmpty().contains("busy"))

            retirementBarrier.countDown()
            val failure = first.get(1, TimeUnit.SECONDS)
            assertTrue(failure is RemoteJvmSourceException)
            assertTrue(failure?.allMessages().orEmpty().contains("loader kind"))
            assertTrue(transport.retirementAcknowledged)

            val recovered = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                JvmSourceExecutionControl(),
            )
            assertEquals("{\"ok\":true}", recovered.resultJson)
        } finally {
            retirementBarrier.countDown()
            callExecutor.shutdownNow()
            binderExecutor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun malformedCallbackKeepsHostBusyUntilTransportDeathThenFreshRunRecovers() {
        val directory = Files.createTempDirectory("jvm-source-malformed-retirement-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val binderExecutor = Executors.newSingleThreadExecutor()
        val callExecutor = Executors.newSingleThreadExecutor()
        val transportDeathBarrier = CountDownLatch(1)
        val transport = FakeTransport(
            malformedResultOnce = true,
            transportDeathOnCancellationOnce = true,
            cancellationRetirementBarrier = transportDeathBarrier,
        )
        try {
            val host = newEnabledHost(transport, sourceFile, binderExecutor)
            val first = callExecutor.submit<Throwable?> {
                runCatching {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                }.exceptionOrNull()
            }
            assertTrue(transport.sessionCancelObserved.await(1, TimeUnit.SECONDS))
            assertFalse(first.isDone)

            val busy = runCatching {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            }.exceptionOrNull()
            assertTrue(busy is RemoteJvmSourceException)
            assertTrue(busy?.message.orEmpty().contains("busy"))

            transportDeathBarrier.countDown()
            assertTrue(first.get(1, TimeUnit.SECONDS) is RemoteJvmSourceException)
            assertTrue(transport.transportRetirementObserved)

            val recovered = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                JvmSourceExecutionControl(),
            )
            assertEquals("{\"ok\":true}", recovered.resultJson)
        } finally {
            transportDeathBarrier.countDown()
            callExecutor.shutdownNow()
            binderExecutor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingProtocol15ObservationIsQuarantinedAndFreshRunRecovers() {
        val directory = Files.createTempDirectory("jvm-source-missing-observation-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val executor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport(
            omitObservationOnce = true,
            transportDeathOnCancellationOnce = true,
        )
        try {
            val host = newEnabledHost(transport, sourceFile, executor)

            val failure = runCatching {
                host.execute(
                    JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                    JvmSourceExecutionControl(),
                )
            }.exceptionOrNull()

            assertTrue(failure is RemoteJvmSourceException)
            assertTrue(failure?.allMessages().orEmpty().contains("observation presence"))
            assertTrue(transport.transportRetirementObserved)

            val recovered = host.execute(
                JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                JvmSourceExecutionControl(),
            )
            assertEquals("{\"ok\":true}", recovered.resultJson)
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun protocolViolationWithoutRetirementAckReleasesHostAfterBoundedQuarantine() {
        val directory = Files.createTempDirectory("jvm-source-no-retirement-ack-test").toFile()
        val sourceFile = File(directory, "Main.java").apply { writeText("public final class Main {}") }
        val binderExecutor = Executors.newSingleThreadExecutor()
        val transport = FakeTransport(loaderKindOnce = JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER)
        try {
            val host = newEnabledHost(
                transport = transport,
                sourceFile = sourceFile,
                executor = binderExecutor,
                providerRetirementTimeoutMillis = 75,
            )
            var failure: Throwable? = null
            val elapsed = measureTimeMillis {
                failure = runCatching {
                    host.execute(
                        JvmSourceExplicitRunner.createAuthorizedSource(sourceFile),
                        JvmSourceExecutionControl(),
                    )
                }.exceptionOrNull()
            }

            assertTrue(failure is RemoteJvmSourceException)
            assertTrue(failure?.allMessages().orEmpty().contains("loader kind"))
            assertTrue(transport.sessionCancelled)
            assertFalse(transport.retirementAcknowledged)
            assertTrue("retirement quarantine was not bounded: ${elapsed}ms", elapsed < 1_000)
        } finally {
            binderExecutor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    private fun newEnabledHost(
        transport: JvmSourceTransport,
        sourceFile: File,
        executor: java.util.concurrent.ExecutorService,
        runtimeApi: Int = 24,
        providerRetirementTimeoutMillis: Long = 3_000,
    ) = RemoteJvmSourceHost(
        transport = transport,
        artifactStore = FakeArtifactStore(sourceFile.readBytes()),
        blockingExecutor = executor,
        featureEnabled = true,
        selectedComponent = { _ -> COMPONENT },
        hostVersionCode = HOST_VERSION,
        hostRuntimeApi = runtimeApi,
        hostIdentity = HOST_IDENTITY,
        launchPackage = { true },
        providerRetirementTimeoutMillis = providerRetirementTimeoutMillis,
    )

    private class FakeTransport(
        private val corruptStdoutMetadata: Boolean = false,
        private val capabilities: JvmSourceCapabilities = CAPABILITIES,
        private val omitTerminal: Boolean = false,
        private val omitTerminalOnce: Boolean = false,
        private val acknowledgeCancellation: Boolean = false,
        private val cancellationRetirementBarrier: CountDownLatch? = null,
        private val beforeStarted: (() -> Unit)? = null,
        private val lateCallbackAfterTerminal: Boolean = false,
        private val dieAfterConnect: Boolean = false,
        private val dieAfterConnectOnce: Boolean = false,
        private val transportLoss: JvmSourceProviderTransportLoss = JvmSourceProviderTransportLoss.BINDER_DIED,
        private val identityLossOnce: JvmSourceProviderLifecyclePolicy.Loss? = null,
        private val failureCode: JvmSourceErrorCode? = null,
        private val failurePhase: JvmSourceFailurePhase = JvmSourceFailurePhase.EXECUTION,
        private val failureOnce: Boolean = false,
        private val emitDiagnosticBeforeFailure: Boolean = false,
        private val emitRuntimeDiagnosticBeforeFailure: Boolean = false,
        private val dexVersion: String = "037",
        private val loaderKind: JvmDexLoaderKind = JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
        private val loaderKindOnce: JvmDexLoaderKind? = null,
        private val malformedResultOnce: Boolean = false,
        private val omitObservationOnce: Boolean = false,
        private val transportDeathOnCancellationOnce: Boolean = false,
        private val bridgeCallerPids: Set<Int> = setOf(COMPILER_PID),
        private val discoveredCandidates: List<JvmSourceServiceCandidate> = listOf(CANDIDATE),
    ) : JvmSourceTransport {
        var inspections = 0
        var binds = 0
        var sessionClosed = false
        var sessionCancelled = false
        var bindingClosed = false
        var lastRequest: JvmSourceRequest? = null
        @Volatile
        var retirementAcknowledged = false
        @Volatile
        var transportRetirementObserved = false
        val sessionCancelObserved = CountDownLatch(1)
        val sessionCleanup = CountDownLatch(1)

        override fun discoverExactAction(action: String): List<JvmSourceServiceCandidate> =
            if (action == JvmSourceContract.SERVICE_ACTION) discoveredCandidates else emptyList()

        override fun inspectExact(component: JvmSourceComponent): JvmSourceServiceCandidate? {
            inspections++
            val steadyCandidate = discoveredCandidates.firstOrNull { it.component == component } ?: CANDIDATE
            val current = if (identityLossOnce != null && inspections == FINAL_INSPECTION_BEFORE_FIRST_DISPATCH) {
                when (identityLossOnce) {
                    JvmSourceProviderLifecyclePolicy.Loss.UNINSTALLED -> null
                    JvmSourceProviderLifecyclePolicy.Loss.DISABLED -> steadyCandidate.copy(pluginEnabled = false)
                    JvmSourceProviderLifecyclePolicy.Loss.UPDATED -> steadyCandidate.copy(
                        versionCode = steadyCandidate.versionCode + 1,
                        lastUpdateTime = steadyCandidate.lastUpdateTime + 1,
                    )
                    JvmSourceProviderLifecyclePolicy.Loss.ROLLED_BACK -> steadyCandidate.copy(
                        versionCode = steadyCandidate.versionCode - 1,
                        lastUpdateTime = steadyCandidate.lastUpdateTime + 1,
                    )
                    JvmSourceProviderLifecyclePolicy.Loss.IDENTITY_CHANGED -> steadyCandidate.copy(
                        signerSha256 = setOf("b".repeat(64)),
                    )
                }
            } else {
                steadyCandidate
            }
            return current?.takeIf { it.component == component }
        }

        override fun bindExact(
            identity: JvmSourcePinnedIdentity,
            callback: JvmSourceBindingCallback,
        ): JvmSourceBinding {
            binds++
            val bindingOrdinal = binds
            val bindingCallback = callback
            callback.onConnected(object : JvmSourceRemoteConnection {
                override fun getProviderInfo(): ByteArray = JvmSourceCodec.encodeProviderInfo(PROVIDER_INFO)

                override fun getCapabilities(): ByteArray = JvmSourceCodec.encodeCapabilities(capabilities)

                override fun openSession(
                    encodedRequest: ByteArray,
                    sourceDescriptor: JvmSourceOwnedDescriptor,
                    stdoutDescriptor: JvmSourceOwnedDescriptor,
                    stderrDescriptor: JvmSourceOwnedDescriptor,
                    hostBridge: JvmSourceHostBridgeEndpoint,
                    callback: JvmSourceRemoteCallback,
                ): JvmSourceRemoteSession {
                    val request = JvmSourceCodec.decodeRequest(encodedRequest)
                    lastRequest = request
                    val terminalFailureCode = failureCode?.takeIf { !failureOnce || binds == 1 }
                    beforeStarted?.invoke()
                    callback.onStarted(
                        JvmSourceCodec.encodeStarted(
                            JvmSourceStarted(
                                request.requestId,
                                request.language,
                                PROVIDER_INFO.providerId,
                                capabilities.toolchainFingerprint,
                                COMPILER_PID,
                                CANDIDATE.packageUid,
                                CANDIDATE.compilerProcessName,
                            ),
                        ),
                    )
                    if (beforeStarted != null) {
                        callback.onDiagnostic(
                            JvmSourceCodec.encodeDiagnostic(
                                JvmSourceDiagnostic(
                                    requestId = request.requestId,
                                    severity = JvmDiagnosticSeverity.INFO,
                                    code = "QUEUED_BEFORE_CANCEL",
                                    message = "diagnostic queued before cancellation reached the provider",
                                    sourceFileName = request.sourceFileName,
                                    line = 1,
                                    column = 1,
                                ),
                            ),
                        )
                    }
                    if (beforeStarted == null && terminalFailureCode == null) {
                        var bridgeSucceeded = false
                        hostBridge.dispatch(
                            JvmSourceCodec.encodeHostCall(
                                JvmHostCall(
                                    request.requestId,
                                    1,
                                    "app.launch",
                                    "{\"packageName\":\"org.example.target\"}",
                                    1_000,
                                ),
                            ),
                        ) { response ->
                            bridgeSucceeded = JvmSourceCodec.decodeHostResponse(response).succeeded
                        }
                        assertTrue(bridgeSucceeded)
                    }
                    val encodedResult = JvmSourceCodec.encodeResult(
                        JvmSourceResult(
                            request.requestId,
                            capabilities.toolchainFingerprint,
                            CLASS_ARTIFACT.size.toLong(),
                            JvmSha256.digest(CLASS_ARTIFACT),
                            DEX_ARTIFACT.size.toLong(),
                            JvmSha256.digest(DEX_ARTIFACT),
                            dexVersion,
                            loaderKindOnce?.takeIf { bindingOrdinal == 1 } ?: loaderKind,
                            "{\"ok\":true}",
                            stdoutSizeBytes = STDOUT.size.toLong() + if (corruptStdoutMetadata) 1 else 0,
                            stdoutSha256 = JvmSha256.digest(STDOUT),
                            stderrSizeBytes = STDERR.size.toLong(),
                            stderrSha256 = JvmSha256.digest(STDERR),
                            workerPid = WORKER_PID,
                            workerUid = CANDIDATE.packageUid,
                            workerProcessName = "$PACKAGE:worker",
                            workerGeneration = 1,
                            internalBridgeCallerPid = WORKER_PID,
                            internalBridgeCallerUid = CANDIDATE.packageUid,
                            internalBridgeCallCount = 1,
                            compilationElapsedMillis = 1,
                            executionElapsedMillis = 1,
                            elapsedMillis = 3,
                            observation = observation(request.requestId)
                                .takeUnless { omitObservationOnce && bindingOrdinal == 1 },
                        ),
                    )
                    val omitThisTerminal = omitTerminal || (omitTerminalOnce && binds == 1)
                    if (!omitThisTerminal) {
                        if (terminalFailureCode == null) {
                            if (malformedResultOnce && bindingOrdinal == 1) {
                                callback.onCompleted(byteArrayOf(1, 2, 3), bridgeCallerPids)
                            } else {
                                callback.onCompleted(encodedResult, bridgeCallerPids)
                            }
                            if (lateCallbackAfterTerminal) callback.onCompleted(encodedResult, bridgeCallerPids)
                        } else {
                            if (emitDiagnosticBeforeFailure) {
                                callback.onDiagnostic(
                                    JvmSourceCodec.encodeDiagnostic(
                                        JvmSourceDiagnostic(
                                            requestId = request.requestId,
                                            severity = JvmDiagnosticSeverity.ERROR,
                                            code = "ECJ_ERROR",
                                            message = "The method println2(String) is undefined for the type PrintStream",
                                            sourceFileName = request.sourceFileName,
                                            line = 4,
                                            column = 28,
                                        ),
                                    ),
                                )
                            }
                            if (emitRuntimeDiagnosticBeforeFailure) {
                                callback.onDiagnostic(
                                    JvmSourceCodec.encodeDiagnostic(
                                        JvmSourceDiagnostic(
                                            requestId = request.requestId,
                                            severity = JvmDiagnosticSeverity.ERROR,
                                            code = "JAVA_RUNTIME_EXCEPTION",
                                            message = "M9-3 secret C:\\private\\classes.dex uid=1000 pid=2000",
                                            sourceFileName = request.sourceFileName,
                                            line = 4,
                                            column = 1,
                                            runtimeExceptionClassName = "java.lang.IllegalStateException",
                                        ),
                                    ),
                                )
                            }
                            callback.onFailed(
                                JvmSourceCodec.encodeError(
                                    JvmSourceError(
                                        requestId = request.requestId,
                                        code = terminalFailureCode,
                                        phase = failurePhase,
                                        message = "controlled fake provider failure",
                                        retryable = false,
                                        observation = observation(request.requestId),
                                    ),
                                ),
                            )
                        }
                    }
                    val cancelSent = AtomicBoolean(false)
                    val openedSession = object : JvmSourceRemoteSession {
                        override fun cancel() {
                            if (!cancelSent.compareAndSet(false, true)) return
                            sessionCancelled = true
                            sessionCancelObserved.countDown()
                            if (transportDeathOnCancellationOnce && bindingOrdinal == 1) {
                                Thread(
                                    {
                                        cancellationRetirementBarrier?.await(5, TimeUnit.SECONDS)
                                        transportRetirementObserved = true
                                        bindingCallback.onBinderDeath(JvmSourceProviderTransportLoss.BINDER_DIED)
                                    },
                                    "fake-jvm-source-transport-retirement",
                                ).apply {
                                    isDaemon = true
                                    start()
                                }
                            } else if (acknowledgeCancellation) {
                                Thread(
                                    {
                                        cancellationRetirementBarrier?.await(5, TimeUnit.SECONDS)
                                        retirementAcknowledged = true
                                        callback.onCancelled(
                                            JvmSourceCodec.encodeCancellation(
                                                JvmSourceCancellation(
                                                    requestId = request.requestId,
                                                    reason = JvmCancellationReason.REQUESTED,
                                                    phase = JvmSourceFailurePhase.EXECUTION,
                                                    elapsedMillis = 1,
                                                    observation = observation(request.requestId),
                                                ),
                                            ),
                                        )
                                    },
                                    "fake-jvm-source-retirement-ack",
                                ).apply {
                                    isDaemon = true
                                    start()
                                }
                            }
                        }
                        override fun close() {
                            sessionClosed = true
                            sessionCleanup.countDown()
                        }
                    }
                    return openedSession
                }
            })
            if (dieAfterConnect || (dieAfterConnectOnce && binds == 1)) callback.onBinderDeath(transportLoss)
            return object : JvmSourceBinding {
                override fun close() {
                    bindingClosed = true
                }
            }
        }
    }

    private class FakeArtifactStore(private val source: ByteArray) : JvmSourceArtifactStore {
        var closeCount = 0
            private set

        override fun snapshotJavaSource(file: File, maximumBytes: Long): JvmSourceInputSnapshot =
            snapshot(JvmSourceKind.JAVA)

        override fun snapshotSource(
            file: File,
            maximumBytes: Long,
            entrySimpleName: String,
        ): JvmSourceInputSnapshot =
            snapshot(requireNotNull(JvmSourceKind.fromFileName(file.name)), entrySimpleName)

        override fun snapshotSourcePackage(
            sourceRoot: File,
            entryFile: File,
            maximumBytes: Long,
            entrySimpleName: String,
        ): JvmSourceInputSnapshot {
            val data = JvmSourcePackageSnapshotPolicy.collect(
                sourceRoot,
                entryFile,
                JVM_PACKAGE_OPERATIONS,
                entrySimpleName,
            )
            require(data.archiveBytes.size.toLong() <= maximumBytes)
            return object : JvmSourceInputSnapshot {
                override val sizeBytes = data.archiveBytes.size.toLong()
                override val sha256Hex = MessageDigest.getInstance("SHA-256")
                    .digest(data.archiveBytes)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                override val sourceFileName = data.sourceFileName
                override val entryClassName = data.entryClassName
                override val sourcePayloadKind = JvmSourcePayloadKind.SOURCE_ARCHIVE
                override val sourceFileCount = data.sourceFileCount
                override val sourceContentBytes = data.sourceContentBytes

                override fun claimReadDescriptor(): JvmSourceOwnedDescriptor = Descriptor()
                override fun close() = Unit
            }
        }

        private fun snapshot(
            kind: JvmSourceKind,
            entrySimpleName: String = JvmSourceR1Policy.ENTRY_SIMPLE_NAME,
        ): JvmSourceInputSnapshot {
            val layout = JvmSourceR1Policy.inspect(kind, source, entrySimpleName)
            return object : JvmSourceInputSnapshot {
                override val sizeBytes = source.size.toLong()
                override val sha256Hex = MessageDigest.getInstance("SHA-256").digest(source).joinToString("") {
                    "%02x".format(it.toInt() and 0xff)
                }
                override val sourceFileName = layout.sourceFileName
                override val entryClassName = layout.entryClassName

                override fun claimReadDescriptor(): JvmSourceOwnedDescriptor = Descriptor()
                override fun close() = Unit
            }
        }

        override fun outputCapture(
            maximumBytes: Long,
            observer: JvmSourceOutputObserver,
        ): JvmSourceOutputCapture =
            Capture(if (nextCapture++ % 2 == 0) STDOUT else STDERR)

        override fun close() {
            closeCount++
        }

        private var nextCapture = 0

        private companion object {
            val JVM_PACKAGE_OPERATIONS = object : JvmSourcePackageSnapshotPolicy.Operations {
                override fun typeNoFollow(file: File): JvmSourcePackageSnapshotPolicy.NodeType {
                    val attributes = runCatching {
                        Files.readAttributes(
                            file.toPath(),
                            java.nio.file.attribute.BasicFileAttributes::class.java,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS,
                        )
                    }.getOrNull() ?: return JvmSourcePackageSnapshotPolicy.NodeType.MISSING
                    return when {
                        attributes.isSymbolicLink -> JvmSourcePackageSnapshotPolicy.NodeType.SYMBOLIC_LINK
                        attributes.isDirectory -> JvmSourcePackageSnapshotPolicy.NodeType.DIRECTORY
                        attributes.isRegularFile -> JvmSourcePackageSnapshotPolicy.NodeType.REGULAR_FILE
                        else -> JvmSourcePackageSnapshotPolicy.NodeType.OTHER
                    }
                }

                override fun children(directory: File): List<File>? = directory.listFiles()?.toList()

                override fun readRegularFile(file: File, maximumBytes: Long): ByteArray =
                    file.readBytes().also { require(it.size.toLong() <= maximumBytes) }
            }
        }
    }

    private class Capture(private val bytes: ByteArray) : JvmSourceOutputCapture {
        override fun claimWriteDescriptor(): JvmSourceOwnedDescriptor = Descriptor()
        override fun await(timeoutMillis: Long) = JvmSourceCapturedOutput(
            bytes,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            },
        )

        override fun abort(cause: Throwable?) = Unit
        override fun close() = Unit
    }

    private class Descriptor : JvmSourceOwnedDescriptor {
        override fun close() = Unit
    }

    private fun Throwable.allMessages(): String = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" | ")

    private companion object {
        const val PACKAGE = "org.example.jvmprovider"
        const val HOST_VERSION = 5_300L
        const val COMPILER_PID = 20_001
        const val WORKER_PID = 20_002
        const val ARGS_JSON = "{\"count\":2,\"nested\":[true,null]}"
        const val FINAL_INSPECTION_BEFORE_FIRST_DISPATCH = 3
        val HOST_IDENTITY = JvmSourceHostProcessIdentity(10_001, 10_000, "org.autojs.autojs6")
        val COMPONENT = JvmSourceComponent(
            PACKAGE,
            "$PACKAGE.service.CompilerService",
        )
        val CANDIDATE = JvmSourceServiceCandidate(
            component = COMPONENT,
            declaredAction = JvmSourceContract.SERVICE_ACTION,
            exported = true,
            applicationEnabled = true,
            serviceEnabled = true,
            pluginEnabled = true,
            providerAuthorized = true,
            requiredPermission = JvmSourceContract.PLUGIN_PERMISSION,
            compilerProcessName = "$PACKAGE:compiler",
            packageUid = 10_001,
            versionCode = 7,
            lastUpdateTime = 123,
            signerSha256 = setOf("aa".repeat(32)),
        )
        val PROVIDER_INFO = JvmProviderInfo(
            protocolMin = JvmSourceNegotiation.HOST_MAX,
            protocolMax = JvmSourceNegotiation.HOST_MAX,
            providerId = "ecj-java",
            providerVersionName = "0.1.0",
            providerVersionCode = 7,
            entryApiVersion = JvmSourceContract.ENTRY_API_VERSION,
            minHostVersionCode = HOST_VERSION,
        )
        val RUNTIME_FINGERPRINT = JvmSha256.digest("runtime-v1".toByteArray())
        val CLASS_ARTIFACT = "Main.class".toByteArray()
        val DEX_ARTIFACT = "classes.dex".toByteArray()
        val STDOUT = "hello stdout".toByteArray()
        val STDERR = "warning stderr".toByteArray()
        fun observation(requestId: org.autojs.plugin.jvmsource.api.JvmRequestId) = JvmSourceObservation(
            requestId = requestId,
            sessionElapsedMillis = 3L,
            workerStartupDurationMillis = 1L,
            compilerStartProfile = JvmObservationStartProfile.WARM,
            workerStartProfile = JvmObservationStartProfile.COLD,
            compileDurationMillis = 1L,
            d8DurationMillis = 1L,
            loadDurationMillis = 1L,
            runDurationMillis = 1L,
            terminationDurationMillis = 1L,
            cacheOutcome = JvmObservationCacheOutcome.MISS,
            cacheMissReason = JvmObservationCacheMissReason.NOT_FOUND,
            resources = listOf(
                JvmSourceResourceObservation(
                    process = JvmObservationProcess.COMPILER,
                    phase = JvmObservationPhase.COMPILE,
                    rssBytes = 1_024L,
                    openFileDescriptorCount = 8,
                    temporaryStorageBytes = 512L,
                    outputBytes = 0L,
                ),
                JvmSourceResourceObservation(
                    process = JvmObservationProcess.WORKER,
                    phase = JvmObservationPhase.RUN,
                    rssBytes = 2_048L,
                    openFileDescriptorCount = 9,
                    temporaryStorageBytes = 256L,
                    outputBytes = 64L,
                ),
            ),
        )
        val CAPABILITIES = JvmSourceCapabilities(
            languages = listOf(JvmSourceLanguage.JAVA),
            isolationCapabilities = JvmIsolationCapability.entries,
            sourceCompilerFamily = JvmSourceCompilerFamily.ECJ,
            sourceCompilerVersion = "3.26.0",
            d8Version = "8.13.17",
            runtimeLibraryFingerprint = RUNTIME_FINGERPRINT,
            toolchainFingerprint = JvmToolchainFingerprint.compute(
                JvmSourceLanguage.JAVA,
                JvmSourceCompilerFamily.ECJ,
                "3.26.0",
                "8.13.17",
                RUNTIME_FINGERPRINT,
            ),
            maxSourceBytes = JvmSourceContract.MAX_SOURCE_BYTES,
            maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
            maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
            maxDiagnosticBytes = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
            maxTimeoutMillis = JvmSourceContract.MAX_TIMEOUT_MILLIS,
            maxConcurrentSessions = 1,
            scriptCapabilities = JvmScriptCapability.entries,
        )
        val KOTLIN_CAPABILITIES = JvmSourceCapabilities(
            languages = listOf(JvmSourceLanguage.KOTLIN),
            isolationCapabilities = JvmIsolationCapability.entries,
            sourceCompilerFamily = JvmSourceCompilerFamily.KOTLIN_JVM,
            sourceCompilerVersion = "2.3.21",
            d8Version = "8.13.17",
            runtimeLibraryFingerprint = RUNTIME_FINGERPRINT,
            toolchainFingerprint = JvmToolchainFingerprint.compute(
                JvmSourceLanguage.KOTLIN,
                JvmSourceCompilerFamily.KOTLIN_JVM,
                "2.3.21",
                "8.13.17",
                RUNTIME_FINGERPRINT,
            ),
            maxSourceBytes = JvmSourceContract.MAX_SOURCE_BYTES,
            maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
            maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
            maxDiagnosticBytes = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
            maxTimeoutMillis = JvmSourceContract.MAX_TIMEOUT_MILLIS,
            maxConcurrentSessions = 1,
            scriptCapabilities = JvmScriptCapability.entries,
        )
    }
}
