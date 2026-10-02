package org.autojs.autojs.core.plugin.compose

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import dalvik.system.PathClassLoader
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.compose.api.spike.ComposeUiRendererFactory
import org.autojs.plugin.compose.api.spike.SpikeContract
import java.util.zip.ZipFile

/** Instrumentation-only probe. P1 implements the production loader against the frozen V1 API. */
class ComposeSpikeLoader(private val context: Context) {
    enum class Error { NOT_INSTALLED, SYSTEM_DISABLED, NOT_ENABLED, NOT_AUTHORIZED, HOST_TOO_OLD, FACTORY_MISSING, CONTRACT_MISMATCH, LOAD_FAILED }
    class Failure(val code: Error, cause: Throwable? = null) : RuntimeException(code.name, cause)
    data class Loaded(val factory: ComposeUiRendererFactory, val loader: ClassLoader, val pluginContext: Context, val nativePath: String) {
        fun context(base: Context) = ComposeHostContext(base, pluginContext, loader)
    }

    fun load(): Loaded {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = try {
            pm.getApplicationInfo(SpikeContract.PACKAGE, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) { throw Failure(Error.NOT_INSTALLED) }
        val centerEnabled = info.enabled && PluginEnableStore.isEnabled(context, info.packageName)
        val authorized = centerEnabled && PluginTrustManager.isAuthorized(context, info.packageName)
        checkEligibility(true, info.enabled, centerEnabled, authorized)
        val minimum = info.metaData?.getInt(SpikeContract.HOST_VERSION_META, Int.MAX_VALUE) ?: Int.MAX_VALUE
        checkHostVersion(BuildConfig.VERSION_CODE.toLong(), minimum.toLong())
        @Suppress("DEPRECATION")
        val services = pm.queryIntentServices(
            Intent(SpikeContract.INFO_ACTION).addCategory(SpikeContract.INFO_CATEGORY).setPackage(info.packageName), 0,
        )
        if (services.size != 1) throw Failure(Error.FACTORY_MISSING)
        val name = info.metaData?.getString(SpikeContract.FACTORY_META) ?: throw Failure(Error.FACTORY_MISSING)
        val abis = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
        val abi = ZipFile(info.sourceDir).use { zip ->
            abis.firstOrNull { zip.getEntry("lib/$it/libandroidx.graphics.path.so") != null }
        } ?: throw Failure(Error.LOAD_FAILED)
        val nativePath = "${info.sourceDir}!/lib/$abi"
        // The draft contract now exists only in instrumentation; its loader delegates host classes to the target.
        val loader = PathClassLoader(info.sourceDir, nativePath, ComposeUiRendererFactory::class.java.classLoader)
        val factory = instantiate(name, loader)
        checkContract(factory.contractVersion())
        return Loaded(factory, loader, context.createPackageContext(info.packageName, 0), nativePath)
    }

    companion object {
        /** The ordered gate is independently testable without changing installed apps or trust grants. */
        fun checkEligibility(installed: Boolean, systemEnabled: Boolean, centerEnabled: Boolean, authorized: Boolean) {
            val error = when {
                !installed -> Error.NOT_INSTALLED
                !systemEnabled -> Error.SYSTEM_DISABLED
                !centerEnabled -> Error.NOT_ENABLED
                !authorized -> Error.NOT_AUTHORIZED
                else -> null
            }
            if (error != null) throw Failure(error)
        }
        fun checkHostVersion(actual: Long, required: Long) {
            if (actual < required) throw Failure(Error.HOST_TOO_OLD)
        }
        fun checkContract(version: Int) {
            if (version != SpikeContract.VERSION) throw Failure(Error.CONTRACT_MISMATCH)
        }
        fun instantiate(name: String, loader: ClassLoader): ComposeUiRendererFactory = try {
            Class.forName(name, true, loader).getDeclaredConstructor().newInstance() as? ComposeUiRendererFactory
                ?: throw Failure(Error.CONTRACT_MISMATCH)
        } catch (failure: Failure) {
            throw failure
        } catch (failure: ClassNotFoundException) {
            throw Failure(Error.FACTORY_MISSING, failure)
        } catch (failure: ReflectiveOperationException) {
            throw Failure(Error.LOAD_FAILED, failure)
        } catch (failure: LinkageError) {
            throw Failure(Error.LOAD_FAILED, failure)
        }
    }
}
