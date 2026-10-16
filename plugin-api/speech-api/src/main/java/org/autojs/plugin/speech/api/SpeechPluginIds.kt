package org.autojs.plugin.speech.api

/**
 * Identity of the official speech plugin (3-Macaw Speech) as reported by its `PluginInfo` (roadmap 5.5). The engine id
 * `speech` is shared by every implementation of this contract; the plugin id names the official one.
 *
 * zh-CN: 官方语音插件 (3-Macaw Speech) 的身份, 与其 `PluginInfo` 返回值一致 (路线图 5.5). 引擎 id `speech` 由本契约的每个实现共用;
 * 插件 id 指向官方实现.
 */
object SpeechPluginIds {

    const val OFFICIAL_PLUGIN_ID = "three-macaw-speech"

    const val OFFICIAL_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.three.macaw.speech"

    const val ENGINE = "speech"

    /** Equals [SpeechPluginActions.SERVICE_CATEGORY]; kept here because roadmap appendix C.1 names it next to the engine. */
    const val CATEGORY = "speech"

    const val VARIANT_DEFAULT = "default"

    /**
     * Smallest host `versionCode` a plugin may declare through `PluginCapabilityKeys.REQUIRES_HOST_VERSION`: the 6.8.0
     * host build that ships the script object `speech` on top of this contract (roadmap D28, P1.3 temporary value;
     * P6.4 raises it to the final 6.8.0 build).
     * zh-CN: 插件经 `PluginCapabilityKeys.REQUIRES_HOST_VERSION` 可声明的最小宿主 `versionCode`: 在本契约之上提供脚本对象 `speech` 的
     * 6.8.0 宿主构建 (路线图 D28, P1.3 临时值; P6.4 回填为最终的 6.8.0 构建号).
     */
    const val REQUIRED_HOST_VERSION_CODE = 5451L
}
