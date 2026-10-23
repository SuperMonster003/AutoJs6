package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.lua.LuaRuntimeHostCapabilities
import org.autojs.autojs.core.plugin.scope.BridgeExecutionScope
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.plugin.lua.runtime.api.LuaCancellationReason
import org.autojs.plugin.lua.runtime.api.LuaExecutionCancellation
import org.autojs.plugin.lua.runtime.api.LuaExecutionError
import org.autojs.plugin.lua.runtime.api.LuaExecutionErrorCode
import org.autojs.plugin.lua.runtime.api.LuaExecutionFailurePhase
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.autojs.plugin.lua.runtime.api.LuaExecutionResult
import org.autojs.plugin.lua.runtime.api.LuaExecutionStarted
import org.autojs.plugin.lua.runtime.api.LuaOutputChunk
import org.autojs.plugin.lua.runtime.api.LuaOutputStream
import org.autojs.plugin.lua.runtime.api.LuaProtocolVersion
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeFamily
import org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo
import org.autojs.plugin.lua.runtime.api.LuaRuntimeLimits
import org.autojs.plugin.lua.runtime.api.LuaRetryDisposition
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.Reader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LuaPluginScriptEngineTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun constructorAndPutAreLocalWhileInitPreparesProvider() {
        val backend = FakeBackend(Mode.COMPLETE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)

        engine.put("context", Any())
        assertEquals(0, backend.prepareCount)
        engine.init()
        assertEquals(1, backend.prepareCount)

        assertEquals(LuaValue.Int64Value(7L), engine.execute(source()))
        assertEquals(1, backend.acquireCount)
        assertEquals(128, backend.lastSession?.grantedCredits)
        assertEquals(1, backend.lastSession?.startCount)
        assertEquals(1, backend.lastSession?.closeCount)
        assertEquals(true, backend.lastScope?.isClosed)
        assertEquals(1, backend.connectionCloseCount)
        assertEquals(listOf("from-fake"), backend.outputs)
        assertEquals(LuaValue.MapValue(emptyMap()), backend.lastRequest?.arguments)
        assertEquals(
            listOf(
                LuaRuntimeHostCapabilities.DEVICE_INFO,
                LuaRuntimeHostCapabilities.UI_TOAST,
            ),
            backend.lastRequest?.requiredCapabilities,
        )
        assertTrue(temporaryFolder.root.resolve("lua-source-snapshots").listFiles().orEmpty().isEmpty())

        engine.destroy()
        engine.destroy()
        assertTrue(engine.isDestroyed)
    }

    @Test
    fun providerWithoutOptionalHostCapabilityStillRunsBasicLua() {
        val backend = FakeBackend(Mode.COMPLETE, EMPTY_CAPABILITY_INFO)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)

        engine.init()
        assertEquals(LuaValue.Int64Value(7L), engine.execute(source()))

        assertEquals(emptyList<String>(), backend.lastRequest?.requiredCapabilities)
        engine.destroy()
    }

    @Test
    fun providerWithoutModuleCapabilityRejectsAnExplicitModuleDirectoryBeforeDispatch() {
        val project = temporaryFolder.newFolder("legacy-provider-project")
        val entry = project.resolve("main.lua").apply { writeText("return require('helper')") }
        project.resolve("main.modules").apply { mkdir() }.resolve("helper.lua").writeText("return 1")
        val backend = FakeBackend(Mode.COMPLETE, EMPTY_CAPABILITY_INFO)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)

        engine.init()
        val failure = assertThrows(LuaRuntimeExecutionException::class.java) {
            engine.execute(LuaFileSource(entry))
        }

        assertTrue(failure.message.orEmpty().contains("does not support controlled module snapshots"))
        assertEquals(0, backend.createCount)
        engine.destroy()
    }

    @Test
    fun grantedModuleCapabilityFreezesModulesBeforeCreatingTheRemoteSession() {
        val project = temporaryFolder.newFolder("lua-project")
        val entry = File(project, "main.lua").apply { writeText("return require('helper')") }
        val helper = File(project, "main.modules/helper.lua").apply {
            checkNotNull(parentFile).mkdirs()
            writeText("return 'before'")
        }
        val backend = FakeBackend(Mode.COMPLETE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)

        engine.init()
        engine.execute(LuaFileSource(entry))
        helper.writeText("return 'after'")

        assertEquals("return 'before'", backend.lastModules?.sourceFor("helper")?.toString(Charsets.UTF_8))
        engine.destroy()
    }

    @Test
    fun executionArgumentsAreFrozenBeforeProviderPreparationAndIgnorePutGlobals() {
        val backend = FakeBackend(Mode.COMPLETE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        val payload = byteArrayOf(1, 2, 3)
        val items = mutableListOf<Any>(7, "second")
        val metadata = linkedMapOf<String, Any>("name" to "before", "ratio" to 1.5)
        val config = ExecutionConfig().apply {
            setArgument("enabled", true)
            setArgument("items", items)
            setArgument("meta", metadata)
            setArgument("payload", payload)
        }
        engine.setTag(ExecutionConfig.tag, config)
        engine.put("context", mapOf("mustNotLeak" to true))

        engine.init()
        payload[0] = 99
        items += "late"
        metadata["name"] = "after"
        config.setArgument("late", "ignored")

        engine.execute(source())

        assertEquals(
            LuaValue.MapValue(
                mapOf(
                    "enabled" to LuaValue.BooleanValue(true),
                    "items" to LuaValue.ArrayValue(
                        listOf(LuaValue.Int64Value(7L), LuaValue.StringValue("second")),
                    ),
                    "meta" to LuaValue.MapValue(
                        mapOf(
                            "name" to LuaValue.StringValue("before"),
                            "ratio" to LuaValue.Float64Value(1.5),
                        ),
                    ),
                    "payload" to LuaValue.BytesValue(byteArrayOf(1, 2, 3)),
                ),
            ),
            backend.lastRequest?.arguments,
        )
        engine.destroy()
    }

    @Test
    fun unsupportedExecutionArgumentFailsBeforeProviderPreparation() {
        val backend = FakeBackend(Mode.COMPLETE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        engine.setTag(
            ExecutionConfig.tag,
            ExecutionConfig().apply { setArgument("unsupported", Any()) },
        )

        val failure = assertThrows(IllegalArgumentException::class.java) { engine.init() }

        assertTrue(failure.message.orEmpty().contains("unsupported type"))
        assertEquals(0, backend.prepareCount)
        assertEquals(0, backend.acquireCount)
        assertEquals(0, backend.createCount)
        engine.destroy()
    }

    @Test
    fun initFailureDoesNotCreateOrDispatchExecution() {
        val backend = FakeBackend(Mode.PREPARE_FAILURE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)

        assertThrows(IllegalStateException::class.java) { engine.init() }
        assertEquals(1, backend.prepareCount)
        assertEquals(0, backend.acquireCount)
        assertFalse(engine.isDestroyed)
        engine.destroy()
    }

    @Test
    fun interruptedPrepareAndAcquireMapToScriptInterruption() {
        val prepareBackend = FakeBackend(Mode.PREPARE_INTERRUPTED)
        val prepareEngine = LuaPluginScriptEngine(temporaryFolder.root, prepareBackend)
        try {
            assertThrows(ScriptInterruptedException::class.java) { prepareEngine.init() }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
            prepareEngine.destroy()
        }

        val acquireBackend = FakeBackend(Mode.ACQUIRE_INTERRUPTED)
        val acquireEngine = LuaPluginScriptEngine(temporaryFolder.root, acquireBackend)
        acquireEngine.init()
        try {
            assertThrows(ScriptInterruptedException::class.java) { acquireEngine.execute(source()) }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
            acquireEngine.destroy()
        }
    }

    @Test
    fun creationTimeTransportViolationPreventsStartDispatch() {
        val backend = FakeBackend(Mode.CREATION_VIOLATION)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        engine.init()

        assertThrows(LuaRuntimeExecutionException::class.java) { engine.execute(source()) }

        assertEquals(0, backend.lastSession?.startCount)
        assertEquals(1, backend.lastSession?.cancelCount)
        assertEquals(1, backend.lastSession?.closeCount)
        assertEquals(1, backend.connectionCloseCount)
        engine.destroy()
    }

    @Test
    fun forceStopCancelsActiveSessionAndUnblocksExecutionThread() {
        val backend = FakeBackend(Mode.STALL)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend, terminalGraceMillis = 20L)
        val failure = AtomicReference<Throwable?>()
        val executionThread = Thread {
            try {
                engine.init()
                engine.execute(source())
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply { isDaemon = true }
        executionThread.start()
        assertTrue(backend.started.await(5, TimeUnit.SECONDS))

        engine.forceStop()
        executionThread.join(5_000L)

        assertFalse(executionThread.isAlive)
        assertTrue(failure.get() is ScriptInterruptedException)
        assertEquals(1, backend.lastSession?.cancelCount)
        assertEquals(1, backend.lastSession?.closeCount)
        assertEquals(1, backend.connectionCloseCount)
        engine.destroy()
    }

    @Test
    fun forceStopInterruptsBlockedPreparationWithoutAcquiringAProvider() {
        val backend = FakeBackend(Mode.BLOCK_PREPARE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        val failure = AtomicReference<Throwable?>()
        val executionThread = Thread {
            try {
                engine.init()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply { isDaemon = true }
        executionThread.start()
        assertTrue(backend.prepareEntered.await(5, TimeUnit.SECONDS))

        engine.forceStop()
        executionThread.join(5_000L)

        assertFalse(executionThread.isAlive)
        assertTrue(failure.get() is ScriptInterruptedException)
        assertEquals(1, backend.prepareCount)
        assertEquals(0, backend.acquireCount)
        assertEquals(0, backend.connectionCloseCount)
        engine.destroy()
    }

    @Test
    fun forceStopInterruptsBlockedAcquireAndCleansTheSourceSnapshot() {
        val backend = FakeBackend(Mode.BLOCK_ACQUIRE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        val failure = AtomicReference<Throwable?>()
        engine.init()
        val executionThread = Thread {
            try {
                engine.execute(source())
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply { isDaemon = true }
        executionThread.start()
        assertTrue(backend.acquireEntered.await(5, TimeUnit.SECONDS))

        engine.forceStop()
        executionThread.join(5_000L)

        assertFalse(executionThread.isAlive)
        assertTrue(failure.get() is ScriptInterruptedException)
        assertEquals(1, backend.acquireCount)
        assertEquals(0, backend.createCount)
        assertEquals(0, backend.connectionCloseCount)
        assertSnapshotsClean()
        engine.destroy()
    }

    @Test
    fun forceStopInterruptsBlockedCreationAndClosesConnectionAndSnapshot() {
        val backend = FakeBackend(Mode.BLOCK_CREATE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        val failure = AtomicReference<Throwable?>()
        engine.init()
        val executionThread = Thread {
            try {
                engine.execute(source())
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply { isDaemon = true }
        executionThread.start()
        assertTrue(backend.createEntered.await(5, TimeUnit.SECONDS))

        engine.forceStop()
        executionThread.join(5_000L)

        assertFalse(executionThread.isAlive)
        assertTrue(failure.get() is ScriptInterruptedException)
        assertEquals(1, backend.createCount)
        assertEquals(1, backend.connectionCloseCount)
        assertNull(backend.lastSession)
        assertSnapshotsClean()
        engine.destroy()
    }

    @Test
    fun providerFailureIsClosedAndNotReplayed() {
        val backend = FakeBackend(Mode.FAST_FAILURE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        engine.init()

        val failure = assertThrows(LuaProviderExecutionException::class.java) { engine.execute(source()) }

        assertEquals(LuaExecutionErrorCode.RUNTIME_ERROR, failure.providerError.code)
        assertSingleClosedDispatch(backend)
        engine.destroy()
    }

    @Test
    fun postDispatchTransportDeathIsClosedAndNotReplayed() {
        val backend = FakeBackend(Mode.TRANSPORT_AFTER_START)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        engine.init()

        assertThrows(LuaRuntimeExecutionException::class.java) { engine.execute(source()) }

        assertSingleClosedDispatch(backend)
        engine.destroy()
    }

    @Test
    fun forceStopDuringLoopIntervalPreventsAnotherExecutionDispatch() {
        val backend = FakeBackend(Mode.COMPLETE)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend)
        val firstIterationFinished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val executionThread = Thread {
            try {
                engine.init()
                repeat(2) {
                    engine.execute(source())
                    firstIterationFinished.countDown()
                    Thread.sleep(60_000L)
                }
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply { isDaemon = true }
        executionThread.start()
        assertTrue(firstIterationFinished.await(5, TimeUnit.SECONDS))

        engine.forceStop()
        executionThread.join(5_000L)

        assertFalse(executionThread.isAlive)
        assertTrue(failure.get() is InterruptedException)
        assertEquals(1, backend.acquireCount)
        assertEquals(1, backend.createCount)
        assertEquals(1, backend.lastSession?.startCount)
        assertSnapshotsClean()
        engine.destroy()
    }

    @Test
    fun destroyIsIdempotentWhileAnActiveSessionAndExecutionThreadAreCleanedUp() {
        val backend = FakeBackend(Mode.STALL)
        val engine = LuaPluginScriptEngine(temporaryFolder.root, backend, terminalGraceMillis = 20L)
        val failure = AtomicReference<Throwable?>()
        val executionThread = Thread {
            try {
                engine.init()
                engine.execute(source())
            } catch (error: Throwable) {
                failure.set(error)
            }
        }.apply { isDaemon = true }
        executionThread.start()
        assertTrue(backend.started.await(5, TimeUnit.SECONDS))

        engine.destroy()
        engine.destroy()
        executionThread.join(5_000L)

        assertFalse(executionThread.isAlive)
        assertTrue(failure.get() is ScriptInterruptedException)
        assertTrue(engine.isDestroyed)
        assertEquals(1, backend.lastSession?.cancelCount)
        assertEquals(1, backend.lastSession?.closeCount)
        assertEquals(1, backend.connectionCloseCount)
        assertSnapshotsClean()
    }

    @Test
    fun destroyedLuaEngineIsRemovedFromTheManagerExactlyOnce() {
        val engine = LuaPluginScriptEngine(temporaryFolder.root, FakeBackend(Mode.COMPLETE))
        val manager = ScriptEngineManager(null)
        manager.registerEngine(LuaScriptSource.ENGINE) { engine }

        val created = manager.createEngineOfSourceOrThrow(source(), 42)
        assertSame(engine, created)
        assertEquals(setOf(engine), manager.engines)

        engine.destroy()
        engine.destroy()

        assertTrue(manager.engines.isEmpty())
        assertTrue(engine.isDestroyed)
    }

    private fun assertSnapshotsClean() {
        assertTrue(temporaryFolder.root.resolve("lua-source-snapshots").listFiles().orEmpty().isEmpty())
    }

    private fun assertSingleClosedDispatch(backend: FakeBackend) {
        assertEquals(1, backend.acquireCount)
        assertEquals(1, backend.createCount)
        assertEquals(1, backend.lastSession?.startCount)
        assertEquals(1, backend.lastSession?.closeCount)
        assertEquals(1, backend.connectionCloseCount)
        assertSnapshotsClean()
    }

    private fun source() = object : LuaScriptSource("main") {
        override val script: String = "return 7"
        override val scriptReader: Reader? = null
    }

    private enum class Mode {
        COMPLETE,
        STALL,
        CREATION_VIOLATION,
        FAST_FAILURE,
        TRANSPORT_AFTER_START,
        BLOCK_PREPARE,
        BLOCK_ACQUIRE,
        BLOCK_CREATE,
        PREPARE_INTERRUPTED,
        ACQUIRE_INTERRUPTED,
        PREPARE_FAILURE,
    }

    private class FakeBackend(
        private val mode: Mode,
        private val info: LuaRuntimeInfo = INFO,
    ) : LuaEngineRuntimeBackend {
        val started = CountDownLatch(1)
        val prepareEntered = CountDownLatch(1)
        val acquireEntered = CountDownLatch(1)
        val createEntered = CountDownLatch(1)
        val outputs = mutableListOf<String>()
        var prepareCount = 0
        var acquireCount = 0
        var createCount = 0
        var connectionCloseCount = 0
        var lastRequest: LuaExecutionRequest? = null
        var lastModules: LuaModuleSnapshot? = null
        var lastSession: FakeSession? = null
        var lastScope: BridgeExecutionScope? = null

        override fun prepare(): LuaEnginePreparedRuntime {
            prepareCount++
            if (mode == Mode.PREPARE_FAILURE) throw IllegalStateException("LUA_RUNTIME_UNAVAILABLE")
            if (mode == Mode.PREPARE_INTERRUPTED) throw InterruptedException("stopped during prepare")
            if (mode == Mode.BLOCK_PREPARE) {
                prepareEntered.countDown()
                CountDownLatch(1).await()
                error("Blocked prepare unexpectedly resumed")
            }
            return LuaEnginePreparedRuntime(info, VERSION, this)
        }

        override fun acquire(prepared: LuaEnginePreparedRuntime): LuaEngineRuntimeConnection {
            require(prepared.token === this)
            acquireCount++
            if (mode == Mode.ACQUIRE_INTERRUPTED) throw InterruptedException("stopped during acquire")
            if (mode == Mode.BLOCK_ACQUIRE) {
                acquireEntered.countDown()
                CountDownLatch(1).await()
                error("Blocked acquire unexpectedly resumed")
            }
            val connectionClosed = AtomicBoolean(false)
            return object : LuaEngineRuntimeConnection {
                override val runtimeInfo: LuaRuntimeInfo = info
                override val protocolVersion: LuaProtocolVersion = VERSION
                override val grantedCapabilities: List<String> = info.capabilities

                override fun createExecution(
                    request: LuaExecutionRequest,
                    source: LuaSourceSnapshot,
                    modules: LuaModuleSnapshot,
                    observer: LuaEngineExecutionObserver,
                    scope: BridgeExecutionScope,
                ): LuaEngineRemoteSession {
                    createCount++
                    lastRequest = request
                    lastScope = scope
                    lastModules = modules
                    assertEquals(source.lengthBytes, request.sourceLengthBytes)
                    if (mode == Mode.BLOCK_CREATE) {
                        createEntered.countDown()
                        CountDownLatch(1).await()
                        error("Blocked create unexpectedly resumed")
                    }
                    if (mode == Mode.CREATION_VIOLATION) {
                        observer.transportViolation(IllegalStateException("provider died during creation"))
                    }
                    return FakeSession(mode, request, observer, started, outputs).also { lastSession = it }
                }

                override fun close() {
                    if (connectionClosed.compareAndSet(false, true)) connectionCloseCount++
                }
            }
        }
    }

    private class FakeSession(
        private val mode: Mode,
        private val request: LuaExecutionRequest,
        private val observer: LuaEngineExecutionObserver,
        private val startedLatch: CountDownLatch,
        private val outputTexts: MutableList<String>,
    ) : LuaEngineRemoteSession {
        var grantedCredits = 0
        var startCount = 0
        var cancelCount = 0
        var closeCount = 0

        override fun grantOutputCredits(count: Int) {
            grantedCredits += count
        }

        override fun start() {
            startCount++
            if (mode == Mode.FAST_FAILURE) {
                observer.onFailed(
                    LuaRuntimeCodec.encodeError(
                        LuaExecutionError(
                            request.requestId,
                            LuaExecutionErrorCode.RUNTIME_ERROR,
                            LuaExecutionFailurePhase.EXECUTION,
                            "fake provider failure",
                            LuaRetryDisposition.DO_NOT_RETRY,
                        ),
                    ),
                )
                return
            }
            observer.onStarted(
                LuaRuntimeCodec.encodeStarted(
                    LuaExecutionStarted(request.requestId, VERSION, "lua54", "5.4.8", 0L, 0L),
                ),
            )
            startedLatch.countDown()
            if (mode == Mode.COMPLETE) {
                val output = LuaOutputChunk(request.requestId, 0L, LuaOutputStream.STDOUT, "from-fake")
                if (observer.onOutput(LuaRuntimeCodec.encodeOutput(output))) outputTexts += output.text
                observer.onCompleted(
                    LuaRuntimeCodec.encodeResult(
                        LuaExecutionResult(request.requestId, LuaValue.Int64Value(7L), 1L),
                    ),
                    descriptorCount = 0,
                )
            } else if (mode == Mode.TRANSPORT_AFTER_START) {
                observer.transportViolation(IllegalStateException("provider died after start"))
            }
        }

        override fun cancel() {
            if (cancelCount != 0) return
            cancelCount++
            observer.onCancelled(
                LuaRuntimeCodec.encodeCancellation(
                    LuaExecutionCancellation(request.requestId, LuaCancellationReason.REQUESTED, 1L),
                ),
            )
        }

        override fun close() {
            if (closeCount != 0) return
            closeCount++
        }
    }

    private companion object {
        val VERSION = LuaProtocolVersion(1, 0)
        val INFO = LuaRuntimeInfo(
            protocolMin = VERSION,
            protocolMax = VERSION,
            providerId = "org.autojs.lua.fake",
            providerVersionName = "1.0",
            providerVersionCode = 1L,
            runtimeFamily = LuaRuntimeFamily.PUC_LUA,
            runtimeSlot = "lua54",
            languageVersion = "5.4.8",
            processAbi = "x86_64",
            supportedAbis = listOf("x86_64"),
            capabilities = LuaRuntimeHostCapabilities.ENABLED,
            limits = LuaRuntimeLimits(
                maxSourceBytes = 1024L,
                maxMemoryBytes = 1024L * 1024L,
                maxOutputBytes = 1024L,
                maxExecutionMillis = 1_000L,
                maxConcurrentExecutions = 1,
            ),
        )
        val EMPTY_CAPABILITY_INFO = LuaRuntimeInfo(
            protocolMin = VERSION,
            protocolMax = VERSION,
            providerId = "org.autojs.lua.compat",
            providerVersionName = "1.0",
            providerVersionCode = 1L,
            runtimeFamily = LuaRuntimeFamily.PUC_LUA,
            runtimeSlot = "lua54",
            languageVersion = "5.4.8",
            processAbi = "x86_64",
            supportedAbis = listOf("x86_64"),
            capabilities = emptyList(),
            limits = INFO.limits,
        )
    }
}
