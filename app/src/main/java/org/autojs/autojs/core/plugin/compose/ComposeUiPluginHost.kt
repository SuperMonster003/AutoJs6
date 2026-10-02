package org.autojs.autojs.core.plugin.compose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.compose.api.ComposeUiContract
import org.autojs.plugin.compose.api.ComposeUiIds

/** One host per owner. Cached code has no Activity references; gates are checked on EVERY lookup. */
class ComposeUiPluginHost(context: Context) : AutoCloseable {
    private val context = context.applicationContext
    private val loader = ComposeUiPluginLoader()
    private var cached: ComposeUiPluginLoader.Loaded? = null
    private var closed = false
    private val changed: (String) -> Unit = { if (it == ComposeUiIds.PLUGIN_PACKAGE_NAME) invalidate() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { name ->
                // Component/system enablement changes do not replace APK code or its JNI owner.
                if (intent.action != Intent.ACTION_PACKAGE_CHANGED) ComposeUiPluginLoader.invalidatePackage(name)
                changed(name)
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        ContextCompat.registerReceiver(this.context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        PluginStateChangeRegistry.addListener(changed)
    }

    @Synchronized fun load(): ComposeUiPluginLoader.Loaded {
        check(!closed)
        val inventory = AndroidInventory(context)
        ComposeUiPluginSelection.beforeLoad(inventory, BuildConfig.VERSION_CODE.toLong())?.let {
            cached = null
            throw ComposeUiError.selection(context, it)
        }
        val identity = inventory.identity()
        cached?.takeIf { it.identity == identity }?.let { return it }
        cached = null
        return loader.load(context, identity, BuildConfig.COMPOSE_UI_SHARED_FINGERPRINT).also { cached = it }
    }

    fun isAvailable(): Boolean = try { load(); true } catch (_: ComposeUiError) { false }
    @Synchronized internal fun invalidate() { cached = null }
    @Synchronized override fun close() {
        if (closed) return
        closed = true; cached = null
        PluginStateChangeRegistry.removeListener(changed)
        context.unregisterReceiver(receiver)
    }

    @Suppress("DEPRECATION")
    private class AndroidInventory(private val context: Context) : ComposePluginInventory {
        private val pm = context.packageManager
        private var info: PackageInfo? = null
        override fun installed(): Boolean {
            info = try { pm.getPackageInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS) }
            catch (_: PackageManager.NameNotFoundException) { null }
            return info != null
        }
        override fun systemEnabled() = info!!.applicationInfo!!.enabled
        override fun centerEnabled() = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME,
            PluginDefaultEnabledPolicy.isEnabledByDefault(ComposeUiIds.ENGINE))
        override fun authorized() = PluginTrustManager.isAuthorized(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        override fun requiredHostVersion() = (info!!.applicationInfo!!.metaData?.get(ComposeUiContract.META_REQUIRES_HOST_VERSION) as? Number)?.toLong() ?: -1
        override fun hasFactoryAndInfoService(): Boolean {
            if (info!!.applicationInfo!!.metaData?.getString(ComposeUiContract.META_RENDERER_FACTORY).isNullOrBlank()) return false
            val services = pm.queryIntentServices(Intent(ComposeUiContract.INFO_ACTION).addCategory(ComposeUiContract.INFO_CATEGORY)
                .setPackage(ComposeUiIds.PLUGIN_PACKAGE_NAME), 0)
            return services.size == 1 && services.single().serviceInfo.let {
                it.enabled && it.exported && it.permission == ComposeUiContract.PLUGIN_PERMISSION
            }
        }
        fun identity() = info!!.let { pkg -> ComposeUiPluginLoader.Package(pkg.packageName, PackageInfoCompat.getLongVersionCode(pkg),
            pkg.lastUpdateTime, pkg.applicationInfo!!.sourceDir, pkg.applicationInfo!!.metaData.getString(ComposeUiContract.META_RENDERER_FACTORY)!!) }
    }
}
