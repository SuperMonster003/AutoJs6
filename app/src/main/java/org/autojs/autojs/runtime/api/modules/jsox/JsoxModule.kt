package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptBuiltInExtender
import org.autojs.autojs.runtime.api.modules.ScriptErrorMapper
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleLayer
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `jsox` module on the new script module layer (roadmap P2.1): `jsox(...modules)` / `jsox.extend(...modules)` extend
 * the built-in objects with the given extension modules (names, arrays or nested arrays; `Mathx` or `Math` alike, a
 * trailing "x" is added and the first letter capitalized), all of them without names, as `jsox.extendAll()` does
 * (`Mathx`, `Numberx`, `Arrayx`). The modules come from the host registry ([ScriptBuiltInExtender]); an unknown name is a
 * RangeError (the current module threw a wrapped IllegalArgumentException).
 *
 * zh-CN: 新脚本模块层上的 jsox 模块 (P2.1): jsox(...modules) / jsox.extend(...modules) 用给定的扩展模块扩展内置对象 (名称, 数组或嵌套数组; Mathx 与 Math
 * 等价, 会补上末尾的 x 并把首字母大写), 不给名称时扩展全部, 同 jsox.extendAll() (Mathx, Numberx, Arrayx). 模块取自宿主注册表 (ScriptBuiltInExtender);
 * 未知名称为 RangeError (现有模块抛出包装的 IllegalArgumentException).
 */
@ScriptModule(name = "jsox")
class JsoxModule(private val runtime: ScriptRuntime) {

    private val extender by lazy {
        val scope = runtime.topLevelScope
        ScriptBuiltInExtender(ScriptModules, runtime, scope, scope.globalThis, ScriptErrorMapper.DebugConsole { level, text -> runtime.console.println(level, text) })
    }

    @ScriptInvoke
    fun invoke(vararg modules: Any?) = extendNamed("jsox", modules)

    @ScriptFunction
    fun extend(vararg modules: Any?) = extendNamed("jsox.extend", modules)

    @ScriptFunction
    fun extendAll() {
        ScriptModuleLayer.BUILT_IN_EXTENSIONS.forEach { extender.extend(it) }
    }

    private fun extendNamed(function: String, modules: Array<out Any?>) {
        val names = JsoxValues.flatten(modules).map { RhinoScriptRuntime.toString(it) }.distinct()
        if (names.isEmpty()) return extendAll()
        names.forEach { name ->
            val module = "${name.removeSuffix("x")}x".replaceFirstChar { it.uppercaseChar() }
            if (module !in ScriptModuleLayer.BUILT_IN_EXTENSIONS || !extender.extend(module)) {
                throw JsoxValues.rangeError(function, ScriptMessages.unknownBuiltInExtension(name, ScriptModuleLayer.BUILT_IN_EXTENSIONS.joinToString(", ")))
            }
        }
    }

}
