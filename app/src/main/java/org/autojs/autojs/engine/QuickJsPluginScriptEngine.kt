package org.autojs.autojs.engine

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.plugin.quickjs.QuickJsBinderCallException
import org.autojs.autojs.core.plugin.quickjs.QuickJsBinderCallFailure
import org.autojs.autojs.core.plugin.quickjs.QuickJsRuntimeBinderCallLane
import org.autojs.autojs.core.plugin.quickjs.QuickJsRuntimeCleanupBinderCallLane
import org.autojs.autojs.core.plugin.quickjs.QuickJsRuntimePluginClient
import org.autojs.autojs.core.plugin.scope.BridgeExecutionScope
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.plugin.quickjs.runtime.api.IQuickJsRuntimeCallback
import org.autojs.plugin.quickjs.runtime.api.QuickJsRuntimeContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Independent JavaScript engine proxy whose source is evaluated only by the QuickJS plugin. */
class QuickJsPluginScriptEngine(
    context: Context,
    private val console: GlobalConsole,
) : ScriptEngine.AbstractScriptEngine<JavaScriptSource>() {
    private val appContext = context.applicationContext
    private val stopped = AtomicBoolean(false)
    private val selectedRuntime = AtomicReference<QuickJsRuntimePluginClient.Selection?>()
    private val activeExecutionId = AtomicReference<String?>()

    override fun put(name: String, value: Any?) = Unit

    override fun init() {
        check(!isDestroyed) { "QuickJS engine is destroyed" }
        if (stopped.get()) throw ScriptInterruptedException()
        val selection = runRpc(SELECT_RPC_DEADLINE_MILLIS, "select") {
            QuickJsRuntimePluginClient.select(appContext)
        }
        val runtimeInfo = runRpc(PREWARM_RPC_DEADLINE_MILLIS, "prewarm") {
            QuickJsRuntimePluginClient.prewarm(appContext, selection)
        }
        check(runtimeInfo.getBoolean(QuickJsRuntimeContract.KEY_RUNTIME_READY)) {
            runtimeInfo.getString(QuickJsRuntimeContract.KEY_ERROR_MESSAGE)
                ?: "The selected QuickJS Runtime plugin is not ready."
        }
        pruneStaleSourceSnapshots()
        selectedRuntime.set(selection)
    }

    override fun execute(scriptSource: JavaScriptSource): Any {
        check(!isDestroyed) { "QuickJS engine is destroyed" }
        if (stopped.get()) throw ScriptInterruptedException()
        requireSupportedMode(scriptSource.executionMode)
        val selection = selectedRuntime.get() ?: error("QuickJS engine was not initialized")
        val executionId = "quickjs-engine-${id}-${UUID.randomUUID()}"
        check(activeExecutionId.compareAndSet(null, executionId)) {
            "QuickJS engine already has an active execution"
        }
        val limits = executionLimits()
        val callback = OutputCallback(executionId, selection.providerUid)
        var sourceFile: File? = null
        // Released in the order of the former finally: the callback, then a source snapshot not unlinked yet.
        // zh-CN: 按原 finally 的顺序释放: 先 callback, 再尚未删除的源码快照.
        val scope = BridgeExecutionScope("quickjs", executionId).apply {
            own(BridgeExecutionScope.Stage.CALLBACKS, "callback", callback::close)
            track(callback, "callback")
            own(BridgeExecutionScope.Stage.FILES, "source") {
                sourceFile?.takeIf(File::exists)?.let { stale ->
                    if (!stale.delete()) Log.w(TAG, "Unable to delete stale QuickJS source snapshot: ${stale.name}")
                }
            }
        }
        try {
            val snapshot = createSourceSnapshot(scriptSource)
            sourceFile = snapshot
            if (stopped.get() || isDestroyed) throw ScriptInterruptedException()
            ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                check(snapshot.delete()) { "Unable to unlink the QuickJS source snapshot before dispatch" }
                if (stopped.get() || isDestroyed) throw ScriptInterruptedException()
                val result = try {
                    runRpc(
                        deadlineMillis = limits.timeoutMillis + EXECUTION_RPC_GRACE_MILLIS,
                        operation = "run-script",
                        poisonOnAbandoned = true,
                    ) {
                        QuickJsRuntimePluginClient.runScript(
                            context = appContext,
                            selection = selection,
                            request = buildRequest(scriptSource, executionId, limits),
                            source = descriptor,
                            callback = callback.binder,
                        )
                    }
                } catch (error: QuickJsRpcTimeoutException) {
                    requestCancellation(selection, executionId)
                    throw QuickJsRuntimeExecutionException(
                        error.message ?: "QuickJS runtime RPC timed out",
                    )
                }
                callback.awaitTerminal()
                if (stopped.get() || result.getBoolean(QuickJsRuntimeContract.KEY_CANCELLED)) {
                    throw ScriptInterruptedException()
                }
                if (!result.getBoolean(QuickJsRuntimeContract.KEY_SUCCEEDED)) {
                    val code = result.getString(QuickJsRuntimeContract.KEY_ERROR_CODE).orEmpty()
                    val message = result.getString(QuickJsRuntimeContract.KEY_ERROR_MESSAGE)
                        ?: "QuickJS runtime execution failed"
                    throw QuickJsRuntimeExecutionException("$code: $message".trimStart(':', ' '))
                }
                return result.getInt(QuickJsRuntimeContract.KEY_EXIT_CODE, 0)
            }
        } finally {
            scope.close()
            activeExecutionId.compareAndSet(executionId, null)
        }
    }

    override fun forceStop() {
        stopped.set(true)
        val selection = selectedRuntime.get() ?: return
        val executionId = activeExecutionId.get() ?: return
        requestCancellation(selection, executionId)
    }

    override fun destroy() {
        if (!isDestroyed) {
            forceStop()
            selectedRuntime.set(null)
            super.destroy()
        }
    }

    override fun toString(): String =
        "QuickJsPluginScriptEngine@${Integer.toHexString(hashCode())}" +
            "{id=$id,source='${getTag(TAG_SOURCE)}',cwd='${cwd()}'}"

    private fun buildRequest(
        source: JavaScriptSource,
        executionId: String,
        limits: ExecutionLimits,
    ): Bundle {
        val sourceName = logicalSourceName(source)
        return Bundle().apply {
            putInt(QuickJsRuntimeContract.KEY_PROTOCOL_VERSION, QuickJsRuntimeContract.PROTOCOL_VERSION)
            putString(QuickJsRuntimeContract.KEY_EXECUTION_ID, executionId)
            putString(QuickJsRuntimeContract.KEY_SOURCE_NAME, sourceName)
            putString(
                QuickJsRuntimeContract.KEY_SOURCE_MODE,
                if (sourceName.endsWith(".mjs", ignoreCase = true)) {
                    QuickJsRuntimeContract.SOURCE_MODE_MODULE
                } else {
                    QuickJsRuntimeContract.SOURCE_MODE_SCRIPT
                },
            )
            putLong(QuickJsRuntimeContract.KEY_TIMEOUT_MILLIS, limits.timeoutMillis)
            putLong(QuickJsRuntimeContract.KEY_MEMORY_LIMIT_BYTES, limits.memoryLimitBytes)
            putLong(QuickJsRuntimeContract.KEY_STACK_LIMIT_BYTES, limits.stackLimitBytes)
            putLong(QuickJsRuntimeContract.KEY_OUTPUT_BYTE_LIMIT, limits.outputByteLimit)
        }
    }

    /**
     * The output of one execution. The plugin gets [binder], which forwards here until [close] and then lets go of this
     * callback and the engine: the plugin process keeps that Binder until it collects its proxy (docs/memory-phase2/ROADMAP.md C38).
     * zh-CN: 一次执行的输出. 插件拿到的是 [binder], 它在 [close] 之前转发到这里, 之后放开本回调与引擎: 插件进程在回收代理之前一直持有该 Binder
     * (docs/memory-phase2/ROADMAP.md C38).
     */
    private inner class OutputCallback(
        private val executionId: String,
        private val expectedProviderUid: Int,
    ) {
        private val closed = AtomicBoolean(false)
        private val acceptedOutputBytes = AtomicLong(0L)
        private val lastSequence = AtomicLong(-1L)
        private val terminal = CountDownLatch(1)
        private val exported = ExportedCallback(this)

        val binder: IQuickJsRuntimeCallback
            get() = exported

        fun close() {
            closed.set(true)
            exported.clear()
            terminal.countDown()
        }

        fun awaitTerminal() {
            try {
                if (!terminal.await(OUTPUT_DRAIN_MILLIS, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "QuickJS callback stream did not emit its terminal event")
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ScriptInterruptedException()
            }
        }

        fun onEvent(event: Bundle?) {
            if (closed.get() || Binder.getCallingUid() != expectedProviderUid) return
            if (
                event?.getInt(QuickJsRuntimeContract.KEY_PROTOCOL_VERSION, -1) !=
                QuickJsRuntimeContract.PROTOCOL_VERSION
            ) {
                return
            }
            if (event.getString(QuickJsRuntimeContract.KEY_EXECUTION_ID) != executionId) return
            val sequence = event.getLong(QuickJsRuntimeContract.KEY_SEQUENCE, -1L)
            if (sequence < 0L || !acceptSequence(sequence)) return
            val level = when (event.getString(QuickJsRuntimeContract.KEY_EVENT_TYPE)) {
                QuickJsRuntimeContract.EVENT_FINISHED -> {
                    close()
                    return
                }
                QuickJsRuntimeContract.EVENT_STARTED -> return
                QuickJsRuntimeContract.EVENT_STDERR -> Log.ERROR
                QuickJsRuntimeContract.EVENT_STDOUT -> Log.INFO
                else -> return
            }
            val text = event.getString(QuickJsRuntimeContract.KEY_TEXT) ?: return
            val byteCount = text.toByteArray(Charsets.UTF_8).size.toLong()
            if (
                byteCount > QuickJsRuntimeContract.MAX_CALLBACK_CHUNK_BYTES ||
                !reserveOutputBytes(byteCount)
            ) {
                return
            }
            val identity = Binder.clearCallingIdentity()
            try {
                if (!closed.get()) console.println(level, text)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }

        private fun acceptSequence(sequence: Long): Boolean {
            while (!closed.get()) {
                val previous = lastSequence.get()
                if (sequence <= previous) return false
                if (lastSequence.compareAndSet(previous, sequence)) return true
            }
            return false
        }

        private fun reserveOutputBytes(byteCount: Long): Boolean {
            while (!closed.get()) {
                val current = acceptedOutputBytes.get()
                if (byteCount > QuickJsRuntimeContract.MAX_OUTPUT_BYTES - current) return false
                if (acceptedOutputBytes.compareAndSet(current, current + byteCount)) return true
            }
            return false
        }
    }

    private class ExportedCallback(callback: OutputCallback) : IQuickJsRuntimeCallback.Stub() {

        private val target = AtomicReference<OutputCallback?>(callback)

        fun clear() {
            target.set(null)
        }

        override fun onEvent(event: Bundle?) {
            target.get()?.onEvent(event)
        }
    }

    private fun requestCancellation(
        selection: QuickJsRuntimePluginClient.Selection,
        executionId: String,
    ) {
        QuickJsRuntimeCleanupBinderCallLane.tryDispatch("cancel-script") {
            runCatching {
                runBlocking { QuickJsRuntimePluginClient.cancel(appContext, selection, executionId) }
            }
        }
    }

    private fun <T> runRpc(
        deadlineMillis: Long,
        operation: String,
        poisonOnAbandoned: Boolean = false,
        block: suspend () -> T,
    ): T {
        return try {
            QuickJsRuntimeBinderCallLane.call(
                operationName = operation,
                timeoutMillis = deadlineMillis,
                onAbandoned = {
                    if (poisonOnAbandoned) QuickJsRuntimeBinderCallLane.poison()
                },
            ) { runBlocking { block() } }
        } catch (error: QuickJsBinderCallException) {
            if (error.failure == QuickJsBinderCallFailure.TIMEOUT) {
                throw QuickJsRpcTimeoutException(
                    "Timed out while attempting to ${operation.replace('-', ' ')}",
                )
            }
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ScriptInterruptedException()
        }
    }

    private fun createSourceSnapshot(source: JavaScriptSource): File {
        val bytes = cleartextSource(source)
        var snapshot: File? = null
        try {
            require(bytes.size <= QuickJsRuntimeContract.MAX_SOURCE_BYTES) {
                "QuickJS source exceeds ${QuickJsRuntimeContract.MAX_SOURCE_BYTES} bytes"
            }
            val directory = sourceSnapshotDirectory()
            check(directory.isDirectory || directory.mkdirs()) {
                "Unable to create the QuickJS snapshot directory"
            }
            val extension = if (logicalSourceName(source).endsWith(".mjs", ignoreCase = true)) {
                ".quickjs.mjs"
            } else {
                ".quickjs.js"
            }
            snapshot = File.createTempFile("quickjs-source-", extension, directory)
            FileOutputStream(snapshot).use { it.write(bytes) }
            return snapshot
        } catch (error: Throwable) {
            snapshot?.delete()
            throw error
        } finally {
            bytes.fill(0)
        }
    }

    private fun cleartextSource(source: JavaScriptSource): ByteArray {
        val file = (source as? JavaScriptFileSource)?.file
        if (file == null) return source.script.toByteArray(Charsets.UTF_8)
        require(file.isFile) { "QuickJS source is missing: ${file.path}" }
        val bytes = readBounded(file, MAX_STORED_SOURCE_BYTES)
        if (
            bytes.size >= EncryptedScriptFileHeader.BLOCK_SIZE &&
            EncryptedScriptFileHeader.isValidFile(bytes)
        ) {
            return try {
                ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE)
            } finally {
                bytes.fill(0)
            }
        }
        bytes.fill(0)
        return source.script.toByteArray(Charsets.UTF_8)
    }

    private fun readBounded(file: File, maximumBytes: Long): ByteArray {
        val initialCapacity = file.length().coerceIn(0L, minOf(maximumBytes, 64L * 1024L)).toInt()
        val output = ByteArrayOutputStream(initialCapacity)
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maximumBytes) {
                    "QuickJS source exceeds its stored size limit: ${file.path}"
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun sourceSnapshotDirectory(): File = File(appContext.cacheDir, "quickjs-source-snapshots")

    private fun pruneStaleSourceSnapshots() {
        val cutoff = System.currentTimeMillis() - STALE_SNAPSHOT_MILLIS
        sourceSnapshotDirectory().listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { stale ->
                if (!stale.delete()) Log.w(TAG, "Unable to prune stale QuickJS source snapshot: ${stale.name}")
            }
    }

    private fun logicalSourceName(source: JavaScriptSource): String {
        source.overriddenFullPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.name
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        val file = (source as? JavaScriptFileSource)?.file
        return file?.name
            ?: source.name.takeIf(JavaScriptSource::isJavaScriptFileName)
            ?: source.fullName.takeIf(String::isNotBlank)
            ?: "script.quickjs.js"
    }

    private fun executionLimits(): ExecutionLimits {
        val config = getTag(ExecutionConfig.tag) as? ExecutionConfig
        return ExecutionLimits(
            timeoutMillis = config.numberArgument("quickjs.timeoutMillis")
                ?.coerceIn(1L, QuickJsRuntimeContract.MAX_TIMEOUT_MILLIS)
                ?: QuickJsRuntimeContract.DEFAULT_TIMEOUT_MILLIS,
            memoryLimitBytes = config.numberArgument("quickjs.memoryLimitBytes")
                ?.coerceIn(1L, QuickJsRuntimeContract.MAX_MEMORY_LIMIT_BYTES)
                ?: QuickJsRuntimeContract.DEFAULT_MEMORY_LIMIT_BYTES,
            stackLimitBytes = config.numberArgument("quickjs.stackLimitBytes")
                ?.coerceIn(1L, QuickJsRuntimeContract.MAX_STACK_LIMIT_BYTES)
                ?: QuickJsRuntimeContract.DEFAULT_STACK_LIMIT_BYTES,
            outputByteLimit = config.numberArgument("quickjs.outputByteLimit")
                ?.coerceIn(1L, QuickJsRuntimeContract.MAX_OUTPUT_BYTES)
                ?: QuickJsRuntimeContract.MAX_OUTPUT_BYTES,
        )
    }

    private fun ExecutionConfig?.numberArgument(name: String): Long? =
        (this?.getArgument(name) as? Number)?.toLong()

    private fun requireSupportedMode(mode: Int) {
        val unsupported = mode and JavaScriptSource.EXECUTION_MODE_QUICKJS.inv()
        require(unsupported == JavaScriptSource.EXECUTION_MODE_NORMAL) {
            "QuickJS Runtime does not support AutoJs6 execution-mode flags: 0x${unsupported.toString(16)}"
        }
    }

    private data class ExecutionLimits(
        val timeoutMillis: Long,
        val memoryLimitBytes: Long,
        val stackLimitBytes: Long,
        val outputByteLimit: Long,
    )

    private companion object {
        const val TAG = "QuickJsPluginScriptEngine"
        const val STALE_SNAPSHOT_MILLIS = 60_000L
        const val SELECT_RPC_DEADLINE_MILLIS = 70_000L
        const val PREWARM_RPC_DEADLINE_MILLIS = 70_000L
        const val EXECUTION_RPC_GRACE_MILLIS = 15_000L
        const val OUTPUT_DRAIN_MILLIS = 2_000L
        const val MAX_STORED_SOURCE_BYTES =
            QuickJsRuntimeContract.MAX_SOURCE_BYTES * 4L + EncryptedScriptFileHeader.BLOCK_SIZE + 16L
    }
}

private class QuickJsRpcTimeoutException(message: String) : IllegalStateException(message)

class QuickJsRuntimeExecutionException(message: String) : IllegalStateException(message)
