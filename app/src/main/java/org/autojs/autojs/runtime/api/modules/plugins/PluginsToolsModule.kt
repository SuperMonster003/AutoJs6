package org.autojs.autojs.runtime.api.modules.plugins

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.plugins.PluginsTools
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

/**
 * `plugins.tools` on the new script module layer (roadmap P3.4): the plugin tools of the current module (`list`,
 * `providers`, `call`, `callAsync`), served by its implementation (`augment/plugins/PluginsTools`, the `plugin_tools`
 * bridge shared with MCP Server; to be moved out before P5.2). `call` checks its arguments first (a tool name that is not a
 * string, tool arguments or options that are not objects are TypeErrors, a blank name a RangeError; D10, the current module
 * threw IllegalArgumentException); `callAsync` takes its arguments as given, because the documentation makes every failure
 * of the asynchronous form a rejection of its Promise.
 *
 * zh-CN: 新脚本模块层上的 plugins.tools (P3.4): 现有模块的插件工具 (list, providers, call, callAsync), 由其实现完成 (augment/plugins/PluginsTools, 与
 * MCP Server 共用 plugin_tools 桥; P5.2 前迁出). call 先检查参数 (工具名不是字符串, 工具参数或选项不是对象为 TypeError, 名称为空白为 RangeError; D10, 现有模块抛
 * IllegalArgumentException); callAsync 原样接收参数, 因为文档规定异步形态的一切失败都以 Promise 拒绝.
 */
@ScriptModule(name = "tools", parent = PluginsModule::class, dollarPrefix = false)
class PluginsToolsModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { PluginsTools(runtime) }

    @ScriptFunction
    fun list(): Any? = impl.list(runtime, emptyArray())

    @ScriptFunction
    fun providers(): Any? = impl.providers(runtime, emptyArray())

    @ScriptFunction
    fun call(name: Any?, args: Any? = null, options: Any? = null): Any? {
        val text = when (name) {
            is CharSequence -> name.toString()
            else -> throw GlobalValues.typeError("plugins.tools.call", "name", "string", name)
        }
        if (text.isBlank()) throw GlobalValues.rangeError("plugins.tools.call", "name", "a tool name", name)
        if (!isNullish(args) && args !is NativeObject) throw GlobalValues.typeError("plugins.tools.call", "args", "Object", args)
        if (!isNullish(options) && options !is Scriptable) throw GlobalValues.typeError("plugins.tools.call", "options", "Object", options)
        return impl.call(runtime, arrayOf(text, args, options))
    }

    @ScriptFunction(arity = [1, 3])
    fun callAsync(vararg args: Any?): Any = impl.callAsync(runtime, args)

}
