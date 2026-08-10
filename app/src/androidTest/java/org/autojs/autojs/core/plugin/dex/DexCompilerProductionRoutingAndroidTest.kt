package org.autojs.autojs.core.plugin.dex

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.rhino.AndroidClassLoader
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Production-entry routing and local-fallback ownership, without a fake Binder provider. */
class DexCompilerProductionRoutingAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Before
    fun settleBefore() {
        service.stopAll()
        awaitNoRegisteredEngines("before DEX production-routing test")
    }

    @After
    fun settleAfter() {
        service.stopAll()
        awaitNoRegisteredEngines("after DEX production-routing test")
    }

    @Test
    fun publicRuntimeLoadJarUsesExperimentalBoundaryAndFallsBackExactlyOnce() {
        val workDir = freshWorkDir("public-runtime")
        val program = File(workDir, PROGRAM_NAME).apply { writeBytes(programJar()) }
        val marker = File(workDir, MARKER_NAME)
        val probe = RecordingProbe {
            DexCompilerRuntimeDecision.UseLocal(DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED)
        }

        try {
            withProbe(probe) {
                val engineClass = runSuccessfulRhinoLoad(workDir, marker, RHINO_LOAD_JAR_SOURCE)
                assertTrue(
                    "runtime.loadJar did not execute through the production Rhino engine",
                    RhinoJavaScriptEngine::class.java.isAssignableFrom(engineClass),
                )
            }

            assertEquals("runtime.loadJar did not reach the experimental boundary exactly once", 1, probe.compileRequests.get())
            assertEquals("The local compiler was not entered exactly once", 1, probe.localInputs.size)
            assertEquals(program.canonicalPath, probe.compileInputs.single().canonicalPath)
            assertEquals(program.canonicalPath, probe.localInputs.single().canonicalPath)
        } finally {
            assertTrue("Unable to remove public runtime.loadJar fixture", workDir.deleteRecursively())
        }
    }

    @Test
    fun publicRuntimeLoadCompatibilityJarStaysLocalAndNeverReachesExperimentalBoundary() {
        val workDir = freshWorkDir("public-compatibility-load")
        val program = File(workDir, PROGRAM_NAME).apply { writeBytes(programJar()) }
        val marker = File(workDir, MARKER_NAME)
        val probe = RecordingProbe { DexCompilerRuntimeDecision.Cancelled }

        try {
            withProbe(probe) {
                runSuccessfulRhinoLoad(workDir, marker, RHINO_COMPATIBILITY_LOAD_SOURCE)
            }

            assertTrue("runtime.load(raw JAR) reached the experimental boundary", probe.compileInputs.isEmpty())
            assertEquals("runtime.load(raw JAR) did not use exactly one local conversion", 1, probe.localInputs.size)
            assertEquals(program.canonicalPath, probe.localInputs.single().canonicalPath)
        } finally {
            assertTrue("Unable to remove public runtime.load fixture", workDir.deleteRecursively())
        }
    }

    @Test
    fun mutatingOriginalAfterDecisionSnapshotStillCompilesPinnedFallback() {
        val workDir = freshWorkDir("snapshot-mutation")
        val program = File(workDir, PROGRAM_NAME).apply { writeBytes(programJar()) }
        val loaderCache = File(workDir, "loader-cache")
        val retainedRef = AtomicReference<TestRetainedInput>()
        val probe = RecordingProbe { input ->
            val snapshot = File(workDir, "retained-${UUID.randomUUID()}.jar")
            input.copyTo(snapshot, overwrite = false)
            val retained = TestRetainedInput(snapshot)
            check(retainedRef.compareAndSet(null, retained)) { "Snapshot decision executed more than once" }
            input.writeBytes(INVALID_MUTATED_PROGRAM)
            DexCompilerRuntimeDecision.UseLocal(
                reason = DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED,
                fallbackInput = retained,
            )
        }

        try {
            withProbe(probe) {
                val fixture = AndroidClassLoader(
                    parent = requireNotNull(javaClass.classLoader),
                    cacheDir = loaderCache,
                ).loadJarWithExperimentalCompiler(program).loadClass(FIXTURE_CLASS_NAME)
                assertFixture(fixture)
            }

            val retained = assertNotNull("The test seam did not capture a retained snapshot", retainedRef.get())
                .let { retainedRef.get()!! }
            assertTrue("The original input was not mutated after snapshot capture", program.readBytes().contentEquals(INVALID_MUTATED_PROGRAM))
            assertEquals("Snapshot fallback was not closed exactly once", 1, retained.closeCount.get())
            assertFalse("Closed retained snapshot still exists", retained.snapshot.exists())
            assertEquals("Snapshot fallback entered the local compiler more than once", 1, probe.localInputs.size)
            assertTrue(
                "The local compiler used the mutable original instead of the retained snapshot",
                probe.localInputs.single().name.startsWith("retained-"),
            )
        } finally {
            retainedRef.get()?.forceCleanup()
            assertTrue("Unable to remove snapshot-mutation fixture", workDir.deleteRecursively())
        }
    }

    @Test
    fun verifiedAdoptionFailureInvalidatesAndFallsBackToRetainedSnapshotExactlyOnce() {
        val workDir = freshWorkDir("adoption-failure")
        val program = File(workDir, PROGRAM_NAME).apply { writeBytes(programJar()) }
        val loaderCache = File(workDir, "loader-cache")
        val retained = TestRetainedInput(File(workDir, "retained.jar").apply { writeBytes(program.readBytes()) })
        val artifact = FailingVerifiedArtifact()
        val probe = RecordingProbe {
            DexCompilerRuntimeDecision.Published(
                artifact = artifact,
                fallbackInput = retained,
                cacheHit = false,
            )
        }

        try {
            withProbe(probe) {
                val fixture = AndroidClassLoader(
                    parent = requireNotNull(javaClass.classLoader),
                    cacheDir = loaderCache,
                ).loadJarWithExperimentalCompiler(program).loadClass(FIXTURE_CLASS_NAME)
                assertFixture(fixture)
            }

            assertEquals("Published decision did not enter the production boundary once", 1, probe.compileRequests.get())
            assertEquals("Adoption failure did not start exactly one local fallback", 1, probe.localInputs.size)
            assertEquals("Failed artifact was not invalidated exactly once", 1, artifact.invalidateCount.get())
            assertEquals("Failed artifact was not opened exactly once", 1, artifact.openCount.get())
            assertEquals("Failed artifact source was not closed exactly once", 1, artifact.sourceCloseCount.get())
            assertEquals("Retained fallback ownership was not closed exactly once", 1, retained.closeCount.get())
            assertFalse("Closed adoption fallback snapshot still exists", retained.snapshot.exists())
            assertTrue(
                "Adoption failure left a partial artifact",
                !loaderCache.exists() || loaderCache.walkTopDown().none { it.name.endsWith(".partial") },
            )
        } finally {
            retained.forceCleanup()
            assertTrue("Unable to remove adoption-failure fixture", workDir.deleteRecursively())
        }
    }

    @Test
    fun cancelledProductionDecisionNeverStartsLocalCompiler() {
        val workDir = freshWorkDir("cancelled")
        val program = File(workDir, PROGRAM_NAME).apply { writeBytes(programJar()) }
        val loaderCache = File(workDir, "loader-cache")
        val probe = RecordingProbe { DexCompilerRuntimeDecision.Cancelled }

        try {
            var cancellation: Throwable? = null
            withProbe(probe) {
                try {
                    AndroidClassLoader(
                        parent = requireNotNull(javaClass.classLoader),
                        cacheDir = loaderCache,
                    ).loadJarWithExperimentalCompiler(program)
                } catch (error: Throwable) {
                    cancellation = error
                }
            }

            val failure = assertNotNull("Cancelled decision did not fail the caller", cancellation).let { cancellation!! }
            assertTrue("Cancelled decision did not surface ScriptInterruptedException: $failure", failure.hasCause<ScriptInterruptedException>())
            assertEquals("Cancelled raw JAR did not reach the decision boundary once", 1, probe.compileRequests.get())
            assertTrue("Caller cancellation incorrectly started a local fallback", probe.localInputs.isEmpty())
        } finally {
            assertTrue("Unable to remove cancellation fixture", workDir.deleteRecursively())
        }
    }

    @Test
    fun sameJarProductionDecisionsRunOutsideLocalConversionMonitorAndFollowerInterrupts() {
        val workDir = freshWorkDir("same-jar-concurrency")
        val program = File(workDir, PROGRAM_NAME).apply { writeBytes(programJar()) }
        val loader = newLoader(workDir, "same-jar")
        val leaderEntered = CountDownLatch(1)
        val followerEntered = CountDownLatch(1)
        val releaseLeader = CountDownLatch(1)
        val holdFollower = CountDownLatch(1)
        val leaderDone = CountDownLatch(1)
        val followerDone = CountDownLatch(1)
        val decisionOrdinal = AtomicInteger()
        val localStarts = AtomicInteger()
        val leaderFailure = AtomicReference<Throwable>()
        val followerFailure = AtomicReference<Throwable>()
        val probe = object : DexCompilerRuntimeAndroidTestProbe {
            override fun overrideCompileDecision(jar: File): DexCompilerRuntimeDecision {
                return when (decisionOrdinal.incrementAndGet()) {
                    1 -> {
                        leaderEntered.countDown()
                        releaseLeader.await()
                        DexCompilerRuntimeDecision.Cancelled
                    }
                    2 -> {
                        followerEntered.countDown()
                        holdFollower.await()
                        DexCompilerRuntimeDecision.Cancelled
                    }
                    else -> error("The same JAR reached the decision seam more than twice")
                }
            }

            override fun onLocalCompilerStarted(jar: File) {
                localStarts.incrementAndGet()
            }
        }
        val leader = loadingThread("dex-routing-leader", loader, program, leaderFailure, leaderDone)
        val follower = loadingThread("dex-routing-follower", loader, program, followerFailure, followerDone)

        try {
            withProbe(probe) {
                try {
                    leader.start()
                    assertTrue(
                        "Leader never reached the production decision boundary",
                        leaderEntered.await(CONCURRENCY_WAIT_SECONDS, TimeUnit.SECONDS),
                    )
                    follower.start()
                    assertTrue(
                        "Follower was blocked by the local conversion monitor before single-flight",
                        followerEntered.await(CONCURRENCY_WAIT_SECONDS, TimeUnit.SECONDS),
                    )

                    follower.interrupt()
                    assertTrue(
                        "Interrupted follower remained blocked while the leader decision was in flight",
                        followerDone.await(CONCURRENCY_WAIT_SECONDS, TimeUnit.SECONDS),
                    )
                    assertEquals("Leader unexpectedly completed before its decision was released", 1L, leaderDone.count)
                    val interruptedFailure = assertNotNull(
                        "Interrupted follower did not fail",
                        followerFailure.get(),
                    ).let { followerFailure.get()!! }
                    assertTrue(
                        "Follower interruption did not surface ScriptInterruptedException: $interruptedFailure",
                        interruptedFailure.hasCause<ScriptInterruptedException>(),
                    )

                    releaseLeader.countDown()
                    assertTrue(
                        "Released leader did not finish",
                        leaderDone.await(CONCURRENCY_WAIT_SECONDS, TimeUnit.SECONDS),
                    )
                    val releasedFailure = assertNotNull(
                        "Released Cancelled leader did not fail",
                        leaderFailure.get(),
                    ).let { leaderFailure.get()!! }
                    assertTrue(releasedFailure.hasCause<ScriptInterruptedException>())
                } finally {
                    releaseLeader.countDown()
                    holdFollower.countDown()
                    leader.interrupt()
                    follower.interrupt()
                    leader.join(THREAD_JOIN_MILLIS)
                    follower.join(THREAD_JOIN_MILLIS)
                    assertFalse("Leader thread leaked from routing gate", leader.isAlive)
                    assertFalse("Follower thread leaked from routing gate", follower.isAlive)
                }
            }

            assertEquals("Same JAR did not admit exactly two concurrent decisions", 2, decisionOrdinal.get())
            assertEquals("Cancelled concurrent decisions unexpectedly started local conversion", 0, localStarts.get())
        } finally {
            releaseLeader.countDown()
            holdFollower.countDown()
            leader.interrupt()
            follower.interrupt()
            assertTrue("Unable to remove same-JAR concurrency fixture", workDir.deleteRecursively())
        }
    }

    @Test
    fun compatibilityAarDexAndDefineClassPathsNeverReachExperimentalBoundary() {
        val workDir = freshWorkDir("non-raw-routing")
        val programBytes = programJar()
        val program = File(workDir, PROGRAM_NAME).apply { writeBytes(programBytes) }
        val aar = File(workDir, "fixture.aar").apply { writeBytes(aarWithClassesJar(programBytes)) }
        val invalidDex = File(workDir, "fixture.dex").apply { writeBytes(INVALID_DEX) }
        val probe = RecordingProbe { DexCompilerRuntimeDecision.Cancelled }

        try {
            withProbe(probe) {
                val compatibilityLoader = newLoader(workDir, "compatibility")
                assertFixture(compatibilityLoader.loadJar(program).loadClass(FIXTURE_CLASS_NAME))

                val aarLoader = newLoader(workDir, "aar")
                assertFixture(aarLoader.loadAar(aar).loadClass(FIXTURE_CLASS_NAME))

                val defineLoader = newLoader(workDir, "define")
                assertFixture(defineLoader.defineClass(FIXTURE_CLASS_NAME, fixtureClassBytes()))

                // DexClassLoader may validate eagerly or lazily depending on API/ART. Either outcome
                // is local-only; the routing assertion below is the contract under test.
                runCatching { newLoader(workDir, "dex").loadDex(invalidDex) }
            }

            assertTrue(
                "A compatibility/AAR/DEX/defineClass path reached the experimental raw-JAR boundary",
                probe.compileInputs.isEmpty(),
            )
            assertEquals("Only compatibility loadJar and AAR's local merged JAR should compile locally", 2, probe.localInputs.size)
        } finally {
            assertTrue("Unable to remove non-raw routing fixture", workDir.deleteRecursively())
        }
    }

    @Test
    fun decisionProbeFaultsAreIsolatedAndInterruptedOverridesReleaseOwnership() {
        val workDir = freshWorkDir("probe-isolation")
        val program = File(workDir, PROGRAM_NAME).apply { writeBytes(programJar()) }
        val retained = TestRetainedInput(File(workDir, "retained.jar").apply { writeBytes(program.readBytes()) })

        try {
            val throwingProbe = object : DexCompilerRuntimeAndroidTestProbe {
                override fun overrideCompileDecision(jar: File): DexCompilerRuntimeDecision? {
                    throw AssertionError("debug probe failure")
                }
            }
            withProbe(throwingProbe) {
                assertNull(
                    "A faulting debug probe replaced the production decision",
                    DexCompilerRuntimeAndroidTestInstrumentation.compileDecisionOverrideOrNull(program),
                )
                assertFalse("A non-interrupt probe fault changed the caller interrupt state", Thread.currentThread().isInterrupted)
            }

            val interruptingProbe = object : DexCompilerRuntimeAndroidTestProbe {
                override fun overrideCompileDecision(jar: File): DexCompilerRuntimeDecision {
                    Thread.currentThread().interrupt()
                    return DexCompilerRuntimeDecision.UseLocal(
                        reason = DexCompilerLocalReason.REMOTE_ATTEMPT_FAILED,
                        fallbackInput = retained,
                    )
                }
            }
            withProbe(interruptingProbe) {
                assertNull(
                    "An interrupted debug override escaped into the production decision path",
                    DexCompilerRuntimeAndroidTestInstrumentation.compileDecisionOverrideOrNull(program),
                )
                assertTrue("The debug seam swallowed caller interruption", Thread.currentThread().isInterrupted)
                assertEquals("Discarded override did not close its retained ownership", 1, retained.closeCount.get())
            }
        } finally {
            // Do not leak the deliberately asserted interrupt into JUnit or the next instrumentation.
            Thread.interrupted()
            retained.forceCleanup()
            assertTrue("Unable to remove probe-isolation fixture", workDir.deleteRecursively())
        }
    }

    private fun runSuccessfulRhinoLoad(
        workDir: File,
        marker: File,
        sourceText: String,
    ): Class<*> {
        val completed = CountDownLatch(1)
        val startedEngineClass = AtomicReference<Class<*>>()
        val failure = AtomicReference<Throwable>()
        val sourceFile = File(workDir, SCRIPT_NAME).apply { writeText(sourceText) }
        val source = JavaScriptFileSource(sourceFile)
        assertEquals(JavaScriptSource.ENGINE, source.engineName)

        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                startedEngineClass.set(execution.engine.javaClass)
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }

        val execution = service.execute(
            source,
            listener,
            ExecutionConfig(workingDirectory = workDir.absolutePath),
        )
        if (!completed.await(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            service.stopAll()
            throw AssertionError("Timed out waiting for runtime bytecode-load regression script")
        }
        awaitEngineRemoved(execution.id)
        failure.get()?.let { throw AssertionError("Runtime bytecode-load regression script failed", it) }
        assertEquals("ok", marker.takeIf(File::isFile)?.readText())
        return checkNotNull(startedEngineClass.get()) { "Script execution did not report its engine" }
    }

    private fun withProbe(probe: DexCompilerRuntimeAndroidTestProbe, block: () -> Unit) {
        val registration: Closeable = DexCompilerRuntimeAndroidTestInstrumentation.installForAndroidTest(probe)
        try {
            block()
        } finally {
            registration.close()
        }
    }

    private fun newLoader(workDir: File, label: String): AndroidClassLoader = AndroidClassLoader(
        parent = requireNotNull(javaClass.classLoader),
        cacheDir = File(workDir, "loader-$label"),
    )

    private fun loadingThread(
        name: String,
        loader: AndroidClassLoader,
        program: File,
        failure: AtomicReference<Throwable>,
        done: CountDownLatch,
    ): Thread = Thread({
        try {
            loader.loadJarWithExperimentalCompiler(program)
        } catch (error: Throwable) {
            failure.set(error)
        } finally {
            done.countDown()
        }
    }, name).apply { isDaemon = true }

    private fun assertFixture(clazz: Class<*>) {
        assertEquals(FIXTURE_CLASS_NAME, clazz.name)
        assertEquals(42, (clazz.getDeclaredMethod("answer").invoke(null) as Number).toInt())
    }

    private fun freshWorkDir(label: String): File =
        File(context.cacheDir, "dex-production-routing-$label-${UUID.randomUUID()}").apply {
            check(mkdirs()) { "Unable to create DEX production-routing fixture: $absolutePath" }
        }

    private fun awaitEngineRemoved(executionId: Int) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId }) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("Engine $executionId remained registered after runtime bytecode load")
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

    private fun programJar(): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.writeStoredEntry(FIXTURE_CLASS_ENTRY, fixtureClassBytes())
        }
        output.toByteArray()
    }

    private fun aarWithClassesJar(program: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.writeStoredEntry("classes.jar", program)
        }
        output.toByteArray()
    }

    /** A deterministic Java 8 class: public static int answer() { return 42; }. */
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
            output.writeUtf8Constant("answer") // #10
            output.writeUtf8Constant("()I") // #11
            output.writeUtf8Constant("SourceFile") // #12
            output.writeUtf8Constant("ProductionRoutingFixture.java") // #13

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

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        val pending = ArrayDeque<Throwable>()
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        pending.add(this)
        while (pending.isNotEmpty() && seen.size < MAX_FAILURE_GRAPH_NODES) {
            val current = pending.removeFirst()
            if (!seen.add(current)) continue
            if (current is T) return true
            current.cause?.let(pending::addLast)
            current.suppressed.forEach(pending::addLast)
        }
        return false
    }

    private class RecordingProbe(
        private val decision: (File) -> DexCompilerRuntimeDecision?,
    ) : DexCompilerRuntimeAndroidTestProbe {
        val compileRequests = AtomicInteger()
        val compileInputs = CopyOnWriteArrayList<File>()
        val localInputs = CopyOnWriteArrayList<File>()

        override fun overrideCompileDecision(jar: File): DexCompilerRuntimeDecision? {
            compileRequests.incrementAndGet()
            compileInputs += jar
            return decision(jar)
        }

        override fun onLocalCompilerStarted(jar: File) {
            localInputs += jar
        }
    }

    private class TestRetainedInput(
        val snapshot: File,
    ) : DexCompilerRetainedInput {
        private val closed = AtomicBoolean(false)
        val closeCount = AtomicInteger()
        override val sizeBytes: Long = snapshot.length()
        override val sha256: String = snapshot.sha256()

        override fun resolve(): File? = snapshot.takeIf { !closed.get() && it.isFile }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            closeCount.incrementAndGet()
            check(!snapshot.exists() || snapshot.delete()) { "Unable to delete retained test snapshot" }
        }

        fun forceCleanup() {
            if (snapshot.exists()) snapshot.delete()
        }
    }

    private class FailingVerifiedArtifact : DexCompilerVerifiedArtifact {
        val invalidateCount = AtomicInteger()
        val openCount = AtomicInteger()
        val sourceCloseCount = AtomicInteger()
        override val cacheKeySha256: String = "1".repeat(64)
        override val outputSha256: String = "2".repeat(64)
        override val sizeBytes: Long = 1L

        override fun openRead(): InputStream {
            openCount.incrementAndGet()
            val closed = AtomicBoolean(false)
            return object : InputStream() {
                override fun read(): Int = throw IOException("injected verified-adoption read failure")

                override fun close() {
                    if (closed.compareAndSet(false, true)) sourceCloseCount.incrementAndGet()
                }
            }
        }

        override fun invalidate(): Boolean {
            invalidateCount.incrementAndGet()
            return true
        }
    }

    private companion object {
        const val PROGRAM_NAME = "program.jar"
        const val MARKER_NAME = "completed.marker"
        const val SCRIPT_NAME = "runtime-load-jar.js"
        const val FIXTURE_INTERNAL_NAME = "org/autojs/fixture/dexcompiler/ProductionRoutingFixture"
        const val FIXTURE_CLASS_ENTRY = "$FIXTURE_INTERNAL_NAME.class"
        const val FIXTURE_CLASS_NAME = "org.autojs.fixture.dexcompiler.ProductionRoutingFixture"
        const val ZIP_DOS_EPOCH_MILLIS = 315_532_800_000L
        const val SCRIPT_TIMEOUT_SECONDS = 90L
        const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 5_000L
        const val ENGINE_REMOVAL_POLL_MILLIS = 25L
        const val CONCURRENCY_WAIT_SECONDS = 5L
        const val THREAD_JOIN_MILLIS = 5_000L
        const val MAX_FAILURE_GRAPH_NODES = 64

        val INVALID_MUTATED_PROGRAM = "mutated-after-snapshot".toByteArray()
        val INVALID_DEX = "not-a-dex".toByteArray()
        val RHINO_LOAD_JAR_SOURCE =
            """
            "rhino";
            runtime.loadJar("./$PROGRAM_NAME");
            files.write("./$MARKER_NAME", "ok");
            """.trimIndent()
        val RHINO_COMPATIBILITY_LOAD_SOURCE =
            """
            "rhino";
            runtime.load("./$PROGRAM_NAME");
            files.write("./$MARKER_NAME", "ok");
            """.trimIndent()
    }
}

private fun File.sha256(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
