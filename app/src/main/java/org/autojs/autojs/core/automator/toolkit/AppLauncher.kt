package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.flow.FlowError

/**
 * Moving between apps (roadmap P4.1): [launchAndWait] resolves an app (package name, label,
 * alias or `App` value) to its package, launches it (unless it is already in front and
 * [LaunchOptions.bringToFront] is off) and waits for the current package to become it;
 * [backUntil] presses back until a condition holds, [backToApp] until a package is in front.
 * zh-CN: 应用之间的切换 (Roadmap P4.1): [launchAndWait] 把应用 (包名, 应用名, 别名或 `App` 值) 解析为包名, 启动它 (已在前台且
 * [LaunchOptions.bringToFront] 关闭时除外) 并等待当前包名变为它; [backUntil] 反复按返回直到条件成立, [backToApp] 直到某个包在前台.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class AppLauncher<N : UiNode<N>> internal constructor(private val kit: Toolkit<N>) {

    data class LaunchOptions(
        val timeout: Long = DEFAULT_TIMEOUT,
        val interval: Long = DEFAULT_INTERVAL,
        /** Launch even when the app is already in front. zh-CN: 应用已在前台时也启动. */
        val bringToFront: Boolean = true,
    ) {

        init {
            require(timeout >= 0) { "timeout ($timeout) cannot be negative" }
            require(interval >= 0) { "interval ($interval) cannot be negative" }
        }

        companion object {

            const val DEFAULT_TIMEOUT = 10000L

            const val DEFAULT_INTERVAL = 200L

        }

    }

    /** The [packageName] now in front, whether a launch was made and how long the wait took. zh-CN: 现在前台的 [packageName], 是否做了启动, 以及等待的耗时. */
    data class Launched(val packageName: String, val launched: Boolean, val elapsed: Long, val attempts: Long)

    /**
     * @throws FlowError `INVALID_TARGET` (reason `unknownApp`) when nothing installed matches [app],
     * `ACTION_FAILED` (reason `launch`) when the launch was refused, `TIMEOUT` (reason `package`)
     * when the app did not come to the front in time.
     */
    fun launchAndWait(app: Any?, options: LaunchOptions = LaunchOptions(), step: String = STEP_LAUNCH): Launched {
        val packageName = resolve(app, step)
        if (!options.bringToFront && kit.bindings.currentPackage() == packageName) return Launched(packageName, false, 0, 0)
        if (!kit.actions.launch(packageName)) throw FlowError.ActionFailed(step, packageName, reason = REASON_LAUNCH)
        val polled = kit.poll(kit.within(options.timeout, options.interval)) {
            if (kit.bindings.currentPackage() == packageName) true else null
        }
        if (polled.value == null) throw FlowError.Timeout(step, packageName, polled.elapsed, polled.attempts, REASON_PACKAGE)
        return Launched(packageName, true, polled.elapsed, polled.attempts)
    }

    data class BackOptions(
        val maxTimes: Int = DEFAULT_MAX_TIMES,
        /** How long the condition is polled for after every press before the next one. zh-CN: 每次按下后, 下一次按下前, 轮询条件的时长. */
        val delay: Long = DEFAULT_DELAY,
        /** The polling interval within [delay]. zh-CN: [delay] 内的轮询间隔. */
        val interval: Long = DEFAULT_INTERVAL,
        val humanize: Humanize = Humanize.NONE,
    ) {

        init {
            require(maxTimes >= 0) { "maxTimes ($maxTimes) cannot be negative" }
            require(delay >= 0) { "delay ($delay) cannot be negative" }
            require(interval > 0) { "interval ($interval) must be positive" }
        }

        companion object {

            const val DEFAULT_MAX_TIMES = 10

            const val DEFAULT_DELAY = 500L

            const val DEFAULT_INTERVAL = 100L

        }

    }

    /** The [value] the condition yielded and how many back [presses] it took. zh-CN: 条件得到的 [value] 与所需的返回按下次数 [presses]. */
    data class BackOutcome(val value: Any?, val presses: Int)

    /**
     * Presses back until [cond] (probed under [root]) holds, at most [BackOptions.maxTimes] times;
     * the condition is checked before the first press and, after every press, polled for up to
     * [BackOptions.delay] milliseconds (every [BackOptions.interval]) so that a slow page transition
     * is not mistaken for "not there" and answered with one press too many.
     * zh-CN: 反复按返回直到 [cond] (在 [root] 下探测) 成立, 至多 [BackOptions.maxTimes] 次; 第一次按下前检查条件, 之后每次按下后在至多
     * [BackOptions.delay] 毫秒内 (每 [BackOptions.interval]) 轮询条件, 以免把缓慢的页面切换误判为 "不在" 而多按一次.
     *
     * @throws FlowError `TIMEOUT` (reason `maxTimes`), `ACTION_FAILED` (reason `back`).
     */
    fun backUntil(cond: Any?, options: BackOptions = BackOptions(), root: N? = null, step: String = STEP_BACK): BackOutcome =
        backWhile(step, kit.bindings.describe(cond), options) { kit.bindings.probe(cond, root) }

    /** [backUntil] with "[app] is in front" as the condition; yields the package name. zh-CN: 以 "[app] 在前台" 为条件的 [backUntil]; 得到包名. */
    fun backToApp(app: Any?, options: BackOptions = BackOptions(), step: String = STEP_BACK_TO_APP): BackOutcome {
        val packageName = resolve(app, step)
        return backWhile(step, packageName, options) { kit.bindings.currentPackage()?.takeIf { it == packageName } }
    }

    private fun backWhile(step: String, selector: String?, options: BackOptions, satisfied: () -> Any?): BackOutcome {
        val start = kit.clock()
        satisfied()?.let { return BackOutcome(it, 0) }
        var presses = 0
        while (true) {
            if (presses >= options.maxTimes) throw FlowError.Timeout(step, selector, kit.clock() - start, presses + 1L, REASON_MAX_TIMES)
            kit.pause(options.humanize)
            if (!kit.actions.back()) throw FlowError.ActionFailed(step, selector, kit.clock() - start, presses + 1L, REASON_BACK)
            presses += 1
            settle(options, satisfied)?.let { return BackOutcome(it, presses) }
        }
    }

    /** [satisfied] polled right after a press and then every [BackOptions.interval] until [BackOptions.delay] has passed. zh-CN: 按下后立即探测 [satisfied], 然后每 [BackOptions.interval] 探测一次, 直到 [BackOptions.delay] 用尽. */
    private fun settle(options: BackOptions, satisfied: () -> Any?): Any? {
        val start = kit.clock()
        while (true) {
            satisfied()?.let { return it }
            val elapsed = kit.clock() - start
            if (elapsed >= options.delay) return null
            kit.sleep(minOf(options.interval, options.delay - elapsed))
        }
    }

    private fun resolve(app: Any?, step: String): String =
        kit.bindings.resolvePackage(app) ?: throw FlowError.InvalidTarget(step, kit.bindings.describe(app), REASON_UNKNOWN_APP)

    companion object {

        const val STEP_LAUNCH = "launchAndWait"

        const val STEP_BACK = "backUntil"

        const val STEP_BACK_TO_APP = "backToApp"

        const val REASON_UNKNOWN_APP = "unknownApp"

        const val REASON_LAUNCH = "launch"

        const val REASON_PACKAGE = "package"

        const val REASON_MAX_TIMES = "maxTimes"

        const val REASON_BACK = "back"

    }

}
