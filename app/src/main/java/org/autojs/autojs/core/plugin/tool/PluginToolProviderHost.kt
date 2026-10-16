package org.autojs.autojs.core.plugin.tool

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.PluginDiscoveryRecovery
import org.autojs.autojs.core.plugin.ServiceBindingLease
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.tool.api.IPluginToolCallback
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

    /**
     * The providers of [packageName] (all when null), for the Plugin Center listing plugins that only describe their tools
     * (MCP roadmap D60).
     * zh-CN: [packageName] 的提供者 (为 null 时为全部), 供插件中心列出只提供工具服务的插件.
     */
    suspend fun discover(context: Context, packageName: String? = null): List<AidlPluginHost.Discovered> = host.discover(context, packageName)

    /** The identity of the provider of [packageName], for the Plugin Center switch. zh-CN: [packageName] 的提供者身份, 供插件中心开关使用. */
    suspend fun probe(context: Context, packageName: String): PluginInfo = host.probe(context, packageName)

    /** One accepted or refused call: the provider's answer, the proxy for `cancel` and death notices, and the binding to release when the call ends. */
    class Started(val answer: Bundle, val provider: IPluginToolProvider, val lease: ServiceBindingLease)

    /**
     * Sends one call to [target] on a dedicated binding, so the pooled connection's idle unbind cannot drop the provider
     * while the call runs; the caller closes [Started.lease] when the call ends.
     * zh-CN: 在专用绑定上向 [target] 发送一次调用, 以免连接池的空闲解绑在调用期间断开提供者; 调用结束时由调用方关闭租约.
     */
    suspend fun invoke(
        context: Context,
        target: AidlPluginHost.Discovered,
        request: Bundle,
        inputs: Array<ParcelFileDescriptor>,
        callback: IPluginToolCallback,
    ): Started {
        val leased = host.callWithDedicatedBindingLease(context, target, retryOnRemoteException = false) { proxy ->
            proxy to requireNotNull(proxy.invoke(request, inputs, callback)) { "invoke returned null" }
        }
        return Started(leased.value.second, leased.value.first, leased.bindingLease)
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
