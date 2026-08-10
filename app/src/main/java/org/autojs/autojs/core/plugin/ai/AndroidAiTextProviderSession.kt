package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import org.autojs.plugin.ai.text.api.IAiTextCallback
import org.autojs.plugin.ai.text.api.IAiTextProvider
import org.autojs.plugin.ai.text.api.IAiTextSession
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exact-component Android transport for one explicitly invoked, default-off AI text session. */
internal class AndroidAiTextProviderSession(
    context: Context,
) : AiTextProviderSessionTransport {
    private val applicationContext = context.applicationContext
    private val discovery = AndroidAiTextProviderDiscovery(applicationContext)

    override fun inspectExact(
        component: AiTextProviderComponent,
    ): AiTextProviderDiscoverySourceResult {
        requireSessionBackgroundThread()
        return discovery.inspectExact(component)
    }

    override fun bindExact(
        snapshot: AiTextProviderPackageSnapshot,
        callback: AiTextProviderSessionBindingCallback,
    ): AiTextProviderSessionBinding {
        requireSessionBackgroundThread()
        return ExactBinding(snapshot.component, callback).also(ExactBinding::bind)
    }

    override fun openRpc(
        connection: AiTextProviderSessionConnection,
    ): AiTextProviderSessionRpc {
        requireSessionBackgroundThread()
        val raw = connection as? AndroidSessionConnection
            ?: throw IllegalArgumentException(
                "Connection does not belong to the Android AI text session transport",
            )
        if (!raw.binder.isBinderAlive) throw AiTextProviderRemoteSessionDeadException()
        if (mapAndroidAiTextRemoteDeadObject { raw.binder.interfaceDescriptor } !=
            IAiTextProvider.DESCRIPTOR
        ) {
            throw AiTextProviderSessionProviderDescriptorMismatchException()
        }
        val provider = requireNotNull(IAiTextProvider.Stub.asInterface(raw.binder)) {
            "AI text provider Binder has no AIDL interface"
        }
        return AndroidSessionRpc(provider)
    }

    /** Actionless binding is intentional: the manifest action is discovery-only. */
    private inner class ExactBinding(
        private val component: AiTextProviderComponent,
        private val callback: AiTextProviderSessionBindingCallback,
    ) : AiTextProviderSessionBinding, ServiceConnection {
        private val closed = AtomicBoolean(false)
        private val registered = AtomicBoolean(false)
        private val unbound = AtomicBoolean(false)
        private val connectionDelivered = AtomicBoolean(false)
        private val deathLock = Any()
        private var linkedBinder: IBinder? = null
        private var linkedRecipient: IBinder.DeathRecipient? = null

        fun bind() {
            val didBind = applicationContext.bindService(
                Intent().setComponent(component.toSessionAndroidComponent()),
                this,
                Context.BIND_AUTO_CREATE,
            )
            if (!didBind) {
                throw IllegalStateException("Unable to bind the exact AI text provider component")
            }
            registered.set(true)
            unbindIfReady()
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) return
            if (!connectionDelivered.compareAndSet(false, true)) {
                callback.onBindingFailure(AiTextProviderSessionRunFailure.DUPLICATE_CONNECTION)
                return
            }
            if (name != component.toSessionAndroidComponent()) {
                callback.onBindingFailure(AiTextProviderSessionRunFailure.UNEXPECTED_COMPONENT)
                return
            }
            val recipient = IBinder.DeathRecipient {
                if (!closed.get()) callback.onBinderDeath()
            }
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
                callback.onConnected(AndroidSessionConnection(service))
            } catch (_: SecurityException) {
                callback.onBindingFailure(AiTextProviderSessionRunFailure.BIND_SECURITY)
            } catch (_: Exception) {
                callback.onBinderDeath()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiTextProviderSessionRunFailure.DISCONNECTED)
            }
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) callback.onBinderDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) {
                callback.onBindingFailure(AiTextProviderSessionRunFailure.NULL_BINDING)
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

    private class AndroidSessionConnection(
        val binder: IBinder,
    ) : AiTextProviderSessionConnection

    private class AndroidSessionRpc(
        private val provider: IAiTextProvider,
    ) : AiTextProviderSessionRpc {
        override fun getProviderInfoEnvelope(): ByteArray? {
            requireSessionBackgroundThread()
            return mapAndroidAiTextRemoteDeadObject(provider::getProviderInfo)
        }

        override fun getCapabilitiesEnvelope(): ByteArray? {
            requireSessionBackgroundThread()
            return mapAndroidAiTextRemoteDeadObject(provider::getCapabilities)
        }

        override fun prepareOpen(
            callback: AiTextProviderSessionCallbackIngress,
            onSessionBinderDeath: () -> Unit,
            operationObserver: AiTextProviderSessionOperationObserver,
        ): AiTextProviderSessionPreparedOpen {
            requireSessionBackgroundThread()
            return AndroidPreparedSessionOpen(
                provider = provider,
                callback = callback,
                onSessionBinderDeath = onSessionBinderDeath,
                operationObserver = operationObserver,
            )
        }
    }

    private class AndroidPreparedSessionOpen(
        private val provider: IAiTextProvider,
        callback: AiTextProviderSessionCallbackIngress,
        private val onSessionBinderDeath: () -> Unit,
        private val operationObserver: AiTextProviderSessionOperationObserver,
    ) : AiTextProviderSessionPreparedOpen {
        private val ingress = AtomicReference<AiTextProviderSessionCallbackIngress?>(callback)
        private val state = AtomicReference(PreparedOpenState.OPEN)
        private val remoteCallback = object : IAiTextCallback.Stub() {
            override fun onStarted(metadata: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                ingress.get()?.onStarted(callingUid, metadata)
            }

            override fun onChunk(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                ingress.get()?.onChunk(callingUid, chunk)
            }

            override fun onToolCalls(
                calls: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor>?,
            ) {
                val callingUid = Binder.getCallingUid()
                forwardDescriptors(descriptors) { target, incoming ->
                    target.onToolCalls(callingUid, calls, incoming)
                }
            }

            override fun onUsage(usage: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                ingress.get()?.onUsage(callingUid, usage)
            }

            override fun onCompleted(
                result: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor>?,
            ) {
                val callingUid = Binder.getCallingUid()
                forwardDescriptors(descriptors) { target, incoming ->
                    target.onCompleted(callingUid, result, incoming)
                }
            }

            override fun onFailed(error: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                ingress.get()?.onFailed(callingUid, error)
            }

            override fun onCancelled() {
                val callingUid = Binder.getCallingUid()
                ingress.get()?.onCancelled(callingUid)
            }
        }

        override fun open(
            requestEnvelope: ByteArray,
            requestDescriptors: AiTextProviderSessionRequestDescriptorLease,
        ): AiTextProviderSessionRemote {
            requireSessionBackgroundThread()
            check(state.compareAndSet(PreparedOpenState.OPEN, PreparedOpenState.DISPATCHED)) {
                when (state.get()) {
                    PreparedOpenState.CLOSED -> "AI text session open is closed"
                    else -> "AI text session open was already dispatched"
                }
            }
            val androidDescriptors = requestDescriptors as?
                AndroidAiTextProviderSessionRequestDescriptors
                ?: throw IllegalArgumentException(
                    "Request descriptors do not belong to the Android AI text session transport",
            )
            return androidDescriptors.withBorrowedDescriptorsForOpen { borrowed ->
                val session = requireNotNull(
                    mapAndroidAiTextRemoteDeadObject {
                        provider.openSession(requestEnvelope.copyOf(), borrowed, remoteCallback)
                    },
                ) { "AI text provider returned no remote session" }
                AndroidRemoteSession.create(
                    session = session,
                    onSessionBinderDeath = onSessionBinderDeath,
                    operationObserver = operationObserver,
                )
            }
        }

        override fun close() {
            state.set(PreparedOpenState.CLOSED)
            ingress.set(null)
        }

        private inline fun forwardDescriptors(
            descriptors: Array<out ParcelFileDescriptor?>?,
            forward: (
                AiTextProviderSessionCallbackIngress,
                AiTextProviderSessionIncomingDescriptors?,
            ) -> Unit,
        ) {
            val target = ingress.get()
            if (target == null) {
                closeAndroidAiTextProviderSessionIncomingDescriptors(descriptors)
                return
            }
            val incoming: AiTextProviderSessionIncomingDescriptors? = if (descriptors == null) {
                null
            } else {
                try {
                    AndroidAiTextProviderSessionIncomingDescriptors(descriptors, operationObserver)
                } catch (_: Throwable) {
                    // The constructor owns-or-closes, but close the hostile platform array again
                    // defensively before synchronously reporting a stable policy failure sentinel.
                    closeAndroidAiTextProviderSessionIncomingDescriptors(descriptors)
                    InvalidAndroidAiTextProviderSessionIncomingDescriptors
                }
            }
            try {
                forward(target, incoming)
            } catch (error: Throwable) {
                runCatching { incoming?.close() }
                throw error
            }
        }

        private enum class PreparedOpenState {
            OPEN,
            DISPATCHED,
            CLOSED,
        }
    }

    private class AndroidRemoteSession private constructor(
        private val session: IAiTextSession,
        private val binder: IBinder,
        private val recipient: IBinder.DeathRecipient,
        private val operationObserver: AiTextProviderSessionOperationObserver,
        private val closed: AtomicBoolean,
        private val deathDelivered: AtomicBoolean,
        private val onSessionBinderDeath: () -> Unit,
        private val controlLock: Any,
    ) : AiTextProviderSessionRemote {
        override fun grantCredits(count: Int) {
            check(count > 0) { "AI text session credits must be positive" }
            withObservedOperation {
                synchronized(controlLock) {
                    check(!closed.get()) { "AI text remote session is closed" }
                    transactControl { session.grantCredits(count) }
                }
            }
        }

        override fun cancel() {
            withObservedOperation {
                synchronized(controlLock) {
                    check(!closed.get()) { "AI text remote session is closed" }
                    transactControl(session::cancel)
                }
            }
        }

        override fun close() {
            synchronized(controlLock) {
                if (!closed.compareAndSet(false, true)) return
                runCatching { binder.unlinkToDeath(recipient, 0) }
                val operation = runCatching(operationObserver::begin).getOrNull()
                try {
                    try {
                        session.close()
                    } catch (_: DeadObjectException) {
                        markSessionBinderDead()
                    } catch (_: Throwable) {
                        // Remote close is best effort; only the trusted death callback may escape.
                    }
                } finally {
                    runCatching { operation?.close() }
                }
            }
        }

        private inline fun <T> withObservedOperation(block: () -> T): T {
            val operation = operationObserver.begin()
            try {
                return block()
            } finally {
                runCatching(operation::close)
            }
        }

        private inline fun <T> transactControl(block: () -> T): T = try {
            block()
        } catch (_: DeadObjectException) {
            markSessionBinderDead()
            throw AiTextProviderRemoteSessionDeadException()
        }

        private fun markSessionBinderDead() {
            closed.set(true)
            runCatching { binder.unlinkToDeath(recipient, 0) }
            signalSessionBinderDeath()
        }

        private fun signalSessionBinderDeath() {
            if (deathDelivered.compareAndSet(false, true)) {
                onSessionBinderDeath()
            }
        }

        companion object {
            fun create(
                session: IAiTextSession,
                onSessionBinderDeath: () -> Unit,
                operationObserver: AiTextProviderSessionOperationObserver,
            ): AndroidRemoteSession {
                val binder = session.asBinder()
                if (!binder.isBinderAlive) throw AiTextProviderRemoteSessionDeadException()
                if (mapAndroidAiTextRemoteDeadObject { binder.interfaceDescriptor } !=
                    IAiTextSession.DESCRIPTOR
                ) {
                    throw AiTextProviderRemoteSessionDescriptorMismatchException()
                }
                val closed = AtomicBoolean(false)
                val deathDelivered = AtomicBoolean(false)
                val controlLock = Any()
                val recipient = IBinder.DeathRecipient {
                    synchronized(controlLock) {
                        if (!closed.get()) {
                            closed.set(true)
                            if (deathDelivered.compareAndSet(false, true)) {
                                onSessionBinderDeath()
                            }
                        }
                    }
                }
                try {
                    binder.linkToDeath(recipient, 0)
                } catch (error: Exception) {
                    throw AiTextProviderRemoteSessionDeadException()
                }
                if (!binder.isBinderAlive) {
                    runCatching { binder.unlinkToDeath(recipient, 0) }
                    throw AiTextProviderRemoteSessionDeadException()
                }
                return AndroidRemoteSession(
                    session = session,
                    binder = binder,
                    recipient = recipient,
                    operationObserver = operationObserver,
                    closed = closed,
                    deathDelivered = deathDelivered,
                    onSessionBinderDeath = onSessionBinderDeath,
                    controlLock = controlLock,
                )
            }
        }
    }
}

private fun AiTextProviderComponent.toSessionAndroidComponent(): ComponentName =
    ComponentName(packageName, className)

private fun requireSessionBackgroundThread() {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "AI text provider session transport must not run on the Android main thread"
    }
}

private inline fun <T> mapAndroidAiTextRemoteDeadObject(block: () -> T): T = try {
    block()
} catch (_: DeadObjectException) {
    throw AiTextProviderRemoteSessionDeadException()
}

/** No-FD sentinel whose invalid count is synchronously and deterministically rejected by policy. */
private object InvalidAndroidAiTextProviderSessionIncomingDescriptors :
    AiTextProviderSessionIncomingDescriptors {
    override val count: Int = -1

    override fun copyForAsync(): AiTextProviderSessionDescriptorLease =
        error("Invalid Android AI text callback descriptors cannot be copied")

    override fun close() = Unit
}
