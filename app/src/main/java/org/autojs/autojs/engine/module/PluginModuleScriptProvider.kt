package org.autojs.autojs.engine.module

import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginAccess
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginMessages
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Lookup
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePlugins
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.commonjs.module.ModuleScript
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider
import java.io.Reader
import java.lang.ref.SoftReference
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * The JavaScript modules of the Bundled Libraries plugin (roadmap T11 / P4.3, design
 * `docs/augment/P4-3-bundled-libraries-design.md` (b)) in the provider chain of the engine's Rhino `Require`, after the
 * APK assets (the local files win while `assets/modules` still has them, until P5.2) and before files and URLs. Both
 * states of the module layer reach it: the Kotlin `require` (P3.7) through `canRequireNamedModule`, jvm-npm through its
 * native fallback, and the global getters (`axios` ...) through the engine's `require`, so a plugin module is one
 * instance for all of them.
 *
 * For an id that a plugin module of the current state answers, the compiled script (shared by the engines of the process,
 * like [ProcessWideAssetModuleScriptProvider], per plugin version); for a known id that the plugin cannot give now (not
 * installed, not authorized, switched off in the Plugin Center or on the plugin's page), a [ScriptModuleUnavailableException]
 * with the guidance; for anything else null, for the next provider.
 *
 * zh-CN: Bundled Libraries 插件的 JavaScript 模块 (ROADMAP T11 / P4.3, 设计 P4-3 (b)), 位于引擎 Rhino Require 的 provider 链中, 在 APK 资产之后
 * (assets/modules 仍有文件时本地优先, 直到 P5.2), 文件与 URL 之前. 模块层两态都经过它: Kotlin require (P3.7) 经 canRequireNamedModule, jvm-npm 经其原生回退,
 * 全局 getter (axios 等) 经引擎的 require, 因此插件模块对它们是同一实例. 当前状态下插件模块能回答的 id 返回编译结果 (同进程各引擎共享, 同
 * ProcessWideAssetModuleScriptProvider, 按插件版本); 插件现在不能提供的已知 id (未安装, 未授权, 在插件中心或插件页面关闭) 抛出带指引的
 * [ScriptModuleUnavailableException]; 其余返回 null, 交给下一个 provider.
 *
 * @param plugin the plugin state and files ([ScriptModulePlugins.access] on Android)
 * @param scope the engine's top-level scope, for the `Error` of a module that cannot be loaded
 */
internal class PluginModuleScriptProvider(
    private val plugin: ScriptModulePluginAccess,
    private val scope: VarScope,
) : ModuleScriptProvider {

    override fun getModuleScript(context: Context, moduleId: String, uri: URI?, base: URI?, paths: Scriptable?): ModuleScript? {
        val id = when (uri) {
            null -> moduleId
            else -> pathOf(uri) ?: return null
        }
        return when (val lookup = plugin.state().lookup(id)) {
            Lookup.Unknown -> null
            is Lookup.Unavailable -> throw ScriptModuleUnavailableException.create(context, scope, moduleId, lookup.libraryId, lookup.reason)
            is Lookup.Available -> moduleScript(context, lookup)
        }
    }

    private fun moduleScript(context: Context, lookup: Lookup.Available): ModuleScript? {
        val installed = lookup.plugin
        val key = Key(installed.versionCode, installed.lastUpdateTime, RhinoCompilationProfile.from(context), lookup.path)
        scripts[key]?.get()?.let { return it }
        val reader: Reader = plugin.open(installed, lookup.path) ?: return null
        val moduleUri = uriOf(lookup.path)
        val script = reader.use { ModuleScript(context.compileReader(it, moduleUri.toString(), 1, null), moduleUri, BASE_URI) }
        scripts.keys.removeAll { it.versionCode != key.versionCode || it.lastUpdateTime != key.lastUpdateTime }
        scripts[key] = SoftReference(script)
        return script
    }

    private data class Key(val versionCode: Long, val lastUpdateTime: Long, val profile: RhinoCompilationProfile, val path: String)

    companion object {

        /** Source names and module URIs of plugin modules; nothing is read through them. */
        private const val URI_PATH_PREFIX = "/android_asset/plugin-modules/${ScriptModulePlugins.PACKAGE_NAME}/"
        private val BASE_URI = URI("file", "", URI_PATH_PREFIX, null, null)

        /**
         * Compiled plugin modules, shared by the engines of the process as the asset modules are; values are soft, and the
         * entries of an older plugin version go when a newer one is compiled.
         * zh-CN: 已编译的插件模块, 与资产模块一样由进程内各引擎共享; 值为软引用, 编译新版本时移除旧版本的条目.
         */
        private val scripts = ConcurrentHashMap<Key, SoftReference<ModuleScript>>()

        private fun uriOf(path: String) = URI("file", "", URI_PATH_PREFIX + path, null, null)

        /** The module path of a [uri] under [BASE_URI] (a relative `require` inside a plugin module), else null. */
        private fun pathOf(uri: URI): String? {
            if (!uri.scheme.equals("file", ignoreCase = true) || !uri.authority.isNullOrEmpty()) return null
            val path = uri.normalize().path ?: return null
            return path.takeIf { it.startsWith(URI_PATH_PREFIX) }?.substring(URI_PATH_PREFIX.length)
        }

        internal fun clearForTests() = scripts.clear()
    }

}

/**
 * A plugin module that cannot be loaded now: a script `Error` with `code` `MODULE_NOT_FOUND` (as the Kotlin `require`
 * of P3.7, roadmap Q83), `library` (the library id) and `reason` (`not-installed`, `unauthorized`, `plugin-disabled`,
 * `library-disabled`, `incompatible`), thrown as a JavaScript exception so that every caller (jvm-npm's native fallback,
 * the global getters) gives scripts the same error; the Kotlin `require` moves it to the position of the calling script.
 * zh-CN: 当前不能加载的插件模块: 脚本 Error, code 为 MODULE_NOT_FOUND (同 P3.7 Kotlin require, Q83), library 为库 id, reason 为原因; 作为 JavaScript 异常抛出,
 * 使各调用方 (jvm-npm 的原生回退, 全局 getter) 给脚本同样的错误; Kotlin require 会把它移到调用脚本的位置.
 */
internal class ScriptModuleUnavailableException private constructor(
    error: Scriptable,
    sourceName: String?,
    lineNumber: Int,
    val moduleId: String,
    val libraryId: String,
    val reason: ScriptModulePluginState.Reason,
    val text: String,
) : org.mozilla.javascript.JavaScriptException(error, sourceName, lineNumber) {

    companion object {

        const val MODULE_NOT_FOUND = "MODULE_NOT_FOUND"

        fun create(cx: Context, scope: VarScope, moduleId: String, libraryId: String, reason: ScriptModulePluginState.Reason): ScriptModuleUnavailableException {
            val text = ScriptModulePluginMessages.unavailable(moduleId, libraryId, reason)
            val error = errorObject(cx, scope, text, libraryId, reason)
            val line = IntArray(1)
            val sourceName = Context.getSourcePositionFromStack(line)
            return ScriptModuleUnavailableException(error, sourceName, line[0], moduleId, libraryId, reason, text)
        }

        /** The `Error` for [text] with the properties of this exception. */
        fun errorObject(cx: Context, scope: VarScope, text: String, libraryId: String, reason: ScriptModulePluginState.Reason): Scriptable =
            cx.newObject(scope, "Error", arrayOf(text)).also {
                ScriptableObject.putProperty(it, "code", MODULE_NOT_FOUND)
                ScriptableObject.putProperty(it, "library", libraryId)
                ScriptableObject.putProperty(it, "reason", reason.code)
            }
    }

}
