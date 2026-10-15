package org.autojs.autojs.core.plugin.jvm

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
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback
import org.autojs.plugin.jvmsource.api.IJvmSourceCallback
import org.autojs.plugin.jvmsource.api.IJvmSourceProvider
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class AndroidJvmSourceTransport(context: Context) : JvmSourceTransport {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    override fun discoverExactAction(action: String): List<JvmSourceServiceCandidate> {
        if (action != JvmSourceContract.SERVICE_ACTION) return emptyList()
        return queryDedicatedServices().mapNotNull { info -> runCatching { candidate(info) }.getOrNull() }
    }

    override fun inspectExact(component: JvmSourceComponent): JvmSourceServiceCandidate? =
        queryDedicatedServices()
            .firstOrNull { it.packageName == component.packageName && it.name == component.className }
            ?.let { info -> runCatching { candidate(info) }.getOrNull() }

    override fun bindExact(
        identity: JvmSourcePinnedIdentity,
        callback: JvmSourceBindingCallback,
    ): JvmSourceBinding = ExactBinding(identity, callback).also(ExactBinding::bind)

    private fun queryDedicatedServices(): List<ServiceInfo> {
        val intent = Intent(JvmSourceContract.SERVICE_ACTION).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        val flags = (PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.GET_META_DATA).toLong()
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, flags.toInt())
        }
        return resolved.mapNotNull { it.serviceInfo }
    }

    private fun candidate(serviceInfo: ServiceInfo): JvmSourceServiceCandidate {
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
        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(applicationContext, packageName).toSet()
        val sameSigner = packageManager.checkSignatures(applicationContext.packageName, packageName) ==
            PackageManager.SIGNATURE_MATCH
        val declaredLanguage = serviceInfo.metaData?.let { metadata ->
            if (!metadata.containsKey(JvmSourceProviderLanguagePolicy.MANIFEST_KEY)) {
                null
            } else {
                val wireName = metadata.getString(JvmSourceProviderLanguagePolicy.MANIFEST_KEY)
                JvmSourceLanguage.entries.firstOrNull { it.wireName == wireName }
                    ?: error("JVM source service declares an invalid language")
            }
        }
        return JvmSourceServiceCandidate(
            component = JvmSourceComponent(packageName, serviceInfo.name),
            declaredAction = JvmSourceContract.SERVICE_ACTION,
            exported = serviceInfo.exported,
            applicationEnabled = applicationInfo.enabled,
            serviceEnabled = serviceInfo.enabled,
            pluginEnabled = PluginEnableStore.isEnabled(applicationContext, packageName, true),
            // R1 compiler/executor authority is stronger than ordinary plugin authorization.
            // Persisted selection is a second gate, not a substitute for same-signer trust.
            providerAuthorized = JvmSourceCompilerTrustPolicy.isAuthorized(sameSigner),
            requiredPermission = serviceInfo.permission,
            compilerProcessName = serviceInfo.processName,
            packageUid = applicationInfo.uid,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            lastUpdateTime = packageInfo.lastUpdateTime,
            signerSha256 = fingerprints,
            declaredLanguage = declaredLanguage,
        )
    }

    private inner class ExactBinding(
        private val identity: JvmSourcePinnedIdentity,
        private val callback: JvmSourceBindingCallback,
    ) : JvmSourceBinding, ServiceConnection {
        private val linkState = JvmSourceExactBindingLinkState<IBinder, IBinder.DeathRecipient>()
        private val registered = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val connected = AtomicBoolean(false)

        fun bind() {
            val component = identity.component.toAndroid()
            val intent = Intent(JvmSourceContract.SERVICE_ACTION).setComponent(component)
            check(PluginAutoWake.bindServiceWaking(applicationContext, intent, this, Context.BIND_AUTO_CREATE)) {
                "Unable to bind exact JVM source provider"
            }
            registered.set(true)
            unbindIfClosed()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (linkState.isClosed()) return
            if (!connected.compareAndSet(false, true)) {
                if (linkState.reserveDeliveryIfOpen()) {
                    callback.onBindingFailure(SecurityException("Provider delivered multiple connections"))
                }
                return
            }
            if (name != identity.component.toAndroid()) {
                if (linkState.reserveDeliveryIfOpen()) {
                    callback.onBindingFailure(SecurityException("Provider connected from an unexpected component"))
                }
                return
            }
            val recipient = IBinder.DeathRecipient {
                if (linkState.reserveDeliveryIfOpen()) {
                    callback.onBinderDeath(JvmSourceProviderTransportLoss.BINDER_DIED)
                }
            }
            try {
                service.linkToDeath(recipient, 0)
                val published = linkState.publish(service, recipient)
                if (published) {
                    callback.onConnected(
                        AndroidRemoteConnection(
                            provider = IJvmSourceProvider.Stub.asInterface(service),
                            identity = identity,
                        ),
                    )
                } else {
                    runCatching { service.unlinkToDeath(recipient, 0) }
                }
            } catch (error: Throwable) {
                if (linkState.reserveDeliveryIfOpen()) callback.onBindingFailure(error)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // Package lifecycle changes can surface through any of these callbacks. Preserve the
            // transport signal as a terminal cause; the next run performs full identity revalidation.
            if (linkState.reserveDeliveryIfOpen()) {
                callback.onBinderDeath(JvmSourceProviderTransportLoss.SERVICE_DISCONNECTED)
            }
        }

        override fun onBindingDied(name: ComponentName) {
            if (linkState.reserveDeliveryIfOpen()) {
                callback.onBinderDeath(JvmSourceProviderTransportLoss.BINDING_DIED)
            }
        }

        override fun onNullBinding(name: ComponentName) {
            if (linkState.reserveDeliveryIfOpen()) {
                callback.onBindingFailure(IllegalStateException("Provider returned a null binding"))
            }
        }

        override fun close() {
            val result = linkState.close()
            if (!result.newlyClosed) return
            result.publishedLink?.let { link ->
                runCatching { link.binder.unlinkToDeath(link.recipient, 0) }
            }
            unbindIfClosed()
        }

        private fun unbindIfClosed() {
            if (linkState.isClosed() && registered.get() && unbound.compareAndSet(false, true)) {
                runCatching { applicationContext.unbindService(this) }
            }
        }
    }

    private class AndroidRemoteConnection(
        private val provider: IJvmSourceProvider,
        private val identity: JvmSourcePinnedIdentity,
    ) : JvmSourceRemoteConnection {
        private val compilerPid = AtomicInteger(0)

        override fun getProviderInfo(): ByteArray = provider.providerInfo

        override fun getCapabilities(): ByteArray = provider.capabilities

        override fun openSession(
            encodedRequest: ByteArray,
            sourceDescriptor: JvmSourceOwnedDescriptor,
            stdoutDescriptor: JvmSourceOwnedDescriptor,
            stderrDescriptor: JvmSourceOwnedDescriptor,
            hostBridge: JvmSourceHostBridgeEndpoint,
            callback: JvmSourceRemoteCallback,
        ): JvmSourceRemoteSession {
            val source = sourceDescriptor.androidDescriptor("source")
            val stdout = stdoutDescriptor.androidDescriptor("stdout")
            val stderr = stderrDescriptor.androidDescriptor("stderr")
            val bridgeState = BridgeCallerState()
            val remoteCallback = object : IJvmSourceCallback.Stub() {
                override fun onStarted(metadata: ByteArray) = fromPinnedUid(callback) { callingPid, callingUid ->
                    val copied = metadata.copyOf()
                    val started = try {
                        JvmSourceCodec.decodeStarted(copied)
                    } catch (error: Throwable) {
                        callback.onTransportViolation(error)
                        return@fromPinnedUid
                    }
                    if (started.compilerPid != callingPid ||
                        started.compilerUid != callingUid ||
                        started.compilerProcessName != identity.compilerProcessName ||
                        !compilerPid.compareAndSet(0, callingPid)
                    ) {
                        callback.onTransportViolation(
                            SecurityException("Started compiler identity differs from the pinned Binder caller"),
                        )
                        return@fromPinnedUid
                    }
                    callback.onStarted(copied)
                }

                override fun onDiagnostic(diagnostic: ByteArray) = fromPinnedCompiler(callback) {
                    callback.onDiagnostic(diagnostic.copyOf())
                }

                override fun onCompleted(result: ByteArray) = fromPinnedCompiler(callback) {
                    val copied = result.copyOf()
                    try {
                        JvmSourceCodec.decodeResult(copied)
                    } catch (error: Throwable) {
                        callback.onTransportViolation(error)
                        return@fromPinnedCompiler
                    }
                    val bridgeCallers = bridgeState.complete()
                    if (bridgeCallers == null || bridgeCallers.any { it != compilerPid.get() }) {
                        callback.onTransportViolation(
                            SecurityException("Host bridge caller PID differs from the pinned compiler proxy PID"),
                        )
                        return@fromPinnedCompiler
                    }
                    callback.onCompleted(copied, bridgeCallers)
                }

                override fun onFailed(error: ByteArray) = fromPinnedCompiler(callback) {
                    bridgeState.terminate()
                    callback.onFailed(error.copyOf())
                }

                override fun onCancelled(cancellation: ByteArray) = fromPinnedCompiler(callback) {
                    bridgeState.terminate()
                    callback.onCancelled(cancellation.copyOf())
                }
            }
            val remoteBridge = object : IJvmHostBridge.Stub() {
                override fun dispatch(request: ByteArray, response: IJvmHostBridgeCallback) =
                    fromPinnedUid(callback) { callingPid, _ ->
                        if (compilerPid.get() <= 0 || callingPid != compilerPid.get()) {
                            callback.onTransportViolation(
                                SecurityException("Host bridge dispatch did not originate from the pinned compiler proxy"),
                            )
                            return@fromPinnedUid
                        }
                        if (!bridgeState.beginDispatch(callingPid)) {
                            callback.onTransportViolation(SecurityException("Host bridge call arrived after terminal state"))
                            return@fromPinnedUid
                        }
                        hostBridge.dispatch(request.copyOf()) { encoded ->
                            bridgeState.finishDispatch()
                            response.onResponse(encoded)
                        }
                    }

                override fun destroy(reason: ByteArray) = fromPinnedUid(callback) { callingPid, _ ->
                    if (compilerPid.get() <= 0 || callingPid != compilerPid.get()) {
                        callback.onTransportViolation(
                            SecurityException("Host bridge destroy did not originate from the pinned compiler proxy"),
                        )
                        return@fromPinnedUid
                    }
                    if (!bridgeState.recordDestroy(callingPid)) {
                        callback.onTransportViolation(SecurityException("Host bridge destroy arrived after terminal state"))
                        return@fromPinnedUid
                    }
                    hostBridge.destroy(reason.copyOf())
                }
            }
            val session = provider.openSession(
                encodedRequest,
                source,
                stdout,
                stderr,
                remoteBridge,
                remoteCallback,
            ) ?: error("Provider returned a null JVM source session")
            return object : JvmSourceRemoteSession {
                private val cancelled = AtomicBoolean(false)
                private val closed = AtomicBoolean(false)

                override fun cancel() {
                    if (cancelled.compareAndSet(false, true)) session.cancel()
                }

                override fun close() {
                    if (closed.compareAndSet(false, true)) session.close()
                }
            }
        }

        private inline fun fromPinnedCompiler(callback: JvmSourceRemoteCallback, action: () -> Unit) =
            fromPinnedUid(callback) { callingPid, _ ->
                if (compilerPid.get() != callingPid) {
                    callback.onTransportViolation(
                        SecurityException("JVM source callback did not originate from the pinned compiler process"),
                    )
                    return@fromPinnedUid
                }
                action()
            }

        private inline fun fromPinnedUid(
            callback: JvmSourceRemoteCallback,
            action: (callingPid: Int, callingUid: Int) -> Unit,
        ) {
            val callingUid = Binder.getCallingUid()
            if (callingUid != identity.packageUid) {
                callback.onTransportViolation(
                    SecurityException(
                        "JVM source Binder caller UID $callingUid differs from pinned UID ${identity.packageUid}",
                    ),
                )
                return
            }
            action(Binder.getCallingPid(), callingUid)
        }

        private fun JvmSourceOwnedDescriptor.androidDescriptor(label: String) =
            (this as? AndroidJvmSourceDescriptor)?.descriptor
                ?: throw IllegalArgumentException("$label descriptor is not an Android PFD")
    }

    private class BridgeCallerState {
        private val callerPids = linkedSetOf<Int>()
        private var pendingDispatches = 0
        private var terminal = false

        @Synchronized
        fun beginDispatch(callingPid: Int): Boolean {
            if (terminal) return false
            callerPids += callingPid
            pendingDispatches++
            return true
        }

        @Synchronized
        fun finishDispatch() {
            check(pendingDispatches > 0)
            pendingDispatches--
        }

        @Synchronized
        fun recordDestroy(callingPid: Int): Boolean {
            if (terminal) return false
            callerPids += callingPid
            return true
        }

        @Synchronized
        fun complete(): Set<Int>? {
            if (terminal) return null
            terminal = true
            if (pendingDispatches != 0) return null
            return callerPids.toSet()
        }

        @Synchronized
        fun terminate() {
            terminal = true
        }
    }
}

private fun JvmSourceComponent.toAndroid(): ComponentName = ComponentName(packageName, className)
