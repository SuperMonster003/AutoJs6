package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.core.pref.SettingsJson

/**
 * The `assets/script-modules.json` of a plugin that provides JavaScript modules (roadmap T11, design
 * `docs/augment/P4-3-bundled-libraries-design.md` (a)): libraries with the module names they answer and the files they
 * own under [moduleRoot]. A module id is normalized like the asset modules of the APK (a leading `modules/` dropped,
 * `.js` appended when missing) and belongs to the library that owns the resulting path.
 *
 * zh-CN: 提供 JavaScript 模块的插件的 `assets/script-modules.json` (ROADMAP T11, 设计 P4-3 (a)): 各库可 require 的模块名及其在 [moduleRoot] 下拥有的文件.
 * 模块 id 的规范化与 APK 资产模块相同 (去掉开头的 `modules/`, 缺少 `.js` 时补上), 规范化后的路径属于拥有它的库.
 */
internal class ScriptModulePluginCatalog private constructor(
    val moduleRoot: String,
    val libraries: List<Library>,
) {

    data class Library(
        val id: String,
        val group: String,
        /** Module names (with `.js`) to their paths. zh-CN: 模块名 (带 `.js`) 到路径. */
        val modules: Map<String, String>,
        /** Owned paths; an entry ending with `/` owns every path under it. zh-CN: 拥有的路径; 以 `/` 结尾的条目拥有其下所有路径. */
        val files: List<String>,
    ) {
        fun owns(path: String) = files.any { if (it.endsWith('/')) path.startsWith(it) else path == it }
    }

    /** The library owning [path] (a normalized module path), or null. */
    fun ownerOf(path: String): Library? = libraries.firstOrNull { it.owns(path) }

    /** The asset path in the plugin APK of the normalized module [path]. */
    fun assetPath(path: String) = "$moduleRoot/$path"

    companion object {

        const val SCHEMA_VERSION = 1L

        /**
         * The catalog in [text], or null when it is not one this host understands (another schema version, a member
         * missing or of another type, an unsafe path).
         */
        fun parse(text: String): ScriptModulePluginCatalog? = runCatching {
            val root = SettingsJson.parseMap(text)
            if ((root["schemaVersion"] as? Number)?.let(SettingsJson::toLong) != SCHEMA_VERSION) return null
            val moduleRoot = (root["moduleRoot"] as? String)?.takeIf(::isSafePath) ?: return null
            val libraries = (root["libraries"] as? List<*>)?.map { raw ->
                val library = raw as Map<*, *>
                val modules = (library["modules"] as Map<*, *>).entries.associate { (name, path) ->
                    normalizeId(name as String) to (path as String).also { require(isSafePath(it)) }
                }
                Library(
                    id = library["id"] as String,
                    group = library["group"] as String,
                    modules = modules,
                    files = (library["files"] as List<*>).map { (it as String).also { path -> require(isSafePath(path.removeSuffix("/"))) } },
                )
            } ?: return null
            ScriptModulePluginCatalog(moduleRoot, libraries)
        }.getOrNull()

        /**
         * [id] as a module path: separators unified, a leading `modules/` dropped (the asset modules require each other
         * as `modules/axios/...`), `.js` appended unless present; null for relative, absolute, URL or escaping ids.
         * zh-CN: [id] 作为模块路径: 统一分隔符, 去掉开头的 `modules/` (资产模块之间以 `modules/axios/...` 互相引用), 没有 `.js` 时补上; 相对, 绝对,
         * URL 或越界的 id 为 null.
         */
        fun normalizeId(id: String): String {
            var path = id.replace('\\', '/')
            if (path.startsWith(MODULES_PREFIX)) path = path.substring(MODULES_PREFIX.length)
            return if (path.endsWith(JS, ignoreCase = true)) path else path + JS
        }

        /** Whether [id] may name a plugin module at all: not relative, absolute, a URL or empty. */
        fun isNamedId(id: String): Boolean {
            if (id.isEmpty() || id.startsWith("./") || id.startsWith("../") || id == "." || id == "..") return false
            if (id.startsWith('/') || id.startsWith('\\') || URI_SCHEME.containsMatchIn(id)) return false
            return isSafePath(id.replace('\\', '/'))
        }

        private fun isSafePath(path: String) = path.isNotEmpty() && !path.startsWith('/') &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." }

        private const val MODULES_PREFIX = "modules/"
        private const val JS = ".js"
        private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    }

}
