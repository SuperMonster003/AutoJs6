package org.autojs.autojs.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.util.Log
import org.autojs.autojs.App
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

open class NativeNodeProcessService : Service() {

    @Volatile
    private var exitScheduled = false

    @Volatile
    private var prewarmStarted = false

    @Volatile
    private var activeCrashMarker: NodeCrashEvidenceStore.Marker? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        NodeCrashEvidenceStore.markServiceDestroy(
            context = this,
            marker = activeCrashMarker,
            reason = "native_process_service.onDestroy",
        )
        activeCrashMarker = null
        scheduleProcessExit(0)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_PREWARM -> {
                if (exitScheduled) {
                    START_NOT_STICKY
                } else {
                    prewarmNode(startId)
                    START_NOT_STICKY
                }
            }
            ACTION_RUN -> {
                if (exitScheduled) {
                    intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)?.send(
                        RESULT_ERROR,
                        Bundle().apply {
                            putString(EXTRA_ERROR_MESSAGE, "Native Node.js process is exiting.")
                        },
                    )
                    return START_NOT_STICKY
                }
                Thread {
                    runNode(intent, startId)
                }.apply {
                    name = "NativeNodeProcessService-$startId"
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

    private fun prewarmNode(startId: Int) {
        if (prewarmStarted) {
            return
        }
        prewarmStarted = true
        Thread {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                NativeNodeRuntimeBridge.requireAvailable()
                val missingSymbols = NativeNodeRuntimeBridge.missingEmbedderSymbols()
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (missingSymbols.isEmpty()) {
                    Log.i(TAG, "${javaClass.simpleName} prewarmed in ${elapsed}ms; embedder symbols are available.")
                } else {
                    Log.w(TAG, "${javaClass.simpleName} prewarmed in ${elapsed}ms; missing embedder symbols: ${missingSymbols.joinToString()}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Native Node.js process prewarm failed.", e)
                stopSelf(startId)
            }
        }.apply {
            name = "NativeNodeProcessService-prewarm-$startId"
            start()
        }
    }

    private fun runNode(intent: Intent, startId: Int) {
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        val argv = intent.getStringArrayExtra(EXTRA_ARGV) ?: emptyArray()
        val workingDirectory = intent.getStringExtra(EXTRA_WORKING_DIRECTORY)
        val startedAt = SystemClock.elapsedRealtime()
        val crashMarker = NodeCrashEvidenceStore.begin(
            context = this,
            executionId = "native-process-$startId-${System.currentTimeMillis()}",
            sourceName = argv.firstOrNull().orEmpty().ifBlank { "node" },
            workingDirectory = workingDirectory,
            executionMode = "native_process",
            launchSurface = "native_process_service",
            timeoutMs = null,
            runtimeAdapter = "desktop_node_main",
            runtimeSlotId = javaClass.simpleName,
            scenario = "native_process_service",
        )
        activeCrashMarker = crashMarker
        var exitCode = -1
        var terminalMarked = false
        try {
            exitCode = NativeNodeRuntimeBridge.runMain(
                argv = argv,
                workingDirectory = workingDirectory,
                outputSink = receiver?.let(::ReceiverOutputSink),
            )
            NodeCrashEvidenceStore.markNativeProcessExit(
                context = this,
                marker = crashMarker,
                exitCode = exitCode,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
            )
            terminalMarked = true
            receiver?.send(
                RESULT_EXIT,
                Bundle().apply { putInt(EXTRA_EXIT_CODE, exitCode) },
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Native Node.js process run failed.", e)
            NodeCrashEvidenceStore.markThrowable(
                context = this,
                marker = crashMarker,
                reason = "native_process_service.exception",
                throwable = e,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
            )
            terminalMarked = true
            receiver?.send(
                RESULT_ERROR,
                Bundle().apply {
                    putString(EXTRA_ERROR_MESSAGE, e.message ?: e.javaClass.name)
                    putString(EXTRA_ERROR_STACK, e.stackTraceToString())
                },
            )
        } finally {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (!terminalMarked) {
                NodeCrashEvidenceStore.markNativeProcessExit(
                    context = this,
                    marker = crashMarker,
                    exitCode = exitCode,
                    elapsedMs = elapsed,
                )
            }
            activeCrashMarker = null
            Log.i(TAG, "${javaClass.simpleName} finished native Node.js run in ${elapsed}ms with exitCode=$exitCode.")
            stopSelf(startId)
            scheduleProcessExit(if (exitCode == 0) 0 else 1)
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

    private class ReceiverOutputSink(private val receiver: ResultReceiver) : NativeNodeRuntimeBridge.OutputSink {

        override fun onStdout(bytes: ByteArray) {
            send(RESULT_STDOUT, bytes)
        }

        override fun onStderr(bytes: ByteArray) {
            send(RESULT_STDERR, bytes)
        }

        private fun send(resultCode: Int, bytes: ByteArray) {
            receiver.send(
                resultCode,
                Bundle().apply { putByteArray(EXTRA_OUTPUT_BYTES, bytes) },
            )
        }
    }

    companion object {

        private const val TAG = "NativeNodeProcessService"
        private const val ACTION_PREWARM = "org.autojs.autojs.engine.action.PREWARM_NATIVE_NODE"
        private const val ACTION_RUN = "org.autojs.autojs.engine.action.RUN_NATIVE_NODE"
        private const val EXTRA_ARGV = "argv"
        private const val EXTRA_WORKING_DIRECTORY = "working_directory"
        private const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val EXTRA_OUTPUT_BYTES = "output_bytes"
        private const val EXTRA_EXIT_CODE = "exit_code"
        private const val EXTRA_ERROR_MESSAGE = "error_message"
        private const val EXTRA_ERROR_STACK = "error_stack"
        private const val RESULT_STDOUT = 1
        private const val RESULT_STDERR = 2
        private const val RESULT_EXIT = 3
        private const val RESULT_ERROR = 4
        private const val PROCESS_EXIT_DELAY_MS = 25L
        private const val NEXT_PREWARM_DELAY_MS = 180L
        private const val NEXT_PREWARM_RETRY_DELAY_MS = 1000L

        private val SERVICE_COMPONENTS = arrayOf<Class<out NativeNodeProcessService>>(
            NativeNodeProcessService::class.java,
            NativeNodeProcessServiceSlot1::class.java,
        )
        private val slotLock = Any()
        private val slotPermits = Semaphore(SERVICE_COMPONENTS.size, true)
        private val busySlots = BooleanArray(SERVICE_COMPONENTS.size)
        private var nextSlotIndex = 0

        fun prewarm(context: Context) {
            val appContext = context.applicationContext
            (appContext as? App)?.cancelDeferredNativeNodePrewarm()
            SERVICE_COMPONENTS.indices.forEach { slotIndex ->
                prewarm(appContext, slotIndex)
            }
        }

        fun run(
            context: Context,
            argv: Array<String>,
            workingDirectory: String?,
            outputSink: NativeNodeRuntimeBridge.OutputSink?,
        ): Int {
            val appContext = context.applicationContext
            (appContext as? App)?.cancelDeferredNativeNodePrewarm()
            val slotIndex = try {
                acquireSlot()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ScriptInterruptedException(e)
            }
            val completion = CountDownLatch(1)
            val result = AtomicReference<Result<Int>>()
            val receiver = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    when (resultCode) {
                        RESULT_STDOUT -> resultData?.getByteArray(EXTRA_OUTPUT_BYTES)?.let {
                            outputSink?.onStdout(it)
                        }
                        RESULT_STDERR -> resultData?.getByteArray(EXTRA_OUTPUT_BYTES)?.let {
                            outputSink?.onStderr(it)
                        }
                        RESULT_EXIT -> {
                            result.set(Result.success(resultData?.getInt(EXTRA_EXIT_CODE) ?: -1))
                            completion.countDown()
                        }
                        RESULT_ERROR -> {
                            val message = resultData?.getString(EXTRA_ERROR_MESSAGE)
                                ?: "Native Node.js process failed."
                            val stack = resultData?.getString(EXTRA_ERROR_STACK).orEmpty()
                            result.set(Result.failure(NativeNodeRuntimeUnavailableException(
                                listOf(message, stack).filter { it.isNotBlank() }.joinToString("\n")
                            )))
                            completion.countDown()
                        }
                    }
                }
            }
            val intent = Intent(appContext, SERVICE_COMPONENTS[slotIndex]).apply {
                action = ACTION_RUN
                putExtra(EXTRA_ARGV, argv)
                putExtra(EXTRA_WORKING_DIRECTORY, workingDirectory)
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
            }
            var component: android.content.ComponentName? = null
            try {
                component = appContext.startService(intent)
                    ?: throw NativeNodeRuntimeUnavailableException(
                        "Failed to start native Node.js process service slot $slotIndex."
                    )
                completion.await()
            } catch (e: InterruptedException) {
                component?.let { appContext.stopService(Intent().setComponent(it)) }
                Thread.currentThread().interrupt()
                throw ScriptInterruptedException(e)
            } finally {
                scheduleNextPrewarm(appContext, slotIndex)
            }
            return result.get()?.getOrThrow()
                ?: throw NativeNodeRuntimeUnavailableException("Native Node.js process service finished without result.")
        }

        private fun prewarm(context: Context, slotIndex: Int) {
            context.applicationContext.startService(
                Intent(context.applicationContext, SERVICE_COMPONENTS[slotIndex]).apply {
                    action = ACTION_PREWARM
                }
            )
        }

        @Throws(InterruptedException::class)
        private fun acquireSlot(): Int {
            slotPermits.acquire()
            var slotIndex = -1
            synchronized(slotLock) {
                repeat(SERVICE_COMPONENTS.size) { offset ->
                    val index = (nextSlotIndex + offset) % SERVICE_COMPONENTS.size
                    if (!busySlots[index]) {
                        busySlots[index] = true
                        nextSlotIndex = (index + 1) % SERVICE_COMPONENTS.size
                        slotIndex = index
                        return@synchronized
                    }
                }
            }
            if (slotIndex >= 0) {
                return slotIndex
            }
            slotPermits.release()
            throw IllegalStateException("No native Node.js process slot is available.")
        }

        private fun releaseSlot(slotIndex: Int) {
            synchronized(slotLock) {
                busySlots[slotIndex] = false
            }
            slotPermits.release()
        }

        private fun scheduleNextPrewarm(context: Context, slotIndex: Int) {
            val appContext = context.applicationContext
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                try {
                    runCatching { prewarm(appContext, slotIndex) }
                        .onFailure { Log.w(TAG, "Failed to schedule native Node.js prewarm for slot $slotIndex.", it) }
                } finally {
                    releaseSlot(slotIndex)
                }
            }, NEXT_PREWARM_DELAY_MS)
            handler.postDelayed({
                runCatching { prewarm(appContext, slotIndex) }
                    .onFailure { Log.w(TAG, "Failed to retry native Node.js prewarm for slot $slotIndex.", it) }
            }, NEXT_PREWARM_RETRY_DELAY_MS)
        }

        @Suppress("DEPRECATION")
        private fun Intent.resultReceiverExtra(name: String): ResultReceiver? {
            return getParcelableExtra(name)
        }
    }
}

class NativeNodeProcessServiceSlot1 : NativeNodeProcessService()
