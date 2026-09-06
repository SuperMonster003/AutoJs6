package org.autojs.autojs.runtime.api.augment.flow

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.automator.flow.CallbackKind
import org.autojs.autojs.core.automator.flow.CombinatorKind
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import java.util.function.Supplier

/**
 * The `flow` (alias `$flow`) namespace and the global entry points of the asynchronous, chainable
 * waits (roadmap P3.2, Appendix A.2): `waitAsync` (also `wait.async`, `auto.wait` and `flow.wait`,
 * D1), `waitThenClick` / `clickWait`, `waitForStable`, `waitForStableThenClick` /
 * `clickWhenStable`, `waitForVisible`, `waitForHidden` / `waitForGone` and `clickWhenStableAfter`;
 * plus, in the namespace only, `waitUntil`, `waitWhile`, `waitForActivity`, `waitForPackage`,
 * `waitThenLongClick`, `sleep` / `delay`, `run`, `of`, `all`, `race`, `any`, `cancelAll`,
 * `defaults` / `trace` (P3.3), the `Flow` constructor and the `pending` / `workers` counts. The synchronous global `wait` is untouched.
 * zh-CN: `flow` (别名 `$flow`) 命名空间与异步链式等待的全局入口 (Roadmap P3.2, 附录 A.2): `waitAsync` (亦即 `wait.async`,
 * `auto.wait` 与 `flow.wait`, D1), `waitThenClick` / `clickWait`, `waitForStable`, `waitForStableThenClick` / `clickWhenStable`,
 * `waitForVisible`, `waitForHidden` / `waitForGone` 与 `clickWhenStableAfter`; 命名空间内另有 `waitUntil`, `waitWhile`,
 * `waitForActivity`, `waitForPackage`, `waitThenLongClick`, `sleep` / `delay`, `run`, `of`, `all`, `race`, `any`, `cancelAll`,
 * `defaults` / `trace` (P3.3), `Flow` 构造器与 `pending` / `workers` 计数. 同步的全局 `wait` 不变.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Flow(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    private val executor = FlowExecutor.of(scriptRuntime)

    override val selfAssignmentFunctions = listOf(
        ::wait.name to listOf("wait", "waitAsync"),
        ::waitUntil.name,
        ::waitWhile.name,
        ::waitForStable.name,
        ::waitForVisible.name,
        ::waitForHidden.name to listOf("waitForHidden", "waitForGone"),
        ::waitForActivity.name,
        ::waitForPackage.name,
        ::waitThenClick.name to listOf("waitThenClick", "clickWait"),
        ::waitThenLongClick.name,
        ::waitForStableThenClick.name to listOf("waitForStableThenClick", "clickWhenStable"),
        ::clickWhenStableAfter.name,
        ::sleep.name to listOf("sleep", "delay"),
        Companion::run.name,
        ::of.name,
        ::all.name,
        ::race.name,
        ::any.name,
        ::cancelAll.name,
        ::defaults.name,
        ::trace.name,
    )

    override val globalAssignmentFunctions = listOf(
        ::waitAsync.name,
        ::waitThenClick.name to listOf("waitThenClick", "clickWait"),
        ::waitForStable.name,
        ::waitForStableThenClick.name to listOf("waitForStableThenClick", "clickWhenStable"),
        ::waitForVisible.name,
        ::waitForHidden.name to listOf("waitForHidden", "waitForGone"),
        ::clickWhenStableAfter.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "Flow" to Supplier { executor.prototype.constructor },
        "pending" to Supplier { executor.pendingCount },
        "workers" to Supplier { executor.workerCount },
    )

    init {
        // `wait.async(...)` on the synchronous global `wait` (D1); Global has been assigned by now.
        // zh-CN: 同步全局 `wait` 上的 `wait.async(...)` (D1); 此时 Global 已完成赋值.
        (scriptRuntime.topLevelScope.globalThis.prop("wait") as? ScriptableObject)?.defineProperty(
            "async",
            newBaseFunction(scriptRuntime.topLevelScope, "async", { args -> waitAsync(scriptRuntime, args) }, NOT_CONSTRUCTABLE),
            READONLY or DONTENUM or PERMANENT,
        )
    }

    override fun invoke(vararg args: Any?): Any = wait(scriptRuntime, args)

    companion object : ArgumentGuards() {

        private fun api(scriptRuntime: ScriptRuntime): FlowApi = FlowExecutor.of(scriptRuntime).api

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun wait(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.WAIT, "flow.wait", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.WAIT, "waitAsync", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitUntil(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.UNTIL, "flow.waitUntil", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitWhile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.WHILE, "flow.waitWhile", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForStable(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.STABLE, "waitForStable", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForVisible(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.VISIBLE, "waitForVisible", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForHidden(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.HIDDEN, "waitForHidden", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.ACTIVITY, "flow.waitForActivity", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).wait(null, WaitKind.PACKAGE, "flow.waitForPackage", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitThenClick(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).waitThen(null, WaitKind.WAIT, "waitThenClick", args, "click", after = false)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitThenLongClick(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).waitThen(null, WaitKind.WAIT, "flow.waitThenLongClick", args, "longClick", after = false)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForStableThenClick(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).waitThen(null, WaitKind.STABLE, "waitForStableThenClick", args, "click", after = false)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clickWhenStableAfter(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).waitThen(null, WaitKind.STABLE, "clickWhenStableAfter", args, "click", after = true)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sleep(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).sleep(null, "flow.sleep", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun run(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).callback(null, CallbackKind.RUN, "flow.run", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun of(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsAtMost(args, 1) { api(scriptRuntime).of(it.getOrNull(0)) }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun all(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).combinator(CombinatorKind.ALL, "flow.all", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun race(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).combinator(CombinatorKind.RACE, "flow.race", args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun any(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).combinator(CombinatorKind.ANY, "flow.any", args)

        /** `flow.defaults({ timeout, interval, stableFor, strictActions, trace })` (P3.3): per-script defaults for the chains created afterwards; without arguments a read. zh-CN: `flow.defaults({ timeout, interval, stableFor, strictActions, trace })` (P3.3): 每脚本一份, 影响其后新建的链; 无参数时为读取. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun defaults(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = api(scriptRuntime).defaults(args)

        /** `flow.trace(on?)` (P3.3): logs every step's start / end / elapsed / outcome to the console. zh-CN: `flow.trace(on?)` (P3.3): 把每个步骤的开始 / 结束 / 耗时 / 结果记录到控制台. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun trace(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = api(scriptRuntime).trace(args)

        /** Cancels every pending flow of the script; returns how many. zh-CN: 取消脚本的全部待定 Flow; 返回个数. */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cancelAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsIsEmpty(args) { FlowExecutor.of(scriptRuntime).cancelAll() }

    }

}
