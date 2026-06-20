package org.autojs.autojs.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
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
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import kotlin.system.exitProcess

class NativeNodeEmbeddedScriptService : Service() {

    @Volatile
    private var exitScheduled = false
    @Volatile
    private var activeLifecycle: NodeEmbeddedRuntimeLifecycle? = null
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
    private var foregroundServiceCreator: ForegroundServiceCreator? = null

    private val foregroundServiceType = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else -> FOREGROUND_SERVICE_TYPE_UNKNOWN
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(
            TAG,
            "android.policy.embedded_service " +
                    NodeAndroidRuntimePolicy.embeddedServiceDiagnostics(
                        this,
                        NativeNodeEmbeddedScriptService::class.java,
                        foregroundServiceType,
                    ),
        )
        startScriptForeground()
        Log.i(TAG, "PROCESS STARTED processName=${currentProcessName()} pid=${Process.myPid()}")
    }

    override fun onDestroy() {
        val interactiveExecutionId = activeExecutionId
            ?.takeIf { activeExecutionMode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING }
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
        foregroundServiceCreator = null
        scheduleProcessExit(0)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_RUN_SCRIPT -> {
                Thread {
                    runScript(intent, startId)
                }.apply {
                    name = "NativeNodeEmbeddedScriptService-$startId"
                    start()
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

    private fun runScript(intent: Intent, startId: Int) {
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val startedAt = android.os.SystemClock.elapsedRealtime()
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
                ).toBundle(),
            )
            stopServiceIfQueueIdle(startId)
            return
        }
        val preparedServiceRequest = try {
            NativeNodeEmbeddedRuntimeBridge.prepareEmbeddedScriptRequest(serviceRequest)
        } catch (e: Throwable) {
            receiver?.send(
                RESULT_SCRIPT,
                NativeNodeEmbeddedRuntimeBridge
                    .embeddedScriptRequestPreparationFailure(serviceRequest, e, startedAt)
                    .toBundle(),
            )
            stopServiceIfQueueIdle(startId)
            return
        }
        val launchSurface = intent.getStringExtra(EXTRA_LAUNCH_SURFACE)
            ?.takeIf { it.isNotBlank() }
            ?: NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT
        NativeNodeEmbeddedRuntimeBridge.validateEmbeddedScriptRequestBeforeServiceStart(preparedServiceRequest, launchSurface)?.let { failure ->
            receiver?.send(RESULT_SCRIPT, failure.toBundle())
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
        val workerThreadsExperimentalEnabled = preparedServiceRequest.workerThreadsExperimentalEnabled
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
        val interactiveSession = executionMode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING &&
            launchSurface == NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION
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
                activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("starting"),
            )
            updateInteractiveForeground(executionId, sourceName)
        }
        val processName = currentProcessName()
        val pid = Process.myPid()
        val queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY
        val queueTimeoutMs = 0L
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
                    message = slotDecision.failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("queue_rejected"),
                    nativeValues = mapOf(
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
                ).toBundle(),
            )
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
            lifecycleStopRequestFile?.parentFile?.deleteRecursively()
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
        val enrichedRuntimeModuleSources = runtimeModuleSources
            .withHostAppInfo()
            .withDeviceInfo()
            .withEngineInfo(engineInfo.toJson())
            .withBridgePermissionManifest(workingDirectory)
            .withBridgeLimitPolicy(bridgeLimits)
            .withBridgeLiveConfig(bridgeSession.configJson())
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
            executionMode = executionMode,
            timeoutMs = timeoutMs,
            captureStdout = serviceRequest.captureStdout,
            captureStderr = serviceRequest.captureStderr,
            env = env,
            moduleSources = moduleSources,
            runtimeModuleSources = enrichedRuntimeModuleSources,
            esmExperimentalEnabled = esmExperimentalEnabled,
            dynamicImportExperimentalEnabled = dynamicImportExperimentalEnabled,
            workerThreadsExperimentalEnabled = workerThreadsExperimentalEnabled,
            javaInteropExperimentalEnabled = javaInteropExperimentalEnabled,
            executionId = executionId,
            queueMode = queueMode,
            queueTimeoutMs = NodeExecutionQueuePolicy.normalizedQueueTimeoutMs(queueMode, queueTimeoutMs),
        )
        val lifecycle = NodeEmbeddedRuntimeLifecycle(TAG, executionId, processName, pid, sourceName)
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
                    "esmExperimentalEnabled=$esmExperimentalEnabled " +
                    "dynamicImportLocalEnabled=$dynamicImportLocalEnabled " +
                    "dynamicImportExperimentalEnabled=$dynamicImportExperimentalEnabled " +
                    "workerThreadsExperimentalEnabled=$workerThreadsExperimentalEnabled " +
                    "javaInteropExperimentalEnabled=$javaInteropExperimentalEnabled " +
                    "queueMode=$queueMode queued=${slotLease.queued} queueWaitMs=${slotLease.queueWaitMs} " +
                    "executionMode=$executionMode " +
                    "bridgeLimits=${bridgeLimits.toJson()} " +
                    "runtimeBudget=${runtimeBudget.toJsonObject()} " +
                    "runtimeFirst=${enrichedRuntimeModuleSources.keys.firstOrNull().orEmpty()}",
        )
        try {
            timeline?.mark("bridge_transport_start", mapOf("transport" to "file"))
            bridgeSession.start()
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
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("running", runningNativeValues),
                    nativeValues = runningNativeValues,
                )
            }
            timeline?.mark("native_enter")
            val nativeResult = NativeNodeEmbeddedRuntimeBridge.runEmbeddedScriptInCurrentProcess(
                context = this,
                request = request,
            )
            timeline?.recordNativeResult(nativeResult)
            val result = dispatchQueuedBridgeRequests(bridgeSession.appendNativeValues(nativeResult))
                .withExecutionMode(executionMode, launchSurface)
                .withRuntimeBudget(runtimeBudget)
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
                    message = completed.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary(state, completed.nativeValues),
                    nativeValues = lifecycle.nativeValues() + completed.nativeValues,
                )
            }
            timeline?.mark("destroy_complete", mapOf("state" to lifecycle.state.name))
            receiver?.send(RESULT_SCRIPT, completed.withLifecycle(lifecycle).withQueueLease(slotLease).withTimeline(timeline).toBundle())
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
                    message = e.message ?: e.javaClass.name,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("exception"),
                    nativeValues = lifecycle.nativeValues(),
                )
            }
            receiver?.send(
                RESULT_SCRIPT,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
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
                    nativeValues = emptyMap(),
                ).withLifecycle(lifecycle).withQueueLease(slotLease).withRuntimeBudget(runtimeBudget).withTimeline(timeline).toBundle(),
            )
        } finally {
            File(File(cacheDir, REQUEST_SPOOL_DIR), safeRequestSpoolName(executionId)).deleteRecursively()
            activeHeartbeatMonitor?.stop("script.finally")
            activeHeartbeatMonitor = null
            lifecycleStopRequestFile?.parentFile?.deleteRecursively()
            bridgeSession.stop()
            val queueStatus = releaseExecutionSlot(executionId)
            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            Log.i(
                TAG,
                "script.exit executionId=$executionId processName=$processName pid=$pid " +
                        "sourceName=$sourceName state=${lifecycle.state.name} elapsed=${elapsed}ms",
            )
            if (queueStatus.idle) {
                stopSelf(startId)
                scheduleProcessExit(0)
            } else {
                Log.i(
                    TAG,
                    "script.queue.keep_service_alive executionId=$executionId active=${queueStatus.activeExecutionId.orEmpty()} " +
                    "queued=${queueStatus.queuedCount}",
                )
            }
            if (interactiveSession) {
                clearActiveInteractiveSession(executionId)
            }
        }
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
            stopSelf(startId)
            scheduleProcessExit(0)
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

    private fun Map<String, String>.withBridgePermissionManifest(workingDirectory: String?): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            putAll(this@withBridgePermissionManifest)
            if (!containsKey(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME)) {
                put(
                    NodeBridgePermissionManifest.RUNTIME_MODULE_NAME,
                    NodeBridgePermissionManifest.runtimeModuleSourceForWorkingDirectory(workingDirectory),
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
        private val thread = Thread {
            loop()
        }.apply {
            name = "AutoJs6NodeBridgeFileTransport-${root.name}"
            isDaemon = true
        }

        init {
            root.deleteRecursively()
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
            provider.destroy()
            requestDir.parentFile?.deleteRecursively()
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
        Handler(Looper.getMainLooper()).postDelayed({
            exitProcess(status)
        }, PROCESS_EXIT_DELAY_MS)
    }

    private fun startScriptForeground() {
        foregroundServiceCreator = ForegroundServiceCreator.Builder(this)
            .setClassName(NativeNodeEmbeddedScriptService::class.java)
            .setIntent(Intent(this, MainActivity::class.java))
            .setNotificationId(NOTIFICATION_ID)
            .setServiceName("AutoJs6 embedded Node script")
            .setServiceDescription("Runs isolated embedded Node scripts")
            .setNotificationTitle("AutoJs6 embedded Node script")
            .setNotificationContent("Running isolated embedded Node script")
            .create()
            .apply { startForeground(foregroundServiceType) }
    }

    private fun currentProcessName(): String = runCatching { App.getProcessNameCompat() }
        .getOrElse { "unknown" }

    private sealed class ExecutionSlotDecision {

        data class Acquired(val lease: ExecutionSlotLease) : ExecutionSlotDecision()

        data class Rejected(val failure: ExecutionSlotFailure) : ExecutionSlotDecision()
    }

    private data class ExecutionSlotLease(
        val executionId: String,
        val queueMode: String,
        val queued: Boolean,
        val queueWaitMs: Long,
        val queuePosition: Int,
        val maxQueuedExecutions: Int,
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

    private fun stopInteractiveLongRunning(intent: Intent, startId: Int) {
        val requestedExecutionId = intent.getStringExtra(EXTRA_EXECUTION_ID)?.takeIf { it.isNotBlank() }
        val executionId = activeExecutionId
        if (executionId == null || (requestedExecutionId != null && requestedExecutionId != executionId)) {
            Log.w(
                TAG,
                "interactive.stop.ignored requested=${requestedExecutionId.orEmpty()} active=${executionId.orEmpty()}",
            )
            stopSelf(startId)
            return
        }
        activeLifecycle?.let { lifecycle ->
            if (lifecycle.state !in setOf(
                    NodeEmbeddedRuntimeState.STOPPING,
                    NodeEmbeddedRuntimeState.STOPPED,
                    NodeEmbeddedRuntimeState.DESTROYED,
                )
            ) {
                lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "interactive.stop")
            }
        }
        val stoppingNativeValues = activeHeartbeatMonitor?.nativeValuesSnapshot("stopping")
            ?: activeLifecycle?.nativeValues().orEmpty()
        activeHeartbeatMonitor?.stop("interactive.stop")
        activeHeartbeatMonitor = null
        NodeLifecycleCheckpointStore.writeStopRequest(
            file = activeLifecycleStopRequestFile,
            executionId = executionId,
            reason = "interactive.stop.requested",
        )
        NodeInteractiveLongRunningStatusStore.write(
            context = this,
            executionId = executionId,
            state = NodeInteractiveLongRunningStatusStore.STATE_STOPPING,
            sourceName = activeSourceName,
            workingDirectory = activeWorkingDirectory,
            startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L },
            stopRequested = true,
            message = "interactive.stop.requested",
            activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("stopping", stoppingNativeValues),
            nativeValues = stoppingNativeValues,
        )
        val sourceName = activeSourceName
        val workingDirectory = activeWorkingDirectory
        val startedAtEpochMs = activeStartedAtEpochMs.takeIf { it > 0L }
        Handler(Looper.getMainLooper()).postDelayed({
            NodeInteractiveLongRunningStatusStore.write(
                context = this,
                executionId = executionId,
                state = NodeInteractiveLongRunningStatusStore.STATE_STOPPED,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                startedAtEpochMs = startedAtEpochMs,
                stopRequested = true,
                message = "interactive.stop.accepted",
                activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("process_exit_scheduled", stoppingNativeValues),
                nativeValues = stoppingNativeValues,
            )
            stopSelf(startId)
            scheduleProcessExit(0)
        }, NodeLifecycleCheckpointStore.STOP_GRACE_MS)
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
        val stopIntent = Intent(this, NativeNodeEmbeddedScriptService::class.java).apply {
            action = ACTION_STOP_INTERACTIVE
            putExtra(EXTRA_EXECUTION_ID, executionId)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            INTERACTIVE_STOP_REQUEST_CODE,
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
        private val ACTIVE_EXECUTION_ID = AtomicReference<String?>(null)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private val EXECUTION_QUEUE_LOCK = Object()
        private val QUEUED_EXECUTION_IDS = ArrayDeque<String>()
        private val CANCELLED_QUEUED_EXECUTION_IDS = mutableSetOf<String>()
        private const val ACTION_RUN_SCRIPT = "org.autojs.autojs.engine.action.RUN_NATIVE_NODE_EMBEDDED_SCRIPT"
        private const val ACTION_STOP_INTERACTIVE = NodeInteractiveLongRunningStatusStore.STOP_ACTION_NAME
        private const val ACTION_SIMULATE_FOREGROUND_TIMEOUT =
            "org.autojs.autojs.engine.action.SIMULATE_NATIVE_NODE_FOREGROUND_TIMEOUT"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_SOURCE_NAME = "source_name"
        private const val EXTRA_WORKING_DIRECTORY = "working_directory"
        private const val EXTRA_EXECUTION_MODE = "execution_mode"
        private const val EXTRA_LAUNCH_SURFACE = "launch_surface"
        private const val EXTRA_TIMEOUT_MS = "timeout_ms"
        private const val EXTRA_ESM_EXPERIMENTAL_ENABLED = "esm_experimental_enabled"
        private const val EXTRA_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED = "dynamic_import_experimental_enabled"
        private const val EXTRA_WORKER_THREADS_EXPERIMENTAL_ENABLED = "worker_threads_experimental_enabled"
        private const val EXTRA_JAVA_INTEROP_EXPERIMENTAL_ENABLED = "java_interop_experimental_enabled"
        private const val EXTRA_SPAWN_DEPTH = "spawn_depth"
        private const val EXTRA_EXECUTION_ID = "execution_id"
        private const val EXTRA_FOREGROUND_SERVICE_TYPE = "foreground_service_type"
        private const val EXTRA_ENV_NAMES = "env_names"
        private const val EXTRA_ENV_VALUES = "env_values"
        private const val EXTRA_MODULE_SOURCE_NAMES = "module_source_names"
        private const val EXTRA_MODULE_SOURCES = "module_sources"
        private const val EXTRA_RUNTIME_MODULE_SOURCE_NAMES = "runtime_module_source_names"
        private const val EXTRA_RUNTIME_MODULE_SOURCES = "runtime_module_sources"
        private const val EXTRA_REQUEST_SPOOL_FILE = "request_spool_file"
        private const val HOST_APP_INFO_RUNTIME_MODULE_NAME = "autojs6:host-app-info"
        private const val DEVICE_INFO_RUNTIME_MODULE_NAME = "autojs6:device-info"
        private const val ENGINE_INFO_RUNTIME_MODULE_NAME = "autojs6:engine-info"
        private const val BRIDGE_LIVE_CONFIG_RUNTIME_MODULE_NAME = "autojs6:bridge-live-config"
        private const val REQUEST_SPOOL_DIR = "embedded-node-script-requests"
        private const val REQUEST_SPOOL_FILE_NAME = "request.json"
        private const val REQUEST_SPOOL_VERSION = 1
        private const val RESULT_SCRIPT = 1
        private const val NOTIFICATION_ID = 0xA6E4
        private const val INTERACTIVE_CONTENT_REQUEST_CODE = 0xA6E5
        private const val INTERACTIVE_STOP_REQUEST_CODE = 0xA6E6
        private const val PROCESS_EXIT_DELAY_MS = 250L
        private const val DEFAULT_INTERACTIVE_TIMEOUT_MS = 3_600_000L
        private const val CLIENT_QUEUE_PROCESS_SETTLE_MS = 650L
        private const val BRIDGE_DISPATCH_WAIT_MS = 1000L
        private const val BRIDGE_LIVE_POLL_INTERVAL_MS = 10L
        private const val BRIDGE_LIVE_STOP_JOIN_MS = 1000L
        private const val BRIDGE_LIVE_DRAIN_ITERATIONS = 25
        private const val HEARTBEAT_STOP_JOIN_MS = 250L
        private const val RESULT_TRANSPORT_STRING_MAX_CHARS = 32_768
        private const val CLIENT_SERVICE_START_GRACE_MS = 10_000L
        private const val TIMEOUT_EXIT_CODE = 124
        private const val TIMEOUT_ERROR_CODE = NodeRuntimeErrorCodes.LEGACY_SCRIPT_TIMEOUT
        private const val DEFAULT_SOURCE_NAME = "<embedded-script>"
        private const val DEFAULT_TIMEOUT_MS = 3000L
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
            try {
                return runScript(
                    context,
                    request.copy(
                        executionId = executionId,
                        queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                        queueTimeoutMs = 0L,
                    ),
                ).withClientQueueLease(slotLease)
            } finally {
                settleBetweenQueuedExecutions()
                releaseExecutionSlot(executionId)
            }
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

        fun runScript(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val executionId = request.executionId?.takeIf { it.isNotBlank() } ?: newExecutionId()
            val requestWithExecutionId = request.copy(executionId = executionId)
            val requestForService = try {
                NativeNodeEmbeddedRuntimeBridge.prepareEmbeddedScriptRequest(requestWithExecutionId)
            } catch (error: Throwable) {
                return NativeNodeEmbeddedRuntimeBridge
                    .embeddedScriptRequestPreparationFailure(requestWithExecutionId, error)
                    .withLifecycle(preflightFailureLifecycle(executionId, requestWithExecutionId))
            }
            NativeNodeEmbeddedRuntimeBridge.validateEmbeddedScriptRequestBeforeServiceStart(requestForService, launchSurface)?.let {
                return it.withLifecycle(preflightFailureLifecycle(executionId, request))
            }
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val appContext = context.applicationContext
            val completion = CountDownLatch(1)
            val result = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>()
            val receiver = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode != RESULT_SCRIPT) {
                        return
                    }
                    result.set(resultData.toEmbeddedScriptResult())
                    completion.countDown()
                }
            }
            var requestSpoolFile: File? = null
            val intent = Intent(appContext, NativeNodeEmbeddedScriptService::class.java).apply {
                action = ACTION_RUN_SCRIPT
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
                putExtra(EXTRA_SOURCE_NAME, requestForService.sourceName)
                putExtra(EXTRA_WORKING_DIRECTORY, requestForService.workingDirectory)
                putExtra(EXTRA_EXECUTION_MODE, requestForService.executionMode)
                putExtra(EXTRA_LAUNCH_SURFACE, launchSurface)
                putExtra(EXTRA_TIMEOUT_MS, requestForService.timeoutMs.coerceAtLeast(1L))
                putExtra(EXTRA_ESM_EXPERIMENTAL_ENABLED, requestForService.esmExperimentalEnabled)
                putExtra(EXTRA_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED, requestForService.dynamicImportExperimentalEnabled)
                putExtra(EXTRA_WORKER_THREADS_EXPERIMENTAL_ENABLED, requestForService.workerThreadsExperimentalEnabled)
                putExtra(EXTRA_JAVA_INTEROP_EXPERIMENTAL_ENABLED, requestForService.javaInteropExperimentalEnabled)
                putExtra(EXTRA_SPAWN_DEPTH, requestForService.spawnDepth.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH))
                putExtra(EXTRA_EXECUTION_ID, executionId)
            }
            var component: android.content.ComponentName? = null
            try {
                requestSpoolFile = writeEmbeddedScriptRequestSpool(appContext, executionId, requestForService)
                intent.putExtra(EXTRA_REQUEST_SPOOL_FILE, requestSpoolFile.absolutePath)
                val startOutcome = startEmbeddedScriptService(appContext, intent)
                component = startOutcome.component ?: return serviceStartFailureResult(
                    request = request,
                    message = "Failed to start embedded script service.",
                    startedAt = startedAt,
                    executionId = executionId,
                    startOutcome = startOutcome,
                )
                val completed = completion.await(clientAwaitTimeoutMs(request.timeoutMs), TimeUnit.MILLISECONDS)
                if (!completed) {
                    appContext.stopService(Intent().setComponent(component))
                    val lifecycle = timeoutLifecycle(executionId, requestForService)
                    return processCrashedOrTimedOut(
                        request = requestForService,
                        message = "Embedded script process crashed or timed out.",
                        startedAt = startedAt,
                        timedOut = true,
                        executionId = executionId,
                        lifecycleValues = lifecycle.nativeValues(),
                    )
                }
            } catch (e: InterruptedException) {
                component?.let { appContext.stopService(Intent().setComponent(it)) }
                Thread.currentThread().interrupt()
                throw ScriptInterruptedException(e)
            } catch (e: Throwable) {
                component?.let { appContext.stopService(Intent().setComponent(it)) }
                return processCrashedOrTimedOut(
                    requestForService,
                    "Embedded script process crashed or timed out.\n${e.message ?: e.javaClass.name}",
                    e,
                    startedAt = startedAt,
                    executionId = executionId,
                )
            } finally {
                requestSpoolFile?.parentFile?.deleteRecursively()
            }
            return result.get() ?: processCrashedOrTimedOut(
                request = requestForService,
                message = "Embedded script process crashed or timed out.",
                startedAt = startedAt,
                executionId = executionId,
            )
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
            val executionId = request.executionId?.takeIf { it.isNotBlank() } ?: newExecutionId()
            val appContext = context.applicationContext
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val requestWithExecutionId = request.copy(
                executionId = executionId,
                executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                timeoutMs = request.timeoutMs.takeIf { it > 0L } ?: DEFAULT_INTERACTIVE_TIMEOUT_MS,
                queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                queueTimeoutMs = 0L,
            )
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
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("preflight_failed"),
                    nativeValues = failure.nativeValues,
                )
                return InteractiveLongRunningSession(
                    executionId = executionId,
                    started = false,
                    status = status,
                    error = failure.withLifecycle(preflightFailureLifecycle(executionId, requestWithExecutionId)),
                )
            }
            NativeNodeEmbeddedRuntimeBridge.validateEmbeddedScriptRequestBeforeServiceStart(
                requestForService,
                NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            )?.let { failure ->
                val status = NodeInteractiveLongRunningStatusStore.write(
                    context = appContext,
                    executionId = executionId,
                    state = NodeInteractiveLongRunningStatusStore.STATE_FAILED,
                    sourceName = requestForService.sourceName,
                    workingDirectory = requestForService.workingDirectory,
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("preflight_failed"),
                    nativeValues = failure.nativeValues,
                )
                return InteractiveLongRunningSession(
                    executionId = executionId,
                    started = false,
                    status = status,
                    error = failure.withLifecycle(preflightFailureLifecycle(executionId, requestForService)),
                )
            }
            NodeInteractiveLongRunningStatusStore.write(
                context = appContext,
                executionId = executionId,
                state = NodeInteractiveLongRunningStatusStore.STATE_STARTING,
                sourceName = requestForService.sourceName,
                workingDirectory = requestForService.workingDirectory,
                activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("client_starting"),
            )
            var requestSpoolFile: File? = null
            try {
                requestSpoolFile = writeEmbeddedScriptRequestSpool(appContext, executionId, requestForService)
                val intent = Intent(appContext, NativeNodeEmbeddedScriptService::class.java).apply {
                    action = ACTION_RUN_SCRIPT
                    putExtra(EXTRA_SOURCE_NAME, requestForService.sourceName)
                    putExtra(EXTRA_WORKING_DIRECTORY, requestForService.workingDirectory)
                    putExtra(EXTRA_EXECUTION_MODE, requestForService.executionMode)
                    putExtra(EXTRA_LAUNCH_SURFACE, NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION)
                    putExtra(EXTRA_TIMEOUT_MS, requestForService.timeoutMs.coerceAtLeast(1L))
                    putExtra(EXTRA_ESM_EXPERIMENTAL_ENABLED, requestForService.esmExperimentalEnabled)
                    putExtra(
                        EXTRA_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED,
                        requestForService.dynamicImportExperimentalEnabled,
                    )
                    putExtra(EXTRA_WORKER_THREADS_EXPERIMENTAL_ENABLED, requestForService.workerThreadsExperimentalEnabled)
                    putExtra(EXTRA_JAVA_INTEROP_EXPERIMENTAL_ENABLED, requestForService.javaInteropExperimentalEnabled)
                    putExtra(EXTRA_SPAWN_DEPTH, requestForService.spawnDepth.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH))
                    putExtra(EXTRA_EXECUTION_ID, executionId)
                    putExtra(EXTRA_REQUEST_SPOOL_FILE, requestSpoolFile.absolutePath)
                }
                val startOutcome = startEmbeddedScriptService(appContext, intent)
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
                        message = failure.errorMessage,
                        activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("service_start_failed"),
                        nativeValues = failure.nativeValues,
                    )
                    requestSpoolFile.parentFile?.deleteRecursively()
                    return InteractiveLongRunningSession(executionId, false, status, failure)
                }
                return InteractiveLongRunningSession(
                    executionId = executionId,
                    started = true,
                    status = NodeInteractiveLongRunningStatusStore.read(appContext, executionId),
                )
            } catch (e: Throwable) {
                requestSpoolFile?.parentFile?.deleteRecursively()
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
                    message = failure.errorMessage,
                    activeResources = NodeInteractiveLongRunningStatusStore.activeResourceSummary("service_start_exception"),
                    nativeValues = failure.nativeValues,
                )
                return InteractiveLongRunningSession(executionId, false, status, failure)
            }
        }

        fun stopInteractiveLongRunning(context: Context, executionId: String): Boolean {
            val appContext = context.applicationContext
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
            val startOutcome = startEmbeddedScriptService(appContext, intent, foreground = false)
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
            return startOutcome.component != null
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
            foreground: Boolean = true,
        ): ServiceStartOutcome {
            val serviceDiagnostics = NodeAndroidRuntimePolicy.embeddedServiceDiagnostics(
                appContext,
                NativeNodeEmbeddedScriptService::class.java,
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
                        serviceClassName = NativeNodeEmbeddedScriptService::class.java.name,
                        sdkInt = Build.VERSION.SDK_INT,
                    )
                } else {
                    JSONObject().apply {
                        put("sdkInt", Build.VERSION.SDK_INT)
                        put("serviceName", NativeNodeEmbeddedScriptService::class.java.name)
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
                        serviceClassName = NativeNodeEmbeddedScriptService::class.java.name,
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

        private fun acquireExecutionSlot(
            executionId: String,
            queueMode: String,
            queueTimeoutMs: Long,
        ): ExecutionSlotDecision {
            val normalizedMode = NodeExecutionQueuePolicy.normalizeMode(queueMode)
            val normalizedQueueTimeoutMs = NodeExecutionQueuePolicy.normalizedQueueTimeoutMs(normalizedMode, queueTimeoutMs)
            val queuedAt = android.os.SystemClock.elapsedRealtime()
            synchronized(EXECUTION_QUEUE_LOCK) {
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

        private fun settleBetweenQueuedExecutions() {
            runCatching {
                Thread.sleep(CLIENT_QUEUE_PROCESS_SETTLE_MS)
            }.onFailure { error ->
                if (error is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
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

        private fun clientAwaitTimeoutMs(requestTimeoutMs: Long): Long {
            val scriptTimeoutMs = requestTimeoutMs.coerceAtLeast(1L)
            return (scriptTimeoutMs + CLIENT_SERVICE_START_GRACE_MS).coerceAtLeast(scriptTimeoutMs)
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
            root.deleteRecursively()
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
                return embeddedScriptRequestFromJson(JSONObject(file.readText(Charsets.UTF_8)))
            }
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = getStringExtra(EXTRA_SOURCE).orEmpty(),
                sourceName = getStringExtra(EXTRA_SOURCE_NAME).orEmpty().ifBlank { DEFAULT_SOURCE_NAME },
                workingDirectory = getStringExtra(EXTRA_WORKING_DIRECTORY)?.takeIf { it.isNotBlank() },
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
                workerThreadsExperimentalEnabled = getBooleanExtra(
                    EXTRA_WORKER_THREADS_EXPERIMENTAL_ENABLED,
                    BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED,
                ),
                javaInteropExperimentalEnabled = getBooleanExtra(
                    EXTRA_JAVA_INTEROP_EXPERIMENTAL_ENABLED,
                    BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED,
                ),
                spawnDepth = getIntExtra(EXTRA_SPAWN_DEPTH, 0).coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH),
            )
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
                put("workerThreadsExperimentalEnabled", request.workerThreadsExperimentalEnabled)
                put("javaInteropExperimentalEnabled", request.javaInteropExperimentalEnabled)
                put("spawnDepth", request.spawnDepth.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH))
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
                executionId = json.optString("executionId").takeIf { it.isNotBlank() },
                executionMode = NodeExecutionModePolicy.normalize(json.optString("executionMode")),
                timeoutMs = json.optLong("timeoutMs", DEFAULT_TIMEOUT_MS).coerceAtLeast(1L),
                queueMode = json.optString("queueMode").ifBlank { NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY },
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
                workerThreadsExperimentalEnabled = json.optBoolean(
                    "workerThreadsExperimentalEnabled",
                    BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED,
                ),
                javaInteropExperimentalEnabled = json.optBoolean(
                    "javaInteropExperimentalEnabled",
                    BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED,
                ),
                spawnDepth = json.optInt("spawnDepth", 0).coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH),
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

        private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.toBundle(): Bundle = Bundle().apply {
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
            pendingCryptoCallbacks?.let { putInt(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS, it) }
            pendingDnsCallbacks?.let { putInt(EXTRA_RESULT_PENDING_DNS_CALLBACKS, it) }
            pendingZlibCallbacks?.let { putInt(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS, it) }
            pendingZlibStreams?.let { putBoolean(EXTRA_RESULT_PENDING_ZLIB_STREAMS, it) }
            pendingZlibStreamCount?.let { putInt(EXTRA_RESULT_PENDING_ZLIB_STREAM_COUNT, it) }
            pendingAsyncCallbacks?.let { putInt(EXTRA_RESULT_PENDING_ASYNC_CALLBACKS, it) }
            pendingCallbacks?.let { putBoolean(EXTRA_RESULT_PENDING_CALLBACKS, it) }
            val transportNativeValues = nativeValues
                .mapValues { (key, value) -> value.limitResultTransportString("native:$key", truncations) }
                .toMutableMap()
            if (truncations.isNotEmpty()) {
                transportNativeValues["embedded_script.result_transport.truncated"] = "true"
                transportNativeValues["embedded_script.result_transport.truncated_fields"] = truncations.keys.joinToString(",")
                truncations.forEach { (key, originalChars) ->
                    transportNativeValues["embedded_script.result_transport.original_chars.$key"] = originalChars.toString()
                }
            }
            putStringArray(EXTRA_RESULT_NATIVE_PAYLOAD, transportNativeValues.map { (key, value) -> "$key=$value" }.toTypedArray())
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
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
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
                pendingCryptoCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS),
                pendingDnsCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_DNS_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_DNS_CALLBACKS),
                pendingZlibCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS),
                pendingZlibStreams = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ZLIB_STREAMS) }
                    ?.getBoolean(EXTRA_RESULT_PENDING_ZLIB_STREAMS),
                pendingZlibStreamCount = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ZLIB_STREAM_COUNT) }
                    ?.getInt(EXTRA_RESULT_PENDING_ZLIB_STREAM_COUNT),
                pendingAsyncCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ASYNC_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_ASYNC_CALLBACKS),
                pendingCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_CALLBACKS) }
                    ?.getBoolean(EXTRA_RESULT_PENDING_CALLBACKS),
            )
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
        private const val EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS = "result_pending_crypto_callbacks"
        private const val EXTRA_RESULT_PENDING_DNS_CALLBACKS = "result_pending_dns_callbacks"
        private const val EXTRA_RESULT_PENDING_ZLIB_CALLBACKS = "result_pending_zlib_callbacks"
        private const val EXTRA_RESULT_PENDING_ZLIB_STREAMS = "result_pending_zlib_streams"
        private const val EXTRA_RESULT_PENDING_ZLIB_STREAM_COUNT = "result_pending_zlib_stream_count"
        private const val EXTRA_RESULT_PENDING_ASYNC_CALLBACKS = "result_pending_async_callbacks"
        private const val EXTRA_RESULT_PENDING_CALLBACKS = "result_pending_callbacks"
        private const val EXTRA_RESULT_NATIVE_PAYLOAD = "result_native_payload"
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
