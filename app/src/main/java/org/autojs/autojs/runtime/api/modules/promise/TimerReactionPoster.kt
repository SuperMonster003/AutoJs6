package org.autojs.autojs.runtime.api.modules.promise

import android.os.Looper
import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.Callable

/**
 * The [ReactionPoster] of a script runtime: while the global `setImmediate` is the timers module's own function
 * ([original], resolved once), a job goes to the timer that `setImmediate` uses (that of the current thread, else the main
 * timer) and runs there as a callback of `setImmediate` runs (`Timer.callFunction`: through `ScriptBridges.call`, a
 * coroutine suspending again ends it quietly, other failures exit the script on the main looper and propagate elsewhere),
 * only without the argument checks of the module function and the ids kept for `clearImmediate`.
 *
 * zh-CN: 脚本运行时的 [ReactionPoster]: 全局 `setImmediate` 仍是 timers 模块自己的函数 ([original], 只解析一次) 时, 任务投递到 `setImmediate` 所用的
 * timer (当前线程的, 否则主 timer), 运行方式与 `setImmediate` 的回调相同 (`Timer.callFunction`: 经 `ScriptBridges.call`, 再次挂起的协程安静结束,
 * 其他失败在主 Looper 上结束脚本, 在其他线程上继续抛出), 只是省去模块函数的参数检查与为 `clearImmediate` 记录的 ID.
 */
internal class TimerReactionPoster(private val runtime: ScriptRuntime, private val original: () -> Any?) : ReactionPoster {

    @Volatile
    private var own: Any? = null

    override fun post(setImmediate: Any?, job: Callable): Boolean {
        val function = own ?: original()?.also { own = it } ?: return false
        if (setImmediate !== function) return false
        val timers = runtime.timers
        (timers.timerForCurrentThread ?: timers.mainTimer).postDelayed({
            Timer.runCallback({ Looper.myLooper() == Looper.getMainLooper() }, { runtime.exit(it) }) {
                runtime.bridges.call(job, null, emptyArray<Any?>())
            }
        }, 0L)
        return true
    }

}
