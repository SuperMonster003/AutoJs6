package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.DEVELOPER
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Developer options (docs/settings/ROADMAP.md P2.5, appendix A.1 `developer`, not selected by default): the new script
 * module layer of the Augment refactor with its debug printing, the Node.js inspector and LeakCanary of debug builds.
 * P3.6 (appendix C Q15) adds the explicitly selected dex and R8 compiler plugins, each a switch and the component it
 * names (`DexCompilerOptInStore` / `R8CompilerOptInStore`); an import skips a pair whose plugin is not installed
 * (`TARGET_MISSING`, P7.3).
 *
 * zh-CN: 开发者选项 (ROADMAP P2.5, 附录 A.1 `developer`, 默认不勾选): Augment 重构的新脚本模块层及其调试打印, Node.js inspector,
 * debug 构建的 LeakCanary. P3.6 (附录 C Q15) 加入显式选择的 dex 与 R8 编译器插件 (各为一个开关与其指向的组件); 导入时组件所属
 * 插件未安装则整组跳过 (`TARGET_MISSING`, P7.3).
 */
object DeveloperKeys : SettingsDomain {

    /** Scripts started while it is on mount the migrated modules from the new layer (docs/augment/ROADMAP.md T8). */
    @JvmField
    val NEW_SCRIPT_MODULE_LAYER = SettingKey.Bool(R.string.key_new_script_module_layer, DEVELOPER, SettingDefault.of(false))

    /** Master switch of the debug printing of the new module layer (docs/augment/ROADMAP.md T5). */
    @JvmField
    val SCRIPT_MODULE_DEBUG_PRINT = SettingKey.Bool(R.string.key_script_module_debug_print, DEVELOPER, SettingDefault.of(false))

    /** Top-level modules left out of the debug printing; missing means none ([scriptModuleDebugPrintDisabledModules]). */
    @JvmField
    val SCRIPT_MODULE_DEBUG_PRINT_DISABLED_MODULES = SettingKey.StrSet(R.string.key_script_module_debug_print_disabled_modules, DEVELOPER, SettingDefault.None)

    /** Starts Node.js scripts with the inspector unless the request says otherwise (`NodeJsRuntimePluginBridge`). */
    @JvmField
    val NODEJS_INSPECTOR_ENABLED = SettingKey.Bool(R.string.key_nodejs_inspector_enabled, DEVELOPER, SettingDefault.of(false))

    /**
     * LeakCanary in debug builds (memory roadmap P1.2; the switch is shown in debug builds only); added after the P0
     * inventory and classified by appendix C Q15.
     * zh-CN: debug 构建的 LeakCanary (内存路线图 P1.2; 开关仅在 debug 构建显示); 在 P0 清单之后加入, 按附录 C Q15 登记.
     */
    @JvmField
    val LEAKCANARY_ENABLED = SettingKey.Bool(R.string.key_leakcanary_enabled, DEVELOPER, SettingDefault.of(true))

    /**
     * Warn in the console when a finished script is not reclaimed (memory roadmap P2.3, `MemoryDiagnostics`); added
     * after the P0 inventory and registered like `key_leakcanary_enabled` (appendix C Q16 / Q21).
     * zh-CN: 已结束的脚本未被回收时在控制台警告 (内存路线图 P2.3); 在 P0 清单之后加入, 与 `key_leakcanary_enabled` 一样登记 (附录 C Q16 / Q21).
     */
    @JvmField
    val MEMORY_UNRECLAIMED_WARNING = SettingKey.Bool(R.string.key_memory_unreclaimed_warning, DEVELOPER, SettingDefault.of(true))

    /**
     * Allow scripts to export hprof: `memory.dumpHeap` of release builds (memory roadmap P5.1, appendix C Q8; debug builds always
     * allow it); added after the P0 inventory and registered like `key_memory_unreclaimed_warning`.
     * zh-CN: 允许脚本导出 hprof: release 构建的 `memory.dumpHeap` (内存路线图 P5.1, 附录 C Q8; debug 构建始终允许); 在 P0 清单之后加入, 与
     * `key_memory_unreclaimed_warning` 一样登记.
     */
    @JvmField
    val MEMORY_SCRIPT_HEAP_DUMP = SettingKey.Bool(R.string.key_memory_script_heap_dump, DEVELOPER, SettingDefault.of(false))

    /** The experimental dex compiler plugin is used (only together with a valid component, `DexCompilerOptInStore.read`). */
    @JvmField
    val DEX_COMPILER_EXPERIMENTAL_ENABLED = SettingKey.Bool(name = "dex_compiler_experimental_enabled", category = DEVELOPER, default = SettingDefault.of(false))

    /** The selected dex compiler component (`DexCompilerComponentCodec`); kept when the switch is turned off. */
    @JvmField
    val DEX_COMPILER_EXPERIMENTAL_COMPONENT = SettingKey.Str(name = "dex_compiler_experimental_component", category = DEVELOPER, default = SettingDefault.None)

    /** The explicitly selected R8 compiler plugin is used (only together with a valid component, `R8CompilerOptInStore.read`). */
    @JvmField
    val R8_COMPILER_EXPLICIT_ENABLED = SettingKey.Bool(name = "r8_compiler_explicit_enabled", category = DEVELOPER, default = SettingDefault.of(false))

    /** The selected R8 compiler component (`R8CompilerComponentCodec`); kept when the switch is turned off. */
    @JvmField
    val R8_COMPILER_EXPLICIT_COMPONENT = SettingKey.Str(name = "r8_compiler_explicit_component", category = DEVELOPER, default = SettingDefault.None)

    override val keys: List<PrefKey<*>> = listOf(
        NEW_SCRIPT_MODULE_LAYER, SCRIPT_MODULE_DEBUG_PRINT, SCRIPT_MODULE_DEBUG_PRINT_DISABLED_MODULES, NODEJS_INSPECTOR_ENABLED,
        LEAKCANARY_ENABLED, MEMORY_UNRECLAIMED_WARNING, MEMORY_SCRIPT_HEAP_DUMP,
        DEX_COMPILER_EXPERIMENTAL_ENABLED, DEX_COMPILER_EXPERIMENTAL_COMPONENT, R8_COMPILER_EXPLICIT_ENABLED, R8_COMPILER_EXPLICIT_COMPONENT,
    )

    // The doctor report entry of the developer options holds no value.
    override val names: List<RegisteredName> = excludedNames(R.string.key_nodejs_doctor_report)

    /** `Pref.scriptModuleDebugPrintDisabledModules`. */
    @JvmStatic
    var scriptModuleDebugPrintDisabledModules: Set<String>
        get() = SCRIPT_MODULE_DEBUG_PRINT_DISABLED_MODULES.storedOrNull()?.toSet() ?: emptySet()
        set(value) = SCRIPT_MODULE_DEBUG_PRINT_DISABLED_MODULES.set(value)
}
