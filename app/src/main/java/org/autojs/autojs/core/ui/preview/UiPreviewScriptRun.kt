package org.autojs.autojs.core.ui.preview

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.eventloop.SimpleEvent
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs6.R
import org.autojs.plugin.ui.preview.IUiPreviewCallback
import org.autojs.plugin.ui.preview.UiPreviewContract
import org.mozilla.javascript.RhinoException
import java.io.File

/**
 * One interactive-preview run of a `"ui"` mode script (3-Block Compose roadmap P10.6, contract v2): a normal
 * host script execution (it is listed among the running tasks under the script's name) whose `ui.layout` content
 * goes into the session's embedded root. The run is its own [UiContentHost], delegating the root to the session,
 * so a stop of this script (its `exit()`, `stopScript`, the host task list) ends the run and leaves the session
 * and its layout-preview runtime alone. Console entries, uncaught errors and the end of the run reach the client
 * through the v2 callbacks. Everything runs on the main thread unless noted.
 *
 * Contract v4 (roadmap P11.5) brings it closer to an Activity: `ui.emitter` is the run's own [EventEmitter], which
 * gets `create` / `resume` at the start and the client's `pause` / `resume` / `back_pressed` through
 * [deliverLifecycle] (an unconsumed back key ends the run with `exit`, as finishing the Activity would); dialogs show
 * in the session's dialog layer; `statusBarColor` / `navigationBarColor` / `backgroundColor` show on the root; and a
 * [UiPreviewBusyWatch] guard ends the run when its script keeps the main thread busy beyond the request's limit, and
 * lets a stop interrupt such a script.
 *
 * zh-CN: 一次 `"ui"` 模式脚本的交互预览运行 (3-Block Compose 路线图 P10.6, 契约 v2): 宿主的一次普通脚本执行 (以脚本名出现在运行列表),
 * 其 `ui.layout` 内容进入会话的嵌入根. 运行自身是一个 [UiContentHost], 根容器委托给会话, 因此这个脚本的停止 (`exit()`, `stopScript`, 宿主运行列表)
 * 只结束这次运行, 不影响会话与它的布局预览运行时. 控制台条目, 未捕获错误与运行结束经 v2 回调送达客户端. 除特别说明外全部在主线程.
 * 契约 v4 (路线图 P11.5) 让它更接近 Activity: `ui.emitter` 是运行自己的 [EventEmitter], 开始时收到 `create` / `resume`, 客户端的 `pause` / `resume` /
 * `back_pressed` 经 [deliverLifecycle] 送达 (未消费的返回键像 Activity 结束一样以 `exit` 结束运行); 对话框显示在会话的对话框层;
 * `statusBarColor` / `navigationBarColor` / `backgroundColor` 显示在根容器上; [UiPreviewBusyWatch] 的 guard 在脚本占用主线程超过请求的上限时结束运行,
 * 并让停止能够中断这样的脚本.
 */
internal class UiPreviewScriptRun(
    private val appContext: Context,
    private val session: UiPreviewSession,
    val runId: String,
    private val name: String,
    private val text: String,
    private val workingDirectory: File,
    /** v3: the script's file in an unpacked project, which becomes the source's path; null for a v2 run. */
    private val scriptFile: File?,
    /** v4: how long the script may keep the main thread busy; 0 turns the watch off (stops still interrupt). */
    private val busyWaitLimitMs: Long,
    private val callback: IUiPreviewCallback,
    private val onEnded: (UiPreviewScriptRun) -> Unit,
) : UiContentHost {

    /** The busy wait that ended the run: the line that was running and how long the main thread had been busy. */
    private class BusyWait(val line: Int, val elapsedMs: Long)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var execution: UiPreviewScriptExecution? = null
    private var logListener: ConsoleImpl.LogListener? = null
    private var emitter: EventEmitter? = null
    private var guard: UiPreviewBusyWatch.Guard? = null
    private var busyWait: BusyWait? = null
    private val warnedFunctions = HashSet<String>()
    private var ended = false
    private var stopping = false

    /** The exit reason of a stop, replacement or release requested from a Binder thread; its script may be busy. */
    @Volatile
    private var interruptReason: String? = null

    /** Why [start] failed, for the service's error report. */
    var startFailure: String? = null
        private set

    override val inflaterContext: Context get() = session.inflaterContext
    override val contentParent: ViewGroup get() = session.contentParent
    override val contentRoot: ViewGroup? get() = session.contentRoot
    override val hostWindow: Window? get() = null
    override val lifecycleEmitter: EventEmitter? get() = emitter
    override val dialogContext: Context get() = session.dialogContext

    override fun showContent(view: View) = session.showContent(view)

    // The warning goes to the run's own console, which the client sees through onConsole, once per function and run.
    // zh-CN: 警告写入运行自己的控制台 (客户端经 onConsole 看到), 每次运行每个函数一次.
    override fun onWindowFunctionIgnored(functionName: String) {
        if (!warnedFunctions.add(functionName)) return
        runCatching { execution?.runtime?.console?.warn(appContext.getString(R.string.ui_preview_window_function_ignored, functionName)) }
    }

    override fun onWindowColor(functionName: String, color: Int): Boolean {
        val show = Runnable {
            if (ended) return@Runnable
            when (functionName) {
                "statusBarColor" -> session.showStatusBarColor(color)
                "navigationBarColor" -> session.showNavigationBarColor(color)
                "backgroundColor" -> session.showBackgroundColor(color)
            }
        }
        if (functionName !in WINDOW_COLOR_FUNCTIONS) return false
        if (Looper.myLooper() == Looper.getMainLooper()) show.run() else mainHandler.post(show)
        return true
    }

    /* A real run keeps the script's own semantics: a failed {{ }} binding throws the way it would in AutoJs6. */

    override fun onEngineForceStop() {
        // An error of a handler or a timer reaches the engine as its uncaught exception before the force stop.
        val cause = runCatching { execution?.engine?.uncaughtException }.getOrNull()
        mainHandler.post { endWithCause(cause) }
    }

    /** Starts the execution; false (with [startFailure]) when the engine could not be created or the body threw at once. */
    fun start(): Boolean {
        val source = StringScriptSource(name, text).also { source -> scriptFile?.let { source.overriddenFullPath = it.absolutePath } }
        val config = ExecutionConfig(workingDirectory = workingDirectory.absolutePath).also { it.uiContentHost = this }
        val listener = object : SimpleScriptExecutionListener() {
            override fun onException(execution: ScriptExecution, e: Throwable) {
                mainHandler.post { endWithCause(e) }
            }
        }
        val started = try {
            ScriptEngineService.getInstance().execute(source, listener, config) as UiPreviewScriptExecution
        } catch (e: Throwable) {
            startFailure = "${e.javaClass.simpleName}: ${e.message}"
            ended = true
            return false
        }
        if (started.engine == null) {
            startFailure = started.startFailure?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "the script engine could not start"
            ended = true
            return false
        }
        execution = started
        val runtime = started.runtime
        runtime?.console?.let { console ->
            val forward = object : ConsoleImpl.LogListener {
                override fun onNewLog(logEntry: ConsoleImpl.LogEntry) {
                    runCatching {
                        callback.onConsole(session.id, Bundle().apply {
                            putString(UiPreviewContract.KEY_RUN_ID, runId)
                            putInt(UiPreviewContract.KEY_LEVEL, logEntry.level)
                            putString(UiPreviewContract.KEY_TEXT, logEntry.content.toString())
                        })
                    }
                }

                override fun onLogClear() {
                    /* The client keeps its own copy of the lines; a cleared host console changes nothing there. */
                }
            }
            logListener = forward
            console.addLogListener(forward)
        }
        if (runtime != null) {
            // The body was only posted; the guard and the emitter are in place before it runs.
            guard = UiPreviewBusyWatch.register(runtime.topLevelScope, busyWaitLimitMs) { line, elapsedMs -> onBusyWait(line, elapsedMs) }
            if (interruptReason != null) guard?.interrupt()
            emitter = EventEmitter(runtime.bridges)
            // Like ScriptExecuteActivity, which emits both right after posting the body, before its listeners exist.
            emit("create", null)
            emit("resume")
        }
        Log.i(TAG, "[${session.id}] run $runId started: ${text.length} chars, busy-wait limit $busyWaitLimitMs ms")
        return true
    }

    /**
     * Lets a stop, a replacement or a release ([reason]) reach a script that keeps the main thread busy: its next
     * instruction check throws. Called on a Binder thread before the service waits for the main thread.
     */
    fun requestInterrupt(reason: String) {
        interruptReason = reason
        guard?.interrupt()
    }

    /** Ends the run with [reason] (`stopped`, `replaced`, `released`): force-stops the engine and reports the exit. */
    fun stop(reason: String) {
        if (ended) return
        stopping = true
        runCatching { execution?.engine?.forceStop() }.onFailure { Log.w(TAG, "[${session.id}] run $runId force stop failed", it) }
        end(reason)
    }

    /**
     * A lifecycle event of `sendLifecycle` (v4): `pause` / `resume` reach `ui.emitter` and the `events` module like an
     * Activity's; `back_pressed` goes to the top dialog first, then to `ui.emitter`, and ends the run with `exit` when no
     * handler consumes it. [onLifecycleHandled][IUiPreviewCallback.onLifecycleHandled] reports what happened.
     */
    fun deliverLifecycle(event: String) {
        if (ended) return
        var consumed = false
        when (event) {
            UiPreviewContract.LIFECYCLE_PAUSE -> emit("pause")
            UiPreviewContract.LIFECYCLE_RESUME -> emit("resume")
            UiPreviewContract.LIFECYCLE_BACK_PRESSED -> {
                consumed = session.dialogLayer.onBackPressed() || SimpleEvent().also { emit("back_pressed", it) }.consumed
            }
        }
        if (ended) return
        runCatching {
            callback.onLifecycleHandled(session.id, Bundle().apply {
                putString(UiPreviewContract.KEY_RUN_ID, runId)
                putString(UiPreviewContract.KEY_LIFECYCLE_EVENT, event)
                putBoolean(UiPreviewContract.KEY_CONSUMED, consumed)
            })
        }
        if (event == UiPreviewContract.LIFECYCLE_BACK_PRESSED && !consumed) stop(UiPreviewContract.EXIT_SCRIPT)
    }

    /** Emits [event] to the `events` module and `ui.emitter`, like ScriptExecuteActivity.emit; a throwing handler exits. */
    private fun emit(event: String, vararg args: Any?) {
        val runtime = execution?.runtime ?: return
        try {
            runtime.events.emit(event, *args)
            emitter?.emit(event, *args)
        } catch (e: Exception) {
            runCatching { runtime.exit(e) }
        }
    }

    /** The guard tripped inside the run's script, which unwinds next; the fallback ends the run if nothing else does. */
    private fun onBusyWait(line: Int, elapsedMs: Long) {
        if (busyWait == null) busyWait = BusyWait(line, elapsedMs)
        mainHandler.post {
            if (!ended) {
                reportBusyWait()
                stop(UiPreviewContract.EXIT_ERROR)
            }
        }
    }

    /** The end of the script on its own ([cause] null: exit or force stop) or by an uncaught error. */
    private fun endWithCause(cause: Throwable?) {
        if (ended) return
        when {
            busyWait != null -> {
                reportBusyWait()
                end(UiPreviewContract.EXIT_ERROR)
            }
            interruptReason != null -> end(interruptReason ?: UiPreviewContract.EXIT_STOPPED)
            stopping -> end(UiPreviewContract.EXIT_STOPPED)
            cause != null && cause !is UiPreviewInterruptedException -> {
                reportError(cause)
                end(UiPreviewContract.EXIT_ERROR)
            }
            else -> end(UiPreviewContract.EXIT_SCRIPT)
        }
    }

    private fun reportError(error: Throwable) {
        val message = UiPreviewFailures.messageOf(error)
        val line = (error as? RhinoException)?.lineNumber() ?: -1
        runCatching {
            callback.onScriptError(session.id, Bundle().apply {
                putString(UiPreviewContract.KEY_RUN_ID, runId)
                putString(UiPreviewContract.KEY_ERROR_MESSAGE, message)
                putInt(UiPreviewContract.KEY_LINE, line)
            })
        }
    }

    private fun reportBusyWait() {
        val busy = busyWait ?: return
        runCatching {
            callback.onScriptError(session.id, Bundle().apply {
                putString(UiPreviewContract.KEY_RUN_ID, runId)
                putString(UiPreviewContract.KEY_ERROR_CODE, UiPreviewContract.ERROR_BUSY_WAIT)
                putString(
                    UiPreviewContract.KEY_ERROR_MESSAGE,
                    "the script kept the main thread busy for more than $busyWaitLimitMs ms without returning to it; the preview stopped it",
                )
                putInt(UiPreviewContract.KEY_LINE, busy.line)
                putLong(UiPreviewContract.KEY_ELAPSED_MS, busy.elapsedMs)
            })
        }
        Log.w(TAG, "[${session.id}] run $runId busy for ${busy.elapsedMs} ms at line ${busy.line}")
    }

    private fun end(reason: String) {
        if (ended) return
        ended = true
        guard?.let { UiPreviewBusyWatch.unregister(it) }
        guard = null
        logListener?.let { listener -> runCatching { execution?.runtime?.console?.removeLogListener(listener) } }
        logListener = null
        emitter = null
        runCatching { execution?.finish() }.onFailure { Log.w(TAG, "[${session.id}] run $runId finish failed", it) }
        execution = null
        runCatching {
            callback.onScriptExit(session.id, Bundle().apply {
                putString(UiPreviewContract.KEY_RUN_ID, runId)
                putString(UiPreviewContract.KEY_REASON, reason)
            })
        }
        Log.i(TAG, "[${session.id}] run $runId ended: $reason")
        onEnded(this)
    }

    companion object {
        private const val TAG = "UiPreviewScriptRun"

        private val WINDOW_COLOR_FUNCTIONS = setOf("statusBarColor", "navigationBarColor", "backgroundColor")
    }
}
