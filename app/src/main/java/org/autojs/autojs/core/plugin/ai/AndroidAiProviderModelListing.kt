package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import org.autojs.plugin.ai.provider.api.IAiModelListCallback
import org.autojs.plugin.ai.provider.api.IAiProvider
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exact-component Android transport for an explicitly invoked model-list run. */
internal class AndroidAiProviderModelListing(
    context: Context,
) : AiProviderModelListingTransport {
    private val applicationContext = context.applicationContext
    private val discovery = AndroidAiProviderDiscovery(applicationContext)

    override fun inspectExact(
        component: AiProviderComponent,
    ): AiProviderDiscoverySourceResult {
        requireModelListingBackgroundThread()
        return discovery.inspectExact(component)
    }

    override fun bindExact(
        snapshot: AiProviderPackageSnapshot,
        callback: AiProviderModelListingBindingCallback,
    ): AiProviderModelListingBinding {
        requireModelListingBackgroundThread()
        return ExactBinding(snapshot.component, callback).also(ExactBinding::bind)
    }

    override fun openRpc(
        connection: AiProviderModelListingConnection,
    ): AiProviderModelListingRpc {
        requireModelListingBackgroundThread()
        val raw = connection as? AndroidModelListingConnection
            ?: throw IllegalArgumentException(
                "Connection does not belong to the Android local AI model-list transport",
            )
        if (!raw.binder.isBinderAlive) {
            throw IllegalStateException("local AI provider Binder is not alive")
        }
        if (raw.binder.interfaceDescriptor != IAiProvider.DESCRIPTOR) {
            throw AiProviderModelListingDescriptorMismatchException()
        }
        val provider = requireNotNull(IAiProvider.Stub.asInterface(raw.binder)) {
            "local AI provider Binder has no AIDL interface"
        }
        return AndroidModelListingRpc(provider)
    }

    private inner class ExactBinding(
        private val component: AiProviderComponent,
        private val callback: AiProviderModelListingBindingCallback,
    ) : AiProviderModelListingBinding, ServiceConnection {
        private val closed = AtomicBoolean(false)
        private val registered = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val connectionDelivered = AtomicBoolean(false)
        private val deathLock = Any()
        private var linkedBinder: IBinder? = null
        private var linkedRecipient: IBinder.DeathRecipient? = null

        fun bind() {
            val didBind = applicationContext.bindService(
                Intent().setComponent(component.toModelListingAndroidComponent()),
                this,
                Context.BIND_AUTO_CREATE,
            )
            if (!didBind) {
                throw IllegalStateException("Unable to bind the exact local AI provider component")
            }
            registered.set(true)
            unbindIfReady()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (!connectionDelivered.compareAndSet(false, true)) {
                callback.onBindingFailure(
                    AiProviderModelListingRunFailure.DUPLICATE_CONNECTION,
                )
                return
            }
            if (name != component.toModelListingAndroidComponent()) {
                callback.onBindingFailure(
                    AiProviderModelListingRunFailure.UNEXPECTED_COMPONENT,
                )
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
                callback.onConnected(AndroidModelListingConnection(service))
            } catch (_: SecurityException) {
                callback.onBindingFailure(AiProviderModelListingRunFailure.BIND_SECURITY)
            } catch (_: Exception) {
                callback.onBinderDeath()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiProviderModelListingRunFailure.DISCONNECTED)
            }
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) callback.onBinderDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiProviderModelListingRunFailure.NULL_BINDING)
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

    private class AndroidModelListingConnection(
        val binder: IBinder,
    ) : AiProviderModelListingConnection

    private class AndroidModelListingRpc(
        private val provider: IAiProvider,
    ) : AiProviderModelListingRpc {
        override fun getProviderInfoEnvelope(): ByteArray? {
            requireModelListingBackgroundThread()
            return provider.getProviderInfo()
        }

        override fun getCapabilitiesEnvelope(): ByteArray? {
            requireModelListingBackgroundThread()
            return provider.getCapabilities()
        }

        override fun prepareCall(
            sequence: Long,
            callback: AiProviderModelListingCallbackIngress,
        ): AiProviderModelListingPreparedCall {
            requireModelListingBackgroundThread()
            require(sequence > 0L) { "Model-list sequence must be positive" }
            return AndroidPreparedModelListCall(provider, callback)
        }
    }

    private class AndroidPreparedModelListCall(
        private val provider: IAiProvider,
        callback: AiProviderModelListingCallbackIngress,
    ) : AiProviderModelListingPreparedCall {
        private val ingress = AtomicReference<AiProviderModelListingCallbackIngress?>(callback)
        private val state = AtomicReference(PreparedCallState.OPEN)
        private val remoteCallback = object : IAiModelListCallback.Stub() {
            override fun onPage(page: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                ingress.get()?.onPage(callingUid, page)
            }

            override fun onFailed(error: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                ingress.get()?.onFailed(callingUid, error)
            }
        }

        override fun dispatch(requestEnvelope: ByteArray) {
            requireModelListingBackgroundThread()
            check(state.compareAndSet(PreparedCallState.OPEN, PreparedCallState.DISPATCHED)) {
                when (state.get()) {
                    PreparedCallState.CLOSED -> "Model-list call is closed"
                    else -> "Model-list call was already dispatched"
                }
            }
            provider.listModels(requestEnvelope.copyOf(), remoteCallback)
        }

        override fun close() {
            state.getAndSet(PreparedCallState.CLOSED)
            ingress.set(null)
        }

        private enum class PreparedCallState {
            OPEN,
            DISPATCHED,
            CLOSED,
        }
    }
}

private fun AiProviderComponent.toModelListingAndroidComponent(): ComponentName =
    ComponentName(packageName, className)

private fun requireModelListingBackgroundThread() {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "local AI provider model-list transport must not run on the Android main thread"
    }
}
