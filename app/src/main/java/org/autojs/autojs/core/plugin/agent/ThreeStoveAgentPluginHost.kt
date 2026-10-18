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
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLink
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLinkCallback
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentPlugin
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentCapabilityKeys
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.plugin.common.api.PluginInfo

/** The dedicated lease pins the inspected component, version and signer until detach. */
object ThreeStoveAgentPluginHost {
    private val host = AidlPluginHost(
        action = ThreeStoveAgentActions.SERVICE_ACTION,
        category = ThreeStoveAgentActions.SERVICE_CATEGORY,
        tag = "ThreeStoveAgentHost",
        moduleLabel = "3-Stove Agent",
        proxyFactory = IThreeStoveAgentPlugin.Stub::asInterface,
        infoReader = { validatePluginInfo(it.getInfo()) },
        // attach() first enforces the signer-aware default and saved enablement via the inspector.
        defaultEnabled = true,
        preBindValidator = ::validateService,
    )

    suspend fun discover(context: Context) = withContext(Dispatchers.IO) {
        host.discover(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        require(packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME) { "Unsupported 3-Stove Agent package" }
        host.probe(context, packageName)
    }

    /** Plugin Center enables the installed package via INFO; attach still validates the runtime. */
    suspend fun probeMetadata(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        require(packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME) { "Unsupported 3-Stove Agent package" }
        PluginInfoServiceHost.probe(context, packageName).also { info ->
            require(info.id == ThreeStoveAgentIds.PLUGIN_ID && info.engine == ThreeStoveAgentIds.ENGINE && info.variant == ThreeStoveAgentIds.VARIANT_DEFAULT) {
                "Unexpected 3-Stove Agent identity"
            }
            val required = PluginCapabilityResolver.resolveRequiresHostVersion(info.capabilities)
            require(required != null && required >= ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE && required <= org.autojs.autojs6.BuildConfig.VERSION_CODE) {
                "Incompatible 3-Stove Agent metadata"
            }
        }
    }

    fun queryServiceCount(context: Context) = queryContractServices(context).size

    suspend fun attach(
        context: Context,
        config: Bundle,
        brokers: ThreeStoveAgentLinkBrokers,
        callback: IThreeStoveAgentLinkCallback,
    ): AidlPluginHost.LeasedCall<IThreeStoveAgentLink> = withContext(Dispatchers.IO) {
        check(ThreeStoveAgentPluginInspector.inspect(context).state == ThreeStoveAgentUiState.AVAILABLE) {
            ThreeStoveAgentContract.ERROR_PLUGIN_UNAVAILABLE
        }
        val target = host.selectOrThrow(context, ThreeStoveAgentIds.PLUGIN_ID, ThreeStoveAgentIds.ENGINE, ThreeStoveAgentIds.VARIANT_DEFAULT, "3-Stove Agent")
        // Retrying attach after RemoteException could replace a live link and replay a task.
        host.callWithDedicatedBindingLease(context, target, retryOnRemoteException = false) { proxy ->
            validatePluginInfo(proxy.getInfo())
            checkNotNull(proxy.attach(config, brokers.modelBroker, brokers.capabilityBroker, callback)) {
                "3-Stove Agent returned no link"
            }
        }
    }

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.id == ThreeStoveAgentIds.PLUGIN_ID && it.engine == ThreeStoveAgentIds.ENGINE && it.variant == ThreeStoveAgentIds.VARIANT_DEFAULT) {
            "Unexpected 3-Stove Agent identity"
        }
        val capabilities = requireNotNull(it.capabilities) { "3-Stove Agent capabilities are missing" }
        val version = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(ThreeStoveAgentCapabilityKeys.CONTRACT_VERSION),
        ) ?: 0L
        require(version in ThreeStoveAgentContract.MIN_SUPPORTED_CONTRACT_VERSION.toLong()..ThreeStoveAgentContract.MAX_SUPPORTED_CONTRACT_VERSION.toLong()) {
            "Unsupported 3-Stove Agent contract version"
        }
        require((PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0) >= ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE) {
            "3-Stove Agent required host version is missing or invalid"
        }
    }

    private fun validateService(context: Context, service: ServiceInfo) {
        require(service.packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME && service.exported && service.enabled) {
            "Invalid 3-Stove Agent service"
        }
        require(service.permission == ThreeStoveAgentActions.PLUGIN_PERMISSION) { "3-Stove Agent service permission is missing" }
        val components = queryContractServices(context)
        require(components.size == 1 && ComponentName(service.packageName, service.name) in components) {
            "Ambiguous or invalid 3-Stove Agent service action/category"
        }
    }

    @Suppress("DEPRECATION")
    private fun queryContractServices(context: Context): Set<ComponentName> = context.packageManager.queryIntentServices(
        Intent(ThreeStoveAgentActions.SERVICE_ACTION).addCategory(ThreeStoveAgentActions.SERVICE_CATEGORY)
            .setPackage(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
        PackageManager.GET_META_DATA,
    ).mapNotNull { it.serviceInfo?.let { service -> ComponentName(service.packageName, service.name) } }.toSet()
}
