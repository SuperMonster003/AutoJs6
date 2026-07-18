package org.autojs.autojs.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.util.Log
import androidx.core.app.NotificationCompat
import org.autojs.autojs.App
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.ForegroundServiceCreator
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.ForegroundServiceUtils
import org.autojs.autojs.util.ForegroundServiceUtils.FOREGROUND_SERVICE_TYPE_UNKNOWN
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import kotlin.system.exitProcess

private fun File.deleteRecursivelyIfExists(): Boolean {
    if (!exists()) {
        return true
    }
    return runCatching {
        deleteRecursively()
    }.getOrElse {
        !exists()
    }
}

open class NativeNodeEmbeddedScriptService : Service() {

    @Volatile
    private var exitScheduled = false
    @Volatile
    private var activeLifecycle: NodeEmbeddedRuntimeLifecycle? = null
    @Volatile
    private var activeCrashMarker: NodeCrashEvidenceStore.Marker? = null
    @Volatile
    private var activeExecutionId: String? = null
    @Volatile
    private var activeExecutionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT
    @Volatile
    private var activeSourceName: String = DEFAULT_SOURCE_NAME
    @Volatile
    private var activeWorkingDirectory: String? = null
    @Volatile
    private var activeStartedAtEpochMs: Long = 0L
    @Volatile
    private var activeHeartbeatMonitor: NodeInteractiveHeartbeatMonitor? = null
    @Volatile
    private var activeLifecycleStopRequestFile: File? = null
    @Volatile
    private var prewarmActive = false
    @Volatile
    private var prewarmStartedAtElapsedMs = 0L
    @Volatile
    private var prewarmStartId = 0
    @Volatile
    private var foregroundActive = false
    @Volatile
    private var retireAfterExecutionReason: String? = null
    private var foregroundServiceCreator: ForegroundServiceCreator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var prewarmStopRunnable: Runnable? = null
    private val runtimeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoJs6-Node-Runtime-${javaClass.simpleName}").apply { isDaemon = true }
    }
    private val runtimeControlValues = AtomicReference<Map<String, String>>(emptyMap())
    private val terminalShutdownStarted = AtomicBoolean(false)
    private val serviceExecutionLock = Object()
    private var activeServiceExecutionRequests = 0
    private var terminalShutdownFuture: Future<*>? = null
    @Volatile
    private var pendingInteractiveExitRunnable: Runnable? = null
    @Volatile
    private var interactiveStopRequestedExecutionId: String? = null

    private val processControlBinder = object : INodeEmbeddedProcessControl.Stub() {
        override fun ensureRuntimeReady(persistentEnabled: Boolean): Bundle {
            retireAfterExecutionReason?.takeIf(String::isNotBlank)?.let { reason ->
                return runtimeStatusBundle(
                    runtimeControlValues.get() + mapOf(
                        "process_runtime.state" to "draining",
                        "process_runtime.healthy" to "false",
                        "process_runtime.ensure.status" to "rejected",
                        "process_runtime.ensure.detail" to "service retirement requested: $reason",
                    ),
                )
            }
            return runRuntimeControl("ensure_runtime_ready") {
                val enabled = persistentEnabled && NodeEmbeddedProcessSessionManager.isEnabled()
                val persistentValues = NativeNodeEmbeddedRuntimeBridge.setProcessRuntimePersistentEnabled(
                    this@NativeNodeEmbeddedScriptService,
                    enabled,
                )
                val readyValues = if (enabled) {
                    NativeNodeEmbeddedRuntimeBridge.ensureProcessRuntimeReady(this@NativeNodeEmbeddedScriptService)
                } else {
                    NativeNodeEmbeddedRuntimeBridge.processRuntimeDiagnostics(this@NativeNodeEmbeddedScriptService)
                }
                val values = mergeProcessRuntimeControlValues(persistentValues, readyValues)
                runtimeControlValues.set(values)
                runtimeStatusBundle(values)
            }
        }

        override fun getRuntimeStatus(): Bundle = runtimeStatusBundle(runtimeControlValues.get())

        override fun requestShutdown(reason: String?) {
            requestPersistentShutdown(
                reason = reason.orEmpty().ifBlank { "binder_request" },
                awaitTerminalShutdown = true,
            )
        }
    }

    private val foregroundServiceType = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else -> FOREGROUND_SERVICE_TYPE_UNKNOWN
    }

    override fun onBind(intent: Intent?): IBinder? {
        return processControlBinder.takeIf { NodeEmbeddedProcessSessionManager.isEnabled() }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(
            TAG,
            "android.policy.embedded_service " +
                    NodeAndroidRuntimePolicy.embeddedServiceDiagnostics(
                        this,
                        javaClass,
                        foregroundServiceType,
                    ),
        )
        startScriptForeground()
        Log.i(TAG, "PROCESS STARTED processName=${currentProcessName()} pid=${Process.myPid()}")
    }

    override fun onDestroy() {
        val interactiveExecutionId = activeExecutionId
            ?.takeIf { activeExecutionMode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING }
        clearPrewarmState("service.onDestroy")
        cancelAllQueuedExecutionsInService()
        activeLifecycle?.let { lifecycle ->
            if (lifecycle.state !in setOf(
                    NodeEmbeddedRuntimeState.STOPPED,
                    NodeEmbeddedRuntimeState.STOPPING,
                    NodeEmbeddedRuntimeState.DESTROYED,
                )
            ) {
                lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "service.onDestroy")
            }
            lifecycle.transition(NodeEmbeddedRuntimeState.DESTROYED, "service.onDestroy")
        }
        val terminalNativeValues = activeHeartbeatMonitor?.nativeValuesSnapshot("destroyed")
            ?: activeLifecycle?.nativeValues().orEmpty()
        NodeCrashEvidenceStore.markServiceDestroy(
            context = this,
            marker = activeCrashMarker,
            reason = "embedded_script_service.onDestroy",
            nativeValues = terminalNativeValues,
        )
        activeCrashMarker = null
        activeHeartbeatMonitor?.stop("service.onDestroy")
        activeHeartbeatMonitor = null
        interactiveExecutionId?.let { executionId ->
            val currentStatus = NodeInteractiveLongRunningStatusStore.read(this, executionId)
            val currentState = currentStatus?.optString("state").orEmpty()
            if (currentState !in setOf(
                    NodeInteractiveLongRunningStatusStore.STATE_STOPPED,
                    NodeInteractiveLongRunningStatusStore.STATE_TIMED_OUT,
                    NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                )
            ) {
                NodeInteractiveLongRunningStatusStore.write(
                    context = this,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_STOPPED,
                    sourceName = activeSourceName,
                    workingDirectory = activeWorkingDirectory,
                    startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L },
                    stopRequested = true,
                    message = "service.onDestroy",
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("destroyed", terminalNativeValues),
                    nativeValues = terminalNativeValues,
                )
            }
        }
        activeLifecycle = null
        clearActiveInteractiveSession(interactiveExecutionId)
        foregroundServiceCreator?.stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundActive = false
        foregroundServiceCreator = null
        pendingInteractiveExitRunnable?.let(mainHandler::removeCallbacks)
        pendingInteractiveExitRunnable = null
        interactiveStopRequestedExecutionId = null
        runtimeExecutor.shutdownNow()
        scheduleProcessExit(0)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureScriptForeground()
        return when (intent?.action) {
            ACTION_PREWARM_RUNTIME -> {
                runtimeExecutor.execute { handlePrewarmRuntime(intent, startId) }
                START_NOT_STICKY
            }
            ACTION_RUN_SCRIPT -> {
                if (!beginServiceExecutionRequest()) {
                    rejectRetiringScriptRequest(intent, startId)
                } else {
                    Thread {
                        try {
                            runScript(intent, startId)
                        } finally {
                            finishServiceExecutionRequest()
                        }
                    }.apply {
                        name = "NativeNodeEmbeddedScriptService-$startId"
                        start()
                    }
                }
                START_NOT_STICKY
            }
            ACTION_STOP_INTERACTIVE -> {
                stopInteractiveLongRunning(intent, startId)
                START_NOT_STICKY
            }
            ACTION_SIMULATE_FOREGROUND_TIMEOUT -> {
                handleForegroundServiceTimeout(
                    startId = startId,
                    foregroundServiceType = intent.getIntExtra(EXTRA_FOREGROUND_SERVICE_TYPE, foregroundServiceType),
                    source = "test.simulated",
                )
                START_NOT_STICKY
            }
            else -> {
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val executionId = activeExecutionId
            ?.takeIf { activeExecutionMode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING }
        if (executionId != null) {
            Log.i(
                TAG,
                "interactive.task_removed executionId=$executionId " +
                        "processName=${currentProcessName()} pid=${Process.myPid()}",
            )
            stopActiveInteractiveLongRunning(
                requestedExecutionId = executionId,
                startId = null,
                ignoredReason = "interactive.task_removed.ignored",
                transitionReason = "interactive.task_removed",
                stopRequestReason = "interactive.task_removed.requested",
                stoppingMessage = "interactive.task_removed.requested",
                stoppedMessage = "interactive.task_removed.accepted",
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int) {
        handleForegroundServiceTimeout(
            startId = startId,
            foregroundServiceType = foregroundServiceType,
            source = "system.onTimeout",
        )
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundServiceTimeout(
            startId = startId,
            foregroundServiceType = fgsType,
            source = "system.onTimeout",
        )
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (NodeEmbeddedProcessSessionManager.isMemoryPressureTrimLevel(level)) {
            // In persistent mode the main-process session manager receives the same
            // memory-pressure signal and owns the binding/stop/exit handshake. If
            // this remote service retires itself independently, the manager can race
            // a new acquire against a still-draining process and quarantine a healthy
            // slot. Legacy one-shot/prewarm mode has no manager, so keep its local
            // cleanup behavior.
            if (NodeEmbeddedProcessSessionManager.isEnabled()) {
                return
            }
            clearPrewarmState("low_memory_$level")
            requestPersistentShutdown("trim_memory_$level")
        }
    }

    private fun beginServiceExecutionRequest(): Boolean = synchronized(serviceExecutionLock) {
        if (!retireAfterExecutionReason.isNullOrBlank()) {
            false
        } else {
            activeServiceExecutionRequests += 1
            true
        }
    }

    private fun finishServiceExecutionRequest() {
        val retirementReason = synchronized(serviceExecutionLock) {
            activeServiceExecutionRequests = (activeServiceExecutionRequests - 1).coerceAtLeast(0)
            serviceExecutionLock.notifyAll()
            retireAfterExecutionReason?.takeIf { activeServiceExecutionRequests == 0 && it.isNotBlank() }
        }
        retirementReason?.let { reason ->
            if (NodeEmbeddedProcessSessionManager.isEnabled()) {
                ensureTerminalShutdownQueued(reason)
            } else {
                mainHandler.post {
                    stopSelf()
                    scheduleProcessExit(0)
                }
            }
        }
    }

    private fun rejectRetiringScriptRequest(intent: Intent, startId: Int) {
        val reason = retireAfterExecutionReason.orEmpty().ifBlank { "service_retiring" }
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        receiver?.send(
            RESULT_SCRIPT,
            requestTransportFailureResult(
                intent = intent,
                throwable = IllegalStateException("Embedded Node service is retiring: $reason"),
                startedAt = startedAt,
            ).let { failure ->
                failure.copy(
                    nativeValues = linkedMapOf<String, String>().apply {
                        putAll(failure.nativeValues)
                        put("embedded_script.native_execution_entered", "false")
                        put("embedded_script.service.retirement_requested", "true")
                        put("embedded_script.service.retirement_reason", reason)
                    },
                )
            }.toBundle(),
        )
        finishStartedServiceWhenIdle(startId, "retiring_request_rejected")
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withoutNativeExecution():
        NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult =
        copy(
            nativeValues = linkedMapOf<String, String>().apply {
                putAll(runtimeControlValues.get())
                putAll(nativeValues)
                put("embedded_script.native_execution_entered", "false")
                put("embedded_script.service.cleanup_clean", "true")
                put("embedded_script.service.cleanup_errors", "")
                put("embedded_script.service.result_delivery_phase", "pre_native_rejection")
                put("embedded_script.service.execution_slot_released", "true")
                put("embedded_script.service.queue_idle_after_release", queueStatusSnapshot().idle.toString())
                put("embedded_script.service.retirement_requested", "false")
            },
        )

    private fun runScript(intent: Intent, startId: Int) {
        val prewarmLease = consumePrewarmForExecution()
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val requestSpoolStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val serviceRequest = try {
            intent.embeddedScriptRequestExtra(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Native Node embedded script request transport failed.", e)
            receiver?.send(
                RESULT_SCRIPT,
                requestTransportFailureResult(
                    intent = intent,
                    throwable = e,
                    startedAt = startedAt,
                ).withPrewarmLease(prewarmLease).withoutNativeExecution().toBundle(),
            )
            stopServiceIfQueueIdle(startId)
            return
        }
        val requestSpoolPhaseMs = NodeEmbeddedProcessSessionManager.elapsedPhaseMs(requestSpoolStartedAtNanos)
        val servicePreparationStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
        val preparedServiceRequest = try {
            NativeNodeEmbeddedRuntimeBridge.prepareEmbeddedScriptRequest(serviceRequest)
        } catch (e: Throwable) {
            receiver?.send(
                RESULT_SCRIPT,
                NativeNodeEmbeddedRuntimeBridge
                    .embeddedScriptRequestPreparationFailure(serviceRequest, e, startedAt)
                    .withPrewarmLease(prewarmLease)
                    .withoutNativeExecution()
                    .toBundle(),
            )
            stopServiceIfQueueIdle(startId)
            return
        }
        val launchSurface = intent.getStringExtra(EXTRA_LAUNCH_SURFACE)
            ?.takeIf { it.isNotBlank() }
            ?: NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT
        NativeNodeEmbeddedRuntimeBridge.validateEmbeddedScriptRequestBeforeServiceStart(
            request = preparedServiceRequest,
            launchSurface = launchSurface,
            context = this,
        )?.let { failure ->
            receiver?.send(
                RESULT_SCRIPT,
                failure.withPrewarmLease(prewarmLease).withoutNativeExecution().toBundle(),
            )
            stopServiceIfQueueIdle(startId)
            return
        }
        val source = preparedServiceRequest.source
        val sourceName = preparedServiceRequest.sourceName.ifBlank { DEFAULT_SOURCE_NAME }
        val workingDirectory = preparedServiceRequest.workingDirectory?.takeIf { it.isNotBlank() }
        val executionMode = NodeExecutionModePolicy.normalize(preparedServiceRequest.executionMode)
        val timeoutMs = preparedServiceRequest.timeoutMs.coerceAtLeast(1L)
        val esmExperimentalEnabled = preparedServiceRequest.esmExperimentalEnabled
        val dynamicImportLocalEnabled = BuildConfig.NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED
        val dynamicImportExperimentalEnabled = preparedServiceRequest.dynamicImportExperimentalEnabled
        val rawNodeNetworkModulesExperimentalEnabled = preparedServiceRequest.rawNodeNetworkModulesExperimentalEnabled
        val workerThreadsExperimentalEnabled = preparedServiceRequest.workerThreadsExperimentalEnabled
        val childProcessExperimentalEnabled = preparedServiceRequest.childProcessExperimentalEnabled
        val javaInteropExperimentalEnabled = preparedServiceRequest.javaInteropExperimentalEnabled
        val spawnDepth = preparedServiceRequest.spawnDepth.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH)
        val env = preparedServiceRequest.env
        val moduleSources = preparedServiceRequest.moduleSources
        val runtimeModuleSources = preparedServiceRequest.runtimeModuleSources
        val bridgeLimits = NodeBridgeLimits.fromRuntimeModuleOrWorkingDirectory(
            runtimeModuleSources[NodeBridgeLimits.RUNTIME_MODULE_NAME],
            workingDirectory,
        )
        val runtimeBudget = NodeRuntimeBudgetPolicy.fromRuntimeModuleOrWorkingDirectory(
            runtimeModuleSources[NodeRuntimeBudgetPolicy.RUNTIME_MODULE_NAME],
            workingDirectory,
            executionMode,
            timeoutMs,
            bridgeLimits,
        )
        val executionId = preparedServiceRequest.executionId?.takeIf { it.isNotBlank() }
            ?: newExecutionId()
        val processPoolPlan = NodeEmbeddedProcessPoolPolicy.plan(
            context = this,
            executionMode = executionMode,
            launchSurface = launchSurface,
            currentServiceClassName = javaClass.name,
            requestedSlotId = preparedServiceRequest.processPoolSlot,
            slotSelectionSource = preparedServiceRequest.processPoolSlotSource,
            defaultSchedulerEnabled = preparedServiceRequest.processPoolSlotSource ==
                    NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER,
        )
        val interactiveSession = executionMode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING &&
            NodeExecutionModePolicy.isInteractiveLongRunningLaunchSurface(launchSurface)
        if (interactiveSession) {
            activeExecutionId = executionId
            activeExecutionMode = executionMode
            activeSourceName = sourceName
            activeWorkingDirectory = workingDirectory
            activeStartedAtEpochMs = System.currentTimeMillis()
            NodeInteractiveLongRunningStatusStore.write(
                context = this,
                executionId = executionId,
                state = NodeInteractiveLongRunningStatusStore.STATE_STARTING,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                startedAtEpochMs = activeStartedAtEpochMs,
                launchSurface = launchSurface,
                activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("starting"),
            )
            updateInteractiveForeground(executionId, sourceName)
        }
        val processName = currentProcessName()
        val pid = Process.myPid()
        val queueMode = NodeExecutionQueuePolicy.normalizeMode(preparedServiceRequest.queueMode)
        val queueTimeoutMs = NodeExecutionQueuePolicy.normalizedQueueTimeoutMs(
            queueMode,
            preparedServiceRequest.queueTimeoutMs,
        )
        val slotDecision = acquireExecutionSlot(
            executionId = executionId,
            queueMode = queueMode,
            queueTimeoutMs = queueTimeoutMs,
        )
        if (slotDecision is ExecutionSlotDecision.Rejected) {
            if (interactiveSession) {
                NodeInteractiveLongRunningStatusStore.write(
                    context = this,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = sourceName,
                    workingDirectory = workingDirectory,
                    startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L },
                    launchSurface = launchSurface,
                    message = slotDecision.failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("queue_rejected"),
                    nativeValues = processPoolPlan.nativeValues() + mapOf(
                        "embedded_script.error_code" to slotDecision.failure.errorCode,
                        "embedded_script.queue_rejected" to "true",
                    ),
                )
            }
            receiver?.send(
                RESULT_SCRIPT,
                queueFailureResult(
                    failure = slotDecision.failure,
                    executionId = executionId,
                    sourceName = sourceName,
                    workingDirectory = workingDirectory,
                    startedAt = startedAt,
                    processName = processName,
                    pid = pid,
                )
                    .withProcessPoolPlan(processPoolPlan)
                    .withPrewarmLease(prewarmLease)
                    .withoutNativeExecution()
                    .toBundle(),
            )
            if (interactiveSession) {
                clearActiveInteractiveSession(executionId)
            }
            stopServiceIfQueueIdle(startId)
            return
        }
        val slotLease = (slotDecision as ExecutionSlotDecision.Acquired).lease
        val engineInfo = NodeBridgeEngineInfo.fromContext(
            context = this,
            id = executionId,
            sourceName = sourceName,
            cwd = workingDirectory.orEmpty(),
            executionMode = executionMode,
            launchSurface = launchSurface,
            spawnDepth = spawnDepth,
            processName = processName,
            pid = pid,
            startedAt = System.currentTimeMillis(),
        )
        val lifecycleStopRequestFile = if (interactiveSession) {
            File(cacheDir, "embedded-node-lifecycle/$executionId/stop-request.json")
        } else {
            null
        }
        if (interactiveSession) {
            lifecycleStopRequestFile?.parentFile?.deleteRecursivelyIfExists()
            lifecycleStopRequestFile?.parentFile?.mkdirs()
            activeLifecycleStopRequestFile = lifecycleStopRequestFile
        }
        val bridgeSession = NodeBridgeFileTransportSession(
            root = File(cacheDir, "embedded-node-bridge/$executionId"),
            bridgeLimits = bridgeLimits,
            provider = AndroidNodeBridgeCapabilityProvider(
                this,
                engineInfo,
                javaInteropExperimentalEnabled = javaInteropExperimentalEnabled,
                bridgeLimits = bridgeLimits,
            ),
        )
        val moduleSourceSession = preparedServiceRequest.moduleSourceProvider?.let { provider ->
            NodeModuleSourceFileTransportSession(
                cacheDirectory = cacheDir,
                executionId = executionId,
                provider = provider,
                timeoutMs = timeoutMs,
            )
        }
        val enrichedRuntimeModuleSources = runtimeModuleSources
            .withHostAppInfo()
            .withDeviceInfo()
            .withEngineInfo(engineInfo.toJson())
            .withBridgePermissionManifest(workingDirectory, preparedServiceRequest.sandboxRoot)
            .withBridgeLimitPolicy(bridgeLimits)
            .withBridgeLiveConfig(bridgeSession.configJson())
            .withModuleSourceProviderConfig(moduleSourceSession?.configJson())
            .withLifecycleConfig(
                NodeLifecycleCheckpointStore.runtimeModuleSource(
                    context = this,
                    engineInfo = engineInfo,
                    executionMode = executionMode,
                    launchSurface = launchSurface,
                    stopRequestFile = lifecycleStopRequestFile,
                ),
            )
        val request = NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
            source = source,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            sandboxRoot = serviceRequest.sandboxRoot,
            executionMode = executionMode,
            timeoutMs = timeoutMs,
            captureStdout = serviceRequest.captureStdout,
            captureStderr = serviceRequest.captureStderr,
            env = env,
            moduleSources = moduleSources,
            runtimeModuleSources = enrichedRuntimeModuleSources,
            esmExperimentalEnabled = esmExperimentalEnabled,
            dynamicImportExperimentalEnabled = dynamicImportExperimentalEnabled,
            rawNodeNetworkModulesExperimentalEnabled = rawNodeNetworkModulesExperimentalEnabled,
            workerThreadsExperimentalEnabled = workerThreadsExperimentalEnabled,
            childProcessExperimentalEnabled = childProcessExperimentalEnabled,
            javaInteropExperimentalEnabled = javaInteropExperimentalEnabled,
            executionId = executionId,
            queueMode = queueMode,
            queueTimeoutMs = queueTimeoutMs,
            runtimeAdapter = preparedServiceRequest.runtimeAdapter,
            processPoolSlot = preparedServiceRequest.processPoolSlot,
            processPoolSlotSource = preparedServiceRequest.processPoolSlotSource,
        )
        val lifecycle = NodeEmbeddedRuntimeLifecycle(TAG, executionId, processName, pid, sourceName)
        val crashMarker = NodeCrashEvidenceStore.begin(
            context = this,
            executionId = executionId,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            executionMode = executionMode,
            launchSurface = launchSurface,
            timeoutMs = timeoutMs,
            runtimeAdapter = request.runtimeAdapter,
            runtimeSlotId = processPoolPlan.selectedSlot.id,
            scenario = "embedded_script_service",
        )
        activeCrashMarker = crashMarker
        val timeline = NodeEmbeddedRuntimeTimeline.create(
            env = env,
            executionId = executionId,
            processName = processName,
            pid = pid,
            sourceName = sourceName,
            startedAtElapsedMs = startedAt,
        )
        timeline?.mark(
            "service_start",
            mapOf(
                "startId" to startId,
                "workingDirectory" to workingDirectory.orEmpty(),
                "executionMode" to executionMode,
                "timeoutMs" to timeoutMs,
                "moduleSourceCount" to moduleSources.size,
                "runtimeModuleSourceCount" to enrichedRuntimeModuleSources.size,
            ),
        )
        if (slotLease.queued) {
            timeline?.mark(
                "queue_dequeue",
                mapOf(
                    "queueWaitMs" to slotLease.queueWaitMs,
                    "queuePosition" to slotLease.queuePosition,
                ),
            )
        }
        activeLifecycle = lifecycle
        lifecycle.transition(NodeEmbeddedRuntimeState.STARTING, "service.runScript")
        Log.i(
            TAG,
                    "script.enter executionId=$executionId processName=$processName pid=$pid sourceName=$sourceName " +
                    "env=${env.size} moduleSources=${moduleSources.size} runtimeModuleSources=${enrichedRuntimeModuleSources.size} " +
                    "sandboxRoot=${request.sandboxRoot.orEmpty()} " +
                    "esmExperimentalEnabled=$esmExperimentalEnabled " +
                    "dynamicImportLocalEnabled=$dynamicImportLocalEnabled " +
                    "dynamicImportExperimentalEnabled=$dynamicImportExperimentalEnabled " +
                    "rawNodeNetworkModulesExperimentalEnabled=$rawNodeNetworkModulesExperimentalEnabled " +
                    "workerThreadsExperimentalEnabled=$workerThreadsExperimentalEnabled " +
                    "childProcessExperimentalEnabled=$childProcessExperimentalEnabled " +
                    "javaInteropExperimentalEnabled=$javaInteropExperimentalEnabled " +
                    "queueMode=$queueMode queued=${slotLease.queued} queueWaitMs=${slotLease.queueWaitMs} " +
                    "executionMode=$executionMode " +
                    "processPool=${processPoolPlan.mode}/${processPoolPlan.selectedSlot.id} " +
                    "bridgeLimits=${bridgeLimits.toJson()} " +
                    "runtimeBudget=${runtimeBudget.toJsonObject()} " +
                    "runtimeFirst=${enrichedRuntimeModuleSources.keys.firstOrNull().orEmpty()}",
        )
        var deliveryResult: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult? = null
        var nativeExecutionEntered = false
        var servicePhaseMs = 0L
        var nativePhaseMs = 0L
        try {
            timeline?.mark("bridge_transport_start", mapOf("transport" to "file"))
            bridgeSession.start()
            moduleSourceSession?.start()
            timeline?.mark("bridge_transport_ready", mapOf("transport" to "file"))
            lifecycle.transition(NodeEmbeddedRuntimeState.RUNNING, "native.enter")
            val heartbeatMonitor = if (interactiveSession) {
                NodeInteractiveHeartbeatMonitor(
                    context = this,
                    executionId = executionId,
                    sourceName = sourceName,
                    workingDirectory = workingDirectory,
                    startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    lifecycle = lifecycle,
                    runtimeBudget = runtimeBudget,
                    bridgeSession = bridgeSession,
                ).also { monitor ->
                    activeHeartbeatMonitor = monitor
                    monitor.start()
                }
            } else {
                null
            }
            if (interactiveSession) {
                val runningNativeValues = heartbeatMonitor?.nativeValuesSnapshot("running")
                    ?: (lifecycle.nativeValues() + runtimeBudget.nativeDiagnostics())
                NodeInteractiveLongRunningStatusStore.write(
                    context = this,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_RUNNING,
                    sourceName = sourceName,
                    workingDirectory = workingDirectory,
                    startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L },
                    launchSurface = launchSurface,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("running", runningNativeValues),
                    nativeValues = runningNativeValues,
                )
            }
            timeline?.mark("native_enter")
            nativeExecutionEntered = true
            servicePhaseMs = (
                NodeEmbeddedProcessSessionManager.elapsedPhaseMs(servicePreparationStartedAtNanos) -
                    slotLease.queueWaitMs
                ).coerceAtLeast(0L)
            val nativeStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
            val nativeResult = try {
                runtimeExecutor.submit<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult> {
                    val processRuntimeValues = ensureServiceRuntimeReady()
                    val executionResult = NativeNodeEmbeddedRuntimeBridge.runEmbeddedScriptInCurrentProcess(
                        context = this,
                        request = request,
                    )
                    val finalRuntimeValues = NativeNodeEmbeddedRuntimeBridge.processRuntimeDiagnostics(
                        this@NativeNodeEmbeddedScriptService,
                    )
                    runtimeControlValues.set(finalRuntimeValues)
                    executionResult.copy(
                        nativeValues = linkedMapOf<String, String>().apply {
                            putAll(processRuntimeValues)
                            putAll(executionResult.nativeValues)
                            putAll(finalRuntimeValues)
                            put("embedded_script.native_execution_entered", "true")
                        },
                    )
                }.get()
            } finally {
                nativePhaseMs = NodeEmbeddedProcessSessionManager.elapsedPhaseMs(nativeStartedAtNanos)
            }
            timeline?.recordNativeResult(nativeResult)
            // Provider diagnostics are collected once by the host after result delivery. The
            // service adds only its final transport counters during cleanup, avoiding a reverse
            // Binder round trip on the execution hot path.
            val result = dispatchQueuedBridgeRequests(bridgeSession.appendNativeValues(nativeResult))
                .withExecutionMode(executionMode, launchSurface)
                .withRuntimeBudget(runtimeBudget)
                .withProcessPoolPlan(processPoolPlan)
            timeline?.recordBridge(result)
            val completed = when {
                result.succeeded -> {
                    timeline?.mark("stop_requested", mapOf("reason" to "script.success"))
                    lifecycle.transition(NodeEmbeddedRuntimeState.DRAINING, "script.success")
                    lifecycle.transition(NodeEmbeddedRuntimeState.STOPPED, "script.success")
                    timeline?.mark("drain_complete", mapOf("state" to lifecycle.state.name))
                    result
                }
                result.timedOut -> {
                    timeline?.mark("stop_requested", mapOf("reason" to "script.timeout"))
                    lifecycle.transition(NodeEmbeddedRuntimeState.TIMED_OUT, "script.timeout")
                    lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "script.timeout")
                    result
                }
                else -> {
                    timeline?.mark("stop_requested", mapOf("reason" to "script.failure"))
                    lifecycle.transition(NodeEmbeddedRuntimeState.FAILED, "script.failure")
                    lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "script.failure")
                    result
                }
            }
            if (interactiveSession) {
                val state = when {
                    completed.succeeded -> NodeInteractiveLongRunningStatusStore.STATE_STOPPED
                    completed.timedOut -> NodeInteractiveLongRunningStatusStore.STATE_TIMED_OUT
                    else -> NodeInteractiveLongRunningStatusStore.STATE_FAILED
                }
                NodeInteractiveLongRunningStatusStore.write(
                    context = this,
                    executionId = executionId,
                    state = state,
                    sourceName = sourceName,
                    workingDirectory = workingDirectory,
                    startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L },
                    launchSurface = launchSurface,
                    message = completed.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary(state, completed.nativeValues),
                    nativeValues = lifecycle.nativeValues() + completed.nativeValues,
                )
            }
            timeline?.mark("destroy_complete", mapOf("state" to lifecycle.state.name))
            val evidenceResult = completed
                .withLifecycle(lifecycle)
                .withQueueLease(slotLease)
                .withPrewarmLease(prewarmLease)
                .withCrashEvidence(crashMarker)
                .withTimeline(timeline)
            deliveryResult = evidenceResult
        } catch (e: Throwable) {
            timeline?.mark(
                "stop_requested",
                mapOf(
                    "reason" to "service.exception",
                    "error" to (e.message ?: e.javaClass.name),
                ),
            )
            lifecycle.transition(NodeEmbeddedRuntimeState.FAILED, "service.exception")
            lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "service.exception")
            Log.e(TAG, "Native Node embedded script service failed.", e)
            timeline?.mark("destroy_complete", mapOf("state" to lifecycle.state.name))
            if (interactiveSession) {
                NodeInteractiveLongRunningStatusStore.write(
                    context = this,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = sourceName,
                    workingDirectory = workingDirectory,
                    startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L },
                    launchSurface = launchSurface,
                    message = e.message ?: e.javaClass.name,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("exception"),
                    nativeValues = lifecycle.nativeValues(),
                )
            }
            val failureRuntimeValues = if (nativeExecutionEntered) {
                runCatching {
                    runtimeExecutor.submit<Map<String, String>> {
                        NativeNodeEmbeddedRuntimeBridge.processRuntimeDiagnostics(
                            this@NativeNodeEmbeddedScriptService,
                        )
                    }.get(RUNTIME_CONTROL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }.getOrElse { diagnosticsError ->
                    mapOf(
                        "process_runtime.state" to "unavailable",
                        "process_runtime.healthy" to "false",
                        "process_runtime.poisoned" to "true",
                        "process_runtime.teardown_clean" to "false",
                        "process_runtime.diagnostics_error" to
                                (diagnosticsError.message ?: diagnosticsError.javaClass.name),
                    )
                }
            } else {
                emptyMap()
            }
            val failureResult = NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
                succeeded = false,
                exitCode = 1,
                resultText = null,
                stdout = "",
                stderr = "",
                errorName = e.javaClass.simpleName,
                errorMessage = e.message ?: e.javaClass.name,
                errorStack = e.stackTraceToString(),
                elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt,
                processName = processName,
                pid = pid,
                sourceName = sourceName,
                nativeValues = failureRuntimeValues + mapOf(
                    "embedded_script.native_execution_entered" to nativeExecutionEntered.toString(),
                ),
            )
                .withLifecycle(lifecycle)
                .withQueueLease(slotLease)
                .withPrewarmLease(prewarmLease)
                .withRuntimeBudget(runtimeBudget)
                .withCrashEvidence(crashMarker)
                .withTimeline(timeline)
            deliveryResult = failureResult
        } finally {
            val cleanupStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
            val cleanupErrors = mutableListOf<String>()
            fun cleanup(name: String, block: () -> Unit) {
                runCatching(block).onFailure { error ->
                    cleanupErrors += "$name:${error.javaClass.simpleName}"
                    Log.e(TAG, "script.cleanup.failed executionId=$executionId step=$name", error)
                }
            }
            cleanup("request_spool") {
                File(File(cacheDir, REQUEST_SPOOL_DIR), safeRequestSpoolName(executionId)).deleteRecursivelyIfExists()
            }
            cleanup("heartbeat") { activeHeartbeatMonitor?.stop("script.finally") }
            activeHeartbeatMonitor = null
            cleanup("lifecycle_stop_request") { lifecycleStopRequestFile?.parentFile?.deleteRecursivelyIfExists() }
            cleanup("bridge_session") { bridgeSession.stop() }
            cleanup("module_source_session") { moduleSourceSession?.stop() }
            val queueStatus = releaseExecutionSlot(executionId)
            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            val interactiveStopCompleted = interactiveSession &&
                    interactiveStopRequestedExecutionId == executionId
            val serviceRetirementRequested = !retireAfterExecutionReason.isNullOrBlank() ||
                    interactiveStopCompleted
            val postCleanupResult = deliveryResult?.let { result ->
                val moduleProviderValues = moduleSourceSession?.nativeValues().orEmpty()
                val cleanedResult = bridgeSession.appendNativeValues(
                    result.copy(nativeValues = result.nativeValues + moduleProviderValues),
                )
                val cleanupPhaseMs = NodeEmbeddedProcessSessionManager.elapsedPhaseMs(cleanupStartedAtNanos)
                val cleanupValues = linkedMapOf<String, String>().apply {
                        putAll(cleanedResult.nativeValues)
                        put("embedded_script.service.cleanup_clean", cleanupErrors.isEmpty().toString())
                        put("embedded_script.service.cleanup_errors", cleanupErrors.joinToString(","))
                        put("embedded_script.service.result_delivery_phase", "post_execution_cleanup")
                        put("embedded_script.service.execution_slot_released", "true")
                        put("embedded_script.service.queue_idle_after_release", queueStatus.idle.toString())
                        put("embedded_script.service.retirement_requested", serviceRetirementRequested.toString())
                }
                cleanedResult.copy(
                    nativeValues = NodeEmbeddedProcessSessionManager.mergeClientPhaseTimings(
                        cleanupValues,
                        linkedMapOf(
                            NodeEmbeddedProcessSessionManager.PhaseTiming.REQUEST_SPOOL to requestSpoolPhaseMs,
                            NodeEmbeddedProcessSessionManager.PhaseTiming.QUEUE to slotLease.queueWaitMs,
                            NodeEmbeddedProcessSessionManager.PhaseTiming.SERVICE to servicePhaseMs,
                            NodeEmbeddedProcessSessionManager.PhaseTiming.NATIVE to nativePhaseMs,
                            NodeEmbeddedProcessSessionManager.PhaseTiming.CLEANUP to cleanupPhaseMs,
                        ),
                    ),
                )
            }
            if (postCleanupResult == null) {
                NodeCrashEvidenceStore.markServiceDestroy(
                    context = this,
                    marker = crashMarker,
                    reason = "embedded_script_service.finally",
                    nativeValues = lifecycle.nativeValues(),
                )
            } else {
                NodeCrashEvidenceStore.markEmbeddedResult(this, crashMarker, postCleanupResult)
            }
            activeCrashMarker = null
            activeLifecycle = null
            Log.i(
                TAG,
                "script.exit executionId=$executionId processName=$processName pid=$pid " +
                        "sourceName=$sourceName state=${lifecycle.state.name} elapsed=${elapsed}ms",
            )
            if (queueStatus.idle) {
                finishStartedServiceWhenIdle(startId, "execution_complete")
            } else {
                Log.i(
                    TAG,
                    "script.queue.keep_service_alive executionId=$executionId active=${queueStatus.activeExecutionId.orEmpty()} " +
                    "queued=${queueStatus.queuedCount}",
                )
            }
            if (interactiveStopCompleted) {
                pendingInteractiveExitRunnable?.let(mainHandler::removeCallbacks)
                pendingInteractiveExitRunnable = null
                interactiveStopRequestedExecutionId = null
            }
            if (interactiveSession) {
                clearActiveInteractiveSession(executionId)
            }
            postCleanupResult?.let { result ->
                runCatching { receiver?.send(RESULT_SCRIPT, result.toBundle()) }
                    .onFailure { error ->
                        Log.w(TAG, "script.result_delivery.failed executionId=$executionId", error)
                        requestPersistentShutdown("result_delivery_failed")
                    }
            }
            if (interactiveStopCompleted) {
                requestPersistentShutdown("interactive_stop_completed")
            }
        }
    }

    @Synchronized
    private fun handlePrewarmRuntime(intent: Intent, startId: Int) {
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val ttlMs = normalizedPrewarmTtlMs(intent.getLongExtra(EXTRA_PREWARM_TTL_MS, DEFAULT_PREWARM_TTL_MS))
        val queueStatus = queueStatusSnapshot()
        if (!queueStatus.idle) {
            receiver?.send(
                RESULT_PREWARM,
                prewarmResultBundle(
                    started = false,
                    status = "busy",
                    ttlMs = ttlMs,
                    reason = "active_or_queued_execution",
                ),
            )
            return
        }
        if (NodeEmbeddedProcessSessionManager.isEnabled()) {
            val ready = runCatching { ensureServiceRuntimeReady() }
            if (ready.isFailure) {
                receiver?.send(
                    RESULT_PREWARM,
                    prewarmResultBundle(
                        started = false,
                        status = "failed",
                        ttlMs = ttlMs,
                        reason = ready.exceptionOrNull()?.message.orEmpty().ifBlank { "runtime_initialization_failed" },
                    ),
                )
                finishStartedServiceWhenIdle(startId, "prewarm_failed")
                return
            }
        }
        prewarmStopRunnable?.let(mainHandler::removeCallbacks)
        prewarmActive = true
        prewarmStartedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        prewarmStartId = startId
        val runnable = Runnable {
            expirePrewarm("ttl_expired")
        }
        prewarmStopRunnable = runnable
        mainHandler.postDelayed(runnable, ttlMs)
        Log.i(TAG, "prewarm.ready processName=${currentProcessName()} pid=${Process.myPid()} ttlMs=$ttlMs")
        receiver?.send(
            RESULT_PREWARM,
            prewarmResultBundle(
                started = true,
                status = "ready",
                ttlMs = ttlMs,
                reason = "process_prewarmed",
            ),
        )
    }

    @Synchronized
    private fun consumePrewarmForExecution(): PrewarmLease {
        if (!prewarmActive) {
            return PrewarmLease(
                status = "not_requested",
                consumed = false,
                ageMs = 0L,
            )
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val ageMs = (now - prewarmStartedAtElapsedMs).coerceAtLeast(0L)
        prewarmStopRunnable?.let(mainHandler::removeCallbacks)
        prewarmStopRunnable = null
        prewarmActive = false
        prewarmStartedAtElapsedMs = 0L
        prewarmStartId = 0
        Log.i(TAG, "prewarm.consumed processName=${currentProcessName()} pid=${Process.myPid()} ageMs=$ageMs")
        return PrewarmLease(
            status = "consumed",
            consumed = true,
            ageMs = ageMs,
        )
    }

    @Synchronized
    private fun expirePrewarm(reason: String) {
        if (!prewarmActive) {
            return
        }
        val ageMs = (android.os.SystemClock.elapsedRealtime() - prewarmStartedAtElapsedMs).coerceAtLeast(0L)
        clearPrewarmState(reason)
        Log.i(TAG, "prewarm.expired reason=$reason processName=${currentProcessName()} pid=${Process.myPid()} ageMs=$ageMs")
        requestPersistentShutdown(reason)
    }

    @Synchronized
    private fun clearPrewarmState(reason: String) {
        prewarmStopRunnable?.let(mainHandler::removeCallbacks)
        prewarmStopRunnable = null
        if (prewarmActive) {
            Log.i(TAG, "prewarm.clear reason=$reason processName=${currentProcessName()} pid=${Process.myPid()}")
        }
        prewarmActive = false
        prewarmStartedAtElapsedMs = 0L
        prewarmStartId = 0
    }

    private fun prewarmResultBundle(
        started: Boolean,
        status: String,
        ttlMs: Long,
        reason: String,
    ): Bundle {
        val runtimeValues = runtimeControlValues.get()
        val runtimeLoaded = runtimeValues["process_runtime.state"] == "ready" &&
                runtimeValues["process_runtime.healthy"]?.toBooleanStrictOrNull() == true
        return Bundle().apply {
            putBoolean(EXTRA_PREWARM_STARTED, started)
            putString(EXTRA_PREWARM_STATUS, status)
            putString(EXTRA_PREWARM_REASON, reason)
            putLong(EXTRA_PREWARM_TTL_MS, ttlMs)
            putLong(EXTRA_PREWARM_STARTED_AT_ELAPSED_MS, prewarmStartedAtElapsedMs)
            putString(EXTRA_PREWARM_PROCESS_NAME, currentProcessName())
            putInt(EXTRA_PREWARM_PID, Process.myPid())
            putBoolean(EXTRA_PREWARM_PROCESS_ONLY, !runtimeLoaded)
            putBoolean(EXTRA_PREWARM_RUNTIME_LOADED, runtimeLoaded)
            putStringArray(
                EXTRA_PREWARM_NATIVE_PAYLOAD,
                (prewarmNativeValues(
                    started = started,
                    status = status,
                    ttlMs = ttlMs,
                    reason = reason,
                    ageMs = 0L,
                ) + runtimeValues).map { (key, value) -> "$key=$value" }.toTypedArray(),
            )
        }
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withPrewarmLease(
        lease: PrewarmLease,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return copy(
            nativeValues = linkedMapOf<String, String>().apply {
                putAll(nativeValues)
                putAll(
                    prewarmNativeValues(
                        started = lease.consumed,
                        status = lease.status,
                        ttlMs = 0L,
                        reason = if (lease.consumed) "consumed_by_execution" else "no_active_prewarm",
                        ageMs = lease.ageMs,
                    ),
                )
            },
        )
    }

    private fun prewarmNativeValues(
        started: Boolean,
        status: String,
        ttlMs: Long,
        reason: String,
        ageMs: Long,
    ): Map<String, String> {
        val runtimeLoaded = runtimeControlValues.get()["process_runtime.state"] == "ready"
        return linkedMapOf(
            "embedded_script.prewarm_pool.enabled" to "true",
            "embedded_script.prewarm_pool.status" to status,
            "embedded_script.prewarm_pool.started" to started.toString(),
            "embedded_script.prewarm_pool.consumed" to (status == "consumed").toString(),
            "embedded_script.prewarm_pool.process_only" to (!runtimeLoaded).toString(),
            "embedded_script.prewarm_pool.runtime_loaded_before_execution" to runtimeLoaded.toString(),
            "embedded_script.prewarm_pool.capabilities_granted" to "false",
            "embedded_script.prewarm_pool.user_modules_loaded" to "false",
            "embedded_script.prewarm_pool.ttl_ms" to ttlMs.toString(),
            "embedded_script.prewarm_pool.age_ms" to ageMs.toString(),
            "embedded_script.prewarm_pool.reason" to reason,
            "embedded_script.prewarm_pool.policy" to "single-use-process-only",
        )
    }

    private fun requestTransportFailureResult(
        intent: Intent,
        throwable: Throwable,
        startedAt: Long,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val executionId = intent.getStringExtra(EXTRA_EXECUTION_ID)?.takeIf { it.isNotBlank() } ?: newExecutionId()
        val sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME).orEmpty().ifBlank { DEFAULT_SOURCE_NAME }
        val workingDirectory = intent.getStringExtra(EXTRA_WORKING_DIRECTORY)?.takeIf { it.isNotBlank() }
        val processName = currentProcessName()
        val pid = Process.myPid()
        val elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val lifecycle = NodeEmbeddedRuntimeLifecycle(TAG, executionId, processName, pid, sourceName)
            .apply { transition(NodeEmbeddedRuntimeState.FAILED, "service.request_transport") }
        val message = "Embedded script request transport failed: ${throwable.message ?: throwable.javaClass.name}"
        val nativeValues = linkedMapOf(
            "embedded_script.request_transport_failed" to "true",
            "embedded_script.error_code" to NodeRuntimeErrorCodes.EMBEDDED_NODE_INVALID_STATE,
            "embedded_script.error_name" to "EmbeddedScriptRequestTransportError",
            "embedded_script.source_name" to sourceName,
            "embedded_script.working_directory" to workingDirectory.orEmpty(),
            "embedded_script.elapsed_ms" to elapsedMs.toString(),
        )
        return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
            succeeded = false,
            exitCode = 1,
            resultText = null,
            stdout = "",
            stderr = "",
            errorName = "EmbeddedScriptRequestTransportError",
            errorMessage = message,
            errorStack = throwable.stackTraceToString(),
            errorCode = NodeRuntimeErrorCodes.EMBEDDED_NODE_INVALID_STATE,
            elapsedMs = elapsedMs,
            processName = processName,
            pid = pid,
            sourceName = sourceName,
            nativeValues = nativeValues,
            workingDirectory = workingDirectory,
        ).withLifecycle(lifecycle)
    }

    private fun stopServiceIfQueueIdle(startId: Int) {
        if (queueStatusSnapshot().idle) {
            finishStartedServiceWhenIdle(startId, "request_rejected")
        }
    }

    private fun finishStartedServiceWhenIdle(startId: Int, reason: String) {
        mainHandler.post {
            if (!queueStatusSnapshot().idle || !stopSelfResult(startId)) {
                return@post
            }
            foregroundServiceCreator?.stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundActive = false
            if (!NodeEmbeddedProcessSessionManager.isEnabled()) {
                scheduleProcessExit(0)
            } else {
                Log.i(
                    TAG,
                    "session.idle processName=${currentProcessName()} pid=${Process.myPid()} reason=$reason",
                )
            }
        }
    }

    private fun dispatchQueuedBridgeRequests(
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val requests = NodeBridgeRequest.fromJsonArray(
            result.nativeValues["embedded_script.bridge_requests_json"].orEmpty(),
        )
        if (requests.isEmpty()) {
            return result
        }
        val provider = AndroidNodeBridgeCapabilityProvider(this)
        val latch = CountDownLatch(requests.size)
        val completed = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val responsesLock = Any()
        val responses = mutableListOf<NodeBridgeResponse>()
        for (request in requests) {
            val responded = AtomicBoolean(false)
            fun complete(response: NodeBridgeResponse) {
                if (!responded.compareAndSet(false, true)) {
                    return
                }
                synchronized(responsesLock) {
                    responses += response
                }
                completed.incrementAndGet()
                if (!response.ok) {
                    failed.incrementAndGet()
                }
                latch.countDown()
            }
            runCatching {
                provider.dispatch(request, ::complete)
            }.onFailure { error ->
                complete(request.dispatchExceptionResponse(error))
            }
        }
        runCatching {
            latch.await(BRIDGE_DISPATCH_WAIT_MS, TimeUnit.MILLISECONDS)
        }
        val pending = latch.count.toInt().coerceAtLeast(0)
        val providerCleanup = runCatching {
            provider.destroy(NodeBridgeError.processDead("per_execution_bridge_dispatch_complete"))
        }
        val nativeValues = linkedMapOf<String, String>().apply {
            putAll(result.nativeValues)
            put("embedded_script.bridge_dispatch_count", completed.get().toString())
            put("embedded_script.bridge_dispatch_failed_count", failed.get().toString())
            put("embedded_script.bridge_dispatch_pending_count", pending.toString())
            put("embedded_script.bridge_responses_json", bridgeResponsesJson(synchronized(responsesLock) { responses.toList() }))
            put(
                "embedded_script.bridge_dispatch_status",
                if (pending == 0) "done" else "pending",
            )
            put("embedded_script.bridge_dispatch_provider_cleanup_clean", providerCleanup.isSuccess.toString())
            putAll(provider.nativeDiagnostics())
        }
        return result.copy(nativeValues = nativeValues)
    }

    private fun Map<String, String>.withHostAppInfo(): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            putAll(this@withHostAppInfo)
            put(HOST_APP_INFO_RUNTIME_MODULE_NAME, hostAppInfoJson())
        }
    }

    private fun hostAppInfoJson(): String {
        val hostPackageName = this.packageName
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(hostPackageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(hostPackageName, 0)
            }
        }.getOrNull()
        val versionCode = packageInfo?.let { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } ?: 0L
        return JSONObject().apply {
            put("packageName", hostPackageName)
            put("versionName", packageInfo?.versionName.orEmpty())
            put("versionCode", versionCode)
        }.toString()
    }

    private fun Map<String, String>.withDeviceInfo(): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            putAll(this@withDeviceInfo)
            put(DEVICE_INFO_RUNTIME_MODULE_NAME, deviceInfoJson())
        }
    }

    private fun deviceInfoJson(): String {
        val metrics = resources.displayMetrics
        return JSONObject().apply {
            put("sdkInt", Build.VERSION.SDK_INT)
            put("width", metrics.widthPixels.coerceAtLeast(0))
            put("height", metrics.heightPixels.coerceAtLeast(0))
            put("density", metrics.density.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 1.0)
        }.toString()
    }

    private fun Map<String, String>.withBridgeLiveConfig(configJson: String): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            putAll(this@withBridgeLiveConfig)
            put(BRIDGE_LIVE_CONFIG_RUNTIME_MODULE_NAME, configJson)
        }
    }

    private fun Map<String, String>.withLifecycleConfig(configJson: String): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            putAll(this@withLifecycleConfig)
            put(NodeLifecycleCheckpointStore.RUNTIME_MODULE_NAME, configJson)
        }
    }

    private fun Map<String, String>.withBridgePermissionManifest(
        workingDirectory: String?,
        sandboxRoot: String?,
    ): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            putAll(this@withBridgePermissionManifest)
            if (!containsKey(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME)) {
                put(
                    NodeBridgePermissionManifest.RUNTIME_MODULE_NAME,
                    NodeBridgePermissionManifest.runtimeModuleSourceForWorkingDirectory(
                        workingDirectory,
                        sandboxRoot,
                        includeBuildNetworkPolicy = BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED,
                    ),
                )
            }
        }
    }

    private fun Map<String, String>.withBridgeLimitPolicy(bridgeLimits: NodeBridgeLimits.Policy): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            putAll(this@withBridgeLimitPolicy)
            if (!containsKey(NodeBridgeLimits.RUNTIME_MODULE_NAME)) {
                put(NodeBridgeLimits.RUNTIME_MODULE_NAME, bridgeLimits.toJson())
            }
        }
    }

    private fun Map<String, String>.withEngineInfo(engineInfoJson: String): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            putAll(this@withEngineInfo)
            put(ENGINE_INFO_RUNTIME_MODULE_NAME, engineInfoJson)
        }
    }

    private fun bridgeResponsesJson(responses: List<NodeBridgeResponse>): String {
        return JSONArray().apply {
            responses.forEach { response ->
                put(JSONObject(response.toJson()))
            }
        }.toString()
    }

    private fun queueFailureResult(
        failure: ExecutionSlotFailure,
        executionId: String,
        sourceName: String,
        workingDirectory: String?,
        startedAt: Long,
        processName: String,
        pid: Int,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val lifecycle = NodeEmbeddedRuntimeLifecycle(TAG, executionId, processName, pid, sourceName)
            .apply {
                transition(NodeEmbeddedRuntimeState.FAILED, "service.${failure.reason}")
            }
        val elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val nativeValues = linkedMapOf<String, String>().apply {
            put("embedded_script.queue_mode", failure.queueMode)
            put("embedded_script.queue_status", failure.reason)
            put("embedded_script.queue_wait_ms", failure.queueWaitMs.toString())
            put("embedded_script.queue_queued_count", failure.queuedCount.toString())
            put("embedded_script.queue_max_queued_executions", failure.maxQueuedExecutions.toString())
            put("embedded_script.concurrent_policy", "single-active-execution-per-process")
            put("embedded_script.active_execution_id", failure.activeExecutionId.orEmpty())
            put("embedded_script.rejected_execution_id", executionId)
            put("embedded_script.error_code", failure.errorCode)
            when (failure.errorCode) {
                NodeRuntimeErrorCodes.NODE_ENGINE_BUSY -> put("embedded_script.engine_busy", "true")
                NodeRuntimeErrorCodes.NODE_QUEUE_FULL -> put("embedded_script.queue_full", "true")
                NodeRuntimeErrorCodes.NODE_QUEUE_TIMEOUT -> put("embedded_script.queue_timeout", "true")
                NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED -> put("embedded_script.queue_cancelled", "true")
            }
        }
        return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
            succeeded = false,
            exitCode = 1,
            resultText = null,
            stdout = "",
            stderr = "",
            errorName = failure.errorName,
            errorMessage = failure.errorMessage,
            errorStack = null,
            errorCode = failure.errorCode,
            elapsedMs = elapsedMs,
            processName = processName,
            pid = pid,
            sourceName = sourceName,
            nativeValues = nativeValues,
            workingDirectory = workingDirectory,
        ).withLifecycle(lifecycle)
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withQueueLease(
        lease: ExecutionSlotLease,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return copy(
            nativeValues = linkedMapOf<String, String>().apply {
                putAll(nativeValues)
                put("embedded_script.queue_mode", lease.queueMode)
                put("embedded_script.queue_status", if (lease.queued) "dequeued" else "not_queued")
                put("embedded_script.queue_queued", lease.queued.toString())
                put("embedded_script.queue_wait_ms", lease.queueWaitMs.toString())
                put("embedded_script.queue_position", lease.queuePosition.toString())
                put("embedded_script.queue_max_queued_executions", lease.maxQueuedExecutions.toString())
            },
        )
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withExecutionMode(
        executionMode: String,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return copy(
            nativeValues = linkedMapOf<String, String>().apply {
                putAll(nativeValues)
                putAll(NodeExecutionModePolicy.nativeValues(executionMode, launchSurface))
            },
        )
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withRuntimeBudget(
        runtimeBudget: NodeRuntimeBudgetPolicy.Policy,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val budgetValues = NodeRuntimeBudgetPolicy.completionNativeValues(runtimeBudget, nativeValues, elapsedMs)
        return copy(
            nativeValues = linkedMapOf<String, String>().apply {
                putAll(nativeValues)
                putAll(budgetValues)
            },
        )
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withCrashEvidence(
        marker: NodeCrashEvidenceStore.Marker?,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return copy(
            nativeValues = linkedMapOf<String, String>().apply {
                putAll(nativeValues)
                putAll(NodeCrashEvidenceStore.nativeValues(marker))
            },
        )
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withTimeline(
        timeline: NodeEmbeddedRuntimeTimeline?,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        if (timeline == null) {
            return this
        }
        return copy(
            nativeValues = linkedMapOf<String, String>().apply {
                putAll(nativeValues)
                putAll(timeline.nativeValues())
            },
        )
    }

    private class NodeInteractiveHeartbeatMonitor(
        context: Context,
        private val executionId: String,
        private val sourceName: String,
        private val workingDirectory: String?,
        private val startedAtEpochMs: Long,
        private val lifecycle: NodeEmbeddedRuntimeLifecycle,
        private val runtimeBudget: NodeRuntimeBudgetPolicy.Policy,
        private val bridgeSession: NodeBridgeFileTransportSession,
    ) {
        private val appContext = context.applicationContext
        private val running = AtomicBoolean(false)
        private val sequence = AtomicInteger(0)
        private val thread = Thread {
            loop()
        }.apply {
            name = "AutoJs6NodeRuntimeHeartbeat-$executionId"
            isDaemon = true
        }

        fun start() {
            if (running.compareAndSet(false, true)) {
                write("running")
                thread.start()
            }
        }

        fun stop(reason: String) {
            if (running.compareAndSet(true, false)) {
                runCatching { thread.join(HEARTBEAT_STOP_JOIN_MS) }
            }
            Log.i(TAG, "runtime.heartbeat.stop executionId=$executionId reason=$reason sequence=${sequence.get()}")
        }

        fun nativeValuesSnapshot(status: String): Map<String, String> {
            val baseValues = linkedMapOf<String, String>().apply {
                putAll(lifecycle.nativeValues())
                putAll(bridgeSession.nativeValuesSnapshot())
                putAll(runtimeBudget.nativeDiagnostics())
            }
            val snapshot = NodeRuntimeBudgetPolicy.heartbeatSnapshot(
                policy = runtimeBudget,
                nativeValues = baseValues,
                executionId = executionId,
                executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                lifecycleState = lifecycle.state.name,
                sequence = sequence.incrementAndGet(),
                startedAtEpochMs = startedAtEpochMs,
                nowEpochMs = System.currentTimeMillis(),
            )
            return linkedMapOf<String, String>().apply {
                putAll(baseValues)
                putAll(NodeRuntimeBudgetPolicy.heartbeatNativeValues(snapshot))
                put("embedded_script.runtime_heartbeat.status_label", status)
            }
        }

        private fun loop() {
            while (running.get()) {
                Thread.sleep(runtimeBudget.heartbeatIntervalMs)
                if (running.get()) {
                    write("running")
                }
            }
        }

        private fun write(status: String) {
            val nativeValues = nativeValuesSnapshot(status)
            NodeInteractiveLongRunningStatusStore.write(
                context = appContext,
                executionId = executionId,
                state = NodeInteractiveLongRunningStatusStore.STATE_RUNNING,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                startedAtEpochMs = startedAtEpochMs,
                stopRequested = false,
                activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary(status, nativeValues),
                nativeValues = nativeValues,
            )
        }
    }

    private class NodeBridgeFileTransportSession(
        root: File,
        private val bridgeLimits: NodeBridgeLimits.Policy,
        private val provider: NodeBridgeCapabilityProvider,
    ) {
        private val requestDir = File(root, "requests")
        private val responseDir = File(root, "responses")
        private val running = AtomicBoolean(false)
        private val completed = AtomicInteger(0)
        private val failed = AtomicInteger(0)
        private val pending = AtomicInteger(0)
        private val resourceLimited = AtomicInteger(0)
        private val pollCount = AtomicInteger(0)
        private val lastPollAtEpochMs = AtomicLong(0L)
        private val lastRequestAtEpochMs = AtomicLong(0L)
        private val lastResponseAtEpochMs = AtomicLong(0L)
        private val startedAtEpochMs = System.currentTimeMillis()
        private val seen = mutableSetOf<String>()
        private val responsesLock = Any()
        private val responses = mutableListOf<NodeBridgeResponse>()
        private val destroyed = AtomicBoolean(false)
        private val thread = Thread {
            loop()
        }.apply {
            name = "AutoJs6NodeBridgeFileTransport-${root.name}"
            isDaemon = true
        }

        init {
            root.deleteRecursivelyIfExists()
            requestDir.mkdirs()
            responseDir.mkdirs()
        }

        fun configJson(): String {
            return JSONObject().apply {
                put("enabled", true)
                put("version", 1)
                put("requestDir", requestDir.absolutePath)
                put("responseDir", responseDir.absolutePath)
                put("pollIntervalMs", BRIDGE_LIVE_POLL_INTERVAL_MS)
            }.toString()
        }

        fun start() {
            if (running.compareAndSet(false, true)) {
                thread.start()
            }
        }

        fun stop() {
            if (running.compareAndSet(true, false)) {
                runCatching {
                    thread.join(BRIDGE_LIVE_STOP_JOIN_MS)
                }
            }
            if (destroyed.compareAndSet(false, true)) {
                provider.destroy()
            }
            requestDir.parentFile?.deleteRecursivelyIfExists()
        }

        fun appendNativeValues(
            result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val nativeValues = linkedMapOf<String, String>().apply {
                putAll(result.nativeValues)
                putAll(nativeValuesSnapshot())
            }
            return result.copy(nativeValues = nativeValues)
        }

        fun nativeValuesSnapshot(): Map<String, String> = linkedMapOf<String, String>().apply {
            put("embedded_script.bridge_live_enabled", "true")
            put("embedded_script.bridge_live_dispatch_count", completed.get().toString())
            put("embedded_script.bridge_live_dispatch_failed_count", failed.get().toString())
            put("embedded_script.bridge_live_dispatch_pending_count", pending.get().coerceAtLeast(0).toString())
            put("embedded_script.bridge_live_dispatch_pending_limit", bridgeLimits.maxPendingBridgeCalls.toString())
            put("embedded_script.bridge_live_dispatch_resource_limited_count", resourceLimited.get().toString())
            put("embedded_script.bridge_live_dispatch_poll_count", pollCount.get().toString())
            put("embedded_script.bridge_live_dispatch_started_at_epoch_ms", startedAtEpochMs.toString())
            put("embedded_script.bridge_live_dispatch_last_poll_at_epoch_ms", lastPollAtEpochMs.get().toString())
            put("embedded_script.bridge_live_dispatch_last_request_at_epoch_ms", lastRequestAtEpochMs.get().toString())
            put("embedded_script.bridge_live_dispatch_last_response_at_epoch_ms", lastResponseAtEpochMs.get().toString())
            put(
                "embedded_script.bridge_live_dispatch_status",
                if (pending.get() <= 0) "done" else "pending",
            )
            put("embedded_script.bridge_live_responses_json", bridgeResponsesJsonSnapshot())
            putAll(provider.nativeDiagnostics())
        }

        private fun loop() {
            while (running.get()) {
                pollCount.incrementAndGet()
                lastPollAtEpochMs.set(System.currentTimeMillis())
                drainRequestFiles()
                Thread.sleep(BRIDGE_LIVE_POLL_INTERVAL_MS)
            }
            repeat(BRIDGE_LIVE_DRAIN_ITERATIONS) {
                pollCount.incrementAndGet()
                lastPollAtEpochMs.set(System.currentTimeMillis())
                drainRequestFiles()
                if (pending.get() <= 0) {
                    return
                }
                Thread.sleep(BRIDGE_LIVE_POLL_INTERVAL_MS)
            }
        }

        private fun drainRequestFiles() {
            val files = requestDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            }.orEmpty()
            files.sortedBy { it.name }.forEach(::dispatchRequestFile)
        }

        private fun dispatchRequestFile(file: File) {
            synchronized(seen) {
                if (!seen.add(file.name)) {
                    return
                }
            }
            val requestText = runCatching { file.readText() }
                .getOrElse { error ->
                    recordResponse(malformedRequestResponse(file, "", error))
                    file.delete()
                    return
                }
            val request = runCatching {
                NodeBridgeRequest.fromJson(requestText)
            }.getOrElse { error ->
                recordResponse(malformedRequestResponse(file, requestText, error))
                file.delete()
                return
            }
            lastRequestAtEpochMs.set(System.currentTimeMillis())
            val responded = AtomicBoolean(false)
            if (pending.get() >= bridgeLimits.maxPendingBridgeCalls) {
                resourceLimited.incrementAndGet()
                recordResponse(
                    NodeBridgeResponse.failure(
                        request.id,
                        NodeBridgeError.resourceLimit(
                            message = "AutoJs6 live bridge exceeded ${bridgeLimits.maxPendingBridgeCalls} pending calls.",
                            module = request.module,
                            method = request.method,
                        ),
                    ),
                )
                file.delete()
                return
            }
            pending.incrementAndGet()
            fun complete(response: NodeBridgeResponse) {
                if (!responded.compareAndSet(false, true)) {
                    return
                }
                recordResponse(response)
                pending.decrementAndGet()
                file.delete()
            }
            runCatching {
                provider.dispatch(request, ::complete)
            }.onFailure { error ->
                complete(request.dispatchExceptionResponse(error))
            }
        }

        private fun malformedRequestResponse(file: File, requestText: String, error: Throwable): NodeBridgeResponse {
            val id = NodeBridgeRequest.recoverId(requestText)
                ?: file.name.removeSuffix(".json").takeIf { it.isNotBlank() }
                ?: "malformed-request"
            return NodeBridgeResponse.failure(
                id,
                NodeBridgeError.invalidRequest(
                    message = "AutoJs6 bridge request is malformed: ${error.message ?: error.javaClass.name}",
                    module = NodeBridgeRequest.recoverModule(requestText),
                    method = NodeBridgeRequest.recoverMethod(requestText),
                ),
            )
        }

        private fun recordResponse(response: NodeBridgeResponse) {
            synchronized(responsesLock) {
                responses += response
            }
            lastResponseAtEpochMs.set(System.currentTimeMillis())
            writeResponse(response)
            completed.incrementAndGet()
            if (!response.ok) {
                failed.incrementAndGet()
            }
        }

        private fun writeResponse(response: NodeBridgeResponse) {
            val fileName = safeFileName(response.id) + ".json"
            val target = File(responseDir, fileName)
            val temp = File(responseDir, "$fileName.tmp-${System.nanoTime()}")
            runCatching {
                temp.writeText(response.toJson())
                if (!temp.renameTo(target)) {
                    target.writeText(response.toJson())
                    temp.delete()
                }
            }.onFailure {
                temp.delete()
            }
        }

        private fun bridgeResponsesJsonSnapshot(): String {
            val snapshot = synchronized(responsesLock) { responses.toList() }
            return JSONArray().apply {
                snapshot.forEach { response ->
                    put(JSONObject(response.toJson()))
                }
            }.toString()
        }

        private fun safeFileName(id: String): String {
            return id.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
        }
    }

    private fun scheduleProcessExit(status: Int) {
        if (exitScheduled) {
            return
        }
        exitScheduled = true
        mainHandler.postDelayed({
            exitProcess(status)
        }, PROCESS_EXIT_DELAY_MS)
    }

    private fun Map<String, String>.withModuleSourceProviderConfig(configJson: String?): Map<String, String> {
        if (configJson.isNullOrBlank()) {
            return this
        }
        return linkedMapOf<String, String>().apply {
            putAll(this@withModuleSourceProviderConfig)
            put(MODULE_SOURCE_PROVIDER_RUNTIME_MODULE_NAME, configJson)
        }
    }

    private fun runRuntimeControl(operation: String, block: () -> Bundle): Bundle {
        return runCatching {
            runtimeExecutor.submit<Bundle> { block() }
                .get(RUNTIME_CONTROL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }.getOrElse { error ->
            Log.e(TAG, "runtime.control.failed operation=$operation", error)
            runtimeStatusBundle(
                mapOf(
                    "process_runtime.state" to "failed",
                    "process_runtime.healthy" to "false",
                    "process_runtime.poisoned" to "true",
                    "process_runtime.control_operation" to operation,
                    "process_runtime.control_error" to (error.message ?: error.javaClass.name),
                ),
            )
        }
    }

    private fun runtimeStatusBundle(values: Map<String, String>): Bundle {
        return Bundle().apply {
            values.forEach { (key, value) -> putString(key, value) }
            putString("process_runtime.process_name", currentProcessName())
            putString("process_runtime.pid", Process.myPid().toString())
            putString("process_runtime.service_class", this@NativeNodeEmbeddedScriptService.javaClass.name)
            putString("process_runtime.service_queue_idle", queueStatusSnapshot().idle.toString())
            putString("process_runtime.service_retire_requested", (!retireAfterExecutionReason.isNullOrBlank()).toString())
            if (!values.containsKey("process_runtime.persistent_enabled")) {
                putString(
                    "process_runtime.persistent_enabled",
                    NodeEmbeddedProcessSessionManager.isEnabled().toString(),
                )
            }
        }
    }

    private fun ensureServiceRuntimeReady(): Map<String, String> {
        val persistent = NodeEmbeddedProcessSessionManager.isEnabled()
        val persistentValues = NativeNodeEmbeddedRuntimeBridge.setProcessRuntimePersistentEnabled(
            this@NativeNodeEmbeddedScriptService,
            persistent,
        )
        val values = if (persistent) {
            mergeProcessRuntimeControlValues(
                persistentValues,
                NativeNodeEmbeddedRuntimeBridge.ensureProcessRuntimeReady(this@NativeNodeEmbeddedScriptService),
            )
        } else {
            persistentValues
        }
        runtimeControlValues.set(values)
        if (persistent) {
            val state = values["process_runtime.state"].orEmpty()
            val healthy = values["process_runtime.healthy"]?.toBooleanStrictOrNull()
            if (state != "ready" || healthy != true) {
                throw IllegalStateException(
                    values["process_runtime.ensure.detail"]
                        .orEmpty()
                        .ifBlank { "Embedded Node process runtime did not become ready; state=$state." },
                )
            }
        }
        return values
    }

    private fun mergeProcessRuntimeControlValues(
        vararg valueSets: Map<String, String>,
    ): Map<String, String> {
        val loadKey = NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_NATIVE_LOAD_TIMING_KEY
        var loadDurationMs = 0L
        var loadDurationPresent = false
        return linkedMapOf<String, String>().apply {
            valueSets.forEach { values ->
                values[loadKey]?.toLongOrNull()?.takeIf { it >= 0L }?.let { durationMs ->
                    loadDurationPresent = true
                    loadDurationMs = if (Long.MAX_VALUE - loadDurationMs < durationMs) {
                        Long.MAX_VALUE
                    } else {
                        loadDurationMs + durationMs
                    }
                }
                putAll(values)
            }
            if (loadDurationPresent) {
                put(loadKey, loadDurationMs.toString())
            }
        }
    }

    private fun requestPersistentShutdown(
        reason: String,
        awaitTerminalShutdown: Boolean = false,
    ) {
        val normalizedReason = reason.ifBlank { "unspecified" }
        val deadline = android.os.SystemClock.elapsedRealtime() + RUNTIME_CONTROL_TIMEOUT_MS
        val readyToShutdown = synchronized(serviceExecutionLock) {
            if (retireAfterExecutionReason.isNullOrBlank()) {
                retireAfterExecutionReason = normalizedReason
            }
            serviceExecutionLock.notifyAll()
            activeServiceExecutionRequests == 0
        }
        if (!NodeEmbeddedProcessSessionManager.isEnabled()) {
            if (readyToShutdown) {
                mainHandler.post {
                    stopSelf()
                    scheduleProcessExit(0)
                }
            }
            return
        }
        if (readyToShutdown) {
            ensureTerminalShutdownQueued(normalizedReason)
        }
        if (!awaitTerminalShutdown) {
            return
        }
        runCatching {
            synchronized(serviceExecutionLock) {
                while (activeServiceExecutionRequests > 0) {
                    val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                    if (remaining <= 0L) {
                        throw IllegalStateException(
                            "Timed out waiting for $activeServiceExecutionRequests embedded Node execution cleanup(s).",
                        )
                    }
                    serviceExecutionLock.wait(remaining.coerceAtMost(250L))
                }
            }
            val shutdownFuture = ensureTerminalShutdownQueued(normalizedReason)
                ?: throw IllegalStateException("Embedded Node runtime executor is unavailable during shutdown.")
            val remaining = deadline - android.os.SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                throw IllegalStateException("Embedded Node runtime shutdown deadline expired.")
            }
            shutdownFuture.get(remaining, TimeUnit.MILLISECONDS)
        }.onFailure { error ->
            Log.w(TAG, "Embedded Node runtime shutdown barrier failed; exiting the service process.", error)
            mainHandler.post {
                stopSelf()
                scheduleProcessExit(0)
            }
        }
    }

    private fun ensureTerminalShutdownQueued(reason: String): Future<*>? = synchronized(serviceExecutionLock) {
        terminalShutdownFuture?.let { return@synchronized it }
        if (runtimeExecutor.isShutdown) {
            return@synchronized null
        }
        runCatching {
            runtimeExecutor.submit {
                shutdownPersistentRuntime(reason)
                mainHandler.post {
                    foregroundServiceCreator?.stopForeground(STOP_FOREGROUND_REMOVE)
                    foregroundActive = false
                    stopSelf()
                }
            }.also { future -> terminalShutdownFuture = future }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to enqueue embedded Node terminal shutdown.", error)
            null
        }
    }

    private fun shutdownPersistentRuntime(reason: String) {
        if (!NodeEmbeddedProcessSessionManager.isEnabled() ||
            !terminalShutdownStarted.compareAndSet(false, true)
        ) {
            return
        }
        val values = runCatching {
            NativeNodeEmbeddedRuntimeBridge.shutdownProcessRuntime(this, reason)
        }.getOrElse { error ->
            mapOf(
                "process_runtime.state" to "terminal",
                "process_runtime.healthy" to "false",
                "process_runtime.shutdown_error" to (error.message ?: error.javaClass.name),
            )
        }
        runtimeControlValues.set(values)
        Log.i(
            TAG,
            "runtime.shutdown processName=${currentProcessName()} pid=${Process.myPid()} " +
                    "reason=$reason state=${values["process_runtime.state"].orEmpty()}",
        )
    }

    private fun startScriptForeground() {
        foregroundServiceCreator = ForegroundServiceCreator.Builder(this)
            .setClassName(NativeNodeEmbeddedScriptService::class.java)
            .setIntent(Intent(this, MainActivity::class.java))
            .setNotificationId(serviceNotificationId())
            .setServiceName("AutoJs6 embedded Node script")
            .setServiceDescription("Runs isolated embedded Node scripts")
            .setNotificationTitle("AutoJs6 embedded Node script")
            .setNotificationContent("Running isolated embedded Node script")
            .create()
            .apply { startForeground(foregroundServiceType) }
        foregroundActive = true
    }

    private fun ensureScriptForeground() {
        if (foregroundActive) {
            return
        }
        val creator = foregroundServiceCreator
        if (creator == null) {
            startScriptForeground()
        } else {
            creator.startForeground(foregroundServiceType)
            foregroundActive = true
        }
    }

    private fun serviceNotificationId(): Int {
        val slotIndex = NodeEmbeddedProcessPoolPolicy.slotForServiceClassName(javaClass.name)?.index ?: 0
        return NOTIFICATION_ID + slotIndex
    }

    private fun currentProcessName(): String = runCatching { App.getProcessNameCompat() }
        .getOrElse { "unknown" }

    private sealed class ExecutionSlotDecision {

        data class Acquired(val lease: ExecutionSlotLease) : ExecutionSlotDecision()

        data class Rejected(val failure: ExecutionSlotFailure) : ExecutionSlotDecision()
    }

    private sealed class DefaultProcessPoolSlotDecision {

        data class Acquired(val lease: DefaultProcessPoolSlotLease) : DefaultProcessPoolSlotDecision()

        data class Rejected(
            val failure: ExecutionSlotFailure,
            val activeSlotIds: List<String>,
        ) : DefaultProcessPoolSlotDecision()

        object NotEligible : DefaultProcessPoolSlotDecision()
    }

    private data class ExecutionSlotLease(
        val executionId: String,
        val queueMode: String,
        val queued: Boolean,
        val queueWaitMs: Long,
        val queuePosition: Int,
        val maxQueuedExecutions: Int,
    )

    private data class DefaultProcessPoolSlotLease(
        val executionId: String,
        val slot: NodeEmbeddedProcessPoolPolicy.ProcessSlot,
        val activeSlotIdsAtAcquire: List<String>,
        val acquiredAtElapsedMs: Long,
        val queueMode: String,
        val queued: Boolean,
        val queueWaitMs: Long,
    )

    private data class ExecutionSlotFailure(
        val errorName: String,
        val errorCode: String,
        val errorMessage: String,
        val queueMode: String,
        val reason: String,
        val activeExecutionId: String?,
        val queueWaitMs: Long,
        val queuedCount: Int,
        val maxQueuedExecutions: Int,
    )

    private data class PrewarmLease(
        val status: String,
        val consumed: Boolean,
        val ageMs: Long,
    )

    private fun stopInteractiveLongRunning(intent: Intent, startId: Int) {
        val requestedExecutionId = intent.getStringExtra(EXTRA_EXECUTION_ID)?.takeIf { it.isNotBlank() }
        stopActiveInteractiveLongRunning(
            requestedExecutionId = requestedExecutionId,
            startId = startId,
            ignoredReason = "interactive.stop.ignored",
            transitionReason = "interactive.stop",
            stopRequestReason = "interactive.stop.requested",
            stoppingMessage = "interactive.stop.requested",
            stoppedMessage = "interactive.stop.accepted",
        )
    }

    private fun stopActiveInteractiveLongRunning(
        requestedExecutionId: String?,
        startId: Int?,
        ignoredReason: String,
        transitionReason: String,
        stopRequestReason: String,
        stoppingMessage: String,
        stoppedMessage: String,
    ): Boolean {
        val executionId = activeExecutionId
        if (executionId == null || (requestedExecutionId != null && requestedExecutionId != executionId)) {
            Log.w(
                TAG,
                "$ignoredReason requested=${requestedExecutionId.orEmpty()} active=${executionId.orEmpty()}",
            )
            startId?.let { stopSelf(it) }
            return false
        }
        activeLifecycle?.let { lifecycle ->
            if (lifecycle.state !in setOf(
                    NodeEmbeddedRuntimeState.STOPPING,
                    NodeEmbeddedRuntimeState.STOPPED,
                    NodeEmbeddedRuntimeState.DESTROYED,
                )
            ) {
                lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, transitionReason)
            }
        }
        val stoppingNativeValues = activeHeartbeatMonitor?.nativeValuesSnapshot("stopping")
            ?: activeLifecycle?.nativeValues().orEmpty()
        activeHeartbeatMonitor?.stop(transitionReason)
        activeHeartbeatMonitor = null
        NodeLifecycleCheckpointStore.writeStopRequest(
            file = activeLifecycleStopRequestFile,
            executionId = executionId,
            reason = stopRequestReason,
        )
        NodeInteractiveLongRunningStatusStore.write(
            context = this,
            executionId = executionId,
            state = NodeInteractiveLongRunningStatusStore.STATE_STOPPING,
            sourceName = activeSourceName,
            workingDirectory = activeWorkingDirectory,
            startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L },
            stopRequested = true,
            message = stoppingMessage,
            activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("stopping", stoppingNativeValues),
            nativeValues = stoppingNativeValues,
        )
        val sourceName = activeSourceName
        val workingDirectory = activeWorkingDirectory
        val startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L }
        pendingInteractiveExitRunnable?.let(mainHandler::removeCallbacks)
        interactiveStopRequestedExecutionId = executionId
        val exitRunnable = Runnable {
            if (interactiveStopRequestedExecutionId != executionId || activeExecutionId != executionId) {
                return@Runnable
            }
            pendingInteractiveExitRunnable = null
            NodeCrashEvidenceStore.markNativeProcessExit(
                context = this,
                marker = activeCrashMarker,
                exitCode = 0,
                elapsedMs = activeStartedAtEpochMs
                    .takeIf { it > 0L }
                    ?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }
                    ?: 0L,
            )
            activeCrashMarker = null
            NodeInteractiveLongRunningStatusStore.write(
                context = this,
                executionId = executionId,
                state = NodeInteractiveLongRunningStatusStore.STATE_STOPPED,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                startedAtEpochMs = startedAtEpochMs,
                stopRequested = true,
                message = stoppedMessage,
                activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("process_exit_scheduled", stoppingNativeValues),
                nativeValues = stoppingNativeValues,
            )
            if (startId != null) {
                stopSelf(startId)
            } else {
                stopSelf()
            }
            scheduleProcessExit(0)
        }
        pendingInteractiveExitRunnable = exitRunnable
        mainHandler.postDelayed(exitRunnable, NodeLifecycleCheckpointStore.STOP_GRACE_MS)
        return true
    }

    private fun handleForegroundServiceTimeout(
        startId: Int,
        foregroundServiceType: Int,
        source: String,
    ) {
        val diagnostics = NodeAndroidRuntimePolicy.foregroundServiceTimeoutDiagnostics(
            startId = startId,
            foregroundServiceType = foregroundServiceType,
            serviceClassName = NativeNodeEmbeddedScriptService::class.java.name,
        )
        Log.w(TAG, "android.policy.foreground_timeout source=$source diagnostics=$diagnostics")
        cancelAllQueuedExecutionsInService()
        activeLifecycle?.let { lifecycle ->
            if (lifecycle.state !in setOf(
                    NodeEmbeddedRuntimeState.TIMED_OUT,
                    NodeEmbeddedRuntimeState.STOPPING,
                    NodeEmbeddedRuntimeState.STOPPED,
                    NodeEmbeddedRuntimeState.DESTROYED,
                )
            ) {
                lifecycle.transition(NodeEmbeddedRuntimeState.TIMED_OUT, source)
            }
            if (lifecycle.state !in setOf(
                    NodeEmbeddedRuntimeState.STOPPING,
                    NodeEmbeddedRuntimeState.STOPPED,
                    NodeEmbeddedRuntimeState.DESTROYED,
                )
            ) {
                lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "$source.cleanup")
            }
        }
        activeExecutionId
            ?.takeIf { activeExecutionMode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING }
            ?.let { executionId ->
                val heartbeatValues = activeHeartbeatMonitor?.nativeValuesSnapshot("foreground_service_timeout")
                    ?: activeLifecycle?.nativeValues().orEmpty()
                activeHeartbeatMonitor?.stop("foreground_service_timeout")
                activeHeartbeatMonitor = null
                val nativeValues = linkedMapOf<String, String>().apply {
                    putAll(heartbeatValues)
                    putAll(NodeAndroidRuntimePolicy.nativeValues("embedded_script.android_foreground_timeout", diagnostics))
                    put("embedded_script.foreground_service_timeout", "true")
                }
                NodeInteractiveLongRunningStatusStore.write(
                    context = this,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_TIMED_OUT,
                    sourceName = activeSourceName,
                    workingDirectory = activeWorkingDirectory,
                    startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L },
                    stopRequested = true,
                    message = "$source foreground service timeout; stopSelf cleanup requested",
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("foreground_service_timeout", nativeValues),
                    nativeValues = nativeValues,
                )
                NodeCrashEvidenceStore.markTimeout(
                    context = this,
                    marker = activeCrashMarker,
                    reason = "foreground_service_timeout",
                    nativeValues = nativeValues,
                )
            }
        foregroundServiceCreator?.stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        scheduleProcessExit(0)
    }

    private fun updateInteractiveForeground(executionId: String, sourceName: String) {
        ForegroundServiceUtils.createNotificationChannelIfNeeded(
            this,
            NativeNodeEmbeddedScriptService::class.java,
            "AutoJs6 interactive Node runtime",
            "Runs user-visible long-running Node automation",
        )
        val channelId = ForegroundServiceUtils.getChannelId(NativeNodeEmbeddedScriptService::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            INTERACTIVE_CONTENT_REQUEST_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, NativeNodeEmbeddedInteractiveStopReceiver::class.java).apply {
            action = ACTION_STOP_INTERACTIVE
            putExtra(EXTRA_EXECUTION_ID, executionId)
            putExtra(EXTRA_STOP_SERVICE_CLASS, this@NativeNodeEmbeddedScriptService.javaClass.name)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            INTERACTIVE_STOP_REQUEST_CODE xor executionId.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val displayName = sourceName.ifBlank { DEFAULT_SOURCE_NAME }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoJs6 interactive Node runtime")
            .setContentText("Running $displayName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Running $displayName"))
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.autojs6_status_bar_icon)
            .setOngoing(true)
            .setSilent(true)
            .setWhen(System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setChannelId(channelId)
            .addAction(R.drawable.ic_stop_black_48dp, "Stop", stopPendingIntent)
            .build()
            .apply { flags = flags or Notification.FLAG_FOREGROUND_SERVICE }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && foregroundServiceType != FOREGROUND_SERVICE_TYPE_UNKNOWN) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun clearActiveInteractiveSession(executionId: String?) {
        if (executionId != null && activeExecutionId != executionId) {
            return
        }
        activeExecutionId = null
        activeExecutionMode = NodeExecutionModePolicy.MODE_ONE_SHOT
        activeSourceName = DEFAULT_SOURCE_NAME
        activeWorkingDirectory = null
        activeStartedAtEpochMs = 0L
        activeLifecycleStopRequestFile = null
    }

    companion object {

        private const val TAG = "NativeNodeEmbeddedScriptService"
        private val CLIENT_PROCESS_SETTLE_EXECUTOR: ScheduledExecutorService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "AutoJs6-Node-ProcessSettle").apply { isDaemon = true }
            }
        }
        private val ACTIVE_EXECUTION_ID = AtomicReference<String?>(null)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private val EXECUTION_QUEUE_LOCK = Object()
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private val DEFAULT_PROCESS_POOL_LOCK = Object()
        private val QUEUED_EXECUTION_IDS = ArrayDeque<String>()
        private val CANCELLED_QUEUED_EXECUTION_IDS = mutableSetOf<String>()
        private val DEFAULT_PROCESS_POOL_BUSY_SLOT_IDS = linkedSetOf<String>()
        private val DEFAULT_PROCESS_POOL_SLOT_COOLDOWN_UNTIL_MS = linkedMapOf<String, Long>()
        private val CLIENT_RUNNING_SCRIPT_COMPONENTS = ConcurrentHashMap<String, ComponentName>()
        private val CLIENT_RUNNING_SCRIPT_SESSION_LEASES =
            ConcurrentHashMap<String, NodeEmbeddedProcessSessionManager.SessionLease>()
        private val CLIENT_MODULE_SOURCE_PROVIDERS =
            ConcurrentHashMap<String, INodeJsModuleSourceProvider>()
        private val CLIENT_ACTIVE_EXECUTION_IDS = ConcurrentHashMap.newKeySet<String>()
        private val CLIENT_CANCELLED_EXECUTION_IDS = ConcurrentHashMap.newKeySet<String>()
        private val CLIENT_LONG_RUNNING_RELEASE_CALLBACKS =
            ConcurrentHashMap<String, (NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?, String) -> Unit>()
        private val CLIENT_EXECUTION_LIFECYCLE_LOCK = Any()
        private const val ACTION_PREWARM_RUNTIME = "org.autojs.autojs.engine.action.PREWARM_NATIVE_NODE_RUNTIME"
        private const val ACTION_RUN_SCRIPT = "org.autojs.autojs.engine.action.RUN_NATIVE_NODE_EMBEDDED_SCRIPT"
        private const val ACTION_STOP_INTERACTIVE = NodeInteractiveLongRunningStatusStore.STOP_ACTION_NAME
        private const val ACTION_SIMULATE_FOREGROUND_TIMEOUT =
            "org.autojs.autojs.engine.action.SIMULATE_NATIVE_NODE_FOREGROUND_TIMEOUT"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_SOURCE_NAME = "source_name"
        private const val EXTRA_WORKING_DIRECTORY = "working_directory"
        private const val EXTRA_SANDBOX_ROOT = "sandbox_root"
        private const val EXTRA_EXECUTION_MODE = "execution_mode"
        private const val EXTRA_LAUNCH_SURFACE = "launch_surface"
        private const val EXTRA_TIMEOUT_MS = "timeout_ms"
        private const val EXTRA_ESM_EXPERIMENTAL_ENABLED = "esm_experimental_enabled"
        private const val EXTRA_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED = "dynamic_import_experimental_enabled"
        private const val EXTRA_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED =
            "raw_node_network_modules_experimental_enabled"
        private const val EXTRA_WORKER_THREADS_EXPERIMENTAL_ENABLED = "worker_threads_experimental_enabled"
        private const val EXTRA_CHILD_PROCESS_EXPERIMENTAL_ENABLED = "child_process_experimental_enabled"
        private const val EXTRA_JAVA_INTEROP_EXPERIMENTAL_ENABLED = "java_interop_experimental_enabled"
        private const val EXTRA_SPAWN_DEPTH = "spawn_depth"
        private const val EXTRA_RUNTIME_ADAPTER = "runtime_adapter"
        private const val EXTRA_PROCESS_POOL_SLOT = "process_pool_slot"
        private const val EXTRA_PROCESS_POOL_SLOT_SOURCE = "process_pool_slot_source"
        private const val EXTRA_EXECUTION_ID = "execution_id"
        private const val EXTRA_STOP_SERVICE_CLASS = "stop_service_class"
        private const val EXTRA_FOREGROUND_SERVICE_TYPE = "foreground_service_type"
        private const val EXTRA_PREWARM_TTL_MS = "prewarm_ttl_ms"
        private const val EXTRA_ENV_NAMES = "env_names"
        private const val EXTRA_ENV_VALUES = "env_values"
        private const val EXTRA_MODULE_SOURCE_NAMES = "module_source_names"
        private const val EXTRA_MODULE_SOURCES = "module_sources"
        private const val EXTRA_RUNTIME_MODULE_SOURCE_NAMES = "runtime_module_source_names"
        private const val EXTRA_RUNTIME_MODULE_SOURCES = "runtime_module_sources"
        private const val EXTRA_REQUEST_SPOOL_FILE = "request_spool_file"
        private const val EXTRA_MODULE_SOURCE_PROVIDER = "module_source_provider"
        private const val EXTRA_MODULE_SOURCE_PROVIDER_BINDER = "binder"
        private const val HOST_APP_INFO_RUNTIME_MODULE_NAME = "autojs6:host-app-info"
        private const val DEVICE_INFO_RUNTIME_MODULE_NAME = "autojs6:device-info"
        private const val ENGINE_INFO_RUNTIME_MODULE_NAME = "autojs6:engine-info"
        private const val BRIDGE_LIVE_CONFIG_RUNTIME_MODULE_NAME = "autojs6:bridge-live-config"
        private const val MODULE_SOURCE_PROVIDER_RUNTIME_MODULE_NAME = "autojs6:module-source-provider"
        private const val REQUEST_SPOOL_DIR = "embedded-node-script-requests"
        private const val REQUEST_SPOOL_FILE_NAME = "request.json"
        private const val REQUEST_SPOOL_VERSION = 1
        private const val RESULT_SCRIPT = 1
        private const val RESULT_PREWARM = 2
        private const val NOTIFICATION_ID = 0xA6E4
        private const val INTERACTIVE_CONTENT_REQUEST_CODE = 0xA6E5
        private const val INTERACTIVE_STOP_REQUEST_CODE = 0xA6E6
        private const val PROCESS_EXIT_DELAY_MS = 250L
        private const val RUNTIME_CONTROL_TIMEOUT_MS = 15_000L
        private const val DEFAULT_PREWARM_TTL_MS = 5_000L
        private const val MAX_PREWARM_TTL_MS = 30_000L
        private const val DEFAULT_INTERACTIVE_TIMEOUT_MS = 3_600_000L
        private const val CLIENT_PROCESS_SETTLE_MS = 650L
        private const val LEGACY_RETIREMENT_RETRY_DELAY_MS = CLIENT_PROCESS_SETTLE_MS + 100L
        private const val BRIDGE_DISPATCH_WAIT_MS = 1000L
        private const val BRIDGE_LIVE_POLL_INTERVAL_MS = 10L
        private const val BRIDGE_LIVE_STOP_JOIN_MS = 1000L
        private const val BRIDGE_LIVE_DRAIN_ITERATIONS = 25
        private const val HEARTBEAT_STOP_JOIN_MS = 250L
        private const val MODULE_SOURCE_PROVIDER_DIAGNOSTICS_TIMEOUT_MS = 250L
        private const val MODULE_SOURCE_PROVIDER_DESCRIPTOR =
            "org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider"
        private const val MODULE_SOURCE_PROVIDER_DIAGNOSTIC_PREFIX =
            "embedded_script.module_provider."
        private const val RESULT_TRANSPORT_STRING_MAX_CHARS = 32_768
        private const val RESULT_TRANSPORT_NATIVE_PAYLOAD_MAX_CHARS = 65_536
        private const val RESULT_TRANSPORT_NATIVE_PAYLOAD_MAX_ENTRIES = 512
        private const val RESULT_TRANSPORT_METADATA_RESERVED_CHARS = 8_192
        private const val RESULT_TRANSPORT_METADATA_RESERVED_ENTRIES = 24
        private const val RESULT_TRANSPORT_OMITTED_KEYS_MAX_CHARS = 4_096
        private const val CLIENT_SERVICE_START_GRACE_MS = 10_000L
        private const val DEFAULT_PROCESS_POOL_STOP_COOLDOWN_MS = 10_000L
        private const val TIMEOUT_EXIT_CODE = 124
        private const val TIMEOUT_ERROR_CODE = NodeRuntimeErrorCodes.LEGACY_SCRIPT_TIMEOUT
        private const val DEFAULT_SOURCE_NAME = "<embedded-script>"
        private const val DEFAULT_TIMEOUT_MS = NativeNodeEmbeddedRuntimeBridge.DEFAULT_EMBEDDED_SCRIPT_TIMEOUT_MS
        private val RESULT_TRANSPORT_NATIVE_PAYLOAD_DEDICATED_KEYS = setOf(
            "embedded_script.result_json",
            "embedded_script.result_text",
            "embedded_script.error_message",
            "embedded_script.error_stack",
            "embedded_script.stdout",
            "embedded_script.stderr",
            "embedded_script.bridge_requests_json",
            "stdout_capture.text",
            "stdout_capture.text.hex",
            "stderr_capture.text",
            "stderr_capture.text.hex",
        )
        private val RESULT_TRANSPORT_NATIVE_PAYLOAD_PRIORITY_KEYS = listOf(
            NodeEmbeddedProcessSessionManager.CLIENT_PHASE_TIMING_SCHEMA_KEY,
            NodeEmbeddedProcessSessionManager.CLIENT_PHASE_TIMING_JSON_KEY,
            "embedded_script.phase_timing.schema",
            "embedded_script.phase_timing.status",
            "embedded_script.phase_timing.count",
            "embedded_script.phase_timing.source",
            "embedded_script.phase_timing.adapter_transferred_count",
            "embedded_script.native_payload_compaction.applied",
            "embedded_script.native_payload_compaction.policy",
            "embedded_script.native_payload_compaction.original_entry_count",
            "embedded_script.native_payload_compaction.omitted_entry_count",
            "embedded_script.native_payload_compaction.sent_entry_count",
            "embedded_script.probe_payload_generation.mode",
            "embedded_script.probe_payload_generation.omitted_optional_placeholders",
            "embedded_script.execution_source.bytes",
            "embedded_script.dynamic_require_fallback_loaded_count",
            "embedded_script.dynamic_require_fallback_source_bytes",
            "embedded_script.dynamic_require_fallback_denied_count",
            "embedded_script.dynamic_require_fallback_last_request",
            "embedded_script.dynamic_require_fallback_last_referrer",
            "embedded_script.dynamic_require_fallback_last_candidate",
            "embedded_script.dynamic_require_fallback_last_denied_reason",
            "embedded_script.module_provider.request_count",
            "embedded_script.module_provider.resolved_source_count",
            "embedded_script.module_provider.decrypted_source_count",
            "embedded_script.module_provider.source_bytes",
            "embedded_script.module_provider.elapsed_ms",
            "embedded_script.module_provider.denial_reason",
            "embedded_script.module_provider.legacy_directory_scan_ran",
            "embedded_script.module_provider.transport_request_count",
            "embedded_script.module_provider.transport_failure_count",
            "process_runtime.ensure.status",
            "symbol.count",
            "initialize.status",
            "platform.create.status",
            "v8.initialize_platform.status",
            "v8.initialize.status",
            "uv.loop.init.status",
            "allocator.create.status",
            "isolate.create.status",
            "isolate_data.create.status",
            "environment.create.status",
            "load_environment.status",
            "embedded_script.phase.bootstrap.status",
            "embedded_script.phase.module_preload.status",
            "embedded_script.phase.script_execution.status",
            "bootstrap_script.status",
            "spin_event_loop.status",
            "completion_drain.status",
            "embedded_script.status",
            "js_result.status",
            "output_envelope.status",
            "stdout_capture.status",
            "environment.free.status",
            "isolate_data.free.status",
            "isolate.unregister.status",
            "isolate.dispose.status",
            "uv.run.status",
            "uv.close.status",
            "uv.loop.close.status",
            "teardown.status",
            "process_runtime.shutdown.status",
            "timing.execution_source_build.ms",
            "timing.process_runtime_initialize.ms",
            "load.duration.ms",
            "timing.load.ms",
            "timing.symbols.ms",
            "timing.initialize.ms",
            "timing.platform_create.ms",
            "timing.v8_initialize_platform.ms",
            "timing.v8_initialize.ms",
            "timing.uv_loop_init.ms",
            "timing.allocator_create.ms",
            "timing.isolate_create.ms",
            "timing.isolate_data_create.ms",
            "timing.environment_create.ms",
            "timing.load_environment.ms",
            "timing.bootstrap_script.ms",
            "timing.bootstrap.ms",
            "timing.module_preload.ms",
            "timing.script_execution.ms",
            "timing.spin_event_loop.ms",
            "timing.completion_drain.ms",
            "timing.embedded_script_result.ms",
            "timing.js_result.ms",
            "timing.output_envelope.ms",
            "timing.stdout_capture.ms",
            "timing.environment_free.ms",
            "timing.isolate_data_free.ms",
            "timing.isolate_unregister.ms",
            "timing.isolate_dispose.ms",
            "timing.uv_run.ms",
            "timing.uv_close.ms",
            "timing.uv_loop_close.ms",
            "timing.teardown.ms",
            "timing.process_runtime_shutdown.ms",
            "timing.total.ms",
            "process_runtime.state",
            "process_runtime.persistent_enabled",
            "process_runtime.healthy",
            "process_runtime.poisoned",
            "process_runtime.poison_reason",
            "process_runtime.teardown_clean",
            "process_runtime.generation",
            "process_runtime.init_count",
            "process_runtime.execution_sequence",
            "execution.teardown_clean",
            "execution.teardown_poison_reason",
            "embedded_script.process_session.enabled",
            "embedded_script.process_session.status",
            "embedded_script.process_session.state",
            "embedded_script.process_session.slot_id",
            "embedded_script.process_session.generation",
            "embedded_script.process_session.manager_epoch",
            "embedded_script.process_session.quarantined",
            "embedded_script.process_session.process_exit_confirmed",
            NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZED_DURING_ACQUIRE_KEY,
            "embedded_script.prewarm_pool.status",
            "embedded_script.prewarm_pool.consumed",
            "embedded_script.prewarm_pool.process_only",
            "embedded_script.prewarm_pool.runtime_loaded_before_execution",
            "embedded_script.compile_cache.requested",
            "embedded_script.compile_cache.enabled",
            "embedded_script.compile_cache.status",
            "embedded_script.compile_cache.prepare_status",
            "embedded_script.compile_cache.prune_status",
            "embedded_script.compile_cache.schema",
            "embedded_script.compile_cache.directory",
            "timing.compile_cache_prepare.ms",
            "timing.compile_cache_prune.ms",
            "embedded_script.native_execution_entered",
            "embedded_script.runtime_state",
            "embedded_script.lifecycle_transitions",
            "embedded_script.source_name",
            "embedded_script.working_directory",
            "embedded_script.sandbox_root",
            "embedded_script.current_working_directory",
            "embedded_script.esm_enabled",
            "embedded_script.esm_entry",
            "embedded_script.esm_module_graph_root",
            "embedded_script.dynamic_import_enabled",
            "embedded_script.package_type_module_policy",
            "embedded_script.console_events_json",
            "embedded_script.console_event_count",
            "embedded_script.console_events_truncated",
            "embedded_script.bridge_resource.active_count",
            "embedded_script.bridge_live_dispatch_pending_count",
            "embedded_script.service.cleanup_clean",
            "embedded_script.service.cleanup_errors",
            "embedded_script.service.result_delivery_phase",
            "embedded_script.service.execution_slot_released",
            "embedded_script.service.queue_idle_after_release",
            "embedded_script.service.retirement_requested",
            "embedded_script.service.retirement_reason",
            "embedded_script.execution_id",
            "embedded_script.crash_evidence.marker_schema",
            "embedded_script.crash_evidence.marker_id",
            "embedded_script.crash_evidence.marker_path",
            "embedded_script.crash_evidence.automatic_collection",
        )
        private const val EXTRA_PREWARM_STARTED = "prewarm_started"
        private const val EXTRA_PREWARM_STATUS = "prewarm_status"
        private const val EXTRA_PREWARM_REASON = "prewarm_reason"
        private const val EXTRA_PREWARM_STARTED_AT_ELAPSED_MS = "prewarm_started_at_elapsed_ms"
        private const val EXTRA_PREWARM_PROCESS_NAME = "prewarm_process_name"
        private const val EXTRA_PREWARM_PID = "prewarm_pid"
        private const val EXTRA_PREWARM_PROCESS_ONLY = "prewarm_process_only"
        private const val EXTRA_PREWARM_RUNTIME_LOADED = "prewarm_runtime_loaded"
        private const val EXTRA_PREWARM_NATIVE_PAYLOAD = "prewarm_native_payload"
        private val TIMER_SOURCE_HINT_PATTERN = Regex("""\bset(?:Timeout|Interval|Immediate)\s*\(""")
        private val FS_CALLBACK_SOURCE_HINT_PATTERN = Regex("""\bfs\.[A-Za-z_$][\w$]*\s*\([^)]*,\s*(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)""")
        private val FS_STREAM_SOURCE_HINT_PATTERN = Regex("""\b(?:fs|nodeFs)\.create(?:Read|Write)Stream\s*\(""")
        private val CRYPTO_CALLBACK_SOURCE_HINT_PATTERN = Regex("""\bcrypto\.[A-Za-z_$][\w$]*\s*\([^)]*,\s*(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)""")
        private val DNS_CALLBACK_SOURCE_HINT_PATTERN = Regex("""\bdns\.[A-Za-z_$][\w$]*\s*\([^)]*,\s*(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)""")
        private val ZLIB_CALLBACK_SOURCE_HINT_PATTERN = Regex("""\bzlib\.[A-Za-z_$][\w$]*\s*\([^)]*,\s*(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)""")
        private val ZLIB_STREAM_SOURCE_HINT_PATTERN = Regex("""\bzlib\.create(?:Gzip|Gunzip|Deflate|Inflate|DeflateRaw|InflateRaw|BrotliCompress|BrotliDecompress)\s*\(""")

        fun enqueueScript(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val executionId = request.executionId?.takeIf { it.isNotBlank() } ?: newExecutionId()
            val queueTimeoutMs = NodeExecutionQueuePolicy.normalizedQueueTimeoutMs(
                NodeExecutionQueuePolicy.MODE_ENQUEUE_WHEN_BUSY,
                request.queueTimeoutMs,
            )
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val slotDecision = acquireExecutionSlot(
                executionId = executionId,
                queueMode = NodeExecutionQueuePolicy.MODE_ENQUEUE_WHEN_BUSY,
                queueTimeoutMs = queueTimeoutMs,
            )
            if (slotDecision is ExecutionSlotDecision.Rejected) {
                return clientQueueFailureResult(
                    failure = slotDecision.failure,
                    executionId = executionId,
                    request = request,
                    startedAt = startedAt,
                )
            }
            val slotLease = (slotDecision as ExecutionSlotDecision.Acquired).lease
            var processSettleDeferred = false
            val scriptResult = try {
                runScriptAttempt(
                    context,
                    request.copy(
                        executionId = executionId,
                        queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                        queueTimeoutMs = 0L,
                    ),
                    launchSurface,
                ).withClientQueueLease(slotLease)
            } finally {
                processSettleDeferred = if (NodeEmbeddedProcessSessionManager.isEnabled()) {
                    releaseExecutionSlot(executionId)
                    false
                } else {
                    deferProcessSettle {
                        releaseExecutionSlot(executionId)
                    }
                }
            }
            return scriptResult.withDeferredProcessSettle(processSettleDeferred)
        }

        fun queueStatus(
            context: Context,
            timeoutMs: Long = NodeExecutionQueuePolicy.CONTROL_TIMEOUT_MS,
        ): NodeExecutionQueueStatus {
            return queueStatusSnapshot()
        }

        fun cancelQueuedExecution(
            context: Context,
            executionId: String,
            timeoutMs: Long = NodeExecutionQueuePolicy.CONTROL_TIMEOUT_MS,
        ): Boolean {
            return cancelQueuedExecutionInService(executionId)
        }

        data class PrewarmRuntimeResult(
            val started: Boolean,
            val status: String,
            val reason: String,
            val ttlMs: Long,
            val processName: String,
            val pid: Int,
            val processOnly: Boolean,
            val runtimeLoaded: Boolean,
            val nativeValues: Map<String, String>,
        )

        fun prewarmRuntime(
            context: Context,
            ttlMs: Long = DEFAULT_PREWARM_TTL_MS,
            timeoutMs: Long = NodeExecutionQueuePolicy.CONTROL_TIMEOUT_MS,
        ): PrewarmRuntimeResult {
            val appContext = context.applicationContext
            if (NodeEmbeddedProcessSessionManager.isEnabled()) {
                val prewarm = NodeEmbeddedProcessSessionManager.prewarm(
                    context = appContext,
                    ttlMs = normalizedPrewarmTtlMs(ttlMs),
                    timeoutMs = timeoutMs,
                )
                return PrewarmRuntimeResult(
                    started = prewarm.started,
                    status = prewarm.status,
                    reason = prewarm.reason,
                    ttlMs = normalizedPrewarmTtlMs(ttlMs),
                    processName = prewarm.processName,
                    pid = prewarm.pid,
                    processOnly = false,
                    runtimeLoaded = prewarm.started,
                    nativeValues = linkedMapOf<String, String>().apply {
                        putAll(prewarm.nativeValues)
                        put("embedded_script.prewarm_pool.enabled", "true")
                        put("embedded_script.prewarm_pool.status", prewarm.status)
                        put("embedded_script.prewarm_pool.reason", prewarm.reason)
                        put("embedded_script.prewarm_pool.process_only", "false")
                        put("embedded_script.prewarm_pool.runtime_loaded_before_execution", prewarm.started.toString())
                        put("embedded_script.prewarm_pool.ttl_ms", normalizedPrewarmTtlMs(ttlMs).toString())
                    },
                )
            }
            val completion = CountDownLatch(1)
            val result = AtomicReference<PrewarmRuntimeResult>()
            val receiver = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode != RESULT_PREWARM) {
                        return
                    }
                    result.set(resultData.toPrewarmRuntimeResult())
                    completion.countDown()
                }
            }
            val intent = Intent(appContext, NativeNodeEmbeddedScriptService::class.java).apply {
                action = ACTION_PREWARM_RUNTIME
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
                putExtra(EXTRA_PREWARM_TTL_MS, normalizedPrewarmTtlMs(ttlMs))
            }
            val startOutcome = startEmbeddedScriptService(appContext, intent)
            if (startOutcome.component == null) {
                return PrewarmRuntimeResult(
                    started = false,
                    status = "start_failed",
                    reason = startOutcome.diagnostics.optString("message").ifBlank { "service_start_failed" },
                    ttlMs = normalizedPrewarmTtlMs(ttlMs),
                    processName = currentProcessNameCompat(),
                    pid = Process.myPid(),
                    processOnly = true,
                    runtimeLoaded = false,
                    nativeValues = startOutcome.nativeValues(),
                )
            }
            val completed = runCatching {
                completion.await(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            }.getOrElse { error ->
                if (error is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                false
            }
            if (!completed) {
                return PrewarmRuntimeResult(
                    started = false,
                    status = "timeout",
                    reason = "prewarm_result_timeout",
                    ttlMs = normalizedPrewarmTtlMs(ttlMs),
                    processName = currentProcessNameCompat(),
                    pid = Process.myPid(),
                    processOnly = true,
                    runtimeLoaded = false,
                    nativeValues = mapOf(
                        "embedded_script.prewarm_pool.enabled" to "true",
                        "embedded_script.prewarm_pool.status" to "timeout",
                        "embedded_script.prewarm_pool.reason" to "prewarm_result_timeout",
                    ),
                )
            }
            return result.get() ?: PrewarmRuntimeResult(
                started = false,
                status = "missing_result",
                reason = "prewarm_result_missing",
                ttlMs = normalizedPrewarmTtlMs(ttlMs),
                processName = currentProcessNameCompat(),
                pid = Process.myPid(),
                processOnly = true,
                runtimeLoaded = false,
                nativeValues = emptyMap(),
            )
        }

        fun runScript(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val stableRequest = if (request.executionId.isNullOrBlank()) {
                request.copy(executionId = newExecutionId())
            } else {
                request
            }.withHostModuleSourceProvider()
            val executionId = stableRequest.executionId.orEmpty()
            val startedAt = android.os.SystemClock.elapsedRealtime()
            if (!beginClientExecution(executionId, stableRequest.moduleSourceProvider)) {
                return clientExecutionCancelledResult(
                    request = stableRequest,
                    startedAt = startedAt,
                    executionId = executionId,
                    stage = "provider_registration",
                )
            }
            var retryProvider: INodeJsModuleSourceProvider? = null
            return try {
                if (isClientExecutionCancelled(executionId)) {
                    return clientExecutionCancelledResult(
                        stableRequest,
                        startedAt,
                        executionId,
                        "after_provider_registration",
                    )
                }
                val first = runScriptAttempt(context, stableRequest, launchSurface)
                if (!shouldRetryLegacyRetirement(first)) {
                    return first
                }
                if (isClientExecutionCancelled(executionId)) {
                    return clientExecutionCancelledResult(
                        stableRequest,
                        startedAt,
                        executionId,
                        "before_legacy_retirement_retry",
                    )
                }
                try {
                    Thread.sleep(LEGACY_RETIREMENT_RETRY_DELAY_MS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return first
                }
                val retryRequest = stableRequest
                    .copy(moduleSourceProvider = null)
                    .withHostModuleSourceProvider()
                retryProvider = retryRequest.moduleSourceProvider
                if (!replaceClientModuleSourceProvider(executionId, retryProvider)) {
                    return clientExecutionCancelledResult(
                        retryRequest,
                        startedAt,
                        executionId,
                        "legacy_retirement_retry_registration",
                    )
                }
                cancelClientModuleSourceProvider(
                    stableRequest.moduleSourceProvider,
                    "Embedded Node legacy-retirement first attempt finished.",
                )
                val retried = runScriptAttempt(context, retryRequest, launchSurface)
                retried.copy(
                    nativeValues = linkedMapOf<String, String>().apply {
                        putAll(retried.nativeValues)
                        put("embedded_script.client.legacy_retirement_retry", "true")
                        put(
                            "embedded_script.client.legacy_retirement_reason",
                            first.nativeValues["embedded_script.service.retirement_reason"].orEmpty(),
                        )
                    },
                )
            } finally {
                finishClientExecution(executionId, "Embedded Node request finished.")
                cancelClientModuleSourceProvider(
                    stableRequest.moduleSourceProvider,
                    "Embedded Node request finished.",
                )
                cancelClientModuleSourceProvider(
                    retryProvider,
                    "Embedded Node legacy-retirement retry finished.",
                )
            }
        }

        private data class ClientExecutionCancellation(
            val acknowledged: Boolean,
            val component: ComponentName? = null,
            val sessionReleaseRequested: Boolean = false,
        )

        private fun beginClientExecution(
            executionId: String,
            provider: INodeJsModuleSourceProvider?,
        ): Boolean {
            if (executionId.isBlank()) {
                return false
            }
            synchronized(CLIENT_EXECUTION_LIFECYCLE_LOCK) {
                if (!CLIENT_ACTIVE_EXECUTION_IDS.add(executionId)) {
                    return false
                }
                CLIENT_CANCELLED_EXECUTION_IDS.remove(executionId)
                provider?.let { CLIENT_MODULE_SOURCE_PROVIDERS[executionId] = it }
                return true
            }
        }

        private fun replaceClientModuleSourceProvider(
            executionId: String,
            provider: INodeJsModuleSourceProvider?,
        ): Boolean {
            synchronized(CLIENT_EXECUTION_LIFECYCLE_LOCK) {
                if (
                    executionId !in CLIENT_ACTIVE_EXECUTION_IDS ||
                    executionId in CLIENT_CANCELLED_EXECUTION_IDS
                ) {
                    return false
                }
                if (provider == null) {
                    CLIENT_MODULE_SOURCE_PROVIDERS.remove(executionId)
                } else {
                    CLIENT_MODULE_SOURCE_PROVIDERS[executionId] = provider
                }
                return true
            }
        }

        private fun isClientExecutionCancelled(executionId: String): Boolean =
            executionId in CLIENT_CANCELLED_EXECUTION_IDS

        private fun finishClientExecution(executionId: String, providerReason: String) {
            val provider = synchronized(CLIENT_EXECUTION_LIFECYCLE_LOCK) {
                CLIENT_LONG_RUNNING_RELEASE_CALLBACKS.remove(executionId)
                CLIENT_RUNNING_SCRIPT_COMPONENTS.remove(executionId)
                CLIENT_RUNNING_SCRIPT_SESSION_LEASES.remove(executionId)
                CLIENT_ACTIVE_EXECUTION_IDS.remove(executionId)
                CLIENT_CANCELLED_EXECUTION_IDS.remove(executionId)
                CLIENT_MODULE_SOURCE_PROVIDERS.remove(executionId)
            }
            cancelClientModuleSourceProvider(provider, providerReason)
        }

        private fun registerLongRunningReleaseCallback(
            executionId: String,
            lease: NodeEmbeddedProcessSessionManager.SessionLease?,
            callback: (NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?, String) -> Unit,
        ): Boolean {
            synchronized(CLIENT_EXECUTION_LIFECYCLE_LOCK) {
                if (
                    executionId !in CLIENT_ACTIVE_EXECUTION_IDS ||
                    executionId in CLIENT_CANCELLED_EXECUTION_IDS
                ) {
                    return false
                }
                CLIENT_LONG_RUNNING_RELEASE_CALLBACKS[executionId] = callback
                lease?.let { CLIENT_RUNNING_SCRIPT_SESSION_LEASES[executionId] = it }
                return true
            }
        }

        private fun cancelClientExecutionLifecycle(
            context: Context,
            executionId: String,
            providerReason: String,
            sessionReason: String,
        ): ClientExecutionCancellation {
            if (executionId.isBlank()) {
                return ClientExecutionCancellation(false)
            }
            var provider: INodeJsModuleSourceProvider? = null
            var component: ComponentName? = null
            var lease: NodeEmbeddedProcessSessionManager.SessionLease? = null
            var releaseCallback:
                    ((NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?, String) -> Unit)? = null
            val acknowledged = synchronized(CLIENT_EXECUTION_LIFECYCLE_LOCK) {
                if (executionId !in CLIENT_ACTIVE_EXECUTION_IDS) {
                    false
                } else {
                    CLIENT_CANCELLED_EXECUTION_IDS.add(executionId)
                    provider = CLIENT_MODULE_SOURCE_PROVIDERS.remove(executionId)
                    component = CLIENT_RUNNING_SCRIPT_COMPONENTS.remove(executionId)
                    lease = CLIENT_RUNNING_SCRIPT_SESSION_LEASES.remove(executionId)
                    releaseCallback = CLIENT_LONG_RUNNING_RELEASE_CALLBACKS.remove(executionId)
                    true
                }
            }
            if (!acknowledged) {
                return ClientExecutionCancellation(false)
            }
            cancelClientModuleSourceProvider(provider, providerReason)
            val sessionReleaseRequested = if (releaseCallback != null) {
                runCatching { releaseCallback.invoke(null, sessionReason) }
                true
            } else if (lease != null) {
                NodeEmbeddedProcessSessionManager.unregisterExecution(lease, executionId)
                NodeEmbeddedProcessSessionManager.release(
                    context = context.applicationContext,
                    lease = lease,
                    healthy = false,
                    reason = sessionReason,
                )
                true
            } else {
                false
            }
            cancelQueuedExecutionInService(executionId)
            synchronized(DEFAULT_PROCESS_POOL_LOCK) {
                DEFAULT_PROCESS_POOL_LOCK.notifyAll()
            }
            return ClientExecutionCancellation(
                acknowledged = true,
                component = component,
                sessionReleaseRequested = sessionReleaseRequested,
            )
        }

        private fun cancelClientModuleSourceProvider(
            provider: INodeJsModuleSourceProvider?,
            reason: String,
        ) {
            provider ?: return
            runCatching { provider.cancel(reason) }
                .onFailure { error -> Log.w(TAG, "module_source_provider.cancel.failed", error) }
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult
                .withHostModuleSourceProviderDiagnostics(
                    provider: INodeJsModuleSourceProvider?,
                ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            provider ?: return this
            return copy(
                nativeValues = linkedMapOf<String, String>().apply {
                    putAll(nativeValues)
                    putAll(hostModuleSourceProviderDiagnostics(provider))
                },
            )
        }

        private fun hostModuleSourceProviderDiagnostics(
            provider: INodeJsModuleSourceProvider,
        ): Map<String, String> {
            val diagnostics = runCatching {
                val localProvider = provider.asBinder()
                    .queryLocalInterface(MODULE_SOURCE_PROVIDER_DESCRIPTOR) != null
                if (localProvider) {
                    provider.nativeDiagnostics
                } else {
                    val task = FutureTask { provider.nativeDiagnostics }
                    Thread(task, "AutoJs6NodeModuleHostDiagnostics").apply {
                        isDaemon = true
                        start()
                    }
                    try {
                        task.get(MODULE_SOURCE_PROVIDER_DIAGNOSTICS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    } catch (error: TimeoutException) {
                        task.cancel(true)
                        throw error
                    }
                }
            }
            return diagnostics.fold(
                onSuccess = { bundle ->
                    bundle?.keySet()?.mapNotNull { key ->
                        if (!key.startsWith(MODULE_SOURCE_PROVIDER_DIAGNOSTIC_PREFIX)) {
                            return@mapNotNull null
                        }
                        (bundle.get(key) as? String)?.let { value -> key to value }
                    }?.toMap().orEmpty()
                },
                onFailure = { error ->
                    if (error is InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    mapOf(
                        "embedded_script.module_provider.diagnostics_failure" to when (error) {
                            is TimeoutException -> "timed_out"
                            is InterruptedException -> "interrupted"
                            else -> "failed"
                        },
                    )
                },
            )
        }

        private fun clientExecutionCancelledResult(
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            startedAt: Long,
            executionId: String,
            stage: String,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
                succeeded = false,
                exitCode = 1,
                resultText = null,
                stdout = "",
                stderr = "",
                errorName = "EmbeddedNodeExecutionCancelledError",
                errorMessage = "Embedded Node.js execution was cancelled before starting.",
                errorStack = null,
                errorCode = NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED,
                elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                processName = currentProcessNameCompat(),
                pid = Process.myPid(),
                sourceName = request.sourceName,
                nativeValues = linkedMapOf(
                    "embedded_script.error_code" to NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED,
                    "embedded_script.client.cancelled" to "true",
                    "embedded_script.client.cancellation_stage" to stage,
                    "embedded_script.execution_id" to executionId,
                    "embedded_script.native_execution_entered" to "false",
                ),
                workingDirectory = request.workingDirectory,
            )
        }

        private fun shouldRetryLegacyRetirement(
            result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        ): Boolean {
            if (NodeEmbeddedProcessSessionManager.isEnabled() || result.succeeded) {
                return false
            }
            val values = result.nativeValues
            if (values["embedded_script.client.legacy_retirement_retry"] == "true") {
                return false
            }
            val reason = values["embedded_script.service.retirement_reason"].orEmpty()
            return values["embedded_script.native_execution_entered"] == "false" &&
                    values["embedded_script.service.retirement_requested"] == "true" &&
                    (
                        reason == "ttl_expired" ||
                            reason.startsWith("trim_memory_") ||
                            reason.startsWith("low_memory_")
                    )
        }

        private fun runScriptAttempt(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            launchSurface: String,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val clientAttemptStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
            val clientAttemptStartedAt = android.os.SystemClock.elapsedRealtime()
            val appContext = context.applicationContext
            (appContext as? App)?.cancelDeferredNativeNodePrewarm()
            val executionId = request.executionId?.takeIf { it.isNotBlank() } ?: newExecutionId()
            val requestWithExecutionId = request.copy(executionId = executionId)
            if (isClientExecutionCancelled(executionId)) {
                return clientExecutionCancelledResult(
                    requestWithExecutionId,
                    clientAttemptStartedAt,
                    executionId,
                    "before_request_preparation",
                )
            }
            val requestForService = try {
                NativeNodeEmbeddedRuntimeBridge.prepareEmbeddedScriptRequest(requestWithExecutionId)
            } catch (error: Throwable) {
                return NativeNodeEmbeddedRuntimeBridge
                    .embeddedScriptRequestPreparationFailure(requestWithExecutionId, error)
                    .withLifecycle(preflightFailureLifecycle(executionId, requestWithExecutionId))
            }
            if (isClientExecutionCancelled(executionId)) {
                return clientExecutionCancelledResult(
                    requestForService,
                    clientAttemptStartedAt,
                    executionId,
                    "after_request_preparation",
                )
            }
            NativeNodeEmbeddedRuntimeBridge.validateEmbeddedScriptRequestBeforeServiceStart(
                request = requestForService,
                launchSurface = launchSurface,
                context = appContext,
            )?.let {
                return it.withLifecycle(preflightFailureLifecycle(executionId, request))
            }
            if (isClientExecutionCancelled(executionId)) {
                return clientExecutionCancelledResult(
                    requestForService,
                    clientAttemptStartedAt,
                    executionId,
                    "after_request_validation",
                )
            }
            val requestQueueMode = NodeExecutionQueuePolicy.normalizeMode(requestForService.queueMode)
            val defaultSchedulerEligible = requestForService.processPoolSlot.isNullOrBlank() &&
                    NodeEmbeddedProcessPoolPolicy.defaultSchedulerEligible(
                        context = appContext,
                        executionMode = requestForService.executionMode,
                        launchSurface = launchSurface,
                    )
            if (
                requestQueueMode == NodeExecutionQueuePolicy.MODE_ENQUEUE_WHEN_BUSY &&
                !defaultSchedulerEligible
            ) {
                return enqueueScript(
                    context = appContext,
                    request = requestForService,
                    launchSurface = launchSurface,
                )
            }
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val defaultSlotDecision = acquireDefaultProcessPoolSlot(
                context = appContext,
                executionId = executionId,
                request = requestForService,
                launchSurface = launchSurface,
            )
            if (defaultSlotDecision is DefaultProcessPoolSlotDecision.Rejected) {
                return defaultProcessPoolFailureResult(
                    context = appContext,
                    request = requestForService,
                    executionId = executionId,
                    startedAt = startedAt,
                    launchSurface = launchSurface,
                    failure = defaultSlotDecision.failure,
                    activeSlotIds = defaultSlotDecision.activeSlotIds,
                )
            }
            val defaultSlotLease = (defaultSlotDecision as? DefaultProcessPoolSlotDecision.Acquired)?.lease
            if (isClientExecutionCancelled(executionId)) {
                releaseDefaultProcessPoolSlot(defaultSlotLease)
                return clientExecutionCancelledResult(
                    requestForService,
                    startedAt,
                    executionId,
                    "after_process_pool_acquire",
                )
            }
            val scheduledRequestForService = defaultSlotLease?.let { lease ->
                requestForService.copy(
                    processPoolSlot = lease.slot.id,
                    processPoolSlotSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER,
                    queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                    queueTimeoutMs = 0L,
                )
            } ?: requestForService.copy(
                queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                queueTimeoutMs = 0L,
            )
            val completion = CountDownLatch(1)
            val result = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
            val resultReceivedAtElapsedMs = AtomicLong(0L)
            val receiver = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode != RESULT_SCRIPT) {
                        return
                    }
                    val resultPayloadDecodeStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
                    resultReceivedAtElapsedMs.compareAndSet(0L, android.os.SystemClock.elapsedRealtime())
                    val decodedResult = resultData.toEmbeddedScriptResult()
                    val resultPayloadDecodeMs = NodeEmbeddedProcessSessionManager.elapsedPhaseMs(
                        resultPayloadDecodeStartedAtNanos,
                    )
                    result.set(
                        decodedResult.withClientPhaseTimings(
                            mapOf(
                                NodeEmbeddedProcessSessionManager.PhaseTiming.RESULT_PAYLOAD_DECODE to
                                    resultPayloadDecodeMs,
                            ),
                        ),
                    )
                    completion.countDown()
                }
            }
            var requestSpoolFile: File? = null
            var requestSpoolPhaseMs = 0L
            var clientCleanupPhaseMs = 0L
            var clientFailureResult: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult? = null
            val intent = Intent(appContext, NativeNodeEmbeddedScriptService::class.java).apply {
                action = ACTION_RUN_SCRIPT
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
                putExtra(EXTRA_SOURCE_NAME, scheduledRequestForService.sourceName)
                putExtra(EXTRA_WORKING_DIRECTORY, scheduledRequestForService.workingDirectory)
                putExtra(EXTRA_SANDBOX_ROOT, scheduledRequestForService.sandboxRoot)
                putExtra(EXTRA_EXECUTION_MODE, scheduledRequestForService.executionMode)
                putExtra(EXTRA_LAUNCH_SURFACE, launchSurface)
                putExtra(EXTRA_TIMEOUT_MS, scheduledRequestForService.timeoutMs.coerceAtLeast(1L))
                putExtra(EXTRA_ESM_EXPERIMENTAL_ENABLED, scheduledRequestForService.esmExperimentalEnabled)
                putExtra(EXTRA_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED, scheduledRequestForService.dynamicImportExperimentalEnabled)
                putExtra(
                    EXTRA_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED,
                    scheduledRequestForService.rawNodeNetworkModulesExperimentalEnabled,
                )
                putExtra(EXTRA_WORKER_THREADS_EXPERIMENTAL_ENABLED, scheduledRequestForService.workerThreadsExperimentalEnabled)
                putExtra(EXTRA_CHILD_PROCESS_EXPERIMENTAL_ENABLED, scheduledRequestForService.childProcessExperimentalEnabled)
                putExtra(EXTRA_JAVA_INTEROP_EXPERIMENTAL_ENABLED, scheduledRequestForService.javaInteropExperimentalEnabled)
                putExtra(
                    EXTRA_SPAWN_DEPTH,
                    scheduledRequestForService.spawnDepth.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH),
                )
                putExtra(EXTRA_RUNTIME_ADAPTER, scheduledRequestForService.runtimeAdapter)
                putExtra(EXTRA_PROCESS_POOL_SLOT, scheduledRequestForService.processPoolSlot)
                putExtra(EXTRA_PROCESS_POOL_SLOT_SOURCE, scheduledRequestForService.processPoolSlotSource)
                putExtra(EXTRA_EXECUTION_ID, executionId)
                putModuleSourceProvider(scheduledRequestForService.moduleSourceProvider)
            }
            val processPoolPlan = NodeEmbeddedProcessPoolPolicy.plan(
                context = appContext,
                executionMode = scheduledRequestForService.executionMode,
                launchSurface = launchSurface,
                requestedSlotId = scheduledRequestForService.processPoolSlot,
                slotSelectionSource = scheduledRequestForService.processPoolSlotSource,
                defaultSchedulerEnabled = defaultSlotLease != null,
            )
            val processDeathReason = AtomicReference<String?>(null)
            val sessionAcquire = if (NodeEmbeddedProcessSessionManager.isEnabled()) {
                NodeEmbeddedProcessSessionManager.acquire(
                    context = appContext,
                    slot = processPoolPlan.selectedSlot,
                )
            } else {
                null
            }
            val processSessionLease = sessionAcquire?.lease
            if (isClientExecutionCancelled(executionId)) {
                clientFailureResult = clientExecutionCancelledResult(
                    scheduledRequestForService,
                    startedAt,
                    executionId,
                    "after_process_session_acquire",
                )
            } else if (sessionAcquire != null && processSessionLease == null) {
                clientFailureResult = processCrashedOrTimedOut(
                    request = scheduledRequestForService,
                    message = sessionAcquire.errorMessage.orEmpty().ifBlank {
                        "Failed to acquire persistent embedded Node process session."
                    },
                    startedAt = startedAt,
                    executionId = executionId,
                    lifecycleValues = sessionAcquire.nativeValues,
                )
            }
            var component: android.content.ComponentName? = null
            var processSettleDeferred = false
            if (clientFailureResult == null && processSessionLease != null) {
                val registered = NodeEmbeddedProcessSessionManager.registerExecution(
                    processSessionLease,
                    executionId,
                ) {
                    processDeathReason.compareAndSet(null, "persistent_process_binder_died")
                    completion.countDown()
                }
                if (!registered) {
                    processDeathReason.compareAndSet(null, "persistent_process_lease_invalidated")
                    clientFailureResult = processCrashedOrTimedOut(
                        request = scheduledRequestForService,
                        message = "Persistent embedded Node process lease was invalidated before execution registration.",
                        startedAt = startedAt,
                        executionId = executionId,
                        lifecycleValues = processSessionLease.nativeValues("registration_failed"),
                    )
                } else {
                    val leaseTracked = synchronized(CLIENT_EXECUTION_LIFECYCLE_LOCK) {
                        if (isClientExecutionCancelled(executionId)) {
                            false
                        } else {
                            CLIENT_RUNNING_SCRIPT_SESSION_LEASES[executionId] = processSessionLease
                            true
                        }
                    }
                    if (!leaseTracked) {
                        clientFailureResult = clientExecutionCancelledResult(
                            scheduledRequestForService,
                            startedAt,
                            executionId,
                            "after_process_session_registration",
                        )
                    }
                }
            }
            try {
                if (clientFailureResult == null) {
                    val requestSpoolStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
                    requestSpoolFile = writeEmbeddedScriptRequestSpool(appContext, executionId, scheduledRequestForService)
                    requestSpoolPhaseMs = NodeEmbeddedProcessSessionManager.elapsedPhaseMs(requestSpoolStartedAtNanos)
                    intent.putExtra(EXTRA_REQUEST_SPOOL_FILE, requestSpoolFile.absolutePath)
                    var startCancelled = false
                    val startOutcome = synchronized(CLIENT_EXECUTION_LIFECYCLE_LOCK) {
                        val outcome = if (isClientExecutionCancelled(executionId)) {
                            startCancelled = true
                            null
                        } else if (processSessionLease != null) {
                            NodeEmbeddedProcessSessionManager.startRegisteredExecution(
                                processSessionLease,
                                executionId,
                            ) {
                                startEmbeddedScriptService(
                                    appContext,
                                    intent,
                                    serviceClass = processPoolPlan.selectedSlot.serviceClass,
                                )
                            }
                        } else {
                            startEmbeddedScriptService(
                                appContext,
                                intent,
                                serviceClass = processPoolPlan.selectedSlot.serviceClass,
                            )
                        }
                        outcome?.component?.let { startedComponent ->
                            component = startedComponent
                            CLIENT_RUNNING_SCRIPT_COMPONENTS[executionId] = startedComponent
                            processSessionLease?.let {
                                CLIENT_RUNNING_SCRIPT_SESSION_LEASES[executionId] = it
                            }
                        }
                        outcome
                    }
                    if (startCancelled) {
                        clientFailureResult = clientExecutionCancelledResult(
                            scheduledRequestForService,
                            startedAt,
                            executionId,
                            "before_service_start",
                        )
                    } else if (startOutcome == null) {
                        processDeathReason.compareAndSet(null, "persistent_process_lease_invalidated_before_start")
                        clientFailureResult = processCrashedOrTimedOut(
                            request = scheduledRequestForService,
                            message = "Persistent embedded Node process lease was invalidated before service start.",
                            startedAt = startedAt,
                            executionId = executionId,
                            lifecycleValues = processSessionLease?.nativeValues("start_invalidated").orEmpty(),
                        )
                    } else {
                        if (startOutcome.component == null) {
                            clientFailureResult = serviceStartFailureResult(
                                request = request,
                                message = "Failed to start embedded script service.",
                                startedAt = startedAt,
                                executionId = executionId,
                                startOutcome = startOutcome,
                            )
                        } else {
                            val completed = completion.await(
                                clientAwaitTimeoutMs(
                                    request = scheduledRequestForService,
                                    includeQueueTimeout = defaultSlotLease == null,
                                ),
                                TimeUnit.MILLISECONDS,
                            )
                            if (!completed || processDeathReason.get() != null) {
                                coolDownDefaultProcessPoolSlot(
                                    defaultSlotLease,
                                    processDeathReason.get() ?: "client.timeout",
                                )
                                appContext.stopService(Intent().setComponent(component))
                                val lifecycle = timeoutLifecycle(executionId, scheduledRequestForService)
                                clientFailureResult = processCrashedOrTimedOut(
                                    request = scheduledRequestForService,
                                    message = if (processDeathReason.get() != null) {
                                        "Embedded script process Binder died during execution."
                                    } else {
                                        "Embedded script process crashed or timed out."
                                    },
                                    startedAt = startedAt,
                                    timedOut = processDeathReason.get() == null,
                                    executionId = executionId,
                                    lifecycleValues = lifecycle.nativeValues(),
                                )
                            }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                coolDownDefaultProcessPoolSlot(defaultSlotLease, "client.interrupted")
                component?.let { appContext.stopService(Intent().setComponent(it)) }
                Thread.currentThread().interrupt()
                throw ScriptInterruptedException(e)
            } catch (e: Throwable) {
                coolDownDefaultProcessPoolSlot(defaultSlotLease, "client.throwable")
                component?.let { appContext.stopService(Intent().setComponent(it)) }
                clientFailureResult = processCrashedOrTimedOut(
                    scheduledRequestForService,
                    "Embedded script process crashed or timed out.\n${e.message ?: e.javaClass.name}",
                    e,
                    startedAt = startedAt,
                    executionId = executionId,
                )
            } finally {
                val cleanupStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
                component?.let { CLIENT_RUNNING_SCRIPT_COMPONENTS.remove(executionId, it) }
                CLIENT_RUNNING_SCRIPT_SESSION_LEASES.remove(executionId)
                NodeEmbeddedProcessSessionManager.unregisterExecution(processSessionLease, executionId)
                requestSpoolFile?.parentFile?.deleteRecursivelyIfExists()
                val receivedResult = clientFailureResult ?: result.get()
                val sessionHealthy = processDeathReason.get() == null &&
                        receivedResult?.let { NodeEmbeddedProcessSessionManager.resultIsReusable(it.nativeValues) } != false
                NodeEmbeddedProcessSessionManager.release(
                    context = appContext,
                    lease = processSessionLease,
                    healthy = sessionHealthy,
                    reason = processDeathReason.get() ?: if (sessionHealthy) "execution_complete" else "execution_teardown_unhealthy",
                )
                if (!NodeEmbeddedProcessSessionManager.isEnabled() && defaultSlotLease != null && component != null) {
                    processSettleDeferred = deferProcessSettle {
                        releaseDefaultProcessPoolSlot(defaultSlotLease)
                    }
                } else {
                    releaseDefaultProcessPoolSlot(defaultSlotLease)
                }
                clientCleanupPhaseMs = NodeEmbeddedProcessSessionManager.elapsedPhaseMs(cleanupStartedAtNanos)
            }
            val scriptResult = clientFailureResult ?: result.get() ?: processCrashedOrTimedOut(
                request = scheduledRequestForService,
                message = "Embedded script process crashed or timed out.",
                startedAt = startedAt,
                executionId = executionId,
            )
            return scriptResult
                .withClientPhaseTimings(
                    linkedMapOf(
                        NodeEmbeddedProcessSessionManager.PhaseTiming.REQUEST_SPOOL to requestSpoolPhaseMs,
                        NodeEmbeddedProcessSessionManager.PhaseTiming.QUEUE to
                            (defaultSlotLease?.queueWaitMs ?: 0L),
                        NodeEmbeddedProcessSessionManager.PhaseTiming.CLEANUP to clientCleanupPhaseMs,
                    ),
                )
                .withProcessPoolPlan(processPoolPlan)
                .withDefaultProcessPoolLease(defaultSlotLease)
                .withProcessSessionLease(processSessionLease)
                .withClientResultReturnTiming(resultReceivedAtElapsedMs.get())
                .withDeferredProcessSettle(processSettleDeferred)
                .withHostModuleSourceProviderDiagnostics(scheduledRequestForService.moduleSourceProvider)
                .withClientPhaseTimings(
                    mapOf(
                        NodeEmbeddedProcessSessionManager.PhaseTiming.CLIENT_ATTEMPT_TOTAL to
                            NodeEmbeddedProcessSessionManager.elapsedPhaseMs(clientAttemptStartedAtNanos),
                    ),
                )
        }

        fun cancelRunningScript(context: Context, executionId: String): Boolean {
            val cancellation = cancelClientExecutionLifecycle(
                context = context,
                executionId = executionId,
                providerReason = "Embedded Node request was cancelled by the client.",
                sessionReason = "client_cancelled_active_execution",
            )
            if (!cancellation.acknowledged) {
                return false
            }
            val component = cancellation.component ?: return true
            val serviceStopped = runCatching {
                context.applicationContext.stopService(Intent().setComponent(component))
            }.onSuccess {
                Log.i(TAG, "client.cancel requested executionId=$executionId component=${component.flattenToShortString()}")
            }.onFailure {
                Log.w(TAG, "Failed to cancel embedded script executionId=$executionId.", it)
            }.getOrDefault(false)
            return serviceStopped || cancellation.sessionReleaseRequested
        }

        data class InteractiveLongRunningSession(
            val executionId: String,
            val started: Boolean,
            val status: JSONObject?,
            val error: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult? = null,
        )

        fun startInteractiveLongRunning(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        ): InteractiveLongRunningSession {
            return startLongRunning(
                context = context,
                request = request,
                launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            )
        }

        fun startPackagedLongRunning(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        ): InteractiveLongRunningSession {
            return startLongRunning(
                context = context,
                request = request,
                launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_PACKAGED_LONG_RUNNING,
            )
        }

        private fun startLongRunning(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            launchSurface: String,
        ): InteractiveLongRunningSession {
            val executionId = request.executionId?.takeIf { it.isNotBlank() } ?: newExecutionId()
            val appContext = context.applicationContext
            (appContext as? App)?.cancelDeferredNativeNodePrewarm()
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val requestWithExecutionId = request.copy(
                executionId = executionId,
                executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                timeoutMs = request.timeoutMs.takeIf { it > 0L } ?: DEFAULT_INTERACTIVE_TIMEOUT_MS,
                queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                queueTimeoutMs = 0L,
            ).withHostModuleSourceProvider()
            if (!beginClientExecution(executionId, requestWithExecutionId.moduleSourceProvider)) {
                val failure = clientExecutionCancelledResult(
                    requestWithExecutionId,
                    startedAt,
                    executionId,
                    "long_running_provider_registration",
                )
                val status = NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = requestWithExecutionId.sourceName,
                    workingDirectory = requestWithExecutionId.workingDirectory,
                    launchSurface = launchSurface,
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("cancelled"),
                    nativeValues = failure.nativeValues,
                )
                return InteractiveLongRunningSession(executionId, false, status, failure)
            }
            val requestForService = try {
                NativeNodeEmbeddedRuntimeBridge.prepareEmbeddedScriptRequest(requestWithExecutionId)
            } catch (error: Throwable) {
                val failure = NativeNodeEmbeddedRuntimeBridge.embeddedScriptRequestPreparationFailure(
                    requestWithExecutionId,
                    error,
                )
                val status = NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = requestWithExecutionId.sourceName,
                    workingDirectory = requestWithExecutionId.workingDirectory,
                    launchSurface = launchSurface,
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("preflight_failed"),
                    nativeValues = failure.nativeValues,
                )
                finishClientExecution(executionId, "Embedded Node long-running request preparation failed.")
                return InteractiveLongRunningSession(
                    executionId = executionId,
                    started = false,
                    status = status,
                    error = failure.withLifecycle(preflightFailureLifecycle(executionId, requestWithExecutionId)),
                )
            }
            NativeNodeEmbeddedRuntimeBridge.validateEmbeddedScriptRequestBeforeServiceStart(
                request = requestForService,
                launchSurface = launchSurface,
                context = appContext,
            )?.let { failure ->
                val status = NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = requestForService.sourceName,
                    workingDirectory = requestForService.workingDirectory,
                    launchSurface = launchSurface,
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("preflight_failed"),
                    nativeValues = failure.nativeValues,
                )
                finishClientExecution(executionId, "Embedded Node long-running request validation failed.")
                return InteractiveLongRunningSession(
                    executionId = executionId,
                    started = false,
                    status = status,
                    error = failure.withLifecycle(preflightFailureLifecycle(executionId, requestForService)),
                )
            }
            val processPoolPlan = NodeEmbeddedProcessPoolPolicy.plan(
                context = appContext,
                executionMode = requestForService.executionMode,
                launchSurface = launchSurface,
                requestedSlotId = requestForService.processPoolSlot,
                slotSelectionSource = requestForService.processPoolSlotSource,
            )
            val sessionAcquire = if (NodeEmbeddedProcessSessionManager.isEnabled()) {
                NodeEmbeddedProcessSessionManager.acquire(appContext, processPoolPlan.selectedSlot)
            } else {
                null
            }
            val processSessionLease = sessionAcquire?.lease
            if (sessionAcquire != null && processSessionLease == null) {
                val failure = processCrashedOrTimedOut(
                    request = requestForService,
                    message = sessionAcquire.errorMessage.orEmpty().ifBlank {
                        "Failed to acquire persistent embedded Node process session."
                    },
                    startedAt = startedAt,
                    executionId = executionId,
                    lifecycleValues = sessionAcquire.nativeValues,
                )
                val status = NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = requestForService.sourceName,
                    workingDirectory = requestForService.workingDirectory,
                    launchSurface = launchSurface,
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("session_acquire_failed"),
                    nativeValues = failure.nativeValues,
                )
                finishClientExecution(executionId, "Embedded Node long-running session acquire failed.")
                return InteractiveLongRunningSession(executionId, false, status, failure)
            }
            val sessionReleased = AtomicBoolean(false)
            fun releaseSession(result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?, reason: String) {
                if (!sessionReleased.compareAndSet(false, true)) {
                    return
                }
                finishClientExecution(executionId, "Embedded Node long-running request finished: $reason")
                NodeEmbeddedProcessSessionManager.unregisterExecution(processSessionLease, executionId)
                NodeEmbeddedProcessSessionManager.release(
                    context = appContext,
                    lease = processSessionLease,
                    healthy = result?.let { NodeEmbeddedProcessSessionManager.resultIsReusable(it.nativeValues) } ?: false,
                    reason = reason,
                )
            }
            if (!registerLongRunningReleaseCallback(executionId, processSessionLease, ::releaseSession)) {
                val failure = clientExecutionCancelledResult(
                    requestForService,
                    startedAt,
                    executionId,
                    "long_running_after_session_acquire",
                )
                releaseSession(null, "long_running_cancelled_after_session_acquire")
                val status = NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = requestForService.sourceName,
                    workingDirectory = requestForService.workingDirectory,
                    launchSurface = launchSurface,
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("cancelled"),
                    nativeValues = failure.nativeValues,
                )
                return InteractiveLongRunningSession(executionId, false, status, failure)
            }
            val completionReceiver = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode != RESULT_SCRIPT) {
                        return
                    }
                    val completed = resultData.toEmbeddedScriptResult()
                    releaseSession(completed, "long_running_complete")
                }
            }
            val sessionRegistered = processSessionLease?.let { lease ->
                NodeEmbeddedProcessSessionManager.registerExecution(lease, executionId) {
                    releaseSession(null, "long_running_binder_died")
                    NodeInteractiveLongRunningStatusStore.write(
                        context = appContext,
                        executionId = executionId,
                        state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                        sourceName = requestForService.sourceName,
                        workingDirectory = requestForService.workingDirectory,
                        launchSurface = launchSurface,
                        message = "Persistent embedded Node process Binder died.",
                        activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("binder_died"),
                    )
                }
            } ?: true
            if (!sessionRegistered) {
                val failure = processCrashedOrTimedOut(
                    request = requestForService,
                    message = "Persistent embedded Node process lease was invalidated before long-running execution registration.",
                    startedAt = startedAt,
                    executionId = executionId,
                    lifecycleValues = processSessionLease.nativeValues("registration_failed"),
                )
                val status = NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = requestForService.sourceName,
                    workingDirectory = requestForService.workingDirectory,
                    launchSurface = launchSurface,
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("registration_failed"),
                    nativeValues = failure.nativeValues,
                )
                releaseSession(null, "long_running_registration_failed")
                return InteractiveLongRunningSession(executionId, false, status, failure)
            }
            NodeInteractiveLongRunningStatusStore.write(
                context = appContext,
                executionId = executionId,
                state = NodeInteractiveLongRunningStatusStore.STATE_STARTING,
                sourceName = requestForService.sourceName,
                workingDirectory = requestForService.workingDirectory,
                launchSurface = launchSurface,
                activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("client_starting"),
            )
            var requestSpoolFile: File? = null
            try {
                requestSpoolFile = writeEmbeddedScriptRequestSpool(appContext, executionId, requestForService)
                val intent = Intent(appContext, NativeNodeEmbeddedScriptService::class.java).apply {
                    action = ACTION_RUN_SCRIPT
                    putExtra(EXTRA_RESULT_RECEIVER, completionReceiver)
                    putExtra(EXTRA_SOURCE_NAME, requestForService.sourceName)
                    putExtra(EXTRA_WORKING_DIRECTORY, requestForService.workingDirectory)
                    putExtra(EXTRA_SANDBOX_ROOT, requestForService.sandboxRoot)
                    putExtra(EXTRA_EXECUTION_MODE, requestForService.executionMode)
                    putExtra(EXTRA_LAUNCH_SURFACE, launchSurface)
                    putExtra(EXTRA_TIMEOUT_MS, requestForService.timeoutMs.coerceAtLeast(1L))
                    putExtra(EXTRA_ESM_EXPERIMENTAL_ENABLED, requestForService.esmExperimentalEnabled)
                    putExtra(
                        EXTRA_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED,
                        requestForService.dynamicImportExperimentalEnabled,
                    )
                    putExtra(
                        EXTRA_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED,
                        requestForService.rawNodeNetworkModulesExperimentalEnabled,
                    )
                    putExtra(EXTRA_WORKER_THREADS_EXPERIMENTAL_ENABLED, requestForService.workerThreadsExperimentalEnabled)
                    putExtra(EXTRA_CHILD_PROCESS_EXPERIMENTAL_ENABLED, requestForService.childProcessExperimentalEnabled)
                    putExtra(EXTRA_JAVA_INTEROP_EXPERIMENTAL_ENABLED, requestForService.javaInteropExperimentalEnabled)
                    putExtra(EXTRA_SPAWN_DEPTH, requestForService.spawnDepth.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH))
                    putExtra(EXTRA_RUNTIME_ADAPTER, requestForService.runtimeAdapter)
                    putExtra(EXTRA_PROCESS_POOL_SLOT, requestForService.processPoolSlot)
                    putExtra(EXTRA_PROCESS_POOL_SLOT_SOURCE, requestForService.processPoolSlotSource)
                    putExtra(EXTRA_EXECUTION_ID, executionId)
                    putExtra(EXTRA_REQUEST_SPOOL_FILE, requestSpoolFile.absolutePath)
                    putModuleSourceProvider(requestForService.moduleSourceProvider)
                }
                var startCancelled = false
                val startOutcome = synchronized(CLIENT_EXECUTION_LIFECYCLE_LOCK) {
                    val outcome = if (
                        sessionReleased.get() ||
                        isClientExecutionCancelled(executionId)
                    ) {
                        startCancelled = true
                        null
                    } else if (processSessionLease != null) {
                        NodeEmbeddedProcessSessionManager.startRegisteredExecution(
                            processSessionLease,
                            executionId,
                        ) {
                            startEmbeddedScriptService(
                                appContext,
                                intent,
                                serviceClass = processPoolPlan.selectedSlot.serviceClass,
                            )
                        }
                    } else {
                        startEmbeddedScriptService(
                            appContext,
                            intent,
                            serviceClass = processPoolPlan.selectedSlot.serviceClass,
                        )
                    }
                    outcome?.component?.let { startedComponent ->
                        CLIENT_RUNNING_SCRIPT_COMPONENTS[executionId] = startedComponent
                        processSessionLease?.let {
                            CLIENT_RUNNING_SCRIPT_SESSION_LEASES[executionId] = it
                        }
                    }
                    outcome
                }
                if (startCancelled) {
                    val failure = clientExecutionCancelledResult(
                        requestForService,
                        startedAt,
                        executionId,
                        "long_running_before_service_start",
                    )
                    val status = NodeInteractiveLongRunningStatusStore.write(
                        context = appContext,
                        executionId = executionId,
                        state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                        sourceName = requestForService.sourceName,
                        workingDirectory = requestForService.workingDirectory,
                        launchSurface = launchSurface,
                        message = failure.errorMessage,
                        activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("cancelled"),
                        nativeValues = failure.nativeValues,
                    )
                    releaseSession(null, "long_running_cancelled_before_start")
                    requestSpoolFile.parentFile?.deleteRecursivelyIfExists()
                    return InteractiveLongRunningSession(executionId, false, status, failure)
                }
                if (startOutcome == null) {
                    val failure = processCrashedOrTimedOut(
                        request = requestForService,
                        message = "Persistent embedded Node process lease was invalidated before long-running service start.",
                        startedAt = startedAt,
                        executionId = executionId,
                        lifecycleValues = processSessionLease?.nativeValues("start_invalidated").orEmpty(),
                    )
                    val status = NodeInteractiveLongRunningStatusStore.write(
                        context = appContext,
                        executionId = executionId,
                        state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                        sourceName = requestForService.sourceName,
                        workingDirectory = requestForService.workingDirectory,
                        launchSurface = launchSurface,
                        message = failure.errorMessage,
                        activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("start_invalidated"),
                        nativeValues = failure.nativeValues,
                    )
                    releaseSession(null, "long_running_start_invalidated")
                    requestSpoolFile.parentFile?.deleteRecursivelyIfExists()
                    return InteractiveLongRunningSession(executionId, false, status, failure)
                }
                if (startOutcome.component == null) {
                    val failure = serviceStartFailureResult(
                        request = requestForService,
                        message = "Failed to start interactive embedded script service.",
                        startedAt = startedAt,
                        executionId = executionId,
                        startOutcome = startOutcome,
                    )
                    val status = NodeInteractiveLongRunningStatusStore.write(
                        context = appContext,
                        executionId = executionId,
                        state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                        sourceName = requestForService.sourceName,
                        workingDirectory = requestForService.workingDirectory,
                        launchSurface = launchSurface,
                        message = failure.errorMessage,
                        activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("service_start_failed"),
                        nativeValues = failure.nativeValues,
                    )
                    releaseSession(failure, "long_running_start_failed")
                    requestSpoolFile.parentFile?.deleteRecursivelyIfExists()
                    return InteractiveLongRunningSession(executionId, false, status, failure)
                }
                return InteractiveLongRunningSession(
                    executionId = executionId,
                    started = true,
                    status = NodeInteractiveLongRunningStatusStore.read(appContext, executionId),
                )
            } catch (e: Throwable) {
                requestSpoolFile?.parentFile?.deleteRecursivelyIfExists()
                val failure = processCrashedOrTimedOut(
                    request = requestForService,
                    message = "Interactive embedded script service failed to start.\n${e.message ?: e.javaClass.name}",
                    throwable = e,
                    startedAt = startedAt,
                    executionId = executionId,
                )
                val status = NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = requestForService.sourceName,
                    workingDirectory = requestForService.workingDirectory,
                    launchSurface = launchSurface,
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("service_start_exception"),
                    nativeValues = failure.nativeValues,
                )
                releaseSession(failure, "long_running_start_exception")
                return InteractiveLongRunningSession(executionId, false, status, failure)
            }
        }

        fun stopInteractiveLongRunning(context: Context, executionId: String): Boolean {
            val appContext = context.applicationContext
            val runningServiceClass = CLIENT_RUNNING_SCRIPT_COMPONENTS[executionId]
                ?.className
                ?.let(NodeEmbeddedProcessPoolPolicy::slotForServiceClassName)
                ?.serviceClass
                ?: NativeNodeEmbeddedScriptService::class.java
            return stopInteractiveLongRunning(appContext, executionId, runningServiceClass)
        }

        private fun stopInteractiveLongRunning(
            appContext: Context,
            executionId: String,
            runningServiceClass: Class<out NativeNodeEmbeddedScriptService>,
        ): Boolean {
            val cancellation = cancelClientExecutionLifecycle(
                context = appContext,
                executionId = executionId,
                providerReason = "Embedded Node long-running request was stopped by the client.",
                sessionReason = "long_running_client_stop",
            )
            val current = NodeInteractiveLongRunningStatusStore.read(appContext, executionId)
            NodeInteractiveLongRunningStatusStore.write(
                context = appContext,
                executionId = executionId,
                state = NodeInteractiveLongRunningStatusStore.STATE_STOPPING,
                sourceName = current?.optString("sourceName").orEmpty().ifBlank { DEFAULT_SOURCE_NAME },
                workingDirectory = current?.optString("workingDirectory")?.takeIf { it.isNotBlank() },
                startedAtEpochMs = current?.optLong("startedAtEpochMs")?.takeIf { it > 0L },
                stopRequested = true,
                message = "client.stop.requested",
                activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("client_stopping"),
            )
            val intent = Intent(appContext, NativeNodeEmbeddedScriptService::class.java).apply {
                action = ACTION_STOP_INTERACTIVE
                putExtra(EXTRA_EXECUTION_ID, executionId)
            }
            val startOutcome = startEmbeddedScriptService(
                appContext,
                intent,
                serviceClass = runningServiceClass,
                foreground = false,
            )
            if (startOutcome.component == null) {
                NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = current?.optString("sourceName").orEmpty().ifBlank { DEFAULT_SOURCE_NAME },
                    workingDirectory = current?.optString("workingDirectory")?.takeIf { it.isNotBlank() },
                    startedAtEpochMs = current?.optLong("startedAtEpochMs")?.takeIf { it > 0L },
                    stopRequested = true,
                    message = startOutcome.diagnostics.optString("message").ifBlank {
                        "client.stop.service_start_failed"
                    },
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("client_stop_failed"),
                    nativeValues = startOutcome.nativeValues(),
                )
            }
            val fallbackStopped = if (
                startOutcome.component == null &&
                cancellation.component != null &&
                !cancellation.sessionReleaseRequested
            ) {
                runCatching {
                    appContext.stopService(Intent().setComponent(cancellation.component))
                }.getOrDefault(false)
            } else {
                false
            }
            return startOutcome.component != null ||
                    fallbackStopped ||
                    (
                        cancellation.acknowledged &&
                            (cancellation.component == null || cancellation.sessionReleaseRequested)
                    )
        }

        fun stopInteractiveLongRunning(context: Context, intent: Intent): Boolean {
            if (intent.action != ACTION_STOP_INTERACTIVE) {
                return false
            }
            val executionId = intent.getStringExtra(EXTRA_EXECUTION_ID)?.takeIf { it.isNotBlank() }
                ?: return false
            val targetServiceClass = intent.getStringExtra(EXTRA_STOP_SERVICE_CLASS)
                ?.let(NodeEmbeddedProcessPoolPolicy::slotForServiceClassName)
                ?.serviceClass
            return if (targetServiceClass != null) {
                stopInteractiveLongRunning(context.applicationContext, executionId, targetServiceClass)
            } else {
                stopInteractiveLongRunning(context, executionId)
            }
        }

        fun interactiveLongRunningStatus(context: Context, executionId: String): JSONObject? {
            return NodeInteractiveLongRunningStatusStore.read(context.applicationContext, executionId)
        }

        fun simulateForegroundServiceTimeoutForTest(
            context: Context,
            executionId: String,
            foregroundServiceType: Int = embeddedForegroundServiceTypeForCurrentSdk(),
        ): Boolean {
            val appContext = context.applicationContext
            val intent = Intent(appContext, NativeNodeEmbeddedScriptService::class.java).apply {
                action = ACTION_SIMULATE_FOREGROUND_TIMEOUT
                putExtra(EXTRA_EXECUTION_ID, executionId)
                putExtra(EXTRA_FOREGROUND_SERVICE_TYPE, foregroundServiceType)
            }
            return runCatching { appContext.startService(intent) != null }.getOrDefault(false)
        }

        private data class ServiceStartOutcome(
            val component: ComponentName?,
            val diagnostics: JSONObject,
            val serviceDiagnostics: JSONObject,
            val throwable: Throwable? = null,
        ) {
            fun nativeValues(): Map<String, String> {
                return linkedMapOf<String, String>().apply {
                    putAll(NodeAndroidRuntimePolicy.nativeValues("embedded_script.android_service_start", diagnostics))
                    putAll(NodeAndroidRuntimePolicy.nativeValues("embedded_script.android_service", serviceDiagnostics))
                    put("embedded_script.service_start_failed", (component == null).toString())
                }
            }
        }

        private fun startEmbeddedScriptService(
            appContext: Context,
            intent: Intent,
            serviceClass: Class<out NativeNodeEmbeddedScriptService> = NativeNodeEmbeddedScriptService::class.java,
            foreground: Boolean = true,
        ): ServiceStartOutcome {
            intent.setClass(appContext, serviceClass)
            val serviceDiagnostics = NodeAndroidRuntimePolicy.embeddedServiceDiagnostics(
                appContext,
                serviceClass,
                embeddedForegroundServiceTypeForCurrentSdk(),
            )
            return try {
                val component = if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                val diagnostics = if (component == null) {
                    NodeAndroidRuntimePolicy.serviceStartFailureDiagnosticsForClass(
                        className = "android.content.ComponentName:null",
                        message = "Context.startService returned null.",
                        serviceClassName = serviceClass.name,
                        sdkInt = Build.VERSION.SDK_INT,
                    )
                } else {
                    JSONObject().apply {
                        put("sdkInt", Build.VERSION.SDK_INT)
                        put("serviceName", serviceClass.name)
                        put("reason", "started")
                        put("componentName", component.flattenToShortString())
                    }
                }
                ServiceStartOutcome(component, diagnostics, serviceDiagnostics)
            } catch (e: Throwable) {
                ServiceStartOutcome(
                    component = null,
                    diagnostics = NodeAndroidRuntimePolicy.serviceStartFailureDiagnostics(
                        throwable = e,
                        serviceClassName = serviceClass.name,
                    ),
                    serviceDiagnostics = serviceDiagnostics,
                    throwable = e,
                )
            }
        }

        private fun serviceStartFailureResult(
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            message: String,
            startedAt: Long,
            executionId: String,
            startOutcome: ServiceStartOutcome,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val diagnostics = startOutcome.diagnostics
            val processName = currentProcessNameCompat()
            val pid = Process.myPid()
            val sourceName = request.sourceName.ifBlank { DEFAULT_SOURCE_NAME }
            val elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            val lifecycle = NodeEmbeddedRuntimeLifecycle(TAG, executionId, processName, pid, sourceName)
                .apply { transition(NodeEmbeddedRuntimeState.FAILED, "client.foreground_service_start_failed") }
            val errorCode = diagnostics.optString("errorCode")
                .ifBlank { NodeRuntimeErrorCodes.EMBEDDED_NODE_INVALID_STATE }
            val errorName = diagnostics.optString("errorName")
                .ifBlank { "EmbeddedNodeForegroundServiceStartError" }
            val androidMessage = diagnostics.optString("androidExceptionMessage")
                .takeIf { it.isNotBlank() }
                ?.let { "\nAndroid: $it" }
                .orEmpty()
            val nativeValues = linkedMapOf<String, String>().apply {
                putAll(startOutcome.nativeValues())
                put("embedded_script.error_code", errorCode)
                put("embedded_script.error_name", errorName)
            }
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
                succeeded = false,
                exitCode = 1,
                resultText = null,
                stdout = "",
                stderr = "",
                errorName = errorName,
                errorMessage = diagnostics.optString("message").ifBlank { message } + androidMessage,
                errorStack = startOutcome.throwable?.stackTraceToString(),
                errorCode = errorCode,
                elapsedMs = elapsedMs,
                processName = processName,
                pid = pid,
                sourceName = sourceName,
                nativeValues = nativeValues,
                workingDirectory = request.workingDirectory,
            ).withLifecycle(lifecycle)
        }

        private fun embeddedForegroundServiceTypeForCurrentSdk(): Int {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else -> FOREGROUND_SERVICE_TYPE_UNKNOWN
            }
        }

        private fun normalizedPrewarmTtlMs(ttlMs: Long): Long {
            return when {
                ttlMs <= 0L -> DEFAULT_PREWARM_TTL_MS
                else -> ttlMs.coerceAtMost(MAX_PREWARM_TTL_MS)
            }
        }

        private fun Bundle?.toPrewarmRuntimeResult(): PrewarmRuntimeResult {
            val nativeValues = nativePayloadMap(this?.getStringArray(EXTRA_PREWARM_NATIVE_PAYLOAD))
            return PrewarmRuntimeResult(
                started = this?.getBoolean(EXTRA_PREWARM_STARTED) ?: false,
                status = this?.getString(EXTRA_PREWARM_STATUS).orEmpty().ifBlank { "unknown" },
                reason = this?.getString(EXTRA_PREWARM_REASON).orEmpty(),
                ttlMs = this?.getLong(EXTRA_PREWARM_TTL_MS) ?: 0L,
                processName = this?.getString(EXTRA_PREWARM_PROCESS_NAME).orEmpty(),
                pid = this?.getInt(EXTRA_PREWARM_PID) ?: -1,
                processOnly = this?.getBoolean(EXTRA_PREWARM_PROCESS_ONLY) ?: true,
                runtimeLoaded = this?.getBoolean(EXTRA_PREWARM_RUNTIME_LOADED) ?: false,
                nativeValues = nativeValues,
            )
        }

        private fun nativePayloadMap(payload: Array<String>?): Map<String, String> {
            return payload.orEmpty()
                .mapNotNull { item ->
                    val separator = item.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        item.substring(0, separator) to item.substring(separator + 1)
                    }
                }
                .toMap(linkedMapOf())
        }

        private fun acquireDefaultProcessPoolSlot(
            context: Context,
            executionId: String,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            launchSurface: String,
        ): DefaultProcessPoolSlotDecision {
            if (!request.processPoolSlot.isNullOrBlank()) {
                return DefaultProcessPoolSlotDecision.NotEligible
            }
            if (!NodeEmbeddedProcessPoolPolicy.defaultSchedulerEligible(
                    context = context,
                    executionMode = request.executionMode,
                    launchSurface = launchSurface,
                )
            ) {
                return DefaultProcessPoolSlotDecision.NotEligible
            }
            val normalizedMode = NodeExecutionQueuePolicy.normalizeMode(request.queueMode)
            val normalizedQueueTimeoutMs = NodeExecutionQueuePolicy.normalizedQueueTimeoutMs(
                normalizedMode,
                request.queueTimeoutMs,
            )
            val queuedAt = android.os.SystemClock.elapsedRealtime()
            var queued = false
            while (true) {
                synchronized(DEFAULT_PROCESS_POOL_LOCK) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val queueWaitMs = (now - queuedAt).coerceAtLeast(0L)
                    val deadline = queuedAt + normalizedQueueTimeoutMs
                    val remainingMs = deadline - now
                    pruneDefaultProcessPoolSlotCooldownsLocked(now)
                    val coolingSlotIds = DEFAULT_PROCESS_POOL_SLOT_COOLDOWN_UNTIL_MS.keys
                        .map { "$it:cooling" }
                    val activeSlotIds = DEFAULT_PROCESS_POOL_BUSY_SLOT_IDS.toList() + coolingSlotIds
                    if (isClientExecutionCancelled(executionId)) {
                        return DefaultProcessPoolSlotDecision.Rejected(
                            failure = queueCancelledFailure(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queueWaitMs = queueWaitMs,
                            ),
                            activeSlotIds = activeSlotIds,
                        )
                    }
                    val slot = NodeEmbeddedProcessPoolPolicy.defaultSchedulerSelectableSlots(
                        context = context,
                        executionMode = request.executionMode,
                        launchSurface = launchSurface,
                    ).firstOrNull { candidate ->
                        candidate.id !in DEFAULT_PROCESS_POOL_BUSY_SLOT_IDS &&
                                candidate.id !in DEFAULT_PROCESS_POOL_SLOT_COOLDOWN_UNTIL_MS
                    }
                    if (slot != null) {
                        DEFAULT_PROCESS_POOL_BUSY_SLOT_IDS.add(slot.id)
                        return DefaultProcessPoolSlotDecision.Acquired(
                            DefaultProcessPoolSlotLease(
                                executionId = executionId,
                                slot = slot,
                                activeSlotIdsAtAcquire = activeSlotIds,
                                acquiredAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
                                queueMode = normalizedMode,
                                queued = queued,
                                queueWaitMs = queueWaitMs,
                            ),
                        )
                    }
                    if (normalizedMode != NodeExecutionQueuePolicy.MODE_ENQUEUE_WHEN_BUSY) {
                        return DefaultProcessPoolSlotDecision.Rejected(
                            failure = defaultProcessPoolBusyFailure(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queuedAt = queuedAt,
                                activeSlotIds = activeSlotIds,
                            ),
                            activeSlotIds = activeSlotIds,
                        )
                    }
                    if (remainingMs <= 0L) {
                        return DefaultProcessPoolSlotDecision.Rejected(
                            failure = queueTimeoutFailure(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queueWaitMs = queueWaitMs,
                            ),
                            activeSlotIds = activeSlotIds,
                        )
                    }
                    queued = true
                    try {
                        DEFAULT_PROCESS_POOL_LOCK.wait(remainingMs.coerceAtMost(100L))
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return DefaultProcessPoolSlotDecision.Rejected(
                            failure = queueCancelledFailure(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queueWaitMs = (android.os.SystemClock.elapsedRealtime() - queuedAt).coerceAtLeast(0L),
                            ),
                            activeSlotIds = activeSlotIds,
                        )
                    }
                }
            }
        }

        private fun defaultProcessPoolBusyFailure(
            executionId: String,
            queueMode: String,
            queuedAt: Long,
            activeSlotIds: List<String>,
        ): ExecutionSlotFailure {
            return ExecutionSlotFailure(
                errorName = "EmbeddedNodeProcessPoolBusyError",
                errorCode = NodeRuntimeErrorCodes.NODE_ENGINE_BUSY,
                errorMessage = "Embedded Node.js default process pool is busy; active slots=${activeSlotIds.joinToString(",")}.",
                queueMode = queueMode,
                reason = "default_process_pool_busy",
                activeExecutionId = null,
                queueWaitMs = (android.os.SystemClock.elapsedRealtime() - queuedAt).coerceAtLeast(0L),
                queuedCount = 0,
                maxQueuedExecutions = NodeEmbeddedProcessPoolPolicy.DEFAULT_SCHEDULER_EFFECTIVE_MAX_ACTIVE_SLOTS,
            )
        }

        private fun releaseDefaultProcessPoolSlot(lease: DefaultProcessPoolSlotLease?) {
            if (lease == null) {
                return
            }
            synchronized(DEFAULT_PROCESS_POOL_LOCK) {
                DEFAULT_PROCESS_POOL_BUSY_SLOT_IDS.remove(lease.slot.id)
                DEFAULT_PROCESS_POOL_LOCK.notifyAll()
            }
        }

        private fun coolDownDefaultProcessPoolSlot(lease: DefaultProcessPoolSlotLease?, reason: String) {
            if (lease == null) {
                return
            }
            val until = android.os.SystemClock.elapsedRealtime() + DEFAULT_PROCESS_POOL_STOP_COOLDOWN_MS
            synchronized(DEFAULT_PROCESS_POOL_LOCK) {
                DEFAULT_PROCESS_POOL_SLOT_COOLDOWN_UNTIL_MS[lease.slot.id] = until
                DEFAULT_PROCESS_POOL_LOCK.notifyAll()
            }
            Log.i(
                TAG,
                "default_process_pool.slot_cooldown slot=${lease.slot.id} " +
                        "executionId=${lease.executionId} reason=$reason " +
                        "cooldownMs=$DEFAULT_PROCESS_POOL_STOP_COOLDOWN_MS",
            )
        }

        private fun pruneDefaultProcessPoolSlotCooldownsLocked(now: Long) {
            val iterator = DEFAULT_PROCESS_POOL_SLOT_COOLDOWN_UNTIL_MS.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value <= now) {
                    iterator.remove()
                }
            }
        }

        private fun defaultProcessPoolFailureResult(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            executionId: String,
            startedAt: Long,
            launchSurface: String,
            failure: ExecutionSlotFailure,
            activeSlotIds: List<String>,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val processName = currentProcessNameCompat()
            val pid = Process.myPid()
            val lifecycle = NodeEmbeddedRuntimeLifecycle(TAG, executionId, processName, pid, request.sourceName)
                .apply {
                    transition(NodeEmbeddedRuntimeState.FAILED, "client.${failure.reason}")
                }
            val processPoolPlan = NodeEmbeddedProcessPoolPolicy.plan(
                context = context,
                executionMode = request.executionMode,
                launchSurface = launchSurface,
                slotSelectionSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_DEFAULT_SCHEDULER,
                defaultSchedulerEnabled = true,
            )
            val nativeValues = linkedMapOf<String, String>().apply {
                putAll(processPoolPlan.nativeValues())
                put("embedded_script.process_pool.scheduler_rejected", "true")
                put("embedded_script.process_pool.active_slot_ids", activeSlotIds.joinToString(","))
                put("embedded_script.concurrent_policy", "one-active-execution-per-process-slot")
                put("embedded_script.rejected_execution_id", executionId)
                put("embedded_script.queue_mode", failure.queueMode)
                put("embedded_script.queue_status", failure.reason)
                put("embedded_script.queue_wait_ms", failure.queueWaitMs.toString())
                put("embedded_script.error_code", failure.errorCode)
                when (failure.errorCode) {
                    NodeRuntimeErrorCodes.NODE_ENGINE_BUSY -> {
                        put("embedded_script.process_pool.busy", "true")
                        put("embedded_script.engine_busy", "true")
                    }
                    NodeRuntimeErrorCodes.NODE_QUEUE_TIMEOUT -> put("embedded_script.queue_timeout", "true")
                    NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED -> put("embedded_script.queue_cancelled", "true")
                }
            }
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
                succeeded = false,
                exitCode = 1,
                resultText = null,
                stdout = "",
                stderr = "",
                errorName = failure.errorName,
                errorMessage = failure.errorMessage,
                errorStack = null,
                errorCode = failure.errorCode,
                elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                processName = processName,
                pid = pid,
                sourceName = request.sourceName,
                nativeValues = nativeValues,
                workingDirectory = request.workingDirectory,
            ).withLifecycle(lifecycle)
        }

        private fun acquireExecutionSlot(
            executionId: String,
            queueMode: String,
            queueTimeoutMs: Long,
        ): ExecutionSlotDecision {
            val normalizedMode = NodeExecutionQueuePolicy.normalizeMode(queueMode)
            val normalizedQueueTimeoutMs = NodeExecutionQueuePolicy.normalizedQueueTimeoutMs(normalizedMode, queueTimeoutMs)
            val queuedAt = android.os.SystemClock.elapsedRealtime()
            synchronized(EXECUTION_QUEUE_LOCK) {
                if (isClientExecutionCancelled(executionId)) {
                    return ExecutionSlotDecision.Rejected(
                        queueCancelledFailure(
                            executionId = executionId,
                            queueMode = normalizedMode,
                            queueWaitMs = 0L,
                        ),
                    )
                }
                if (ACTIVE_EXECUTION_ID.get() == null && QUEUED_EXECUTION_IDS.isEmpty()) {
                    ACTIVE_EXECUTION_ID.set(executionId)
                    return ExecutionSlotDecision.Acquired(
                        ExecutionSlotLease(
                            executionId = executionId,
                            queueMode = normalizedMode,
                            queued = false,
                            queueWaitMs = 0L,
                            queuePosition = 0,
                            maxQueuedExecutions = NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS,
                        ),
                    )
                }
                if (normalizedMode != NodeExecutionQueuePolicy.MODE_ENQUEUE_WHEN_BUSY) {
                    return ExecutionSlotDecision.Rejected(
                        busyFailure(
                            executionId = executionId,
                            queueMode = normalizedMode,
                            queuedAt = queuedAt,
                        ),
                    )
                }
                if (QUEUED_EXECUTION_IDS.size >= NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS) {
                    return ExecutionSlotDecision.Rejected(
                        queueFullFailure(
                            executionId = executionId,
                            queueMode = normalizedMode,
                            queuedAt = queuedAt,
                        ),
                    )
                }
                QUEUED_EXECUTION_IDS.addLast(executionId)
                EXECUTION_QUEUE_LOCK.notifyAll()
            }

            val deadline = queuedAt + normalizedQueueTimeoutMs
            while (true) {
                synchronized(EXECUTION_QUEUE_LOCK) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (isClientExecutionCancelled(executionId)) {
                        QUEUED_EXECUTION_IDS.remove(executionId)
                        CANCELLED_QUEUED_EXECUTION_IDS.remove(executionId)
                        EXECUTION_QUEUE_LOCK.notifyAll()
                        return ExecutionSlotDecision.Rejected(
                            queueCancelledFailure(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queueWaitMs = (now - queuedAt).coerceAtLeast(0L),
                            ),
                        )
                    }
                    if (CANCELLED_QUEUED_EXECUTION_IDS.remove(executionId)) {
                        QUEUED_EXECUTION_IDS.remove(executionId)
                        EXECUTION_QUEUE_LOCK.notifyAll()
                        return ExecutionSlotDecision.Rejected(
                            queueCancelledFailure(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queueWaitMs = (now - queuedAt).coerceAtLeast(0L),
                            ),
                        )
                    }
                    if (QUEUED_EXECUTION_IDS.peekFirst() == executionId && ACTIVE_EXECUTION_ID.get() == null) {
                        ACTIVE_EXECUTION_ID.set(executionId)
                        QUEUED_EXECUTION_IDS.removeFirst()
                        EXECUTION_QUEUE_LOCK.notifyAll()
                        return ExecutionSlotDecision.Acquired(
                            ExecutionSlotLease(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queued = true,
                                queueWaitMs = (now - queuedAt).coerceAtLeast(0L),
                                queuePosition = 1,
                                maxQueuedExecutions = NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS,
                            ),
                        )
                    }
                    val remainingMs = deadline - now
                    if (remainingMs <= 0L) {
                        QUEUED_EXECUTION_IDS.remove(executionId)
                        CANCELLED_QUEUED_EXECUTION_IDS.remove(executionId)
                        EXECUTION_QUEUE_LOCK.notifyAll()
                        return ExecutionSlotDecision.Rejected(
                            queueTimeoutFailure(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queueWaitMs = (now - queuedAt).coerceAtLeast(0L),
                            ),
                        )
                    }
                    try {
                        EXECUTION_QUEUE_LOCK.wait(remainingMs.coerceAtMost(100L))
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        QUEUED_EXECUTION_IDS.remove(executionId)
                        CANCELLED_QUEUED_EXECUTION_IDS.remove(executionId)
                        EXECUTION_QUEUE_LOCK.notifyAll()
                        return ExecutionSlotDecision.Rejected(
                            queueCancelledFailure(
                                executionId = executionId,
                                queueMode = normalizedMode,
                                queueWaitMs = (android.os.SystemClock.elapsedRealtime() - queuedAt).coerceAtLeast(0L),
                            ),
                        )
                    }
                }
            }
        }

        private fun releaseExecutionSlot(executionId: String): NodeExecutionQueueStatus {
            synchronized(EXECUTION_QUEUE_LOCK) {
                if (ACTIVE_EXECUTION_ID.get() == executionId) {
                    ACTIVE_EXECUTION_ID.set(null)
                }
                EXECUTION_QUEUE_LOCK.notifyAll()
                return queueStatusSnapshotLocked()
            }
        }

        private fun cancelQueuedExecutionInService(executionId: String): Boolean {
            if (executionId.isBlank()) {
                return false
            }
            synchronized(EXECUTION_QUEUE_LOCK) {
                if (!QUEUED_EXECUTION_IDS.contains(executionId)) {
                    return false
                }
                CANCELLED_QUEUED_EXECUTION_IDS.add(executionId)
                EXECUTION_QUEUE_LOCK.notifyAll()
                return true
            }
        }

        private fun cancelAllQueuedExecutionsInService() {
            synchronized(EXECUTION_QUEUE_LOCK) {
                CANCELLED_QUEUED_EXECUTION_IDS.addAll(QUEUED_EXECUTION_IDS)
                EXECUTION_QUEUE_LOCK.notifyAll()
            }
        }

        private fun queueStatusSnapshot(): NodeExecutionQueueStatus {
            synchronized(EXECUTION_QUEUE_LOCK) {
                return queueStatusSnapshotLocked()
            }
        }

        private fun queueStatusSnapshotLocked(): NodeExecutionQueueStatus {
            return NodeExecutionQueueStatus(
                activeExecutionId = ACTIVE_EXECUTION_ID.get(),
                queuedExecutionIds = QUEUED_EXECUTION_IDS.toList(),
                maxQueuedExecutions = NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS,
            )
        }

        private fun busyFailure(
            executionId: String,
            queueMode: String,
            queuedAt: Long,
        ): ExecutionSlotFailure {
            return ExecutionSlotFailure(
                errorName = "EmbeddedNodeEngineBusyError",
                errorCode = NodeRuntimeErrorCodes.NODE_ENGINE_BUSY,
                errorMessage = "Embedded Node.js engine is busy; only one active execution is allowed per :nodejs_embedded process.",
                queueMode = queueMode,
                reason = "engine_busy",
                activeExecutionId = ACTIVE_EXECUTION_ID.get(),
                queueWaitMs = (android.os.SystemClock.elapsedRealtime() - queuedAt).coerceAtLeast(0L),
                queuedCount = QUEUED_EXECUTION_IDS.size,
                maxQueuedExecutions = NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS,
            )
        }

        private fun queueFullFailure(
            executionId: String,
            queueMode: String,
            queuedAt: Long,
        ): ExecutionSlotFailure {
            return ExecutionSlotFailure(
                errorName = "EmbeddedNodeQueueFullError",
                errorCode = NodeRuntimeErrorCodes.NODE_QUEUE_FULL,
                errorMessage = "Embedded Node.js execution queue is full; maxQueuedExecutions=${NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS}.",
                queueMode = queueMode,
                reason = "queue_full",
                activeExecutionId = ACTIVE_EXECUTION_ID.get(),
                queueWaitMs = (android.os.SystemClock.elapsedRealtime() - queuedAt).coerceAtLeast(0L),
                queuedCount = QUEUED_EXECUTION_IDS.size,
                maxQueuedExecutions = NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS,
            )
        }

        private fun queueTimeoutFailure(
            executionId: String,
            queueMode: String,
            queueWaitMs: Long,
        ): ExecutionSlotFailure {
            return ExecutionSlotFailure(
                errorName = "EmbeddedNodeQueueTimeoutError",
                errorCode = NodeRuntimeErrorCodes.NODE_QUEUE_TIMEOUT,
                errorMessage = "Embedded Node.js execution waited ${queueWaitMs}ms in queue and timed out before starting.",
                queueMode = queueMode,
                reason = "queue_timeout",
                activeExecutionId = ACTIVE_EXECUTION_ID.get(),
                queueWaitMs = queueWaitMs,
                queuedCount = QUEUED_EXECUTION_IDS.size,
                maxQueuedExecutions = NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS,
            )
        }

        private fun queueCancelledFailure(
            executionId: String,
            queueMode: String,
            queueWaitMs: Long,
        ): ExecutionSlotFailure {
            return ExecutionSlotFailure(
                errorName = "EmbeddedNodeQueueCancelledError",
                errorCode = NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED,
                errorMessage = "Embedded Node.js queued execution was cancelled before starting.",
                queueMode = queueMode,
                reason = "queue_cancelled",
                activeExecutionId = ACTIVE_EXECUTION_ID.get(),
                queueWaitMs = queueWaitMs,
                queuedCount = QUEUED_EXECUTION_IDS.size,
                maxQueuedExecutions = NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS,
            )
        }

        private fun clientQueueFailureResult(
            failure: ExecutionSlotFailure,
            executionId: String,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            startedAt: Long,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val processName = currentProcessNameCompat()
            val pid = Process.myPid()
            val lifecycle = NodeEmbeddedRuntimeLifecycle(TAG, executionId, processName, pid, request.sourceName)
                .apply {
                    transition(NodeEmbeddedRuntimeState.FAILED, "client.${failure.reason}")
                }
            val elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            val nativeValues = linkedMapOf<String, String>().apply {
                put("embedded_script.queue_mode", failure.queueMode)
                put("embedded_script.queue_status", failure.reason)
                put("embedded_script.queue_wait_ms", failure.queueWaitMs.toString())
                put("embedded_script.queue_queued_count", failure.queuedCount.toString())
                put("embedded_script.queue_max_queued_executions", failure.maxQueuedExecutions.toString())
                put("embedded_script.concurrent_policy", "single-active-execution-per-process")
                put("embedded_script.active_execution_id", failure.activeExecutionId.orEmpty())
                put("embedded_script.rejected_execution_id", executionId)
                put("embedded_script.error_code", failure.errorCode)
                when (failure.errorCode) {
                    NodeRuntimeErrorCodes.NODE_ENGINE_BUSY -> put("embedded_script.engine_busy", "true")
                    NodeRuntimeErrorCodes.NODE_QUEUE_FULL -> put("embedded_script.queue_full", "true")
                    NodeRuntimeErrorCodes.NODE_QUEUE_TIMEOUT -> put("embedded_script.queue_timeout", "true")
                    NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED -> put("embedded_script.queue_cancelled", "true")
                }
            }
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
                succeeded = false,
                exitCode = 1,
                resultText = null,
                stdout = "",
                stderr = "",
                errorName = failure.errorName,
                errorMessage = failure.errorMessage,
                errorStack = null,
                errorCode = failure.errorCode,
                elapsedMs = elapsedMs,
                processName = processName,
                pid = pid,
                sourceName = request.sourceName,
                nativeValues = nativeValues,
                workingDirectory = request.workingDirectory,
            ).withLifecycle(lifecycle)
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withClientQueueLease(
            lease: ExecutionSlotLease,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            return copy(
                nativeValues = linkedMapOf<String, String>().apply {
                    putAll(nativeValues)
                    put("embedded_script.queue_mode", lease.queueMode)
                    put("embedded_script.queue_status", if (lease.queued) "dequeued" else "not_queued")
                    put("embedded_script.queue_queued", lease.queued.toString())
                    put("embedded_script.queue_wait_ms", lease.queueWaitMs.toString())
                    put("embedded_script.queue_position", lease.queuePosition.toString())
                    put("embedded_script.queue_max_queued_executions", lease.maxQueuedExecutions.toString())
                },
            )
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withProcessPoolPlan(
            plan: NodeEmbeddedProcessPoolPolicy.ProcessPoolPlan,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            return copy(
                nativeValues = linkedMapOf<String, String>().apply {
                    putAll(nativeValues)
                    putAll(plan.nativeValues())
                },
            )
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withDefaultProcessPoolLease(
            lease: DefaultProcessPoolSlotLease?,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            if (lease == null) {
                return this
            }
            return copy(
                nativeValues = linkedMapOf<String, String>().apply {
                    putAll(nativeValues)
                    put("embedded_script.process_pool.queue_mode", lease.queueMode)
                    put("embedded_script.process_pool.queue_queued", lease.queued.toString())
                    put(
                        "embedded_script.process_pool.queue_status",
                        if (lease.queued) "dequeued" else "not_queued",
                    )
                    put("embedded_script.process_pool.queue_wait_ms", lease.queueWaitMs.toString())
                },
            )
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withClientPhaseTimings(
            phaseTimings: Map<String, Long>,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult = copy(
            nativeValues = NodeEmbeddedProcessSessionManager.mergeClientPhaseTimings(nativeValues, phaseTimings),
        )

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withProcessSessionLease(
            lease: NodeEmbeddedProcessSessionManager.SessionLease?,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val leaseValues = lease?.nativeValues("execution_complete").orEmpty()
            val runtimeInitializedDuringAcquire =
                leaseValues[NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZED_DURING_ACQUIRE_KEY] == "true"
            val sessionValues = if (lease != null) {
                leaseValues.filterKeys { key -> key.startsWith("embedded_script.process_session.") }
            } else if (NodeEmbeddedProcessSessionManager.isEnabled()) {
                    mapOf(
                        "embedded_script.process_session.enabled" to "true",
                        "embedded_script.process_session.status" to "acquire_failed",
                    )
                } else {
                    mapOf(
                        "embedded_script.process_session.enabled" to "false",
                        "embedded_script.process_session.status" to "one_shot_rollback",
                    )
                }
            return copy(
                nativeValues = NodeEmbeddedProcessSessionManager.mergeClientPhaseTimings(
                    linkedMapOf<String, String>().apply {
                        putAll(nativeValues)
                        if (lease != null) {
                            remove(NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZE_TIMING_KEY)
                            remove(NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_NATIVE_LOAD_TIMING_KEY)
                            if (runtimeInitializedDuringAcquire) {
                                NodeEmbeddedProcessSessionManager.PROCESS_RUNTIME_INITIALIZATION_PHASE_KEYS
                                    .forEach { key -> leaseValues[key]?.let { put(key, it) } }
                            }
                        }
                        putAll(sessionValues)
                    },
                    NodeEmbeddedProcessSessionManager.clientPhaseTimings(leaseValues),
                ),
            )
        }

        private fun deferProcessSettle(releaseSlot: () -> Unit): Boolean {
            val releaseRunnable = Runnable { releaseSlot() }
            val scheduled = runCatching {
                CLIENT_PROCESS_SETTLE_EXECUTOR.schedule(
                    releaseRunnable,
                    CLIENT_PROCESS_SETTLE_MS,
                    TimeUnit.MILLISECONDS,
                )
                true
            }.onFailure { error ->
                Log.w(TAG, "Failed to schedule client process-settle window; releasing the slot immediately.", error)
            }.getOrDefault(false)
            if (!scheduled) {
                releaseRunnable.run()
            }
            return scheduled
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withClientResultReturnTiming(
            resultReceivedAtElapsedMs: Long,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            if (resultReceivedAtElapsedMs <= 0L) {
                return this
            }
            val resultToReturnMs = (android.os.SystemClock.elapsedRealtime() - resultReceivedAtElapsedMs).coerceAtLeast(0L)
            return copy(
                nativeValues = NodeEmbeddedProcessSessionManager.mergeClientPhaseTimings(
                    linkedMapOf<String, String>().apply {
                        putAll(nativeValues)
                        put("embedded_script.client.result_to_return_ms", resultToReturnMs.toString())
                    },
                    mapOf(
                        NodeEmbeddedProcessSessionManager.PhaseTiming.CALLBACK_TO_RETURN to resultToReturnMs,
                    ),
                ),
            )
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withDeferredProcessSettle(
            deferred: Boolean,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            return copy(
                nativeValues = linkedMapOf<String, String>().apply {
                    putAll(nativeValues)
                    put("embedded_script.client.process_settle_deferred", deferred.toString())
                    put(
                        "embedded_script.client.process_settle_delay_ms",
                        if (deferred) CLIENT_PROCESS_SETTLE_MS.toString() else "0",
                    )
                },
            )
        }

        private fun processCrashedOrTimedOut(
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            message: String,
            throwable: Throwable? = null,
            startedAt: Long = android.os.SystemClock.elapsedRealtime(),
            timedOut: Boolean = false,
            executionId: String = newExecutionId(),
            lifecycleValues: Map<String, String> = emptyMap(),
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            val fallbackLifecycleValues = lifecycleValues.ifEmpty {
                processFailureLifecycle(executionId, request, timedOut).nativeValues()
            }
            val nativeValues = linkedMapOf<String, String>().apply {
                putAll(fallbackLifecycleValues)
                if (timedOut) {
                    putAll(timeoutNativeValues(request, elapsedMs))
                }
            }
            val timeoutMessage = if (timedOut) timeoutDiagnosticMessage(request, elapsedMs, nativeValues) else message
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
                succeeded = false,
                exitCode = if (timedOut) TIMEOUT_EXIT_CODE else 1,
                resultText = null,
                stdout = "",
                stderr = "",
                errorName = if (timedOut) "EmbeddedScriptTimeoutError" else throwable?.javaClass?.simpleName ?: "EmbeddedScriptProcessError",
                errorMessage = timeoutMessage,
                errorStack = throwable?.stackTraceToString(),
                errorCode = if (timedOut) TIMEOUT_ERROR_CODE else null,
                elapsedMs = elapsedMs,
                processName = "unknown",
                pid = -1,
                sourceName = request.sourceName,
                nativeValues = nativeValues,
                timedOut = timedOut,
                timeoutMs = request.timeoutMs.coerceAtLeast(1L).takeIf { timedOut },
                workingDirectory = request.workingDirectory,
                pendingTimers = nativeValues["embedded_script.pending_timers"]?.equals("true", ignoreCase = true),
                pendingTimerCount = nativeValues["embedded_script.pending_timer_count"]?.toIntOrNull(),
                pendingScopedFsCallbacks = nativeValues["embedded_script.pending_scoped_fs_callbacks"]?.toIntOrNull(),
                pendingScopedFsStreams = nativeValues["embedded_script.pending_scoped_fs_streams"]?.equals("true", ignoreCase = true),
                pendingScopedFsStreamCount = nativeValues["embedded_script.pending_scoped_fs_stream_count"]?.toIntOrNull(),
                pendingScopedFsWatchCount = nativeValues["embedded_script.pending_scoped_fs_watch_count"]?.toIntOrNull(),
                pendingScopedFsWatchFileCount = nativeValues["embedded_script.pending_scoped_fs_watch_file_count"]?.toIntOrNull(),
                pendingCryptoCallbacks = nativeValues["embedded_script.pending_crypto_callbacks"]?.toIntOrNull(),
                pendingDnsCallbacks = nativeValues["embedded_script.pending_dns_callbacks"]?.toIntOrNull(),
                pendingZlibCallbacks = nativeValues["embedded_script.pending_zlib_callbacks"]?.toIntOrNull(),
                pendingZlibStreams = nativeValues["embedded_script.pending_zlib_streams"]?.equals("true", ignoreCase = true),
                pendingZlibStreamCount = nativeValues["embedded_script.pending_zlib_stream_count"]?.toIntOrNull(),
                pendingAsyncCallbacks = nativeValues["embedded_script.pending_async_callbacks"]?.toIntOrNull(),
                pendingCallbacks = nativeValues["embedded_script.pending_callbacks"]?.equals("true", ignoreCase = true),
            )
        }

        private fun timeoutLifecycle(
            executionId: String,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        ): NodeEmbeddedRuntimeLifecycle {
            return NodeEmbeddedRuntimeLifecycle(TAG, executionId, currentProcessNameCompat(), Process.myPid(), request.sourceName)
                .apply {
                    transition(NodeEmbeddedRuntimeState.STARTING, "client.await")
                    transition(NodeEmbeddedRuntimeState.RUNNING, "client.await")
                    transition(NodeEmbeddedRuntimeState.TIMED_OUT, "timeoutMs=${request.timeoutMs.coerceAtLeast(1L)}")
                    transition(NodeEmbeddedRuntimeState.STOPPING, "client.stopService")
                    transition(NodeEmbeddedRuntimeState.DESTROYED, "client.timeout")
                }
        }

        private fun processFailureLifecycle(
            executionId: String,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            timedOut: Boolean,
        ): NodeEmbeddedRuntimeLifecycle {
            return NodeEmbeddedRuntimeLifecycle(TAG, executionId, currentProcessNameCompat(), Process.myPid(), request.sourceName)
                .apply {
                    transition(NodeEmbeddedRuntimeState.STARTING, "client.await")
                    transition(NodeEmbeddedRuntimeState.RUNNING, "client.await")
                    transition(
                        if (timedOut) NodeEmbeddedRuntimeState.TIMED_OUT else NodeEmbeddedRuntimeState.CRASHED,
                        if (timedOut) "client.timeout" else "client.process_failure",
                    )
                    transition(NodeEmbeddedRuntimeState.STOPPING, "client.stopService")
                    transition(NodeEmbeddedRuntimeState.DESTROYED, "client.process_failure")
                }
        }

        private fun preflightFailureLifecycle(
            executionId: String,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        ): NodeEmbeddedRuntimeLifecycle {
            return NodeEmbeddedRuntimeLifecycle(TAG, executionId, currentProcessNameCompat(), Process.myPid(), request.sourceName)
                .apply {
                    transition(NodeEmbeddedRuntimeState.FAILED, "client.preflight")
                }
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withLifecycle(
            lifecycle: NodeEmbeddedRuntimeLifecycle,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            return copy(
                nativeValues = linkedMapOf<String, String>().apply {
                    putAll(nativeValues)
                    putAll(lifecycle.nativeValues())
                },
            )
        }

        private fun newExecutionId(): String = UUID.randomUUID().toString()

        private fun currentProcessNameCompat(): String = runCatching { App.getProcessNameCompat() }
            .getOrElse { "unknown" }

        private fun clientAwaitTimeoutMs(
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            includeQueueTimeout: Boolean,
        ): Long {
            val scriptTimeoutMs = request.timeoutMs.coerceAtLeast(1L)
            val queueTimeoutMs = if (includeQueueTimeout) {
                NodeExecutionQueuePolicy.normalizedQueueTimeoutMs(
                    NodeExecutionQueuePolicy.normalizeMode(request.queueMode),
                    request.queueTimeoutMs,
                )
            } else {
                0L
            }
            return (scriptTimeoutMs + queueTimeoutMs + CLIENT_SERVICE_START_GRACE_MS).coerceAtLeast(scriptTimeoutMs)
        }

        private fun timeoutNativeValues(
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            elapsedMs: Long,
        ): Map<String, String> {
            val timeoutMs = request.timeoutMs.coerceAtLeast(1L)
            val pendingTimers = hasLikelyPendingTimerSourceHint(request.source)
            val pendingScopedFsCallbacks = hasLikelyScopedFsCallbackSourceHint(request.source)
            val pendingScopedFsStreams = hasLikelyScopedFsStreamSourceHint(request.source)
            val pendingCryptoCallbacks = hasLikelyCryptoCallbackSourceHint(request.source)
            val pendingDnsCallbacks = hasLikelyDnsCallbackSourceHint(request.source)
            val pendingZlibCallbacks = hasLikelyZlibCallbackSourceHint(request.source)
            val pendingZlibStreams = hasLikelyZlibStreamSourceHint(request.source)
            return linkedMapOf(
                "embedded_script.succeeded" to "false",
                "embedded_script.exit_code" to TIMEOUT_EXIT_CODE.toString(),
                "embedded_script.error_name" to "EmbeddedScriptTimeoutError",
                "embedded_script.error_code" to TIMEOUT_ERROR_CODE,
                "embedded_script.source_name" to request.sourceName,
                "embedded_script.working_directory" to request.workingDirectory.orEmpty(),
                "embedded_script.elapsed_ms" to elapsedMs.toString(),
                "embedded_script.timed_out" to "true",
                "embedded_script.timeout_ms" to timeoutMs.toString(),
                "embedded_script.pending_timers" to pendingTimers.toString(),
                "embedded_script.pending_timer_count" to if (pendingTimers) "1" else "0",
                "embedded_script.pending_scoped_fs_callbacks" to if (pendingScopedFsCallbacks) "1" else "0",
                "embedded_script.pending_scoped_fs_streams" to pendingScopedFsStreams.toString(),
                "embedded_script.pending_scoped_fs_stream_count" to if (pendingScopedFsStreams) "1" else "0",
                "embedded_script.pending_crypto_callbacks" to if (pendingCryptoCallbacks) "1" else "0",
                "embedded_script.pending_dns_callbacks" to if (pendingDnsCallbacks) "1" else "0",
                "embedded_script.pending_zlib_callbacks" to if (pendingZlibCallbacks) "1" else "0",
                "embedded_script.pending_zlib_streams" to pendingZlibStreams.toString(),
                "embedded_script.pending_zlib_stream_count" to if (pendingZlibStreams) "1" else "0",
                "embedded_script.pending_async_callbacks" to listOf(
                    pendingScopedFsCallbacks,
                    pendingScopedFsStreams,
                    pendingCryptoCallbacks,
                    pendingDnsCallbacks,
                    pendingZlibCallbacks,
                    pendingZlibStreams,
                ).count { it }.toString(),
                "embedded_script.pending_callbacks" to (
                    pendingScopedFsCallbacks ||
                        pendingScopedFsStreams ||
                        pendingCryptoCallbacks ||
                        pendingDnsCallbacks ||
                        pendingZlibCallbacks ||
                        pendingZlibStreams
                    ).toString(),
            )
        }

        private fun timeoutDiagnosticMessage(
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            elapsedMs: Long,
            nativeValues: Map<String, String>,
        ): String {
            val timeoutMs = request.timeoutMs.coerceAtLeast(1L)
            return buildString {
                append("Embedded Node.js script timed out after ")
                append(timeoutMs)
                append("ms")
                append('\n')
                append("source=")
                append(request.sourceName)
                append('\n')
                append("workingDirectory=")
                append(request.workingDirectory.orEmpty())
                append('\n')
                append("elapsedMs=")
                append(elapsedMs)
                append('\n')
                append("timeoutMs=")
                append(timeoutMs)
                append('\n')
                append("pendingTimers=")
                append(nativeValues["embedded_script.pending_timers"] ?: "unknown")
                append('\n')
                append("pendingScopedFsCallbacks=")
                append(nativeValues["embedded_script.pending_scoped_fs_callbacks"] ?: "unknown")
                append('\n')
                append("pendingScopedFsStreamCount=")
                append(nativeValues["embedded_script.pending_scoped_fs_stream_count"] ?: "unknown")
                append('\n')
                append("pendingCryptoCallbacks=")
                append(nativeValues["embedded_script.pending_crypto_callbacks"] ?: "unknown")
                append('\n')
                append("pendingDnsCallbacks=")
                append(nativeValues["embedded_script.pending_dns_callbacks"] ?: "unknown")
                append('\n')
                append("pendingZlibCallbacks=")
                append(nativeValues["embedded_script.pending_zlib_callbacks"] ?: "unknown")
                append('\n')
                append("pendingZlibStreamCount=")
                append(nativeValues["embedded_script.pending_zlib_stream_count"] ?: "unknown")
            }
        }

        private fun hasLikelyPendingTimerSourceHint(source: String): Boolean {
            return TIMER_SOURCE_HINT_PATTERN.containsMatchIn(source)
        }

        private fun hasLikelyScopedFsCallbackSourceHint(source: String): Boolean {
            return FS_CALLBACK_SOURCE_HINT_PATTERN.containsMatchIn(source)
        }

        private fun hasLikelyScopedFsStreamSourceHint(source: String): Boolean {
            return FS_STREAM_SOURCE_HINT_PATTERN.containsMatchIn(source)
        }

        private fun hasLikelyCryptoCallbackSourceHint(source: String): Boolean {
            return CRYPTO_CALLBACK_SOURCE_HINT_PATTERN.containsMatchIn(source)
        }

        private fun hasLikelyDnsCallbackSourceHint(source: String): Boolean {
            return DNS_CALLBACK_SOURCE_HINT_PATTERN.containsMatchIn(source)
        }

        private fun hasLikelyZlibCallbackSourceHint(source: String): Boolean {
            return ZLIB_CALLBACK_SOURCE_HINT_PATTERN.containsMatchIn(source)
        }

        private fun hasLikelyZlibStreamSourceHint(source: String): Boolean {
            return ZLIB_STREAM_SOURCE_HINT_PATTERN.containsMatchIn(source)
        }

        private fun writeEmbeddedScriptRequestSpool(
            context: Context,
            executionId: String,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        ): File {
            val root = File(File(context.cacheDir, REQUEST_SPOOL_DIR), safeRequestSpoolName(executionId))
            root.deleteRecursivelyIfExists()
            if (!root.mkdirs() && !root.isDirectory) {
                error("Failed to create embedded script request spool directory: ${root.absolutePath}")
            }
            val target = File(root, REQUEST_SPOOL_FILE_NAME)
            val temp = File(root, "$REQUEST_SPOOL_FILE_NAME.tmp")
            temp.writeText(embeddedScriptRequestJson(request).toString(), Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            return target
        }

        private fun safeRequestSpoolName(executionId: String): String {
            return executionId
                .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                .take(96)
                .ifBlank { newExecutionId() }
        }

        private fun Intent.embeddedScriptRequestExtra(
            context: Context,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest {
            requestSpoolFileExtra(context)?.let { file ->
                return embeddedScriptRequestFromJson(JSONObject(file.readText(Charsets.UTF_8))).copy(
                    moduleSourceProvider = moduleSourceProviderExtra(),
                )
            }
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = getStringExtra(EXTRA_SOURCE).orEmpty(),
                sourceName = getStringExtra(EXTRA_SOURCE_NAME).orEmpty().ifBlank { DEFAULT_SOURCE_NAME },
                workingDirectory = getStringExtra(EXTRA_WORKING_DIRECTORY)?.takeIf { it.isNotBlank() },
                sandboxRoot = getStringExtra(EXTRA_SANDBOX_ROOT)?.takeIf { it.isNotBlank() },
                executionId = getStringExtra(EXTRA_EXECUTION_ID)?.takeIf { it.isNotBlank() },
                executionMode = NodeExecutionModePolicy.normalize(getStringExtra(EXTRA_EXECUTION_MODE)),
                timeoutMs = getLongExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS).coerceAtLeast(1L),
                env = envExtra(),
                moduleSources = moduleSourcesExtra(),
                runtimeModuleSources = runtimeModuleSourcesExtra(),
                esmExperimentalEnabled = getBooleanExtra(
                    EXTRA_ESM_EXPERIMENTAL_ENABLED,
                    BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED,
                ),
                dynamicImportExperimentalEnabled = getBooleanExtra(
                    EXTRA_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED,
                    BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED,
                ),
                rawNodeNetworkModulesExperimentalEnabled = getBooleanExtra(
                    EXTRA_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED,
                    BuildConfig.NODEJS_RAW_NETWORK_MODULES_EXPERIMENTAL_ENABLED,
                ),
                workerThreadsExperimentalEnabled = getBooleanExtra(
                    EXTRA_WORKER_THREADS_EXPERIMENTAL_ENABLED,
                    BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED,
                ),
                childProcessExperimentalEnabled = getBooleanExtra(
                    EXTRA_CHILD_PROCESS_EXPERIMENTAL_ENABLED,
                    BuildConfig.NODEJS_CHILD_PROCESS_EXPERIMENTAL_ENABLED,
                ),
                javaInteropExperimentalEnabled = getBooleanExtra(
                    EXTRA_JAVA_INTEROP_EXPERIMENTAL_ENABLED,
                    BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED,
                ),
                spawnDepth = getIntExtra(EXTRA_SPAWN_DEPTH, 0).coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH),
                runtimeAdapter = getStringExtra(EXTRA_RUNTIME_ADAPTER)?.takeIf { it.isNotBlank() },
                processPoolSlot = getStringExtra(EXTRA_PROCESS_POOL_SLOT)?.takeIf { it.isNotBlank() },
                processPoolSlotSource = getStringExtra(EXTRA_PROCESS_POOL_SLOT_SOURCE)?.takeIf { it.isNotBlank() },
                moduleSourceProvider = moduleSourceProviderExtra(),
            )
        }

        private fun Intent.putModuleSourceProvider(provider: INodeJsModuleSourceProvider?) {
            provider ?: return
            putExtra(
                EXTRA_MODULE_SOURCE_PROVIDER,
                Bundle().apply { putBinder(EXTRA_MODULE_SOURCE_PROVIDER_BINDER, provider.asBinder()) },
            )
        }

        private fun Intent.moduleSourceProviderExtra(): INodeJsModuleSourceProvider? {
            val binder = getBundleExtra(EXTRA_MODULE_SOURCE_PROVIDER)
                ?.getBinder(EXTRA_MODULE_SOURCE_PROVIDER_BINDER)
                ?: return null
            return INodeJsModuleSourceProvider.Stub.asInterface(binder)
        }

        private fun Intent.requestSpoolFileExtra(context: Context): File? {
            val path = getStringExtra(EXTRA_REQUEST_SPOOL_FILE)?.takeIf { it.isNotBlank() } ?: return null
            val root = File(context.cacheDir, REQUEST_SPOOL_DIR).canonicalFile
            val file = File(path).canonicalFile
            val rootPrefix = root.path + File.separator
            require(file.path == root.path || file.path.startsWith(rootPrefix)) {
                "Embedded script request spool path escapes cache directory: $path"
            }
            require(file.isFile) {
                "Embedded script request spool file is missing: ${file.absolutePath}"
            }
            return file
        }

        private fun embeddedScriptRequestJson(
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        ): JSONObject {
            return JSONObject().apply {
                put("version", REQUEST_SPOOL_VERSION)
                put("source", request.source)
                put("sourceName", request.sourceName)
                put("workingDirectory", request.workingDirectory)
                put("sandboxRoot", request.sandboxRoot)
                put("executionId", request.executionId)
                put("executionMode", NodeExecutionModePolicy.normalize(request.executionMode))
                put("timeoutMs", request.timeoutMs.coerceAtLeast(1L))
                put("queueMode", request.queueMode)
                put("queueTimeoutMs", request.queueTimeoutMs)
                put("captureStdout", request.captureStdout)
                put("captureStderr", request.captureStderr)
                put("env", orderedStringMapJson(request.env))
                put("moduleSources", orderedStringMapJson(request.moduleSources))
                put("runtimeModuleSources", orderedStringMapJson(request.runtimeModuleSources))
                put("esmExperimentalEnabled", request.esmExperimentalEnabled)
                put("dynamicImportExperimentalEnabled", request.dynamicImportExperimentalEnabled)
                put("rawNodeNetworkModulesExperimentalEnabled", request.rawNodeNetworkModulesExperimentalEnabled)
                put("workerThreadsExperimentalEnabled", request.workerThreadsExperimentalEnabled)
                put("childProcessExperimentalEnabled", request.childProcessExperimentalEnabled)
                put("javaInteropExperimentalEnabled", request.javaInteropExperimentalEnabled)
                put("spawnDepth", request.spawnDepth.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH))
                request.runtimeAdapter?.takeIf { it.isNotBlank() }?.let { put("runtimeAdapter", it) }
                request.processPoolSlot?.takeIf { it.isNotBlank() }?.let { put("processPoolSlot", it) }
                request.processPoolSlotSource?.takeIf { it.isNotBlank() }?.let { put("processPoolSlotSource", it) }
            }
        }

        private fun embeddedScriptRequestFromJson(
            json: JSONObject,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest {
            require(json.optInt("version") == REQUEST_SPOOL_VERSION) {
                "Unsupported embedded script request spool version: ${json.optInt("version")}"
            }
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = json.optString("source"),
                sourceName = json.optString("sourceName").ifBlank { DEFAULT_SOURCE_NAME },
                workingDirectory = json.optString("workingDirectory").takeIf { it.isNotBlank() },
                sandboxRoot = json.optString("sandboxRoot").takeIf { it.isNotBlank() },
                executionId = json.optString("executionId").takeIf { it.isNotBlank() },
                executionMode = NodeExecutionModePolicy.normalize(json.optString("executionMode")),
                timeoutMs = json.optLong("timeoutMs", DEFAULT_TIMEOUT_MS).coerceAtLeast(1L),
                queueMode = json.optString("queueMode").ifBlank { NodeExecutionQueuePolicy.MODE_ENQUEUE_WHEN_BUSY },
                queueTimeoutMs = json.optLong("queueTimeoutMs", 0L),
                captureStdout = json.optBoolean("captureStdout", true),
                captureStderr = json.optBoolean("captureStderr", true),
                env = orderedStringMapFromJson(json.optJSONArray("env")),
                moduleSources = orderedStringMapFromJson(json.optJSONArray("moduleSources")),
                runtimeModuleSources = orderedStringMapFromJson(json.optJSONArray("runtimeModuleSources")),
                esmExperimentalEnabled = json.optBoolean(
                    "esmExperimentalEnabled",
                    BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED,
                ),
                dynamicImportExperimentalEnabled = json.optBoolean(
                    "dynamicImportExperimentalEnabled",
                    BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED,
                ),
                rawNodeNetworkModulesExperimentalEnabled = json.optBoolean(
                    "rawNodeNetworkModulesExperimentalEnabled",
                    BuildConfig.NODEJS_RAW_NETWORK_MODULES_EXPERIMENTAL_ENABLED,
                ),
                workerThreadsExperimentalEnabled = json.optBoolean(
                    "workerThreadsExperimentalEnabled",
                    BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED,
                ),
                childProcessExperimentalEnabled = json.optBoolean(
                    "childProcessExperimentalEnabled",
                    BuildConfig.NODEJS_CHILD_PROCESS_EXPERIMENTAL_ENABLED,
                ),
                javaInteropExperimentalEnabled = json.optBoolean(
                    "javaInteropExperimentalEnabled",
                    BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED,
                ),
                spawnDepth = json.optInt("spawnDepth", 0).coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH),
                runtimeAdapter = json.optString("runtimeAdapter").takeIf { it.isNotBlank() },
                processPoolSlot = json.optString("processPoolSlot").takeIf { it.isNotBlank() },
                processPoolSlotSource = json.optString("processPoolSlotSource").takeIf { it.isNotBlank() },
            )
        }

        private fun orderedStringMapJson(map: Map<String, String>): JSONArray {
            return JSONArray().apply {
                map.forEach { (name, source) ->
                    put(JSONArray().put(name).put(source))
                }
            }
        }

        private fun orderedStringMapFromJson(array: JSONArray?): Map<String, String> {
            if (array == null) {
                return emptyMap()
            }
            return linkedMapOf<String, String>().apply {
                for (index in 0 until array.length()) {
                    val pair = array.optJSONArray(index) ?: continue
                    val name = pair.optString(0)
                    if (name.isNotBlank()) {
                        put(name, pair.optString(1))
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        private fun Intent.resultReceiverExtra(name: String): ResultReceiver? {
            return getParcelableExtra(name)
        }

        private fun Intent.moduleSourcesExtra(): Map<String, String> {
            return sourceMapExtra(EXTRA_MODULE_SOURCE_NAMES, EXTRA_MODULE_SOURCES)
        }

        private fun Intent.runtimeModuleSourcesExtra(): Map<String, String> {
            return sourceMapExtra(EXTRA_RUNTIME_MODULE_SOURCE_NAMES, EXTRA_RUNTIME_MODULE_SOURCES)
        }

        private fun Intent.envExtra(): Map<String, String> {
            return sourceMapExtra(EXTRA_ENV_NAMES, EXTRA_ENV_VALUES)
        }

        private fun Intent.sourceMapExtra(namesExtra: String, sourcesExtra: String): Map<String, String> {
            val names = getStringArrayExtra(namesExtra).orEmpty()
            val sources = getStringArrayExtra(sourcesExtra).orEmpty()
            if (names.size != sources.size) {
                return emptyMap()
            }
            return names.indices.associate { index -> names[index] to sources[index] }
        }

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.toBundle(): Bundle {
            val resultPayloadEncodeStartedAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
            return Bundle().apply {
                val truncations = linkedMapOf<String, Int>()
                putBoolean(EXTRA_RESULT_SUCCEEDED, succeeded)
                exitCode?.let { putInt(EXTRA_RESULT_EXIT_CODE, it) }
                putString(EXTRA_RESULT_TEXT, resultText?.limitResultTransportString(EXTRA_RESULT_TEXT, truncations))
                putString(EXTRA_RESULT_STDOUT, stdout.limitResultTransportString(EXTRA_RESULT_STDOUT, truncations))
                putString(EXTRA_RESULT_STDERR, stderr.limitResultTransportString(EXTRA_RESULT_STDERR, truncations))
                putString(EXTRA_RESULT_ERROR_NAME, errorName)
                putString(EXTRA_RESULT_ERROR_MESSAGE, errorMessage?.limitResultTransportString(EXTRA_RESULT_ERROR_MESSAGE, truncations))
                putString(EXTRA_RESULT_ERROR_STACK, errorStack?.limitResultTransportString(EXTRA_RESULT_ERROR_STACK, truncations))
                putString(EXTRA_RESULT_ERROR_CODE, errorCode)
                putLong(EXTRA_RESULT_ELAPSED_MS, elapsedMs)
                putString(EXTRA_RESULT_PROCESS_NAME, processName)
                putInt(EXTRA_RESULT_PID, pid)
                putString(EXTRA_RESULT_SOURCE_NAME, sourceName)
                putBoolean(EXTRA_RESULT_TIMED_OUT, timedOut)
                timeoutMs?.let { putLong(EXTRA_RESULT_TIMEOUT_MS, it) }
                putString(EXTRA_RESULT_WORKING_DIRECTORY, workingDirectory)
                pendingTimers?.let { putBoolean(EXTRA_RESULT_PENDING_TIMERS, it) }
                pendingTimerCount?.let { putInt(EXTRA_RESULT_PENDING_TIMER_COUNT, it) }
                pendingTimeoutCount?.let { putInt(EXTRA_RESULT_PENDING_TIMEOUT_COUNT, it) }
                pendingIntervalCount?.let { putInt(EXTRA_RESULT_PENDING_INTERVAL_COUNT, it) }
                pendingImmediateCount?.let { putInt(EXTRA_RESULT_PENDING_IMMEDIATE_COUNT, it) }
                pendingScopedFsCallbacks?.let { putInt(EXTRA_RESULT_PENDING_SCOPED_FS_CALLBACKS, it) }
                pendingScopedFsStreams?.let { putBoolean(EXTRA_RESULT_PENDING_SCOPED_FS_STREAMS, it) }
                pendingScopedFsStreamCount?.let { putInt(EXTRA_RESULT_PENDING_SCOPED_FS_STREAM_COUNT, it) }
                pendingScopedFsWatchCount?.let { putInt(EXTRA_RESULT_PENDING_SCOPED_FS_WATCH_COUNT, it) }
                pendingScopedFsWatchFileCount?.let { putInt(EXTRA_RESULT_PENDING_SCOPED_FS_WATCH_FILE_COUNT, it) }
                pendingCryptoCallbacks?.let { putInt(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS, it) }
                pendingDnsCallbacks?.let { putInt(EXTRA_RESULT_PENDING_DNS_CALLBACKS, it) }
                pendingZlibCallbacks?.let { putInt(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS, it) }
                pendingNodeTestCallbacks?.let { putInt(EXTRA_RESULT_PENDING_NODE_TEST_CALLBACKS, it) }
                pendingZlibStreams?.let { putBoolean(EXTRA_RESULT_PENDING_ZLIB_STREAMS, it) }
                pendingZlibStreamCount?.let { putInt(EXTRA_RESULT_PENDING_ZLIB_STREAM_COUNT, it) }
                pendingAsyncCallbacks?.let { putInt(EXTRA_RESULT_PENDING_ASYNC_CALLBACKS, it) }
                pendingCallbacks?.let { putBoolean(EXTRA_RESULT_PENDING_CALLBACKS, it) }
                val transportNativeValues = nativeValues.toTransportNativeValues(truncations)
                if (truncations.isNotEmpty()) {
                    transportNativeValues.appendBoundedTransportEntry(
                        "embedded_script.result_transport.truncated",
                        "true",
                    )
                    transportNativeValues.appendBoundedTransportEntry(
                        "embedded_script.result_transport.truncated_fields",
                        truncations.keys.joinToString(",").limitOmittedKeysList(),
                    )
                    truncations.forEach { (key, originalChars) ->
                        transportNativeValues.appendBoundedTransportEntry(
                            "embedded_script.result_transport.original_chars.$key",
                            originalChars.toString(),
                        )
                    }
                }
                putStringArray(
                    EXTRA_RESULT_NATIVE_PAYLOAD,
                    transportNativeValues.map { (key, value) -> "$key=$value" }.toTypedArray(),
                )
                // Covers local Bundle field population, native-value filtering/truncation and
                // String-array construction. Android parcel serialization and Binder transfer
                // happen after this method returns and are intentionally excluded.
                putLong(
                    EXTRA_RESULT_PAYLOAD_ENCODE_MS,
                    NodeEmbeddedProcessSessionManager.elapsedPhaseMs(resultPayloadEncodeStartedAtNanos),
                )
            }
        }

        private fun Map<String, String>.toTransportNativeValues(
            truncations: MutableMap<String, Int>,
        ): MutableMap<String, String> {
            val transport = linkedMapOf<String, String>()
            val omittedKeys = mutableListOf<String>()
            val originalChars = entries.sumOf { (key, value) ->
                (key.length + 1 + value.length).toLong()
            }
            var sentChars = 0L
            var omittedChars = 0L
            val priorityKeys = linkedSetOf<String>().apply {
                this@toTransportNativeValues.keys
                    .filter(::isCompileCacheTransportPriorityKey)
                    .sorted()
                    .forEach(::add)
                addAll(RESULT_TRANSPORT_NATIVE_PAYLOAD_PRIORITY_KEYS)
            }
            val payloadMaxEntries = (
                RESULT_TRANSPORT_NATIVE_PAYLOAD_MAX_ENTRIES -
                    RESULT_TRANSPORT_METADATA_RESERVED_ENTRIES
                ).coerceAtLeast(0)
            val payloadMaxChars = (
                RESULT_TRANSPORT_NATIVE_PAYLOAD_MAX_CHARS -
                    RESULT_TRANSPORT_METADATA_RESERVED_CHARS
                ).coerceAtLeast(0)

            fun appendTransportEntry(key: String, value: String) {
                val originalEntryChars = (key.length + 1 + value.length).toLong()
                if (key in RESULT_TRANSPORT_NATIVE_PAYLOAD_DEDICATED_KEYS) {
                    omittedKeys += key
                    omittedChars += originalEntryChars
                    return
                }
                val limitedValue = value.limitResultTransportString("native:$key", truncations)
                val transportEntryChars = (key.length + 1 + limitedValue.length).toLong()
                if (transport.size >= payloadMaxEntries || sentChars + transportEntryChars > payloadMaxChars) {
                    omittedKeys += key
                    omittedChars += originalEntryChars
                    return
                }
                transport[key] = limitedValue
                sentChars += transportEntryChars
            }

            priorityKeys.forEach { key ->
                this[key]?.let { appendTransportEntry(key, it) }
            }
            for ((key, value) in this) {
                if (key in priorityKeys) {
                    continue
                }
                appendTransportEntry(key, value)
            }
            val omittedCount = omittedKeys.size
            if (omittedCount > 0) {
                val sentEntries = transport.size
                transport.appendBoundedTransportEntry("embedded_script.result_transport.native_payload_limited", "true")
                transport.appendBoundedTransportEntry("embedded_script.result_transport.native_payload_hard_cap", "true")
                transport.appendBoundedTransportEntry("embedded_script.result_transport.native_payload_original_entries", size.toString())
                transport.appendBoundedTransportEntry("embedded_script.result_transport.native_payload_sent_entries", sentEntries.toString())
                transport.appendBoundedTransportEntry("embedded_script.result_transport.native_payload_omitted_entries", omittedCount.toString())
                transport.appendBoundedTransportEntry("embedded_script.result_transport.native_payload_original_chars", originalChars.toString())
                transport.appendBoundedTransportEntry("embedded_script.result_transport.native_payload_sent_chars", sentChars.toString())
                transport.appendBoundedTransportEntry("embedded_script.result_transport.native_payload_omitted_chars", omittedChars.toString())
                transport.appendBoundedTransportEntry(
                    "embedded_script.result_transport.native_payload_max_entries",
                    RESULT_TRANSPORT_NATIVE_PAYLOAD_MAX_ENTRIES.toString(),
                )
                transport.appendBoundedTransportEntry(
                    "embedded_script.result_transport.native_payload_max_chars",
                    RESULT_TRANSPORT_NATIVE_PAYLOAD_MAX_CHARS.toString(),
                )
                transport.appendBoundedTransportEntry(
                    "embedded_script.result_transport.native_payload_metadata_reserved_entries",
                    RESULT_TRANSPORT_METADATA_RESERVED_ENTRIES.toString(),
                )
                transport.appendBoundedTransportEntry(
                    "embedded_script.result_transport.native_payload_metadata_reserved_chars",
                    RESULT_TRANSPORT_METADATA_RESERVED_CHARS.toString(),
                )
                transport.appendBoundedTransportEntry(
                    "embedded_script.result_transport.native_payload_omitted_keys",
                    omittedKeys.joinToString(",").limitOmittedKeysList(),
                )
            }
            return transport
        }

        private fun isCompileCacheTransportPriorityKey(key: String): Boolean =
            key.startsWith("embedded_script.compile_cache.") ||
                key.startsWith("timing.compile_cache_")

        private fun MutableMap<String, String>.appendBoundedTransportEntry(
            key: String,
            value: String,
        ): Boolean {
            val previous = this[key]
            val previousChars = previous?.let { (key.length + 1 + it.length).toLong() } ?: 0L
            val projectedEntries = size + if (previous == null) 1 else 0
            val projectedChars = entries.sumOf { (existingKey, existingValue) ->
                (existingKey.length + 1 + existingValue.length).toLong()
            } - previousChars + (key.length + 1 + value.length).toLong()
            if (
                projectedEntries > RESULT_TRANSPORT_NATIVE_PAYLOAD_MAX_ENTRIES ||
                projectedChars > RESULT_TRANSPORT_NATIVE_PAYLOAD_MAX_CHARS
            ) {
                return false
            }
            put(key, value)
            return true
        }

        private fun String.limitOmittedKeysList(): String {
            return if (length <= RESULT_TRANSPORT_OMITTED_KEYS_MAX_CHARS) {
                this
            } else {
                take(RESULT_TRANSPORT_OMITTED_KEYS_MAX_CHARS) + "..."
            }
        }

        private fun String.limitResultTransportString(
            key: String,
            truncations: MutableMap<String, Int>,
        ): String {
            if (length <= RESULT_TRANSPORT_STRING_MAX_CHARS) {
                return this
            }
            truncations[key] = length
            val suffix = "\n...[truncated by AutoJs6 Embedded Node result transport; originalChars=$length]"
            val keep = (RESULT_TRANSPORT_STRING_MAX_CHARS - suffix.length).coerceAtLeast(0)
            return take(keep) + suffix
        }

        private fun Bundle?.toEmbeddedScriptResult(): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val payload = this?.getStringArray(EXTRA_RESULT_NATIVE_PAYLOAD)
                ?.mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
                }
                ?.toMap()
                .orEmpty()
            val decodedResult = NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
                succeeded = this?.getBoolean(EXTRA_RESULT_SUCCEEDED) ?: false,
                exitCode = this?.takeIf { it.containsKey(EXTRA_RESULT_EXIT_CODE) }?.getInt(EXTRA_RESULT_EXIT_CODE),
                resultText = this?.getString(EXTRA_RESULT_TEXT),
                stdout = this?.getString(EXTRA_RESULT_STDOUT).orEmpty(),
                stderr = this?.getString(EXTRA_RESULT_STDERR).orEmpty(),
                errorName = this?.getString(EXTRA_RESULT_ERROR_NAME),
                errorMessage = this?.getString(EXTRA_RESULT_ERROR_MESSAGE),
                errorStack = this?.getString(EXTRA_RESULT_ERROR_STACK),
                errorCode = this?.getString(EXTRA_RESULT_ERROR_CODE),
                elapsedMs = this?.getLong(EXTRA_RESULT_ELAPSED_MS) ?: 0L,
                processName = this?.getString(EXTRA_RESULT_PROCESS_NAME).orEmpty(),
                pid = this?.getInt(EXTRA_RESULT_PID) ?: -1,
                sourceName = this?.getString(EXTRA_RESULT_SOURCE_NAME).orEmpty(),
                nativeValues = payload,
                timedOut = this?.getBoolean(EXTRA_RESULT_TIMED_OUT) ?: false,
                timeoutMs = this?.takeIf { it.containsKey(EXTRA_RESULT_TIMEOUT_MS) }?.getLong(EXTRA_RESULT_TIMEOUT_MS),
                workingDirectory = this?.getString(EXTRA_RESULT_WORKING_DIRECTORY),
                pendingTimers = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_TIMERS) }
                    ?.getBoolean(EXTRA_RESULT_PENDING_TIMERS),
                pendingTimerCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_TIMER_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_TIMER_COUNT),
                pendingTimeoutCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_TIMEOUT_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_TIMEOUT_COUNT),
                pendingIntervalCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_INTERVAL_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_INTERVAL_COUNT),
                pendingImmediateCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_IMMEDIATE_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_IMMEDIATE_COUNT),
                pendingScopedFsCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_SCOPED_FS_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_SCOPED_FS_CALLBACKS),
                pendingScopedFsStreams = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_SCOPED_FS_STREAMS) }
                    ?.getBoolean(EXTRA_RESULT_PENDING_SCOPED_FS_STREAMS),
                pendingScopedFsStreamCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_SCOPED_FS_STREAM_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_SCOPED_FS_STREAM_COUNT),
                pendingScopedFsWatchCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_SCOPED_FS_WATCH_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_SCOPED_FS_WATCH_COUNT),
                pendingScopedFsWatchFileCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_SCOPED_FS_WATCH_FILE_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_SCOPED_FS_WATCH_FILE_COUNT),
                pendingCryptoCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS),
                pendingDnsCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_DNS_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_DNS_CALLBACKS),
                pendingZlibCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS),
                pendingNodeTestCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_NODE_TEST_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_NODE_TEST_CALLBACKS),
                pendingZlibStreams = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ZLIB_STREAMS) }
                    ?.getBoolean(EXTRA_RESULT_PENDING_ZLIB_STREAMS),
                pendingZlibStreamCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ZLIB_STREAM_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_ZLIB_STREAM_COUNT),
                pendingAsyncCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ASYNC_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_ASYNC_CALLBACKS),
                pendingCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_CALLBACKS) }
                    ?.getBoolean(EXTRA_RESULT_PENDING_CALLBACKS),
            )
            val resultPayloadEncodeMs = this
                ?.takeIf { it.containsKey(EXTRA_RESULT_PAYLOAD_ENCODE_MS) }
                ?.getLong(EXTRA_RESULT_PAYLOAD_ENCODE_MS)
            return if (resultPayloadEncodeMs == null || resultPayloadEncodeMs < 0L) {
                decodedResult
            } else {
                decodedResult.withClientPhaseTimings(
                    mapOf(
                        NodeEmbeddedProcessSessionManager.PhaseTiming.RESULT_PAYLOAD_ENCODE to
                            resultPayloadEncodeMs,
                    ),
                )
            }
        }

        private const val EXTRA_RESULT_SUCCEEDED = "result_succeeded"
        private const val EXTRA_RESULT_EXIT_CODE = "result_exit_code"
        private const val EXTRA_RESULT_TEXT = "result_text"
        private const val EXTRA_RESULT_STDOUT = "result_stdout"
        private const val EXTRA_RESULT_STDERR = "result_stderr"
        private const val EXTRA_RESULT_ERROR_NAME = "result_error_name"
        private const val EXTRA_RESULT_ERROR_MESSAGE = "result_error_message"
        private const val EXTRA_RESULT_ERROR_STACK = "result_error_stack"
        private const val EXTRA_RESULT_ERROR_CODE = "result_error_code"
        private const val EXTRA_RESULT_ELAPSED_MS = "result_elapsed_ms"
        private const val EXTRA_RESULT_PAYLOAD_ENCODE_MS = "result_payload_encode_ms"
        private const val EXTRA_RESULT_PROCESS_NAME = "result_process_name"
        private const val EXTRA_RESULT_PID = "result_pid"
        private const val EXTRA_RESULT_SOURCE_NAME = "result_source_name"
        private const val EXTRA_RESULT_TIMED_OUT = "result_timed_out"
        private const val EXTRA_RESULT_TIMEOUT_MS = "result_timeout_ms"
        private const val EXTRA_RESULT_WORKING_DIRECTORY = "result_working_directory"
        private const val EXTRA_RESULT_PENDING_TIMERS = "result_pending_timers"
        private const val EXTRA_RESULT_PENDING_TIMER_COUNT = "result_pending_timer_count"
        private const val EXTRA_RESULT_PENDING_TIMEOUT_COUNT = "result_pending_timeout_count"
        private const val EXTRA_RESULT_PENDING_INTERVAL_COUNT = "result_pending_interval_count"
        private const val EXTRA_RESULT_PENDING_IMMEDIATE_COUNT = "result_pending_immediate_count"
        private const val EXTRA_RESULT_PENDING_SCOPED_FS_CALLBACKS = "result_pending_scoped_fs_callbacks"
        private const val EXTRA_RESULT_PENDING_SCOPED_FS_STREAMS = "result_pending_scoped_fs_streams"
        private const val EXTRA_RESULT_PENDING_SCOPED_FS_STREAM_COUNT = "result_pending_scoped_fs_stream_count"
        private const val EXTRA_RESULT_PENDING_SCOPED_FS_WATCH_COUNT = "result_pending_scoped_fs_watch_count"
        private const val EXTRA_RESULT_PENDING_SCOPED_FS_WATCH_FILE_COUNT = "result_pending_scoped_fs_watch_file_count"
        private const val EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS = "result_pending_crypto_callbacks"
        private const val EXTRA_RESULT_PENDING_DNS_CALLBACKS = "result_pending_dns_callbacks"
        private const val EXTRA_RESULT_PENDING_ZLIB_CALLBACKS = "result_pending_zlib_callbacks"
        private const val EXTRA_RESULT_PENDING_NODE_TEST_CALLBACKS = "result_pending_node_test_callbacks"
        private const val EXTRA_RESULT_PENDING_ZLIB_STREAMS = "result_pending_zlib_streams"
        private const val EXTRA_RESULT_PENDING_ZLIB_STREAM_COUNT = "result_pending_zlib_stream_count"
        private const val EXTRA_RESULT_PENDING_ASYNC_CALLBACKS = "result_pending_async_callbacks"
        private const val EXTRA_RESULT_PENDING_CALLBACKS = "result_pending_callbacks"
        private const val EXTRA_RESULT_NATIVE_PAYLOAD = "result_native_payload"
    }
}

class NativeNodeEmbeddedScriptServiceSlot1 : NativeNodeEmbeddedScriptService()

class NativeNodeEmbeddedScriptServiceSlot2 : NativeNodeEmbeddedScriptService()

class NativeNodeEmbeddedInteractiveStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context, intent)) {
            Log.w(TAG, "interactive.stop.broadcast_ignored action=${intent.action.orEmpty()}")
        }
    }

    private companion object {
        private const val TAG = "NativeNodeEmbeddedInteractiveStopReceiver"
    }
}

private fun NodeBridgeRequest.dispatchExceptionResponse(error: Throwable): NodeBridgeResponse {
    return NodeBridgeResponse.failure(
        id,
        NodeBridgeError.providerFailed(
            message = error.message ?: error.javaClass.name,
            module = module,
            method = method,
            stack = error.stackTraceToString(),
        ),
    )
}
