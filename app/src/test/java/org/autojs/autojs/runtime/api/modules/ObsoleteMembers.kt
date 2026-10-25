package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.modules.differential.SurfaceDiff
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Roadmap P3.6: the members of an obsolete module (`assets/modules/obsolete`, loaded by `ObsoleteModuleHost`; both
 * removed in P5.2, the surfaces are recorded) that the new layer lacks or has with another type. The surfaces are compared by [SurfaceDiff], which stops below
 * `Object.prototype`; a member only the obsolete surface has counts as present when the new object reads one of the same
 * type through its prototype chain (the obsolete modules defined `toString` on their prototypes, the new objects inherit it).
 *
 * zh-CN: P3.6: 旧模块 (`assets/modules/obsolete`, 由 ObsoleteModuleHost 加载; 两者已在 P5.2 删除, 表面为记录值) 中新层缺失或类型不同的成员. 表面由 SurfaceDiff 比较, 它止于
 * Object.prototype 之下; 只在旧表面中的成员, 若新对象经原型链读到同类型的值, 视为存在 (旧模块在原型上定义 toString, 新对象继承它).
 */
internal object ObsoleteMembers {

    /**
     * `name: obsolete <type>, new <type>` for every member of the obsolete surface [first] (recorded, see
     * `ScriptModuleExpectations.obsoleteSurface`) that [new] lacks or has with another type.
     */
    fun problems(first: Map<String, SurfaceDiff.Member>, new: Any?, newScope: Scriptable): List<String> {
        val report = SurfaceDiff.compare(first, SurfaceDiff.surface(new, newScope))
        val absent = report.onlyInFirst.sorted().mapNotNull { name ->
            val type = typeOf(new as Scriptable, name)
            if (type == first.getValue(name).type) null else "$name: obsolete ${first.getValue(name).type}, new $type"
        }
        return absent + report.typeMismatches.toSortedMap().map { (name, types) -> "$name: obsolete ${types.first}, new ${types.second}" }
    }

    /** `typeof` of the property [name] of [scope] (inherited included), `missing` when it has none. */
    fun typeOf(scope: Scriptable, name: String): String = when (val value = ScriptableObject.getProperty(scope, name)) {
        Scriptable.NOT_FOUND -> "missing"
        else -> RhinoScriptRuntime.`typeof`(value)
    }

}
