package org.autojs.autojs.runtime.api.modules.plugins

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptBuiltInExtender
import org.autojs.autojs.runtime.api.modules.ScriptErrorMapper
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * The `plugins` module on the new script module layer (roadmap P3.4): callable as `load`, with the data properties
 * `extend` (carrying `extend.exclude` and `extend.registerModule`), `extendAll` and `extendAllBut`, the function `load`
 * and the sub-module `tools` ([PluginsToolsModule]), in the order of the current module.
 *
 * The built-in extensions (`plugins.extend('Mathx')`, `plugins.extendAll()`) now go through the new layer's
 * [ScriptBuiltInExtender], as `jsox` does (P2.1 noted these two as the last callers of the old `Jsox`); the names and the
 * registered modules keep the current registry (`LegacyPluginExtensionRegistry`). The extension functions are written by
 * hand with the module's installation (created when the module object is built), so their errors are mapped like those of
 * generated members (T5). `load` keeps the current
 * implementation (application plugins and project plugins; to be moved out before P5.2), with a string name (D10).
 *
 * zh-CN: 新脚本模块层上的 plugins 模块 (P3.4): 可调用 (同 load), 数据属性 extend (带 extend.exclude 与 extend.registerModule), extendAll, extendAllBut,
 * 函数 load 与子模块 tools, 顺序同现有模块. 内置扩展 (plugins.extend('Mathx'), plugins.extendAll()) 改经新层的 ScriptBuiltInExtender, 与 jsox 相同 (P2.1 记录
 * 这两处是旧 Jsox 的最后调用方); 名称与注册的模块沿用现有登记表 (LegacyPluginExtensionRegistry). 扩展函数经模块的安装对象手写 (在模块对象构建时创建), 其错误与生成的成员一样经映射 (T5).
 * load 沿用现有实现 (应用插件与项目插件; P5.2 前迁出), 名称为字符串 (D10).
 */
@ScriptModule(name = "plugins")
class PluginsModule(private val runtime: ScriptRuntime) {

    private val registry = LegacyPluginExtensionRegistry()

    private val extender by lazy {
        val scope = runtime.topLevelScope
        ScriptBuiltInExtender(ScriptModules, runtime, scope, scope.globalThis, ScriptErrorMapper.DebugConsole { level, text -> runtime.console.println(level, text) })
    }

    private lateinit var installation: ScriptModuleInstallation

    /** Keeps the installation, which creates the extension functions when the module object is built. */
    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) {
        this.installation = installation
    }

    @ScriptProperty
    val extend: Any
        get() = installation.function("extend", 0) { cx, _, _, args -> perform(cx, registry.select(args)) }.also { extend ->
            extend.defineProperty("exclude", installation.function("exclude", 0) { _, _, _, args -> Undefined.instance.also { registry.exclude(args) } }, ScriptableObject.EMPTY)
            extend.defineProperty("registerModule", installation.function("registerModule", 0) { _, _, _, args -> Undefined.instance.also { register(args.firstOrNull()) } }, ScriptableObject.EMPTY)
        }

    @ScriptProperty
    val extendAll: Any
        get() = installation.function("extendAll", 0) { cx, _, _, _ -> perform(cx, registry.selectAll()) }

    @ScriptProperty
    val extendAllBut: Any
        get() = installation.function("extendAllBut", 0) { cx, _, _, args -> perform(cx, registry.selectAllBut(args)) }

    @ScriptInvoke
    fun invoke(name: String): Any? = Plugins.load(runtime, arrayOf(name))

    @ScriptFunction
    fun load(name: String): Any? = Plugins.load(runtime, arrayOf(name))

    private fun perform(cx: Context, actions: List<LegacyPluginExtensionRegistry.Action>): Any {
        actions.forEach { action ->
            when (action) {
                is LegacyPluginExtensionRegistry.Action.Preset -> extender.extend(action.name)
                is LegacyPluginExtensionRegistry.Action.Registered -> {
                    val value = action.value
                    require(!isNullish(value)) { "Registered plugin extension $value must expose extendJsBuildInObjects()" }
                    val extension = value as? Scriptable ?: Context.toObject(value, runtime.topLevelScope)
                    ScriptableObject.callMethod(cx, extension, "extendJsBuildInObjects", emptyArray())
                }
            }
        }
        return Undefined.instance
    }

    /** `extend.registerModule({ Name: extension, ... })`: every own property becomes a registered extension. */
    private fun register(module: Any?) {
        if (isNullish(module)) return
        val source = module as? Scriptable ?: Context.toObject(module, runtime.topLevelScope)
        registry.register(source.ids.mapNotNull { id ->
            when (id) {
                is Int -> id.toString() to ScriptableObject.getProperty(source, id)
                else -> runCatching { Context.toString(id) }.getOrNull()?.let { name -> name to ScriptableObject.getProperty(source, name) }
            }
        })
    }

}
