package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.plugin.ai.provider.api.IAiProvider
import java.util.concurrent.atomic.AtomicBoolean

/** Exact-component Android Binder transport for the explicitly invoked metadata-only handshake. */
internal class AndroidAiProviderHandshake(
    context: Context,
) : AiProviderHandshakeTransport {
    private val applicationContext = context.applicationContext
    private val discovery = AndroidAiProviderDiscovery(applicationContext)

    override fun inspectExact(
        component: AiProviderComponent,
    ): AiProviderDiscoverySourceResult {
        requireBackgroundThread()
        return discovery.inspectExact(component)
    }

    override fun bindExact(
        snapshot: AiProviderPackageSnapshot,
        callback: AiProviderBindingCallback,
    ): AiProviderBinding {
        requireBackgroundThread()
        return ExactBinding(snapshot.component, callback).also(ExactBinding::bind)
    }

    override fun openMetadataRpc(
        connection: AiProviderRawConnection,
    ): AiProviderMetadataRpc {
        requireBackgroundThread()
        val raw = connection as? AndroidRawConnection
            ?: throw IllegalArgumentException("Connection does not belong to the Android local AI transport")
        if (!raw.binder.isBinderAlive) throw IllegalStateException("local AI provider Binder is not alive")
        val descriptor = raw.binder.interfaceDescriptor
        if (descriptor != IAiProvider.DESCRIPTOR) {
            throw AiProviderDescriptorMismatchException()
        }
        val provider = requireNotNull(IAiProvider.Stub.asInterface(raw.binder)) {
            "local AI provider Binder has no AIDL interface"
        }
        return AndroidMetadataRpc(provider)
    }

    private inner class ExactBinding(
        private val component: AiProviderComponent,
        private val callback: AiProviderBindingCallback,
    ) : AiProviderBinding, ServiceConnection {
        private val closed = AtomicBoolean(false)
        private val registered = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val connectionDelivered = AtomicBoolean(false)
        private val deathLock = Any()
        private var linkedBinder: IBinder? = null
        private var linkedRecipient: IBinder.DeathRecipient? = null

        fun bind() {
            val didBind = PluginAutoWake.bindServiceWaking(
                applicationContext,
                Intent().setComponent(component.toAndroidComponent()),
                this,
                Context.BIND_AUTO_CREATE,
            )
            if (!didBind) throw IllegalStateException("Unable to bind the exact local AI provider component")
            registered.set(true)
            unbindIfReady()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (!connectionDelivered.compareAndSet(false, true)) {
                callback.onBindingFailure(AiProviderHandshakeFailure.DUPLICATE_CONNECTION)
                return
            }
            if (name != component.toAndroidComponent()) {
                callback.onBindingFailure(AiProviderHandshakeFailure.UNEXPECTED_COMPONENT)
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
                callback.onBindingFailure(AiProviderHandshakeFailure.BIND_SECURITY)
            } catch (_: Exception) {
                callback.onBindingFailure(AiProviderHandshakeFailure.BINDER_DIED)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiProviderHandshakeFailure.DISCONNECTED)
            }
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) callback.onBinderDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiProviderHandshakeFailure.NULL_BINDING)
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
    ) : AiProviderRawConnection

    private class AndroidMetadataRpc(
        private val provider: IAiProvider,
    ) : AiProviderMetadataRpc {
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

private fun AiProviderComponent.toAndroidComponent(): ComponentName =
    ComponentName(packageName, className)

private fun requireBackgroundThread() {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "local AI provider metadata RPC must not run on the Android main thread"
    }
}
