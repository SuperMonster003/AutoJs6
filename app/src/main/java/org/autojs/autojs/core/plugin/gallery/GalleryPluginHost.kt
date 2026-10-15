package org.autojs.autojs.core.plugin.gallery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.gallery.api.GalleryActions
import org.autojs.plugin.gallery.api.GalleryCapabilityKeys
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.autojs.plugin.gallery.api.GalleryIds
import org.autojs.plugin.gallery.api.IGalleryCallback
import org.autojs.plugin.gallery.api.IGalleryPlugin
import org.autojs.plugin.gallery.api.IGallerySession
import java.io.Closeable
import java.io.File

/**
 * Host client of the gallery contract (3-Maple Image roadmap M6, `docs/dev/gallery-plugin-protocol-v1.md`):
 * any package answering the `org.autojs.plugin.GALLERY` query with the `gallery` category and engine
 * may be selected, and the plugin must declare a contract version the host understands plus a
 * `requiresHostVersion` not below the build that ships this module.
 *
 * Interactive calls open a session and start the plugin's session Activity with its token (two-step
 * launch); the caller follows the session through a [GallerySessionSink]. File access stays here:
 * sources are opened read-only from the script's paths, outputs are staged, read back and published
 * by [GalleryOutput], so the plugin never sees a path.
 */
object GalleryPluginHost {

    const val ACTION_GALLERY = GalleryActions.SERVICE_ACTION

    private const val LABEL = "Gallery plugin"

    private val defaultEnabled = PluginDefaultEnabledPolicy.isEnabledByDefault(GalleryIds.ENGINE)

    private val host = AidlPluginHost(
        action = ACTION_GALLERY,
        category = GalleryActions.SERVICE_CATEGORY,
        tag = "GalleryPluginHost",
        moduleLabel = LABEL,
        proxyFactory = { binder -> IGalleryPlugin.Stub.asInterface(binder) },
        infoReader = { proxy -> validatePluginInfo(proxy.getInfo()) },
        defaultEnabled = defaultEnabled,
        preBindValidator = ::validateService,
    )

    suspend fun discover(context: Context, packageName: String? = null) = withContext(Dispatchers.IO) {
        host.discover(context, packageName)
    }

    suspend fun probe(context: Context, packageName: String): PluginInfo = withContext(Dispatchers.IO) {
        host.probe(context, packageName)
    }

    fun queryServiceCount(context: Context): Int = host.queryServiceCount(context)

    /** Whether a usable gallery plugin is installed, authorized and enabled. */
    suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { selectOrThrow(context) }.isSuccess
    }

    /** The capabilities bundle of the selected plugin; failures carry the contract codes like every other call. */
    suspend fun getCapabilities(context: Context): Bundle = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        try {
            withTimeout(GalleryContract.CALL_TIMEOUT_MS) { host.call(context, target) { proxy -> proxy.capabilities ?: Bundle() } }
        } catch (t: Throwable) {
            throw GalleryPluginException(GalleryErrorMapper.fromThrowable(t, GalleryErrorMapper.Phase.OPEN), t)
        }
    }

    /** Opens the plugin's picker; the `result` event's items go through [GalleryPickPolicy.accept]. */
    suspend fun openPicker(context: Context, options: GalleryPickerOptions, sink: GallerySessionSink): GallerySessionClient = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        val peer = peerOf(target)
        requireFeature(peer, GalleryContract.FEATURE_PICKER)
        openSession(context, target, peer, sink) { proxy, callback ->
            proxy.openPicker(GalleryBundles.pickerOptions(peer.contractVersion, options), callback)
        }
    }

    /** Opens the plugin's viewer over the images at [paths] (1 to `MAX_VIEW_PAGES`), starting at [startIndex]. */
    suspend fun openViewer(context: Context, paths: List<String>, startIndex: Int, sink: GallerySessionSink): GallerySessionClient = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) throw GalleryErrorMapper.failure(GalleryErrorCodes.INVALID_ARGUMENT, "at least one image is required")
        if (paths.size > GalleryContract.MAX_VIEW_PAGES) {
            throw GalleryErrorMapper.failure(GalleryErrorCodes.LIMIT_EXCEEDED, "the viewer opens at most ${GalleryContract.MAX_VIEW_PAGES} images, not ${paths.size}")
        }
        if (startIndex !in paths.indices) throw GalleryErrorMapper.failure(GalleryErrorCodes.INVALID_ARGUMENT, "start index $startIndex is outside 0..${paths.lastIndex}")
        val target = selectForCall(context)
        val peer = peerOf(target)
        requireFeature(peer, GalleryContract.FEATURE_VIEWER)
        GallerySources.open(paths).use { sources ->
            openSession(context, target, peer, sink) { proxy, callback ->
                val names = sources.files.map { it.name }
                proxy.openViewer(sources.descriptors.toTypedArray(), GalleryBundles.viewerOptions(peer.contractVersion, names, startIndex), callback)
            }
        }
    }

    /**
     * Opens the plugin's editor for the image at [path]; the output is staged by [GalleryOutput] and
     * published with [GalleryEditorClient.publish] once the session's `result` event arrived.
     */
    suspend fun openEditor(
        context: Context,
        path: String,
        output: GalleryOutputRequest,
        encode: GalleryEncodeOptions,
        sink: GallerySessionSink,
    ): GalleryEditorClient = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        val peer = peerOf(target)
        requireFeature(peer, GalleryContract.FEATURE_EDITOR)
        GallerySources.open(listOf(path)).use { sources ->
            val staged = GalleryOutput.stage(sources.files.single(), output, GalleryOutput.SUFFIX_EDITED)
            try {
                val session = staged.openStaging().use { staging ->
                    openSession(context, target, peer, sink) { proxy, callback ->
                        proxy.openEditor(sources.single, staging, GalleryBundles.editorOptions(peer.contractVersion, sources.files.single().name, encode), callback)
                    }
                }
                GalleryEditorClient(context.applicationContext, session, staged)
            } catch (t: Throwable) {
                staged.discard()
                throw t
            }
        }
    }

    /** Converts the image at [path] without UI and publishes the output; answers the written image. */
    suspend fun convert(context: Context, path: String, output: GalleryOutputRequest, options: GalleryConvertOptions): GalleryWrittenImage = withContext(Dispatchers.IO) {
        val target = selectForCall(context)
        val peer = peerOf(target)
        requireFeature(peer, GalleryContract.FEATURE_CONVERT)
        GallerySources.open(listOf(path)).use { sources ->
            val staged = GalleryOutput.stage(sources.files.single(), output, GalleryOutput.SUFFIX_CONVERTED)
            try {
                val answer = staged.openStaging().use { staging ->
                    val request = GalleryBundles.convertOptions(peer.contractVersion, sources.files.single().name, options)
                    withTimeout(GalleryContract.CONVERT_TIMEOUT_MS) {
                        host.call(context, target, retryOnRemoteException = false) { proxy -> proxy.convert(sources.single, staging, request) }
                    }
                }
                val result = GalleryBundles.answer(answer, "convert")
                val written = staged.publish(
                    result.getString(GalleryContract.KEY_MIME_TYPE),
                    result.takeIf { it.containsKey(GalleryContract.KEY_WIDTH) }?.getInt(GalleryContract.KEY_WIDTH),
                    result.takeIf { it.containsKey(GalleryContract.KEY_HEIGHT) }?.getInt(GalleryContract.KEY_HEIGHT),
                )
                announce(context, written)
                written
            } catch (t: Throwable) {
                staged.discard()
                throw if (t is GalleryPluginException) t else GalleryPluginException(GalleryErrorMapper.fromThrowable(t, GalleryErrorMapper.Phase.CALL), t)
            }
        }
    }

    /** Lets the media library list a published output right away. */
    internal fun announce(context: Context, written: GalleryWrittenImage) {
        runCatching { MediaScannerConnection.scanFile(context.applicationContext, arrayOf(written.path), arrayOf(written.mimeType), null) }
    }

    private suspend fun openSession(
        context: Context,
        target: AidlPluginHost.Discovered,
        peer: GalleryPeer,
        sink: GallerySessionSink,
        open: (IGalleryPlugin, IGalleryCallback) -> IGallerySession?,
    ): GallerySessionClient {
        val bridge = GallerySessionBridge(sink)
        val callback = GalleryCallbackBinder(bridge)
        val leased = try {
            withTimeout(GalleryContract.CALL_TIMEOUT_MS) {
                host.callWithDedicatedBindingLease(context.applicationContext, target, retryOnRemoteException = false) { proxy ->
                    open(proxy, callback) ?: throw GalleryErrorMapper.failure(GalleryErrorCodes.INTERNAL, "the plugin opened no session")
                }
            }
        } catch (e: GalleryPluginException) {
            throw e
        } catch (t: Throwable) {
            throw GalleryPluginException(GalleryErrorMapper.fromThrowable(t, GalleryErrorMapper.Phase.OPEN), t)
        }
        val token = try {
            GallerySessionClient.decodeState(GalleryBundles.answer(leased.value.state, "getState")).token?.takeIf { it.isNotBlank() }
                ?: throw GalleryErrorMapper.failure(GalleryErrorCodes.INTERNAL, "the session carries no token")
        } catch (t: Throwable) {
            runCatching { leased.value.close(GalleryBundles.closeOptions(peer.contractVersion, finish = true)) }
            runCatching { leased.bindingLease.close() }
            throw if (t is GalleryPluginException) t else GalleryPluginException(GalleryErrorMapper.fromThrowable(t, GalleryErrorMapper.Phase.CALL), t)
        }
        val client = GallerySessionClient(leased, bridge, token, peer)
        try {
            GalleryActivityLauncher.launch(context, target.serviceInfo.packageName, token)
        } catch (t: Throwable) {
            client.close(finish = true)
            throw t
        }
        return client
    }

    private fun requireFeature(peer: GalleryPeer, feature: String) {
        if (!peer.supports(feature)) {
            throw GalleryErrorMapper.failure(GalleryErrorCodes.PLUGIN_INCOMPATIBLE, "the gallery plugin does not offer $feature")
        }
    }

    private suspend fun selectForCall(context: Context): AidlPluginHost.Discovered = try {
        selectOrThrow(context)
    } catch (e: GalleryPluginException) {
        throw e
    } catch (t: Throwable) {
        throw GalleryPluginException(discoveryError(context, t), t)
    }

    /** `PLUGIN_DISABLED` when a package answers the query but the user disabled every one of them, else the mapped failure. */
    private suspend fun discoveryError(context: Context, cause: Throwable): GalleryError {
        val mapped = GalleryErrorMapper.fromThrowable(cause, GalleryErrorMapper.Phase.DISCOVERY)
        if (mapped.code != GalleryErrorCodes.PLUGIN_UNAVAILABLE) return mapped
        val packages = runCatching { host.discover(context).map { it.serviceInfo.packageName }.distinct() }.getOrDefault(emptyList())
        if (packages.isEmpty() || packages.any { PluginEnableStore.isEnabled(context, it, defaultEnabled) }) return mapped
        return GalleryErrorMapper.normalize(GalleryErrorCodes.PLUGIN_DISABLED, "gallery plugin is disabled: " + packages.joinToString())
    }

    internal suspend fun selectOrThrow(context: Context): AidlPluginHost.Discovered {
        return host.selectOrThrow(
            context = context,
            engineId = null,
            engine = GalleryIds.ENGINE,
            variant = GalleryIds.VARIANT_DEFAULT,
            label = LABEL,
        )
    }

    internal fun validatePluginInfo(info: PluginInfo): PluginInfo = info.also {
        require(it.engine == GalleryIds.ENGINE) { "Unexpected gallery plugin engine: ${it.engine}" }
        require(it.variant == GalleryIds.VARIANT_DEFAULT) { "Unexpected gallery plugin variant: ${it.variant}" }
        val capabilities = requireNotNull(it.capabilities) { "gallery plugin capabilities are missing" }
        val contractVersion = baseContractVersion(capabilities)
        require(GalleryContract.supportsContractVersion(contractVersion)) {
            "Unsupported gallery contract version: $contractVersion"
        }
        val requiredHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(capabilities) ?: 0L
        require(requiredHostVersion >= GalleryIds.REQUIRED_HOST_VERSION_CODE) {
            "gallery plugin required host version is missing or invalid"
        }
    }

    /**
     * The contract version and features agreed with the discovered plugin: its baseline version
     * (validated at discovery) raised to its `galleryMaxContractVersion` but never above the host's own
     * maximum. A plugin whose info could not be read is treated as a version-1 peer without features.
     */
    internal fun peerOf(target: AidlPluginHost.Discovered): GalleryPeer {
        val capabilities = target.pluginInfo?.capabilities ?: return GalleryPeer.LEGACY
        val base = baseContractVersion(capabilities).takeIf(GalleryContract::supportsContractVersion) ?: return GalleryPeer.LEGACY
        val max = PluginCapabilityResolver.coerceLong(
            @Suppress("DEPRECATION") capabilities.get(GalleryCapabilityKeys.MAX_CONTRACT_VERSION),
        )?.toInt()
        val features = capabilities.getStringArray(GalleryCapabilityKeys.FEATURES)?.filterNotNull()?.toSet().orEmpty()
        return GalleryPeer(GalleryPeer.negotiate(base, max), features)
    }

    private fun baseContractVersion(capabilities: Bundle): Int = PluginCapabilityResolver.coerceLong(
        @Suppress("DEPRECATION") capabilities.get(GalleryCapabilityKeys.CONTRACT_VERSION),
    )?.toInt() ?: 0

    private fun validateService(context: Context, serviceInfo: ServiceInfo) {
        require(serviceInfo.exported && serviceInfo.enabled) {
            "gallery plugin service is not exported and enabled"
        }
        require(serviceInfo.permission == GalleryActions.PLUGIN_PERMISSION) {
            "gallery plugin service must require ${GalleryActions.PLUGIN_PERMISSION}"
        }
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        require(component in queryContractServices(context, serviceInfo.packageName)) {
            "gallery plugin service does not declare the required action and category"
        }
    }

    private fun queryContractServices(context: Context, packageName: String): Set<ComponentName> {
        val intent = Intent(GalleryActions.SERVICE_ACTION).apply {
            addCategory(GalleryActions.SERVICE_CATEGORY)
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

/**
 * An editor session with its staged output: [publish] reads back and publishes what the plugin
 * reported in its `result` event; closing (the script stopped, the user left, the session failed)
 * finishes the session and deletes the staging file.
 */
class GalleryEditorClient internal constructor(
    private val context: Context,
    val session: GallerySessionClient,
    private val output: GalleryOutput,
) : Closeable {

    fun publish(result: GalleryEvent): GalleryWrittenImage {
        val written = output.publish(result.mimeType, result.width, result.height)
        GalleryPluginHost.announce(context, written)
        return written
    }

    /** The staging file (tests and diagnostics). */
    val stagingFile: File get() = output.staging

    fun discard() = output.discard()

    override fun close() {
        session.close(finish = true)
        output.discard()
    }
}
