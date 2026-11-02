package org.autojs.autojs.runtime

import android.util.Log
import org.autojs.autojs.core.looper.TimerCallbackRejectedException
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.rhino.ScriptEventLoop
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs6.R

/**
 * The [ScriptEventLoop] of a script: the timers of its threads and its console.
 * zh-CN: 脚本的 [ScriptEventLoop]: 其各线程的 timer 与控制台.
 */
internal class RuntimeEventLoop(private val runtime: ScriptRuntime) : ScriptEventLoop {

    override fun hasTimer(thread: Thread): Boolean = runtime.timers.timerOfThread(thread) != null

    override fun post(thread: Thread?, task: Runnable) {
        val timers = runtime.timers
        val timer = (if (thread == null) timers.mainTimer else timers.timerOfThread(thread)) ?: return
        try {
            timer.postDelayed({
                RhinoUtils.withRhinoContext(runtime) { cx ->
                    if (cx is AutoJsContext) cx.runTask(this, task) else task.run()
                }
            }, 0L)
        } catch (_: TimerCallbackRejectedException) {
            // The script is ending: nothing would run the reactions anyway.
            // zh-CN: 脚本正在结束: 反应本来也不会有人运行.
        }
    }

    override fun reportUnhandledRejection(description: String) {
        runtime.console.println(Log.ERROR, ScriptMessages.resource(R.string.error_promise_uncaught_rejection, "Uncaught (in promise) %s", description))
    }

}
