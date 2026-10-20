package org.autojs.autojs.core.accessibility.companion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings.Secure
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityServiceUsher
import org.autojs.autojs.core.accessibility.companion.AccessibilityCompanionPolicy.Companion
import org.autojs.autojs.core.accessibility.companion.AccessibilityCompanionPolicy.Decision
import org.autojs.autojs.core.accessibility.control.EnabledA11yServices
import org.autojs.autojs.core.accessibility.control.RootEnabledServicesStore
import org.autojs.autojs.core.accessibility.control.SecureEnabledServicesStore
import org.autojs.autojs.core.accessibility.control.ServiceStartStrategy
import org.autojs.autojs.core.accessibility.control.SettingsStoreStartStrategy
import org.autojs.autojs.core.accessibility.control.ShizukuEnabledServicesStore
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.StateListener
import org.autojs.autojs.core.accessibility.registry.ConnectionListeners
import org.autojs.autojs.core.plugin.center.PluginInfoServiceHost
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.pref.keys.A11yKeys
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.SettingsUtils
import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.AutoJs6AccessibilityCompanionContract as Contract
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host side of the accessibility companion contract. Whenever the AutoJs6 accessibility service
 * binds or unbinds, official companion plugins are discovered through their `org.autojs.plugin.INFO`
 * service (category [Contract.INFO_CATEGORY]); those that publish the `follow-host` policy are
 * attached to or detached from the enabled accessibility services with the same root, secure
 * settings and Shizuku strategies the host uses for itself, and every companion receives
 * [Contract.ACTION_HOST_SERVICE_STATE_CHANGED] so that it can re-check on its own.
 * zh-CN: 无障碍伴生契约的宿主侧. AutoJs6 无障碍服务绑定或解绑时, 通过 `org.autojs.plugin.INFO` 服务
 * (类别 [Contract.INFO_CATEGORY]) 发现官方伴生插件; 发布 `follow-host` 策略的伴生服务使用宿主自身的
 * root, 安全设置与 Shizuku 策略附加到或从已启用无障碍服务中分离, 并向每个伴生插件广播
 * [Contract.ACTION_HOST_SERVICE_STATE_CHANGED] 以便其自行复查.
 *
 * Created by SuperMonster003 on Sep 15, 2026.
 */
object AccessibilityCompanionSync {

    private const val TAG = "A11yCompanionSync"
    private const val LISTENER_OWNER = "AccessibilityCompanionSync"

    /** Binding changes come in bursts (restart is unbind then bind); one run covers the burst. */
    private const val DEBOUNCE_MILLIS = 600L

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "a11y-companion-sync").apply { isDaemon = true }
    }
    private val installed = AtomicBoolean(false)
    private val log: (String) -> Unit = { Log.d(TAG, it) }

    @Volatile
    private var pending: ScheduledFuture<*>? = null

    /** Registers the registry listener once and performs a first pass for the current state. */
    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        AccessibilityService.registry.addListener(LISTENER_OWNER, StateListener { previous, current ->
            if (ConnectionListeners.isBindingChanged(previous, current)) request(appContext, "host-binding")
        })
        request(appContext, "startup")
    }

    /** Debounced asynchronous pass; safe to call from any thread. */
    fun request(context: Context, trigger: String) {
        val appContext = context.applicationContext
        synchronized(this) {
            pending?.cancel(false)
            pending = executor.schedule({
                runCatching { syncNow(appContext, trigger) }
                    .onFailure { Log.w(TAG, "Companion sync triggered by $trigger failed", it) }
            }, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    /** One synchronous pass; blocks on plugin-info bindings and must not run on the main thread. */
    fun syncNow(context: Context, trigger: String) {
        val companions = discoverCompanions(context)
        if (companions.isEmpty()) return
        val hostServiceName = hostServiceName(context)
        val hostServiceEnabled = EnabledA11yServices.contains(readEnabledServices(context), hostServiceName)
        val hostServiceBound = AccessibilityService.registry.isBound
        companions.forEach { companion ->
            val companionEnabled = EnabledA11yServices.contains(readEnabledServices(context), companion.serviceComponent)
            val decision = AccessibilityCompanionPolicy.decide(companion, hostServiceEnabled, companionEnabled)
            val via = when (decision) {
                Decision.NONE -> null
                Decision.ATTACH -> strategies(context, companion.serviceComponent).firstOrNull { it.isAvailable() && it.enable() }?.name
                Decision.DETACH -> strategies(context, companion.serviceComponent).firstOrNull { it.isAvailable() && it.disable() }?.name
            }
            log("$trigger: ${companion.packageName} policy=${companion.policy} decision=$decision via=${via ?: "none"}")
            broadcastHostState(context, companion.packageName, hostServiceName, hostServiceEnabled, hostServiceBound)
        }
    }

    /** Official-signed packages exposing an INFO service in the companion category, with valid capabilities. */
    private fun discoverCompanions(context: Context): List<Companion> {
        val pm = context.packageManager
        val intent = Intent(PluginActions.INFO)
            .addCategory(Contract.INFO_CATEGORY)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }.mapNotNull { it.serviceInfo?.packageName }.distinct()
        return packages.mapNotNull { packageName ->
            if (!PluginTrustManager.resolveTrustInfo(context, packageName).isOfficial) {
                log("$packageName is not an official plugin; ignored")
                return@mapNotNull null
            }
            val info = runCatching { runBlocking { PluginInfoServiceHost.discover(context, packageName) } }
                .getOrElse { emptyList() }
                .firstNotNullOfOrNull { it.pluginInfo }
            val capabilities = info?.capabilities
            AccessibilityCompanionPolicy.companionOf(
                packageName = packageName,
                serviceComponent = capabilities?.getString(Contract.CAPABILITY_SERVICE_COMPONENT),
                policy = capabilities?.getString(Contract.CAPABILITY_SERVICE_POLICY),
            )
        }
    }

    private fun broadcastHostState(
        context: Context,
        packageName: String,
        hostServiceName: String,
        hostServiceEnabled: Boolean,
        hostServiceBound: Boolean,
    ) {
        val intent = Intent(Contract.ACTION_HOST_SERVICE_STATE_CHANGED)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(Contract.EXTRA_HOST_PACKAGE_NAME, context.packageName)
            .putExtra(Contract.EXTRA_HOST_SERVICE_COMPONENT, hostServiceName)
            .putExtra(Contract.EXTRA_HOST_SERVICE_ENABLED, hostServiceEnabled)
            .putExtra(Contract.EXTRA_HOST_SERVICE_BOUND, hostServiceBound)
        runCatching { context.sendBroadcast(intent, Contract.PLUGIN_PERMISSION) }
            .onFailure { Log.w(TAG, "Unable to notify $packageName", it) }
    }

    /** Same order and preference gates as the host's own controller. */
    private fun strategies(context: Context, serviceName: String): List<ServiceStartStrategy> {
        val resolver = context.contentResolver
        val readBack = { readEnabledServices(context) }
        return listOf(
            SettingsStoreStartStrategy(
                name = "root",
                serviceName = serviceName,
                store = RootEnabledServicesStore(log),
                available = { A11yKeys.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS.get() && RootUtils.isRootAvailable() },
                readBack = readBack,
                log = log,
            ),
            SettingsStoreStartStrategy(
                name = "secure settings",
                serviceName = serviceName,
                store = SecureEnabledServicesStore(resolver),
                available = { A11yKeys.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS.get() && SettingsUtils.SecureSettings.isGranted(context) },
                readBack = readBack,
                log = log,
            ),
            SettingsStoreStartStrategy(
                name = "shizuku",
                serviceName = serviceName,
                store = ShizukuEnabledServicesStore(context, log),
                available = { A11yKeys.ENABLE_A11Y_SERVICE_WITH_SHIZUKU.get() && WrappedShizuku.isOperational() },
                readBack = readBack,
                log = log,
            ),
        )
    }

    private fun hostServiceName(context: Context) = "${context.packageName}/${AccessibilityServiceUsher::class.java.name}"

    private fun readEnabledServices(context: Context): String? =
        Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES)

}
