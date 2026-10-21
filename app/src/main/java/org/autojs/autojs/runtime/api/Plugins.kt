package org.autojs.autojs.runtime.api

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import org.autojs.autojs.core.plugin.Plugin
import org.autojs.autojs.core.plugin.Plugin.PluginLoadException
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.pio.PFiles.copyAssetDir
import org.autojs.autojs.pio.PFiles.deleteRecursively
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.autojs.autojs.runtime.resource.RuntimeResourceScope.Stage
import org.autojs.autojs6.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by SuperMonster003 on Apr 2, 2024.
 * Transformed by SuperMonster003 on Apr 2, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Apr 2, 2024.
class Plugins(
    private val context: Context,
    private val runtime: PluginRuntime,
    private val resourceScope: RuntimeResourceScope? = null,
) {

    private val mPluginCacheDir = File(context.cacheDir, "plugin-scripts/")

    private val mPlugins = ConcurrentHashMap<String, Plugin>()

    // Connections passed to bindService, unbound when the script exits (docs/memory/ROADMAP.md H8).
    // zh-CN: 传给 bindService 的连接, 在脚本结束时解绑 (docs/memory/ROADMAP.md H8).
    private val mBoundPlugins: MutableSet<Plugin> = ConcurrentHashMap.newKeySet()

    init {
        resourceScope?.own(Stage.PLUGINS, "plugins.unbindAll") { unbindAll() }
    }

    fun load(packageName: String): Plugin {
        mPlugins[packageName]?.let { return it }

        if (!PluginEnableStore.isEnabled(context, packageName, defaultEnabled = true)) {
            throw PluginLoadException(context.getString(R.string.error_plugin_is_not_enabled_in_plugin_center, packageName))
        }
        if (!PluginTrustManager.isAuthorized(context, packageName)) {
            throw PluginLoadException(context.getString(R.string.error_plugin_is_not_authorized_in_plugin_center, packageName))
        }

        var packageContext = packages[packageName] ?: loadInstalledPackage(packageName) ?: throw Resources.NotFoundException(
            // "Plugin $packageName not found in installed apps or directory ${File(runtime.pluginSearchDir)}"
            context.getString(R.string.error_plugin_not_found_in_installed_apps, packageName)
        )
        packages.putIfAbsent(packageName, packageContext)?.let { packageContext = it }

        return try {
            val plugin = Plugin.load(context, packageContext, runtime, runtime.topLevelScope)
            val scriptCacheDir = getScriptCacheDir(packageName)
            copyAssetDir(packageContext.context.assets, plugin.assetsScriptDir, scriptCacheDir.path)
            plugin.mainScriptPath = File(scriptCacheDir, "index.js").path
            bindService(plugin)
            mPlugins[packageName] = plugin
            plugin
        } catch (e: Exception) {
            throw PluginLoadException(e)
        }
    }

    private fun bindService(plugin: Plugin) {
        if (plugin.version < 2) return
        val componentName = plugin.componentName ?: return
        if (plugin.pkg.installed) {
            val intent = Intent().apply { setComponent(componentName) }
            // A connection must be unbound even if bindService returns false.
            // zh-CN: 即使 bindService 返回 false, 连接也需要解绑.
            mBoundPlugins.add(plugin)
            resourceScope?.track(plugin, "plugin service ${componentName.packageName}")
            context.applicationContext.bindService(intent, plugin, Context.BIND_AUTO_CREATE)
        } else {
            val service = UnsupportedConnection().asBinder()
            plugin.onServiceConnected(componentName, service)
        }
    }

    private fun getScriptCacheDir(packageName: String): File {
        return File(mPluginCacheDir, packageName + File.separator).apply { mkdirs() }
    }

    fun clear() {
        deleteRecursively(mPluginCacheDir)
    }

    /**
     * Unbinds the plugin services bound by this script when it exits (docs/memory/ROADMAP.md H8). Until a connection is
     * unbound, the system keeps it, together with the plugin instance and the script runtime the plugin was loaded for.
     * zh-CN: 脚本结束时解绑本脚本绑定的插件服务 (docs/memory/ROADMAP.md H8). 连接解绑之前, 系统会一直持有它, 连同插件实例及其加载时所属的脚本运行时.
     */
    fun unbindAll() {
        mPlugins.clear()
        val bound = mBoundPlugins.toList()
        mBoundPlugins.clear()
        bound.forEach { plugin ->
            runCatching { context.applicationContext.unbindService(plugin) }
        }
    }

    private fun loadInstalledPackage(packageName: String) = try {
        val applicationInfo = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val ctx = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
        Plugin.Package(ctx, applicationInfo, true)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @JvmRecord
    data class PluginRuntime(val topLevelScope: TopLevelScope, val pluginSearchDir: String, val engine: String) {
        fun createScopedAppContext(hostContext: Context, selfContext: Context?) = ScopedAppContext(hostContext, selfContext)
    }

    class ScopedAppContext(private val hostContext: Context, selfContext: Context?) : ContextWrapper(selfContext) {
        override fun getApplicationContext() = this

        override fun getCacheDir(): File = hostContext.cacheDir

        override fun getDataDir(): File = hostContext.dataDir

        override fun getDatabasePath(name: String): File = hostContext.getDatabasePath(name)

        override fun getFilesDir(): File = hostContext.filesDir
    }

    private inner class UnsupportedConnection : ServiceProxy(null), IRemoteCall {
        override fun call(action: String, args: Map<Any?, Any?>, callback: IRemoteCallback): Map<Any?, Any?> {
            throw UnsupportedOperationException(context.getString(R.string.error_unsupported_plugin_connection))
        }
    }

    open class ServiceProxy(private val iBinder: IBinder?) : IScriptServiceInterface {
        override fun asBinder(): IBinder? = iBinder

        @Throws(RemoteException::class)
        override fun call(executionConfig: ExecutionConfig?, b: Boolean): Int {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(1)
                executionConfig?.writeToParcel(data, 0)
                data.writeInt(if (b) 1 else 0)
                if (iBinder == null || !iBinder.transact(TRANSACTION_call, data, reply, 0)) {
                    // @Dubious by SuperMonster003 on Apr 2, 2024.
                    return TRANSACTION_EMPTY_RESULT
                }
                reply.readException()
                return reply.readInt()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }

    interface IScriptServiceInterface : IInterface {
        @Throws(RemoteException::class)
        fun call(executionConfig: ExecutionConfig?, b: Boolean): Int

    }

    interface IRemoteCallback : IInterface {
        @Throws(RemoteException::class)
        fun call(event: String, args: Map<Any?, Any?>): Map<Any?, Any?>

    }

    interface IRemoteCall : IInterface {
        @Throws(RemoteException::class)
        fun call(action: String, args: Map<Any?, Any?>, callback: IRemoteCallback): Map<Any?, Any?>

    }

    companion object {

        val DESCRIPTOR: String = IScriptServiceInterface::class.java.name

        private const val TRANSACTION_EMPTY_RESULT = 0

        @Suppress("ConstPropertyName")
        private const val TRANSACTION_call = IBinder.FIRST_CALL_TRANSACTION + 0

        private val packages = ConcurrentHashMap<String, Plugin.Package>()

    }

}
