package org.autojs.autojs.core.plugin.explorer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginResourceResolver
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveSessionConnection
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenInteractionException
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenRequestOptions
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenRemoteFailure
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveVolumeSource
import org.autojs.autojs.core.plugin.explorer.archive.archiveOpenInteractionForServiceError
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.ui.explorer.ExplorerPluginActionProvider
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.explorer.api.ExplorerActionCapabilityKeys
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.autojs.plugin.explorer.api.ExplorerArchiveRequestKeys
import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal data class ResolvedExplorerAction(
    val packageName: String,
    val component: ComponentName,
    val actionLabel: String,
    val menuTitle: String,
    val descriptor: ExplorerActionDescriptor,
    val pluginTarget: AidlPluginHost.Discovered,
)

internal object ExplorerActionRegistry : ExplorerPluginActionProvider {

    private val snapshot = AtomicReference<List<ResolvedExplorerAction>>(emptyList())

    // PackageManager-only fingerprint of the last clean refresh. While unchanged, plugin
    // services are not re-bound: binding may spawn plugin processes and starve the UI for
    // hundreds of milliseconds right after the host returns to the foreground.
    // zh-CN: 上次完整刷新的轻量 PackageManager 指纹. 指纹未变化时跳过插件服务重新绑定,
    // 因为绑定可能拉起插件进程, 在宿主回到前台后造成数百毫秒的界面卡顿.
    private val refreshStamps = AtomicReference<List<ExplorerActionRefreshStamp>?>(null)

    /**
     * Rebuilds the action snapshot and reports whether consumers must rebind their action
     * references. Discovery and catalog IPC run only when the installed/enabled/authorized
     * plugin set changed since the last clean refresh; otherwise the existing snapshot stays
     * valid because launches re-validate components and re-bind services on demand.
     *
     * A completed refresh always replaces the backing action objects, even when their visible
     * catalog presentation is unchanged. Consumers must therefore rebind after every completed
     * refresh so a row cannot retain a pre-update action that fails launch-time membership checks.
     */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val stamps = collectRefreshStamps(appContext)
        if (stamps != null && stamps == refreshStamps.get()) {
            return@withContext false
        }
        var discoveryClean = true
        val resolved = LinkedHashMap<String, ResolvedExplorerAction>()
        ExplorerActionPluginHost.discoverWakingFailures(appContext)
            .sortedWith(compareBy({ it.serviceInfo.packageName }, { it.serviceInfo.name }))
            .forEach candidateLoop@{ candidate ->
                val packageName = candidate.serviceInfo.packageName
                if (candidate.error != null) discoveryClean = false
                val pluginInfo = candidate.pluginInfo ?: return@candidateLoop
                val advertisedProtocolVersion = resolveProtocolVersion(pluginInfo)
                if (!isAvailablePlugin(appContext, packageName, pluginInfo, advertisedProtocolVersion)) {
                    return@candidateLoop
                }

                runCatching {
                    val catalog = ExplorerActionPluginHost.getActionCatalog(appContext, candidate)
                    ExplorerActionCatalogParser.parse(
                        packageName,
                        catalog,
                        requireNotNull(advertisedProtocolVersion),
                    ).forEach actionLoop@{ descriptor ->
                        val component = ExplorerActionLauncher.resolveAndValidateActivity(
                            context = appContext,
                            packageName = packageName,
                            activityClassName = descriptor.activityClassName,
                        ) ?: return@actionLoop
                        val label = resolveActionLabel(appContext, packageName, descriptor) ?: return@actionLoop
                        val menuTitle = ExplorerActionCatalogPolicy.sanitizeDisplayText(
                            label,
                            ExplorerActionProtocol.MAX_LABEL_LENGTH,
                        ) ?: return@actionLoop
                        val key = "$packageName:${descriptor.id}"
                        resolved.putIfAbsent(
                            key,
                            ResolvedExplorerAction(
                                packageName = packageName,
                                component = component,
                                actionLabel = label,
                                menuTitle = menuTitle,
                                descriptor = descriptor,
                                pluginTarget = candidate,
                            ),
                        )
                    }
                }.onFailure { error ->
                    discoveryClean = false
                    Log.w(TAG, "Failed to load Explorer Action catalog from $packageName", error)
                }
            }
        val next = resolved.values.sortedWith(
            compareByDescending<ResolvedExplorerAction> { it.descriptor.priority }
                .thenBy { it.packageName }
                .thenBy { it.descriptor.id }
        )
        // A refresh with failed candidates keeps the fingerprint unset so the next call retries.
        // zh-CN: 存在失败候选的刷新不记录指纹, 下一次调用会重试.
        refreshStamps.set(stamps.takeIf { discoveryClean })
        snapshot.set(next)
        true
    }

    private fun collectRefreshStamps(context: Context): List<ExplorerActionRefreshStamp>? = runCatching {
        val packageManager = context.packageManager
        val intent = Intent(ExplorerActionPluginHost.ACTION_EXPLORER_ACTION).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, 0)
        }
        services.mapNotNull { it.serviceInfo }
            .map { serviceInfo ->
                val packageName = serviceInfo.packageName
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
                ExplorerActionRefreshStamp(
                    packageName = packageName,
                    serviceName = serviceInfo.name,
                    versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                    lastUpdateTime = packageInfo.lastUpdateTime,
                    enabled = PluginEnableStore.isEnabled(context, packageName, true),
                    authorized = PluginTrustManager.isAuthorized(context, packageName),
                )
            }
            .sortedWith(compareBy({ it.packageName }, { it.serviceName }))
    }.getOrNull()

    override fun actionsFor(
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        items: List<ExplorerItem>,
    ): List<ResolvedExplorerAction> {
        return matchingActionsFor(items).filter { action ->
            ExplorerActionPolicy.allows(surface, entryPoint, action.descriptor)
        }
    }

    private fun matchingActionsFor(items: List<ExplorerItem>): List<ResolvedExplorerAction> {
        if (items.isEmpty() || items.size > ExplorerActionProtocol.MAX_TARGETS_PER_REQUEST) return emptyList()
        if (!targetsShareCanonicalParent(items)) return emptyList()
        val targets = items.map { item -> targetFor(item) ?: return emptyList() }
        return matchingActionsForTargets(targets)
    }

    internal fun matchingActionsForFile(file: File): List<ResolvedExplorerAction> {
        val source = runCatching { file.canonicalFile }.getOrNull() ?: return emptyList()
        if (!source.isFile || !source.canRead() || source.parentFile == null) return emptyList()
        return matchingActionsForTargets(
            listOf(
                ExplorerActionTarget(
                    mimeType = Mime.fromFileOrWildcard(source.path),
                    extension = source.extension,
                    kind = ExplorerActionValues.TARGET_FILE,
                    fileName = source.name,
                ),
            ),
        )
    }

    private fun matchingActionsForTargets(
        targets: List<ExplorerActionTarget>,
    ): List<ResolvedExplorerAction> {
        val actions = snapshot.get()
        val matchingIdsByPackage = actions.groupBy(ResolvedExplorerAction::packageName).mapValues {
            (packageName, packageActions) ->
            ExplorerActionMatchingPolicy.matchingActionIds(
                descriptors = packageActions.map(ResolvedExplorerAction::descriptor),
                targets = targets.map { target ->
                    if (target.kind != ExplorerActionValues.TARGET_FILE) target else target.copy(
                        mimeType = ExplorerM3uPlaylistPolicy.resolveMimeType(
                            packageName, target.extension, target.mimeType,
                        ),
                    )
                },
            )
        }
        return actions.filter { action ->
            action.descriptor.id in matchingIdsByPackage[action.packageName].orEmpty()
        }
    }

    private fun targetsShareCanonicalParent(items: List<ExplorerItem>): Boolean {
        var commonParent: File? = null
        items.forEach { item ->
            val parent = runCatching {
                File(item.path).canonicalFile.parentFile?.canonicalFile
            }.getOrNull() ?: return false
            if (commonParent == null) {
                commonParent = parent
            } else if (parent != commonParent) {
                return false
            }
        }
        return commonParent != null
    }

    private fun targetFor(item: ExplorerItem): ExplorerActionTarget? {
        val file = File(item.path)
        if (!file.canRead()) return null
        return when {
            file.isFile -> {
                val targetType = ExplorerActionTargetTypePolicy.resolve(
                    type = item.type,
                    fallbackMimeType = Mime.fromFileOrWildcard(file.path),
                    rawExtension = file.extension,
                )
                ExplorerActionTarget(
                    mimeType = targetType.mimeType,
                    extension = targetType.extension,
                    kind = org.autojs.plugin.explorer.api.ExplorerActionValues.TARGET_FILE,
                    fileName = file.name,
                )
            }
            file.isDirectory -> ExplorerActionTarget(
                mimeType = DIRECTORY_MIME_TYPE,
                extension = "",
                kind = org.autojs.plugin.explorer.api.ExplorerActionValues.TARGET_DIRECTORY,
            )
            else -> null
        }
    }

    override fun launch(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        items: List<ExplorerItem>,
        action: ResolvedExplorerAction,
    ): Result<Unit> {
        if (action.descriptor.presentation != ExplorerActionValues.PRESENTATION_ACTIVITY) {
            return Result.failure(IllegalArgumentException("Explorer Action requires host presentation: ${action.descriptor.id}"))
        }
        if (!ExplorerActionPolicy.allows(surface, entryPoint, action.descriptor)) {
            return Result.failure(IllegalArgumentException("Explorer Action is not allowed on $surface from $entryPoint"))
        }
        if (action !in matchingActionsFor(items)) {
            return Result.failure(IllegalStateException("Explorer Action no longer matches the target: ${action.descriptor.id}"))
        }
        if (!PluginEnableStore.isEnabled(context, action.packageName, true)) {
            return Result.failure(IllegalStateException("Explorer Action plugin is disabled: ${action.packageName}"))
        }
        if (!PluginTrustManager.isAuthorized(context, action.packageName)) {
            return Result.failure(IllegalStateException("Explorer Action plugin is not authorized: ${action.packageName}"))
        }
        return ExplorerActionLauncher.launch(context, surface, items, action)
    }

    override suspend fun openArchive(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
        options: ExplorerArchiveOpenRequestOptions,
    ): ExplorerArchiveSessionConnection = withContext(Dispatchers.IO) {
        require(action.descriptor.presentation == ExplorerActionValues.PRESENTATION_HOST_EXPLORER) {
            "Explorer Action does not provide a host archive session"
        }
        require(ExplorerActionPolicy.allows(surface, entryPoint, action.descriptor)) {
            "Explorer Action is not allowed on $surface from $entryPoint"
        }
        require(action in matchingActionsFor(listOf(item))) {
            "Explorer Action no longer matches the target: ${action.descriptor.id}"
        }
        check(PluginEnableStore.isEnabled(context, action.packageName, true)) {
            "Explorer Action plugin is disabled: ${action.packageName}"
        }
        check(PluginTrustManager.isAuthorized(context, action.packageName)) {
            "Explorer Action plugin is not authorized: ${action.packageName}"
        }

        val source = File(item.path).canonicalFile
        require(source.isFile && source.canRead()) { "Archive source is not a readable file: ${item.name}" }
        val request = Bundle().apply {
            putString(
                ExplorerArchiveRequestKeys.DISPLAY_NAME,
                item.name.take(ExplorerActionProtocol.MAX_ARCHIVE_DISPLAY_NAME_LENGTH),
            )
            putLong(ExplorerArchiveRequestKeys.SIZE, source.length())
            putLong(ExplorerArchiveRequestKeys.LAST_MODIFIED, item.lastModified())
        }
        val requestPassword = options.passwordChars()
        var volumeSource: ExplorerArchiveVolumeSource? = null
        try {
            if (action.descriptor.protocolVersion >= ARCHIVE_VOLUME_PROTOCOL_VERSION) {
                volumeSource = ExplorerArchiveVolumeSource.create(
                    source = source,
                    expectedUid = action.pluginTarget.serviceInfo.applicationInfo.uid,
                )
                volumeSource?.let { sourceProvider ->
                    request.putBinder(
                        ExplorerArchiveRequestKeys.VOLUME_SOURCE_BINDER,
                        sourceProvider.asBinder(),
                    )
                }
            }
            requestPassword?.let { password ->
                request.putCharArray(ExplorerArchiveRequestKeys.PASSWORD, password)
            }
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                val leased = try {
                    ExplorerActionPluginHost.openArchive(
                        context = context.applicationContext,
                        target = action.pluginTarget,
                        source = descriptor,
                        request = request,
                        protocolVersion = action.descriptor.protocolVersion,
                    )
                } catch (error: ExplorerArchiveOpenRemoteFailure) {
                    val interaction = archiveOpenInteractionForServiceError(
                        action.descriptor.protocolVersion,
                        error.errorCode,
                    ) ?: throw error
                    throw ExplorerArchiveOpenInteractionException(
                        interactionKind = interaction,
                        message = error.message
                            ?.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
                            ?.trim()
                            ?.take(ExplorerActionProtocol.MAX_ARCHIVE_OPERATION_MESSAGE_LENGTH)
                            ?.takeIf(String::isNotEmpty)
                            ?: "Archive password is required",
                        cause = error,
                    )
                }
                ExplorerArchiveSessionConnection.create(
                    context = context,
                    pluginPackageName = action.packageName,
                    pluginUid = action.pluginTarget.serviceInfo.applicationInfo.uid,
                    protocolVersion = action.descriptor.protocolVersion,
                    remote = leased.value,
                    bindingLease = leased.bindingLease,
                    sourcePath = source.path,
                    volumeSource = volumeSource,
                ).also { volumeSource = null }
            }
        } finally {
            request.getCharArray(ExplorerArchiveRequestKeys.PASSWORD)?.fill('\u0000')
            request.remove(ExplorerArchiveRequestKeys.PASSWORD)
            request.remove(ExplorerArchiveRequestKeys.VOLUME_SOURCE_BINDER)
            requestPassword?.fill('\u0000')
            volumeSource?.close()
        }
    }

    private fun isAvailablePlugin(
        context: Context,
        packageName: String,
        pluginInfo: PluginInfo,
        advertisedProtocolVersion: Long?,
    ): Boolean {
        if (pluginInfo.engine != ExplorerActionPluginIds.ENGINE) return false
        if (!PluginEnableStore.isEnabled(context, packageName, true)) return false
        if (!PluginTrustManager.isAuthorized(context, packageName)) return false
        val requiresHostVersion = PluginCapabilityResolver.resolveRequiresHostVersion(pluginInfo.capabilities)
        if (!ExplorerActionAvailabilityPolicy.isHostVersionCompatible(
                org.autojs.autojs6.BuildConfig.VERSION_CODE.toLong(),
                requiresHostVersion,
            )
        ) {
            return false
        }
        if (!ExplorerActionAvailabilityPolicy.isProtocolSupported(advertisedProtocolVersion)) {
            return false
        }
        return ExplorerActionAvailabilityPolicy.isAbiCompatible(
            pluginInfo.supportedAbis?.asList(),
            Build.SUPPORTED_ABIS.asList(),
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveProtocolVersion(pluginInfo: PluginInfo): Long? = pluginInfo.capabilities
        ?.get(ExplorerActionCapabilityKeys.PROTOCOL_VERSION)
        ?.let(PluginCapabilityResolver::coerceLong)

    private fun resolvePluginTitle(context: Context, packageName: String, pluginInfo: PluginInfo): String {
        return PluginResourceResolver.resolve(
            context = context,
            packageName = packageName,
            identifier = "app_name" to PluginResourceResolver.ResType.STRING,
            fallback = PluginResourceResolver.resolveStringValue(context, packageName, pluginInfo.name),
        )?.let { ExplorerActionCatalogPolicy.sanitizeDisplayText(it) }
            ?: packageName
    }

    private fun resolveActionLabel(
        context: Context,
        packageName: String,
        descriptor: ExplorerActionDescriptor,
    ): String? {
        return descriptor.labelResourceName?.let { resourceName ->
            PluginResourceResolver.resolve(
                context = context,
                packageName = packageName,
                identifier = resourceName to PluginResourceResolver.ResType.STRING,
                fallback = descriptor.labelFallback,
            )
        }?.let { ExplorerActionCatalogPolicy.sanitizeDisplayText(it) }
            ?: ExplorerActionCatalogPolicy.sanitizeDisplayText(descriptor.labelFallback)
    }

    private const val TAG = "ExplorerActionRegistry"
    private const val DIRECTORY_MIME_TYPE = "inode/directory"
    private const val ARCHIVE_VOLUME_PROTOCOL_VERSION = 12
}

private data class ExplorerActionRefreshStamp(
    val packageName: String,
    val serviceName: String,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val enabled: Boolean,
    val authorized: Boolean,
)
