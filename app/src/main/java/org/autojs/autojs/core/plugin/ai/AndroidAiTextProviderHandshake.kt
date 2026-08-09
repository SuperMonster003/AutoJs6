package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import org.autojs.plugin.ai.text.api.IAiTextProvider
import java.util.concurrent.atomic.AtomicBoolean

/** Exact-component Android Binder transport for the explicitly invoked metadata-only handshake. */
internal class AndroidAiTextProviderHandshake(
    context: Context,
) : AiTextProviderHandshakeTransport {
    private val applicationContext = context.applicationContext
    private val discovery = AndroidAiTextProviderDiscovery(applicationContext)

    override fun inspectExact(
        component: AiTextProviderComponent,
    ): AiTextProviderDiscoverySourceResult {
        requireBackgroundThread()
        return discovery.inspectExact(component)
    }

    override fun bindExact(
        snapshot: AiTextProviderPackageSnapshot,
        callback: AiTextProviderBindingCallback,
    ): AiTextProviderBinding {
        requireBackgroundThread()
        return ExactBinding(snapshot.component, callback).also(ExactBinding::bind)
    }

    override fun openMetadataRpc(
        connection: AiTextProviderRawConnection,
    ): AiTextProviderMetadataRpc {
        requireBackgroundThread()
        val raw = connection as? AndroidRawConnection
            ?: throw IllegalArgumentException("Connection does not belong to the Android AI text transport")
        if (!raw.binder.isBinderAlive) throw IllegalStateException("AI text provider Binder is not alive")
        val descriptor = raw.binder.interfaceDescriptor
        if (descriptor != IAiTextProvider.DESCRIPTOR) {
            throw AiTextProviderDescriptorMismatchException()
        }
        val provider = requireNotNull(IAiTextProvider.Stub.asInterface(raw.binder)) {
            "AI text provider Binder has no AIDL interface"
        }
        return AndroidMetadataRpc(provider)
    }

    private inner class ExactBinding(
        private val component: AiTextProviderComponent,
        private val callback: AiTextProviderBindingCallback,
    ) : AiTextProviderBinding, ServiceConnection {
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
            if (!didBind) throw IllegalStateException("Unable to bind the exact AI text provider component")
            registered.set(true)
            unbindIfReady()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (!connectionDelivered.compareAndSet(false, true)) {
                callback.onBindingFailure(AiTextProviderHandshakeFailure.DUPLICATE_CONNECTION)
                return
            }
            if (name != component.toAndroidComponent()) {
                callback.onBindingFailure(AiTextProviderHandshakeFailure.UNEXPECTED_COMPONENT)
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
                callback.onBindingFailure(AiTextProviderHandshakeFailure.BIND_SECURITY)
            } catch (_: Exception) {
                callback.onBindingFailure(AiTextProviderHandshakeFailure.BINDER_DIED)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiTextProviderHandshakeFailure.DISCONNECTED)
            }
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) callback.onBinderDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiTextProviderHandshakeFailure.NULL_BINDING)
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
    ) : AiTextProviderRawConnection

    private class AndroidMetadataRpc(
        private val provider: IAiTextProvider,
    ) : AiTextProviderMetadataRpc {
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

private fun AiTextProviderComponent.toAndroidComponent(): ComponentName =
    ComponentName(packageName, className)

private fun requireBackgroundThread() {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "AI text provider metadata RPC must not run on the Android main thread"
    }
}
