package org.autojs.autojs.core.ui.preview

import android.os.Looper
import android.util.Log
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionTask
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.ScriptSource

/**
 * The registered execution of a ui-mode preview runtime (3-Block Compose roadmap P10.2, Q20 option b):
 * it goes through `ScriptEngineService.execute` like every script, so it is listed among the running
 * tasks under its label and can be stopped from the host, but its engine lives on the main thread with a
 * [UiContentHost] instead of a [org.autojs.autojs.execution.ScriptExecuteActivity] and stays alive until
 * the preview session is released.
 *
 * zh-CN: ui 模式预览运行时的注册执行 (3-Block Compose 路线图 P10.2, Q20 方案 b): 与其它脚本一样经 `ScriptEngineService.execute`
 * 进入, 因此以其标签出现在运行列表中并可从宿主侧停止; 但引擎以 [UiContentHost] 取代 ScriptExecuteActivity 在主线程运行, 直到预览会话释放.
 */
class UiPreviewScriptExecution(
    private val manager: ScriptEngineManager,
    task: ScriptExecutionTask,
) : ScriptExecution.AbstractScriptExecution(task) {

    private var engine: ScriptEngine<out ScriptSource>? = null

    /** Why [start] failed, for the session's error report. */
    var startFailure: Throwable? = null
        private set

    val runtime: ScriptRuntime?
        get() = (engine as? JavaScriptEngine)?.runtime

    override fun getEngine(): ScriptEngine<out ScriptSource>? = engine

    /**
     * Creates and initializes the engine on the main thread and starts the constant script; the body is
     * posted to the main looper and finishes right away. Returns false (and reports the failure) when the
     * engine could not be created, in which case the service unregisters the execution.
     */
    fun start(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "A preview runtime is created on the main thread" }
        val host = config.uiContentHost ?: return false
        val source = source as? JavaScriptSource ?: return false
        return try {
            val created = manager.createEngineOfSourceOrThrow(source, id)
            engine = created
            created.setTag(ExecutionConfig.tag, config)
            created.setTag(ScriptEngine.TAG_ENV_PATH, config.envPath)
            created.setTag(ScriptEngine.TAG_WORKING_DIRECTORY, config.workingDirectory)
            created.setTag(ScriptEngine.TAG_SOURCE, source)
            created.setTag(UiContentHost.ENGINE_TAG, host)
            created.init()
            listener.onStart(this)
            (created as LoopBasedJavaScriptEngine).execute(source, object : LoopBasedJavaScriptEngine.ExecuteCallback {
                override fun onResult(r: Any?) {
                    /* The body of the constant script has nothing to report; the engine stays alive for the session. */
                }

                override fun onException(e: Throwable) {
                    Log.w(TAG, "preview script $id failed", e)
                    listener.onException(this@UiPreviewScriptExecution, e)
                    destroyEngine()
                }
            })
            true
        } catch (t: Throwable) {
            Log.w(TAG, "preview runtime $id could not start", t)
            startFailure = t
            destroyEngine()
            listener.onException(this, t)
            false
        }
    }

    /** Reports success once and destroys the engine (main thread, where its Rhino context was entered). */
    fun finish() {
        if (!isFinished) {
            runCatching { listener.onSuccess(this, null) }
        }
        destroyEngine()
    }

    private fun destroyEngine() {
        val current = engine ?: return
        engine = null
        runCatching { if (!current.isDestroyed) current.destroy() }
            .onFailure { Log.w(TAG, "preview engine $id destroy failed", it) }
    }

    companion object {
        private const val TAG = "UiPreviewExecution"
    }
}
