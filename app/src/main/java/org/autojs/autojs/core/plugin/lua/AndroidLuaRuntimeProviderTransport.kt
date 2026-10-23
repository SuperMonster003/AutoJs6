package org.autojs.autojs.core.plugin.lua

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.lua.runtime.api.ILuaRuntimeProvider
import org.autojs.plugin.lua.runtime.api.LuaPluginActions
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object LuaRuntimePluginHostFactory {
    fun create(context: Context): LuaRuntimePluginHost = LuaRuntimePluginHost(
        transport = AndroidLuaRuntimeProviderTransport(context),
        environment = LuaRuntimeProviderEnvironment(
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            supportedProcessAbis = Build.SUPPORTED_ABIS.asList(),
            releaseBuild = !BuildConfig.DEBUG,
            allowUserApprovedProviders = true,
        ),
        requirement = LuaRuntimeProviderRequirement(
            hostCapabilities = LuaRuntimeHostCapabilities.ENABLED,
            requiredCapabilities = emptyList(),
        ),
    )
}

/**
 * Android PackageManager/Binder adapter for [LuaRuntimePluginHost].
 *
 * Package visibility explicitly declares both the common INFO and Lua runtime discovery actions.
 * Runtime lookup is additionally scoped to the package whose INFO service was verified. Every
 * probe and live runtime connection binds with an actionless Intent containing only the exact
 * component.
 */
internal class AndroidLuaRuntimeProviderTransport(
    context: Context,
    private val bindTimeoutMillis: Long = DEFAULT_BIND_TIMEOUT_MILLIS,
) : LuaRuntimeProviderTransport {
    private val applicationContext = context.applicationContext ?: context
    private val packageManager = applicationContext.packageManager

    init {
        require(bindTimeoutMillis in 1L..MAX_BIND_TIMEOUT_MILLIS) {
            "Lua runtime bind timeout is outside the host limit"
        }
    }

    override fun discover(runtimeAction: String, timeoutMillis: Long): Collection<LuaRuntimeProviderDescriptor> {
        if (runtimeAction != LuaRuntimeContract.SERVICE_ACTION) return emptyList()
        require(timeoutMillis in 1L..MAX_BIND_TIMEOUT_MILLIS) {
            "Lua runtime discovery timeout is outside the host limit"
        }
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        // INFO is the action of every AutoJs6 plugin, so only the packages that declare the Lua runtime action are read:
        // otherwise discovery binds the INFO service of each installed plugin and fails once more than
        // MAX_DISCOVERED_SERVICES plugins are installed (docs/memory-phase2/ROADMAP.md C39).
        // zh-CN: INFO 是所有 AutoJs6 插件共用的动作, 因此只读取声明了 Lua 运行时动作的包: 否则发现过程会绑定每个已安装插件的 INFO 服务,
        // 并在已安装插件多于 MAX_DISCOVERED_SERVICES 个时失败 (docs/memory-phase2/ROADMAP.md C39).
        val runtimePackages = queryServices(Intent(runtimeAction)).mapTo(HashSet()) { it.packageName }
        // MATCH_DISABLED_COMPONENTS lets us observe manifest defaults plus component overrides;
        // dormant services are not discovery candidates.
        val infoServices = queryServices(Intent(LuaPluginActions.INFO))
            .filter { it.packageName in runtimePackages }
            .filter(::isServiceEffectivelyEnabled)
            .sortedWith(compareBy<ServiceInfo>({ it.packageName }, { it.name }))
        require(infoServices.size <= MAX_DISCOVERED_SERVICES) { "Too many Lua plugin INFO services were discovered" }
        val descriptors = ArrayList<LuaRuntimeProviderDescriptor>()
        infoServices.forEach { infoService ->
            descriptors += descriptorsFromInfoService(
                infoService,
                runtimeAction,
                deadlineNanos,
            )
            remainingMillis(deadlineNanos)
            require(descriptors.size <= MAX_DISCOVERED_SERVICES) {
                "Too many Lua runtime services were discovered"
            }
        }
        return descriptors
    }

    override fun inspectExact(
        component: LuaRuntimeProviderComponent,
        timeoutMillis: Long,
    ): LuaRuntimeProviderDescriptor? {
        require(timeoutMillis in 1L..MAX_BIND_TIMEOUT_MILLIS) {
            "Lua runtime exact inspection timeout is outside the host limit"
        }
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val infoServices = queryServices(Intent(LuaPluginActions.INFO).setPackage(component.packageName))
            .filter(::isServiceEffectivelyEnabled)
            .sortedWith(compareBy<ServiceInfo>({ it.packageName }, { it.name }))
        require(infoServices.size <= MAX_DISCOVERED_SERVICES) {
            "Too many Lua plugin INFO services were discovered for the pinned package"
        }
        val descriptors = ArrayList<LuaRuntimeProviderDescriptor>()
        infoServices.forEach { infoService ->
            descriptors += descriptorsFromInfoService(
                infoService,
                LuaRuntimeContract.SERVICE_ACTION,
                deadlineNanos,
            )
            remainingMillis(deadlineNanos)
            require(descriptors.size <= MAX_DISCOVERED_SERVICES) {
                "Too many Lua runtime services were discovered for the pinned package"
            }
        }
        val descriptor = descriptors.filter { it.component == component }.singleOrNull() ?: return null
        // Keep the package UID/version/update/signer read last: INFO is another Binder endpoint and
        // must not leave a pre-bind package snapshot authoritative at source-dispatch time.
        val packageSnapshot = try {
            packageSnapshot(component.packageName)
        } catch (failure: Throwable) {
            failure.rethrowIfInterrupted()
            return null
        }
        remainingMillis(deadlineNanos)
        return descriptor.copy(
            applicationEnabled = packageSnapshot.enabled,
            packageUid = packageSnapshot.uid,
            versionCode = packageSnapshot.versionCode,
            lastUpdateTime = packageSnapshot.lastUpdateTime,
            signerSha256 = packageSnapshot.signerSha256,
            trustLevel = packageSnapshot.trustLevel,
            sameSignerAsHost = packageSnapshot.sameSignerAsHost,
        )
    }

    override fun bindExact(
        component: LuaRuntimeProviderComponent,
        timeoutMillis: Long,
    ): LuaRuntimeProviderBinding {
        require(timeoutMillis in 1L..bindTimeoutMillis) { "Lua runtime bind exceeds its phase budget" }
        val lease = ExactComponentBinderLease(
            applicationContext,
            component.toAndroidComponent(),
            timeoutMillis,
        )
        return transferOrClose(lease) { acquired ->
            acquired.bindAndAwait()
            AndroidLuaRuntimeProviderBinding(acquired, component, timeoutMillis)
        }
    }

    private fun descriptorsFromInfoService(
        infoService: ServiceInfo,
        runtimeAction: String,
        deadlineNanos: Long,
    ): List<LuaRuntimeProviderDescriptor> {
        val packageName = infoService.packageName
        val packageSnapshot = try {
            packageSnapshot(packageName)
        } catch (failure: Throwable) {
            failure.rethrowIfInterrupted()
            return emptyList()
        }
        remainingMillis(deadlineNanos)
        if (!packageSnapshot.enabled || (!packageSnapshot.authorized && !packageSnapshot.sameSignerAsHost)) {
            return emptyList()
        }
        if (!infoService.exported || !isServiceEffectivelyEnabled(infoService) ||
            infoService.permission != LuaRuntimeContract.PLUGIN_PERMISSION
        ) {
            return emptyList()
        }
        val pluginInfo = try {
            readPluginInfo(
                LuaRuntimeProviderComponent(packageName, infoService.name),
                deadlineNanos,
            )
        } catch (failure: Throwable) {
            failure.rethrowIfInterrupted()
            if (failure is LuaBinderCallException) throw failure
            return emptyList()
        }

        val runtimeIntent = Intent(runtimeAction).setPackage(packageName)
        return queryServices(runtimeIntent)
            .filter(::isServiceEffectivelyEnabled)
            .map { runtimeService ->
                LuaRuntimeProviderDescriptor(
                    component = LuaRuntimeProviderComponent(packageName, runtimeService.name),
                    infoAction = LuaPluginActions.INFO,
                    runtimeAction = runtimeAction,
                    pluginId = pluginInfo.id.orEmpty(),
                    engine = pluginInfo.engine.orEmpty(),
                    variant = pluginInfo.variant.orEmpty(),
                    infoVersionCode = pluginInfo.versionCode,
                    infoSupportedAbis = pluginInfo.supportedAbis.orEmpty().asList(),
                    exported = runtimeService.exported,
                    applicationEnabled = packageSnapshot.enabled,
                    serviceEnabled = isServiceEffectivelyEnabled(runtimeService),
                    requiredPermission = runtimeService.permission,
                    packageUid = packageSnapshot.uid,
                    versionCode = packageSnapshot.versionCode,
                    lastUpdateTime = packageSnapshot.lastUpdateTime,
                    signerSha256 = packageSnapshot.signerSha256,
                    trustLevel = packageSnapshot.trustLevel,
                    sameSignerAsHost = packageSnapshot.sameSignerAsHost,
                )
            }.also { remainingMillis(deadlineNanos) }
    }

    private fun readPluginInfo(
        component: LuaRuntimeProviderComponent,
        deadlineNanos: Long,
    ): PluginInfo {
        val lease = ExactComponentBinderLease(
            applicationContext,
            component.toAndroidComponent(),
            remainingMillis(deadlineNanos),
        )
        return try {
            val binder = lease.bindAndAwait().requireAliveBinder()
            val provider = IPluginInfoProvider.Stub.asInterface(binder)
                ?: throw IllegalStateException("Lua plugin INFO service returned a null provider")
            LuaRuntimeBinderCallLane.call(
                operationName = "plugin-info",
                timeoutMillis = remainingMillis(deadlineNanos),
            ) { provider.info }
                ?: throw IllegalStateException("Lua plugin INFO service returned null metadata")
        } finally {
            lease.close()
        }
    }

    private fun packageSnapshot(packageName: String): PackageSnapshot {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        val trust = PluginTrustManager.resolveTrustInfo(applicationContext, packageName)
        val sameSignerAsHost = packageManager.checkSignatures(applicationContext.packageName, packageName) ==
            PackageManager.SIGNATURE_MATCH
        return PackageSnapshot(
            uid = applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signerSha256 = trust.fingerprintsSha256,
            trustLevel = when (trust.authorizedState) {
                PluginAuthorizedState.OFFICIAL -> LuaRuntimeProviderTrustLevel.OFFICIAL
                PluginAuthorizedState.TRUSTED,
                PluginAuthorizedState.USER_GRANTED,
                -> LuaRuntimeProviderTrustLevel.USER_APPROVED
                PluginAuthorizedState.REQUIRED -> LuaRuntimeProviderTrustLevel.UNTRUSTED
            },
            sameSignerAsHost = sameSignerAsHost,
            authorized = trust.authorizedState != PluginAuthorizedState.REQUIRED,
            enabled = applicationInfo.enabled &&
                PluginEnableStore.isEnabled(applicationContext, packageName, defaultEnabled = true),
        )
    }

    private fun queryServices(intent: Intent): List<ServiceInfo> {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, flags.toInt())
        }
        return resolved.mapNotNull { it.serviceInfo }
    }

    private fun isServiceEffectivelyEnabled(serviceInfo: ServiceInfo): Boolean =
        isLuaServiceEffectivelyEnabled(
            manifestEnabled = serviceInfo.enabled,
            componentEnabledSetting = packageManager.getComponentEnabledSetting(
                ComponentName(serviceInfo.packageName, serviceInfo.name),
            ),
        )

    private fun remainingMillis(deadlineNanos: Long): Long {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) {
            throw LuaBinderCallException(LuaBinderCallFailure.TIMEOUT, "plugin-discovery")
        }
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
    }

    private data class PackageSnapshot(
        val uid: Int,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val signerSha256: List<String>,
        val trustLevel: LuaRuntimeProviderTrustLevel,
        val sameSignerAsHost: Boolean,
        val authorized: Boolean,
        val enabled: Boolean,
    )

    private companion object {
        const val DEFAULT_BIND_TIMEOUT_MILLIS = 15_000L
        const val MAX_BIND_TIMEOUT_MILLIS = 60_000L
        const val MAX_DISCOVERED_SERVICES = 16
    }
}

/**
 * [ServiceInfo.enabled] is the manifest default. PackageManager component overrides are stored
 * separately, so discovery must combine both values when MATCH_DISABLED_COMPONENTS is used.
 */
internal fun isLuaServiceEffectivelyEnabled(
    manifestEnabled: Boolean,
    componentEnabledSetting: Int,
): Boolean = when (componentEnabledSetting) {
    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestEnabled
    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
    -> false
    else -> false
}

private class AndroidLuaRuntimeProviderBinding(
    private val lease: ExactComponentBinderLease,
    override val component: LuaRuntimeProviderComponent,
    private val callTimeoutMillis: Long,
) : LuaRuntimeProviderBinding {
    override val provider: ILuaRuntimeProvider
        get() = ILuaRuntimeProvider.Stub.asInterface(lease.requireAliveBinder())
            ?: throw IllegalStateException("Lua runtime service returned a null provider")

    override fun readRuntimeInfo(timeoutMillis: Long): ByteArray {
        require(timeoutMillis in 1L..callTimeoutMillis) { "Lua runtime-info call exceeds its phase budget" }
        val remote = provider
        return LuaRuntimeBinderCallLane.call(
            operationName = "runtime-info",
            timeoutMillis = timeoutMillis,
        ) { remote.getRuntimeInfo() }
    }

    override fun close() = lease.close()
}

/** One exact actionless service binding. It is intentionally never pooled or retried. */
private class ExactComponentBinderLease(
    private val context: Context,
    private val expectedComponent: ComponentName,
    private val timeoutMillis: Long,
) : ServiceConnection, AutoCloseable {
    private val completion = CountDownLatch(1)
    private val registered = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val unbound = AtomicBoolean(false)
    private val binder = AtomicReference<IBinder?>()
    private val deathRecipient = AtomicReference<IBinder.DeathRecipient?>()
    private val failure = AtomicReference<Throwable?>()
    private val connectionDelivered = AtomicBoolean(false)
    private val binderLock = Any()

    fun bindAndAwait(): ExactComponentBinderLease {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Synchronous Lua runtime binding must not block the Android main thread"
        }
        // A refused binding, or one that threw, has already been unbound (docs/memory-phase2/ROADMAP.md C11, C43).
        // zh-CN: 被拒绝或抛出异常的绑定已经解绑 (docs/memory-phase2/ROADMAP.md C11, C43).
        val didBind = PluginAutoWake.bindServiceWaking(
            context,
            Intent().setComponent(expectedComponent),
            this,
            Context.BIND_AUTO_CREATE,
        )
        if (!didBind) throw IllegalStateException("Unable to bind exact Lua runtime component $expectedComponent")
        registered.set(true)
        unbindIfClosed()
        if (!completion.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            close()
            throw IllegalStateException("Timed out binding exact Lua runtime component $expectedComponent")
        }
        failure.get()?.let { throw IllegalStateException("Lua runtime binding failed: $expectedComponent", it) }
        requireAliveBinder()
        return this
    }

    fun requireAliveBinder(): IBinder {
        failure.get()?.let { throw IllegalStateException("Lua runtime binding is no longer valid", it) }
        check(!closed.get()) { "Lua runtime binding is closed" }
        return checkNotNull(binder.get()) { "Lua runtime binder is unavailable" }.also { value ->
            check(value.isBinderAlive) { "Lua runtime binder is dead" }
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        if (closed.get()) return
        if (!connectionDelivered.compareAndSet(false, true)) {
            fail(SecurityException("Lua runtime delivered more than one service connection"))
            return
        }
        if (name != expectedComponent) {
            fail(SecurityException("Lua runtime connected from an unexpected component"))
            return
        }
        val recipient = IBinder.DeathRecipient {
            fail(IllegalStateException("Lua runtime binder died: $expectedComponent"))
        }
        try {
            service.linkToDeath(recipient, 0)
            val accepted = synchronized(binderLock) {
                if (closed.get()) {
                    false
                } else {
                    binder.set(service)
                    deathRecipient.set(recipient)
                    true
                }
            }
            if (!accepted) {
                runCatching { service.unlinkToDeath(recipient, 0) }
                return
            }
            completion.countDown()
        } catch (error: Throwable) {
            fail(error)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        fail(IllegalStateException("Lua runtime service disconnected: $name"))
    }

    override fun onBindingDied(name: ComponentName) {
        fail(IllegalStateException("Lua runtime binding died: $name"))
    }

    override fun onNullBinding(name: ComponentName) {
        fail(IllegalStateException("Lua runtime returned a null binding: $name"))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val (value, recipient) = synchronized(binderLock) {
            binder.getAndSet(null) to deathRecipient.getAndSet(null)
        }
        if (value != null && recipient != null) runCatching { value.unlinkToDeath(recipient, 0) }
        completion.countDown()
        unbindIfClosed()
    }

    private fun fail(error: Throwable) {
        failure.compareAndSet(null, error)
        completion.countDown()
    }

    private fun unbindIfClosed() {
        if (closed.get() && registered.get() && unbound.compareAndSet(false, true)) {
            runCatching { context.unbindService(this) }
        }
    }
}

private fun LuaRuntimeProviderComponent.toAndroidComponent(): ComponentName =
    ComponentName(packageName, className)
