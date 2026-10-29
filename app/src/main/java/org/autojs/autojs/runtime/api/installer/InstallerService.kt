package org.autojs.autojs.runtime.api.installer

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.plugin.installer.InstallerErrorMapper
import org.autojs.autojs.core.plugin.installer.InstallerBundles
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.autojs.core.plugin.installer.InstallerPluginHost
import org.autojs.autojs.core.plugin.installer.InstallerSessionListener
import org.autojs.autojs.core.plugin.installer.InstallerSource
import org.autojs.autojs.core.plugin.installer.InstallerSourceDeletion
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.installer.InstallerScriptArguments.Source
import org.autojs.autojs.runtime.api.installer.InstallerScriptArguments.SourcePlan
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Per-script installer owner. All discovery, provider and Binder work uses its cancellable IO
 * scope. Closing rejects future work, cancels pending/running sessions and closes every local
 * descriptor, including descriptors returned after an open raced with script exit.
 *
 * V1 one-shot operations have no remote cancellation token: closing cancels their host wait and
 * releases the binding, but cannot undo an uninstall or permission action already sent to Android.
 */
class InstallerService(context: Context, parentContext: CoroutineContext) : Closeable {

    constructor(context: Context, scriptRuntime: ScriptRuntime) : this(context, scriptRuntime.coroutineContext)

    private val appContext = context.applicationContext ?: context
    private val tasks = InstallerTaskScope(parentContext)
    private val lock = Any()
    private val operations = mutableSetOf<InstallerOperation>()
    private val inspections = mutableSetOf<SourceLease>()
    private var closed = false

    val isClosed: Boolean get() = synchronized(lock) { closed || tasks.isClosed }

    suspend fun isAvailable(): Boolean = call(InstallerErrorMapper.Phase.DISCOVERY) {
        InstallerPluginHost.isAvailable(appContext)
    }

    /** Probe failures are values here; all action methods still reject PLUGIN_UNAVAILABLE. */
    suspend fun status(): Map<String, Any?> = call(InstallerErrorMapper.Phase.DISCOVERY) {
        try {
            val info = InstallerPluginHost.getSelectedInfo(appContext)
            linkedMapOf<String, Any?>("available" to true).apply {
                info.versionName.takeIf { it.isNotBlank() }?.let { put("pluginVersion", it) }
                info.capabilities?.let { put("contractVersion", InstallerBundles.peer(it).contractVersion) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val error = InstallerErrorMapper.fromThrowable(failure, InstallerErrorMapper.Phase.DISCOVERY)
            mapOf("available" to false, "reason" to error.message)
        }
    }

    suspend fun availableAuthorizers(): List<String> = call(InstallerErrorMapper.Phase.DISCOVERY) {
        authorizerStates().filter { it.second.available }.map { it.first }
    }

    /** `auto` reports aggregate readiness, without pretending to know the plugin's configured order. */
    suspend fun authorizerState(name: String = InstallerContract.AUTHORIZER_AUTO): Map<String, Any?> =
        call(InstallerErrorMapper.Phase.DISCOVERY) {
            require(InstallerContract.isAuthorizer(name)) { "unknown authorizer: $name" }
            if (name != InstallerContract.AUTHORIZER_AUTO) {
                val state = InstallerPluginHost.getAuthorizerState(appContext, name)
                authorizerValue(name, state.available, state.running, state.granted, state.reason)
            } else {
                val states = authorizerStates().map { it.second }
                val available = states.any { it.available }
                val running = states.any { it.available && it.running }
                val granted = states.any { it.available && it.running && it.granted }
                val reason = if (granted) null else if (available) {
                    "No authorizer is currently running and granted."
                } else states.mapNotNull { it.reason }.distinct().joinToString("; ").ifBlank { "No authorizer is available." }
                authorizerValue(name, available, running, granted, reason)
            }
        }

    suspend fun requestAuthorizer(
        name: String,
        timeoutMillis: Long = InstallerContract.DEFAULT_USER_ACTION_TIMEOUT_MILLIS,
    ): Boolean = call(InstallerErrorMapper.Phase.DISCOVERY) {
        require(InstallerContract.isAuthorizer(name)) { "unknown authorizer: $name" }
        require(timeoutMillis in 1L..InstallerContract.MAX_SESSION_TIMEOUT_MILLIS) { "authorizer timeout out of range" }
        if (name == InstallerContract.AUTHORIZER_AUTO) {
            // Concrete authorizers are the explicit consent paths. `auto` only selects among
            // already ready paths; merely probing auto must not trigger a Root/Shizuku prompt.
            authorizerStates().any { (_, state) -> state.available && state.running && state.granted }
        } else InstallerPluginHost.requestAuthorizer(appContext, name, timeoutMillis)
    }

    suspend fun users(): List<InstallerJson.DeviceUser> = call(InstallerErrorMapper.Phase.DISCOVERY) {
        InstallerPluginHost.getUsers(appContext)
    }

    suspend fun isDefault(): Boolean = call(InstallerErrorMapper.Phase.DISCOVERY) {
        InstallerPluginHost.getDefaultInstallerState(appContext).isSelf
    }

    /** True means the requested state was reached, including successfully disabling the default. */
    suspend fun setDefault(enabled: Boolean, authorizer: String = InstallerContract.AUTHORIZER_AUTO,
        mode: String = InstallerContract.DEFAULT_MODE_PREFERRED): Boolean =
        call(InstallerErrorMapper.Phase.DISCOVERY) {
            InstallerPluginHost.setDefaultInstaller(appContext, enabled, authorizer, mode).satisfies(enabled, mode)
        }

    suspend fun uninstall(packageName: String, options: InstallerJson.UninstallOptions): InstallerJson.UninstallResult =
        call(InstallerErrorMapper.Phase.DISCOVERY) {
            InstallerPluginHost.uninstall(appContext, packageName, options)
        }

    /** JSON (including optional icon Base64) is decoded into script objects on the script thread. */
    suspend fun inspect(source: Source): String = call(InstallerErrorMapper.Phase.DISCOVERY) {
        requirePlugin()
        val lease = SourceLease(SourcePlan(listOf(listOf(source)), isBatch = false), deleteSource = false)
        synchronized(lock) {
            ensureOpen()
            inspections += lease
        }
        try {
            InstallerPluginHost.inspect(appContext, lease.openSource(source, 0))
        } finally {
            lease.close()
            synchronized(lock) { inspections -= lease }
        }
    }

    suspend fun inspect(plan: SourcePlan): String {
        require(!plan.isBatch && plan.items.size == 1 && plan.items.single().size == 1) { "inspect requires one package source" }
        return inspect(plan.items.single().single())
    }

    fun newSession(
        plan: SourcePlan,
        options: InstallerScriptOptions,
        listener: InstallerSessionListener = object : InstallerSessionListener {},
    ): InstallerOperation = synchronized(lock) {
        ensureOpen()
        val sourceLease = SourceLease(plan, options.install.deleteSource, options.install.explicitDeleteSource)
        val backend = object : InstallerOperationBackend {
            override suspend fun open(id: String, listener: InstallerSessionListener): InstallerOperationSession {
                requirePlugin()
                val sources = plan.items.flatMapIndexed { index, item -> item.map { sourceLease.openSource(it, index) } }
                val handle = try {
                    InstallerPluginHost.openSession(
                        appContext, sources, options.interaction, options.install, listener, hostSessionId = id, isBatch = plan.isBatch,
                        origin = InstallerContract.SOURCE_SCRIPT,
                    )
                } catch (failure: Throwable) {
                    throw mapped(failure, InstallerErrorMapper.Phase.OPEN)
                }
                if (sourceLease.isClosed || isClosed) {
                    runCatching { handle.cancel() }
                    handle.close()
                    throw CancellationException("the script is exiting")
                }
                return object : InstallerOperationSession {
                    override suspend fun await() = handle.await()
                    override fun cancel(): Boolean = handle.cancel()
                    override fun close() = handle.close()
                }
            }

            override fun cancelOpening() = sourceLease.cancelOpening()
            override fun complete(value: InstallerJson.Completion): InstallerJson.Completion = sourceLease.complete(value)
            override fun close() = sourceLease.close()
        }
        InstallerOperation(plan.isBatch, options.install.timeoutMillis, options.install.authorizer, tasks, backend, listener) {
            synchronized(lock) { operations -= it }
        }.also { if (!it.isTerminal) operations += it }
    }

    suspend fun install(plan: SourcePlan, options: InstallerScriptOptions): InstallerJson.Completion {
        val operation = newSession(plan, options)
        try {
            operation.start()
            return operation.await()
        } finally {
            operation.close()
        }
    }

    override fun close() {
        val active: List<InstallerOperation>
        val opened: List<SourceLease>
        synchronized(lock) {
            if (closed) return
            closed = true
            active = operations.toList()
            opened = inspections.toList()
            operations.clear()
            inspections.clear()
        }
        active.forEach { runCatching { it.close() } }
        opened.forEach { runCatching { it.close() } }
        tasks.close()
    }

    private suspend fun authorizerStates(): List<Pair<String, InstallerJson.AuthorizerState>> =
        InstallerPluginHost.authorizers(appContext).distinct().filter { it in InstallerContract.AUTHORIZERS }
            .map { it to InstallerPluginHost.getAuthorizerState(appContext, it) }

    private fun authorizerValue(name: String, available: Boolean, running: Boolean, granted: Boolean, reason: String?): Map<String, Any?> =
        linkedMapOf<String, Any?>("name" to name, "available" to available, "running" to running, "granted" to granted).apply {
            if (reason != null) put("reason", reason)
        }

    private suspend fun requirePlugin() {
        try {
            InstallerPluginHost.getCapabilities(appContext)
        } catch (failure: Throwable) {
            throw mapped(failure, InstallerErrorMapper.Phase.DISCOVERY)
        }
    }

    private fun ensureOpen() {
        if (closed) throw InstallerPluginException(InstallerErrorMapper.error(
            InstallerErrorCodes.CANCELLED, "the script is exiting", retryable = false,
        ))
        tasks.checkOpen()
    }

    private suspend fun <T> call(phase: InstallerErrorMapper.Phase, block: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        val pending = synchronized(lock) {
            ensureOpen()
            tasks.prepare(block = block)
        }
        try {
            return pending.await()
        } catch (failure: Throwable) {
            pending.cancel()
            throw mapped(failure, phase)
        }
    }

    private fun mapped(failure: Throwable, phase: InstallerErrorMapper.Phase): InstallerPluginException =
        failure as? InstallerPluginException ?: InstallerPluginException(InstallerErrorMapper.fromThrowable(failure, phase), failure)

    private inner class SourceLease(private val plan: SourcePlan, private val deleteSource: Boolean,
        private val explicitDeleteSource: Boolean? = deleteSource) : Closeable {
        private val sourceLock = Any()
        private val signal = CancellationSignal()
        private val cancelSent = AtomicBoolean()
        private val sources = mutableListOf<Pair<InstallerSource, String?>>()
        private var closed = false

        val isClosed: Boolean get() = synchronized(sourceLock) { closed }

        suspend fun openSource(source: Source, item: Int): InstallerSource = suspendCancellableCoroutine { continuation ->
            // Registered before entering the provider: cancellation can unblock a stalled query
            // or open even while its calling IO thread has not returned.
            continuation.invokeOnCancellation { close() }
            try {
                synchronized(sourceLock) { if (closed) throw CancellationException("package sources are closed") }
                val opened = when (source) {
                    is Source.FilePath -> InstallerSource.fromFile(source.file, item)
                    is Source.ContentUri -> InstallerSource.fromUri(appContext, Uri.parse(source.uri), item, cancellationSignal = signal)
                }
                synchronized(sourceLock) {
                    if (closed) {
                        opened.close()
                        throw CancellationException("package sources are closed")
                    }
                    sources += opened to opened.ownershipKey
                }
                continuation.resume(opened)
            } catch (failure: Throwable) {
                val cause = if (isClosed) CancellationException("package sources are closed") else failure
                continuation.resumeWithException(mapped(cause, InstallerErrorMapper.Phase.SOURCE))
            }
        }

        fun cancelOpening() {
            if (cancelSent.compareAndSet(false, true)) tasks.control { signal.cancel() }
        }

        fun complete(completion: InstallerJson.Completion): InstallerJson.Completion {
            val opened = synchronized(sourceLock) { sources.toList() }
            val deletionSources = plan.items.flatMapIndexed { item, itemSources ->
                itemSources.mapIndexed { sourceIndex, source ->
                    val owned = opened.filter { it.first.item == item }.getOrNull(sourceIndex)
                    object : InstallerSourceDeletion.Source {
                        override val item: Int = item
                        override val isOwnedFile: Boolean = source is Source.FilePath
                        override val ownershipKey: String? = owned?.second ?: (source as? Source.FilePath)?.file?.absolutePath
                        override val retentionKey: String? = owned?.first?.retentionKey ?: ownershipKey
                        override fun deleteIfUnchanged(): Boolean = owned?.first?.deleteOwnedFileIfUnchanged() == true
                    }
                }
            }
            return InstallerSourceDeletion.apply(completion, deletionSources, deleteSource, explicitDeleteSource)
        }

        override fun close() {
            val opened: List<InstallerSource>
            synchronized(sourceLock) {
                if (closed) return
                closed = true
                opened = sources.map { it.first }
                sources.clear()
            }
            opened.forEach { it.close() }
            cancelOpening()
        }
    }
}
