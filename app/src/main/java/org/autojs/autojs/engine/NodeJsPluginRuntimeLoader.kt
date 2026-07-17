package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.nodejs.api.NodeJsPluginIds
import java.io.File

object NodeJsPluginRuntimeLoader {

    private const val NODE_LIBRARY_NAME = "node"
    private const val NODE_LIBRARY_FILE = "libnode.so"
    private const val BRIDGE_LIBRARY_NAME = "autojs6-node"
    private const val BRIDGE_LIBRARY_FILE = "libautojs6-node.so"

    fun loadNodeOrSystem(context: Context? = null) {
        runCatching { System.loadLibrary("c++_shared") }
        loadPluginOrSystem(NODE_LIBRARY_NAME, NODE_LIBRARY_FILE, context)
    }

    fun loadBridgeOrSystem(context: Context? = null) {
        loadPluginOrSystem(BRIDGE_LIBRARY_NAME, BRIDGE_LIBRARY_FILE, context)
    }

    private fun loadPluginOrSystem(libraryName: String, libraryFileName: String, context: Context?) {
        val appContext = context ?: runCatching { GlobalAppContext.get() }.getOrNull()
        val selectedLibrary = appContext?.let { resolveNativeLibraryFile(it, libraryFileName) }
        if (selectedLibrary != null) {
            System.load(selectedLibrary.absolutePath)
            return
        }
        System.loadLibrary(libraryName)
    }

    fun resolveNodeLibraryFile(context: Context): File? {
        return resolveNativeLibraryFile(context, NODE_LIBRARY_FILE)
    }

    fun resolveBridgeLibraryFile(context: Context): File? {
        return resolveNativeLibraryFile(context, BRIDGE_LIBRARY_FILE)
    }

    private fun resolveNativeLibraryFile(context: Context, libraryFileName: String): File? {
        val hostLibrary = hostLibraryFile(context, libraryFileName)
        val pluginLibrary = findPluginNativeLibrary(context, libraryFileName)
        return if (usesPackagedHostRuntime()) {
            hostLibrary ?: pluginLibrary
        } else {
            pluginLibrary ?: hostLibrary
        }
    }

    private fun usesPackagedHostRuntime(): Boolean =
        BuildConfig.NODEJS_RELEASE_FLAVOR in setOf("nodeFull", "nodeMini")

    fun resolveNodeLibraryDir(context: Context): String? {
        return resolveNodeLibraryFile(context)?.parent
            ?: context.applicationContext.applicationInfo.nativeLibraryDir
    }

    private fun findPluginNativeLibrary(context: Context, libraryFileName: String): File? {
        val appContext = context.applicationContext
        return queryNodePluginCandidates(appContext)
            .asSequence()
            .filter { PluginEnableStore.isEnabled(appContext, it.serviceInfo.packageName, true) }
            .filter { PluginTrustManager.isAuthorized(appContext, it.serviceInfo.packageName) }
            .filter { isHostVersionCompatible(it.serviceInfo) }
            .mapNotNull { candidate ->
                candidate.applicationInfo.nativeLibraryDir
                    ?.takeIf(String::isNotBlank)
                    ?.let(::File)
                    ?.resolve(libraryFileName)
                    ?.takeIf(File::isFile)
                    ?.let { file -> candidate to file }
            }
            .sortedWith(compareByDescending<Pair<Candidate, File>> { it.first.versionCode ?: 0L })
            .map { it.second }
            .firstOrNull()
    }

    private fun queryNodePluginCandidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        return queryNodeInfoServices(pm)
            .mapNotNull { serviceInfo ->
                val appInfo = runCatching { getApplicationInfo(pm, serviceInfo.packageName) }.getOrNull()
                    ?: serviceInfo.applicationInfo
                val packageInfo = runCatching { getPackageInfo(pm, serviceInfo.packageName) }.getOrNull()
                Candidate(
                    serviceInfo = serviceInfo,
                    applicationInfo = appInfo,
                    versionCode = packageInfo?.let(PackageInfoCompat::getLongVersionCode),
                )
            }
    }

    private fun queryNodeInfoServices(pm: PackageManager): List<ServiceInfo> {
        val intent = Intent(PluginActions.INFO).apply {
            addCategory(NodeJsPluginIds.ENGINE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val flags = PackageManager.GET_META_DATA
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            pm.queryIntentServices(intent, flags)
        }
        return resolveList.mapNotNull { it.serviceInfo }
    }

    private fun isHostVersionCompatible(serviceInfo: ServiceInfo): Boolean {
        @Suppress("DEPRECATION")
        val requiresHostVersion = serviceInfo.metaData
            ?.get(PluginCapabilityKeys.REQUIRES_HOST_VERSION)
            ?.let(PluginCapabilityResolver::coerceLong)
        return PluginCapabilityResolver.isHostVersionCompatible(
            BuildConfig.VERSION_CODE.toLong(),
            requiresHostVersion,
        )
    }

    private fun hostLibraryFile(context: Context, libraryFileName: String): File? {
        return context.applicationContext.applicationInfo.nativeLibraryDir
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.resolve(libraryFileName)
            ?.takeIf(File::isFile)
    }

    private fun getApplicationInfo(pm: PackageManager, packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }
    }

    private fun getPackageInfo(pm: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(packageName, 0)
        }
    }

    private data class Candidate(
        val serviceInfo: ServiceInfo,
        val applicationInfo: ApplicationInfo,
        val versionCode: Long?,
    )
}
