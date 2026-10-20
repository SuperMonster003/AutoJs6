package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

/**
 * The global function `isNullish(o?)` on the new script module layer (roadmap P2.2): whether [values]' first value is
 * `null` or `undefined` (`isNullish()` is true). It takes any number of arguments, as the current module does, because it
 * is a predicate scripts pass as a callback (`list.some(isNullish)` calls it with the element, the index and the array);
 * appendix C Q7 lets such functions declare a rest parameter instead of rejecting surplus arguments.
 *
 * zh-CN: 新脚本模块层上的全局函数 isNullish(o?) (P2.2): 第一个值是否为 null 或 undefined (isNullish() 为 true). 与现有模块一样接受任意个参数, 因为它是
 * 脚本常作为回调传入的谓词 (list.some(isNullish) 会传入元素, 索引与数组); 附录 C Q7 允许这类函数以剩余参数声明而不拒绝多余参数.
 */
@ScriptModule(name = "isNullish", dollarPrefix = false)
object IsNullishModule {

    @ScriptInvoke
    fun invoke(vararg values: Any?): Boolean {
        val value = values.firstOrNull()
        return value == null || Undefined.isUndefined(value) || value === Scriptable.NOT_FOUND
    }

}
