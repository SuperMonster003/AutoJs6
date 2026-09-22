package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginAbiUtils
import org.autojs.autojs.core.plugin.center.PluginCenterProcessLogger
import org.autojs.autojs.core.plugin.center.PluginIndexEntry
import org.autojs.autojs.core.plugin.center.PluginIndexRepository
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginInstalledIconResolver
import org.autojs.autojs.core.plugin.center.PluginNativeAlignmentPolicy
import org.autojs.autojs.core.plugin.nativealign.NativePageSize
import org.autojs.autojs.util.AndroidUtils
import org.autojs.autojs6.R

/**
 * Builds the wizard rows from the official plugin index. The cached index is
 * preferred; the network is only consulted when the cache is missing or does
 * not know every catalog package.
 *
 * zh-CN: 根据官方插件索引构建向导行. 优先使用缓存索引;
 * 仅当缓存缺失或缺少目录中的包时才访问网络.
 */
object PluginInstallWizardLoader {

    private const val TAG = "PluginInstallWizard"

    private val indexRepo = PluginIndexRepository()

    sealed interface Result {
        /** [warning] carries the online fetch failure when cached entries had to be used instead. */
        data class Success(val items: List<PluginInstallWizardItem>, val warning: String? = null) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun load(context: Context): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val sources = setOf(PluginIndexSource.OFFICIAL)

        var entries = runCatching { indexRepo.getCachedIndex(appContext, sources) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrElse { emptyList() }

        var failureMessage: String? = null
        if (needsOnlineIndex(entries)) {
            PluginCenterProcessLogger.i("Plugin install wizard fetches the online index (cached entries: ${entries.size}).")
            runCatching { indexRepo.fetchOnlineIndex(appContext, forceRefresh = false, enabledSources = sources) }
                .onSuccess { fetched -> if (fetched.isNotEmpty()) entries = fetched }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    failureMessage = error.message ?: error.toString()
                    Log.w(TAG, "fetchOnlineIndex failed: $failureMessage")
                }
        }

        if (entries.isEmpty()) {
            return@withContext Result.Failure(failureMessage ?: appContext.getString(R.string.text_unavailable))
        }

        val packageManager = appContext.packageManager
        val items = buildItems(
            entries = entries,
            isInstalled = { packageName -> packageManager.isPackageInstalled(packageName) },
            deviceAbis = AndroidUtils.deviceFilteredAbiList,
            pageSize = NativePageSize.current(),
            loadInstalledIcon = { packageName -> packageManager.loadInstalledIcon(appContext, packageName) },
        )
        PluginCenterProcessLogger.i("Plugin install wizard rows built: count=${items.size}.")
        Result.Success(items, warning = failureMessage)
    }

    /** True when the cache is empty or misses at least one catalog package. */
    fun needsOnlineIndex(cachedEntries: List<PluginIndexEntry>): Boolean {
        if (cachedEntries.isEmpty()) return true
        val known = cachedEntries.mapTo(HashSet()) { it.packageName }
        return PluginInstallWizardCatalog.entries.any { it.packageName !in known }
    }

    /**
     * Catalog entries first (catalog order), then any featured official entry the
     * catalog does not know, sorted by title under [PluginInstallWizardCategory.OTHERS].
     */
    fun buildItems(
        entries: List<PluginIndexEntry>,
        isInstalled: (String) -> Boolean,
        deviceAbis: Collection<String>,
        pageSize: Long,
        loadInstalledIcon: (String) -> Drawable? = { null },
    ): List<PluginInstallWizardItem> {
        val entriesByPackage = entries
            .filter { it.source == PluginIndexSource.OFFICIAL }
            .associateBy { it.packageName }

        val catalogItems = PluginInstallWizardCatalog.entries.mapNotNull { catalogEntry ->
            val indexEntry = entriesByPackage[catalogEntry.packageName] ?: return@mapNotNull null
            toItem(
                indexEntry = indexEntry,
                fallbackTitle = catalogEntry.fallbackTitle,
                category = catalogEntry.category,
                inStandard = catalogEntry.inStandard,
                inMinimal = catalogEntry.inMinimal,
                isInstalled = isInstalled,
                deviceAbis = deviceAbis,
                pageSize = pageSize,
                loadInstalledIcon = loadInstalledIcon,
            )
        }

        val extraItems = entriesByPackage.values
            .filter { it.isFeatured && !PluginInstallWizardCatalog.contains(it.packageName) }
            .map { indexEntry ->
                toItem(
                    indexEntry = indexEntry,
                    fallbackTitle = indexEntry.packageName,
                    category = PluginInstallWizardCategory.OTHERS,
                    inStandard = false,
                    inMinimal = false,
                    isInstalled = isInstalled,
                    deviceAbis = deviceAbis,
                    pageSize = pageSize,
                    loadInstalledIcon = loadInstalledIcon,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        return catalogItems + extraItems
    }

    private fun toItem(
        indexEntry: PluginIndexEntry,
        fallbackTitle: String,
        category: PluginInstallWizardCategory,
        inStandard: Boolean,
        inMinimal: Boolean,
        isInstalled: (String) -> Boolean,
        deviceAbis: Collection<String>,
        pageSize: Long,
        loadInstalledIcon: (String) -> Drawable?,
    ): PluginInstallWizardItem {
        val latestRelease = indexEntry.releases.maxByOrNull { it.versionCode }
        val alignment = latestRelease?.nativePageAlignment ?: indexEntry.nativePageAlignment
        val installed = isInstalled(indexEntry.packageName)
        return PluginInstallWizardItem(
            packageName = indexEntry.packageName,
            title = indexEntry.title.takeIf { it.isNotBlank() } ?: fallbackTitle,
            category = category,
            inStandard = inStandard,
            inMinimal = inMinimal,
            isInstalled = installed,
            installedIcon = if (installed) loadInstalledIcon(indexEntry.packageName) else null,
            apkUrl = latestRelease?.apkUrl?.takeIf { it.isNotBlank() },
            apkSha256 = latestRelease?.apkSha256,
            apkSizeBytes = latestRelease?.apkSizeBytes,
            iconUrl = indexEntry.iconUrl?.toString(),
            nightIconUrl = indexEntry.nightIconUrl?.toString(),
            isAbiMismatch = PluginAbiUtils.hasMismatchWithDevice(indexEntry.supportedAbis, deviceAbis),
            isNativeAlignmentMismatch = PluginNativeAlignmentPolicy.needsWarning(alignment, pageSize),
        )
    }

    private fun PackageManager.isPackageInstalled(packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun PackageManager.loadInstalledIcon(context: Context, packageName: String): Drawable? = runCatching {
        @Suppress("DEPRECATION")
        val appInfo = getApplicationInfo(packageName, 0)
        PluginInstalledIconResolver.load(context, appInfo)
    }.getOrNull()

}
