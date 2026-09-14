package org.autojs.autojs.core.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.engine.NodeJsRuntimePluginHost
import org.autojs.plugin.nodejs.api.NodeJsPluginActions
import org.autojs.plugin.nodejs.api.NodeJsPluginIds
import java.io.File

/**
 * Decides whether the terminal can offer `node` / `npm`, and why not otherwise.
 *
 * Resolution order: an authorized Node.js Runtime plugin must be installed, it must declare the
 * launcher contract in its manifest, the launcher must exist as a real ELF file in the plugin's
 * native library directory, and a one-shot `--version` run must succeed on this device. The result
 * is cached per plugin build and invalidated when the plugin set changes.
 *
 * zh-CN: 判定终端能否提供 `node` / `npm`, 不能时给出原因. 结果按插件构建缓存, 插件集合变化时失效.
 */
object NodeCliLocator {

    /** Package of the official Node.js Runtime plugin, used to deep-link Plugin Center. zh-CN: 官方 Node.js 运行时插件包名, 用于跳转插件中心. */
    const val OFFICIAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.nodejs"

    data class Launcher(
        val packageName: String,
        val versionCode: Long,
        val nativeLibraryDir: String,
        val executable: File,
        val descriptor: NodeCliMetadata.Descriptor,
    )

    sealed class Resolution {

        data class Available(val launcher: Launcher, val probe: NodeCliProbe.Result) : Resolution()

        sealed class Unavailable : Resolution() {

            /** No runtime plugin service is installed at all. zh-CN: 完全未安装运行时插件. */
            object PluginMissing : Unavailable()

            /** Installed, but disabled or not authorized in Plugin Center. zh-CN: 已安装但在插件中心被禁用或未授权. */
            object PluginNotAuthorized : Unavailable()

            /** The plugin predates the launcher contract. zh-CN: 插件早于启动器契约. */
            data class PluginTooOld(val packageName: String, val reason: String) : Unavailable()

            /** The contract is declared but the launcher file is absent or not an ELF. zh-CN: 已声明契约但启动器文件缺失或不是 ELF. */
            data class ExecutableMissing(val packageName: String, val abi: String, val executableName: String) : Unavailable()

            /** The device refused to run the launcher, or it failed to report a version. zh-CN: 设备拒绝运行启动器, 或启动器未能报告版本. */
            data class ExecDenied(val launcher: Launcher, val probe: NodeCliProbe.Result) : Unavailable()

            /** The launcher runs, but installing the npm / corepack archive or linking commands failed. zh-CN: 启动器可运行, 但安装 npm / corepack 资产或创建命令链接失败. */
            data class SetupFailed(val launcher: Launcher, val message: String) : Unavailable()

        }

    }

    private data class CacheEntry(val key: String, val resolution: Resolution)

    private val lock = Any()
    private var cache: CacheEntry? = null
    private var observing = false

    /**
     * @param refresh re-run the launcher check even when a cached result exists
     */
    suspend fun resolve(context: Context, refresh: Boolean = false): Resolution {
        val appContext = context.applicationContext
        ensureObserver()
        val candidate = NodeJsRuntimePluginHost.discover(appContext).firstOrNull()
            ?: return if (hasAnyRuntimeService(appContext)) {
                Resolution.Unavailable.PluginNotAuthorized
            } else {
                Resolution.Unavailable.PluginMissing
            }
        val packageName = candidate.serviceInfo.packageName
        val versionCode = candidate.packageVersionCode ?: -1L
        val cacheKey = "$packageName@$versionCode:${candidate.packageLastUpdateTime}"
        if (!refresh) {
            synchronized(lock) { cache?.takeIf { it.key == cacheKey }?.let { return it.resolution } }
        }
        val resolution = resolveUncached(candidate, packageName, versionCode)
        synchronized(lock) { cache = CacheEntry(cacheKey, resolution) }
        return resolution
    }

    fun cachedOrNull(): Resolution? = synchronized(lock) { cache?.resolution }

    fun invalidate() = synchronized(lock) { cache = null }

    private fun resolveUncached(
        candidate: NodeJsRuntimePluginHost.Candidate,
        packageName: String,
        versionCode: Long,
    ): Resolution {
        val descriptor = when (val parsed = NodeCliMetadata.parse(candidate.serviceInfo.metaData?.toMap())) {
            is NodeCliMetadata.ParseResult.Missing -> return Resolution.Unavailable.PluginTooOld(packageName, parsed.reason)
            is NodeCliMetadata.ParseResult.Valid -> parsed.descriptor
        }
        val nativeLibraryDir = candidate.serviceInfo.applicationInfo?.nativeLibraryDir
        val executable = nativeLibraryDir?.let { File(it, descriptor.executableName) }
        if (executable == null || !NodeCliMetadata.isElf(executable)) {
            return Resolution.Unavailable.ExecutableMissing(
                packageName = packageName,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                executableName = descriptor.executableName,
            )
        }
        val launcher = Launcher(packageName, versionCode, nativeLibraryDir, executable, descriptor)
        val probe = NodeCliProbe.run(executable)
        return if (probe.succeeded) {
            Resolution.Available(launcher, probe)
        } else {
            Resolution.Unavailable.ExecDenied(launcher, probe)
        }
    }

    private fun hasAnyRuntimeService(context: Context): Boolean {
        val intent = Intent(NodeJsPluginActions.RUNTIME).apply {
            addCategory(NodeJsPluginIds.ENGINE)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentServices(intent, flags)
            }
        }.getOrDefault(emptyList())
        return resolved.isNotEmpty()
    }

    private fun ensureObserver() {
        synchronized(lock) {
            if (observing) return
            observing = true
        }
        PluginStateChangeRegistry.addListener { invalidate() }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.toMap(): Map<String, Any?> = keySet().associateWith { get(it) }

}
