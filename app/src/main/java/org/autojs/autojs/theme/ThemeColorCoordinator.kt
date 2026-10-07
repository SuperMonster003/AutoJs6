package org.autojs.autojs.theme

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.autojs.autojs.theme.ThemeColorApplyPolicy.Action

/**
 * The single entry point for changing the theme color (roadmap D10). It tags every change with a
 * [ThemeColorSource], holds intelligent changes while theme color pages are open (D6) and merges
 * bursts of script changes (D11); the decisions themselves live in [ThemeColorApplyPolicy].
 * zh-CN: 修改主题色的唯一入口 (Roadmap D10). 为每次修改标注 [ThemeColorSource], 主题色页面打开期间挂起智能修改 (D6),
 * 并合并连续的脚本修改 (D11); 具体决策由 [ThemeColorApplyPolicy] 负责.
 *
 * Changes are committed on the main thread through [ThemeColorManager.setThemeColor], followed by
 * [ThemeChangeNotifier.notifyThemeChanged]. Callers may invoke [apply] from any thread.
 * zh-CN: 修改在主线程上经 [ThemeColorManager.setThemeColor] 生效, 随后调用 [ThemeChangeNotifier.notifyThemeChanged]. 调用方可在任意线程调用 [apply].
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object ThemeColorCoordinator {

    interface Listener {

        /** A user or script choice replaced the theme color; an active intelligent library should stop. zh-CN: 用户或脚本的选择替换了主题色; 激活中的智能库应停用. */
        fun onExplicitColorApplied(source: ThemeColorSource) = Unit

        /** The last pause token was released; intelligent changes may resume. zh-CN: 最后一个暂停令牌已释放; 可恢复智能修改. */
        fun onResumed() = Unit

    }

    /**
     * Sees every committed change, before and after it takes effect (roadmap P6.3: script history and undo).
     * zh-CN: 观察每次生效的修改, 分别在生效前后回调 (Roadmap P6.3: 脚本历史与撤销).
     */
    interface CommitObserver {

        fun beforeCommit(themeColor: ThemeColor, source: ThemeColorSource) = Unit

        /** [scriptName] is the script of the last merged request for [ThemeColorSource.SCRIPT]. zh-CN: 对 [ThemeColorSource.SCRIPT] 而言, [scriptName] 为最后一次合并请求所属的脚本. */
        fun afterCommit(themeColor: ThemeColor, source: ThemeColorSource, scriptName: String?) = Unit

    }

    @Volatile
    var listener: Listener? = null

    @Volatile
    var commitObserver: CommitObserver? = null

    @Volatile
    private var lastScriptName: String? = null

    private val policy = ThemeColorApplyPolicy<ThemeColor>()

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val flushRunnable = Runnable { perform { policy.flush(SystemClock.uptimeMillis()) } }

    val isPaused: Boolean
        get() = synchronized(policy) { policy.isPaused }

    /**
     * The script color waiting to be applied, or the current theme color.
     * zh-CN: 等待生效的脚本颜色, 没有时为当前主题色.
     */
    val pendingOrCurrent: ThemeColor
        get() = synchronized(policy) { policy.pendingScript } ?: ThemeColorManager.currentThemeColor

    @JvmStatic
    fun apply(color: Int, source: ThemeColorSource) = apply(ThemeColorManager.resolve(color), source)

    @JvmStatic
    fun apply(themeColor: ThemeColor, source: ThemeColorSource) {
        if (source == ThemeColorSource.USER || source == ThemeColorSource.RESTORE || source == ThemeColorSource.ACTIVATION) {
            handler.removeCallbacks(flushRunnable)
        }
        perform { policy.request(themeColor, source, SystemClock.uptimeMillis()) }
    }

    /**
     * A change requested by a script through `colors.theme` (D11): merged within the script window and recorded with
     * [scriptName] when it applies.
     * zh-CN: 脚本经 `colors.theme` 请求的修改 (D11): 在脚本窗口内合并, 生效时连同 [scriptName] 一起记录.
     */
    @JvmStatic
    fun applyFromScript(themeColor: ThemeColor, scriptName: String?) {
        lastScriptName = scriptName
        apply(themeColor, ThemeColorSource.SCRIPT)
    }

    /**
     * Holds intelligent changes until every token has been released with [resume].
     * zh-CN: 挂起智能修改, 直到每个令牌都经 [resume] 释放.
     */
    @JvmStatic
    fun pause(token: Any) = synchronized(policy) { policy.pause(token) }

    @JvmStatic
    fun resume(token: Any) {
        val released = synchronized(policy) { policy.resume(token) }
        if (released) runOnMain { listener?.onResumed() }
    }

    private fun perform(decide: () -> Action<ThemeColor>) {
        when (val action = synchronized(policy) { decide() }) {
            is Action.Apply -> runOnMain { commit(action.value, action.source) }
            is Action.ScheduleFlush -> handler.postAtTime(flushRunnable, action.at)
            Action.None -> Unit
        }
    }

    private fun commit(themeColor: ThemeColor, source: ThemeColorSource) {
        commitObserver?.beforeCommit(themeColor, source)
        ThemeColorManager.setThemeColor(themeColor)
        ThemeChangeNotifier.notifyThemeChanged()
        if (ThemeColorApplyPolicy.deactivatesIntelligent(source)) {
            listener?.onExplicitColorApplied(source)
        }
        commitObserver?.afterCommit(themeColor, source, if (source == ThemeColorSource.SCRIPT) lastScriptName else null)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

}
