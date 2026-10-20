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
 *
 * zh-CN: 开发者选项 (ROADMAP P2.5, 附录 A.1 `developer`, 默认不勾选): Augment 重构的新脚本模块层及其调试打印, Node.js inspector,
 * debug 构建的 LeakCanary.
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

    override val keys: List<PrefKey<*>> = listOf(
        NEW_SCRIPT_MODULE_LAYER, SCRIPT_MODULE_DEBUG_PRINT, SCRIPT_MODULE_DEBUG_PRINT_DISABLED_MODULES, NODEJS_INSPECTOR_ENABLED,
        LEAKCANARY_ENABLED,
    )

    // The doctor report entry of the developer options holds no value.
    override val names: List<RegisteredName> = excludedNames(R.string.key_nodejs_doctor_report)

    /** `Pref.scriptModuleDebugPrintDisabledModules`. */
    @JvmStatic
    var scriptModuleDebugPrintDisabledModules: Set<String>
        get() = SCRIPT_MODULE_DEBUG_PRINT_DISABLED_MODULES.storedOrNull()?.toSet() ?: emptySet()
        set(value) = SCRIPT_MODULE_DEBUG_PRINT_DISABLED_MODULES.set(value)
}
