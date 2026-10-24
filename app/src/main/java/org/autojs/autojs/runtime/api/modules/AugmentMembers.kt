package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.ScriptableObject
import java.util.function.Supplier

/**
 * Members a module of roadmap P4.1 takes over from its current augment module unchanged, for what the annotations
 * cannot declare: the data properties the current module computes at run time (error constructors, objects of nested
 * functions, the forwarded client methods of `mail`, the catalog functions of `compose`) and its getters. Removed with
 * the `augment` package (P5.2), when the values move into the modules.
 *
 * zh-CN: P4.1 的模块从现有 augment 模块原样接过的成员, 即注解无法声明的部分: 现有模块在运行时计算的数据属性 (错误构造函数, 嵌套函数的对象, mail 转发的
 * 客户端方法, compose 的目录函数) 与 getter. 随 augment 包在 P5.2 删除 (届时这些值移入模块).
 */
internal object AugmentMembers {

    /**
     * Defines the data properties of [module] (`selfAssignmentProperties`, plain `name to value` pairs) on [target] in
     * their order, with the attributes of the current `Augmentable.assign` (PERMANENT; the values as they are).
     */
    @JvmStatic
    fun defineProperties(module: Augmentable, target: ScriptableObject) {
        module.selfAssignmentProperties.forEach { (name, value) ->
            target.defineProperty(name as String, value, ScriptableObject.PERMANENT)
        }
    }

    /**
     * Runs [parse], a parser of the current module that only rejects values (an unknown name or constant) with an
     * IllegalArgumentException, turning that into a RangeError of [function] with the same message (D10).
     */
    @JvmStatic
    fun <T> values(function: String, parse: () -> T): T = try {
        parse()
    } catch (e: IllegalArgumentException) {
        throw UtilValues.rangeError(function, e.message ?: function)
    }

    /** The current value of the getter [name] of [module] (`selfAssignmentGetters`, `name to Supplier`). */
    @JvmStatic
    fun getter(module: Augmentable, name: String): Any? {
        val supplier = module.selfAssignmentGetters.firstNotNullOf { item -> (item as Pair<*, *>).takeIf { it.first == name }?.second }
        @Suppress("UNCHECKED_CAST")
        return (supplier as Supplier<Any?>).get()
    }

}
