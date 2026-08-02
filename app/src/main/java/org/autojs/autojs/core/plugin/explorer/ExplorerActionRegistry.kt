package org.autojs.autojs.core.plugin.explorer

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginResourceResolver
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.ui.explorer.ExplorerPluginActionProvider
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.explorer.api.ExplorerActionCapabilityKeys
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal data class ResolvedExplorerAction(
    val packageName: String,
    val component: ComponentName,
    val menuTitle: String,
    val descriptor: ExplorerActionDescriptor,
)

internal object ExplorerActionRegistry : ExplorerPluginActionProvider {

    private val snapshot = AtomicReference<List<ResolvedExplorerAction>>(emptyList())

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val resolved = LinkedHashMap<String, ResolvedExplorerAction>()
        ExplorerActionPluginHost.discover(appContext)
            .sortedWith(compareBy({ it.serviceInfo.packageName }, { it.serviceInfo.name }))
            .forEach candidateLoop@{ candidate ->
                val packageName = candidate.serviceInfo.packageName
                val pluginInfo = candidate.pluginInfo ?: return@candidateLoop
                val advertisedProtocolVersion = resolveProtocolVersion(pluginInfo)
                if (!isAvailablePlugin(appContext, packageName, pluginInfo, advertisedProtocolVersion)) {
                    return@candidateLoop
                }

                runCatching {
                    val catalog = ExplorerActionPluginHost.getActionCatalog(appContext, candidate)
                    val pluginTitle = resolvePluginTitle(appContext, packageName, pluginInfo)
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
                            "$pluginTitle: $label",
                            ExplorerActionProtocol.MAX_LABEL_LENGTH,
                        ) ?: return@actionLoop
                        val key = "$packageName:${descriptor.id}"
                        resolved.putIfAbsent(
                            key,
                            ResolvedExplorerAction(
                                packageName = packageName,
                                component = component,
                                menuTitle = menuTitle,
                                descriptor = descriptor,
                            ),
                        )
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Failed to load Explorer Action catalog from $packageName", error)
                }
            }
        snapshot.set(
            resolved.values.sortedWith(
                compareByDescending<ResolvedExplorerAction> { it.descriptor.priority }
                    .thenBy { it.packageName }
                    .thenBy { it.descriptor.id }
            )
        )
    }

    override fun actionsFor(
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
    ): List<ResolvedExplorerAction> {
        return matchingActionsFor(item).filter { action ->
            ExplorerActionPolicy.allows(surface, entryPoint, action.descriptor)
        }
    }

    private fun matchingActionsFor(item: ExplorerItem): List<ResolvedExplorerAction> {
        val file = File(item.path)
        if (!file.isFile || !file.canRead()) return emptyList()
        val target = ExplorerActionTarget(
            mimeType = Mime.fromFileOrWildcard(file.path),
            extension = file.extension.lowercase(Locale.ROOT),
        )
        return snapshot.get().filter { action ->
            ExplorerActionCatalogPolicy.matches(action.descriptor, target)
        }
    }

    override fun launch(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
    ): Result<Unit> {
        if (!ExplorerActionPolicy.allows(surface, entryPoint, action.descriptor)) {
            return Result.failure(IllegalArgumentException("Explorer Action is not allowed on $surface from $entryPoint"))
        }
        if (action !in matchingActionsFor(item)) {
            return Result.failure(IllegalStateException("Explorer Action no longer matches the target: ${action.descriptor.id}"))
        }
        if (!PluginEnableStore.isEnabled(context, action.packageName, true)) {
            return Result.failure(IllegalStateException("Explorer Action plugin is disabled: ${action.packageName}"))
        }
        if (!PluginTrustManager.isAuthorized(context, action.packageName)) {
            return Result.failure(IllegalStateException("Explorer Action plugin is not authorized: ${action.packageName}"))
        }
        return ExplorerActionLauncher.launch(context, surface, item, action)
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
}
