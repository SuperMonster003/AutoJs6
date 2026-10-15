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
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
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
 * zh-CN: 一次 `"ui"` 模式脚本的交互预览运行 (3-Block Compose 路线图 P10.6, 契约 v2): 宿主的一次普通脚本执行 (以脚本名出现在运行列表),
 * 其 `ui.layout` 内容进入会话的嵌入根. 运行自身是一个 [UiContentHost], 根容器委托给会话, 因此这个脚本的停止 (`exit()`, `stopScript`, 宿主运行列表)
 * 只结束这次运行, 不影响会话与它的布局预览运行时. 控制台条目, 未捕获错误与运行结束经 v2 回调送达客户端. 除特别说明外全部在主线程.
 */
internal class UiPreviewScriptRun(
    private val appContext: Context,
    private val session: UiPreviewSession,
    val runId: String,
    private val name: String,
    private val text: String,
    private val workingDirectory: File,
    private val callback: IUiPreviewCallback,
    private val onEnded: (UiPreviewScriptRun) -> Unit,
) : UiContentHost {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var execution: UiPreviewScriptExecution? = null
    private var logListener: ConsoleImpl.LogListener? = null
    private var ended = false
    private var stopping = false

    /** Why [start] failed, for the service's error report. */
    var startFailure: String? = null
        private set

    override val inflaterContext: Context get() = session.inflaterContext
    override val contentParent: ViewGroup get() = session.contentParent
    override val contentRoot: ViewGroup? get() = session.contentRoot
    override val hostWindow: Window? get() = null

    override fun showContent(view: View) = session.showContent(view)

    override fun onWindowFunctionIgnored(functionName: String) = session.onWindowFunctionIgnored(functionName)

    /* A real run keeps the script's own semantics: a failed {{ }} binding throws the way it would in AutoJs6. */

    override fun onEngineForceStop() {
        mainHandler.post { end(if (stopping) UiPreviewContract.EXIT_STOPPED else UiPreviewContract.EXIT_SCRIPT) }
    }

    /** Starts the execution; false (with [startFailure]) when the engine could not be created or the body threw at once. */
    fun start(): Boolean {
        val source = StringScriptSource(name, text)
        val config = ExecutionConfig(workingDirectory = workingDirectory.absolutePath).also { it.uiContentHost = this }
        val listener = object : SimpleScriptExecutionListener() {
            override fun onException(execution: ScriptExecution, e: Throwable) {
                mainHandler.post {
                    if (ended) return@post
                    reportError(e)
                    end(UiPreviewContract.EXIT_ERROR)
                }
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
        started.runtime?.console?.let { console ->
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
        Log.i(TAG, "[${session.id}] run $runId started: ${text.length} chars")
        return true
    }

    /** Ends the run with [reason] (`stopped`, `replaced`, `released`): force-stops the engine and reports the exit. */
    fun stop(reason: String) {
        if (ended) return
        stopping = true
        runCatching { execution?.engine?.forceStop() }.onFailure { Log.w(TAG, "[${session.id}] run $runId force stop failed", it) }
        end(reason)
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

    private fun end(reason: String) {
        if (ended) return
        ended = true
        logListener?.let { listener -> runCatching { execution?.runtime?.console?.removeLogListener(listener) } }
        logListener = null
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
    }
}
