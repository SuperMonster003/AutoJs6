package org.autojs.autojs.core.plugin.dex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.IDexCompilerCallback
import org.autojs.plugin.dexcompiler.api.IDexCompilerProvider
import java.util.concurrent.atomic.AtomicBoolean

/** Dedicated PackageManager and AIDL transport for the opt-in DEX compiler experiment. */
internal class AndroidDexCompilerExperimentalTransport(
    context: Context,
) : DexCompilerExperimentalTransport {

    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    override fun discoverExactAction(action: String): List<DexCompilerServiceCandidate> {
        if (action != DexCompilerContract.SERVICE_ACTION) return emptyList()
        return queryExactAction().mapNotNull { serviceInfo ->
            runCatching { candidate(serviceInfo) }.getOrNull()
        }
    }

    override fun inspectExact(component: DexCompilerComponent): DexCompilerServiceCandidate? =
        queryExactAction()
            .firstOrNull { it.packageName == component.packageName && it.name == component.className }
            ?.let { serviceInfo -> runCatching { candidate(serviceInfo) }.getOrNull() }

    override fun bindExact(
        identity: DexCompilerPinnedIdentity,
        callback: DexCompilerBindingCallback,
    ): DexCompilerBinding = ExactBinding(identity, callback).also(ExactBinding::bind)

    private fun queryExactAction(): List<ServiceInfo> {
        val intent = Intent(DexCompilerContract.SERVICE_ACTION).apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, flags.toInt())
        }
        return resolved.mapNotNull { it.serviceInfo }
    }

    private fun candidate(serviceInfo: ServiceInfo): DexCompilerServiceCandidate {
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
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        val signerSha256 = PluginSignatureUtils.getSha256Fingerprints(applicationContext, packageName).toSet()
        return DexCompilerServiceCandidate(
            component = DexCompilerComponent(packageName, serviceInfo.name),
            declaredAction = DexCompilerContract.SERVICE_ACTION,
            exported = serviceInfo.exported,
            applicationEnabled = applicationInfo.enabled &&
                PluginEnableStore.isEnabled(applicationContext, packageName, true),
            serviceEnabled = serviceInfo.enabled,
            requiredPermission = serviceInfo.permission,
            packageUid = applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signerSha256 = signerSha256,
            trustLevel = if (signerSha256.any { it in PluginTrustManager.OFFICIAL_SHA_256 }) {
                DexCompilerTrustLevel.OFFICIAL
            } else {
                DexCompilerTrustLevel.REQUIRED
            },
            sameSignerAsHost = packageManager.checkSignatures(applicationContext.packageName, packageName) ==
                PackageManager.SIGNATURE_MATCH,
        )
    }

    private inner class ExactBinding(
        private val identity: DexCompilerPinnedIdentity,
        private val callback: DexCompilerBindingCallback,
    ) : DexCompilerBinding, ServiceConnection {
        private val closed = AtomicBoolean(false)
        private val registered = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val connectionDelivered = AtomicBoolean(false)
        private val deathLock = Any()
        private var linkedBinder: IBinder? = null
        private var linkedRecipient: IBinder.DeathRecipient? = null

        fun bind() {
            val component = identity.component.toAndroidComponent()
            val intent = Intent(DexCompilerContract.SERVICE_ACTION).setComponent(component)
            val didBind = PluginAutoWake.bindServiceWaking(applicationContext, intent, this, Context.BIND_AUTO_CREATE)
            if (!didBind) throw IllegalStateException("Unable to bind the exact DEX compiler component")
            registered.set(true)
            unbindIfReady()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (!connectionDelivered.compareAndSet(false, true)) {
                callback.onBindingFailure(SecurityException("DEX compiler delivered more than one connection"))
                return
            }
            if (name != identity.component.toAndroidComponent()) {
                callback.onBindingFailure(SecurityException("DEX compiler connected from an unexpected component"))
                return
            }
            val recipient = IBinder.DeathRecipient(callback::onBinderDeath)
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
                callback.onConnected(
                    AndroidRemoteConnection(
                        provider = IDexCompilerProvider.Stub.asInterface(service),
                        pinnedPackageUid = identity.packageUid,
                    ),
                )
            } catch (error: Throwable) {
                callback.onBindingFailure(error)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) callback.onBindingFailure(null)
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) callback.onBinderDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(IllegalStateException("DEX compiler returned a null binding"))
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(deathLock) {
                val binder = linkedBinder
                val recipient = linkedRecipient
                linkedBinder = null
                linkedRecipient = null
                if (binder != null && recipient != null) runCatching { binder.unlinkToDeath(recipient, 0) }
            }
            unbindIfReady()
        }

        private fun unbindIfReady() {
            if (closed.get() && registered.get() && unbound.compareAndSet(false, true)) {
                runCatching { applicationContext.unbindService(this) }
            }
        }
    }

    private class AndroidRemoteConnection(
        private val provider: IDexCompilerProvider,
        private val pinnedPackageUid: Int,
    ) : DexCompilerRemoteConnection {
        override fun getCompilerInfo(): ByteArray = provider.compilerInfo

        override fun getCapabilities(): ByteArray = provider.capabilities

        override fun openSession(
            encodedRequest: ByteArray,
            programDescriptor: DexCompilerOwnedDescriptor,
            outputDescriptor: DexCompilerOwnedDescriptor,
            callback: DexCompilerRemoteCallback,
        ): DexCompilerRemoteSession {
            val program = (programDescriptor as? AndroidDexCompilerDescriptor)?.descriptor
                ?: throw IllegalArgumentException("Program descriptor is not an Android PFD")
            val output = (outputDescriptor as? AndroidDexCompilerDescriptor)?.descriptor
                ?: throw IllegalArgumentException("Output descriptor is not an Android PFD")
            val remoteCallback = object : IDexCompilerCallback.Stub() {
                override fun onStarted(metadata: ByteArray) = fromPinnedUid(callback) {
                    callback.onStarted(metadata)
                }

                override fun onProgress(progress: ByteArray) = fromPinnedUid(callback) {
                    callback.onProgress(progress)
                }

                override fun onCompleted(result: ByteArray) = fromPinnedUid(callback) {
                    callback.onCompleted(result)
                }

                override fun onFailed(error: ByteArray) = fromPinnedUid(callback) {
                    callback.onFailed(error)
                }

                override fun onCancelled(cancellation: ByteArray) = fromPinnedUid(callback) {
                    callback.onCancelled(cancellation)
                }
            }
            val session = provider.openSession(encodedRequest, program, output, remoteCallback)
                ?: throw IllegalStateException("DEX compiler returned a null session")
            return object : DexCompilerRemoteSession {
                private val closed = AtomicBoolean(false)
                private val cancelled = AtomicBoolean(false)

                override fun cancel() {
                    if (cancelled.compareAndSet(false, true)) session.cancel()
                }

                override fun close() {
                    if (closed.compareAndSet(false, true)) session.close()
                }
            }
        }

        private inline fun fromPinnedUid(callback: DexCompilerRemoteCallback, action: () -> Unit) {
            val callingUid = Binder.getCallingUid()
            if (!DexCompilerTransportCallerPolicy.isExpectedUid(pinnedPackageUid, callingUid)) {
                callback.onTransportViolation(
                    SecurityException("DEX compiler callback UID $callingUid differs from pinned UID $pinnedPackageUid"),
                )
                return
            }
            action()
        }
    }
}

private fun DexCompilerComponent.toAndroidComponent(): ComponentName = ComponentName(packageName, className)
