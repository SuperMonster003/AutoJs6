package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues

/**
 * The legacy global functions on the new script module layer (roadmap P3.0): an assign module with `isObject` and
 * `isObjectSpecies`, which check a non-null object by its JavaScript `typeof` (arrays and dates included, functions
 * not), as documented for `isObjectSpecies` and as the current `augment/global/Legacy` does for both. `species` (mounted
 * after this module) replaces the global `isObject` with `species.isObject` and keeps its position, as it does now.
 *
 * zh-CN: 新脚本模块层上的 legacy 全局函数 (P3.0): assign 模块, 含 isObject 与 isObjectSpecies, 按 JavaScript 的 typeof 判断非空对象 (数组与日期为真, 函数为假),
 * 与文档对 isObjectSpecies 的描述及现有 augment/global/Legacy 对两者的实现相同. 之后挂载的 species 以 species.isObject 替换全局 isObject 并保留其位置, 与现在相同.
 */
@ScriptModule(name = "legacy", assign = true)
object LegacyModule {

    @ScriptFunction
    fun isObject(o: Any?): Boolean = isNonNullObject(o)

    @ScriptFunction
    fun isObjectSpecies(o: Any?): Boolean = isNonNullObject(o)

    private fun isNonNullObject(o: Any?): Boolean = o != null && UtilValues.typeOf(o) == "object"

}
