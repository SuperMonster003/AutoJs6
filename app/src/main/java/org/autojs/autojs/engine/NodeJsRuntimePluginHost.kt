package org.autojs.autojs.engine

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.PluginBindRefusedException
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin
import org.autojs.plugin.nodejs.api.NodeJsPluginActions
import org.autojs.plugin.nodejs.api.NodeJsPluginIds
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Application-scoped discovery cache and Binder session for the external Node runtime. */
object NodeJsRuntimePluginHost {

    private const val TAG = "NodeJsRuntimePluginHost"
    private const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    private const val DEFAULT_BIND_TIMEOUT_MS = 10_000L

    private val handler = Handler(Looper.getMainLooper())
    private val candidateCacheLock = Any()
    private val observerLock = Any()
    private val poolLock = Any()
    private val sessionGeneration = AtomicLong(0L)
    private val binderDeathCount = AtomicLong(0L)
    private val reconnectCount = AtomicLong(0L)
    private val binderCallExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "AutoJs6-Node-Plugin-Rpc").apply { isDaemon = true }
    }
    private val connectionPool = LinkedHashMap<ComponentName, PooledConnection>()
    private val executionRegistry = NodeRuntimePluginExecutionRegistry<AcquiredRuntime>()
    private val launchPrewarmInFlight = java.util.concurrent.atomic.AtomicBoolean()

    /** Pooled connections and registered executions, for the bridge leak gate (docs/memory-phase2/ROADMAP.md T1 (h)). zh-CN: 连接池与执行注册表的大小, 供桥接泄漏门禁使用 (docs/memory-phase2/ROADMAP.md T1 (h)). */
    internal fun pooledConnectionCount(): Int = synchronized(poolLock) { connectionPool.size }
    internal fun registeredExecutionCount(): Int = executionRegistry.size()

    /** Start one worker while the host prepares its engine, TypeScript output and workspace. */
    @JvmStatic
    fun prepareForExecution(context: Context) {
        if (!launchPrewarmInFlight.compareAndSet(false, true)) return
        binderCallExecutor.execute {
            try {
                kotlinx.coroutines.runBlocking {
                    prewarm(context.applicationContext, Bundle().apply {
                        putInt(NodeJsRuntimeContract.KEY_PREWARM_SLOTS, 1)
                    })
                }
            } catch (_: Exception) {
                // The actual execution reports discovery/connection errors through its normal path.
            } finally { launchPrewarmInFlight.set(false) }
        }
    }

    private var candidateInvalidationGeneration = 0L
    private var candidateDiscoveryInFlightCount = 0
    private var cachedCandidates: CandidateSnapshot? = null
    private var observerContext: Context? = null

    private val pluginStateChangedListener: (String) -> Unit = { packageName ->
        invalidateCachedState(packageName, "host_plugin_state_changed")
    }

    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart
            if (!shouldInvalidateForPackageChange(context, packageName, intent.action)) return
            invalidateCachedState(packageName, intent.action ?: "package_changed")
        }
    }

    data class Candidate(
        val serviceInfo: ServiceInfo,
        val packageVersionCode: Long?,
        val packageLastUpdateTime: Long = 0L,
        val packageUid: Int = -1,
        val signerSha256: String? = null,
        val authorizationState: String = "",
    ) {
        val component: ComponentName
            get() = ComponentName(serviceInfo.packageName, serviceInfo.name)

        internal val identity: String
            get() = buildString {
                append(component.flattenToShortString())
                append('@').append(packageVersionCode ?: -1L)
                append(':').append(packageLastUpdateTime)
                append(':').append(packageUid)
                append(':').append(signerSha256.orEmpty())
            }
    }

    data class OperationDiagnostics(
        val discoveryElapsedMs: Long,
        val candidateCacheHit: Boolean,
        val candidateCacheGeneration: Long,
        val bindElapsedMs: Long,
        val sessionReused: Boolean,
        val sessionGeneration: Long,
        val binderDeathCount: Long,
        val reconnectCount: Long,
        val packageName: String,
        val serviceName: String,
    ) {
        fun toNativeValues(): Map<String, String> = linkedMapOf(
            "embedded_script.runtime_plugin.discovery_elapsed_ms" to discoveryElapsedMs.toString(),
            "embedded_script.runtime_plugin.candidate_cache_hit" to candidateCacheHit.toString(),
            "embedded_script.runtime_plugin.candidate_cache_generation" to candidateCacheGeneration.toString(),
            "embedded_script.runtime_plugin.bind_elapsed_ms" to bindElapsedMs.toString(),
            "embedded_script.runtime_plugin.session_reused" to sessionReused.toString(),
            "embedded_script.runtime_plugin.session_generation" to sessionGeneration.toString(),
            "embedded_script.runtime_plugin.binder_death_count" to binderDeathCount.toString(),
            "embedded_script.runtime_plugin.reconnect_count" to reconnectCount.toString(),
            "embedded_script.runtime_plugin.session_package_name" to packageName,
            "embedded_script.runtime_plugin.session_service_name" to serviceName,
        )
    }

    data class ScriptRunResult(
        val result: Bundle,
        val runtimeInfo: Bundle,
        val candidate: Candidate,
        val diagnostics: OperationDiagnostics,
    )

    private data class CandidateSnapshot(
        val generation: Long,
        val candidates: List<Candidate>,
    )

    private data class DiscoveryResult(
        val candidates: List<Candidate>,
        val elapsedMs: Long,
        val cacheHit: Boolean,
        val generation: Long,
    )

    private data class AcquiredRuntime(
        val runtime: INodeJsRuntimePlugin,
        val entry: PooledConnection,
        val binder: IBinder,
        val bindingGeneration: Long,
        val bindElapsedMs: Long,
        val sessionReused: Boolean,
        val sessionGeneration: Long,
        var maxContractVersion: Int = NodeJsRuntimeContract.CONTRACT_VERSION,
        var asynchronousExecution: Boolean = false,
    )

    private class BindingAttempt(
        val generation: Long,
        val startedAt: Long,
    ) {
        lateinit var connection: ServiceConnection
        var registered = false
        var binder: IBinder? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var timeout: Runnable? = null
    }

    suspend fun discover(context: Context): List<Candidate> {
        return discoverDetailed(context).candidates
    }

    suspend fun probe(context: Context): Bundle {
        val discovery = discoverDetailed(context)
        val candidate = discovery.candidates.firstOrNull()
            ?: throw NodeRuntimePluginUnavailableException("No enabled trusted Node.js runtime plugin service found.")
        val (acquired, runtimeInfo) = acquireRuntimeInfo(context, candidate, refresh = true)
        return Bundle(runtimeInfo).apply {
            putLong("hostDiscoveryElapsedMs", discovery.elapsedMs)
            putBoolean("hostCandidateCacheHit", discovery.cacheHit)
            putLong("hostBindElapsedMs", acquired.bindElapsedMs)
            putBoolean("hostSessionReused", acquired.sessionReused)
            putLong("hostSessionGeneration", acquired.sessionGeneration)
        }
    }

    suspend fun prewarm(context: Context, request: Bundle = Bundle.EMPTY): Bundle {
        val discovery = discoverDetailed(context)
        val candidate = discovery.candidates.firstOrNull()
            ?: throw NodeRuntimePluginUnavailableException("No enabled trusted Node.js runtime plugin service found.")
        val (acquired, _) = acquireRuntimeInfo(context, candidate)
        val negotiatedRequest = Bundle(request).apply {
            putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION)
        }
        return try {
            acquired.runtime.prewarmRuntime(negotiatedRequest) ?: Bundle.EMPTY
        } catch (error: RemoteException) {
            invalidateAcquired(acquired, error, binderFailure = true)
            throw NodeRuntimePluginOperationException(
                message = "Node.js runtime plugin prewarm failed: ${candidate.component}",
                cause = error,
                candidate = candidate,
                diagnostics = acquired.toOperationDiagnostics(discovery),
                dispatchStarted = false,
            )
        }
    }

    suspend fun runScript(
        context: Context,
        request: Bundle,
        callback: INodeJsRuntimeCallback? = null,
    ): Bundle {
        return runScriptWithInfo(context, request, callback).result
    }

    internal fun reserveExecution(
        executionId: String,
    ): NodeRuntimePluginExecutionRegistry.Reservation? = executionRegistry.reserve(executionId)

    internal fun releaseExecution(reservation: NodeRuntimePluginExecutionRegistry.Reservation) =
        executionRegistry.release(reservation)

    internal suspend fun runScriptWithInfo(
        context: Context,
        request: Bundle,
        callback: INodeJsRuntimeCallback? = null,
        reservation: NodeRuntimePluginExecutionRegistry.Reservation? = null,
    ): ScriptRunResult {
        val executionId = request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID).orEmpty()
        val claimedReservation = claimExecutionReservation(executionId, reservation)
        return try {
            runReservedScriptWithInfo(context, request, callback, claimedReservation)
        } finally {
            releaseExecution(claimedReservation)
        }
    }

    private suspend fun runReservedScriptWithInfo(
        context: Context,
        request: Bundle,
        callback: INodeJsRuntimeCallback?,
        reservation: NodeRuntimePluginExecutionRegistry.Reservation,
    ): ScriptRunResult {
        val discovery = discoverDetailed(context)
        val candidate = discovery.candidates.firstOrNull()
            ?: throw NodeRuntimePluginUnavailableException("No enabled trusted Node.js runtime plugin service found.")
        val acquired: AcquiredRuntime
        val runtimeInfo: Bundle
        try {
            val acquiredWithInfo = acquireRuntimeInfo(context, candidate)
            acquired = acquiredWithInfo.first
            runtimeInfo = acquiredWithInfo.second
        } catch (error: NodeRuntimePluginContractException) {
            throw error
        } catch (error: Throwable) {
            throw NodeRuntimePluginOperationException(
                message = "Node.js runtime plugin session preparation failed: ${candidate.component}",
                cause = error,
                candidate = candidate,
                diagnostics = null,
                dispatchStarted = false,
            )
        }
        val diagnostics = acquired.toOperationDiagnostics(discovery)
        acquired.maxContractVersion = runtimeInfo.getInt(
            NodeJsRuntimeContract.KEY_MAX_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION,
        )
        acquired.asynchronousExecution = acquired.maxContractVersion >= NodeJsRuntimeContract.START_SCRIPT_CONTRACT_VERSION &&
                runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES)
                    ?.contains(NodeJsRuntimeContract.CAPABILITY_ASYNC_SCRIPT_EXECUTION) == true
        if (!markExecutionDispatched(reservation, acquired)) {
            throw NodeRuntimePluginOperationException(
                message = "Node.js runtime plugin execution was cancelled before Binder dispatch: " +
                        "'${reservation.executionId}'.",
                candidate = candidate,
                diagnostics = diagnostics,
                dispatchStarted = false,
            )
        }
        val issuedAt = SystemClock.elapsedRealtime()
        val result = try {
            // From this point onward a failed transaction is not safe to replay:
            // the remote process may have executed user code before Binder died.
            invokeRunScript(acquired, request, callback)
        } catch (error: Throwable) {
            if (error is RemoteException) {
                invalidateAcquired(acquired, error, binderFailure = true)
            }
            throw NodeRuntimePluginOperationException(
                message = "Node.js runtime plugin execution response was lost: ${candidate.component}",
                cause = error,
                candidate = candidate,
                diagnostics = diagnostics,
                dispatchStarted = true,
            )
        }
        result.putLong("hostRunScriptIssuedAtMs", issuedAt)
        return ScriptRunResult(
            result = result,
            runtimeInfo = runtimeInfo,
            candidate = candidate,
            diagnostics = diagnostics,
        )
    }

    private fun claimExecutionReservation(
        executionId: String,
        reservation: NodeRuntimePluginExecutionRegistry.Reservation?,
    ): NodeRuntimePluginExecutionRegistry.Reservation {
        if (executionId.isBlank()) {
            throw NodeRuntimePluginUnavailableException("Node.js runtime plugin execution ID must not be empty.")
        }
        if (reservation == null) {
            return reserveExecution(executionId)
                ?: throw NodeRuntimePluginUnavailableException(
                    "Duplicate Node.js runtime plugin execution ID: '$executionId'.",
                )
        }
        if (reservation.executionId != executionId) {
            throw NodeRuntimePluginUnavailableException(
                "Node.js runtime plugin reservation '${reservation.executionId}' does not match '$executionId'.",
            )
        }
        if (!executionRegistry.isActive(reservation)) {
            throw NodeRuntimePluginUnavailableException(
                "Node.js runtime plugin execution reservation is no longer active: '$executionId'.",
            )
        }
        return reservation
    }

    /** Atomically closes the pre-dispatch cancellation window. */
    private fun markExecutionDispatched(
        reservation: NodeRuntimePluginExecutionRegistry.Reservation,
        acquired: AcquiredRuntime,
    ): Boolean {
        return executionRegistry.markDispatched(reservation, acquired)
    }

    private fun invokeRunScript(
        acquired: AcquiredRuntime,
        request: Bundle,
        callback: INodeJsRuntimeCallback?,
    ): Bundle {
        val startedAt = SystemClock.elapsedRealtime()
        val executionId = request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID).orEmpty()
        val completion = CompletableFuture<Bundle>()
        val death = IBinder.DeathRecipient { completion.completeExceptionally(android.os.DeadObjectException()) }
        if (acquired.asynchronousExecution) acquired.binder.linkToDeath(death, 0)
        val future: Future<Bundle> = if (acquired.asynchronousExecution) {
            val terminalCallback = object : INodeJsRuntimeCallback.Stub() {
                override fun onEvent(event: Bundle) {
                    if (event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE) == NodeJsRuntimeContract.EVENT_FINISHED) {
                        val result = event.getBundle(NodeJsRuntimeContract.KEY_EVENT_RESULT)
                        if (event.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID) == executionId && result != null) {
                            completion.complete(result)
                        } else {
                            completion.completeExceptionally(RemoteException("Invalid Node.js runtime completion."))
                        }
                    }
                    callback?.onEvent(event)
                }
            }
            // Only the acknowledgement occupies an RPC thread. Completion may arrive before it.
            CompletableFuture.supplyAsync({
                val ack = acquired.runtime.startScript(request, terminalCallback)
                    ?: throw RemoteException("Missing Node.js runtime acknowledgement.")
                if (ack.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID) != executionId) {
                    throw RemoteException("Invalid Node.js runtime acknowledgement.")
                }
                ack
            }, binderCallExecutor).thenCompose { ack ->
                if (ack.getBoolean(NodeJsRuntimeContract.KEY_ACCEPTED)) completion
                else CompletableFuture.completedFuture(ack)
            }
        } else {
            binderCallExecutor.submit<Bundle> { acquired.runtime.runScript(request, callback) ?: Bundle.EMPTY }
        }
        val timeoutMs = request.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 0L)
        return try {
            if (timeoutMs > 0L) {
                future.get(
                    NodeRuntimePluginRpcDeadline.responseWaitTimeoutMs(timeoutMs),
                    TimeUnit.MILLISECONDS,
                )
            } else {
                future.get()
            }
        } catch (_: TimeoutException) {
            future.cancel(true)
            scheduleBoundedCancellation(acquired, executionId)
            Bundle().apply {
                putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION)
                putBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false)
                putInt(NodeJsRuntimeContract.KEY_EXIT_CODE, 1)
                putString(NodeJsRuntimeContract.KEY_ERROR_NAME, "NodeRuntimePluginExecutionTimeoutError")
                putString(
                    NodeJsRuntimeContract.KEY_ERROR_MESSAGE,
                    "Node.js runtime plugin execution exceeded ${timeoutMs} ms.",
                )
                putString(NodeJsRuntimeContract.KEY_ERROR_CODE, NodeRuntimeErrorCodes.SCRIPT_TIMEOUT)
                putBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, true)
                putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, timeoutMs)
                putLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, elapsedSince(startedAt))
                putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, request.getString(NodeJsRuntimeContract.KEY_SOURCE_NAME))
                putString(
                    NodeJsRuntimeContract.KEY_WORKING_DIRECTORY,
                    request.getString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY),
                )
                putStringArray(
                    NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                    arrayOf(
                        "embedded_script.runtime_plugin.enabled=true",
                        "embedded_script.runtime_plugin.backend_dispatched=true",
                        "embedded_script.runtime_plugin.host_timeout=true",
                        "embedded_script.runtime_plugin.fallback_safe=false",
                        "embedded_script.error_code=${NodeRuntimeErrorCodes.SCRIPT_TIMEOUT}",
                    ),
                )
            }
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        } finally {
            if (acquired.asynchronousExecution) acquired.binder.unlinkToDeath(death, 0)
        }
    }

    private fun scheduleBoundedCancellation(acquired: AcquiredRuntime, executionId: String) {
        val cancellation = binderCallExecutor.submit {
            try {
                acquired.runtime.cancelScript(executionId)
            } catch (error: RemoteException) {
                invalidateAcquired(acquired, error, binderFailure = true)
            }
        }
        handler.postDelayed(
            { cancellation.cancel(true) },
            NodeRuntimePluginRpcDeadline.CANCEL_RPC_BUDGET_MS,
        )
    }

    fun postMessage(executionId: String, message: Bundle): Boolean {
        val dispatched = executionRegistry.dispatched(executionId) ?: return false
        if (dispatched.maxContractVersion < NodeJsRuntimeContract.POST_MESSAGE_CONTRACT_VERSION) return false
        val runtime = synchronized(dispatched.entry.lock) {
            dispatched.runtime.takeIf {
                dispatched.entry.matchesBindingLocked(dispatched.bindingGeneration, dispatched.binder) &&
                        dispatched.binder.isBinderAlive
            }
        } ?: return false
        return try {
            runtime.postMessage(executionId, message)
        } catch (error: RemoteException) {
            invalidateAcquired(dispatched, error, binderFailure = true)
            false
        }
    }

    suspend fun cancelScript(context: Context, executionId: String): Boolean {
        val dispatched = when (val cancellation = executionRegistry.cancel(executionId)) {
            NodeRuntimePluginExecutionRegistry.Cancellation.Unknown -> return false
            NodeRuntimePluginExecutionRegistry.Cancellation.BeforeDispatch -> return true
            is NodeRuntimePluginExecutionRegistry.Cancellation.AfterDispatch -> cancellation.value
        }
        val activeRuntime = synchronized(dispatched.entry.lock) {
            dispatched.runtime.takeIf {
                dispatched.entry.matchesBindingLocked(dispatched.bindingGeneration, dispatched.binder) &&
                        dispatched.binder.isBinderAlive
            }
        } ?: return false
        return try {
            activeRuntime.cancelScript(executionId)
        } catch (error: RemoteException) {
            invalidateAcquired(dispatched, error, binderFailure = true)
            false
        }
    }

    /** Closes the cached Binder session without shutting down the plugin process. */
    fun closeSession(context: Context, reason: String = "host_session_closed") {
        ensureObservers(context.applicationContext)
        invalidateConnections(packageName = null, reason = reason)
    }

    internal fun invalidateForTest(context: Context, packageName: String? = null) {
        ensureObservers(context.applicationContext)
        invalidateCachedState(packageName, "instrumentation")
    }

    internal fun validateRuntimeInfoForTest(runtimeInfo: Bundle) {
        validateRuntimeInfo(runtimeInfo)
    }

    private fun discoverDetailed(context: Context): DiscoveryResult {
        val startedAt = SystemClock.elapsedRealtime()
        val appContext = context.applicationContext
        ensureObservers(appContext)
        synchronized(candidateCacheLock) {
            cachedCandidates?.let { snapshot ->
                return DiscoveryResult(
                    candidates = snapshot.candidates,
                    elapsedMs = elapsedSince(startedAt),
                    cacheHit = true,
                    generation = snapshot.generation,
                )
            }
        }

        var candidates = emptyList<Candidate>()
        var generation = 0L
        repeat(2) {
            generation = synchronized(candidateCacheLock) { candidateInvalidationGeneration }
            synchronized(candidateCacheLock) { candidateDiscoveryInFlightCount += 1 }
            candidates = try {
                queryAuthorizedRuntimeCandidates(appContext)
            } finally {
                synchronized(candidateCacheLock) { candidateDiscoveryInFlightCount -= 1 }
            }
            val stored = synchronized(candidateCacheLock) {
                if (generation == candidateInvalidationGeneration) {
                    cachedCandidates = CandidateSnapshot(generation, candidates)
                    true
                } else {
                    false
                }
            }
            if (stored) {
                return DiscoveryResult(
                    candidates = candidates,
                    elapsedMs = elapsedSince(startedAt),
                    cacheHit = false,
                    generation = generation,
                )
            }
        }
        return DiscoveryResult(
            candidates = candidates,
            elapsedMs = elapsedSince(startedAt),
            cacheHit = false,
            generation = generation,
        )
    }

    @JvmStatic
    fun isPackagedRuntimeAvailable(context: Context): Boolean =
        queryAuthorizedRuntimeCandidates(context).isNotEmpty()

    /** Called only by an interactive packaged-app launch, before runtime binding. */
    fun activatePackagedRuntimeIfNeeded(activity: android.app.Activity) {
        val candidate = queryAuthorizedRuntimeCandidates(activity).firstOrNull() ?: return
        if (candidate.serviceInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_STOPPED == 0) return
        val intent = Intent(NodeJsPluginActions.PACKAGED_WAKE)
            .setPackage(candidate.serviceInfo.packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        val component = intent.resolveActivity(activity.packageManager) ?: return
        activity.startActivity(intent.setComponent(component))
    }

    private fun runtimeAction(): String = if (BuildConfig.isInrt) {
        NodeJsPluginActions.PACKAGED_RUNTIME
    } else NodeJsPluginActions.RUNTIME

    private fun runtimePermission(): String = if (BuildConfig.isInrt) {
        NodeJsPluginActions.PACKAGED_RUNTIME_PERMISSION
    } else PLUGIN_PERMISSION

    private fun queryAuthorizedRuntimeCandidates(context: Context): List<Candidate> {
        return queryRuntimeServices(context)
            .asSequence()
            .filter { it.exported && it.enabled && it.applicationInfo.enabled }
            .filter { it.permission == runtimePermission() }
            .filter {
                context.packageManager.checkPermission(runtimePermission(), context.packageName) ==
                        PackageManager.PERMISSION_GRANTED
            }
            .filter { BuildConfig.isInrt || PluginEnableStore.isEnabled(context, it.packageName, true) }
            .filter { BuildConfig.isInrt || isHostVersionCompatible(it) }
            .mapNotNull { serviceInfo ->
                val trustInfo = PluginTrustManager.resolveTrustInfo(context, serviceInfo.packageName)
                if (trustInfo.authorizedState !in AUTHORIZED_STATES) {
                    return@mapNotNull null
                }
                val packageInfo = packageInfo(context, serviceInfo.packageName) ?: return@mapNotNull null
                Candidate(
                    serviceInfo = serviceInfo,
                    packageVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                    packageLastUpdateTime = packageInfo.lastUpdateTime,
                    packageUid = serviceInfo.applicationInfo.uid,
                    signerSha256 = trustInfo.primaryFingerprintSha256,
                    authorizationState = trustInfo.authorizedState.name.lowercase(),
                )
            }
            .sortedWith(
                compareByDescending<Candidate> { it.packageVersionCode ?: 0L }
                    .thenByDescending { it.packageLastUpdateTime }
                    .thenBy { it.serviceInfo.packageName }
                    .thenBy { it.serviceInfo.name },
            )
            .toList()
    }

    private fun queryRuntimeServices(context: Context, packageName: String? = null): List<ServiceInfo> {
        val intent = Intent(runtimeAction()).apply {
            addCategory(NodeJsPluginIds.ENGINE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            packageName?.let(::setPackage)
        }
        val flags = PackageManager.GET_META_DATA
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, flags)
        }
        return resolveList.mapNotNull { it.serviceInfo }
    }

    private fun shouldInvalidateForPackageChange(
        context: Context,
        packageName: String?,
        action: String?,
    ): Boolean {
        if (packageName.isNullOrBlank()) return true
        val knownCandidate = synchronized(candidateCacheLock) {
            candidateDiscoveryInFlightCount > 0 ||
                    cachedCandidates?.candidates?.any {
                        it.serviceInfo.packageName == packageName
                    } == true
        } || synchronized(poolLock) {
            connectionPool.values.any { it.candidate.serviceInfo.packageName == packageName }
        }
        if (knownCandidate) return true
        if (
            action !in setOf(
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED,
            )
        ) {
            return false
        }
        return runCatching {
            queryRuntimeServices(context, packageName).isNotEmpty()
        }.getOrDefault(false)
    }

    private fun isHostVersionCompatible(serviceInfo: ServiceInfo): Boolean {
        @Suppress("DEPRECATION")
        val requiresHostVersion = serviceInfo.metaData
            ?.get(PluginCapabilityKeys.REQUIRES_HOST_VERSION)
            ?.let(PluginCapabilityResolver::coerceLong)
        return PluginCapabilityResolver.isHostVersionCompatible(
            BuildConfig.VERSION_CODE.toLong(),
            requiresHostVersion,
        )
    }

    private fun packageInfo(context: Context, packageName: String) = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
    }.getOrNull()

    private suspend fun acquireRuntimeInfo(
        context: Context,
        candidate: Candidate,
        refresh: Boolean = false,
    ): Pair<AcquiredRuntime, Bundle> {
        var acquired = PluginAutoWake.retryRefusedBind(context.applicationContext, candidate.component.packageName) {
            awaitRuntime(context.applicationContext, candidate)
        }
        if (!refresh) {
            synchronized(acquired.entry.lock) {
                acquired.entry.runtimeInfo?.let { cached ->
                    return acquired to Bundle(cached)
                }
            }
        }
        var runtimeInfo = try {
            invokeRuntimeInfo(acquired)
        } catch (error: RemoteException) {
            invalidateAcquired(acquired, error, binderFailure = true)
            acquired = PluginAutoWake.retryRefusedBind(context.applicationContext, candidate.component.packageName) {
                awaitRuntime(context.applicationContext, candidate)
            }
            try {
                invokeRuntimeInfo(acquired)
            } catch (retryError: RemoteException) {
                invalidateAcquired(acquired, retryError, binderFailure = true)
                throw retryError
            }
        }
        try {
            validateRuntimeInfo(runtimeInfo)
        } catch (error: NodeRuntimePluginContractException) {
            invalidateAcquired(acquired, error, binderFailure = false)
            throw error
        }
        if (!refresh) {
            synchronized(acquired.entry.lock) {
                if (acquired.entry.sessionGeneration == acquired.sessionGeneration) {
                    acquired.entry.runtimeInfo = Bundle(runtimeInfo)
                }
            }
        }
        return acquired to runtimeInfo
    }

    private fun invokeRuntimeInfo(acquired: AcquiredRuntime): Bundle {
        val future = binderCallExecutor.submit<Bundle> {
            acquired.runtime.runtimeInfo ?: Bundle.EMPTY
        }
        return try {
            future.get(
                NodeRuntimePluginRpcDeadline.RUNTIME_INFO_RPC_BUDGET_MS,
                TimeUnit.MILLISECONDS,
            )
        } catch (_: TimeoutException) {
            future.cancel(true)
            val error = NodeRuntimePluginUnavailableException(
                "Node.js runtime plugin information RPC exceeded " +
                        "${NodeRuntimePluginRpcDeadline.RUNTIME_INFO_RPC_BUDGET_MS} ms.",
            )
            invalidateAcquired(acquired, error, binderFailure = true)
            throw error
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        }
    }

    private fun validateRuntimeInfo(runtimeInfo: Bundle) {
        val contractVersion = runtimeInfo.getInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, 0)
        if (!NodeJsRuntimeContract.supportsContractVersion(contractVersion)) {
            throw NodeRuntimePluginContractException(
                "Unsupported Node.js runtime plugin contract $contractVersion; " +
                        "host requires ${NodeJsRuntimeContract.CONTRACT_VERSION}.",
            )
        }
        val runtimeSlot = runtimeInfo.getString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT).orEmpty()
        val nodeVersion = runtimeInfo.getString(NodeJsRuntimeContract.KEY_NODE_VERSION).orEmpty()
        if (runtimeSlot.isBlank() || nodeVersion.isBlank()) {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin omitted runtimeSlot or nodeVersion during negotiation.",
            )
        }
        val capabilities = runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES).orEmpty().toSet()
        val missing = REQUIRED_CAPABILITIES.filterNot(capabilities::contains)
        if (missing.isNotEmpty()) {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin is missing mandatory capabilities: ${missing.joinToString()}.",
            )
        }
        val processAbi = runtimeInfo.getString("processAbi").orEmpty()
        val supportedAbis = runtimeInfo.getStringArray("supportedAbis").orEmpty().toSet()
        if (processAbi.isBlank() || processAbi !in supportedAbis || processAbi !in Build.SUPPORTED_ABIS) {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin reported an unsupported process ABI '$processAbi'.",
            )
        }
        validateLifecycleContract(runtimeInfo)
    }

    /** Each runtime remains single-active; an advertised process pool may schedule several runtimes. */
    private fun validateLifecycleContract(runtimeInfo: Bundle) {
        if (!runtimeInfo.getBoolean("persistentProcessRuntime", false)) {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin does not advertise a persistent process runtime.",
            )
        }
        if (!runtimeInfo.getBoolean("dedicatedRuntimeProcess", false)) {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin does not run in a dedicated runtime process.",
            )
        }
        val maxConcurrentExecutions = runtimeInfo.getInt("maxConcurrentExecutions", 0)
        val processPool = runtimeInfo.getString("processModel") == "process_pool" &&
                runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES).orEmpty().contains("processPoolExecution")
        if (maxConcurrentExecutions < 1 || (maxConcurrentExecutions != 1 && !processPool)) {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin reported unsupported execution capacity $maxConcurrentExecutions.",
            )
        }
        val queueCapacity = runtimeInfo.getInt("queueCapacity", -1)
        if (queueCapacity < 0) {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin reported an invalid admission queue capacity $queueCapacity.",
            )
        }
        val cancellationMode = runtimeInfo.getString("cancellationMode").orEmpty()
        if (cancellationMode !in SUPPORTED_CANCELLATION_MODES) {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin reported an unsupported cancellation mode '$cancellationMode'; " +
                        "host supports ${SUPPORTED_CANCELLATION_MODES.joinToString()}.",
            )
        }
    }

    private suspend fun awaitRuntime(appContext: Context, candidate: Candidate): AcquiredRuntime {
        val entry = getOrCreateEntry(appContext, candidate)
        return suspendCancellableCoroutine { continuation ->
            var bindingToStart: BindingAttempt? = null
            var staleConnectionToUnbind: ServiceConnection? = null
            synchronized(entry.lock) {
                if (entry.retired) {
                    continuation.resumeWithException(
                        NodeRuntimePluginUnavailableException("Node.js runtime plugin candidate was retired: ${entry.component}"),
                    )
                    return@suspendCancellableCoroutine
                }
                val existing = entry.proxy
                val existingBinder = entry.binder
                val existingBinding = entry.activeBinding
                if (existing != null &&
                    existingBinder?.isBinderAlive == true &&
                    existingBinding != null &&
                    entry.matchesBindingLocked(existingBinding.generation, existingBinder)
                ) {
                    continuation.resume(
                        AcquiredRuntime(
                            runtime = existing,
                            entry = entry,
                            binder = existingBinder,
                            bindingGeneration = existingBinding.generation,
                            bindElapsedMs = 0L,
                            sessionReused = true,
                            sessionGeneration = entry.sessionGeneration,
                        ),
                    )
                    return@suspendCancellableCoroutine
                }
                entry.waiters += continuation
                if (!entry.connecting) {
                    val hadDeadSession = entry.proxy != null || entry.binder != null
                    staleConnectionToUnbind = entry.detachActiveBindingLocked()
                    if (hadDeadSession) {
                        entry.binderDeathCount = binderDeathCount.incrementAndGet()
                    }
                    bindingToStart = entry.beginBindingLocked()
                }
            }
            postUnbind(entry.appContext, staleConnectionToUnbind)
            bindingToStart?.let { startBind(entry, it) }
            continuation.invokeOnCancellation {
                synchronized(entry.lock) { entry.waiters.remove(continuation) }
            }
        }
    }

    private fun getOrCreateEntry(appContext: Context, candidate: Candidate): PooledConnection {
        var retiredEntry: PooledConnection? = null
        val entry = synchronized(poolLock) {
            val existing = connectionPool[candidate.component]
            if (existing != null && existing.candidate.identity == candidate.identity && !existing.retired) {
                existing
            } else {
                if (existing != null) {
                    connectionPool.remove(candidate.component)
                    retiredEntry = existing
                }
                PooledConnection(appContext, candidate).also { connectionPool[candidate.component] = it }
            }
        }
        retiredEntry?.let { retireEntry(it, "candidate_identity_changed") }
        return entry
    }

    private fun startBind(entry: PooledConnection, binding: BindingAttempt) {
        val intent = Intent(runtimeAction()).setComponent(entry.component)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        // A refused bind (false or SecurityException) stays registered with the context until unbindService, so it is
        // released here, whether or not this attempt is still the active binding; it never becomes `registered` (H28 / Q46).
        // zh-CN: 被拒绝的绑定 (false 或 SecurityException) 在 unbindService 之前一直登记在 Context 中, 因此在此释放,
        // 不论这次尝试是否仍是当前绑定; 它不会被标记为 `registered` (H28 / Q46).
        val outcome = NodeRuntimePluginBindCall.attempt(
            bind = { entry.appContext.bindService(intent, binding.connection, Context.BIND_AUTO_CREATE) },
            release = { postUnbind(entry.appContext, binding.connection) },
        )
        when (outcome) {
            is NodeRuntimePluginBindCall.Outcome.Denied -> {
                failWaiters(
                    entry,
                    binding,
                    NodeRuntimePluginUnavailableException(
                        "bindService permission denied: ${entry.component}",
                        outcome.error,
                    ),
                )
                return
            }
            NodeRuntimePluginBindCall.Outcome.Refused -> {
                failWaiters(
                    entry,
                    binding,
                    NodeRuntimePluginUnavailableException(
                        "bindService failed: ${entry.component}",
                        PluginBindRefusedException(entry.component.packageName, "bindService failed: ${entry.component}"),
                    ),
                )
                return
            }
            NodeRuntimePluginBindCall.Outcome.Bound -> Unit
        }
        var staleConnectionToUnbind: ServiceConnection? = null
        var timeoutToPost: Runnable? = null
        synchronized(entry.lock) {
            binding.registered = true
            if (!entry.matchesBindingLocked(binding.generation, null) || entry.retired) {
                binding.registered = false
                staleConnectionToUnbind = binding.connection
            } else if (entry.connecting && entry.proxy == null) {
                val timeout = Runnable { handleBindTimeout(entry, binding) }
                binding.timeout = timeout
                timeoutToPost = timeout
            }
        }
        postUnbind(entry.appContext, staleConnectionToUnbind)
        timeoutToPost?.let { handler.postDelayed(it, DEFAULT_BIND_TIMEOUT_MS) }
    }

    private fun handleBindTimeout(entry: PooledConnection, binding: BindingAttempt) {
        var connectionToUnbind: ServiceConnection? = null
        var pending = emptyList<CancellableContinuation<AcquiredRuntime>>()
        val matched = synchronized(entry.lock) {
            if (!entry.matchesBindingLocked(binding.generation, binding.binder) ||
                !entry.connecting ||
                entry.proxy != null
            ) {
                false
            } else {
                pending = entry.waiters.toList()
                entry.waiters.clear()
                connectionToUnbind = entry.detachActiveBindingLocked()
                true
            }
        }
        if (!matched) return
        postUnbind(entry.appContext, connectionToUnbind)
        val error = NodeRuntimePluginUnavailableException("bindService timed out: ${entry.component}")
        pending.forEach { waiter ->
            if (!waiter.isCompleted) waiter.resumeWithException(error)
        }
    }

    private fun failWaiters(
        entry: PooledConnection,
        binding: BindingAttempt,
        error: Throwable,
    ) {
        var connectionToUnbind: ServiceConnection? = null
        var pending = emptyList<CancellableContinuation<AcquiredRuntime>>()
        val matched = synchronized(entry.lock) {
            if (!entry.matchesBindingLocked(binding.generation, binding.binder)) {
                false
            } else {
                pending = entry.waiters.toList()
                entry.waiters.clear()
                connectionToUnbind = entry.detachActiveBindingLocked()
                true
            }
        }
        if (!matched) return
        postUnbind(entry.appContext, connectionToUnbind)
        pending.forEach { waiter ->
            if (!waiter.isCompleted) waiter.resumeWithException(error)
        }
    }

    private fun invalidateAcquired(
        acquired: AcquiredRuntime,
        error: Throwable,
        binderFailure: Boolean,
    ) {
        invalidateEntry(
            entry = acquired.entry,
            error = error,
            binderFailure = binderFailure,
            expectedBindingGeneration = acquired.bindingGeneration,
            expectedBinder = acquired.binder,
        )
    }

    private fun invalidateEntry(
        entry: PooledConnection,
        error: Throwable,
        binderFailure: Boolean,
        expectedBindingGeneration: Long? = null,
        expectedBinder: IBinder? = null,
    ): Boolean {
        var pending = emptyList<CancellableContinuation<AcquiredRuntime>>()
        var connectionToUnbind: ServiceConnection? = null
        val matched = synchronized(entry.lock) {
            if (expectedBindingGeneration != null &&
                !entry.matchesBindingLocked(expectedBindingGeneration, expectedBinder)
            ) {
                false
            } else {
                val hadLiveSession = entry.proxy != null || entry.binder != null
                if (binderFailure && hadLiveSession) {
                    entry.binderDeathCount = binderDeathCount.incrementAndGet()
                }
                pending = entry.waiters.toList()
                entry.waiters.clear()
                connectionToUnbind = entry.detachActiveBindingLocked()
                true
            }
        }
        if (!matched) return false
        val unavailable = NodeRuntimePluginUnavailableException(
            "Node.js runtime plugin session disconnected: ${entry.component}: ${error.message}",
            error,
        )
        pending.forEach { waiter ->
            if (!waiter.isCompleted) waiter.resumeWithException(unavailable)
        }
        postUnbind(entry.appContext, connectionToUnbind)
        return true
    }

    private fun postUnbind(context: Context, connection: ServiceConnection?) {
        if (connection == null) return
        handler.post { runCatching { context.unbindService(connection) } }
    }

    private fun retireEntry(entry: PooledConnection, reason: String) {
        synchronized(entry.lock) { entry.retired = true }
        invalidateEntry(
            entry,
            NodeRuntimePluginUnavailableException("Node.js runtime plugin session retired: $reason"),
            binderFailure = false,
        )
    }

    private fun ensureObservers(appContext: Context) {
        synchronized(observerLock) {
            if (observerContext != null) return
            observerContext = appContext
            PluginStateChangeRegistry.addListener(pluginStateChangedListener)
            val packageFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
                addDataScheme("package")
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(packageChangedReceiver, packageFilter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(packageChangedReceiver, packageFilter)
                }
            }.onFailure { error -> Log.w(TAG, "Failed to register package invalidation receiver.", error) }

        }
    }

    private fun invalidateCachedState(packageName: String?, reason: String) {
        synchronized(candidateCacheLock) {
            candidateInvalidationGeneration += 1L
            cachedCandidates = null
        }
        invalidateConnections(packageName, reason)
    }

    private fun invalidateConnections(packageName: String?, reason: String) {
        val retired = synchronized(poolLock) {
            val entries = connectionPool.values
                .filter { packageName == null || it.candidate.serviceInfo.packageName == packageName }
            entries.forEach { connectionPool.remove(it.component) }
            entries
        }
        retired.forEach { retireEntry(it, reason) }
    }

    private fun AcquiredRuntime.toOperationDiagnostics(discovery: DiscoveryResult) = OperationDiagnostics(
        discoveryElapsedMs = discovery.elapsedMs,
        candidateCacheHit = discovery.cacheHit,
        candidateCacheGeneration = discovery.generation,
        bindElapsedMs = bindElapsedMs,
        sessionReused = sessionReused,
        sessionGeneration = sessionGeneration,
        binderDeathCount = binderDeathCount.get(),
        reconnectCount = reconnectCount.get(),
        packageName = entry.candidate.serviceInfo.packageName,
        serviceName = entry.candidate.serviceInfo.name,
    )

    private class PooledConnection(
        val appContext: Context,
        val candidate: Candidate,
    ) {
        val component: ComponentName = candidate.component
        val lock = Any()
        val waiters = ArrayList<CancellableContinuation<AcquiredRuntime>>()
        var proxy: INodeJsRuntimePlugin? = null
        var binder: IBinder? = null
        var runtimeInfo: Bundle? = null
        var connecting = false
        var retired = false
        var everConnected = false
        var sessionGeneration = 0L
        var binderDeathCount = 0L
        var activeBinding: BindingAttempt? = null
        var nextBindingGeneration = 0L

        fun beginBindingLocked(): BindingAttempt {
            check(activeBinding == null) { "A Node.js runtime plugin binding is already active." }
            val binding = BindingAttempt(
                generation = ++nextBindingGeneration,
                startedAt = SystemClock.elapsedRealtime(),
            )
            binding.connection = createConnection(binding)
            activeBinding = binding
            connecting = true
            return binding
        }

        fun matchesBindingLocked(expectedGeneration: Long, expectedBinder: IBinder?): Boolean {
            val current = activeBinding ?: return false
            return NodeRuntimePluginBindingIdentity.matches(
                currentGeneration = current.generation,
                currentBinder = current.binder,
                expectedGeneration = expectedGeneration,
                expectedBinder = expectedBinder,
            )
        }

        fun takeConnectionForUnbindLocked(binding: BindingAttempt): ServiceConnection? {
            if (!binding.registered) return null
            binding.registered = false
            return binding.connection
        }

        fun detachActiveBindingLocked(): ServiceConnection? {
            val binding = activeBinding
            if (binding != null) {
                binding.timeout?.let(handler::removeCallbacks)
                binding.timeout = null
                val activeBinder = binding.binder
                val activeRecipient = binding.deathRecipient
                if (activeBinder != null && activeRecipient != null) {
                    runCatching { activeBinder.unlinkToDeath(activeRecipient, 0) }
                }
                binding.deathRecipient = null
            }
            val connectionToUnbind = binding?.let(::takeConnectionForUnbindLocked)
            activeBinding = null
            proxy = null
            binder = null
            runtimeInfo = null
            connecting = false
            return connectionToUnbind
        }

        private fun createConnection(binding: BindingAttempt): ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                var staleConnectionToUnbind: ServiceConnection? = null
                val canLink = synchronized(lock) {
                    binding.registered = true
                    if (retired || !matchesBindingLocked(binding.generation, null)) {
                        staleConnectionToUnbind = takeConnectionForUnbindLocked(binding)
                        false
                    } else {
                        // Publish the Binder identity before linkToDeath so an immediate death
                        // callback can invalidate this exact generation without touching a successor.
                        binding.binder = service
                        true
                    }
                }
                if (!canLink) {
                    postUnbind(appContext, staleConnectionToUnbind)
                    return
                }

                val runtime = INodeJsRuntimePlugin.Stub.asInterface(service)
                val recipient = IBinder.DeathRecipient {
                    invalidateEntry(
                        this@PooledConnection,
                        NodeRuntimePluginUnavailableException("Binder died: $name"),
                        binderFailure = true,
                        expectedBindingGeneration = binding.generation,
                        expectedBinder = service,
                    )
                }
                try {
                    service.linkToDeath(recipient, 0)
                } catch (error: RemoteException) {
                    val invalidated = invalidateEntry(
                        entry = this@PooledConnection,
                        error = error,
                        binderFailure = true,
                        expectedBindingGeneration = binding.generation,
                        expectedBinder = service,
                    )
                    if (!invalidated) {
                        val connection = synchronized(lock) { takeConnectionForUnbindLocked(binding) }
                        postUnbind(appContext, connection)
                    }
                    return
                }

                var pending = emptyList<CancellableContinuation<AcquiredRuntime>>()
                var acquired: AcquiredRuntime? = null
                staleConnectionToUnbind = null
                synchronized(lock) {
                    if (retired || !matchesBindingLocked(binding.generation, service)) {
                        runCatching { service.unlinkToDeath(recipient, 0) }
                        staleConnectionToUnbind = takeConnectionForUnbindLocked(binding)
                    } else {
                        binding.deathRecipient = recipient
                        binder = service
                        proxy = runtime
                        connecting = false
                        binding.timeout?.let(handler::removeCallbacks)
                        binding.timeout = null
                        val reconnect = everConnected
                        if (reconnect) reconnectCount.incrementAndGet()
                        everConnected = true
                        sessionGeneration = NodeJsRuntimePluginHost.sessionGeneration.incrementAndGet()
                        val bindElapsedMs = elapsedSince(binding.startedAt)
                        acquired = AcquiredRuntime(
                            runtime = runtime,
                            entry = this@PooledConnection,
                            binder = service,
                            bindingGeneration = binding.generation,
                            bindElapsedMs = bindElapsedMs,
                            sessionReused = false,
                            sessionGeneration = sessionGeneration,
                        )
                        pending = waiters.toList()
                        waiters.clear()
                    }
                }
                postUnbind(appContext, staleConnectionToUnbind)
                val connected = acquired ?: return
                pending.forEach { waiter ->
                    if (!waiter.isCompleted) waiter.resume(connected)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                val expectedBinder = synchronized(lock) { binding.binder }
                invalidateEntry(
                    this@PooledConnection,
                    NodeRuntimePluginUnavailableException("Service disconnected: $name"),
                    binderFailure = true,
                    expectedBindingGeneration = binding.generation,
                    expectedBinder = expectedBinder,
                )
            }

            override fun onBindingDied(name: ComponentName) {
                val expectedBinder = synchronized(lock) { binding.binder }
                invalidateEntry(
                    this@PooledConnection,
                    NodeRuntimePluginUnavailableException("Service binding died: $name"),
                    binderFailure = true,
                    expectedBindingGeneration = binding.generation,
                    expectedBinder = expectedBinder,
                )
            }

            override fun onNullBinding(name: ComponentName) {
                invalidateEntry(
                    this@PooledConnection,
                    NodeRuntimePluginUnavailableException("Service returned null binding: $name"),
                    binderFailure = false,
                    expectedBindingGeneration = binding.generation,
                )
            }
        }
    }

    private fun elapsedSince(startedAt: Long): Long =
        (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)

    private val REQUIRED_CAPABILITIES = setOf(
        NodeJsRuntimeContract.CAPABILITY_SYNC_SCRIPT_EXECUTION,
        NodeJsRuntimeContract.CAPABILITY_BUNDLE_TRANSPORT,
        NodeJsRuntimeContract.CAPABILITY_NATIVE_EMBEDDED_RUNTIME,
        NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER,
        NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE,
        NodeJsRuntimeContract.CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER,
        NodeJsRuntimeContract.CAPABILITY_SCOPED_WORKSPACE_ARCHIVE_TRANSPORT,
    )

    /**
     * `process_restart` restarts the plugin process outright; the cooperative
     * variant first asks the runtime to stop itself and only restarts the
     * process when that does not release the execution in time. Both terminate
     * an in-flight execution, which is all the host depends on.
     */
    private val SUPPORTED_CANCELLATION_MODES = setOf(
        "process_restart",
        "cooperative_stop_with_process_restart_fallback",
    )

    private val AUTHORIZED_STATES = setOf(
        PluginAuthorizedState.OFFICIAL,
        PluginAuthorizedState.TRUSTED,
        PluginAuthorizedState.USER_GRANTED,
    )
}

class NodeRuntimePluginUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class NodeRuntimePluginContractException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class NodeRuntimePluginOperationException(
    message: String,
    cause: Throwable? = null,
    val candidate: NodeJsRuntimePluginHost.Candidate?,
    val diagnostics: NodeJsRuntimePluginHost.OperationDiagnostics?,
    val dispatchStarted: Boolean,
) : IllegalStateException(message, cause)
