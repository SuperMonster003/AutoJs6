package org.autojs.plugin.common.api

object PluginCapabilityKeys {
    const val REQUIRES_HOST_VERSION = "requiresHostVersion"
    const val NATIVE_PAGE_ALIGNMENT = "org.autojs.plugin.contract.NATIVE_PAGE_ALIGNMENT"

    /**
     * Manifest meta-data listing every ABI a plugin is built for, written by the shared
     * native-alignment Gradle plugin from the packaged native libraries; `universal` means any ABI.
     *
     * zh-CN: 清单 meta-data, 列出插件构建支持的全部 ABI, 由共享的 native-alignment Gradle 插件
     * 根据实际打包的原生库写入; `universal` 表示任意 ABI.
     */
    const val SUPPORTED_ABIS = "org.autojs.plugin.contract.SUPPORTED_ABIS"
}
