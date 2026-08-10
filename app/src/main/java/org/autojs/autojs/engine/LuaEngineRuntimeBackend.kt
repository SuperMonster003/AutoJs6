package org.autojs.autojs.engine

import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.autojs.core.plugin.lua.LuaRuntimeAcquiredProvider
import org.autojs.autojs.core.plugin.lua.LuaRuntimeBinderCallLane
import org.autojs.autojs.core.plugin.lua.LuaRuntimePluginHost
import org.autojs.autojs.core.plugin.lua.LuaRuntimePreparedProvider
import org.autojs.autojs.core.plugin.lua.LuaRuntimeProviderCallbackCallerPolicy
import org.autojs.plugin.lua.runtime.api.ILuaExecutionCallback
import org.autojs.plugin.lua.runtime.api.ILuaExecutionSession
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityBroker
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityCallback
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.autojs.plugin.lua.runtime.api.LuaHostCallError
import org.autojs.plugin.lua.runtime.api.LuaHostErrorCode
import org.autojs.plugin.lua.runtime.api.LuaProtocolVersion
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo
import org.autojs.plugin.lua.runtime.api.LuaRuntimeValidation
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class LuaEnginePreparedRuntime(
    val runtimeInfo: LuaRuntimeInfo,
    val protocolVersion: LuaProtocolVersion,
    internal val token: Any,
)

internal interface LuaEngineRuntimeBackend {
    fun prepare(): LuaEnginePreparedRuntime
    fun acquire(prepared: LuaEnginePreparedRuntime): LuaEngineRuntimeConnection
}

internal interface LuaEngineRuntimeConnection : Closeable {
    val runtimeInfo: LuaRuntimeInfo
    val protocolVersion: LuaProtocolVersion

    fun createExecution(
        request: LuaExecutionRequest,
        source: LuaSourceSnapshot,
        observer: LuaEngineExecutionObserver,
    ): LuaEngineRemoteSession
}

internal interface LuaEngineRemoteSession : Closeable {
    fun grantOutputCredits(count: Int)
    fun start()
    fun cancel()
}

/** Production adapter. It never retries after an execution session has been created. */
internal class BinderLuaEngineRuntimeBackend(
    private val host: LuaRuntimePluginHost,
) : LuaEngineRuntimeBackend {

    override fun prepare(): LuaEnginePreparedRuntime {
        val prepared = host.prepare()
        return LuaEnginePreparedRuntime(
            runtimeInfo = prepared.runtimeInfo,
            protocolVersion = prepared.pinnedIdentity.protocolVersion,
            token = prepared,
        )
    }

    override fun acquire(prepared: LuaEnginePreparedRuntime): LuaEngineRuntimeConnection {
        val hostPrepared = prepared.token as? LuaRuntimePreparedProvider
            ?: throw IllegalArgumentException("Lua prepared-runtime token belongs to another backend")
        val acquired = host.acquire(hostPrepared)
        return try {
            check(acquired.pinnedIdentity.protocolVersion == prepared.protocolVersion) {
                "Lua provider protocol identity changed before dispatch"
            }
            check(acquired.pinnedIdentity.matchesRuntimeInfo(prepared.runtimeInfo)) {
                "Lua provider runtime identity changed before dispatch"
            }
            BinderConnection(acquired)
        } catch (error: Throwable) {
            runCatching { acquired.close() }
            throw error
        }
    }

    private class BinderConnection(
        private val acquired: LuaRuntimeAcquiredProvider,
    ) : LuaEngineRuntimeConnection {

        override val runtimeInfo: LuaRuntimeInfo
            get() = acquired.runtimeInfo

        override val protocolVersion: LuaProtocolVersion
            get() = acquired.pinnedIdentity.protocolVersion

        override fun createExecution(
            request: LuaExecutionRequest,
            source: LuaSourceSnapshot,
            observer: LuaEngineExecutionObserver,
        ): LuaEngineRemoteSession {
            val sessionRef = AtomicReference<ILuaExecutionSession?>()
            val revoked = AtomicBoolean(false)
            val callback = executionCallback(observer, sessionRef, revoked)
            val broker = rejectingHostBroker(request, observer, revoked)
            val provider = acquired.provider
            val providerBinder = provider.asBinder()
                ?: throw IllegalStateException("Lua runtime provider returned a null Binder")
            val deathRecipient = IBinder.DeathRecipient {
                observer.transportViolation(IllegalStateException("Lua runtime provider Binder died"))
            }
            providerBinder.linkToDeath(deathRecipient, 0)
            var session: ILuaExecutionSession? = null
            var sessionBinder: IBinder? = null
            var sessionDeathRecipient: IBinder.DeathRecipient? = null
            var attached = false
            try {
                val createdSession = LuaRuntimeBinderCallLane.call(
                    operationName = "create-execution",
                    timeoutMillis = CREATE_EXECUTION_TIMEOUT_MILLIS,
                    onAbandoned = {
                        revoked.set(true)
                        LuaRuntimeBinderCallLane.poison()
                    },
                    lateResultDisposer = { lateSession: ILuaExecutionSession? ->
                        runCatching { lateSession?.cancel() }
                        runCatching { lateSession?.close() }
                    },
                ) {
                    source.openReadOnlyDescriptor().use { descriptor ->
                        provider.createExecution(
                            LuaRuntimeCodec.encodeExecutionRequest(request),
                            descriptor,
                            callback,
                            broker,
                        )
                    }
                } ?: throw IllegalStateException("Lua runtime provider returned a null execution session")
                session = createdSession
                val createdSessionBinder = createdSession.asBinder()
                    ?: throw IllegalStateException("Lua runtime provider returned a session without a Binder")
                if (createdSessionBinder !== providerBinder) {
                    val createdSessionDeathRecipient = IBinder.DeathRecipient {
                        observer.transportViolation(IllegalStateException("Lua execution session Binder died"))
                    }
                    createdSessionBinder.linkToDeath(createdSessionDeathRecipient, 0)
                    sessionBinder = createdSessionBinder
                    sessionDeathRecipient = createdSessionDeathRecipient
                }
                sessionRef.set(createdSession)
                acquired.attachSession(createdSession)
                attached = true
                return BinderRemoteSession(
                    acquired = acquired,
                    session = createdSession,
                    sessionRef = sessionRef,
                    providerBinder = providerBinder,
                    providerDeathRecipient = deathRecipient,
                    sessionBinder = sessionBinder,
                    sessionDeathRecipient = sessionDeathRecipient,
                    revoked = revoked,
                )
            } catch (error: Throwable) {
                revoked.set(true)
                session?.let { unownedSession ->
                    sessionRef.compareAndSet(unownedSession, null)
                    if (attached) acquired.releaseSession(unownedSession)
                    runCatching { unownedSession.cancel() }
                    runCatching { unownedSession.close() }
                }
                val binderToUnlink = sessionBinder
                val recipientToUnlink = sessionDeathRecipient
                if (binderToUnlink != null && recipientToUnlink != null) {
                    runCatching { binderToUnlink.unlinkToDeath(recipientToUnlink, 0) }
                }
                runCatching { providerBinder.unlinkToDeath(deathRecipient, 0) }
                throw error
            }
        }

        override fun close() {
            acquired.close()
        }

        private fun executionCallback(
            observer: LuaEngineExecutionObserver,
            sessionRef: AtomicReference<ILuaExecutionSession?>,
            revoked: AtomicBoolean,
        ) = object : ILuaExecutionCallback.Stub() {

            override fun onStarted(metadata: ByteArray?) = fromPinnedUid(observer) {
                if (revoked.get()) return@fromPinnedUid
                observer.onStarted(requireNotNull(metadata) { "Lua started metadata is null" })
            }

            override fun onOutput(output: ByteArray?) = fromPinnedUid(observer) {
                if (revoked.get()) return@fromPinnedUid
                if (!observer.onOutput(requireNotNull(output) { "Lua output payload is null" })) return@fromPinnedUid
                val session = sessionRef.get()
                if (session == null) {
                    observer.transportViolation(IllegalStateException("Lua output arrived before session creation"))
                } else {
                    runCatching { session.grantOutputCredits(1) }
                        .onFailure(observer::transportViolation)
                }
            }

            override fun onCompleted(result: ByteArray?, descriptors: Array<out ParcelFileDescriptor?>?) {
                closeDescriptors(descriptors) { count ->
                    fromPinnedUid(observer) {
                        if (revoked.get()) return@fromPinnedUid
                        observer.onCompleted(requireNotNull(result) { "Lua result payload is null" }, count)
                    }
                }
            }

            override fun onFailed(error: ByteArray?) = fromPinnedUid(observer) {
                if (revoked.get()) return@fromPinnedUid
                observer.onFailed(requireNotNull(error) { "Lua error payload is null" })
            }

            override fun onCancelled(cancellation: ByteArray?) = fromPinnedUid(observer) {
                if (revoked.get()) return@fromPinnedUid
                observer.onCancelled(requireNotNull(cancellation) { "Lua cancellation payload is null" })
            }
        }

        private fun rejectingHostBroker(
            request: LuaExecutionRequest,
            observer: LuaEngineExecutionObserver,
            revoked: AtomicBoolean,
        ) = object : ILuaHostCapabilityBroker.Stub() {

            override fun invoke(
                encodedRequest: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor?>?,
                callback: ILuaHostCapabilityCallback?,
            ) {
                closeDescriptors(descriptors) { descriptorCount ->
                    fromPinnedUid(observer) {
                        if (revoked.get()) return@fromPinnedUid
                        try {
                            LuaRuntimeValidation.validateV1DescriptorCount(descriptorCount)
                            val hostRequest = LuaRuntimeCodec.decodeHostRequest(
                                requireNotNull(encodedRequest) { "Lua host request payload is null" },
                            )
                            require(hostRequest.executionId == request.requestId) {
                                "Lua host request execution ID does not match"
                            }
                            requireNotNull(callback) { "Lua host capability callback is null" }.onFailed(
                                LuaRuntimeCodec.encodeHostError(
                                    LuaHostCallError(
                                        executionId = hostRequest.executionId,
                                        callId = hostRequest.callId,
                                        code = LuaHostErrorCode.CAPABILITY_DENIED,
                                        message = "Host capabilities are unavailable in Lua host R1",
                                    ),
                                ),
                            )
                        } catch (error: Throwable) {
                            observer.transportViolation(error)
                        }
                    }
                }
            }
        }

        private inline fun fromPinnedUid(observer: LuaEngineExecutionObserver, action: () -> Unit) {
            val callingUid = Binder.getCallingUid()
            if (!LuaRuntimeProviderCallbackCallerPolicy.isExpectedUid(acquired.pinnedIdentity, callingUid)) {
                observer.transportViolation(
                    SecurityException(
                        "Lua runtime callback UID $callingUid differs from pinned UID ${acquired.expectedCallbackUid}",
                    ),
                )
                return
            }
            val identity = Binder.clearCallingIdentity()
            try {
                action()
            } catch (error: Throwable) {
                observer.transportViolation(error)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }

        private inline fun closeDescriptors(
            descriptors: Array<out ParcelFileDescriptor?>?,
            action: (Int) -> Unit,
        ) {
            try {
                action(descriptors?.size ?: 0)
            } finally {
                descriptors?.forEach { descriptor -> runCatching { descriptor?.close() } }
            }
        }

        private companion object {
            const val CREATE_EXECUTION_TIMEOUT_MILLIS = 15_000L
        }
    }

    private class BinderRemoteSession(
        private val acquired: LuaRuntimeAcquiredProvider,
        private val session: ILuaExecutionSession,
        private val sessionRef: AtomicReference<ILuaExecutionSession?>,
        private val providerBinder: IBinder,
        private val providerDeathRecipient: IBinder.DeathRecipient,
        private val sessionBinder: IBinder?,
        private val sessionDeathRecipient: IBinder.DeathRecipient?,
        private val revoked: AtomicBoolean,
    ) : LuaEngineRemoteSession {
        private val started = AtomicBoolean(false)
        private val cancelled = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override fun grantOutputCredits(count: Int) {
            check(!closed.get()) { "Lua execution session is closed" }
            session.grantOutputCredits(count)
        }

        override fun start() {
            check(!closed.get()) { "Lua execution session is closed" }
            if (started.compareAndSet(false, true)) session.start()
        }

        override fun cancel() {
            if (!closed.get() && cancelled.compareAndSet(false, true)) session.cancel()
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            revoked.set(true)
            sessionRef.compareAndSet(session, null)
            runCatching { session.close() }
            acquired.releaseSession(session)
            val binderToUnlink = sessionBinder
            val recipientToUnlink = sessionDeathRecipient
            if (binderToUnlink != null && recipientToUnlink != null) {
                runCatching { binderToUnlink.unlinkToDeath(recipientToUnlink, 0) }
            }
            runCatching { providerBinder.unlinkToDeath(providerDeathRecipient, 0) }
        }
    }
}
