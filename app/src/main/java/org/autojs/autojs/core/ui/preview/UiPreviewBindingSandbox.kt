package org.autojs.autojs.core.ui.preview

import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

/**
 * The binding context of an editor preview (3-Block Compose roadmap P11.10): `{{ }}` expressions, which the host
 * evaluates like a `with` block over this object, see the [values] of the script's literal declarations and a few
 * pure standard globals; every other name fails instead of reaching the runtime's top-level scope, so an expression
 * typed into the layout, such as one calling `files` or `shell`, is reported as a binding failure and never runs.
 *
 * zh-CN: 编辑器预览的绑定上下文 (3-Block Compose 路线图 P11.10): 宿主以 `with` 块的方式在此对象上求值 `{{ }}` 表达式, 只能看到脚本字面量声明的
 * [values] 与少量纯标准全局对象; 其他名称直接失败, 不会落到运行时的顶层作用域. 因此在布局里输入的表达式 (例如调用 `files` 或 `shell`)
 * 只会作为绑定失败报告出来, 不会执行.
 */
internal class UiPreviewBindingSandbox(private val values: Scriptable, scope: VarScope) : ScriptableObject() {

    init {
        // The host turns the binding context into an object against itself (Context.toObject(ctx, ctx)), which needs a parent scope.
        // zh-CN: 宿主以绑定上下文自身为作用域把它转为对象 (Context.toObject(ctx, ctx)), 这要求它有父作用域.
        parentScope = scope
    }

    override fun getClassName(): String = "Object"

    // Claiming every other name keeps the lookup here, so it never falls through to the runtime's top-level scope.
    // zh-CN: 声称拥有其他所有名称, 查找就停在这里, 不会落到运行时的顶层作用域.
    override fun has(name: String, start: Scriptable): Boolean = values.has(name, values) || name !in STANDARD_GLOBALS

    override fun get(name: String, start: Scriptable): Any? {
        if (values.has(name, values)) return values.get(name, values)
        if (name in STANDARD_GLOBALS) return NOT_FOUND
        throw UnavailableNameException(name)
    }

    override fun put(name: String, start: Scriptable, value: Any?) {
        throw UnavailableNameException(name)
    }

    /** The declared value of [name], or null (for [UiPreviewBindingGuard], which checks computed keys). */
    fun constant(name: String): Any? = if (values.has(name, values)) values.get(name, values) else null

    class UnavailableNameException(val name: String) :
        RuntimeException("$name is not available in the editor preview, which only knows the literal declarations of the script")

    companion object {
        /**
         * Globals without side effects that the expressions of a layout commonly use. Not `Object`: its reflection
         * functions (`getPrototypeOf`, `getOwnPropertyDescriptor`) would reach `Function` around [UiPreviewBindingGuard].
         */
        private val STANDARD_GLOBALS = setOf(
            "undefined", "NaN", "Infinity", "Math", "JSON", "String", "Number", "Boolean", "Array",
            "parseInt", "parseFloat", "isNaN", "isFinite", "encodeURIComponent", "decodeURIComponent",
        )
    }
}
