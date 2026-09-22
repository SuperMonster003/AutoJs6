package org.autojs.autojs.core.plugin.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginInfoServiceHost
import org.autojs.plugin.ai.agent.api.AiAgentActions
import org.autojs.plugin.ai.agent.api.AiAgentCapabilityKeys
import org.autojs.plugin.ai.agent.api.AiAgentContract
import org.autojs.plugin.ai.agent.api.AiAgentIds
import org.autojs.plugin.ai.agent.api.IAiAgentLink
import org.autojs.plugin.ai.agent.api.IAiAgentLinkCallback
import org.autojs.plugin.ai.agent.api.IAiAgentPlugin
import org.autojs.plugin.common.api.PluginInfo

/** The dedicated lease pins the inspected component, version and signer until detach. */
object AiAgentPluginHost {
    private val host = AidlPluginHost(
        action = AiAgentActions.SERVICE_ACTION,
        category = AiAgentActions.SERVICE_CATEGORY,
        tag = "AiAgentHost",
        moduleLabel = "AI Agent",
        proxyFactory = IAiAgentPlugin.Stub::asInterface,
        infoReader = { validatePluginInfo(it.getInfo()) },
        defaultEnabled = false,
        preBindValidator = ::validateService,
    )

    suspend fun discover(context: Context) = withContext(Dispatchers.IO) {
        host.discover(context, AiAgentIds.DEFAULT_PACKAGE_NAME)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        require(packageName == AiAgentIds.DEFAULT_PACKAGE_NAME) { "Unsupported AI Agent package" }
        host.probe(context, packageName)
    }

    /** Plugin Center enables the installed package via INFO; attach still validates the runtime. */
    suspend fun probeMetadata(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        require(packageName == AiAgentIds.DEFAULT_PACKAGE_NAME) { "Unsupported AI Agent package" }
        PluginInfoServiceHost.probe(context, packageName).also { info ->
            require(info.id == AiAgentIds.PLUGIN_ID && info.engine == AiAgentIds.ENGINE && info.variant == AiAgentIds.VARIANT_DEFAULT) {
                "Unexpected AI Agent identity"
            }
            val required = PluginCapabilityResolver.resolveRequiresHostVersion(info.capabilities)
            require(required != null && required >= AiAgentIds.REQUIRED_HOST_VERSION_CODE && required <= org.autojs.autojs6.BuildConfig.VERSION_CODE) {
                "Incompatible AI Agent metadata"
            }
        }
    }

    fun queryServiceCount(context: Context) = queryContractServices(context).size

    suspend fun attach(
        context: Context,
        config: Bundle,
        brokers: AiAgentLinkBrokers,
        callback: IAiAgentLinkCallback,
    ): AidlPluginHost.LeasedCall<IAiAgentLink> = withContext(Dispatchers.IO) {
        check(AiAgentPluginInspector.inspect(context).state == AiAgentUiState.AVAILABLE) {
            AiAgentContract.ERROR_PLUGIN_UNAVAILABLE
        }
        val target = host.selectOrThrow(context, AiAgentIds.PLUGIN_ID, AiAgentIds.ENGINE, AiAgentIds.VARIANT_DEFAULT, "AI Agent")
        // Retrying attach after RemoteException could replace a live link and replay a task.
        host.callWithDedicatedBindingLease(context, target, retryOnRemoteException = false) { proxy ->
            validatePluginInfo(proxy.getInfo())
            checkNotNull(proxy.attach(config, brokers.modelBroker, brokers.capabilityBroker, callback)) {
                "AI Agent returned no link"
            }
        }
    }

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.id == AiAgentIds.PLUGIN_ID && it.engine == AiAgentIds.ENGINE && it.variant == AiAgentIds.VARIANT_DEFAULT) {
            "Unexpected AI Agent identity"
        }
        val capabilities = requireNotNull(it.capabilities) { "AI Agent capabilities are missing" }
        val version = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(AiAgentCapabilityKeys.CONTRACT_VERSION),
        ) ?: 0L
        require(version in AiAgentContract.MIN_SUPPORTED_CONTRACT_VERSION.toLong()..AiAgentContract.MAX_SUPPORTED_CONTRACT_VERSION.toLong()) {
            "Unsupported AI Agent contract version"
        }
        require((PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0) >= AiAgentIds.REQUIRED_HOST_VERSION_CODE) {
            "AI Agent required host version is missing or invalid"
        }
    }

    private fun validateService(context: Context, service: ServiceInfo) {
        require(service.packageName == AiAgentIds.DEFAULT_PACKAGE_NAME && service.exported && service.enabled) {
            "Invalid AI Agent service"
        }
        require(service.permission == AiAgentActions.PLUGIN_PERMISSION) { "AI Agent service permission is missing" }
        val components = queryContractServices(context)
        require(components.size == 1 && ComponentName(service.packageName, service.name) in components) {
            "Ambiguous or invalid AI Agent service action/category"
        }
    }

    @Suppress("DEPRECATION")
    private fun queryContractServices(context: Context): Set<ComponentName> = context.packageManager.queryIntentServices(
        Intent(AiAgentActions.SERVICE_ACTION).addCategory(AiAgentActions.SERVICE_CATEGORY)
            .setPackage(AiAgentIds.DEFAULT_PACKAGE_NAME).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
        PackageManager.GET_META_DATA,
    ).mapNotNull { it.serviceInfo?.let { service -> ComponentName(service.packageName, service.name) } }.toSet()
}
