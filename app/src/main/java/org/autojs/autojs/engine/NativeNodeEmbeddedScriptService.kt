package org.autojs.autojs.engine

import android.app.Service
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
import org.autojs.autojs.App
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.ForegroundServiceCreator
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.ForegroundServiceUtils.FOREGROUND_SERVICE_TYPE_UNKNOWN
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import kotlin.system.exitProcess

class NativeNodeEmbeddedScriptService : Service() {

    @Volatile
    private var exitScheduled = false
    @Volatile
    private var activeLifecycle: NodeEmbeddedRuntimeLifecycle? = null
    private var foregroundServiceCreator: ForegroundServiceCreator? = null

    private val foregroundServiceType = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else -> FOREGROUND_SERVICE_TYPE_UNKNOWN
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startScriptForeground()
        Log.i(TAG, "PROCESS STARTED processName=${currentProcessName()} pid=${Process.myPid()}")
    }

    override fun onDestroy() {
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
        activeLifecycle = null
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
            else -> {
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    private fun runScript(intent: Intent, startId: Int) {
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME).orEmpty().ifBlank { DEFAULT_SOURCE_NAME }
        val workingDirectory = intent.getStringExtra(EXTRA_WORKING_DIRECTORY)?.takeIf { it.isNotBlank() }
        val timeoutMs = intent.getLongExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS).coerceAtLeast(1L)
        val esmExperimentalEnabled = intent.getBooleanExtra(
            EXTRA_ESM_EXPERIMENTAL_ENABLED,
            BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED,
        )
        val spawnDepth = intent.getIntExtra(EXTRA_SPAWN_DEPTH, 0)
            .coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH)
        val env = intent.envExtra()
        val moduleSources = intent.moduleSourcesExtra()
        val runtimeModuleSources = intent.runtimeModuleSourcesExtra()
        val executionId = intent.getStringExtra(EXTRA_EXECUTION_ID)?.takeIf { it.isNotBlank() }
            ?: newExecutionId()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val processName = currentProcessName()
        val pid = Process.myPid()
        val engineInfo = NodeBridgeEngineInfo.fromContext(
            context = this,
            id = executionId,
            sourceName = sourceName,
            cwd = workingDirectory.orEmpty(),
            spawnDepth = spawnDepth,
            processName = processName,
            pid = pid,
            startedAt = System.currentTimeMillis(),
        )
        val bridgeSession = NodeBridgeFileTransportSession(
            root = File(cacheDir, "embedded-node-bridge/$executionId"),
            provider = AndroidNodeBridgeCapabilityProvider(this, engineInfo),
        )
        val enrichedRuntimeModuleSources = runtimeModuleSources
            .withHostAppInfo()
            .withDeviceInfo()
            .withEngineInfo(engineInfo.toJson())
            .withBridgeLiveConfig(bridgeSession.configJson())
        val request = NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
            source = source,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            timeoutMs = timeoutMs,
            env = env,
            moduleSources = moduleSources,
            runtimeModuleSources = enrichedRuntimeModuleSources,
            esmExperimentalEnabled = esmExperimentalEnabled,
        )
        val lifecycle = NodeEmbeddedRuntimeLifecycle(TAG, executionId, processName, pid, sourceName)
        activeLifecycle = lifecycle
        lifecycle.transition(NodeEmbeddedRuntimeState.STARTING, "service.runScript")
        Log.i(
            TAG,
                    "script.enter executionId=$executionId processName=$processName pid=$pid sourceName=$sourceName " +
                    "env=${env.size} moduleSources=${moduleSources.size} runtimeModuleSources=${enrichedRuntimeModuleSources.size} " +
                    "esmExperimentalEnabled=$esmExperimentalEnabled " +
                    "runtimeFirst=${enrichedRuntimeModuleSources.keys.firstOrNull().orEmpty()}",
        )
        try {
            bridgeSession.start()
            lifecycle.transition(NodeEmbeddedRuntimeState.RUNNING, "native.enter")
            val nativeResult = NativeNodeEmbeddedRuntimeBridge.runEmbeddedScriptInCurrentProcess(
                context = this,
                request = request,
            )
            val result = dispatchQueuedBridgeRequests(bridgeSession.appendNativeValues(nativeResult))
            val completed = when {
                result.succeeded -> {
                    lifecycle.transition(NodeEmbeddedRuntimeState.DRAINING, "script.success")
                    lifecycle.transition(NodeEmbeddedRuntimeState.STOPPED, "script.success")
                    result
                }
                result.timedOut -> {
                    lifecycle.transition(NodeEmbeddedRuntimeState.TIMED_OUT, "script.timeout")
                    lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "script.timeout")
                    result
                }
                else -> {
                    lifecycle.transition(NodeEmbeddedRuntimeState.FAILED, "script.failure")
                    lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "script.failure")
                    result
                }
            }
            receiver?.send(RESULT_SCRIPT, completed.withLifecycle(lifecycle).toBundle())
        } catch (e: Throwable) {
            lifecycle.transition(NodeEmbeddedRuntimeState.FAILED, "service.exception")
            lifecycle.transition(NodeEmbeddedRuntimeState.STOPPING, "service.exception")
            Log.e(TAG, "Native Node embedded script service failed.", e)
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
                ).withLifecycle(lifecycle).toBundle(),
            )
        } finally {
            bridgeSession.stop()
            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            Log.i(
                TAG,
                "script.exit executionId=$executionId processName=$processName pid=$pid " +
                        "sourceName=$sourceName state=${lifecycle.state.name} elapsed=${elapsed}ms",
            )
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

    private class NodeBridgeFileTransportSession(
        root: File,
        private val provider: NodeBridgeCapabilityProvider,
    ) {
        private val requestDir = File(root, "requests")
        private val responseDir = File(root, "responses")
        private val running = AtomicBoolean(false)
        private val completed = AtomicInteger(0)
        private val failed = AtomicInteger(0)
        private val pending = AtomicInteger(0)
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
                put("embedded_script.bridge_live_enabled", "true")
                put("embedded_script.bridge_live_dispatch_count", completed.get().toString())
                put("embedded_script.bridge_live_dispatch_failed_count", failed.get().toString())
                put("embedded_script.bridge_live_dispatch_pending_count", pending.get().coerceAtLeast(0).toString())
                put(
                    "embedded_script.bridge_live_dispatch_status",
                    if (pending.get() <= 0) "done" else "pending",
                )
                put("embedded_script.bridge_live_responses_json", bridgeResponsesJsonSnapshot())
            }
            return result.copy(nativeValues = nativeValues)
        }

        private fun loop() {
            while (running.get()) {
                drainRequestFiles()
                Thread.sleep(BRIDGE_LIVE_POLL_INTERVAL_MS)
            }
            repeat(BRIDGE_LIVE_DRAIN_ITERATIONS) {
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
            val responded = AtomicBoolean(false)
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

    companion object {

        private const val TAG = "NativeNodeEmbeddedScriptService"
        private const val ACTION_RUN_SCRIPT = "org.autojs.autojs.engine.action.RUN_NATIVE_NODE_EMBEDDED_SCRIPT"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_SOURCE_NAME = "source_name"
        private const val EXTRA_WORKING_DIRECTORY = "working_directory"
        private const val EXTRA_TIMEOUT_MS = "timeout_ms"
        private const val EXTRA_ESM_EXPERIMENTAL_ENABLED = "esm_experimental_enabled"
        private const val EXTRA_SPAWN_DEPTH = "spawn_depth"
        private const val EXTRA_EXECUTION_ID = "execution_id"
        private const val EXTRA_ENV_NAMES = "env_names"
        private const val EXTRA_ENV_VALUES = "env_values"
        private const val EXTRA_MODULE_SOURCE_NAMES = "module_source_names"
        private const val EXTRA_MODULE_SOURCES = "module_sources"
        private const val EXTRA_RUNTIME_MODULE_SOURCE_NAMES = "runtime_module_source_names"
        private const val EXTRA_RUNTIME_MODULE_SOURCES = "runtime_module_sources"
        private const val HOST_APP_INFO_RUNTIME_MODULE_NAME = "autojs6:host-app-info"
        private const val DEVICE_INFO_RUNTIME_MODULE_NAME = "autojs6:device-info"
        private const val ENGINE_INFO_RUNTIME_MODULE_NAME = "autojs6:engine-info"
        private const val BRIDGE_LIVE_CONFIG_RUNTIME_MODULE_NAME = "autojs6:bridge-live-config"
        private const val RESULT_SCRIPT = 1
        private const val NOTIFICATION_ID = 0xA6E4
        private const val PROCESS_EXIT_DELAY_MS = 250L
        private const val BRIDGE_DISPATCH_WAIT_MS = 1000L
        private const val BRIDGE_LIVE_POLL_INTERVAL_MS = 10L
        private const val BRIDGE_LIVE_STOP_JOIN_MS = 1000L
        private const val BRIDGE_LIVE_DRAIN_ITERATIONS = 25
        private const val TIMEOUT_EXIT_CODE = 124
        private const val TIMEOUT_ERROR_CODE = NodeRuntimeErrorCodes.LEGACY_SCRIPT_TIMEOUT
        private const val DEFAULT_SOURCE_NAME = "<embedded-script>"
        private const val DEFAULT_TIMEOUT_MS = 3000L
        private val TIMER_SOURCE_HINT_PATTERN = Regex("""\bset(?:Timeout|Interval|Immediate)\s*\(""")
        private val FS_CALLBACK_SOURCE_HINT_PATTERN = Regex("""\bfs\.[A-Za-z_$][\w$]*\s*\([^)]*,\s*(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)""")
        private val CRYPTO_CALLBACK_SOURCE_HINT_PATTERN = Regex("""\bcrypto\.[A-Za-z_$][\w$]*\s*\([^)]*,\s*(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)""")
        private val DNS_CALLBACK_SOURCE_HINT_PATTERN = Regex("""\bdns\.[A-Za-z_$][\w$]*\s*\([^)]*,\s*(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)""")
        private val ZLIB_CALLBACK_SOURCE_HINT_PATTERN = Regex("""\bzlib\.[A-Za-z_$][\w$]*\s*\([^)]*,\s*(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)""")

        fun runScript(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            val executionId = newExecutionId()
            NativeNodeEmbeddedRuntimeBridge.validateEmbeddedScriptRequestBeforeServiceStart(request)?.let {
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
            val intent = Intent(appContext, NativeNodeEmbeddedScriptService::class.java).apply {
                action = ACTION_RUN_SCRIPT
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
                putExtra(EXTRA_SOURCE, request.source)
                putExtra(EXTRA_SOURCE_NAME, request.sourceName)
                putExtra(EXTRA_WORKING_DIRECTORY, request.workingDirectory)
                putExtra(EXTRA_TIMEOUT_MS, request.timeoutMs.coerceAtLeast(1L))
                putExtra(EXTRA_ESM_EXPERIMENTAL_ENABLED, request.esmExperimentalEnabled)
                putExtra(EXTRA_SPAWN_DEPTH, request.spawnDepth.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH))
                putExtra(EXTRA_EXECUTION_ID, executionId)
                putExtra(EXTRA_ENV_NAMES, request.env.keys.toTypedArray())
                putExtra(EXTRA_ENV_VALUES, request.env.values.toTypedArray())
                putExtra(EXTRA_MODULE_SOURCE_NAMES, request.moduleSources.keys.toTypedArray())
                putExtra(EXTRA_MODULE_SOURCES, request.moduleSources.values.toTypedArray())
                putExtra(EXTRA_RUNTIME_MODULE_SOURCE_NAMES, request.runtimeModuleSources.keys.toTypedArray())
                putExtra(EXTRA_RUNTIME_MODULE_SOURCES, request.runtimeModuleSources.values.toTypedArray())
            }
            var component: android.content.ComponentName? = null
            try {
                component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                } ?: return processCrashedOrTimedOut(
                    request = request,
                    message = "Failed to start embedded script service.",
                    startedAt = startedAt,
                    executionId = executionId,
                )
                val completed = completion.await(request.timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
                if (!completed) {
                    appContext.stopService(Intent().setComponent(component))
                    val lifecycle = timeoutLifecycle(executionId, request)
                    return processCrashedOrTimedOut(
                        request = request,
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
                    request,
                    "Embedded script process crashed or timed out.\n${e.message ?: e.javaClass.name}",
                    e,
                    startedAt = startedAt,
                    executionId = executionId,
                )
            }
            return result.get() ?: processCrashedOrTimedOut(
                request = request,
                message = "Embedded script process crashed or timed out.",
                startedAt = startedAt,
                executionId = executionId,
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
                pendingCryptoCallbacks = nativeValues["embedded_script.pending_crypto_callbacks"]?.toIntOrNull(),
                pendingDnsCallbacks = nativeValues["embedded_script.pending_dns_callbacks"]?.toIntOrNull(),
                pendingZlibCallbacks = nativeValues["embedded_script.pending_zlib_callbacks"]?.toIntOrNull(),
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

        private fun timeoutNativeValues(
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            elapsedMs: Long,
        ): Map<String, String> {
            val timeoutMs = request.timeoutMs.coerceAtLeast(1L)
            val pendingTimers = hasLikelyPendingTimerSourceHint(request.source)
            val pendingScopedFsCallbacks = hasLikelyScopedFsCallbackSourceHint(request.source)
            val pendingCryptoCallbacks = hasLikelyCryptoCallbackSourceHint(request.source)
            val pendingDnsCallbacks = hasLikelyDnsCallbackSourceHint(request.source)
            val pendingZlibCallbacks = hasLikelyZlibCallbackSourceHint(request.source)
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
                "embedded_script.pending_crypto_callbacks" to if (pendingCryptoCallbacks) "1" else "0",
                "embedded_script.pending_dns_callbacks" to if (pendingDnsCallbacks) "1" else "0",
                "embedded_script.pending_zlib_callbacks" to if (pendingZlibCallbacks) "1" else "0",
                "embedded_script.pending_async_callbacks" to listOf(
                    pendingScopedFsCallbacks,
                    pendingCryptoCallbacks,
                    pendingDnsCallbacks,
                    pendingZlibCallbacks,
                ).count { it }.toString(),
                "embedded_script.pending_callbacks" to (
                    pendingScopedFsCallbacks ||
                        pendingCryptoCallbacks ||
                        pendingDnsCallbacks ||
                        pendingZlibCallbacks
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
                append("pendingCryptoCallbacks=")
                append(nativeValues["embedded_script.pending_crypto_callbacks"] ?: "unknown")
                append('\n')
                append("pendingDnsCallbacks=")
                append(nativeValues["embedded_script.pending_dns_callbacks"] ?: "unknown")
                append('\n')
                append("pendingZlibCallbacks=")
                append(nativeValues["embedded_script.pending_zlib_callbacks"] ?: "unknown")
            }
        }

        private fun hasLikelyPendingTimerSourceHint(source: String): Boolean {
            return TIMER_SOURCE_HINT_PATTERN.containsMatchIn(source)
        }

        private fun hasLikelyScopedFsCallbackSourceHint(source: String): Boolean {
            return FS_CALLBACK_SOURCE_HINT_PATTERN.containsMatchIn(source)
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
            putBoolean(EXTRA_RESULT_SUCCEEDED, succeeded)
            exitCode?.let { putInt(EXTRA_RESULT_EXIT_CODE, it) }
            putString(EXTRA_RESULT_TEXT, resultText)
            putString(EXTRA_RESULT_STDOUT, stdout)
            putString(EXTRA_RESULT_STDERR, stderr)
            putString(EXTRA_RESULT_ERROR_NAME, errorName)
            putString(EXTRA_RESULT_ERROR_MESSAGE, errorMessage)
            putString(EXTRA_RESULT_ERROR_STACK, errorStack)
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
            pendingCryptoCallbacks?.let { putInt(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS, it) }
            pendingDnsCallbacks?.let { putInt(EXTRA_RESULT_PENDING_DNS_CALLBACKS, it) }
            pendingZlibCallbacks?.let { putInt(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS, it) }
            pendingAsyncCallbacks?.let { putInt(EXTRA_RESULT_PENDING_ASYNC_CALLBACKS, it) }
            pendingCallbacks?.let { putBoolean(EXTRA_RESULT_PENDING_CALLBACKS, it) }
            putStringArray(EXTRA_RESULT_NATIVE_PAYLOAD, nativeValues.map { (key, value) -> "$key=$value" }.toTypedArray())
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
                pendingCryptoCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS),
                pendingDnsCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_DNS_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_DNS_CALLBACKS),
                pendingZlibCallbacks = this?.takeIf { it.containsKey(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS) }
                    ?.getInt(EXTRA_RESULT_PENDING_ZLIB_CALLBACKS),
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
        private const val EXTRA_RESULT_PENDING_CRYPTO_CALLBACKS = "result_pending_crypto_callbacks"
        private const val EXTRA_RESULT_PENDING_DNS_CALLBACKS = "result_pending_dns_callbacks"
        private const val EXTRA_RESULT_PENDING_ZLIB_CALLBACKS = "result_pending_zlib_callbacks"
        private const val EXTRA_RESULT_PENDING_ASYNC_CALLBACKS = "result_pending_async_callbacks"
        private const val EXTRA_RESULT_PENDING_CALLBACKS = "result_pending_callbacks"
        private const val EXTRA_RESULT_NATIVE_PAYLOAD = "result_native_payload"
    }
}

private fun NodeBridgeRequest.dispatchExceptionResponse(error: Throwable): NodeBridgeResponse {
    return NodeBridgeResponse.failure(
        id,
        NodeBridgeError(
            message = error.message ?: error.javaClass.name,
            code = NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD,
            module = module,
            method = method,
            stack = error.stackTraceToString(),
        ),
    )
}
