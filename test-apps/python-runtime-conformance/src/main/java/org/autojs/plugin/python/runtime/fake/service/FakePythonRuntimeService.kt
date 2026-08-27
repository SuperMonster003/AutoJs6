package org.autojs.plugin.python.runtime.fake.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.DeadObjectException
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonHostCapabilityBroker
import org.autojs.plugin.python.runtime.api.IPythonRuntimeProvider
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonErrorCode
import org.autojs.plugin.python.runtime.api.PythonExecutionError
import org.autojs.plugin.python.runtime.api.PythonFailurePhase
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractException
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractViolation
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.autojs.plugin.python.runtime.fake.FakePythonFixture
import org.autojs.plugin.python.runtime.fake.FakePythonRequestEnvelope
import org.autojs.plugin.python.runtime.fake.FakeSingleActiveSessionGate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val processSessionGate = FakeSingleActiveSessionGate<RemoteFakePythonExecutionSession>()

class FakePythonRuntimeService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var worker: ThreadPoolExecutor
    private lateinit var scheduler: java.util.concurrent.ScheduledExecutorService
    private lateinit var callbackLane: SerialCallbackLane
    private val sessions = ConcurrentHashMap.newKeySet<RemoteFakePythonExecutionSession>()

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
        worker = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            { runnable -> Thread(runnable, "python-fake-worker").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
        scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "python-fake-deadline").apply { isDaemon = true }
        }
        callbackLane = SerialCallbackLane()
    }

    /**
     * The runtime action is discovery-only. The conformance fixture deliberately rejects an
     * action-bearing explicit bind so the Android gate proves the host used a component-only
     * Intent after pinning this service.
     */
    override fun onBind(intent: Intent?): IBinder? = binder.takeIf {
        intent != null &&
            intent.action == null &&
            intent.component == ComponentName(this, FakePythonRuntimeService::class.java)
    }

    override fun onDestroy() {
        sessions.toList().forEach(RemoteFakePythonExecutionSession::serviceDestroyed)
        sessions.clear()
        worker.shutdownNow()
        scheduler.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private val binder = object : IPythonRuntimeProvider.Stub() {
        override fun getRuntimeInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return PythonRuntimeCodec.encodeRuntimeInfo(FakePythonFixture.runtimeInfo)
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return PythonRuntimeCodec.encodeCapabilities(FakePythonFixture.capabilities)
        }

        override fun openSession(
            request: ByteArray?,
            descriptors: Array<out ParcelFileDescriptor?>?,
            admissionCallback: IPythonSessionOpenCallback?,
            executionCallback: IPythonExecutionCallback?,
        ) {
            var receiverCopiesTransferred = false
            var unassignedOwner: OwnedParcelFileDescriptors? = null
            var unadmittedSession: RemoteFakePythonExecutionSession? = null
            try {
                val ownerUid = callerVerifier.enforceAllowedCaller()
                val safeRequest = requireNotNull(request) { "Python request metadata is missing" }
                val safeAdmission = requireNotNull(admissionCallback) { "Python admission callback is missing" }
                val safeExecution = requireNotNull(executionCallback) { "Python execution callback is missing" }
                requireCallbacksAlive(safeAdmission, safeExecution)

                val requestId = try {
                    FakePythonRequestEnvelope.requireUniqueRequestId(safeRequest)
                } catch (error: Throwable) {
                    throw IllegalArgumentException("Python request has no unique valid correlation ID", error)
                }

                val decoded = try {
                    PythonRuntimeCodec.decodeExecutionRequest(safeRequest.copyOf()).also { value ->
                        val descriptorCount = descriptors?.size
                            ?: throw IllegalArgumentException("Python descriptor array is missing")
                        require(descriptors.none { it == null }) { "Python descriptor array contains null" }
                        PythonRuntimeValidation.validateRequestAgainst(
                            request = value,
                            capabilities = FakePythonFixture.capabilities,
                            negotiatedVersion = FakePythonFixture.protocolVersion,
                            descriptorCount = descriptorCount,
                        )
                    }
                } catch (error: Throwable) {
                    reject(
                        safeAdmission,
                        PythonExecutionError(
                            requestId = requestId,
                            code = admissionCode(error),
                            phase = admissionPhase(error),
                            message = "Fake Python session request was rejected",
                            retryableBeforeDispatch = true,
                        ),
                    )
                    return
                }

                val incoming = checkNotNull(descriptors)
                val owned = try {
                    OwnedParcelFileDescriptors.takeReceiverCopies(incoming).also {
                        receiverCopiesTransferred = true
                        unassignedOwner = it
                    }
                } catch (_: Throwable) {
                    reject(
                        safeAdmission,
                        PythonExecutionError(
                            requestId = requestId,
                            code = PythonErrorCode.SESSION_OPEN_FAILED,
                            phase = PythonFailurePhase.SESSION_OPEN,
                            message = "Fake Python descriptor ownership transfer failed",
                            retryableBeforeDispatch = true,
                        ),
                    )
                    return
                }

                val session = RemoteFakePythonExecutionSession(
                    ownerUid = ownerUid,
                    request = decoded,
                    descriptors = owned,
                    admissionCallback = safeAdmission,
                    executionCallback = safeExecution,
                    callerVerifier = callerVerifier,
                    worker = worker,
                    scheduler = scheduler,
                    callbackLane = callbackLane,
                    onFinished = { finished ->
                        processSessionGate.release(finished)
                        sessions.remove(finished)
                    },
                )
                unassignedOwner = null
                unadmittedSession = session
                sessions += session
                if (!processSessionGate.tryAcquire(session)) {
                    sessions.remove(session)
                    session.forceClose()
                    reject(
                        safeAdmission,
                        PythonExecutionError(
                            requestId = requestId,
                            code = PythonErrorCode.BUSY,
                            phase = PythonFailurePhase.SESSION_OPEN,
                            message = "Fake Python runtime already has an active session",
                            retryableBeforeDispatch = true,
                        ),
                    )
                    return
                }
                if (!session.linkCallbackDeaths()) {
                    session.forceClose()
                    throw DeadObjectException()
                }
                try {
                    safeAdmission.onOpened(session)
                    unadmittedSession = null
                } catch (error: Throwable) {
                    session.forceClose()
                    throw error
                }
            } finally {
                unadmittedSession?.forceClose()
                unassignedOwner?.close()
                if (!receiverCopiesTransferred) {
                    OwnedParcelFileDescriptors.closeIncoming(descriptors)
                }
            }
        }

        override fun openSessionWithHostCapabilities(
            request: ByteArray?,
            descriptors: Array<out ParcelFileDescriptor?>?,
            hostCapabilityBroker: IPythonHostCapabilityBroker?,
            admissionCallback: IPythonSessionOpenCallback?,
            executionCallback: IPythonExecutionCallback?,
        ) {
            try {
                callerVerifier.enforceAllowedCaller()
                requireNotNull(hostCapabilityBroker) { "Python Host capability broker is missing" }
                val safeRequest = requireNotNull(request) { "Python request metadata is missing" }
                val safeAdmission = requireNotNull(admissionCallback) { "Python admission callback is missing" }
                val safeExecution = requireNotNull(executionCallback) { "Python execution callback is missing" }
                requireCallbacksAlive(safeAdmission, safeExecution)
                val requestId = FakePythonRequestEnvelope.requireUniqueRequestId(safeRequest)
                reject(
                    safeAdmission,
                    PythonExecutionError(
                        requestId = requestId,
                        code = PythonErrorCode.UNSUPPORTED_CAPABILITY,
                        phase = PythonFailurePhase.NEGOTIATION,
                        message = "Python R1 fake runtime does not support Host capability brokers",
                        retryableBeforeDispatch = true,
                    ),
                )
            } finally {
                OwnedParcelFileDescriptors.closeIncoming(descriptors)
            }
        }
    }

    private fun requireCallbacksAlive(
        admission: IPythonSessionOpenCallback,
        execution: IPythonExecutionCallback,
    ) {
        if (!admission.asBinder().isBinderAlive || !execution.asBinder().isBinderAlive) {
            throw DeadObjectException()
        }
    }

    private fun reject(callback: IPythonSessionOpenCallback, error: PythonExecutionError) {
        PythonRuntimeValidation.validateAdmissionError(error)
        callback.onRejected(PythonRuntimeCodec.encodeExecutionError(error))
    }

    private fun admissionCode(error: Throwable): PythonErrorCode = when {
        error is PythonRuntimeContractException &&
            error.violation == PythonRuntimeContractViolation.PROTOCOL_INCOMPATIBLE ->
            PythonErrorCode.UNSUPPORTED_PROTOCOL
        error is PythonRuntimeContractException &&
            error.violation == PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE ->
            PythonErrorCode.UNSUPPORTED_CAPABILITY
        else -> PythonErrorCode.INVALID_REQUEST
    }

    private fun admissionPhase(error: Throwable): PythonFailurePhase = when (admissionCode(error)) {
        PythonErrorCode.UNSUPPORTED_PROTOCOL,
        PythonErrorCode.UNSUPPORTED_CAPABILITY,
        -> PythonFailurePhase.NEGOTIATION
        else -> PythonFailurePhase.SESSION_OPEN
    }
}
