package org.autojs.autojs.core.plugin.tool

import android.content.Context
import android.os.Bundle
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.PluginDiscoveryRecovery
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.tool.api.IPluginToolProvider
import org.autojs.plugin.tool.api.PluginToolCatalogRequestKeys
import org.autojs.plugin.tool.api.PluginToolContract

/**
 * Binds the providers of the plugin tool contract (docs/dev/plugin-tool-protocol-v1.md) through [AidlPluginHost].
 * zh-CN: 经 [AidlPluginHost] 绑定插件工具契约的提供者.
 */
object PluginToolProviderHost {

    private val host = AidlPluginHost(
        action = PluginToolContract.SERVICE_ACTION,
        tag = "PluginToolProviderHost",
        moduleLabel = "plugin tool provider",
        proxyFactory = { binder -> IPluginToolProvider.Stub.asInterface(binder) },
        infoReader = { proxy -> proxy.getInfo() },
    )

    private val recovery = PluginDiscoveryRecovery()

    /**
     * Discovers every provider; services that fail to bind are woken and discovered again when the Plugin Center enables
     * and authorizes them (ColorOS refuses plugins that never ran).
     * zh-CN: 发现所有提供者; 绑定失败的服务若在插件中心已启用且已授权, 会被唤醒并重新发现.
     */
    suspend fun discoverWakingFailures(context: Context): List<AidlPluginHost.Discovered> =
        host.discoverWakingFailures(context, recovery) { packageName ->
            PluginEnableStore.isEnabled(context, packageName, true) && PluginTrustManager.isAuthorized(context, packageName)
        }

    /** The catalog answer (PluginToolCatalogKeys) of [target]; authorization and host version are enforced by the host. */
    suspend fun getCatalog(context: Context, target: AidlPluginHost.Discovered): Bundle = host.call(context, target) { proxy ->
        val request = Bundle().apply {
            putInt(PluginToolCatalogRequestKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION)
            putLong(PluginToolCatalogRequestKeys.HOST_VERSION_CODE, BuildConfig.VERSION_CODE.toLong())
        }
        requireNotNull(proxy.getCatalog(request)) { "getCatalog returned null" }
    }
}
