package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import org.autojs.autojs.core.plugin.PluginAutoWake
import org.autojs.plugin.ai.provider.api.IAiTargetListCallback
import org.autojs.plugin.ai.provider.api.IAiProvider
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exact-component Android transport for an explicitly invoked target-catalog run. */
internal class AndroidAiProviderTargetListing(
    context: Context,
) : AiProviderTargetListingTransport {
    private val applicationContext = context.applicationContext
    private val discovery = AndroidAiProviderDiscovery(applicationContext)

    override fun inspectExact(
        component: AiProviderComponent,
    ): AiProviderDiscoverySourceResult {
        requireTargetListingBackgroundThread()
        return discovery.inspectExact(component)
    }

    override fun bindExact(
        snapshot: AiProviderPackageSnapshot,
        callback: AiProviderTargetListingBindingCallback,
    ): AiProviderTargetListingBinding {
        requireTargetListingBackgroundThread()
        return ExactBinding(snapshot.component, callback).also(ExactBinding::bind)
    }

    override fun openRpc(
        connection: AiProviderTargetListingConnection,
    ): AiProviderTargetListingRpc {
        requireTargetListingBackgroundThread()
        val raw = connection as? AndroidTargetListingConnection
            ?: throw IllegalArgumentException(
                "Connection does not belong to the Android AI target-catalog transport",
            )
        if (!raw.binder.isBinderAlive) {
            throw IllegalStateException("local AI provider Binder is not alive")
        }
        if (raw.binder.interfaceDescriptor != IAiProvider.DESCRIPTOR) {
            throw AiProviderTargetListingDescriptorMismatchException()
        }
        val provider = requireNotNull(IAiProvider.Stub.asInterface(raw.binder)) {
            "local AI provider Binder has no AIDL interface"
        }
        return AndroidTargetListingRpc(provider)
    }

    private inner class ExactBinding(
        private val component: AiProviderComponent,
        private val callback: AiProviderTargetListingBindingCallback,
    ) : AiProviderTargetListingBinding, ServiceConnection {
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
                Intent().setComponent(component.toTargetListingAndroidComponent()),
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
                    AiProviderTargetListingRunFailure.DUPLICATE_CONNECTION,
                )
                return
            }
            if (name != component.toTargetListingAndroidComponent()) {
                callback.onBindingFailure(
                    AiProviderTargetListingRunFailure.UNEXPECTED_COMPONENT,
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
                callback.onConnected(AndroidTargetListingConnection(service))
            } catch (_: SecurityException) {
                callback.onBindingFailure(AiProviderTargetListingRunFailure.BIND_SECURITY)
            } catch (_: Exception) {
                callback.onBinderDeath()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiProviderTargetListingRunFailure.DISCONNECTED)
            }
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) callback.onBinderDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiProviderTargetListingRunFailure.NULL_BINDING)
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

    private class AndroidTargetListingConnection(
        val binder: IBinder,
    ) : AiProviderTargetListingConnection

    private class AndroidTargetListingRpc(
        private val provider: IAiProvider,
    ) : AiProviderTargetListingRpc {
        override fun getProviderInfoEnvelope(): ByteArray? {
            requireTargetListingBackgroundThread()
            return provider.getProviderInfo()
        }

        override fun getCapabilitiesEnvelope(): ByteArray? {
            requireTargetListingBackgroundThread()
            return provider.getCapabilities()
        }

        override fun prepareCall(
            sequence: Long,
            callback: AiProviderTargetListingCallbackIngress,
        ): AiProviderTargetListingPreparedCall {
            requireTargetListingBackgroundThread()
            require(sequence > 0L) { "Target-list sequence must be positive" }
            return AndroidPreparedTargetListCall(provider, callback)
        }
    }

    private class AndroidPreparedTargetListCall(
        private val provider: IAiProvider,
        callback: AiProviderTargetListingCallbackIngress,
    ) : AiProviderTargetListingPreparedCall {
        private val ingress = AtomicReference<AiProviderTargetListingCallbackIngress?>(callback)
        private val state = AtomicReference(PreparedCallState.OPEN)
        private val remoteCallback = object : IAiTargetListCallback.Stub() {
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
            requireTargetListingBackgroundThread()
            check(state.compareAndSet(PreparedCallState.OPEN, PreparedCallState.DISPATCHED)) {
                when (state.get()) {
                    PreparedCallState.CLOSED -> "Target-list call is closed"
                    else -> "Target-list call was already dispatched"
                }
            }
            provider.listTargets(requestEnvelope.copyOf(), remoteCallback)
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

private fun AiProviderComponent.toTargetListingAndroidComponent(): ComponentName =
    ComponentName(packageName, className)

private fun requireTargetListingBackgroundThread() {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "AI provider target-catalog transport must not run on the Android main thread"
    }
}
