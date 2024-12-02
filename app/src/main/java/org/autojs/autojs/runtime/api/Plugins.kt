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
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.pio.PFiles.copyAssetDir
import org.autojs.autojs.pio.PFiles.deleteRecursively
import org.autojs.autojs.rhino.TopLevelScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by SuperMonster003 on Apr 2, 2024.
 * Transformed by SuperMonster003 on Apr 2, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Apr 2, 2024.
class Plugins(private val context: Context, private val runtime: PluginRuntime) {

    private val mPluginCacheDir = File(context.cacheDir, "plugin-scripts/")

    private val mPlugins = ConcurrentHashMap<String, Plugin>()

    fun load(packageName: String): Plugin {
        mPlugins[packageName]?.let { return it }

        var packageContext = packages[packageName] ?: loadInstalledPackage(packageName) ?: throw Resources.NotFoundException(
            // "Plugin $packageName not found in installed apps or directory ${File(runtime.pluginSearchDir)}"
            "Plugin $packageName not found in installed apps"
        )
        packages.putIfAbsent(packageName, packageContext)?.let { packageContext = it }

        val lock = Object()
        synchronized(lock) {
            try {
                val plugin = Plugin.load(context, packageContext, runtime, runtime.topLevelScope)
                val scriptCacheDir = getScriptCacheDir(packageName)
                copyAssetDir(packageContext.context.assets, plugin.assetsScriptDir, scriptCacheDir.path, null)
                plugin.mainScriptPath = File(scriptCacheDir, "index.js").path
                bindService(plugin)
                mPlugins[packageName] = plugin
                lock.notify()
                return plugin
            } catch (e: Exception) {
                lock.notify()
                throw PluginLoadException(e)
            }
        }
    }

    private fun bindService(plugin: Plugin) {
        if (plugin.version < 2) return
        val componentName = plugin.componentName ?: return
        if (plugin.pkg.installed) {
            val intent = Intent().apply { setComponent(componentName) }
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

    private fun loadInstalledPackage(packageName: String) = try {
        val applicationInfo = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val ctx = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
        Plugin.Package(ctx, applicationInfo, true)
    } catch (e: PackageManager.NameNotFoundException) {
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

    private class UnsupportedConnection : ServiceProxy(null), IRemoteCall {
        override fun call(action: String, args: Map<Any?, Any?>, callback: IRemoteCallback): Map<Any?, Any?> {
            throw UnsupportedOperationException("Unsupported plugin connection")
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
