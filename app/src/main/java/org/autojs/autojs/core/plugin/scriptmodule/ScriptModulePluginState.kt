package org.autojs.autojs.core.plugin.scriptmodule

import java.io.Reader

/**
 * The plugin as the module provider sees it: [ScriptModulePlugins.access] on Android, a fake in JVM tests.
 * zh-CN: module provider 眼中的插件: Android 上为 [ScriptModulePlugins.access], JVM 测试中为替身.
 */
internal interface ScriptModulePluginAccess {

    fun state(): ScriptModulePluginState

    /** The source of the normalized module [path] of [plugin], or null when the plugin changed or the file is missing. */
    fun open(plugin: ScriptModulePluginState.Installed, path: String): Reader?

}

/**
 * What the host knows about the Bundled Libraries plugin at one moment (roadmap T11, design P4-3 (b) / (d)), and the
 * answer for a module id. Pure, so the four states are tested on the JVM; [ScriptModulePlugins] builds it from the
 * PackageManager and the Plugin Center stores.
 *
 * zh-CN: 宿主某一时刻对 Bundled Libraries 插件的了解 (ROADMAP T11, 设计 P4-3 (b) / (d)), 以及对模块 id 的回答. 纯逻辑, 四态在 JVM 上测试;
 * [ScriptModulePlugins] 由 PackageManager 与插件中心的存储构建它.
 */
internal sealed interface ScriptModulePluginState {

    /** Not installed (or not visible); only the [KNOWN_MODULES] get install guidance. zh-CN: 未安装; 只有已知名称给出安装指引. */
    data object NotInstalled : ScriptModulePluginState

    /**
     * Installed. [catalog] is null when the plugin declares none this host understands; [enabledLibraries] are the
     * libraries whose alias is enabled (roadmap Q100).
     */
    data class Installed(
        val versionCode: Long,
        val lastUpdateTime: Long,
        val authorized: Boolean,
        val pluginEnabled: Boolean,
        val catalog: ScriptModulePluginCatalog?,
        val enabledLibraries: Set<String>,
    ) : ScriptModulePluginState

    enum class Reason(val code: String) {
        NOT_INSTALLED("not-installed"),
        UNAUTHORIZED("unauthorized"),
        PLUGIN_DISABLED("plugin-disabled"),
        LIBRARY_DISABLED("library-disabled"),
        INCOMPATIBLE("incompatible"),
    }

    sealed interface Lookup {
        /** Not a plugin module: the next provider decides. zh-CN: 不是插件模块, 交给下一个 provider. */
        data object Unknown : Lookup

        data class Available(val plugin: Installed, val libraryId: String, val path: String) : Lookup

        data class Unavailable(val libraryId: String, val reason: Reason) : Lookup
    }

    /** The answer for the module [id]. zh-CN: 对模块 [id] 的回答. */
    fun lookup(id: String): Lookup {
        if (!ScriptModulePluginCatalog.isNamedId(id)) return Lookup.Unknown
        val path = ScriptModulePluginCatalog.normalizeId(id)
        val known = KNOWN_MODULES[path]
        return when (this) {
            NotInstalled -> known?.let { Lookup.Unavailable(it, Reason.NOT_INSTALLED) } ?: Lookup.Unknown
            is Installed -> {
                val library = catalog?.ownerOf(path)?.id
                    ?: return known?.let { Lookup.Unavailable(it, if (catalog == null) Reason.INCOMPATIBLE else Reason.NOT_INSTALLED) } ?: Lookup.Unknown
                when {
                    !authorized -> Lookup.Unavailable(library, Reason.UNAUTHORIZED)
                    !pluginEnabled -> Lookup.Unavailable(library, Reason.PLUGIN_DISABLED)
                    library !in enabledLibraries -> Lookup.Unavailable(library, Reason.LIBRARY_DISABLED)
                    else -> Lookup.Available(this, library, path)
                }
            }
        }
    }

    companion object {

        /**
         * The requirable names of the first Bundled Libraries release (the files of `assets/modules` that P5.2 removed) to
         * their library, for the guidance while there is no catalog to read. An installed plugin answers from its catalog.
         * zh-CN: Bundled Libraries 首个版本可 require 的名称 (即 P5.2 已删除的 `assets/modules` 文件) 到其所属的库, 用于没有清单可读时的指引.
         * 已安装的插件以其清单为准.
         */
        val KNOWN_MODULES: Map<String, String> = linkedMapOf(
            "axios.js" to "axios",
            "cheerio.js" to "cheerio",
            "dayjs.js" to "dayjs",
            "i18n.js" to "i18n",
            "banana-i18n.js" to "i18n",
            "pangu.js" to "pangu",
            "lodash.js" to "lodash",
            "buffer.js" to "buffer",
            "events.js" to "events",
            "fs.js" to "fs",
            "os.js" to "os",
            "path.js" to "path",
            "process.js" to "process",
            "nodejs.js" to "nodejs",
        )
    }

}
