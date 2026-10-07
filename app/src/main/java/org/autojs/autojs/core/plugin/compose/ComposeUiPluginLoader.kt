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
import org.autojs.plugin.compose.api.interop.AndroidViewInteropV1
import org.autojs.plugin.compose.api.interop.AndroidViewRendererFactoryV1
import org.autojs.plugin.compose.api.dialog.DialogInteropV1
import org.autojs.plugin.compose.api.dialog.DialogRendererFactoryV1
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
        val androidViewFactory: AndroidViewRendererFactoryV1? = null,
        val dialogFactory: DialogRendererFactoryV1? = null,
    ) {
        fun context(base: Context) = ComposeHostContext(base, pluginContext, loader)
    }

    internal fun load(context: Context, identity: Package, fingerprint: String): Loaded =
        loadCachedIdentity(codeCache, identity) { loadUncached(context, identity, fingerprint) }

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
            if (!validComponents(factory.contractVersion(), components) ||
                capabilities.getString(ComposeUiCapabilityKeys.COMPOSE_VERSION).isNullOrBlank() ||
                capabilities.getStringArrayList(ComposeUiCapabilityKeys.FEATURES) == null) {
                throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
            }
            val interop = if (AndroidViewInteropV1.FEATURE in capabilities.getStringArrayList(ComposeUiCapabilityKeys.FEATURES)!!) {
                val name = capabilities.getString(AndroidViewInteropV1.FACTORY_CLASS_KEY)
                    ?: throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
                val extension = Class.forName(name, true, loader).getDeclaredConstructor().newInstance() as? AndroidViewRendererFactoryV1
                    ?: throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
                if (extension.extensionVersion() != AndroidViewInteropV1.VERSION) throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
                extension
            } else null
            val dialog = if (DialogInteropV1.FEATURE in capabilities.getStringArrayList(ComposeUiCapabilityKeys.FEATURES)!!) {
                val name = capabilities.getString(DialogInteropV1.FACTORY_CLASS_KEY)
                    ?: throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
                val extension = Class.forName(name, true, loader).getDeclaredConstructor().newInstance() as? DialogRendererFactoryV1
                    ?: throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
                if (extension.extensionVersion() != DialogInteropV1.VERSION) throw ComposeUiError.selection(context, SelectionFailure.CONTRACT)
                extension
            } else null
            return Loaded(identity, factory, loader, context.createPackageContext(identity.name, 0), nativePath,
                if (interop == null) components else components + AndroidViewInteropV1.COMPONENT, interop, dialog)
        } catch (error: ComposeUiError) { throw error }
        catch (error: Exception) { throw ComposeUiError(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE,
            ComposeUiError.selection(context, SelectionFailure.CONTRACT).message!!, cause = error) }
        catch (error: LinkageError) { throw ComposeUiError(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE,
            ComposeUiError.selection(context, SelectionFailure.CONTRACT).message!!, cause = error) }
    }

    companion object {
        /** A V1 provider may only advertise V1 nodes; newer hosts still load its original subset. */
        internal fun validComponents(version: Int, components: Set<String>): Boolean {
            val catalog = when (version) {
                1 -> ComponentCatalog.V1
                org.autojs.plugin.compose.api.v2.ComposeUiV2.VERSION -> org.autojs.plugin.compose.api.v2.ComposeUiV2.catalog
                else -> return false
            }
            return components.isNotEmpty() && components.all { catalog.component(it) != null }
        }
        private val codeCache = HashMap<Package, Loaded>()

        // JNI libraries belong to one ClassLoader. A caller may have read an old PackageManager
        // identity before another caller loaded the new APK, so insertion must not evict siblings.
        // Host refresh owns pruning through a snapshot taken before its package inventory query.
        internal fun <T : Any> loadCachedIdentity(cache: MutableMap<Package, T>, identity: Package, create: () -> T): T =
            synchronized(cache) { cache.getOrPut(identity, create) }

        internal fun cachedPackages(name: String): Set<Package> = synchronized(codeCache) {
            codeCache.keys.filter { it.name == name }.toSet()
        }
        internal fun invalidatePackage(name: String, keep: Package? = null, observed: Set<Package>? = null) = synchronized(codeCache) {
            codeCache.keys.removeAll { it.name == name && it != keep && (observed == null || it in observed) }
        }
    }
}
