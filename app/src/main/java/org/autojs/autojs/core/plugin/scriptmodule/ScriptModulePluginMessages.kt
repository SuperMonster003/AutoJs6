package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Reason
import org.autojs.autojs6.R

/**
 * The install and enable guidance of a plugin module that cannot be loaded now (design P4-3 (d)), from the host's
 * string resources (`strings_script_module_plugins.xml`, 10 languages); without an Android context (JVM tests) the
 * English text of the default resources, which `ScriptModulePluginMessagesTest` keeps equal to them.
 *
 * zh-CN: 当前不能加载的插件模块的安装与启用指引 (设计 P4-3 (d)), 取自宿主字符串资源 (`strings_script_module_plugins.xml`, 10 种语言);
 * 没有 Android Context 时 (JVM 测试) 用默认资源的英文文本, 由 ScriptModulePluginMessagesTest 保证一致.
 */
internal object ScriptModulePluginMessages {

    /** The message for module [id] of [libraryId] that is unavailable for [reason]. */
    fun unavailable(id: String, libraryId: String, reason: Reason): String {
        val (resource, fallback) = when (reason) {
            Reason.NOT_INSTALLED -> R.string.error_script_module_plugin_not_installed to NOT_INSTALLED
            Reason.UNAUTHORIZED -> R.string.error_script_module_plugin_not_authorized to UNAUTHORIZED
            Reason.PLUGIN_DISABLED -> R.string.error_script_module_plugin_disabled to PLUGIN_DISABLED
            Reason.LIBRARY_DISABLED -> R.string.error_script_module_plugin_library_disabled to LIBRARY_DISABLED
            Reason.INCOMPATIBLE -> R.string.error_script_module_plugin_incompatible to INCOMPATIBLE
        }
        val args = arrayOf(id, ScriptModulePlugins.PLUGIN_TITLE, libraryId)
        return try {
            GlobalAppContext.get().getString(resource, *args)
        } catch (_: RuntimeException) {
            String.format(java.util.Locale.ROOT, fallback, *args)
        }
    }

    internal const val NOT_INSTALLED = "Cannot find module '%1\$s': it is provided by the official plugin \"%2\$s\", which is not installed. Install the plugin in AutoJs6 > Plugin center."
    internal const val UNAUTHORIZED = "Cannot find module '%1\$s': the plugin \"%2\$s\" that provides it is not authorized. Authorize the plugin in AutoJs6 > Plugin center."
    internal const val PLUGIN_DISABLED = "Cannot find module '%1\$s': the plugin \"%2\$s\" that provides it is disabled. Enable the plugin in AutoJs6 > Plugin center."
    internal const val LIBRARY_DISABLED = "Cannot find module '%1\$s': the library \"%3\$s\" is switched off in the plugin \"%2\$s\". Switch it on in AutoJs6 > Plugin center > %2\$s."
    internal const val INCOMPATIBLE = "Cannot find module '%1\$s': the installed plugin \"%2\$s\" cannot be read by this version of AutoJs6. Update AutoJs6 or the plugin."

}
