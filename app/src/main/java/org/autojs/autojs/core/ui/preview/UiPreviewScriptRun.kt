package org.autojs.autojs.core.ui.preview

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.eventloop.SimpleEvent
import org.autojs.autojs.core.plugin.typescript.TypeScriptDiagnosticsException
import org.autojs.autojs.core.plugin.typescript.TypeScriptExecutionCompiler
import org.autojs.autojs.core.plugin.typescript.TypeScriptSourceMapBinding
import org.autojs.autojs.core.plugin.typescript.TypeScriptSourceMapBindings
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.script.TypeScriptStringSource
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
 * Contract v5 (roadmap P11.H3, D69) runs TSX: the text is compiled by TypeScript Engine on a worker thread, the way a
 * `.tsx` file is before it runs, and the run starts on the main thread with the generated JavaScript once that is done
 * (a stop meanwhile ends the run before it starts). A text that does not compile reports its error diagnostics; the
 * lines of the run's errors are mapped back to the TSX text through the compiler's source map.
 *
 * zh-CN: 一次 `"ui"` 模式脚本的交互预览运行 (3-Block Compose 路线图 P10.6, 契约 v2): 宿主的一次普通脚本执行 (以脚本名出现在运行列表),
 * 其 `ui.layout` 内容进入会话的嵌入根. 运行自身是一个 [UiContentHost], 根容器委托给会话, 因此这个脚本的停止 (`exit()`, `stopScript`, 宿主运行列表)
 * 只结束这次运行, 不影响会话与它的布局预览运行时. 控制台条目, 未捕获错误与运行结束经 v2 回调送达客户端. 除特别说明外全部在主线程.
 * 契约 v4 (路线图 P11.5) 让它更接近 Activity: `ui.emitter` 是运行自己的 [EventEmitter], 开始时收到 `create` / `resume`, 客户端的 `pause` / `resume` /
 * `back_pressed` 经 [deliverLifecycle] 送达 (未消费的返回键像 Activity 结束一样以 `exit` 结束运行); 对话框显示在会话的对话框层;
 * `statusBarColor` / `navigationBarColor` / `backgroundColor` 显示在根容器上; [UiPreviewBusyWatch] 的 guard 在脚本占用主线程超过请求的上限时结束运行,
 * 并让停止能够中断这样的脚本.
 * 契约 v5 (路线图 P11.H3, D69) 运行 TSX: 文本先在工作线程经 TypeScript Engine 编译 (与运行 `.tsx` 文件前相同), 完成后在主线程以生成的 JavaScript 开始运行
 * (其间的停止让运行在开始前结束). 编译失败时回报其错误诊断; 运行中错误的行号经编译器的源映射换回 TSX 文本的行.
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
    /** v5: the language of [text], `js` or `tsx`. */
    private val dialect: String,
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
        val line = errorLine(cause)
        mainHandler.post { endWithCause(cause, line) }
    }

    /**
     * Starts the execution; false (with [startFailure]) when the engine could not be created or the body threw at once.
     * A TSX run returns true at once and starts once its compilation is done; it reports a failure of either through
     * the callbacks.
     */
    fun start(): Boolean {
        if (dialect == UiPreviewContract.SCRIPT_DIALECT_TSX) {
            compileThenStart()
            return true
        }
        val source = StringScriptSource(name, text).also { source -> scriptFile?.let { source.overriddenFullPath = it.absolutePath } }
        if (startExecution(source)) return true
        ended = true
        return false
    }

    /** v5: compiles the TSX text on a worker thread, then starts it on the main thread unless the run ended meanwhile. */
    private fun compileThenStart() {
        val sourceName = UiPreviewRequests.tsxSourceName(name)
        val source = TypeScriptStringSource(sourceName, text).also { source -> scriptFile?.let { source.overriddenFullPath = it.absolutePath } }
        Thread({
            val startedAt = SystemClock.elapsedRealtime()
            val compiled = try {
                TypeScriptExecutionCompiler.prepare(appContext, source)
            } catch (e: Throwable) {
                Log.i(TAG, "[${session.id}] run $runId did not compile: ${e.javaClass.simpleName}")
                mainHandler.post {
                    if (!ended) {
                        reportCompileFailure(sourceName, e)
                        end(UiPreviewContract.EXIT_ERROR)
                    }
                }
                return@Thread
            }
            Log.i(TAG, "[${session.id}] run $runId compiled in ${SystemClock.elapsedRealtime() - startedAt} ms")
            mainHandler.post {
                if (ended) return@post
                if (!startExecution(compiled)) {
                    reportFailure(UiPreviewContract.ERROR_SCRIPT_FAILED, startFailure ?: "the script could not start", -1, -1)
                    end(UiPreviewContract.EXIT_ERROR)
                }
            }
        }, "UiPreviewTsxCompile-$runId").apply { isDaemon = true }.start()
    }

    /** Starts the execution of [source] on the main thread; false (with [startFailure]) when it could not start. */
    private fun startExecution(source: ScriptSource): Boolean {
        val config = ExecutionConfig(workingDirectory = workingDirectory.absolutePath).also { it.uiContentHost = this }
        val listener = object : SimpleScriptExecutionListener() {
            override fun onException(execution: ScriptExecution, e: Throwable) {
                // The line now: the engine, and with it the source map of a TSX run, goes right after this call.
                val line = errorLine(e)
                mainHandler.post { endWithCause(e, line) }
            }
        }
        val started = try {
            ScriptEngineService.getInstance().execute(source, listener, config) as UiPreviewScriptExecution
        } catch (e: Throwable) {
            startFailure = "${e.javaClass.simpleName}: ${e.message}"
            return false
        }
        if (started.engine == null) {
            startFailure = started.startFailure?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "the script engine could not start"
            return false
        }
        execution = started
        val runtime = started.runtime
        runtime?.console?.let { console ->
            val forward = object : ConsoleImpl.LogListener {
                override fun onNewLog(logEntry: ConsoleImpl.LogEntry) {
                    console(logEntry.level, logEntry.content.toString())
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
            guard = UiPreviewBusyWatch.register(runtime.topLevelScope, busyWaitLimitMs) { sourceName, line, elapsedMs -> onBusyWait(sourceName, line, elapsedMs) }
            if (interruptReason != null) guard?.interrupt()
            emitter = EventEmitter(runtime.bridges)
            // Like ScriptExecuteActivity, which emits both right after posting the body, before its listeners exist.
            emit("create", null)
            emit("resume")
        }
        Log.i(TAG, "[${session.id}] run $runId started: ${text.length} chars, $dialect, busy-wait limit $busyWaitLimitMs ms")
        return true
    }

    private fun console(level: Int, text: String) {
        runCatching {
            callback.onConsole(session.id, Bundle().apply {
                putString(UiPreviewContract.KEY_RUN_ID, runId)
                putInt(UiPreviewContract.KEY_LEVEL, level)
                putString(UiPreviewContract.KEY_TEXT, text)
            })
        }
    }

    /**
     * v5: the line of the TSX text for a [line] of the generated JavaScript in [sourceName] (the entry when null),
     * through the source map the engine got with the compiled source; -1 without a mapping. A JS run's lines stay.
     */
    private fun scriptLine(sourceName: String?, line: Int): Int {
        if (dialect != UiPreviewContract.SCRIPT_DIALECT_TSX || line <= 0) return line
        val bindings = when (val tag = execution?.engine?.getTag(TypeScriptExecutionCompiler.SOURCE_MAP_ENGINE_TAG)) {
            is TypeScriptSourceMapBindings -> tag
            is TypeScriptSourceMapBinding -> TypeScriptSourceMapBindings.single(tag)
            else -> return -1
        }
        val frame = sourceName ?: bindings.bindings.first().generatedSourceName
        return runCatching { bindings.originalPositionFor(frame, line)?.lineNumber }.getOrNull() ?: -1
    }

    /** v5: every error diagnostic goes to the console, the first one (or the failure itself) to `onScriptError`. */
    private fun reportCompileFailure(sourceName: String, error: Throwable) {
        val diagnostics = (error as? TypeScriptDiagnosticsException)?.typeScriptDiagnostics.orEmpty().filter { it.isError }
        diagnostics.forEach { console(Log.ERROR, TypeScriptExecutionCompiler.formatDiagnostic(sourceName, it)) }
        val first = diagnostics.firstOrNull()
        if (first == null) {
            reportFailure(UiPreviewContract.ERROR_COMPILE_FAILED, UiPreviewFailures.messageOf(error), -1, -1)
        } else {
            reportFailure(UiPreviewContract.ERROR_COMPILE_FAILED, "TS${first.code}: ${first.message}", first.range?.line ?: -1, first.range?.column ?: -1)
        }
    }

    private fun reportFailure(code: String, message: String, line: Int, column: Int) {
        runCatching {
            callback.onScriptError(session.id, Bundle().apply {
                putString(UiPreviewContract.KEY_RUN_ID, runId)
                putString(UiPreviewContract.KEY_ERROR_CODE, code)
                putString(UiPreviewContract.KEY_ERROR_MESSAGE, message.take(UiPreviewContract.MAX_ERROR_MESSAGE_CHARS))
                putInt(UiPreviewContract.KEY_LINE, line)
                putInt(UiPreviewContract.KEY_COLUMN, column)
            })
        }
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
    private fun onBusyWait(sourceName: String?, line: Int, elapsedMs: Long) {
        if (busyWait == null) busyWait = BusyWait(scriptLine(sourceName, line), elapsedMs)
        mainHandler.post {
            if (!ended) {
                reportBusyWait()
                stop(UiPreviewContract.EXIT_ERROR)
            }
        }
    }

    /** The end of the script on its own ([cause] null: exit or force stop) or by an uncaught error at [line]. */
    private fun endWithCause(cause: Throwable?, line: Int) {
        if (ended) return
        when {
            busyWait != null -> {
                reportBusyWait()
                end(UiPreviewContract.EXIT_ERROR)
            }
            interruptReason != null -> end(interruptReason ?: UiPreviewContract.EXIT_STOPPED)
            stopping -> end(UiPreviewContract.EXIT_STOPPED)
            cause != null && cause !is UiPreviewInterruptedException -> {
                reportError(cause, line)
                end(UiPreviewContract.EXIT_ERROR)
            }
            else -> end(UiPreviewContract.EXIT_SCRIPT)
        }
    }

    /** The script line of [error] (a TSX line for a TSX run), -1 when unknown; asked while the engine is still there. */
    private fun errorLine(error: Throwable?): Int =
        (error as? RhinoException)?.let { runCatching { scriptLine(it.sourceName(), it.lineNumber()) }.getOrNull() } ?: -1

    private fun reportError(error: Throwable, line: Int) {
        val message = UiPreviewFailures.messageOf(error)
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
