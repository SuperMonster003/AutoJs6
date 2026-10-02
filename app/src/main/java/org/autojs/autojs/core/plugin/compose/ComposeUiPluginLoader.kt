package org.autojs.autojs.core.plugin.compose

import android.content.Context
import android.os.Build
import android.os.Process
import dalvik.system.PathClassLoader
import org.autojs.plugin.compose.api.ComposeUiCapabilityKeys
import org.autojs.plugin.compose.api.ComposeUiContract
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.loading.ComposeUiRendererFactory
import java.util.zip.ZipFile

class ComposeUiPluginLoader {
    data class Package(val name: String, val version: Long, val updated: Long, val apk: String, val factoryName: String)
    data class Loaded(
        val identity: Package,
        val factory: ComposeUiRendererFactory,
        val loader: ClassLoader,
        val pluginContext: Context,
        val nativePath: String,
        val components: Set<String>,
    ) {
        fun context(base: Context) = ComposeHostContext(base, pluginContext, loader)
    }

    internal fun load(context: Context, identity: Package, fingerprint: String): Loaded = synchronized(codeCache) {
        // JNI libraries belong to a ClassLoader. Share the same loader across script/Activity owners.
        codeCache[identity]?.let { return@synchronized it }
        loadUncached(context, identity, fingerprint).also { loaded ->
            codeCache.keys.removeAll { it.name == identity.name }
            codeCache[identity] = loaded
        }
    }

    private fun loadUncached(context: Context, identity: Package, fingerprint: String): Loaded {
        try {
            val abis = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
            val abi = ZipFile(identity.apk).use { zip -> abis.firstOrNull { zip.getEntry("lib/$it/libandroidx.graphics.path.so") != null } }
                ?: throw ComposeUiError(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE)
            val nativePath = "${identity.apk}!/lib/$abi"
            val loader = PathClassLoader(identity.apk, nativePath, ComposeUiRendererFactory::class.java.classLoader)
            val factory = Class.forName(identity.factoryName, true, loader).getDeclaredConstructor().newInstance() as? ComposeUiRendererFactory
                ?: throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
            // Check the method before reading a capability Bundle from an incompatible provider.
            if (!ComposeUiContract.supports(factory.contractVersion())) throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
            val capabilities = factory.capabilities()
            if (capabilities.getInt(ComposeUiCapabilityKeys.CONTRACT_VERSION, -1) != factory.contractVersion()) {
                throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
            }
            ComposeUiPluginSelection.afterLoad(factory.contractVersion(), { capabilities.getString(ComposeUiCapabilityKeys.SHARED_DEPS_FINGERPRINT) }, fingerprint)
                ?.let { throw ComposeUiError.selection(context, it) }
            val components = capabilities.getStringArrayList(ComposeUiCapabilityKeys.COMPONENTS)?.toSet()
                ?: throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
            if (components.isEmpty() || components.any { ComponentCatalog.V1.component(it) == null } ||
                capabilities.getString(ComposeUiCapabilityKeys.COMPOSE_VERSION).isNullOrBlank() ||
                capabilities.getStringArrayList(ComposeUiCapabilityKeys.FEATURES) == null) {
                throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
            }
            return Loaded(identity, factory, loader, context.createPackageContext(identity.name, 0), nativePath, components)
        } catch (error: ComposeUiError) { throw error }
        catch (error: Exception) { throw ComposeUiError(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE,
            ComposeUiError.selection(context, SelectionFailure.CONTRACT).message!!, cause = error) }
        catch (error: LinkageError) { throw ComposeUiError(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE,
            ComposeUiError.selection(context, SelectionFailure.CONTRACT).message!!, cause = error) }
    }

    companion object {
        private val codeCache = HashMap<Package, Loaded>()
        internal fun invalidatePackage(name: String) = synchronized(codeCache) { codeCache.keys.removeAll { it.name == name } }
    }
}
