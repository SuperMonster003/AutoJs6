package org.autojs.autojs.core.automator.flow

/**
 * The failure of a Flow step (roadmap Appendix A.4). One subclass per [Code]; the fields are what
 * the JavaScript `FlowError` exposes: the step that failed, the selector it waited for (when any),
 * the milliseconds spent and the attempts made, plus an optional [reason] refining the code
 * (`unstable` / `missing` for a stability timeout, `chain` for the whole-chain deadline of
 * `timeout(ms)`, `sync` for `await` giving up). The runtime turns it into a JS `Error` (P3.2).
 * zh-CN: Flow 步骤的失败 (Roadmap 附录 A.4). 每个 [Code] 一个子类; 字段即 JavaScript 侧 `FlowError` 暴露的内容: 失败的步骤,
 * 等待的选择器 (若有), 已用毫秒数与尝试次数, 以及细化代码的可选 [reason] (稳定检测超时的 `unstable` / `missing`, `timeout(ms)`
 * 全链截止的 `chain`, `await` 放弃的 `sync`). 运行时把它转换为 JS `Error` (P3.2).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
sealed class FlowError(
    val code: Code,
    val step: String,
    val selector: String?,
    val elapsed: Long,
    val attempts: Long,
    val reason: String?,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    enum class Code {
        TIMEOUT,
        ACTION_FAILED,
        CANCELLED,
        A11Y_UNAVAILABLE,
        INVALID_TARGET,
    }

    /** The condition was not satisfied within the plan. zh-CN: 条件未在计划内满足. */
    class Timeout(step: String, selector: String?, elapsed: Long, attempts: Long, reason: String? = null) : FlowError(
        Code.TIMEOUT, step, selector, elapsed, attempts, reason,
        "Timed out after $elapsed ms and $attempts attempt(s) in step \"$step\"" + (selector?.let { " waiting for $it" } ?: "") + (reason?.let { " ($it)" } ?: ""),
    )

    /** A strict action reported `false`. zh-CN: 严格模式的动作返回了 `false`. */
    class ActionFailed(step: String, selector: String? = null, elapsed: Long = 0, attempts: Long = 1) : FlowError(
        Code.ACTION_FAILED, step, selector, elapsed, attempts, null,
        "Action \"$step\" failed",
    )

    /** The chain was cancelled before or while the step ran. zh-CN: 链在步骤执行前或执行中被取消. */
    class Cancelled(step: String, selector: String? = null, elapsed: Long = 0, attempts: Long = 0) : FlowError(
        Code.CANCELLED, step, selector, elapsed, attempts, null,
        "Cancelled in step \"$step\"",
    )

    /** The accessibility service is not available for the step. zh-CN: 无障碍服务对该步骤不可用. */
    class A11yUnavailable(step: String, selector: String? = null) : FlowError(
        Code.A11Y_UNAVAILABLE, step, selector, 0, 0, null,
        "Accessibility service unavailable for step \"$step\"",
    )

    /** The current value is not something the step can work on. zh-CN: 当前值不是该步骤可作用的目标. */
    class InvalidTarget(step: String, selector: String? = null, reason: String? = null, elapsed: Long = 0, attempts: Long = 0) : FlowError(
        Code.INVALID_TARGET, step, selector, elapsed, attempts, reason,
        "Invalid target for step \"$step\"" + (reason?.let { " ($it)" } ?: ""),
    )

}
