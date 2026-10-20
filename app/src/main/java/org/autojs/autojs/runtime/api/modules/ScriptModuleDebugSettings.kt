package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.modules.generated.ScriptModules

/**
 * The rules behind the debug printing switches of the new script module layer (roadmap T5, appendix C Q4), shared by
 * `ScriptModuleLayer` (the printing policy) and the developer options (P1.3): a master switch, off by default, and a
 * set of top-level modules excluded from printing, empty by default so that every module prints once the master switch
 * is on. The preferences store the excluded modules, so modules added later print by default.
 *
 * zh-CN: 新脚本模块层调试打印开关的规则 (T5, 附录 C Q4), 由 ScriptModuleLayer (打印策略) 与开发者选项 (P1.3) 共用: 总开关默认关; 偏好设置保存
 * 不打印的顶层模块, 默认为空, 因此总开关打开后全部模块都打印, 以后新增的模块也默认打印.
 */
object ScriptModuleDebugSettings {

    /** The top-level modules listed by the per-module switches, from the host registry. */
    @JvmStatic
    val moduleNames: List<String>
        get() = ScriptModules.moduleNames

    /** Whether the Java exceptions of top-level [module] print a debug entry. */
    @JvmStatic
    fun prints(module: String, masterEnabled: Boolean, excluded: Set<String>): Boolean = masterEnabled && module !in excluded

    /** The listed modules that print, in list order. */
    @JvmStatic
    fun printingModules(all: List<String>, excluded: Set<String>): List<String> = all.filter { it !in excluded }

    /**
     * The excluded set after the user checked [checked] among [all]: the unchecked listed modules, plus earlier entries
     * that are not listed (kept as they are).
     */
    @JvmStatic
    fun excludedAfter(all: List<String>, checked: Collection<String>, previous: Set<String>): Set<String> =
        (previous - all.toSet()) + all.filter { it !in checked }

}
