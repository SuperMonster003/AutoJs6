package org.autojs.autojs.core.plugin.mcp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityBroker
import org.autojs.plugin.mcp.server.api.IMcpServerCallback
import org.autojs.plugin.mcp.server.api.IMcpServerPlugin
import org.autojs.plugin.mcp.server.api.IMcpServerSession
import org.autojs.plugin.mcp.server.api.McpServerActions
import org.autojs.plugin.mcp.server.api.McpServerCapabilityKeys
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.api.McpServerIds

/**
 * Strict host wrapper for the official MCP Server Binder contract (roadmap P1.2, decision D18):
 * the plugin is disabled by default, must carry the official package name, and must declare a
 * contract version the host understands before any server is opened.
 */
object McpServerPluginHost {

    const val ACTION_MCP_SERVER = McpServerActions.SERVICE_ACTION

    private const val LABEL = "MCP Server"

    private val host = AidlPluginHost(
        action = ACTION_MCP_SERVER,
        category = McpServerActions.SERVICE_CATEGORY,
        tag = "McpServerHost",
        moduleLabel = LABEL,
        proxyFactory = { binder -> IMcpServerPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(McpServerIds.ENGINE),
        preBindValidator = ::validateService,
        // The plugin owns the pairing confirmation a computer requests. Since Android 14 a bound service may start
        // it only with this opt-in, and only while AutoJs6 is visible; otherwise the plugin's notification remains.
        // zh-CN: 插件负责电脑发起的配对确认界面. Android 14 起被绑定的服务只有在此选项下, 且仅在 AutoJs6 可见时才能启动该界面; 否则仍由插件通知承担.
        allowActivityStarts = true,
    )

    suspend fun discover(context: Context) = withContext(Dispatchers.IO) {
        host.discover(context, McpServerIds.DEFAULT_PACKAGE_NAME)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        require(packageName == McpServerIds.DEFAULT_PACKAGE_NAME) {
            "Unsupported MCP Server package: $packageName"
        }
        host.probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = queryContractServices(context).size

    suspend fun getCapabilities(context: Context): Bundle = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        host.call(context, target) { proxy -> proxy.getCapabilities() ?: Bundle() }
    }

    /**
     * Opens a server on a dedicated binding lease. The caller owns the returned lease and must
     * close it after `IMcpServerSession.close()`; [McpServerSessionController] does both and
     * adds death handling and bounded rebinding.
     */
    suspend fun openServer(
        context: Context,
        config: McpServerConfig,
        broker: IMcpHostCapabilityBroker,
        callback: IMcpServerCallback,
    ): AidlPluginHost.LeasedCall<IMcpServerSession> = withContext(Dispatchers.IO) {
        config.validate()?.let { error -> throw IllegalArgumentException("${error.code}: ${error.message}") }
        val target = selectOrThrow(context)
        if (config.useSavedSettings) {
            val settingsVersion = host.call(context, target) { proxy ->
                proxy.getCapabilities()?.getInt(McpServerCapabilityKeys.SETTINGS_VERSION, 0) ?: 0
            }
            require(settingsVersion >= McpServerContract.SETTINGS_VERSION) { "MCP Server settings extension is unavailable" }
        }
        host.callWithDedicatedBindingLease(context, target, retryOnRemoteException = false) { proxy ->
            openServerWithContract(proxy, config, broker, callback)
        }
    }

    /** Negotiates from validated metadata; no exception-based probing of a v1 Binder. */
    internal fun openServerWithContract(
        proxy: IMcpServerPlugin,
        config: McpServerConfig,
        broker: IMcpHostCapabilityBroker,
        callback: IMcpServerCallback,
    ): IMcpServerSession {
        val capabilities = validatePluginInfo(proxy.getInfo()).capabilities
        val version = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities?.get(McpServerCapabilityKeys.CONTRACT_VERSION),
        )!!.toInt()
        val session = if (version >= 2) {
            require(broker is McpHostCapabilityBroker) { "MCP v2 requires a host-owned shared capability broker" }
            proxy.openServerV2(McpServerBundles.configToBundle(config, 2), broker.sharedBroker, callback)
        } else {
            proxy.openServer(McpServerBundles.configToBundle(config, 1), broker, callback)
        }
        return checkNotNull(session) { "MCP Server returned no session" }
    }

    private suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered {
        return host.selectOrThrow(
            context = context,
            engineId = McpServerIds.PLUGIN_ID,
            engine = McpServerIds.ENGINE,
            variant = McpServerIds.VARIANT_DEFAULT,
            label = LABEL,
        )
    }

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.id == McpServerIds.PLUGIN_ID) { "Unexpected MCP Server plugin id: ${it.id}" }
        require(it.engine == McpServerIds.ENGINE) { "Unexpected MCP Server engine: ${it.engine}" }
        require(it.variant == McpServerIds.VARIANT_DEFAULT) { "Unexpected MCP Server variant: ${it.variant}" }
        val capabilities = requireNotNull(it.capabilities) { "MCP Server capabilities are missing" }
        val contractVersion = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(McpServerCapabilityKeys.CONTRACT_VERSION),
        )?.toInt() ?: 0
        require(McpServerContract.supportsContractVersion(contractVersion)) {
            "Unsupported MCP Server contract version: $contractVersion"
        }
        val requiredHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0L
        require(requiredHostVersion >= McpServerIds.REQUIRED_HOST_VERSION_CODE) {
            "MCP Server required host version is missing or invalid"
        }
    }

    private fun validateService(context: Context, serviceInfo: ServiceInfo) {
        require(serviceInfo.packageName == McpServerIds.DEFAULT_PACKAGE_NAME) {
            "Unsupported MCP Server package: ${serviceInfo.packageName}"
        }
        require(serviceInfo.exported && serviceInfo.enabled) {
            "MCP Server service is not exported and enabled"
        }
        require(serviceInfo.permission == McpServerActions.PLUGIN_PERMISSION) {
            "MCP Server service must require ${McpServerActions.PLUGIN_PERMISSION}"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context)) {
            "MCP Server service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context): Set<ComponentName> {
        val intent = Intent(McpServerActions.SERVICE_ACTION).apply {
            addCategory(McpServerActions.SERVICE_CATEGORY)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(McpServerIds.DEFAULT_PACKAGE_NAME)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { item ->
            item.serviceInfo?.let { service -> ComponentName(service.packageName, service.name) }
        }.toSet()
    }
}
