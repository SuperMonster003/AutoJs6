package org.autojs.autojs.core.plugin.explorer.output

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils

internal data class ExplorerActionPluginIdentity(
    val uid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val signingDigests: Set<String>,
)

internal object ExplorerActionPluginIdentityResolver {

    fun resolve(context: Context, packageName: String): ExplorerActionPluginIdentity {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val applicationInfo = packageInfo.applicationInfo
            ?: throw PackageManager.NameNotFoundException("Plugin application info is unavailable")
        val signingDigests = PluginSignatureUtils.getSha256Fingerprints(context, packageName).toSortedSet()
        require(signingDigests.isNotEmpty()) { "Explorer Action plugin has no signing identity" }
        return ExplorerActionPluginIdentity(
            uid = applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signingDigests = signingDigests,
        )
    }

    fun matches(
        context: Context,
        transaction: ExplorerActionOutputTransaction,
    ): Boolean = runCatching {
        resolve(context, transaction.pluginPackageName) == ExplorerActionPluginIdentity(
            uid = transaction.pluginUid,
            versionCode = transaction.pluginVersionCode,
            lastUpdateTime = transaction.pluginLastUpdateTime,
            signingDigests = transaction.pluginSigningDigests,
        )
    }.getOrDefault(false)
}
