package org.autojs.autojs.engine

import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.autojs.core.plugin.lua.LuaRuntimeAcquiredProvider
import org.autojs.autojs.core.plugin.lua.LuaRuntimeBinderCallLane
import org.autojs.autojs.core.plugin.lua.LuaRuntimePluginHost
import org.autojs.autojs.core.plugin.lua.LuaRuntimePreparedProvider
import org.autojs.autojs.core.plugin.lua.LuaRuntimeProviderCallbackCallerPolicy
import org.autojs.autojs.core.plugin.scope.BridgeExecutionScope
import org.autojs.plugin.lua.runtime.api.ILuaExecutionCallback
import org.autojs.plugin.lua.runtime.api.ILuaExecutionSession
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityBroker
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityCallback
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
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
    val grantedCapabilities: List<String>

    fun createExecution(
        request: LuaExecutionRequest,
        source: LuaSourceSnapshot,
        modules: LuaModuleSnapshot,
        observer: LuaEngineExecutionObserver,
        scope: BridgeExecutionScope,
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
    private val hostStorage: LuaHostStorage,
    private val toastSink: LuaToastSink,
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
            BinderConnection(acquired, hostStorage, toastSink)
        } catch (error: Throwable) {
            runCatching { acquired.close() }
            throw error
        }
    }

    private class BinderConnection(
        private val acquired: LuaRuntimeAcquiredProvider,
        private val hostStorage: LuaHostStorage,
        private val toastSink: LuaToastSink,
    ) : LuaEngineRuntimeConnection {

        override val runtimeInfo: LuaRuntimeInfo
            get() = acquired.runtimeInfo

        override val protocolVersion: LuaProtocolVersion
            get() = acquired.pinnedIdentity.protocolVersion

        override val grantedCapabilities: List<String>
            get() = acquired.pinnedIdentity.grantedCapabilities

        override fun createExecution(
            request: LuaExecutionRequest,
            source: LuaSourceSnapshot,
            modules: LuaModuleSnapshot,
            observer: LuaEngineExecutionObserver,
            scope: BridgeExecutionScope,
        ): LuaEngineRemoteSession {
            require(acquired.pinnedIdentity.grantedCapabilities.containsAll(request.requiredCapabilities)) {
                "Lua execution requirements exceed the pinned host capability grant"
            }
            val sessionRef = AtomicReference<ILuaExecutionSession?>()
            val revoked = AtomicBoolean(false)
            val broker = hostBroker(request, source.storagePrincipal, modules, observer, revoked)
            val revokeBroker: () -> Unit = {
                revoked.set(true)
                broker.close()
            }
            val callback = ExecutionCallback(observer, sessionRef, revoked, revokeBroker)
            // The broker is revoked before the session closes, as the close of the session does itself.
            // zh-CN: broker 在会话关闭之前撤销, 与会话自身的 close 一致.
            scope.own(BridgeExecutionScope.Stage.BROKER, "broker") { revokeBroker() }
            scope.track(broker, "broker")
            scope.own(BridgeExecutionScope.Stage.CALLBACKS, "callback", callback::close)
            scope.track(callback, "callback")
            val provider = acquired.provider
            val providerBinder = provider.asBinder()
                ?: throw IllegalStateException("Lua runtime provider returned a null Binder")
            val deathRecipient = IBinder.DeathRecipient {
                revokeBroker()
                observer.transportViolation(IllegalStateException("Lua runtime provider Binder died"))
            }
            providerBinder.linkToDeath(deathRecipient, 0)
            var session: ILuaExecutionSession? = null
            var sessionBinder: IBinder? = null
            var sessionDeathRecipient: IBinder.DeathRecipient? = null
            var attached = false
            try {
                acquired.revalidateBeforeDispatch()
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
                            callback.binder,
                            broker.binder,
                        )
                    }
                } ?: throw IllegalStateException("Lua runtime provider returned a null execution session")
                session = createdSession
                val createdSessionBinder = createdSession.asBinder()
                    ?: throw IllegalStateException("Lua runtime provider returned a session without a Binder")
                if (createdSessionBinder !== providerBinder) {
                    val createdSessionDeathRecipient = IBinder.DeathRecipient {
                        revokeBroker()
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
                    closeHostBroker = broker::close,
                ).also { scope.track(it, "session") }
            } catch (error: Throwable) {
                revoked.set(true)
                broker.close()
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

        /**
         * The callback of one execution. The plugin gets [binder], which forwards here until [close] and then lets go of this
         * callback, its observer and the session: the plugin process keeps that Binder until it collects its proxy
         * (docs/memory-phase2/ROADMAP.md C41).
         * zh-CN: 一次执行的回调. 插件拿到的是 [binder], 它在 [close] 之前转发到这里, 之后放开本回调, 其观察者与会话: 插件进程在回收代理之前一直持有该 Binder
         * (docs/memory-phase2/ROADMAP.md C41).
         */
        private inner class ExecutionCallback(
            private val observer: LuaEngineExecutionObserver,
            private val sessionRef: AtomicReference<ILuaExecutionSession?>,
            private val revoked: AtomicBoolean,
            private val revokeBroker: () -> Unit,
        ) {
            private val exported = ExportedExecutionCallback(this)

            val binder: ILuaExecutionCallback
                get() = exported

            fun close() {
                exported.clear()
            }

            fun onStarted(metadata: ByteArray?) = fromPinnedUid(observer, revokeBroker) {
                if (revoked.get()) return@fromPinnedUid
                observer.onStarted(requireNotNull(metadata) { "Lua started metadata is null" })
            }

            fun onOutput(output: ByteArray?) = fromPinnedUid(observer, revokeBroker) {
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

            fun onCompleted(result: ByteArray?, descriptors: Array<out ParcelFileDescriptor?>?) {
                closeDescriptors(descriptors) { count ->
                    fromPinnedUid(observer, revokeBroker) {
                        if (revoked.get()) return@fromPinnedUid
                        try {
                            observer.onCompleted(requireNotNull(result) { "Lua result payload is null" }, count)
                        } finally {
                            revokeBroker()
                        }
                    }
                }
            }

            fun onFailed(error: ByteArray?) = fromPinnedUid(observer, revokeBroker) {
                if (revoked.get()) return@fromPinnedUid
                try {
                    observer.onFailed(requireNotNull(error) { "Lua error payload is null" })
                } finally {
                    revokeBroker()
                }
            }

            fun onCancelled(cancellation: ByteArray?) = fromPinnedUid(observer, revokeBroker) {
                if (revoked.get()) return@fromPinnedUid
                try {
                    observer.onCancelled(requireNotNull(cancellation) { "Lua cancellation payload is null" })
                } finally {
                    revokeBroker()
                }
            }
        }

        private fun hostBroker(
            request: LuaExecutionRequest,
            storagePrincipal: LuaStoragePrincipal?,
            modules: LuaModuleSnapshot,
            observer: LuaEngineExecutionObserver,
            revoked: AtomicBoolean,
        ) = SessionHostBroker(request, storagePrincipal, modules, observer, revoked)

        /**
         * The host capability broker of one execution. The plugin gets [binder], which forwards here until [close] and then lets
         * go of this broker, its dispatcher and observer (docs/memory-phase2/ROADMAP.md C41).
         * zh-CN: 一次执行的宿主能力 broker. 插件拿到的是 [binder], 它在 [close] 之前转发到这里, 之后放开本 broker, 其分发器与观察者
         * (docs/memory-phase2/ROADMAP.md C41).
         */
        private inner class SessionHostBroker(
            private val request: LuaExecutionRequest,
            storagePrincipal: LuaStoragePrincipal?,
            modules: LuaModuleSnapshot,
            private val observer: LuaEngineExecutionObserver,
            private val revoked: AtomicBoolean,
        ) {
            private val exported = ExportedHostBroker(this)

            val binder: ILuaHostCapabilityBroker
                get() = exported

            private val dispatcher = LuaHostCapabilityDispatcher(
                executionId = request.requestId,
                grantedCapabilities = request.requiredCapabilities,
                moduleSnapshot = modules,
                storagePrincipal = storagePrincipal,
                hostStorage = hostStorage,
                toastSink = toastSink,
            )

            fun invoke(
                encodedRequest: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor?>?,
                callback: ILuaHostCapabilityCallback?,
            ) {
                closeDescriptors(descriptors) { descriptorCount ->
                    fromPinnedUid(observer, ::closeForViolation) {
                        if (revoked.get()) return@fromPinnedUid
                        try {
                            LuaRuntimeValidation.validateV1DescriptorCount(descriptorCount)
                            val hostRequest = LuaRuntimeCodec.decodeHostRequest(
                                requireNotNull(encodedRequest) { "Lua host request payload is null" },
                            )
                            val hostCallback = requireNotNull(callback) {
                                "Lua host capability callback is null"
                            }
                            when (val outcome = dispatcher.dispatch(hostRequest, descriptorCount)) {
                                is LuaHostDispatchOutcome.Completed -> hostCallback.onCompleted(
                                    LuaRuntimeCodec.encodeHostResult(outcome.result),
                                    emptyArray(),
                                )
                                is LuaHostDispatchOutcome.Failed -> hostCallback.onFailed(
                                    LuaRuntimeCodec.encodeHostError(outcome.error),
                                )
                            }
                        } catch (error: Throwable) {
                            closeForViolation()
                            observer.transportViolation(error)
                        }
                    }
                }
            }

            fun close() {
                exported.clear()
                dispatcher.close()
            }

            private fun closeForViolation() {
                revoked.set(true)
                dispatcher.close()
            }
        }

        /**
         * Forwards to [ExecutionCallback] until cleared; afterwards only closes the descriptors of a late result.
         * zh-CN: 清空之前转发到 [ExecutionCallback]; 之后只关闭迟到结果所附的描述符.
         */
        private class ExportedExecutionCallback(callback: ExecutionCallback) : ILuaExecutionCallback.Stub() {

            private val target = AtomicReference<ExecutionCallback?>(callback)

            fun clear() {
                target.set(null)
            }

            override fun onStarted(metadata: ByteArray?) {
                target.get()?.onStarted(metadata)
            }

            override fun onOutput(output: ByteArray?) {
                target.get()?.onOutput(output)
            }

            override fun onCompleted(result: ByteArray?, descriptors: Array<out ParcelFileDescriptor?>?) {
                target.get()?.onCompleted(result, descriptors) ?: closeQuietly(descriptors)
            }

            override fun onFailed(error: ByteArray?) {
                target.get()?.onFailed(error)
            }

            override fun onCancelled(cancellation: ByteArray?) {
                target.get()?.onCancelled(cancellation)
            }
        }

        /**
         * Forwards to [SessionHostBroker] until cleared; afterwards a call is ignored as by a revoked broker.
         * zh-CN: 清空之前转发到 [SessionHostBroker]; 之后的调用与已撤销的 broker 一样被忽略.
         */
        private class ExportedHostBroker(broker: SessionHostBroker) : ILuaHostCapabilityBroker.Stub() {

            private val target = AtomicReference<SessionHostBroker?>(broker)

            fun clear() {
                target.set(null)
            }

            override fun invoke(
                encodedRequest: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor?>?,
                callback: ILuaHostCapabilityCallback?,
            ) {
                target.get()?.invoke(encodedRequest, descriptors, callback) ?: closeQuietly(descriptors)
            }
        }

        private inline fun fromPinnedUid(
            observer: LuaEngineExecutionObserver,
            onViolation: () -> Unit = {},
            action: () -> Unit,
        ) {
            val callingUid = Binder.getCallingUid()
            if (!LuaRuntimeProviderCallbackCallerPolicy.isExpectedUid(acquired.pinnedIdentity, callingUid)) {
                onViolation()
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
                onViolation()
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

            fun closeQuietly(descriptors: Array<out ParcelFileDescriptor?>?) {
                descriptors?.forEach { descriptor -> runCatching { descriptor?.close() } }
            }
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
        private val closeHostBroker: () -> Unit,
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
            closeHostBroker()
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
