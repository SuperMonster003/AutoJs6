package org.autojs.autojs.core.plugin.python

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.python.runtime.api.IPythonRuntimeProvider
import org.autojs.plugin.python.runtime.api.PythonRuntimeCapabilities
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeInfo
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AndroidTest-only adapter used by the Python Runtime conformance instrumentation.
 *
 * Discovery is action based, but the Binder handshake is deliberately component-only. The
 * complete PackageManager identity is captured before binding and compared again after the
 * connection arrives, so a package replacement cannot silently inherit an accepted binding.
 */
internal class AndroidPythonRuntimeConformanceTransport(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    fun discover(): List<AndroidPythonRuntimeProviderIdentitySnapshot> =
        queryRuntimeServices().mapNotNull { serviceInfo ->
            runCatching { snapshot(serviceInfo) }.getOrNull()
        }

    fun inspectExact(
        component: PythonRuntimeProviderComponent,
    ): AndroidPythonRuntimeProviderIdentitySnapshot? =
        queryRuntimeServices()
            .firstOrNull {
                it.packageName == component.packageName &&
                    it.name == component.className
            }
            ?.let { serviceInfo -> runCatching { snapshot(serviceInfo) }.getOrNull() }

    fun bindExact(
        expected: AndroidPythonRuntimeProviderIdentitySnapshot,
        callback: AndroidPythonRuntimeBindingCallback,
    ): AndroidPythonRuntimeBinding = ExactBinding(expected, callback).also(ExactBinding::bind)

    private fun queryRuntimeServices(): List<ServiceInfo> {
        val discoveryIntent = Intent(PythonRuntimeContract.RUNTIME_ACTION).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(
                discoveryIntent,
                PackageManager.ResolveInfoFlags.of(flags),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(discoveryIntent, flags.toInt())
        }
        return resolved.mapNotNull { it.serviceInfo }
    }

    private fun snapshot(serviceInfo: ServiceInfo): AndroidPythonRuntimeProviderIdentitySnapshot {
        val packageName = serviceInfo.packageName
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(
                    PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        val trustInfo = PluginTrustManager.resolveTrustInfo(applicationContext, packageName)
        val trustLevel = when (trustInfo.authorizedState) {
            PluginAuthorizedState.OFFICIAL -> PythonRuntimeProviderTrustLevel.OFFICIAL
            PluginAuthorizedState.TRUSTED,
            PluginAuthorizedState.USER_GRANTED,
            -> PythonRuntimeProviderTrustLevel.USER_APPROVED
            PluginAuthorizedState.REQUIRED -> PythonRuntimeProviderTrustLevel.UNTRUSTED
        }
        return AndroidPythonRuntimeProviderIdentitySnapshot(
            component = PythonRuntimeProviderComponent(packageName, serviceInfo.name),
            declaredAction = PythonRuntimeContract.RUNTIME_ACTION,
            exported = serviceInfo.exported,
            applicationEnabled = applicationInfo.enabled,
            serviceEnabled = serviceInfo.enabled,
            requiredPermission = serviceInfo.permission,
            packageUid = applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signerSha256 = trustInfo.fingerprintsSha256
                .map { it.lowercase() }
                .distinct()
                .sorted(),
            trustLevel = trustLevel,
            sameSignerAsHost = packageManager.checkSignatures(
                applicationContext.packageName,
                packageName,
            ) == PackageManager.SIGNATURE_MATCH,
        )
    }

    private inner class ExactBinding(
        private val expected: AndroidPythonRuntimeProviderIdentitySnapshot,
        private val callback: AndroidPythonRuntimeBindingCallback,
    ) : AndroidPythonRuntimeBinding, ServiceConnection {
        override val intentAction: String? = null

        private val closed = AtomicBoolean(false)
        private val registered = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val connectionDelivered = AtomicBoolean(false)
        private val providerDelivered = AtomicBoolean(false)
        private val deathLock = Any()
        private var linkedBinder: IBinder? = null
        private var linkedRecipient: IBinder.DeathRecipient? = null

        fun bind() {
            check(inspectExact(expected.component) == expected) {
                "Python Runtime provider identity changed before binding"
            }
            val intent = Intent().setComponent(expected.component.toAndroidComponent())
            check(intent.action == intentAction) {
                "Python Runtime conformance binding must not carry a discovery action"
            }
            val didBind = applicationContext.bindService(intent, this, Context.BIND_AUTO_CREATE)
            if (!didBind) throw IllegalStateException("Unable to bind the exact Python Runtime component")
            registered.set(true)
            unbindIfReady()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (!connectionDelivered.compareAndSet(false, true)) {
                failAndClose(SecurityException("Python Runtime delivered more than one connection"))
                return
            }
            if (name != expected.component.toAndroidComponent()) {
                failAndClose(SecurityException("Python Runtime connected from an unexpected component"))
                return
            }
            if (inspectExact(expected.component) != expected) {
                failAndClose(SecurityException("Python Runtime provider identity changed while binding"))
                return
            }
            val recipient = IBinder.DeathRecipient {
                reportBinderDeath()
            }
            try {
                service.linkToDeath(recipient, 0)
                synchronized(deathLock) {
                    if (closed.get()) {
                        runCatching { service.unlinkToDeath(recipient, 0) }
                        return
                    }
                    linkedBinder = service
                    linkedRecipient = recipient
                }
                providerDelivered.set(true)
                callback.onConnected(IPythonRuntimeProvider.Stub.asInterface(service))
            } catch (error: Throwable) {
                failAndClose(error)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (closed.get()) return
            if (providerDelivered.get()) {
                reportBinderDeath()
            } else {
                failAndClose(null)
            }
        }

        override fun onBindingDied(name: ComponentName) {
            reportBinderDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) {
                failAndClose(IllegalStateException("Python Runtime returned a null binding"))
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            clearDeathLink(unlink = true)
            unbindIfReady()
        }

        private fun failAndClose(cause: Throwable?) {
            if (!closed.compareAndSet(false, true)) return
            clearDeathLink(unlink = true)
            unbindIfReady()
            callback.onBindingFailure(cause)
        }

        private fun reportBinderDeath() {
            if (!closed.compareAndSet(false, true)) return
            clearDeathLink(unlink = true)
            unbindIfReady()
            callback.onBinderDeath()
        }

        private fun clearDeathLink(unlink: Boolean) {
            synchronized(deathLock) {
                val binder = linkedBinder
                val recipient = linkedRecipient
                linkedBinder = null
                linkedRecipient = null
                if (unlink && binder != null && recipient != null) {
                    runCatching { binder.unlinkToDeath(recipient, 0) }
                }
            }
        }

        private fun unbindIfReady() {
            if (closed.get() && registered.get() && unbound.compareAndSet(false, true)) {
                runCatching { applicationContext.unbindService(this) }
            }
        }
    }
}

internal data class AndroidPythonRuntimeProviderIdentitySnapshot(
    val component: PythonRuntimeProviderComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val signerSha256: List<String>,
    val trustLevel: PythonRuntimeProviderTrustLevel,
    val sameSignerAsHost: Boolean,
) {
    fun toCandidate(
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
    ): PythonRuntimeProviderCandidate = PythonRuntimeProviderCandidate(
        component = component,
        declaredAction = declaredAction,
        exported = exported,
        applicationEnabled = applicationEnabled,
        serviceEnabled = serviceEnabled,
        requiredPermission = requiredPermission,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        signerSha256 = signerSha256,
        trustLevel = trustLevel,
        sameSignerAsHost = sameSignerAsHost,
        runtimeInfo = runtimeInfo,
        capabilities = capabilities,
    )
}

internal interface AndroidPythonRuntimeBinding : Closeable {
    /** Always null: discovery actions are never reused for the explicit Binder handshake. */
    val intentAction: String?
}

internal interface AndroidPythonRuntimeBindingCallback {
    fun onConnected(provider: IPythonRuntimeProvider)
    fun onBindingFailure(cause: Throwable?)
    fun onBinderDeath()
}

private fun PythonRuntimeProviderComponent.toAndroidComponent(): ComponentName =
    ComponentName(packageName, className)
