package org.autojs.autojs.core.plugin.epub

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.epub.api.EpubActions
import org.autojs.plugin.epub.api.EpubCapabilityKeys
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.autojs.plugin.epub.api.EpubIds
import org.autojs.plugin.epub.api.IEpubPlugin
import java.io.Closeable

/**
 * Host wrapper for the EPUB Binder contract (plugin roadmap P5.1, decisions D10 and D29): the
 * plugin is enabled by default, any package answering the `org.autojs.plugin.EPUB` query with the
 * `epub` category and engine may be selected, and the plugin must declare a contract version the
 * host understands plus a `requiresHostVersion` not below the build that ships this module.
 *
 * The official plugin exports this service next to its Explorer Action service; the plugin
 * center shows the package once, under the identity of its common info service, and the enable
 * and authorization records of that package apply to both services.
 *
 * Contract version 2 (plugin roadmap P9.4): the plugin keeps `epubContractVersion` at its
 * baseline and advertises the newest version it implements through `epubMaxContractVersion`;
 * [peerOf] negotiates the version every request of a book or session carries, and the clients
 * refuse the appended calls when the negotiated version does not include them.
 */
object EpubPluginHost {

    const val ACTION_EPUB = EpubActions.SERVICE_ACTION

    private const val LABEL = "EPUB plugin"

    private val defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(EpubIds.ENGINE)

    private val signedHost = createHost(ACTION_EPUB, EpubActions.PLUGIN_PERMISSION)
    private val packagedHost = createHost(EpubActions.PACKAGED_SERVICE_ACTION, EpubActions.PACKAGED_PERMISSION)

    internal fun isPackaged(context: Context): Boolean =
        context.packageName != "org.autojs.autojs6" || context.checkSelfPermission(EpubActions.PLUGIN_PERMISSION) != PackageManager.PERMISSION_GRANTED

    private fun hostFor(context: Context) = if (isPackaged(context)) packagedHost else signedHost

    private fun createHost(action: String, permission: String) = AidlPluginHost(
        action = action,
        category = EpubActions.SERVICE_CATEGORY,
        tag = "EpubPluginHost",
        moduleLabel = LABEL,
        proxyFactory = { binder -> IEpubPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        defaultEnabled = defaultEnabled,
        preBindValidator = { context, service -> validateService(context, service, action, permission) },
    )

    suspend fun discover(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        hostFor(context).discover(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        hostFor(context).probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = hostFor(context).queryServiceCount(context)

    /** The capabilities bundle of the selected plugin; failures carry the contract codes like every other call. */
    suspend fun getCapabilities(context: Context): Bundle = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        try {
            hostFor(context).call(context, target) { proxy -> proxy.getCapabilities() ?: Bundle() }
        } catch (t: Throwable) {
            throw EpubPluginException(EpubErrorMapper.fromThrowable(t, EpubErrorMapper.Phase.OPEN), t)
        }
    }

    /**
     * Opens the book at [path] for extraction (plugin roadmap P5.4, D5): the path is validated
     * and opened read-only here, the descriptor travels with the call and is closed right after,
     * and the returned client owns the dedicated binding lease of the plugin service. A book opened
     * after the caller was cancelled (the script stopped) is closed again (Q59).
     */
    suspend fun openBook(context: Context, path: String, displayName: String? = null): EpubBookClient = closingLate {
        EpubSource.open(path).use { source ->
            val target = selectForCall(context)
            val peer = peerOf(target)
            val leased = lease(context, target) { proxy ->
                proxy.openBook(source.descriptor, EpubBundles.openOptions(peer.contractVersion, displayName ?: source.file.name))
                    ?: throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.INTERNAL, "openBook returned no book"))
            }
            EpubBookClient(leased, peer)
        }
    }

    /**
     * Opens a reader session for the book at [path] and starts the plugin's reader Activity with
     * the session token (two-step launch, D12): events reach [sink] through the generation and
     * sequence checks of [EpubReaderBridge]; the returned client owns the lease and the bridge.
     * When the Activity cannot be started the session is closed again with `finish`. A session opened
     * after the caller was cancelled ends and leaves the reader open, as at script exit (Q59).
     */
    suspend fun openReader(context: Context, path: String, options: EpubReaderOptions = EpubReaderOptions(), sink: EpubReaderSink): EpubReaderClient = closingLate {
        EpubSource.open(path).use { source ->
            val target = selectForCall(context)
            val peer = peerOf(target)
            val bridge = EpubReaderBridge(sink)
            val callback = EpubReaderCallbackBinder(bridge)
            val leased = lease(context, target) { proxy ->
                val bundle = EpubBundles.readerOptions(
                    version = peer.contractVersion,
                    displayName = options.displayName ?: source.file.name,
                    locatorJson = options.locatorJson,
                    href = options.href,
                    progression = options.progression,
                    preferencesJson = options.preferencesJson,
                )
                proxy.openReader(source.descriptor, bundle, callback)
                    ?: throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.INTERNAL, "openReader returned no session"))
            }
            val token = try {
                EpubReaderClient.decodeState(EpubBundles.answer(leased.value.state, "getState")).token?.takeIf { it.isNotBlank() }
                    ?: throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.INTERNAL, "the reader session carries no token"))
            } catch (t: Throwable) {
                runCatching { leased.value.close(EpubBundles.closeOptions(peer.contractVersion, finish = true)) }
                runCatching { leased.bindingLease.close() }
                throw if (t is EpubPluginException) t else EpubPluginException(EpubErrorMapper.fromThrowable(t, EpubErrorMapper.Phase.CALL), t)
            }
            val client = EpubReaderClient(leased, bridge, token, peer)
            try {
                EpubReaderLauncher.launch(context, target.serviceInfo.packageName, token)
            } catch (t: Throwable) {
                client.close(finish = true)
                throw t
            }
            client
        }
    }

    /** Runs an opener on IO; a client it returns after the caller was cancelled is closed (Q59). */
    private suspend fun <C : Closeable> closingLate(open: suspend CoroutineScope.() -> C): C =
        AidlPluginHost.withContextAbandoningLate(Dispatchers.IO, { client -> client.close() }, open)

    private suspend fun selectForCall(context: Context): AidlPluginHost.Discovered = try {
        selectOrThrow(context)
    } catch (e: EpubPluginException) {
        throw e
    } catch (t: Throwable) {
        throw EpubPluginException(discoveryError(context, t), t)
    }

    /** `PLUGIN_DISABLED` when a package answers the query but the user disabled every one of them, else the mapped failure. */
    private suspend fun discoveryError(context: Context, cause: Throwable): EpubError {
        val mapped = EpubErrorMapper.fromThrowable(cause, EpubErrorMapper.Phase.DISCOVERY)
        if (mapped.code != EpubErrorCodes.PLUGIN_UNAVAILABLE) return mapped
        val packages = runCatching { hostFor(context).discover(context).map { it.serviceInfo.packageName }.distinct() }.getOrDefault(emptyList())
        if (packages.isEmpty() || packages.any { PluginEnableStore.isEnabled(context, it, defaultEnabled) }) return mapped
        return EpubErrorMapper.normalize(EpubErrorCodes.PLUGIN_DISABLED, "EPUB plugin is disabled: " + packages.joinToString())
    }

    private suspend fun <T> lease(context: Context, target: AidlPluginHost.Discovered, block: (IEpubPlugin) -> T): AidlPluginHost.LeasedCall<T> = try {
        hostFor(context).callWithDedicatedBindingLease(context.applicationContext, target, retryOnRemoteException = false, block = block)
    } catch (e: EpubPluginException) {
        throw e
    } catch (t: Throwable) {
        throw EpubPluginException(EpubErrorMapper.fromThrowable(t, EpubErrorMapper.Phase.OPEN), t)
    }

    internal suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered {
        return hostFor(context).selectOrThrow(
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
        val contractVersion = baseContractVersion(capabilities)
        require(EpubContract.supportsContractVersion(contractVersion)) {
            "Unsupported EPUB contract version: $contractVersion"
        }
        val requiredHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0L
        require(requiredHostVersion >= EpubIds.REQUIRED_HOST_VERSION_CODE) {
            "EPUB plugin required host version is missing or invalid"
        }
    }

    /**
     * The contract version and features agreed with the discovered plugin: its baseline version
     * (validated at discovery) raised to its `epubMaxContractVersion` but never above the host's
     * own maximum. A plugin whose info could not be read is treated as a version-1 peer.
     */
    internal fun peerOf(target: AidlPluginHost.Discovered): EpubPeer {
        val capabilities = target.pluginInfo?.capabilities ?: return EpubPeer.LEGACY
        val base = baseContractVersion(capabilities).takeIf(EpubContract::supportsContractVersion) ?: return EpubPeer.LEGACY
        val max = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(EpubCapabilityKeys.MAX_CONTRACT_VERSION),
        )?.toInt()
        val features = capabilities.getStringArray(EpubCapabilityKeys.FEATURES)?.filterNotNull()?.toSet().orEmpty()
        return EpubPeer(EpubPeer.negotiate(base, max), features)
    }

    private fun baseContractVersion(capabilities: Bundle): Int = PluginCapabilityResolver.coerceLong(
        @Suppress("DEPRECATION") capabilities.get(EpubCapabilityKeys.CONTRACT_VERSION),
    )?.toInt() ?: 0

    private fun validateService(context: Context, serviceInfo: ServiceInfo, action: String, permission: String) {
        require(serviceInfo.exported && serviceInfo.enabled) {
            "EPUB plugin service is not exported and enabled"
        }
        require(serviceInfo.permission == permission) {
            "EPUB plugin service must require $permission"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context, serviceInfo.packageName, action)) {
            "EPUB plugin service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context, packageName: String, action: String): Set<ComponentName> {
        val intent = Intent(action).apply {
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

/** Start position and preferences of `openReader` (all optional; the plugin opens at the saved position otherwise). */
data class EpubReaderOptions(
    val displayName: String? = null,
    val locatorJson: String? = null,
    val href: String? = null,
    val progression: Double? = null,
    val preferencesJson: String? = null,
)
