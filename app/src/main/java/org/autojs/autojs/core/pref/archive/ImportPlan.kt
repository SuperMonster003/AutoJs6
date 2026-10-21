package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.store.StoreId

/**
 * The plan of an import (docs/settings/ROADMAP.md T8, D8 / D9 / D20), made by [SettingsImportPlanner] and applied by
 * `SettingsImportApplier` (P7.4): for the selected [categories], the values to write ([puts], in their stored form),
 * the stored keys the archive does not have ([removes], back to their defaults, D8), the entries left as they are
 * ([skips], counted by [SkipReason], D9) and the deployment items the user confirms first ([deployments], T18).
 * Categories that are not selected do not appear at all. The plugin directories of the archive (D3, P13.1) whose plugin
 * has a settings provider on this device are handed to it ([plugins], package names, P13.2); any other is skipped as a
 * whole: [SkipReason.TARGET_MISSING] with the category [PLUGINS], the package name as store and no name. Pure data (R3).
 *
 * zh-CN: 一次导入的计划 (ROADMAP T8, D8 / D9 / D20), 由 [SettingsImportPlanner] 生成, 由 `SettingsImportApplier` (P7.4) 应用:
 * 对勾选的 [categories], 要写入的值 ([puts], 存储形式), 归档中没有的已存储键 ([removes], 恢复默认, D8), 保持原样的条目 ([skips],
 * 按 [SkipReason] 计数, D9), 以及需要用户先确认的部署项 ([deployments], T18). 未勾选的分类完全不出现. 归档中的插件目录 (D3, P13.1)
 * 若其插件在本机有设置提供方则交给它 ([plugins], 包名, P13.2); 其余整体跳过: `TARGET_MISSING`, 分类为 [PLUGINS], 存储为包名, 名称为空.
 * 纯数据 (R3).
 */
data class ImportPlan @JvmOverloads constructor(
    val categories: List<String>,
    val puts: List<Put>,
    val removes: List<Remove>,
    val skips: List<Skip>,
    val deployments: List<Deployment>,
    val plugins: List<String> = emptyList(),
) {

    /** Why an entry of the archive is left as it is (T8, D9); the current value stays. zh-CN: 条目被跳过的原因, 当前值不变. */
    enum class SkipReason {
        /** No declaration of the category knows the name (an unregistered entry of `other` included). */
        UNKNOWN_KEY,

        /** The type tag is not the one the key declares. */
        TYPE_MISMATCH,

        /** The value does not fit the key: an Enum value out of its candidates, JSON its codec refuses, a malformed entry. */
        INVALID_VALUE,

        /**
         * The plugin the value is about is not installed (`plugin_enable_state`, the compiler plugins of appendix C Q15),
         * or a plugin directory of the archive has no settings provider on this device (D3, P13.1).
         */
        TARGET_MISSING,

        /** A category, or a type tag, of a newer format than this version reads. */
        NEWER_FORMAT,

        /** The user gave up the deployment item of the key (D20 / T18). */
        DECLINED,
    }

    /** Something a deployment item needs before it can run (T18). zh-CN: 部署项运行前需要的前置条件 (T18). */
    enum class Prerequisite {
        /** The editor font is downloaded through the Ace editor plugin, which is not installed. */
        ACE_EDITOR_PLUGIN,
    }

    /** [value] (stored form) written as [name] of [store]. zh-CN: 把 [value] (存储形式) 写为 [store] 中的 [name]. */
    data class Put(val category: String, val store: StoreId, val name: String, val value: Any, val effect: SettingEffect)

    /** [name] of [store] removed: its default applies again (D8). zh-CN: 删除 [store] 中的 [name], 恢复默认 (D8). */
    data class Remove(val category: String, val store: StoreId, val name: String, val effect: SettingEffect)

    /**
     * An entry of the archive left as it is: [name] of the store named [store] (null when the whole store could not be
     * read), why, and what was wrong when the reason alone does not say it ([detail]).
     * zh-CN: 保持原样的归档条目: 名为 [store] 的存储中的 [name] (整个存储无法读取时为 null), 原因, 以及原因之外的说明 ([detail]).
     */
    data class Skip @JvmOverloads constructor(val category: String, val store: String, val name: String?, val reason: SkipReason, val detail: String? = null)

    /**
     * A value written only once the deployment item [kind] has run (T18): the user confirms or gives it up, a deployment
     * that succeeds writes it ([toPut]); [prerequisites] are what the item needs first.
     * zh-CN: 部署项 [kind] 运行之后才写入的值 (T18): 由用户确认或放弃, 部署成功后写入 ([toPut]); [prerequisites] 为部署项的前置条件.
     */
    data class Deployment(
        val kind: DeploymentKind,
        val category: String,
        val store: StoreId,
        val name: String,
        val value: Any,
        val effect: SettingEffect,
        val prerequisites: Set<Prerequisite> = emptySet(),
    ) {
        fun toPut(): Put = Put(category, store, name, value, effect)
    }

    /** The number of skips per reason, every reason included. zh-CN: 每种原因的跳过数, 含计数为 0 的原因. */
    val skipCounts: Map<SkipReason, Int>
        get() = SkipReason.values().associateWithTo(LinkedHashMap()) { reason -> skips.count { it.reason == reason } }

    /** The stores the plan writes to, in the order of [puts] then [removes]. zh-CN: 计划写入的存储. */
    val stores: Set<StoreId>
        get() = (puts.map { it.store } + removes.map { it.store }).toCollection(LinkedHashSet())

    /**
     * This plan with [declined] given up (D20): they leave [deployments] and are skipped as [SkipReason.DECLINED], so
     * that their keys keep the current values.
     * zh-CN: 放弃 [declined] 之后的计划 (D20): 它们从 [deployments] 移出, 计为 [SkipReason.DECLINED], 其键保持当前值.
     */
    fun decline(declined: Collection<Deployment>): ImportPlan {
        val given = deployments.filter { it in declined }
        if (given.isEmpty()) return this
        return copy(
            deployments = deployments - given.toSet(),
            skips = skips + given.map { Skip(it.category, it.store.name, it.name, SkipReason.DECLINED) },
        )
    }

    companion object {

        /**
         * The category of the skip of a plugin directory (P13.1): `plugins/`, which no category id can be.
         * zh-CN: 插件目录被跳过时的分类 (P13.1): `plugins/`, 不会与任何分类 id 相同.
         */
        const val PLUGINS = SettingsArchive.PLUGINS_DIR
    }
}

/**
 * What an import plan needs to know about this device (T8): the installed plugins ([SkipReason.TARGET_MISSING]),
 * whether the Ace editor plugin is installed (the prerequisite of the editor font, T18) and whether the deployment of a
 * value is satisfied already ([isDeployed]: the directory exists, the font is installed). The app probes these before
 * planning (P7.5 / P7.6); tests pass plain values.
 *
 * zh-CN: 导入计划需要了解的本机情况 (T8): 已安装的插件 (`TARGET_MISSING`), Ace 编辑器插件是否已安装 (编辑器字体的前置条件, T18),
 * 以及某个值的部署是否已满足 ([isDeployed]: 目录已存在, 字体已安装). 应用在生成计划前探测 (P7.5 / P7.6); 测试直接传值.
 */
class ImportEnvironment @JvmOverloads constructor(
    val installedPlugins: Set<String> = emptySet(),
    val aceEditorPluginInstalled: Boolean = false,
    private val deployed: (kind: DeploymentKind, value: Any) -> Boolean = { _, _ -> false },
) {

    /** True when the deployment [kind] of [value] (stored form) needs nothing on this device. */
    fun isDeployed(kind: DeploymentKind, value: Any): Boolean = deployed(kind, value)
}
