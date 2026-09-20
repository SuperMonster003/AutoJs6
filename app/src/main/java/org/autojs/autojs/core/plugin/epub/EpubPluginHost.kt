package org.autojs.autojs.core.plugin.epub

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
import org.autojs.plugin.epub.api.EpubActions
import org.autojs.plugin.epub.api.EpubCapabilityKeys
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubIds
import org.autojs.plugin.epub.api.IEpubPlugin

/**
 * Host wrapper for the EPUB Binder contract (plugin roadmap P5.1, decisions D10 and D29): the
 * plugin is enabled by default, any package answering the `org.autojs.plugin.EPUB` query with the
 * `epub` category and engine may be selected, and the plugin must declare a contract version the
 * host understands plus a `requiresHostVersion` not below the build that ships this module.
 *
 * The official plugin exports this service next to its Explorer Action service; the plugin
 * center shows the package once, under the identity of its common info service, and the enable
 * and authorization records of that package apply to both services.
 */
object EpubPluginHost {

    const val ACTION_EPUB = EpubActions.SERVICE_ACTION

    private const val LABEL = "EPUB plugin"

    private val host = AidlPluginHost(
        action = ACTION_EPUB,
        category = EpubActions.SERVICE_CATEGORY,
        tag = "EpubPluginHost",
        moduleLabel = LABEL,
        proxyFactory = { binder -> IEpubPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(EpubIds.ENGINE),
        preBindValidator = ::validateService,
    )

    suspend fun discover(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        host.discover(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        host.probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    suspend fun getCapabilities(context: Context): Bundle = withContext(Dispatchers.IO) {
        val target = selectOrThrow(context)
        host.call(context, target) { proxy -> proxy.getCapabilities() ?: Bundle() }
    }

    internal suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered {
        return host.selectOrThrow(
            context = context,
            engineId = null,
            engine = EpubIds.ENGINE,
            variant = EpubIds.VARIANT_DEFAULT,
            label = LABEL,
        )
    }

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.engine == EpubIds.ENGINE) { "Unexpected EPUB plugin engine: ${it.engine}" }
        require(it.variant == EpubIds.VARIANT_DEFAULT) { "Unexpected EPUB plugin variant: ${it.variant}" }
        val capabilities = requireNotNull(it.capabilities) { "EPUB plugin capabilities are missing" }
        val contractVersion = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(EpubCapabilityKeys.CONTRACT_VERSION),
        )?.toInt() ?: 0
        require(EpubContract.supportsContractVersion(contractVersion)) {
            "Unsupported EPUB contract version: $contractVersion"
        }
        val requiredHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0L
        require(requiredHostVersion >= EpubIds.REQUIRED_HOST_VERSION_CODE) {
            "EPUB plugin required host version is missing or invalid"
        }
    }

    private fun validateService(context: Context, serviceInfo: ServiceInfo) {
        require(serviceInfo.exported && serviceInfo.enabled) {
            "EPUB plugin service is not exported and enabled"
        }
        require(serviceInfo.permission == EpubActions.PLUGIN_PERMISSION) {
            "EPUB plugin service must require ${EpubActions.PLUGIN_PERMISSION}"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context, serviceInfo.packageName)) {
            "EPUB plugin service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context, packageName: String): Set<ComponentName> {
        val intent = Intent(EpubActions.SERVICE_ACTION).apply {
            addCategory(EpubActions.SERVICE_CATEGORY)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setPackage(packageName)
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
