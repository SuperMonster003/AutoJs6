package org.autojs.autojs.core.plugin.tool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.tool.api.PluginToolCapabilityKeys
import org.autojs.plugin.tool.api.PluginToolCatalogKeys
import org.autojs.plugin.tool.api.PluginToolContract
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** Why a provider's tools are, or are not, available. */
enum class PluginToolProviderStatus { AVAILABLE, DISABLED, UNAUTHORIZED, INCOMPATIBLE, FAILED, REFUSED }

/**
 * One provider package as the registry last saw it. [catalog] is set while [status] is AVAILABLE; [detail] says why
 * otherwise, and [catalog] diagnostics list the tools that were dropped.
 */
data class PluginToolProvider(
    val packageName: String,
    val serviceName: String,
    val versionCode: Long,
    val trust: PluginAuthorizedState,
    val status: PluginToolProviderStatus,
    val catalog: PluginToolCatalog?,
    val detail: String?,
    val target: AidlPluginHost.Discovered?,
)

/**
 * The providers of the plugin tool contract and their catalogs (MCP Server roadmap P12 D30 / D33 / D34,
 * docs/dev/plugin-tool-protocol-v1.md). [refresh] takes a PackageManager-only fingerprint of the provider services
 * (package, versionCode, update time, Plugin Center enable and authorization state) and rebuilds the snapshot only when
 * it changed, so a package change, an update or a switch in the Plugin Center shows up on the next call while listing
 * tools normally binds nothing. Catalogs are cached per (package, versionCode, update time) and fetched again only for
 * changed packages. A provider is AVAILABLE when it is enabled, authorized, meets its required host version, speaks a
 * supported contract version, returns an accepted catalog and keeps its short name against the other providers
 * ([PluginToolShortNames]). One provider per package: further provider services of a package are ignored.
 *
 * zh-CN: 插件工具契约的提供者及其目录. [refresh] 取提供者服务的轻量 PackageManager 指纹 (包, versionCode, 更新时间, 插件中心启用与授权状态),
 * 仅在指纹变化时重建快照, 因此包变化, 更新或插件中心开关会在下一次调用时体现, 而平时列出工具不绑定任何服务. 目录按 (包, versionCode, 更新时间)
 * 缓存, 只为变化的包重新获取. 每个包只有一个提供者.
 */
object PluginToolRegistry {

    private const val TAG = "PluginToolRegistry"

    private data class Stamp(
        val packageName: String,
        val serviceName: String,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val enabled: Boolean,
        val authorized: Boolean,
    )

    private data class CachedCatalog(val versionCode: Long, val lastUpdateTime: Long, val official: Boolean, val result: CatalogResult)

    private sealed class CatalogResult {
        data class Parsed(val parse: PluginToolCatalogParse) : CatalogResult()
        data class Error(val code: String, val message: String?) : CatalogResult()
    }

    private val mutex = Mutex()
    private val snapshot = AtomicReference<List<PluginToolProvider>>(emptyList())
    private val stamps = AtomicReference<List<Stamp>?>(null)
    private val catalogs = ConcurrentHashMap<String, CachedCatalog>()

    /** The providers of the last refresh, sorted by package. */
    val providers: List<PluginToolProvider>
        get() = snapshot.get()

    /** The tools of the AVAILABLE providers of the last refresh. */
    val tools: List<Pair<PluginToolProvider, PluginToolDefinition>>
        get() = snapshot.get().filter { it.status == PluginToolProviderStatus.AVAILABLE }.flatMap { provider ->
            provider.catalog?.tools.orEmpty().map { provider to it }
        }

    /** Forgets the fingerprint so the next [refresh] rebuilds the snapshot; cached catalogs stay valid. */
    fun invalidate() {
        stamps.set(null)
    }

    /**
     * Rebuilds the snapshot when the provider set changed since the last clean refresh; true when it was rebuilt. A refresh
     * whose discovery or catalog calls failed keeps no fingerprint, so the next call retries.
     */
    suspend fun refresh(context: Context): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val current = collectStamps(appContext)
            if (current != null && current == stamps.get()) return@withContext false
            var clean = current != null
            val byPackage = current.orEmpty().groupBy { it.packageName }.mapValues { (_, list) -> list.minBy { it.serviceName } }
            val discovered = PluginToolProviderHost.discoverWakingFailures(appContext)
                .sortedBy { it.serviceInfo.name }
                .groupBy { it.serviceInfo.packageName }
                .mapValues { (_, list) -> list.first() }

            val candidates = byPackage.values.sortedBy { it.packageName }.map { stamp ->
                val trust = PluginTrustManager.resolveTrustInfo(appContext, stamp.packageName).authorizedState
                val target = discovered[stamp.packageName]
                fun entry(status: PluginToolProviderStatus, detail: String?, catalog: PluginToolCatalog? = null) =
                    PluginToolProvider(stamp.packageName, stamp.serviceName, stamp.versionCode, trust, status, catalog, detail, target)
                when {
                    !stamp.enabled -> entry(PluginToolProviderStatus.DISABLED, "the plugin is disabled in the Plugin Center")
                    !stamp.authorized -> entry(PluginToolProviderStatus.UNAUTHORIZED, "the plugin is not authorized in the Plugin Center")
                    target == null -> entry(PluginToolProviderStatus.FAILED, "the provider service was not discovered").also { clean = false }
                    target.pluginInfo == null -> entry(PluginToolProviderStatus.FAILED, "getInfo failed: ${target.error?.message}").also { clean = false }
                    else -> incompatibility(target.pluginInfo!!)?.let { entry(PluginToolProviderStatus.INCOMPATIBLE, it) }
                        ?: when (val result = catalog(appContext, stamp, target, trust == PluginAuthorizedState.OFFICIAL)) {
                            null -> entry(PluginToolProviderStatus.FAILED, "getCatalog failed").also { clean = false }
                            is CatalogResult.Error -> entry(PluginToolProviderStatus.REFUSED, "the plugin reported ${result.code}${result.message?.let { ": $it" }.orEmpty()}")
                            is CatalogResult.Parsed -> when (val parse = result.parse) {
                                is PluginToolCatalogParse.Refused -> entry(PluginToolProviderStatus.REFUSED, parse.reason)
                                is PluginToolCatalogParse.Accepted -> entry(PluginToolProviderStatus.AVAILABLE, null, parse.catalog)
                            }
                        }
                }
            }

            val refusedNames = PluginToolShortNames.resolve(
                candidates.filter { it.status == PluginToolProviderStatus.AVAILABLE }.map { PluginToolShortNames.Claim(it.packageName, it.catalog!!.shortName, rank(it.trust)) },
            )
            val next = candidates.map { provider ->
                refusedNames[provider.packageName]?.let { reason -> provider.copy(status = PluginToolProviderStatus.REFUSED, catalog = null, detail = reason) } ?: provider
            }
            catalogs.keys.retainAll(byPackage.keys)
            snapshot.set(next)
            stamps.set(current.takeIf { clean })
            Log.i(TAG, "refresh: " + next.joinToString { "${it.packageName}=${it.status}${it.catalog?.let { c -> "(${c.shortName}: ${c.tools.size})" }.orEmpty()}" })
            true
        }
    }

    /** Why [info] cannot serve this host, or null. */
    private fun incompatibility(info: PluginInfo): String? {
        val requires = PluginCapabilityResolver.resolveRequiresHostVersion(info.capabilities)
        if (!PluginCapabilityResolver.isHostVersionCompatible(BuildConfig.VERSION_CODE.toLong(), requires)) return "the plugin needs AutoJs6 build $requires or later"
        @Suppress("DEPRECATION")
        val version = PluginCapabilityResolver.coerceLong(info.capabilities?.get(PluginToolCapabilityKeys.CONTRACT_VERSION))?.toInt()
            ?: return "the plugin declares no ${PluginToolCapabilityKeys.CONTRACT_VERSION}"
        if (version < PluginToolContract.MIN_CONTRACT_VERSION) return "plugin tool contract version $version is not supported"
        return null
    }

    /** The cached result while the package is unchanged, otherwise a fresh getCatalog; null when the call failed. */
    private suspend fun catalog(context: Context, stamp: Stamp, target: AidlPluginHost.Discovered, official: Boolean): CatalogResult? {
        catalogs[stamp.packageName]?.takeIf { it.versionCode == stamp.versionCode && it.lastUpdateTime == stamp.lastUpdateTime && it.official == official }
            ?.let { return it.result }
        val answer = runCatching { PluginToolProviderHost.getCatalog(context, target) }.getOrElse { error ->
            Log.w(TAG, "getCatalog failed for ${stamp.packageName}", error)
            return null
        }
        val json = answer.getString(PluginToolCatalogKeys.CATALOG_JSON)
        val result = when {
            json != null -> CatalogResult.Parsed(PluginToolCatalogParser.parse(json, official))
            else -> CatalogResult.Error(answer.getString(PluginToolCatalogKeys.ERROR_CODE) ?: "NO_CATALOG", answer.getString(PluginToolCatalogKeys.ERROR_MESSAGE))
        }
        catalogs[stamp.packageName] = CachedCatalog(stamp.versionCode, stamp.lastUpdateTime, official, result)
        return result
    }

    private fun rank(trust: PluginAuthorizedState): Int = when (trust) {
        PluginAuthorizedState.OFFICIAL -> 3
        PluginAuthorizedState.TRUSTED -> 2
        PluginAuthorizedState.USER_GRANTED -> 1
        PluginAuthorizedState.REQUIRED -> 0
    }

    private fun collectStamps(context: Context): List<Stamp>? = runCatching {
        val packageManager = context.packageManager
        val intent = Intent(PluginToolContract.SERVICE_ACTION).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, 0)
        }
        services.mapNotNull { it.serviceInfo }.map { serviceInfo ->
            val packageName = serviceInfo.packageName
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            Stamp(
                packageName = packageName,
                serviceName = serviceInfo.name,
                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                lastUpdateTime = packageInfo.lastUpdateTime,
                enabled = PluginEnableStore.isEnabled(context, packageName, true),
                authorized = PluginTrustManager.isAuthorized(context, packageName),
            )
        }.sortedWith(compareBy({ it.packageName }, { it.serviceName }))
    }.getOrNull()
}
