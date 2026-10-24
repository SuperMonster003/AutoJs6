package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapCalls.method
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * A job of the global Promise ([PromiseConstructor]): a reaction, or the check for a rejection without reaction. It goes
 * through `Promise._immediateFn` as the module's `function () { ... }` did; a [ReactionPoster] may post it to a timer
 * itself, and a script that replaced `_immediateFn` or `setImmediate` gets it as a function ([function]).
 *
 * zh-CN: 全局 Promise ([PromiseConstructor]) 的一项任务: 一个反应, 或对没有反应的拒绝的检查. 它与原模块的 `function () { ... }` 一样经
 * `Promise._immediateFn` 投递; [ReactionPoster] 可以自行把它投递到 timer, 替换了 `_immediateFn` 或 `setImmediate` 的脚本得到的是函数形式 ([function]).
 */
internal abstract class PromiseJob : Callable {

    abstract fun run(cx: Context)

    final override fun call(cx: Context, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? {
        run(cx)
        return Undefined.instance
    }

    /** The job as the anonymous function of the module (`length` 0), for script code. */
    fun function(scope: VarScope): LambdaFunction = method(scope, "", 0) { cx, _, _, _ ->
        run(cx)
        Undefined.instance
    }

}

/**
 * Posts the jobs of the default `Promise._immediateFn` without calling the global `setImmediate` (roadmap P4.2, evaluation
 * §4.3), in the order that `setImmediate` would post them.
 *
 * zh-CN: 不经全局 `setImmediate` 投递默认 `Promise._immediateFn` 的任务 (ROADMAP P4.2, 评估 §4.3), 顺序与 `setImmediate` 投递时相同.
 */
fun interface ReactionPoster {

    /**
     * Posts [job] as `setImmediate(job)` would, or returns false (then the job is passed to [setImmediate], the current
     * value of the global name, as the module did) when [setImmediate] is not the function the poster stands for.
     *
     * zh-CN: 像 `setImmediate(job)` 那样投递 [job]; [setImmediate] (全局名称的当前值) 不是本对象所代表的函数时返回 false, 任务照原模块交给 [setImmediate].
     */
    fun post(setImmediate: Any?, job: Callable): Boolean

}
