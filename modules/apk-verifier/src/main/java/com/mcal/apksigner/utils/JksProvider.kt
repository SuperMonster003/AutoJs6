package com.mcal.apksigner.utils

import java.security.Provider

/**
 * The [Provider] a [JksKeyStore] reports through `KeyStore.getProvider()`. It registers no services and is never
 * added to [java.security.Security]: the JKS reader is instantiated directly, so the provider is metadata only.
 * The `(String, double, String)` constructor is the one available on every supported API level.
 *
 * zh-CN: [JksKeyStore] 经 `KeyStore.getProvider()` 报告的 [Provider]. 它不注册任何服务, 也不会加入 [java.security.Security]:
 * JKS 读取器是直接实例化的, 因此该 provider 只是元数据. `(String, double, String)` 构造器是所有受支持 API 级别都有的那个.
 */
@Suppress("DEPRECATION")
internal object JksProvider : Provider("AutoJs6JKS", 1.0, "Pure Java JKS keystore reader") {
    private fun readResolve(): Any = JksProvider
}
