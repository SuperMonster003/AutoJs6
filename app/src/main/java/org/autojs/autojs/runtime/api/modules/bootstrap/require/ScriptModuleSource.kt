package org.autojs.autojs.runtime.api.modules.bootstrap.require

import org.autojs.autojs.engine.module.ScriptModuleUnavailableException

/**
 * Named modules (roadmap T11, P3.7): Node core module names and library names, consulted before the local lookup (core
 * names) or after it misses (everything else). P3.7 provides the asset source; the plugin modules of P4.3 reach it
 * through the same source, as a provider of the engine's Rhino `Require` (`PluginModuleScriptProvider`, design
 * `docs/augment/P4-3-bundled-libraries-design.md` (b)), so that they share its module cache with the global getters.
 *
 * zh-CN: 命名模块 (ROADMAP T11, P3.7): Node 核心模块名与库名, 本地解析之前 (核心名) 或未命中之后 (其余) 查询. P3.7 提供资产源; P4.3 的插件模块作为引擎
 * Rhino Require 的 provider (`PluginModuleScriptProvider`, 设计 P4-3 (b)) 经同一个源到达, 从而与全局 getter 共用模块缓存.
 */
internal interface ScriptModuleSource {

    /** Whether [name] is a Node core module name this source provides; reads cached indexes only, no IO. */
    fun isCore(name: String): Boolean

    /**
     * Exports of the named module [id], or [ABSENT] when this source does not know [id]. Throws an Error with install
     * guidance when it knows [id] but cannot provide it now (plugin not installed or disabled): a
     * `ScriptModuleUnavailableException`, which `ScriptRequire` moves to the position of the calling script.
     */
    fun require(id: String): Any?

    companion object {
        val ABSENT = Any()
    }

}

/**
 * The sources of a runtime in order: a name is core when any source says so, and a module comes from the first source
 * that knows it.
 * zh-CN: 一个运行时的各个源, 按顺序: 任一源认为是核心名即为核心名, 模块取自第一个认识它的源.
 */
internal class ScriptModuleSources(private val sources: List<ScriptModuleSource>) : ScriptModuleSource {

    override fun isCore(name: String) = sources.any { it.isCore(name) }

    override fun require(id: String): Any? {
        for (source in sources) {
            val exports = source.require(id)
            if (exports !== ScriptModuleSource.ABSENT) return exports
        }
        return ScriptModuleSource.ABSENT
    }

}

/**
 * The modules of the APK assets (`assets/modules`), loaded by the engine's Rhino `Require` so that a module is the same
 * instance as the one of the global getters (`require('axios') === axios`): the Node core modules of jvm-npm (buffer,
 * events, fs, lodash, nodejs, os, path, process) and any id the engine's module script providers can supply (assets and
 * their sub-paths, existing absolute paths). Relative ids are not named modules: a relative id that the local lookup
 * misses is not found, where jvm-npm passed it to the native `require`, which resolved it against jvm-npm's own asset
 * directory.
 *
 * zh-CN: APK 资产 (`assets/modules`) 中的模块, 由引擎的 Rhino Require 加载, 因此与全局 getter 的模块是同一实例 (`require('axios') === axios`):
 * jvm-npm 的 Node 核心模块 (buffer, events, fs, lodash, nodejs, os, path, process) 与引擎的模块脚本提供者能提供的任意 id (资产及其子路径,
 * 存在的绝对路径). 相对 id 不是命名模块: 本地解析未命中的相对 id 即为找不到; jvm-npm 曾把它交给原生 require, 后者按 jvm-npm 自身的资产目录解析.
 *
 * @param canLoad whether the engine's providers can supply [id] (no module is executed)
 * @param load the exports of [id] from the engine's `Require`
 */
internal class AssetModuleSource(
    private val canLoad: (id: String) -> Boolean,
    private val load: (id: String) -> Any?,
) : ScriptModuleSource {

    override fun isCore(name: String) = ScriptModuleResolver.normalizeName(name) in CORE_FILES

    override fun require(id: String): Any? = when {
        id.startsWith("./") || id.startsWith("../") -> ScriptModuleSource.ABSENT
        loadable(id) -> load(id)
        else -> ScriptModuleSource.ABSENT
    }

    /**
     * Whether the providers can supply [id]. A plugin module that cannot be loaded now (design P4-3 (d)) is still taken
     * from the engine's `Require` when this script loaded it before the switch, as with jvm-npm; otherwise its `Require`
     * throws the same guidance error.
     * zh-CN: provider 能否提供 [id]. 当前不能加载的插件模块 (设计 P4-3 (d)) 若本脚本在开关之前已加载, 仍从引擎的 Require 取得, 同 jvm-npm; 否则 Require 抛出同样的指引错误.
     */
    private fun loadable(id: String): Boolean = try {
        canLoad(id)
    } catch (_: ScriptModuleUnavailableException) {
        true
    }

    companion object {
        /** jvm-npm's `builtInModules`. */
        val CORE_FILES = setOf("buffer.js", "events.js", "fs.js", "lodash.js", "nodejs.js", "os.js", "path.js", "process.js")
    }

}
