package org.autojs.autojs.core.plugin.r8

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.plugin.r8compiler.api.IR8CompilerCallback
import org.autojs.plugin.r8compiler.api.IR8CompilerProvider
import org.autojs.plugin.r8compiler.api.IR8CompilerSession
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import java.util.concurrent.atomic.AtomicBoolean

/** PackageManager and AIDL adapter dedicated to the default-off, same-signer R8 path. */
internal class AndroidR8CompilerTransport(
    context: Context,
) : R8CompilerTransport {

    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    override fun discoverExactAction(action: String): List<R8CompilerServiceCandidate> {
        if (action != R8CompilerContract.SERVICE_ACTION) return emptyList()
        return queryExactAction().mapNotNull { serviceInfo ->
            runCatching { candidate(serviceInfo) }.getOrNull()
        }
    }

    override fun inspectExact(component: R8CompilerComponent): R8CompilerServiceCandidate? =
        queryExactAction()
            .firstOrNull { it.packageName == component.packageName && it.name == component.className }
            ?.let { serviceInfo -> runCatching { candidate(serviceInfo) }.getOrNull() }

    override fun bindExact(
        identity: R8CompilerPinnedIdentity,
        callback: R8CompilerBindingCallback,
    ): R8CompilerBinding = ExactBinding(identity, callback).also(ExactBinding::bind)

    private fun queryExactAction(): List<ServiceInfo> {
        val intent = Intent(R8CompilerContract.SERVICE_ACTION).apply {
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

    private fun candidate(serviceInfo: ServiceInfo): R8CompilerServiceCandidate {
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
        val signerSha256 = PluginSignatureUtils.getSha256Fingerprints(
            applicationContext,
            packageName,
        ).toSet()
        return R8CompilerServiceCandidate(
            component = R8CompilerComponent(packageName, serviceInfo.name),
            declaredAction = R8CompilerContract.SERVICE_ACTION,
            exported = serviceInfo.exported,
            applicationEnabled = applicationInfo.enabled &&
                PluginEnableStore.isEnabled(applicationContext, packageName, true),
            serviceEnabled = serviceInfo.enabled,
            requiredPermission = serviceInfo.permission,
            packageUid = applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signerSha256 = signerSha256,
            sameSignerAsHost = packageManager.checkSignatures(
                applicationContext.packageName,
                packageName,
            ) == PackageManager.SIGNATURE_MATCH,
        )
    }

    private inner class ExactBinding(
        private val identity: R8CompilerPinnedIdentity,
        private val callback: R8CompilerBindingCallback,
    ) : R8CompilerBinding, ServiceConnection {
        private val closed = AtomicBoolean(false)
        private val registered = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val connectionDelivered = AtomicBoolean(false)
        private val deathLock = Any()
        private var linkedBinder: IBinder? = null
        private var linkedRecipient: IBinder.DeathRecipient? = null

        fun bind() {
            val intent = Intent(R8CompilerContract.SERVICE_ACTION)
                .setComponent(identity.component.toAndroidComponent())
            val didBind = PluginAutoWake.bindServiceWaking(applicationContext, intent, this, Context.BIND_AUTO_CREATE)
            check(didBind) { "Unable to bind the exact R8 compiler component" }
            registered.set(true)
            unbindIfReady()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (!connectionDelivered.compareAndSet(false, true)) {
                callback.onBindingFailure(SecurityException("R8 compiler delivered multiple connections"))
                return
            }
            if (name != identity.component.toAndroidComponent()) {
                callback.onBindingFailure(SecurityException("R8 compiler component changed while binding"))
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
                        provider = IR8CompilerProvider.Stub.asInterface(service),
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
                callback.onBindingFailure(IllegalStateException("R8 compiler returned a null binding"))
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(deathLock) {
                val binder = linkedBinder
                val recipient = linkedRecipient
                linkedBinder = null
                linkedRecipient = null
                if (binder != null && recipient != null) {
                    runCatching { binder.unlinkToDeath(recipient, 0) }
                }
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
        private val provider: IR8CompilerProvider,
        private val pinnedPackageUid: Int,
    ) : R8CompilerRemoteConnection {
        override fun getCompilerInfo(): ByteArray = provider.compilerInfo

        override fun getCapabilities(): ByteArray = provider.capabilities

        override fun getRetraceCapabilities(): ByteArray = provider.retraceCapabilities

        override fun openSession(
            encodedRequest: ByteArray,
            inputBundleDescriptor: R8CompilerOwnedDescriptor,
            outputBundleDescriptor: R8CompilerOwnedDescriptor,
            callback: R8CompilerRemoteCallback,
        ): R8CompilerRemoteSession = openRemoteSession(
            inputBundleDescriptor,
            outputBundleDescriptor,
            callback,
        ) { input, output, remoteCallback ->
            provider.openSession(encodedRequest, input, output, remoteCallback)
        }

        override fun openRetraceSession(
            encodedRequest: ByteArray,
            inputBundleDescriptor: R8CompilerOwnedDescriptor,
            outputStackTraceDescriptor: R8CompilerOwnedDescriptor,
            callback: R8CompilerRemoteCallback,
        ): R8CompilerRemoteSession = openRemoteSession(
            inputBundleDescriptor,
            outputStackTraceDescriptor,
            callback,
        ) { input, output, remoteCallback ->
            provider.openRetraceSession(encodedRequest, input, output, remoteCallback)
        }

        private fun openRemoteSession(
            inputBundleDescriptor: R8CompilerOwnedDescriptor,
            outputDescriptor: R8CompilerOwnedDescriptor,
            callback: R8CompilerRemoteCallback,
            open: (
                ParcelFileDescriptor,
                ParcelFileDescriptor,
                IR8CompilerCallback,
            ) -> IR8CompilerSession?,
        ): R8CompilerRemoteSession {
            val inputBundle = (inputBundleDescriptor as? AndroidR8CompilerDescriptor)?.descriptor
                ?: throw IllegalArgumentException("R8 input bundle descriptor is not an Android PFD")
            val output = (outputDescriptor as? AndroidR8CompilerDescriptor)?.descriptor
                ?: throw IllegalArgumentException("R8 output descriptor is not an Android PFD")
            val remoteCallback = object : IR8CompilerCallback.Stub() {
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
            val session = open(inputBundle, output, remoteCallback)
                ?: throw IllegalStateException("R8 provider returned a null session")
            return object : R8CompilerRemoteSession {
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

        private inline fun fromPinnedUid(callback: R8CompilerRemoteCallback, action: () -> Unit) {
            val callingUid = Binder.getCallingUid()
            if (!R8CompilerTransportCallerPolicy.isExpectedUid(pinnedPackageUid, callingUid)) {
                callback.onTransportViolation(
                    SecurityException(
                        "R8 compiler callback UID $callingUid differs from pinned UID $pinnedPackageUid",
                    ),
                )
                return
            }
            action()
        }
    }
}

private fun R8CompilerComponent.toAndroidComponent(): ComponentName =
    ComponentName(packageName, className)
