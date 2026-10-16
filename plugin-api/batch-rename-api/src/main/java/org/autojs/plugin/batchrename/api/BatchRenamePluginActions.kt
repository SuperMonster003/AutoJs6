package org.autojs.plugin.batchrename.api

import org.autojs.plugin.common.api.PluginActions

/**
 * Discovery contract of the Batch Rename plugin. The literals are frozen: the plugin Manifest, the host `<queries>`
 * block and the Plugin Center registration all repeat them.
 *
 * zh-CN: Batch Rename 插件的发现契约. 这些字面值已冻结: 插件 Manifest, 宿主 `<queries>` 与插件中心注册都重复使用它们.
 */
object BatchRenamePluginActions {

    /** Generic plugin-discovery action answered by the plugin's `IPluginInfoProvider` service. */
    const val INFO = PluginActions.INFO

    /** Action of the [IBatchRenamePlugin] service; always paired with [SERVICE_CATEGORY]. */
    const val BATCH_RENAME = "org.autojs.plugin.BATCH_RENAME"

    /** Intent category of the INFO and [BATCH_RENAME] services, so unrelated plugin services never match. */
    const val SERVICE_CATEGORY = "batch-rename"

    /** Signature permission every exported entry point of the plugin requires. */
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

    /**
     * Activity action of the rename history page of the host (plugin roadmap D38 / D51), opened from the plugin's
     * "Rename history" menu item. The intent names the host package; the page requires [PLUGIN_PERMISSION]. Hosts
     * without the page do not resolve it.
     * zh-CN: 宿主重命名历史页面的 Activity 动作 (插件路线图 D38 / D51), 由插件的 "重命名历史" 菜单项打开. Intent 需指定宿主包名; 该页面要求
     * [PLUGIN_PERMISSION]. 没有该页面的宿主无法解析此动作.
     */
    const val RENAME_HISTORY = "org.autojs.autojs6.action.RENAME_HISTORY"
}
