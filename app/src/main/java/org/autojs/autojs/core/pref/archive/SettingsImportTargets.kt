package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.plugin.dex.DexCompilerComponentCodec
import org.autojs.autojs.core.plugin.r8.R8CompilerComponentCodec
import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.pref.keys.PluginCenterKeys

/**
 * Keys whose value is about one plugin (docs/settings/ROADMAP.md D9 / T8): an import skips them as `TARGET_MISSING`
 * when that plugin is not installed. [packages] names, for the entries of one store of an archive category, the
 * package each entry needs.
 *
 * zh-CN: 值与某个插件相关的键 (ROADMAP D9 / T8): 该插件未安装时导入计为 `TARGET_MISSING` 跳过. [packages] 对归档分类中一个存储的
 * 条目给出每个条目所需的包名.
 */
fun interface ImportTarget {

    /** The package each entry of [entries] (of the store named [store]) needs, by entry name; the others need none. */
    fun packages(store: String, entries: Map<String, TypedValue>): Map<String, String>
}

/**
 * The targets of the first phase (T8): the enable and suspension state per plugin (`plugin_enable_state`, named by
 * package, appendix C Q11) and the explicitly selected dex / R8 compiler plugins, a switch and the component it names,
 * skipped together when the component's plugin is not installed (appendix C Q15). Other plugin keys of
 * `plugin_center` (update checks, launcher icons, hidden plugins) stay importable: they apply once the plugin is
 * installed.
 *
 * zh-CN: 第一期的目标 (T8): 每个插件的启用与挂起状态 (`plugin_enable_state`, 以包名命名, 附录 C Q11), 以及显式选择的 dex / R8
 * 编译器插件 (一个开关与其指向的组件, 组件所属插件未安装时一起跳过, 附录 C Q15). `plugin_center` 的其他插件键 (检查更新, 启动图标,
 * 隐藏插件) 仍可导入: 插件安装后即生效.
 */
object SettingsImportTargets {

    /** Members of [family] need the package their name ends with. zh-CN: [family] 的成员需要其名称后缀所指的包. */
    @JvmStatic
    fun packageFamily(family: KeyFamily<*, *>): ImportTarget = ImportTarget { store, entries ->
        if (store != family.template.store.name) emptyMap()
        else entries.keys.mapNotNull { name -> family.suffixOf(name)?.takeIf { it.isNotEmpty() }?.let { name to it } }.toMap()
    }

    /**
     * [switch] and [component] need the package that [packageOf] reads from the stored component; when the archive
     * has no readable component, neither needs one (the app ignores a switch without a valid component).
     * zh-CN: [switch] 与 [component] 需要 [packageOf] 从组件值中读出的包; 归档中没有可读的组件时二者都不需要 (应用会忽略没有有效组件的开关).
     */
    @JvmStatic
    fun componentPair(switch: PrefKey<*>, component: PrefKey<*>, packageOf: (String) -> String?): ImportTarget = ImportTarget { store, entries ->
        require(switch.store == component.store) { "${switch.name} and ${component.name} are in different stores" }
        val value = entries[component.name]?.value as? String
        val packageName = value?.takeIf { store == component.store.name }?.let(packageOf)
        if (packageName == null) emptyMap() else listOf(switch.name, component.name).filter { it in entries }.associateWith { packageName }
    }

    @JvmField
    val ALL: List<ImportTarget> = listOf(
        packageFamily(PluginCenterKeys.PLUGIN_ENABLED),
        packageFamily(PluginCenterKeys.PLUGIN_SUSPENDED),
        componentPair(DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_ENABLED, DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_COMPONENT) {
            DexCompilerComponentCodec.decodeOrNull(it)?.packageName
        },
        componentPair(DeveloperKeys.R8_COMPILER_EXPLICIT_ENABLED, DeveloperKeys.R8_COMPILER_EXPLICIT_COMPONENT) {
            R8CompilerComponentCodec.decodeOrNull(it)?.packageName
        },
    )
}
