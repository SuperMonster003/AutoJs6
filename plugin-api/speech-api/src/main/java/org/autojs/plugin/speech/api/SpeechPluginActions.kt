package org.autojs.plugin.speech.api

import org.autojs.plugin.common.api.PluginActions

/**
 * Discovery contract of the speech plugin family (the script object `speech`, 3-Macaw Speech roadmap D21). The
 * literals are frozen: the plugin Manifest, the host `<queries>` block and the Plugin Center registration all repeat
 * them.
 *
 * zh-CN: 语音插件家族 (脚本对象 `speech`, 3-Macaw Speech 路线图 D21) 的发现契约. 这些字面值已冻结: 插件 Manifest, 宿主 `<queries>` 与
 * 插件中心注册都重复使用它们.
 */
object SpeechPluginActions {

    /** Generic plugin-discovery action answered by the plugin's `IPluginInfoProvider` service. */
    const val INFO = PluginActions.INFO

    /** Action of the [ISpeechPlugin] service; always paired with [SERVICE_CATEGORY]. */
    const val SPEECH = "org.autojs.plugin.SPEECH"

    /** Separate entry for generated applications signed by their authors. The original entry stays signature protected. */
    const val PACKAGED_SPEECH = "org.autojs.plugin.speech.PACKAGED"

    /** Normal permission declared by the speech provider and requested explicitly by generated applications. */
    const val PACKAGED_PERMISSION = "org.autojs.plugin.speech.permission.RUNTIME"

    /** Intent category of the [SPEECH] service, so unrelated `org.autojs.plugin.*` services never match. */
    const val SERVICE_CATEGORY = "speech"

    /** Signature permission of the original host capability entry. */
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
}
