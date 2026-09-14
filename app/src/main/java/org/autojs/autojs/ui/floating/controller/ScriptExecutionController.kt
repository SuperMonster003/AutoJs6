package org.autojs.autojs.ui.floating.controller

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts

/**
 * Starts and stops one script file on behalf of a [FloatingControllerWindow] and tracks whether
 * the execution it started is still alive. It observes the global execution listener of the
 * script engine service (which covers every engine, including plugin engines) and matches the
 * finished execution by id, so the window never has to know how the script was launched.
 *
 * zh-CN: 代表 [FloatingControllerWindow] 启动与停止一个脚本文件, 并跟踪其启动的执行是否仍在运行.
 * 通过脚本引擎服务的全局执行监听器 (覆盖包括插件引擎在内的全部引擎) 按 id 匹配已结束的执行, 窗口无需关心脚本的启动方式.
 *
 * Created by SuperMonster003 on Sep 14, 2026.
 */
class ScriptExecutionController(val file: ScriptFile) : SimpleScriptExecutionListener() {

    enum class State { IDLE, RUNNING }

    private val mLock = Any()
    private val mMainHandler = Handler(Looper.getMainLooper())
    private var mExecution: ScriptExecution? = null
    private var mRegistered = false

    @Volatile
    var state = State.IDLE
        private set

    /** Invoked on the main thread whenever [state] changes. zh-CN: [state] 变化时在主线程回调. */
    var onStateChanged: ((State) -> Unit)? = null

    val displayName: String
        get() = file.simplifiedName

    init {
        mRegistered = AutoJs.instance.scriptEngineService.registerGlobalScriptExecutionListener(this)
    }

    /**
     * Runs the script unless it is already running. The interactive variant may show plugin
     * availability prompts and therefore needs an activity context; the floating window itself
     * uses the plain variant.
     *
     * zh-CN: 若脚本未在运行则启动它. 交互式变体可能弹出插件可用性提示, 因此需要 Activity 上下文; 悬浮窗自身使用普通变体.
     */
    fun run(context: Context, interactive: Boolean = false) {
        // The lock makes a callback of a very short script wait until the execution is recorded.
        // zh-CN: 加锁使极短脚本的回调等到执行对象被记录之后再处理.
        synchronized(mLock) {
            if (state == State.RUNNING) return
            val execution = if (interactive) Scripts.runInteractive(context, file) else Scripts.run(context, file)
            mExecution = execution
            setState(if (execution != null) State.RUNNING else State.IDLE)
        }
    }

    fun stop() {
        val execution = synchronized(mLock) { mExecution } ?: return
        // The engine of a UI-mode script only exists once its activity has started.
        // zh-CN: UI 模式脚本的引擎仅在其 Activity 启动后才存在.
        execution.engine?.forceStop()
    }

    fun toggle(context: Context) {
        if (state == State.RUNNING) stop() else run(context)
    }

    fun release() {
        if (mRegistered) {
            AutoJs.instance.scriptEngineService.unregisterGlobalScriptExecutionListener(this)
            mRegistered = false
        }
        onStateChanged = null
    }

    override fun onSuccess(execution: ScriptExecution, result: Any?) = onFinished(execution)

    override fun onException(execution: ScriptExecution, e: Throwable) = onFinished(execution)

    private fun onFinished(finished: ScriptExecution) {
        synchronized(mLock) {
            val current = mExecution ?: return
            if (current.id != finished.id) return
            mExecution = null
            setState(State.IDLE)
        }
    }

    private fun setState(newState: State) {
        if (state == newState) return
        state = newState
        mMainHandler.post { onStateChanged?.invoke(newState) }
    }

}
