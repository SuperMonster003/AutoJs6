package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Reason
import org.autojs.autojs6.BuildConfig
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

    /**
     * The message for module [id] of [libraryId] that is unavailable for [reason]. A [packaged] app has no Plugin
     * center, so it gets one of two messages for its developer (roadmap P7.1, Q188).
     * zh-CN: 模块 [id] (属于 [libraryId]) 因 [reason] 不可用时的消息. 打包应用 ([packaged]) 没有插件中心, 改用面向开发者的两条消息之一 (P7.1, Q188).
     */
    fun unavailable(id: String, libraryId: String, reason: Reason, packaged: Boolean = BuildConfig.isInrt): String {
        val (resource, fallback) = when {
            packaged && reason == Reason.LIBRARY_DISABLED -> R.string.error_script_module_packaged_library_disabled to PACKAGED_LIBRARY_DISABLED
            packaged -> R.string.error_script_module_packaged_not_included to PACKAGED_NOT_INCLUDED
            reason == Reason.NOT_INSTALLED -> R.string.error_script_module_plugin_not_installed to NOT_INSTALLED
            reason == Reason.UNAUTHORIZED -> R.string.error_script_module_plugin_not_authorized to UNAUTHORIZED
            reason == Reason.PLUGIN_DISABLED -> R.string.error_script_module_plugin_disabled to PLUGIN_DISABLED
            reason == Reason.LIBRARY_DISABLED -> R.string.error_script_module_plugin_library_disabled to LIBRARY_DISABLED
            else -> R.string.error_script_module_plugin_incompatible to INCOMPATIBLE
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
    internal const val PACKAGED_LIBRARY_DISABLED = "Cannot find module '%1\$s': this app was packaged without the library \"%3\$s\", and the library is switched off in the plugin \"%2\$s\" on this device. Package the app again with AutoJs6, or switch the library on in the plugin."
    internal const val PACKAGED_NOT_INCLUDED = "Cannot find module '%1\$s': this app was packaged without the library \"%3\$s\". Package the app again with AutoJs6 (select \"%2\$s\" in \"Libraries\" if the module is loaded dynamically), or install the plugin \"%2\$s\" on this device."
    internal const val INCOMPATIBLE = "Cannot find module '%1\$s': the installed plugin \"%2\$s\" cannot be read by this version of AutoJs6. Update AutoJs6 or the plugin."

}
