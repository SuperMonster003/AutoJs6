package org.autojs.autojs.core.plugin

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.IBinder
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.extension.ArrayExtensions.toHashCode
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.Plugins.PluginRuntime
import java.lang.reflect.Method

/**
 * Created by SuperMonster003 on Apr 2, 2024.
 * Transformed by SuperMonster003 on Apr 2, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Apr 2, 2024.
class Plugin(private val pluginInstance: ServiceConnection, val pkg: Package) : ServiceConnection {

    @ScriptInterface
    lateinit var mainScriptPath: String

    private val mGetVersion: Method
    private val mGetScriptDir: Method
    private val mGetService: Method?
    private val mOnServiceConnected: Method?
    private val mOnServiceDisconnected: Method?

    val version: Int
        get() {
            try {
                return mGetVersion.invoke(pluginInstance) as? Int ?: 0
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }
        }

    val componentName: ComponentName?
        get() {
            try {
                return mGetService?.invoke(pluginInstance) as? ComponentName
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

    val assetsScriptDir: String
        get() {
            try {
                return mGetScriptDir.invoke(pluginInstance) as String
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

    init {
        mGetVersion = getMethod("getVersion")
        mGetScriptDir = getMethod("getAssetsScriptDir")
        mGetService = getMethodOrNull("getService")
        mOnServiceConnected = getMethodOrNull("onServiceConnected", ComponentName::class.java, IBinder::class.java)
        mOnServiceDisconnected = getMethodOrNull("onServiceDisconnected", ComponentName::class.java)
    }

    override fun onServiceConnected(componentName: ComponentName, service: IBinder?) {
        try {
            mOnServiceConnected?.invoke(pluginInstance, componentName, service)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        try {
            mOnServiceDisconnected?.invoke(pluginInstance, componentName)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @ScriptInterface
    fun unwrap() = pluginInstance

    private fun getMethod(s: String, vararg classes: Class<*>): Method {
        try {
            return pluginInstance.javaClass.getMethod(s, *classes)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        }
    }

    private fun getMethodOrNull(s: String, vararg classes: Class<*>) = try {
        getMethod(s, *classes)
    } catch (ex: Exception) {
        null.also { ex.printStackTrace() }
    }

    @JvmRecord
    data class Package(val context: Context, val applicationInfo: ApplicationInfo, val installed: Boolean) {

        override fun equals(other: Any?) = when {
            this === other -> true
            other !is Package -> false
            else -> context == other.context && applicationInfo == other.applicationInfo && installed == other.installed
        }

        override fun toString() = "Package(context=$context, applicationInfo=$applicationInfo, installed=$installed)"

        override fun hashCode(): Int {
            // @Hint by SuperMonster003 on Oct 25, 2024.
            //  ! We've got magic. :)
            //  # var result = context.hashCode()
            //  # result = 31 * result + applicationInfo.hashCode()
            //  # result = 31 * result + installed.hashCode()
            //  # return result
            return listOf(context, applicationInfo, installed).toHashCode()
        }

    }

    class PluginLoadException : RuntimeException {
        constructor(cause: Throwable?) : super(cause)

        constructor(message: String?) : super(message)
    }

    companion object {

        private const val KEY_REGISTRY = "org.autojs.plugin.sdk.registry"

        fun load(context: Context?, pkg: Package, runtime: PluginRuntime?, scope: TopLevelScope?): Plugin {
            try {
                val registryClass = pkg.applicationInfo.metaData.getString(KEY_REGISTRY) ?: throw PluginLoadException("No registry in metadata")
                val pluginClass = Class.forName(registryClass, true, pkg.context.classLoader)
                val loadDefault = pluginClass.getMethod("loadDefault", Context::class.java, Context::class.java, Any::class.java, Any::class.java)
                val pluginInstance = loadDefault.invoke(null, context, pkg.context, runtime, scope) ?: throw Exception("Plugin instance must be non-null")
                pluginInstance as? ServiceConnection ?: throw Exception("Plugin instance must be type of android.content.ServiceConnection")
                return create(pluginInstance, pkg)
            } catch (e: Throwable) {
                e.printStackTrace()
                throw PluginLoadException(e)
            }
        }

        private fun create(pluginInstance: ServiceConnection, pkg: Package) = Plugin(pluginInstance, pkg)

    }

}
