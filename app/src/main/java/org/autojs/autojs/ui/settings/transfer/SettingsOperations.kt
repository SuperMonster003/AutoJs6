package org.autojs.autojs.ui.settings.transfer

import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.UUID

/**
 * The imports, undos and resets that run now (docs/settings/ROADMAP.md T10, P7.5), by the id the wizard state keeps
 * ([SettingsWizardState.operation]). They run in a scope of the process, not of the page: applying a theme color,
 * the night mode or a language recreates the page, whose wizard then waits for the same operation instead of running
 * it again (a second internal backup would hold the imported values). An id the process does not know any more
 * belongs to an operation of a process that ended.
 *
 * zh-CN: 正在运行的导入, 撤销与恢复默认 (ROADMAP T10, P7.5), 以向导状态记录的 id ([SettingsWizardState.operation]) 区分. 它们在进程
 * 级的作用域中运行, 而不是页面的: 应用主题色, 夜间模式或语言会重建页面, 重建后的向导等待同一个操作, 而不是再运行一次 (第二份内部
 * 备份会包含已导入的值). 进程不再认识的 id 属于已结束进程中的操作.
 */
@MainThread
object SettingsOperations {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val running = HashMap<String, Deferred<SettingsTransferOutcome>>()

    @JvmStatic
    fun newId(): String = UUID.randomUUID().toString()

    /** Starts [work] as the operation [id], or returns the one already started. zh-CN: 以 [id] 启动 [work], 已启动时返回原操作. */
    @JvmStatic
    fun start(id: String, work: suspend () -> SettingsTransferOutcome): Deferred<SettingsTransferOutcome> =
        running.getOrPut(id) { scope.async { work() } }

    /** The operation [id] of this process, or null. zh-CN: 本进程中的操作 [id], 没有时为 null. */
    @JvmStatic
    fun find(id: String): Deferred<SettingsTransferOutcome>? = running[id]

    /** Forgets the operation [id] once its outcome is in the wizard state. zh-CN: 结果写入向导状态后忘记操作 [id]. */
    @JvmStatic
    fun forget(id: String) {
        running.remove(id)
    }
}
