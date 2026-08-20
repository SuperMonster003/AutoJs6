package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiProvider
import java.util.concurrent.atomic.AtomicBoolean

/** Exact-component Android Binder transport for the explicitly invoked metadata-only handshake. */
internal class AndroidOnDeviceAiProviderHandshake(
    context: Context,
) : OnDeviceAiProviderHandshakeTransport {
    private val applicationContext = context.applicationContext
    private val discovery = AndroidOnDeviceAiProviderDiscovery(applicationContext)

    override fun inspectExact(
        component: OnDeviceAiProviderComponent,
    ): OnDeviceAiProviderDiscoverySourceResult {
        requireBackgroundThread()
        return discovery.inspectExact(component)
    }

    override fun bindExact(
        snapshot: OnDeviceAiProviderPackageSnapshot,
        callback: OnDeviceAiProviderBindingCallback,
    ): OnDeviceAiProviderBinding {
        requireBackgroundThread()
        return ExactBinding(snapshot.component, callback).also(ExactBinding::bind)
    }

    override fun openMetadataRpc(
        connection: OnDeviceAiProviderRawConnection,
    ): OnDeviceAiProviderMetadataRpc {
        requireBackgroundThread()
        val raw = connection as? AndroidRawConnection
            ?: throw IllegalArgumentException("Connection does not belong to the Android on-device AI transport")
        if (!raw.binder.isBinderAlive) throw IllegalStateException("on-device AI provider Binder is not alive")
        val descriptor = raw.binder.interfaceDescriptor
        if (descriptor != IOnDeviceAiProvider.DESCRIPTOR) {
            throw OnDeviceAiProviderDescriptorMismatchException()
        }
        val provider = requireNotNull(IOnDeviceAiProvider.Stub.asInterface(raw.binder)) {
            "on-device AI provider Binder has no AIDL interface"
        }
        return AndroidMetadataRpc(provider)
    }

    private inner class ExactBinding(
        private val component: OnDeviceAiProviderComponent,
        private val callback: OnDeviceAiProviderBindingCallback,
    ) : OnDeviceAiProviderBinding, ServiceConnection {
        private val closed = AtomicBoolean(false)
        private val registered = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val connectionDelivered = AtomicBoolean(false)
        private val deathLock = Any()
        private var linkedBinder: IBinder? = null
        private var linkedRecipient: IBinder.DeathRecipient? = null

        fun bind() {
            val didBind = applicationContext.bindService(
                Intent().setComponent(component.toAndroidComponent()),
                this,
                Context.BIND_AUTO_CREATE,
            )
            if (!didBind) throw IllegalStateException("Unable to bind the exact on-device AI provider component")
            registered.set(true)
            unbindIfReady()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (!connectionDelivered.compareAndSet(false, true)) {
                callback.onBindingFailure(OnDeviceAiProviderHandshakeFailure.DUPLICATE_CONNECTION)
                return
            }
            if (name != component.toAndroidComponent()) {
                callback.onBindingFailure(OnDeviceAiProviderHandshakeFailure.UNEXPECTED_COMPONENT)
                return
            }
            val recipient = IBinder.DeathRecipient(callback::onBinderDeath)
            try {
                service.linkToDeath(recipient, 0)
                if (!service.isBinderAlive) {
                    runCatching { service.unlinkToDeath(recipient, 0) }
                    callback.onBinderDeath()
                    return
                }
                synchronized(deathLock) {
                    if (closed.get()) {
                        runCatching { service.unlinkToDeath(recipient, 0) }
                        return
                    }
                    linkedBinder = service
                    linkedRecipient = recipient
                }
                callback.onConnected(AndroidRawConnection(service))
            } catch (_: SecurityException) {
                callback.onBindingFailure(OnDeviceAiProviderHandshakeFailure.BIND_SECURITY)
            } catch (_: Exception) {
                callback.onBindingFailure(OnDeviceAiProviderHandshakeFailure.BINDER_DIED)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(OnDeviceAiProviderHandshakeFailure.DISCONNECTED)
            }
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) callback.onBinderDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(OnDeviceAiProviderHandshakeFailure.NULL_BINDING)
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

    private class AndroidRawConnection(
        val binder: IBinder,
    ) : OnDeviceAiProviderRawConnection

    private class AndroidMetadataRpc(
        private val provider: IOnDeviceAiProvider,
    ) : OnDeviceAiProviderMetadataRpc {
        override fun getProviderInfoEnvelope(): ByteArray? {
            requireBackgroundThread()
            return provider.getProviderInfo()
        }

        override fun getCapabilitiesEnvelope(): ByteArray? {
            requireBackgroundThread()
            return provider.getCapabilities()
        }
    }
}

private fun OnDeviceAiProviderComponent.toAndroidComponent(): ComponentName =
    ComponentName(packageName, className)

private fun requireBackgroundThread() {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "on-device AI provider metadata RPC must not run on the Android main thread"
    }
}
