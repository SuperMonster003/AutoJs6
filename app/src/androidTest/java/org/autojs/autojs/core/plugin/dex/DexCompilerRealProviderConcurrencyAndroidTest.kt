package org.autojs.autojs.core.plugin.dex

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.rhino.AndroidClassLoader
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Explicit real-device production single-flight evidence.
 *
 * These tests use a debug-only scoped probe to establish join and dispatch ordering without timing
 * inference. They do not cover BUSY, process/Binder death, timeout, last-waiter cancellation, or a
 * complete device/API matrix.
 */
class DexCompilerRealProviderConcurrencyAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun sameSemanticKeySharesOneRemoteDispatchAndReturnsFreshCallerCapabilities() {
        val identity = requireRealProvider()
        val savedOptIn = snapshotOptInStore()
        val token = UUID.randomUUID()
        val program = File(context.cacheDir, "dex-real-concurrency-$token.jar")
        val loaderCache = File(context.codeCacheDir, "dex-real-concurrency-$token")
        val probe = BlockingProductionProbe()
        var registration: Closeable? = null
        var leader: RuntimeCall? = null
        var follower: RuntimeCall? = null
        var primaryFailure: Throwable? = null
        try {
            writeProgram(program)
            selectRealProvider(identity)
            val coordinator = productionCoordinator()
            registration = DexCompilerRuntimeAndroidTestInstrumentation.installForAndroidTest(probe)

            leader = RuntimeCall("dex-real-concurrency-leader") { coordinator.compile(program) }.also { it.start() }
            assertTrue("The shared production producer did not reach its deterministic gate", probe.awaitProducer())
            follower = RuntimeCall("dex-real-concurrency-follower") { coordinator.compile(program) }.also { it.start() }
            assertTrue("The second caller did not join the live production flight", probe.awaitFollower())
            probe.releaseProducer()

            val first = requirePublished(checkNotNull(leader).awaitDecision(), "leader")
            val second = requirePublished(checkNotNull(follower).awaitDecision(), "follower")
            assertEquals(1, probe.leaderJoinCount.get())
            assertEquals(1, probe.followerJoinCount.get())
            assertEquals(1, probe.producerCount.get())
            assertEquals(1, probe.dispatchCount.get())
            assertEquals(0, probe.cancelledAfterFallbackCloseCount.get())
            assertEquals(0, probe.fallbackCloseFailureCount.get())
            assertFalse("The unique leader unexpectedly used a pre-existing cache entry", first.cacheHit)
            assertFalse("The follower did not receive the shared leader outcome", second.cacheHit)

            assertNotSame(first.fallbackInput, second.fallbackInput)
            val firstSnapshot = first.fallbackInput.resolve()
            val secondSnapshot = second.fallbackInput.resolve()
            assertNotNull("The leader fallback snapshot is unavailable", firstSnapshot)
            assertNotNull("The follower fallback snapshot is unavailable", secondSnapshot)
            assertNotEquals("The callers unexpectedly share one retained snapshot file", firstSnapshot, secondSnapshot)

            assertNotSame(first.artifact, second.artifact)
            assertEquals(first.artifact.cacheKeySha256, second.artifact.cacheKeySha256)
            assertEquals(first.artifact.outputSha256, second.artifact.outputSha256)
            assertEquals(first.artifact.sizeBytes, second.artifact.sizeBytes)
            val firstRead = requireNotNull(first.artifact.openRead()) { "The leader artifact cannot be opened" }
            val secondRead = requireNotNull(second.artifact.openRead()) { "The follower artifact cannot be opened" }
            firstRead.use { assertTrue("The leader artifact is empty", it.read() >= 0) }
            secondRead.use { assertTrue("The follower artifact is empty", it.read() >= 0) }

            first.fallbackInput.close()
            assertNull("Closing the leader fallback did not release it", first.fallbackInput.resolve())
            assertNotNull("Closing the leader fallback affected the follower", second.fallbackInput.resolve())
            second.fallbackInput.close()
            assertNull("Closing the follower fallback did not release it", second.fallbackInput.resolve())

            val fixture = AndroidClassLoader(
                parent = requireNotNull(javaClass.classLoader),
                cacheDir = loaderCache,
            ).loadJarWithExperimentalCompiler(program).loadClass(FIXTURE_CLASS_NAME)
            assertEquals(FIXTURE_CLASS_NAME, fixture.name)
            assertEquals(42, fixture.getDeclaredMethod("answer").invoke(null))
            assertEquals(
                "The production AndroidClassLoader cache hit unexpectedly opened another remote session",
                1,
                probe.dispatchCount.get(),
            )

            assertTrue("Unable to invalidate the exact concurrent cache generation", first.artifact.invalidate())
            val reopenedLeader = first.artifact.openRead()
            val reopenedFollower = second.artifact.openRead()
            reopenedLeader?.close()
            reopenedFollower?.close()
            assertNull("The invalidated leader artifact can still be opened", reopenedLeader)
            assertNull("The invalidated follower artifact can still be opened", reopenedFollower)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            probe.releaseProducer()
            val cleanup = mutableListOf<() -> Unit>()
            cleanup += { leader?.awaitForCleanup(); Unit }
            cleanup += { follower?.awaitForCleanup(); Unit }
            cleanup += { leader?.stop(); Unit }
            cleanup += { follower?.stop(); Unit }
            cleanup += { registration?.close() }
            cleanup += { cleanupDecision(leader?.decisionOrNull()) }
            cleanup += { cleanupDecision(follower?.decisionOrNull()) }
            cleanup += { restoreOptInStore(savedOptIn) }
            cleanup += { check(!program.exists() || program.delete()) { "Unable to delete the concurrent input JAR" } }
            cleanup += {
                check(!loaderCache.exists() || loaderCache.deleteRecursively()) {
                    "Unable to delete the concurrent AndroidClassLoader cache"
                }
            }
            finishCleanup(primaryFailure, cleanup)
        }
    }

    @Test
    fun interruptingFollowerReturnsCancelledWhileSharedLeaderStillPublishes() {
        val identity = requireRealProvider()
        val savedOptIn = snapshotOptInStore()
        val program = File(context.cacheDir, "dex-real-interrupt-${UUID.randomUUID()}.jar")
        val probe = BlockingProductionProbe()
        var registration: Closeable? = null
        var leader: RuntimeCall? = null
        var follower: RuntimeCall? = null
        var primaryFailure: Throwable? = null
        try {
            writeProgram(program)
            selectRealProvider(identity)
            val coordinator = productionCoordinator()
            registration = DexCompilerRuntimeAndroidTestInstrumentation.installForAndroidTest(probe)

            leader = RuntimeCall("dex-real-interrupt-leader") { coordinator.compile(program) }.also { it.start() }
            assertTrue("The interrupt leader did not reach its deterministic gate", probe.awaitProducer())
            follower = RuntimeCall("dex-real-interrupt-follower") { coordinator.compile(program) }.also { it.start() }
            assertTrue("The interrupt caller did not join as follower", probe.awaitFollower())

            checkNotNull(follower).interrupt()
            assertSame(DexCompilerRuntimeDecision.Cancelled, checkNotNull(follower).awaitDecision())
            assertEquals(1, probe.cancelledAfterFallbackCloseCount.get())
            assertEquals(0, probe.fallbackCloseFailureCount.get())

            probe.releaseProducer()
            val published = requirePublished(checkNotNull(leader).awaitDecision(), "remaining leader")
            assertFalse("The unique remaining leader unexpectedly used the cache", published.cacheHit)
            assertEquals(1, probe.leaderJoinCount.get())
            assertEquals(1, probe.followerJoinCount.get())
            assertEquals(1, probe.producerCount.get())
            assertEquals(1, probe.dispatchCount.get())
            requireNotNull(published.artifact.openRead()) {
                "The remaining caller did not receive a usable artifact"
            }.use { assertTrue("The remaining caller artifact is empty", it.read() >= 0) }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            probe.releaseProducer()
            val cleanup = mutableListOf<() -> Unit>()
            cleanup += { leader?.awaitForCleanup(); Unit }
            cleanup += { follower?.awaitForCleanup(); Unit }
            cleanup += { leader?.stop(); Unit }
            cleanup += { follower?.stop(); Unit }
            cleanup += { registration?.close() }
            cleanup += { cleanupDecision(leader?.decisionOrNull()) }
            cleanup += { cleanupDecision(follower?.decisionOrNull()) }
            cleanup += { restoreOptInStore(savedOptIn) }
            cleanup += { check(!program.exists() || program.delete()) { "Unable to delete the interrupt input JAR" } }
            finishCleanup(primaryFailure, cleanup)
        }
    }

    private fun productionCoordinator(): AndroidDexCompilerJarLoadCoordinator {
        AndroidDexCompilerRuntime.initialize(context, eligibleProcess = true)
        return requireNotNull(AndroidDexCompilerRuntime.coordinatorOrNull()) {
            "The production DEX compiler runtime was not initialized"
        }
    }

    private fun selectRealProvider(identity: DexCompilerPinnedIdentity) {
        assertEquals(
            DexCompilerOptInOptions(enabled = true, selectedComponent = identity.component),
            DexCompilerOptInStore.select(identity.component),
        )
    }

    private fun requireRealProvider(): DexCompilerPinnedIdentity {
        assumeTrue(
            "Real DEX compiler concurrency instrumentation is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assumeTrue("Production concurrency instrumentation requires a debug host build", BuildConfig.DEBUG)
        assertTrue("Real DEX compiler instrumentation requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals(HOST_PACKAGE, context.packageName)
        assertEquals(
            "The real DEX compiler and host must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        val candidate = AndroidDexCompilerExperimentalTransport(context)
            .discoverExactAction(DexCompilerContract.SERVICE_ACTION)
            .singleOrNull { it.component == REAL_COMPONENT }
        val exact = requireNotNull(candidate) { "The exact real DEX compiler component is unavailable" }
        assertTrue(exact.sameSignerAsHost)
        assertNull(DexCompilerExperimentalPolicy.rejection(exact, releaseBuild = !BuildConfig.DEBUG))
        return exact.pin()
    }

    private fun requirePublished(
        decision: DexCompilerRuntimeDecision,
        caller: String,
    ): DexCompilerRuntimeDecision.Published {
        if (decision is DexCompilerRuntimeDecision.Published) return decision
        if (decision is DexCompilerRuntimeDecision.UseLocal) decision.fallbackInput?.close()
        throw AssertionError("The production $caller did not publish: $decision")
    }

    private fun cleanupDecision(decision: DexCompilerRuntimeDecision?) {
        when (decision) {
            is DexCompilerRuntimeDecision.Published -> {
                decision.fallbackInput.close()
                decision.artifact.invalidate()
            }
            is DexCompilerRuntimeDecision.UseLocal -> decision.fallbackInput?.close()
            DexCompilerRuntimeDecision.Cancelled,
            null -> Unit
        }
    }

    private fun finishCleanup(primaryFailure: Throwable?, actions: List<() -> Unit>) {
        val failures = actions.mapNotNull { action -> runCatching(action).exceptionOrNull() }
        if (primaryFailure != null) {
            failures.forEach(primaryFailure::addSuppressed)
            return
        }
        val first = failures.firstOrNull() ?: return
        failures.drop(1).forEach(first::addSuppressed)
        throw first
    }

    private fun writeProgram(file: File) {
        FileOutputStream(file).use { output ->
            output.write(uniqueProgramJar())
            output.fd.sync()
        }
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

    private fun uniqueProgramJar(): ByteArray {
        val classBytes = fixtureClassBytes()
        val nonceBytes = UUID.randomUUID().toString().toByteArray(StandardCharsets.US_ASCII)
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeStoredEntry(FIXTURE_CLASS_ENTRY, classBytes)
                zip.writeStoredEntry("org/autojs/fixture/dexcompiler/concurrent-${UUID.randomUUID()}.txt", nonceBytes)
            }
            output.toByteArray()
        }
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

    /** A deterministic Java 8 class: public static int answer() { return 42; }. */
    private fun fixtureClassBytes(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(0xcafebabe.toInt())
            output.writeShort(0)
            output.writeShort(52)
            output.writeShort(14)
            output.writeUtf8Constant(FIXTURE_INTERNAL_NAME)
            output.writeByte(7); output.writeShort(1)
            output.writeUtf8Constant("java/lang/Object")
            output.writeByte(7); output.writeShort(3)
            output.writeUtf8Constant("<init>")
            output.writeUtf8Constant("()V")
            output.writeUtf8Constant("Code")
            output.writeByte(12); output.writeShort(5); output.writeShort(6)
            output.writeByte(10); output.writeShort(4); output.writeShort(8)
            output.writeUtf8Constant("answer")
            output.writeUtf8Constant("()I")
            output.writeUtf8Constant("SourceFile")
            output.writeUtf8Constant("RealProviderFixture.java")

            output.writeShort(0x0031)
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

    private class RuntimeCall(
        name: String,
        private val action: () -> DexCompilerRuntimeDecision,
    ) {
        private val decision = AtomicReference<DexCompilerRuntimeDecision?>()
        private val failure = AtomicReference<Throwable?>()
        private val finished = CountDownLatch(1)
        private val thread = Thread {
            try {
                decision.set(action())
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }.apply {
            this.name = name
            isDaemon = true
        }

        fun start() = thread.start()

        fun interrupt() = thread.interrupt()

        fun awaitDecision(): DexCompilerRuntimeDecision {
            assertTrue("Production runtime caller did not finish", finished.await(CALL_WAIT_MILLIS, TimeUnit.MILLISECONDS))
            failure.get()?.let { error ->
                throw AssertionError("Production runtime caller failed").also { it.initCause(error) }
            }
            return requireNotNull(decision.get()) { "Production runtime caller returned no decision" }
        }

        fun decisionOrNull(): DexCompilerRuntimeDecision? = decision.get()

        fun awaitForCleanup() {
            check(finished.await(CALL_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                "Production runtime caller did not finish during cleanup"
            }
        }

        fun stop() {
            if (!thread.isAlive) return
            thread.interrupt()
            thread.join(CALLER_SHUTDOWN_WAIT_MILLIS)
            check(!thread.isAlive) { "Production runtime caller did not terminate" }
        }
    }

    private class BlockingProductionProbe : DexCompilerRuntimeAndroidTestProbe {
        val leaderJoinCount = AtomicInteger()
        val followerJoinCount = AtomicInteger()
        val producerCount = AtomicInteger()
        val dispatchCount = AtomicInteger()
        val cancelledAfterFallbackCloseCount = AtomicInteger()
        val fallbackCloseFailureCount = AtomicInteger()
        private val producerStarted = CountDownLatch(1)
        private val followerJoined = CountDownLatch(1)
        private val producerRelease = CountDownLatch(1)

        override fun onSingleFlightJoined(isLeader: Boolean) {
            if (isLeader) {
                leaderJoinCount.incrementAndGet()
            } else {
                followerJoinCount.incrementAndGet()
                followerJoined.countDown()
            }
        }

        override fun onSharedProducerStarted() {
            producerCount.incrementAndGet()
            producerStarted.countDown()
            producerRelease.await(PROBE_GATE_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        }

        override fun onDispatchCommitted() {
            dispatchCount.incrementAndGet()
        }

        override fun onCallerCancelledAfterFallbackCloseAttempt() {
            cancelledAfterFallbackCloseCount.incrementAndGet()
        }

        override fun onFallbackCloseFailure() {
            fallbackCloseFailureCount.incrementAndGet()
        }

        fun awaitProducer(): Boolean = producerStarted.await(PROBE_EVENT_WAIT_MILLIS, TimeUnit.MILLISECONDS)

        fun awaitFollower(): Boolean = followerJoined.await(PROBE_EVENT_WAIT_MILLIS, TimeUnit.MILLISECONDS)

        fun releaseProducer() = producerRelease.countDown()
    }

    private data class OptInStoreSnapshot(
        val options: DexCompilerOptInOptions,
        val enabledPresent: Boolean,
        val enabled: Boolean?,
        val componentPresent: Boolean,
        val component: String?,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.dexCompiler.realProvider.enabled"
        const val OPT_IN_ENABLED_KEY = "dex_compiler_experimental_enabled"
        const val OPT_IN_COMPONENT_KEY = "dex_compiler_experimental_component"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.dexcompiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.DexCompilerService"
        val REAL_COMPONENT = DexCompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)

        const val FIXTURE_INTERNAL_NAME = "org/autojs/fixture/dexcompiler/RealProviderFixture"
        const val FIXTURE_CLASS_ENTRY = "$FIXTURE_INTERNAL_NAME.class"
        const val FIXTURE_CLASS_NAME = "org.autojs.fixture.dexcompiler.RealProviderFixture"
        const val ZIP_DOS_EPOCH_MILLIS = 315_532_800_000L
        const val PROBE_EVENT_WAIT_MILLIS = 30_000L
        const val PROBE_GATE_WAIT_MILLIS = 60_000L
        const val CALL_WAIT_MILLIS = DexCompilerContract.DEFAULT_TIMEOUT_MILLIS + 30_000L
        const val CALLER_SHUTDOWN_WAIT_MILLIS = 5_000L
    }
}
