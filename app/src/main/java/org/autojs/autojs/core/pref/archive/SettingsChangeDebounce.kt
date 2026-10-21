package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId

/**
 * The change backup of docs/settings/ROADMAP.md D22 / T20 (P9.3): listens to the changes of the settings stores
 * ([SettingsStores.addChangeListener]) and backs the settings up once they stayed unchanged for [delayMillis] (five
 * minutes). The first tracked change of a burst schedules the one-time work through [scheduler]; when the work is due it
 * asks [onDue], which is true when the quiet time is over (back up now) and otherwise schedules the work again for the
 * rest of it, so a burst of changes schedules once or a few times and yields one backup. A work due in a new process
 * (the app was stopped meanwhile) backs up at once. Only changes that [filter] tracks count: the keys of the categories
 * of an automatic backup ([SettingsAutoBackup.CATEGORIES]), not `other`, `credentials` or `developer`, whose changes
 * would not change the backup, and not the keys of the automatic backup itself. Pure JVM (R3): the clock and the
 * scheduler are given; the app schedules with WorkManager (`AndroidSettingsChangeBackup`).
 *
 * zh-CN: ROADMAP D22 / T20 的变更后备份 (P9.3): 监听设置存储的变化 ([SettingsStores.addChangeListener]), 设置在 [delayMillis] (5 分钟)
 * 内没有新变化时备份一次. 一轮连续变化中第一个计入的变化经 [scheduler] 安排一次性任务; 任务到期时调用 [onDue], 静默时间已满时返回
 * true (立即备份), 否则按剩余时间重新安排任务, 因此连续变化只安排一次或几次任务, 只产生一份备份. 在新进程中到期的任务 (其间应用被结束)
 * 立即备份. 只有 [filter] 计入的变化才算: 自动备份所含分类 ([SettingsAutoBackup.CATEGORIES]) 的键, 不含 `other`, `credentials` 与
 * `developer` (它们的变化不会改变备份内容), 也不含自动备份自身的键. 纯 JVM (R3): 时钟与调度由调用方提供; 应用以 WorkManager 调度
 * (`AndroidSettingsChangeBackup`).
 */
class SettingsChangeDebounce @JvmOverloads constructor(
    private val scheduler: Scheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val delayMillis: Long = DELAY_MILLIS,
    private val filter: Filter = Filter(),
    private val enabled: () -> Boolean = { true },
) : SettingsStores.ChangeListener {

    init {
        require(delayMillis > 0) { "The quiet time of a change backup is positive" }
    }

    /**
     * Schedules the one-time work of the change backup; replaceable, a WorkManager `OneTimeWorkRequest` in the app.
     * zh-CN: 安排变更后备份的一次性任务; 可替换, 应用中为 WorkManager 的 `OneTimeWorkRequest`.
     */
    interface Scheduler {

        /** Runs the work [delayMillis] from now, replacing a pending one. zh-CN: [delayMillis] 后执行任务, 替换尚未执行的任务. */
        fun schedule(delayMillis: Long)

        /** Cancels the pending work. zh-CN: 取消尚未执行的任务. */
        fun cancel()
    }

    /**
     * Which changes count (T20): those of the declared keys and key families of [categories], except [ownKeys] (the
     * keys of the automatic backup itself, such as its switches); a store cleared counts when it holds such a key.
     * Undeclared names, which the run-time fallback puts in `other` (P4.3), never count.
     * zh-CN: 哪些变化计入 (T20): [categories] 中已声明的键与键族的变化, [ownKeys] (自动备份自身的键, 如其开关) 除外; 存储被清空时, 若其中
     * 有这样的键则计入. 未声明的名称 (运行时兜底归入 `other`, P4.3) 不计入.
     */
    class Filter @JvmOverloads constructor(
        keys: List<PrefKey<*>> = SettingsRegistry.keys,
        families: List<KeyFamily<*, *>> = SettingsRegistry.families,
        categories: Collection<SettingCategory> = SettingsAutoBackup.CATEGORIES,
        ownKeys: Collection<PrefKey<*>> = emptyList(),
    ) {

        private val tracked: Map<StoreId, Set<String>> by lazy {
            val own = ownKeys.map { it.store to it.name }.toSet()
            keys.filter { it.category in categories && (it.store to it.name) !in own }
                .groupBy({ it.store }, { it.name }).mapValues { it.value.toSet() }
        }

        private val trackedFamilies: Map<StoreId, List<KeyFamily<*, *>>> by lazy {
            families.filter { it.category in categories }.groupBy { it.template.store }
        }

        private val own: Set<Pair<StoreId, String>> by lazy { ownKeys.map { it.store to it.name }.toSet() }

        /** True when the change of [name] in [store] (null after a clear) counts. zh-CN: [store] 中 [name] 的变化 (清空时为 null) 是否计入. */
        fun tracks(store: StoreId, name: String?): Boolean {
            if (name == null) return tracked[store].orEmpty().isNotEmpty() || trackedFamilies[store].orEmpty().isNotEmpty()
            if (name in tracked[store].orEmpty()) return true
            return (store to name) !in own && trackedFamilies[store].orEmpty().any { it.matches(name) }
        }
    }

    private val lock = Any()

    private var lastChange: Long? = null

    private var scheduled = false

    override fun onChanged(store: StoreId, name: String?) {
        if (!filter.tracks(store, name) || !enabled()) return
        synchronized(lock) {
            lastChange = clock()
            if (!scheduled) {
                scheduled = true
                scheduler.schedule(delayMillis)
            }
        }
    }

    /**
     * Called by the scheduled work: true when the settings stayed unchanged for [delayMillis], or nothing is known of
     * the changes (a new process), so back them up now; false otherwise, the work then being scheduled again for the
     * rest of the quiet time, or when the change backup is switched off.
     * zh-CN: 由安排的任务调用: 设置在 [delayMillis] 内没有变化, 或不知道之前的变化 (新进程) 时为 true, 立即备份; 否则为 false, 并按剩余的
     * 静默时间重新安排任务; 变更后备份已关闭时同样为 false.
     */
    fun onDue(): Boolean = synchronized(lock) {
        if (!enabled()) {
            reset()
            return false
        }
        val last = lastChange ?: return true.also { reset() }
        val quiet = clock() - last
        if (quiet >= delayMillis) {
            reset()
            true
        } else {
            scheduled = true
            scheduler.schedule((delayMillis - quiet).coerceAtMost(delayMillis))
            false
        }
    }

    /**
     * Forgets the changes and cancels the pending work (the change backup is switched off).
     * zh-CN: 忘记之前的变化并取消尚未执行的任务 (变更后备份被关闭).
     */
    fun cancel() = synchronized(lock) {
        reset()
        scheduler.cancel()
    }

    private fun reset() {
        lastChange = null
        scheduled = false
    }

    companion object {

        /** The quiet time before a change backup (D22). zh-CN: 变更后备份前的静默时间 (D22). */
        const val DELAY_MILLIS = 5 * 60 * 1000L
    }
}
