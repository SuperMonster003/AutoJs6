package org.autojs.autojs.core.pref

import java.io.IOException

/**
 * The extension point for the settings of an official plugin in a settings archive (docs/settings/ROADMAP.md D3,
 * P13.1): the plugin [packageName] exports its settings as one JSON document ([exportSettings]), which the archive holds
 * as `plugins/<packageName>/settings.json` and lists in the manifest's `plugins`, and takes such a document back
 * ([importSettings]). The second phase implements it over the plugin's ContentProvider (`call` with `exportSettings` /
 * `importSettings`, docs/dev/plugin-settings-transfer-protocol-v1.md, P13.2); the first phase registers none
 * ([SettingsRegistry.pluginSettingsProviders]), so that an import counts every plugin directory as `TARGET_MISSING`.
 * Both calls may block on another process: callers run them off the main thread.
 *
 * zh-CN: 设置归档中官方插件设置的扩展点 (ROADMAP D3, P13.1): 插件 [packageName] 把自己的设置导出为一个 JSON 文档
 * ([exportSettings]), 归档以 `plugins/<包名>/settings.json` 保存并在 manifest 的 `plugins` 中列出, 导入时再交还给插件
 * ([importSettings]). 第二期经插件的 ContentProvider 实现 (`call` 的 `exportSettings` / `importSettings`,
 * docs/dev/plugin-settings-transfer-protocol-v1.md, P13.2); 第一期不登记任何提供方 ([SettingsRegistry.pluginSettingsProviders]),
 * 因此导入时每个插件目录都计为 `TARGET_MISSING`. 两个调用都可能阻塞于其他进程, 调用方在主线程之外调用.
 */
interface PluginSettingsProvider {

    /** The package name of the plugin. zh-CN: 插件的包名. */
    val packageName: String

    /**
     * The settings of the plugin: the JSON document (P13.2) and the number of entries it holds, shown by the wizards;
     * throws [IOException] when the plugin cannot be reached or refuses.
     * zh-CN: 插件的设置: JSON 文档 (P13.2) 与其中的条目数 (向导显示); 无法连接插件或插件拒绝时抛 [IOException].
     */
    @Throws(IOException::class)
    fun exportSettings(): Exported

    /**
     * Hands [text], a document [exportSettings] gave on this or another device, to the plugin and returns what it did;
     * throws [IOException] when the plugin cannot be reached or refuses the document as a whole.
     * zh-CN: 把 [text] (本机或其他设备上 [exportSettings] 给出的文档) 交给插件并返回其处理结果; 无法连接插件或插件整体拒绝该文档时抛
     * [IOException].
     */
    @Throws(IOException::class)
    fun importSettings(text: String): Imported

    /** An exported document and its number of entries. zh-CN: 导出的文档及其条目数. */
    data class Exported(val text: String, val entries: Int)

    /**
     * What an import did: [written] entries taken, [skipped] entries left as they were (unknown, invalid, of a newer
     * format; D9 for plugins).
     * zh-CN: 导入的结果: [written] 为写入的条目数, [skipped] 为保持原样的条目数 (未知, 无效, 更新格式; 即插件侧的 D9).
     */
    data class Imported(val written: Int, val skipped: Int)
}
