package org.autojs.autojs.theme

/**
 * Who asked for a theme color change (roadmap D10).
 * zh-CN: 主题色修改的发起方 (Roadmap D10).
 */
enum class ThemeColorSource {

    /** An explicit choice in the theme color pages. zh-CN: 在主题色页面中的显式选择. */
    USER,

    /** The intelligent color engine. zh-CN: 智能颜色引擎. */
    INTELLIGENT,

    /**
     * The user activated an intelligent library or picked one of its groups: applied at once, even while paused,
     * and the library stays active.
     * zh-CN: 用户激活了智能库或选择了其中的组: 立即应用 (暂停期间也是如此), 且智能库保持激活.
     */
    ACTIVATION,

    /** A script through the `colors.theme` API. zh-CN: 脚本经 `colors.theme` API 发起. */
    SCRIPT,

    /** Restoring an earlier selection, such as undoing a script change. zh-CN: 恢复之前的选择, 例如撤销脚本修改. */
    RESTORE,

}

/**
 * Pure decision state of [ThemeColorCoordinator] (roadmap D6, D10, D11).
 * zh-CN: [ThemeColorCoordinator] 的纯决策状态 (Roadmap D6, D10, D11).
 *
 * - [ThemeColorSource.USER], [ThemeColorSource.RESTORE] and [ThemeColorSource.ACTIVATION] apply at once and discard a pending script change.
 * - [ThemeColorSource.INTELLIGENT] is dropped while any pause token is held; the engine re-evaluates on resume.
 * - [ThemeColorSource.SCRIPT] opens a fixed window of [scriptWindowMillis]; requests inside it replace each other
 *   and only the last one is applied when the window closes, so a tight loop causes a single visible change.
 * zh-CN:
 * - [ThemeColorSource.USER], [ThemeColorSource.RESTORE] 与 [ThemeColorSource.ACTIVATION] 立即生效, 并丢弃尚未生效的脚本修改.
 * - 持有任一暂停令牌期间丢弃 [ThemeColorSource.INTELLIGENT], 恢复后由引擎重新评估.
 * - [ThemeColorSource.SCRIPT] 开启一个长度为 [scriptWindowMillis] 的固定窗口; 窗口内的请求相互覆盖, 窗口结束时只应用最后一次,
 *   因此紧密循环只产生一次可见变化.
 *
 * Not thread-safe; the coordinator serializes access.
 * zh-CN: 非线程安全, 由协调器串行访问.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
class ThemeColorApplyPolicy<T : Any>(private val scriptWindowMillis: Long = SCRIPT_WINDOW_MILLIS) {

    sealed interface Action<out T> {

        data class Apply<T>(val value: T, val source: ThemeColorSource) : Action<T>

        /** Call [flush] at [at] (same clock as the requests). zh-CN: 在 [at] 时调用 [flush] (与请求使用同一时钟). */
        data class ScheduleFlush(val at: Long) : Action<Nothing>

        data object None : Action<Nothing>

    }

    private val pauseTokens = LinkedHashSet<Any>()
    private var pendingScriptValue: T? = null
    private var windowEndsAt: Long? = null

    val isPaused: Boolean
        get() = pauseTokens.isNotEmpty()

    /** The script value waiting for its window to close. zh-CN: 等待窗口结束的脚本修改值. */
    val pendingScript: T?
        get() = pendingScriptValue

    fun request(value: T, source: ThemeColorSource, now: Long): Action<T> = when (source) {
        ThemeColorSource.USER, ThemeColorSource.RESTORE, ThemeColorSource.ACTIVATION -> {
            pendingScriptValue = null
            windowEndsAt = null
            Action.Apply(value, source)
        }
        ThemeColorSource.INTELLIGENT -> when {
            isPaused -> Action.None
            else -> Action.Apply(value, source)
        }
        ThemeColorSource.SCRIPT -> {
            pendingScriptValue = value
            when (windowEndsAt) {
                null -> (now + scriptWindowMillis).let {
                    windowEndsAt = it
                    Action.ScheduleFlush(it)
                }
                else -> Action.None
            }
        }
    }

    /**
     * Closes the script window. A flush before the window end (a stale schedule) is ignored.
     * zh-CN: 关闭脚本窗口. 早于窗口结束时间的 flush (过期的调度) 被忽略.
     */
    fun flush(now: Long): Action<T> {
        val endsAt = windowEndsAt ?: return Action.None
        if (now < endsAt) return Action.None
        windowEndsAt = null
        val value = pendingScriptValue ?: return Action.None
        pendingScriptValue = null
        return Action.Apply(value, ThemeColorSource.SCRIPT)
    }

    fun pause(token: Any) {
        pauseTokens += token
    }

    /**
     * @return true when this call released the last pause token.
     * zh-CN: 本次调用释放了最后一个暂停令牌时返回 true.
     */
    fun resume(token: Any): Boolean = pauseTokens.remove(token) && pauseTokens.isEmpty()

    companion object {

        const val SCRIPT_WINDOW_MILLIS = 500L

        /** An explicit user or script choice deactivates the intelligent library (D10). zh-CN: 用户或脚本的显式选择会停用智能库 (D10). */
        fun deactivatesIntelligent(source: ThemeColorSource) = source == ThemeColorSource.USER || source == ThemeColorSource.SCRIPT

    }

}
