package org.autojs.autojs.engine

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.plugin.bun.BunBinderCallException
import org.autojs.autojs.core.plugin.bun.BunBinderCallFailure
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.autojs.core.plugin.bun.BunRuntimeBinderCallLane
import org.autojs.autojs.core.plugin.bun.BunRuntimeCleanupBinderCallLane
import org.autojs.autojs.core.plugin.bun.BunRuntimePluginClient
import org.autojs.autojs.core.plugin.scope.BridgeExecutionScope
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.TypeScriptStringSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.bun.runtime.api.BunPluginCapabilityKeys
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.autojs.plugin.bun.runtime.api.IBunRuntimeCallback
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

/** Independent JavaScript engine proxy whose source is evaluated only by the Bun plugin. */
class BunPluginScriptEngine internal constructor(
    context: Context,
    console: GlobalConsole,
    private val consoleFactory: () -> ConsoleImpl,
) : ScriptEngine.AbstractScriptEngine<JavaScriptSource>() {
    constructor(context: Context, console: GlobalConsole) : this(context, console, { ConsoleImpl(console.uiHandler, console) })

    private val appContext = context.applicationContext
    private val stopped = AtomicBoolean(false)
    private val selectedRuntime = AtomicReference<BunRuntimePluginClient.Selection?>()

    private val activeExecutionId = AtomicReference<String?>()
    // The error of exit(error) in a Bun Worker (api-coverage Q139 (b)); the execution fails with it instead of stopping.
    private val scriptStopMessage = AtomicReference<String?>()

    override fun put(name: String, value: Any?) = Unit

    override fun init() {
        check(!isDestroyed) { "Bun engine is destroyed" }
        if (stopped.get()) throw ScriptInterruptedException()
        val selection = runRpc(SELECT_RPC_DEADLINE_MILLIS, "select") {
            BunRuntimePluginClient.select(appContext)
        }
        val runtimeInfo = runRpc(PREWARM_RPC_DEADLINE_MILLIS, "prewarm") {
            BunRuntimePluginClient.prewarm(appContext, selection)
        }
        check(runtimeInfo.getBoolean(BunRuntimeContract.KEY_RUNTIME_READY)) {
            runtimeInfo.getString(BunRuntimeContract.KEY_ERROR_MESSAGE)
                ?: "The selected Bun Runtime plugin is not ready."
        }
        pruneStaleSourceSnapshots()
        selectedRuntime.set(selection)
    }

    override fun execute(scriptSource: JavaScriptSource): Any {
        check(!isDestroyed) { "Bun engine is destroyed" }
        if (stopped.get()) throw ScriptInterruptedException()
        requireSupportedMode(scriptSource.executionMode)
        val selection = selectedRuntime.get() ?: error("Bun engine was not initialized")
        val executionId = "bun-engine-${id}-${UUID.randomUUID()}"
        check(activeExecutionId.compareAndSet(null, executionId)) { "Bun engine already has an active execution" }
        val timeoutMillis = executionTimeoutMillis(scriptSource)
        val executionConsole = consoleFactory().apply { setTitle(scriptSource.name) }
        val callback = OutputCallback(executionId, selection.providerUid, executionConsole)
        var payloadFile: File? = null
        val broker = createCapabilityBroker(selection, executionId, executionConsole, logicalSourceName(scriptSource))
        // Released in the order of the former finally: broker, callback, console, then a payload not unlinked yet.
        // zh-CN: 按原 finally 的顺序释放: broker, callback, console, 最后是尚未删除的 payload.
        val scope = BridgeExecutionScope("bun", executionId).apply {
            broker?.let {
                own(BridgeExecutionScope.Stage.BROKER, "broker", it::close)
                track(it, "broker")
            }
            own(BridgeExecutionScope.Stage.CALLBACKS, "callback", callback::close)
            track(callback, "callback")
            own(BridgeExecutionScope.Stage.CONSOLE, "console") {
                executionConsole.onRuntimeExit()
                if (executionConsole.configurator.isExitOnClose) executionConsole.hideDelayed()
            }
            own(BridgeExecutionScope.Stage.FILES, "payload") {
                payloadFile?.takeIf(File::exists)?.let { stale ->
                    if (!stale.delete()) Log.w(TAG, "Unable to delete stale Bun payload: ${stale.name}")
                }
            }
        }
        try {
            val payload = createPayload(scriptSource, selection, executionId, timeoutMillis)
            broker?.attachTo(payload.request)
            payloadFile = payload.file
            if (stopped.get() || isDestroyed) throw ScriptInterruptedException()
            ParcelFileDescriptor.open(payload.file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                check(payload.file.delete()) { "Unable to unlink the Bun payload before dispatch" }
                if (stopped.get() || isDestroyed) throw ScriptInterruptedException()
                val result = try {
                    runRpc(
                        deadlineMillis = timeoutMillis + EXECUTION_RPC_GRACE_MILLIS,
                        operation = "run-script",
                        poisonOnAbandoned = true,
                    ) {
                        BunRuntimePluginClient.runScript(
                            context = appContext,
                            selection = selection,
                            request = payload.request,
                            source = descriptor,
                            callback = callback.binder,
                        )
                    }
                } catch (error: BunRpcTimeoutException) {
                    requestCancellation(selection, executionId)
                    throw BunRuntimeExecutionException(error.message ?: "Bun runtime RPC timed out")
                }
                // runScript is synchronous, but output and finished are oneway Binder calls. Its return
                // can overtake callbacks already queued for this process. Drain through finished before
                // finally closes the callback, otherwise the last stdout/stderr chunks can disappear.
                if (!stopped.get() && !result.getBoolean(BunRuntimeContract.KEY_CANCELLED)) {
                    val drained = try {
                        callback.awaitFinished(OUTPUT_DRAIN_MILLIS) { stopped.get() || isDestroyed }
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw ScriptInterruptedException()
                    }
                    // Early plugin failures may return even when their finished callback cannot be
                    // delivered. Preserve that terminal error below instead of masking its cause.
                    if (!drained && result.getBoolean(BunRuntimeContract.KEY_SUCCEEDED) && !stopped.get() && !isDestroyed) {
                        throw BunRuntimeExecutionException("Bun output callbacks did not finish within $OUTPUT_DRAIN_MILLIS ms")
                    }
                }
                if (stopped.get() || result.getBoolean(BunRuntimeContract.KEY_CANCELLED)) {
                    scriptStopMessage.get()?.let { throw BunRuntimeExecutionException(it) }
                    throw ScriptInterruptedException()
                }
                if (!result.getBoolean(BunRuntimeContract.KEY_SUCCEEDED)) {
                    val code = result.getString(BunRuntimeContract.KEY_ERROR_CODE).orEmpty()
                    val message = result.getString(BunRuntimeContract.KEY_ERROR_MESSAGE)
                        ?: "Bun runtime execution failed"
                    throw BunRuntimeExecutionException("$code: $message".trimStart(':', ' '))
                }
                return result.getInt(BunRuntimeContract.KEY_EXIT_CODE, 0)
            }
        } finally {
            scope.close()
            activeExecutionId.compareAndSet(executionId, null)
        }
    }

    /** One dispatchable unit: the file handed to the plugin and the request that describes it. */
    private class Payload(val file: File, val request: Bundle)

    /**
     * A script inside a declared project (nearest `project.json` or `package.json`) is sent as a
     * bounded workspace archive when the plugin advertises the capability; every other source keeps
     * the single-file snapshot, so older plugins and bare scripts behave exactly as before.
     */
    private fun createPayload(
        source: JavaScriptSource,
        selection: BunRuntimePluginClient.Selection,
        executionId: String,
        timeoutMillis: Long,
    ): Payload {
        val request = buildRequest(source, executionId, timeoutMillis)
        attachHostInfo(request, selection)
        val fileSource = source as? JavaScriptFileSource
        val projectRoot = fileSource
            ?.takeIf { supportsWorkspaceArchive(selection) }
            ?.let { BunPluginWorkspaceArchive.findProjectRoot(semanticFile(it)) }
        if (fileSource == null || projectRoot == null) {
            return Payload(createSourceSnapshot(source), request)
        }
        val bytes = cleartextSource(source)
        try {
            require(bytes.size <= BunRuntimeContract.MAX_SOURCE_BYTES) {
                "Bun source exceeds ${BunRuntimeContract.MAX_SOURCE_BYTES} bytes"
            }
            val directory = sourceSnapshotDirectory()
            check(directory.isDirectory || directory.mkdirs()) { "Unable to create the Bun snapshot directory" }
            val archive = BunPluginWorkspaceArchive.create(
                root = projectRoot,
                entryFile = semanticFile(fileSource),
                entryBytes = bytes,
                directory = directory,
                decryptProjectScripts = BuildConfig.isInrt,
            )
            request.putInt(BunRuntimeContract.KEY_WORKSPACE_ARCHIVE_VERSION, BunRuntimeContract.WORKSPACE_ARCHIVE_VERSION)
            request.putString(BunRuntimeContract.KEY_WORKSPACE_ENTRY_POINT, archive.entryPoint)
            request.putInt(BunRuntimeContract.KEY_WORKSPACE_MAX_ENTRIES, BunRuntimeContract.MAX_WORKSPACE_ENTRIES)
            request.putLong(BunRuntimeContract.KEY_WORKSPACE_MAX_BYTES, BunRuntimeContract.MAX_WORKSPACE_BYTES)
            return Payload(archive.file, request)
        } finally {
            bytes.fill(0)
        }
    }

    private fun supportsWorkspaceArchive(selection: BunRuntimePluginClient.Selection): Boolean =
        selection.discovered.pluginInfo?.capabilities
            ?.getBoolean(BunPluginCapabilityKeys.SUPPORTS_WORKSPACE_ARCHIVE, false) == true

    /**
     * First capability of the Bun capability bridge (plugin M7): a read-only host info snapshot.
     * Offered only when the plugin advertises `SUPPORTS_HOST_INFO` and the host grant is on. The
     * plugin verifies the package name against the Binder caller and resolves the host version from
     * `PackageManager` itself, so only advisory fields travel in the request; revoking the grant
     * simply stops offering the keys, and older plugins never see them.
     */
    private fun attachHostInfo(request: Bundle, selection: BunRuntimePluginClient.Selection) {
        if (!supportsHostInfo(selection) || !BunHostCapabilityGrants.isHostInfoGranted(appContext)) return
        request.putInt(BunRuntimeContract.KEY_HOST_INFO_VERSION, BunRuntimeContract.HOST_INFO_VERSION)
        request.putBundle(BunRuntimeContract.KEY_HOST_INFO, Bundle().apply {
            putString(BunRuntimeContract.HOST_INFO_KEY_PACKAGE_NAME, appContext.packageName)
            putString(BunRuntimeContract.HOST_INFO_KEY_VERSION_DATE, BuildConfig.VERSION_DATE)
            putString(
                BunRuntimeContract.HOST_INFO_KEY_LANGUAGE_TAG,
                Language.getPrefLanguage().getLocalCompatibleLanguageTag(),
            )
        })
    }

    private fun supportsHostInfo(selection: BunRuntimePluginClient.Selection): Boolean =
        selection.discovered.pluginInfo?.capabilities
            ?.getBoolean(BunPluginCapabilityKeys.SUPPORTS_HOST_INFO, false) == true

    /**
     * Dynamic calls of the Bun capability bridge (plugin M7, second part): a broker Binder scoped to this
     * execution plus the granted capability IDs travel in the request, and the plugin serves them to the script
     * over a private socket. Offered only when the plugin advertises `SUPPORTS_HOST_CAPABILITY_BRIDGE` and at
     * least one dynamic capability is granted; revoking every grant means no broker, no socket and no variable.
     */
    private fun createCapabilityBroker(
        selection: BunRuntimePluginClient.Selection,
        executionId: String,
        executionConsole: ConsoleImpl,
        sourceName: String,
    ): BunHostCapabilityBroker? {
        if (!supportsCapabilityBridge(selection)) return null
        val granted = BunHostCapabilityGrants.grantedBridgeCapabilities(appContext)
        if (granted.isEmpty()) return null
        val config = getTag(ExecutionConfig.tag) as? ExecutionConfig
        val spawnDepth = (config?.getArgument(NodeBridgeEngineInfo.SPAWN_DEPTH_ARGUMENT) as? Number)?.toInt()?.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH) ?: 0
        // execution.control is not a grant: a script may always end its own execution (exit / stop in a Worker, Q139 (b)).
        // The request carries at most the plugin's bound (Q170 (a)); a plugin that advertises none parses 64 at most.
        val limit = selection.discovered.pluginInfo?.capabilities
            ?.getInt(BunPluginCapabilityKeys.MAX_HOST_CAPABILITY_COUNT, BunRuntimeContract.LEGACY_MAX_HOST_CAPABILITY_COUNT)
            ?: BunRuntimeContract.LEGACY_MAX_HOST_CAPABILITY_COUNT
        val capabilities = BunHostCapabilityGrants.offeredCapabilities(granted, limit)
        if (capabilities.size <= granted.size) Log.w(TAG, "Bun grants past the plugin's bound of $limit are not offered: ${granted.drop(capabilities.size - 1)}")
        return BunHostCapabilityBroker(appContext, selection.providerUid, executionId, capabilities, { executionConsole }, cwd(),
            config?.agentExecutionState, spawnDepth, sourceName, ::stopFromScript)
    }

    private fun supportsCapabilityBridge(selection: BunRuntimePluginClient.Selection): Boolean =
        selection.discovered.pluginInfo?.capabilities
            ?.getBoolean(BunPluginCapabilityKeys.SUPPORTS_HOST_CAPABILITY_BRIDGE, false) == true

    private fun semanticFile(source: JavaScriptFileSource): File =
        source.overriddenFullPath?.takeIf(String::isNotBlank)?.let(::File) ?: source.file

    // exit / stop of a Bun Worker through execution.control (api-coverage Q139 (b)): ends this execution as forceStop does;
    // with a message (exit(error)) the execution fails with it, as the exit code of a main thread's exit(error) makes it fail.
    private fun stopFromScript(message: String?) {
        if (message != null) scriptStopMessage.compareAndSet(null, message)
        forceStop()
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
        "BunPluginScriptEngine@${Integer.toHexString(hashCode())}" +
            "{id=$id,source='${getTag(TAG_SOURCE)}',cwd='${cwd()}'}"

    private fun buildRequest(
        source: JavaScriptSource,
        executionId: String,
        timeoutMillis: Long,
    ): Bundle = Bundle().apply {
        putInt(BunRuntimeContract.KEY_PROTOCOL_VERSION, BunRuntimeContract.PROTOCOL_VERSION)
        putString(BunRuntimeContract.KEY_EXECUTION_ID, executionId)
        putString(BunRuntimeContract.KEY_SOURCE_NAME, logicalSourceName(source))
        putLong(BunRuntimeContract.KEY_TIMEOUT_MILLIS, timeoutMillis)
        putLong(BunRuntimeContract.KEY_OUTPUT_BYTE_LIMIT, BunRuntimeContract.MAX_OUTPUT_BYTES)
        putStringArrayList(BunRuntimeContract.KEY_ARGUMENTS, arrayListOf())
        putBundle(BunRuntimeContract.KEY_ENVIRONMENT, Bundle())
    }

    /**
     * The output of one execution. The plugin gets [binder], which forwards here until [close] and then lets go of this
     * callback and its console: the plugin process keeps that Binder until it collects its proxy (docs/memory-phase2/ROADMAP.md C37).
     * zh-CN: 一次执行的输出. 插件拿到的是 [binder], 它在 [close] 之前转发到这里, 之后放开本回调及其 console: 插件进程在回收代理之前一直持有该 Binder
     * (docs/memory-phase2/ROADMAP.md C37).
     */
    internal class OutputCallback(
        private val executionId: String,
        private val expectedProviderUid: Int,
        private val executionConsole: ConsoleImpl,
    ) {
        private val closed = AtomicBoolean(false)
        private val acceptedOutputBytes = AtomicLong(0L)
        private val finished = CountDownLatch(1)
        private val exported = ExportedCallback(this)

        val binder: IBunRuntimeCallback
            get() = exported

        fun close() {
            closed.set(true)
            exported.clear()
        }

        /** Does not mistake a local close/cancellation for the plugin's completed output delivery. */
        @Throws(InterruptedException::class)
        fun awaitFinished(timeoutMillis: Long, cancelled: () -> Boolean = { false }): Boolean {
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis.coerceAtLeast(0L)
            while (true) {
                if (cancelled()) return false
                if (finished.count == 0L) return true
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) return false
                if (finished.await(minOf(remaining, 50L), TimeUnit.MILLISECONDS)) return true
            }
        }

        fun onEvent(event: Bundle?) {
            if (closed.get() || Binder.getCallingUid() != expectedProviderUid) return
            if (event?.getInt(BunRuntimeContract.KEY_PROTOCOL_VERSION, -1) != BunRuntimeContract.PROTOCOL_VERSION) return
            if (event.getString(BunRuntimeContract.KEY_EXECUTION_ID) != executionId) return
            if (event.getLong(BunRuntimeContract.KEY_SEQUENCE, -1L) < 0L) return
            val level = when (event.getString(BunRuntimeContract.KEY_EVENT_TYPE)) {
                BunRuntimeContract.EVENT_FINISHED -> {
                    close()
                    finished.countDown()
                    return
                }
                BunRuntimeContract.EVENT_STARTED -> return
                BunRuntimeContract.EVENT_STDERR -> Log.ERROR
                BunRuntimeContract.EVENT_STDOUT -> Log.INFO
                else -> return
            }
            val text = event.getString(BunRuntimeContract.KEY_TEXT) ?: return
            val byteCount = text.toByteArray(Charsets.UTF_8).size.toLong()
            if (byteCount > BunRuntimeContract.MAX_CALLBACK_CHUNK_BYTES || !reserveOutputBytes(byteCount)) return
            val identity = Binder.clearCallingIdentity()
            try {
                if (!closed.get()) executionConsole.println(level, text)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }

        private fun reserveOutputBytes(byteCount: Long): Boolean {
            while (!closed.get()) {
                val current = acceptedOutputBytes.get()
                if (byteCount > BunRuntimeContract.MAX_OUTPUT_BYTES - current) return false
                if (acceptedOutputBytes.compareAndSet(current, current + byteCount)) return true
            }
            return false
        }
    }

    private class ExportedCallback(callback: OutputCallback) : IBunRuntimeCallback.Stub() {

        private val target = AtomicReference<OutputCallback?>(callback)

        fun clear() {
            target.set(null)
        }

        override fun onEvent(event: Bundle?) {
            target.get()?.onEvent(event)
        }
    }

    private fun requestCancellation(selection: BunRuntimePluginClient.Selection, executionId: String) {
        BunRuntimeCleanupBinderCallLane.tryDispatch("cancel-script") {
            runCatching {
                runBlocking { BunRuntimePluginClient.cancel(appContext, selection, executionId) }
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
            BunRuntimeBinderCallLane.call(
                operationName = operation,
                timeoutMillis = deadlineMillis,
                onAbandoned = {
                    if (poisonOnAbandoned) BunRuntimeBinderCallLane.poison()
                },
                isCancelled = { stopped.get() || isDestroyed },
            ) { runBlocking { block() } }
        } catch (error: BunBinderCallException) {
            if (error.failure == BunBinderCallFailure.TIMEOUT) {
                throw BunRpcTimeoutException("Timed out while attempting to ${operation.replace('-', ' ')}")
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
            require(bytes.size <= BunRuntimeContract.MAX_SOURCE_BYTES) {
                "Bun source exceeds ${BunRuntimeContract.MAX_SOURCE_BYTES} bytes"
            }
            val directory = sourceSnapshotDirectory()
            check(directory.isDirectory || directory.mkdirs()) { "Unable to create the Bun snapshot directory" }
            val extension = logicalSourceName(source).let { name ->
                when {
                    name.endsWith(".tsx", true) -> ".bun.tsx"
                    name.endsWith(".ts", true) -> ".bun.ts"
                    else -> ".bun.js"
                }
            }
            snapshot = File.createTempFile("bun-source-", extension, directory)
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
        val maximumStoredBytes = BunRuntimeContract.MAX_SOURCE_BYTES + EncryptedScriptFileHeader.BLOCK_SIZE + 16
        require(file.isFile) { "Bun source is missing: ${file.path}" }
        val bytes = readBounded(file, maximumStoredBytes)
        if (bytes.size < EncryptedScriptFileHeader.BLOCK_SIZE || !EncryptedScriptFileHeader.isValidFile(bytes)) {
            return bytes
        }
        return try {
            ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE)
        } finally {
            bytes.fill(0)
        }
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
                    "Bun source exceeds its stored size limit: ${file.path}"
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun sourceSnapshotDirectory(): File = File(appContext.cacheDir, "bun-source-snapshots")

    private fun pruneStaleSourceSnapshots() {
        val cutoff = System.currentTimeMillis() - STALE_SNAPSHOT_MILLIS
        sourceSnapshotDirectory().listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { stale ->
                if (!stale.delete()) Log.w(TAG, "Unable to prune stale Bun source snapshot: ${stale.name}")
            }
    }

    private fun logicalSourceName(source: JavaScriptSource): String {
        if (source is TypeScriptStringSource) return source.compilationSourceName
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
            ?: "script.bun.js"
    }

    private fun executionTimeoutMillis(source: JavaScriptSource): Long {
        val config = getTag(ExecutionConfig.tag) as? ExecutionConfig
        val explicit = (config?.getArgument("bun.timeoutMillis") as? Number)?.toLong()
        val project = if (explicit == null) (source as? JavaScriptFileSource)
            ?.let { BunPluginWorkspaceArchive.findProjectRoot(semanticFile(it)) }
            ?.let { ProjectConfig.fromProjectDirForExecution(it.path)?.bunConfig?.timeoutMs } else null
        return (explicit ?: project ?: BunRuntimeContract.DEFAULT_TIMEOUT_MILLIS)
            .coerceIn(1L, BunRuntimeContract.MAX_TIMEOUT_MILLIS)
    }

    private fun requireSupportedMode(mode: Int) {
        val unsupported = mode and JavaScriptSource.EXECUTION_MODE_BUN.inv()
        require(unsupported == JavaScriptSource.EXECUTION_MODE_NORMAL) {
            "Bun Runtime does not support AutoJs6 execution-mode flags: 0x${unsupported.toString(16)}"
        }
    }

    private companion object {
        const val TAG = "BunPluginScriptEngine"
        const val STALE_SNAPSHOT_MILLIS = 60_000L
        const val SELECT_RPC_DEADLINE_MILLIS = 70_000L
        const val PREWARM_RPC_DEADLINE_MILLIS = 70_000L
        const val EXECUTION_RPC_GRACE_MILLIS = 15_000L
        const val OUTPUT_DRAIN_MILLIS = 5_000L
    }
}

private class BunRpcTimeoutException(message: String) : IllegalStateException(message)

class BunRuntimeExecutionException(message: String) : IllegalStateException(message)
