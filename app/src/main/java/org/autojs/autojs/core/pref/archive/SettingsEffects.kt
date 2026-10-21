package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.core.pref.keys.TimedTaskKeys
import org.autojs.autojs.core.pref.store.StoreId

/**
 * The effect table of imports, resets and undos (docs/settings/ROADMAP.md T12, D10): every `APPLY` key is bound to one
 * [Entry], the existing entry point that applies it without a restart, and [run] calls each entry once for the changed
 * keys it covers (the three theme colors make one call). A change of a `RESTART` key makes the result page offer
 * "restart now" ([Report.restartNeeded]). The table and the runner are pure JVM (R3); what an entry does on Android is
 * given as an [Action] (`AndroidSettingsEffects` in the app, fakes in JVM tests). The guard test checks that every
 * `APPLY` key of the registry is in [TABLE] ("APPLY keys need an entry", T12).
 *
 * zh-CN: 导入, 恢复默认与撤销的生效表 (ROADMAP T12, D10): 每个 `APPLY` 键绑定一个 [Entry], 即不重启即可应用它的现有入口; [run] 对变化的
 * 键按入口各调用一次 (三个主题色只调用一次). `RESTART` 键发生变化时结果页提供 "立即重启" ([Report.restartNeeded]). 生效表与执行逻辑为
 * 纯 JVM (R3); 入口在 Android 上的动作以 [Action] 提供 (应用内为 `AndroidSettingsEffects`, JVM 测试为替身). 守卫测试检查注册表中每个
 * `APPLY` 键都在 [TABLE] 中 ("APPLY 键必须有入口", T12).
 */
object SettingsEffects {

    /** The entry points of T12, in the order they run. zh-CN: T12 的入口, 按执行顺序. */
    enum class Entry {
        /** `ThemeColorCoordinator.apply` with the stored theme color (`ThemeColorManager.setThemeColor`). */
        THEME_COLOR,

        /** `ViewUtils.setDefaultNightMode` from the night mode switches, as the app does at start. */
        NIGHT_MODE,

        /** `LocaleUtils.setLocale` / `setFollowSystem` with the stored language. */
        LANGUAGE,

        /** The launcher component switch of `LauncherIconPreference`. */
        LAUNCHER_ICON,

        /** `TimedTaskScheduler.init`: the timed tasks are scheduled again on the stored backend. */
        TIMED_TASK_BACKEND,

        /** The start / stop entry of the drawer switch of the foreground service (needs the notification permission, appendix C Q13). */
        FOREGROUND_SERVICE,
    }

    /** What [Entry] does in the app; it throws when it fails. zh-CN: [Entry] 在应用中的动作, 失败时抛出异常. */
    fun interface Action {
        fun run()
    }

    /**
     * A failure an entry reports with a [reason] the result page explains (with a way to grant the permission).
     * zh-CN: 入口报告的失败, 结果页按 [reason] 说明 (并提供授权入口).
     */
    class EffectException @JvmOverloads constructor(val reason: Reason, message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
        enum class Reason {
            /** The foreground service needs the notification permission (Android 13 and later). */
            NOTIFICATION_PERMISSION,
        }
    }

    /** A written or removed key and the effect it declares. zh-CN: 被写入或删除的键及其声明的生效方式. */
    data class Change(val store: StoreId, val name: String, val effect: SettingEffect)

    /**
     * What [run] did: the entries that ran and those that failed (with why), and whether a `RESTART` key changed.
     * zh-CN: [run] 的结果: 已执行与失败的入口 (及原因), 以及是否有 `RESTART` 键发生变化.
     */
    data class Report(val applied: List<Entry>, val failures: Map<Entry, Throwable>, val restartNeeded: Boolean) {
        val succeeded: List<Entry>
            get() = applied - failures.keys
    }

    /**
     * The `APPLY` keys of each entry (T12), declared where the keys are: the theme colors, the night mode list and its
     * two switches, the language, the launcher icon, the timed task backend, the foreground service.
     * zh-CN: 每个入口对应的 `APPLY` 键 (T12): 主题色, 夜间模式列表及其两个开关, 语言, 启动图标, 定时任务后端, 前台服务.
     */
    @JvmStatic
    val TABLE: Map<Entry, List<PrefKey<*>>> by lazy {
        linkedMapOf(
            Entry.THEME_COLOR to listOf(AppearanceKeys.THEME_COLOR_PRIMARY, AppearanceKeys.THEME_COLOR_PRIMARY_DARK, AppearanceKeys.THEME_COLOR_ACCENT),
            Entry.NIGHT_MODE to listOf(AppearanceKeys.NIGHT_MODE, AppearanceKeys.NIGHT_MODE_ENABLED, AppearanceKeys.AUTO_NIGHT_MODE_ENABLED),
            Entry.LANGUAGE to listOf(AppearanceKeys.APP_LANGUAGE),
            Entry.LAUNCHER_ICON to listOf(GeneralKeys.LAUNCHER_ICON),
            Entry.TIMED_TASK_BACKEND to listOf(TimedTaskKeys.TIMED_TASK_BACKEND),
            Entry.FOREGROUND_SERVICE to listOf(GeneralKeys.FOREGROUND_SERVICE),
        )
    }

    /**
     * The entry of the key [name] of [store] in [table], or null.
     * zh-CN: [table] 中 [store] 的键 [name] 所属的入口, 不存在时为 null.
     */
    @JvmStatic
    @JvmOverloads
    fun entryOf(store: StoreId, name: String, table: Map<Entry, List<PrefKey<*>>> = TABLE): Entry? =
        table.entries.firstOrNull { (_, keys) -> keys.any { it.store == store && it.name == name } }?.key

    /**
     * The entries [changes] need, in the order of [Entry]: those of the changed `APPLY` keys.
     * zh-CN: [changes] 需要的入口, 按 [Entry] 的顺序: 即变化的 `APPLY` 键所属的入口.
     */
    @JvmStatic
    @JvmOverloads
    fun entriesFor(changes: Collection<Change>, table: Map<Entry, List<PrefKey<*>>> = TABLE): List<Entry> {
        val needed = changes.filter { it.effect == SettingEffect.APPLY }.mapNotNullTo(HashSet()) { entryOf(it.store, it.name, table) }
        return Entry.values().filter { it in needed }
    }

    /**
     * Runs the entries [changes] need with [actions], each once and in order; a failing entry does not stop the
     * others. In the app, call it on the main thread once the stores are written (`SettingsImportApplier.write`).
     * zh-CN: 用 [actions] 依次执行 [changes] 需要的入口, 每个一次; 某个入口失败不影响其他入口. 应用内在存储写入之后
     * (`SettingsImportApplier.write`) 于主线程调用.
     */
    @JvmStatic
    @JvmOverloads
    fun run(changes: Collection<Change>, actions: (Entry) -> Action, table: Map<Entry, List<PrefKey<*>>> = TABLE): Report {
        val entries = entriesFor(changes, table)
        val failures = LinkedHashMap<Entry, Throwable>()
        entries.forEach { entry ->
            try {
                actions(entry).run()
            } catch (e: Exception) {
                failures[entry] = e
            }
        }
        return Report(entries, failures, changes.any { it.effect == SettingEffect.RESTART })
    }
}
