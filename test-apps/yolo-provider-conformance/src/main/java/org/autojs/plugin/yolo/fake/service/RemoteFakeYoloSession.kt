package org.autojs.plugin.yolo.fake.service

import android.content.Context
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import org.autojs.plugin.yolo.api.IYoloCallback
import org.autojs.plugin.yolo.api.IYoloSession
import org.autojs.plugin.yolo.api.YoloCancellation
import org.autojs.plugin.yolo.api.YoloCancellationReason
import org.autojs.plugin.yolo.api.YoloCapabilities
import org.autojs.plugin.yolo.api.YoloCodec
import org.autojs.plugin.yolo.api.YoloDetectRequest
import org.autojs.plugin.yolo.api.YoloDetectResult
import org.autojs.plugin.yolo.api.YoloError
import org.autojs.plugin.yolo.api.YoloErrorCode
import org.autojs.plugin.yolo.api.YoloFailurePhase
import org.autojs.plugin.yolo.api.YoloValidation
import org.autojs.plugin.yolo.fake.FakeYoloScenario
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class RemoteFakeYoloSession(
    private val context: Context,
    private val ownerUid: Int,
    private val scenario: FakeYoloScenario,
    private val callback: IYoloCallback,
    private val callerVerifier: FakeYoloCallerVerifier,
    private val capabilities: YoloCapabilities,
    private val onFinished: (RemoteFakeYoloSession) -> Unit,
) : IYoloSession.Stub() {
    private val callbackBinder = callback.asBinder()
    private val closed = AtomicBoolean(false)
    private val active = AtomicReference<YoloDetectRequest?>()
    private val previousResult = AtomicReference<YoloDetectResult?>()
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fake-yolo-hostile").apply { isDaemon = true }
    }
    private val callbackDeathRecipient = IBinder.DeathRecipient {
        finishSession(notifyActive = false, YoloCancellationReason.PROVIDER_SHUTDOWN)
    }

    init {
        callbackBinder.linkToDeath(callbackDeathRecipient, 0)
        if (!callbackBinder.isBinderAlive) {
            finishSession(notifyActive = false, YoloCancellationReason.PROVIDER_SHUTDOWN)
        }
    }

    override fun detect(request: ByteArray?, imageFd: ParcelFileDescriptor?) {
        callerVerifier.enforceSessionOwner(ownerUid)
        runCatching { imageFd?.close() }
        val decoded = try {
            YoloCodec.decodeDetectRequest(
                requireNotNull(request) { "Fake YOLO detect request is missing" }.copyOf(),
            ).also { YoloValidation.validateDetectRequestAgainst(it, capabilities) }
        } catch (_: Throwable) {
            return
        }
        dispatch { handleDetect(decoded) }
    }

    override fun cancel(request: ByteArray?) {
        callerVerifier.enforceSessionOwner(ownerUid)
        val decoded = try {
            YoloCodec.decodeCancelRequest(
                requireNotNull(request) { "Fake YOLO cancel request is missing" }.copyOf(),
            ).also(YoloValidation::validateCancelRequest)
        } catch (_: Throwable) {
            return
        }
        dispatch {
            val current = active.get() ?: return@dispatch
            if (current.requestId != decoded.requestId || current.sequence != decoded.sequence) {
                return@dispatch
            }
            if (scenario == FakeYoloScenario.STALL_CANCEL_NO_ACK) return@dispatch
            if (active.compareAndSet(current, null)) {
                publish {
                    callback.onCancelled(
                        YoloCodec.encodeCancellation(
                            YoloCancellation(
                                requestId = current.requestId,
                                sequence = current.sequence,
                                reason = decoded.reason,
                                elapsedMillis = 0L,
                            ),
                        ),
                    )
                }
            }
        }
    }

    override fun close() {
        callerVerifier.enforceSessionOwner(ownerUid)
        finishSession(notifyActive = true, YoloCancellationReason.SESSION_CLOSED)
    }

    fun serviceDestroyed() {
        finishSession(notifyActive = false, YoloCancellationReason.PROVIDER_SHUTDOWN)
    }

    private fun handleDetect(request: YoloDetectRequest) {
        if (closed.get()) return
        if (!active.compareAndSet(null, request)) {
            publishError(request, YoloErrorCode.BUSY, "Fake YOLO session is busy")
            return
        }
        when (scenario) {
            FakeYoloScenario.DUPLICATE_TERMINAL -> {
                val result = emptyResult(request)
                active.compareAndSet(request, null)
                publish { callback.onDetected(YoloCodec.encodeDetectResult(result)) }
                publish { callback.onDetected(YoloCodec.encodeDetectResult(result)) }
            }

            FakeYoloScenario.LATE_OLD_TERMINAL -> {
                val result = emptyResult(request)
                val old = previousResult.getAndSet(result)
                active.compareAndSet(request, null)
                if (old == null) {
                    publish { callback.onDetected(YoloCodec.encodeDetectResult(result)) }
                } else {
                    publish { callback.onDetected(YoloCodec.encodeDetectResult(old)) }
                    publish { callback.onDetected(YoloCodec.encodeDetectResult(result)) }
                }
            }

            FakeYoloScenario.STALL_CANCEL_ACK,
            FakeYoloScenario.STALL_CANCEL_NO_ACK,
            -> Unit

            FakeYoloScenario.WRONG_CALLBACK_UID -> {
                val result = emptyResult(request)
                val binding = IsolatedWrongUidYoloCallbackBinding(
                    context = context,
                    callback = callback,
                    terminal = YoloCodec.encodeDetectResult(result),
                )
                try {
                    if (!binding.bindAndAwaitAcknowledgement()) {
                        publishError(request, YoloErrorCode.INTERNAL, "Wrong-UID relay was unavailable")
                    }
                } finally {
                    binding.close()
                    active.compareAndSet(request, null)
                }
            }

            FakeYoloScenario.PROVIDER_DEATH -> {
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun emptyResult(request: YoloDetectRequest) = YoloDetectResult(
        requestId = request.requestId,
        sequence = request.sequence,
        imageWidth = request.image.width,
        imageHeight = request.image.height,
        elapsedMillis = 0L,
        detections = emptyList(),
    )

    private fun publishError(request: YoloDetectRequest, code: YoloErrorCode, message: String) {
        publish {
            callback.onFailed(
                YoloCodec.encodeError(
                    YoloError(
                        requestId = request.requestId,
                        sequence = request.sequence,
                        code = code,
                        phase = YoloFailurePhase.ADMISSION,
                        message = message,
                        retryableBeforeDispatch = true,
                    ),
                ),
            )
        }
    }

    private fun publish(block: () -> Unit) {
        runCatching(block).onFailure {
            finishSession(notifyActive = false, YoloCancellationReason.PROVIDER_SHUTDOWN)
        }
    }

    private fun dispatch(block: () -> Unit) {
        if (closed.get()) return
        try {
            worker.execute(block)
        } catch (_: RejectedExecutionException) {
            // A closed hostile session intentionally drops new one-way traffic.
        }
    }

    private fun finishSession(
        notifyActive: Boolean,
        reason: YoloCancellationReason,
    ) {
        if (!closed.compareAndSet(false, true)) return
        val request = active.getAndSet(null)
        if (notifyActive && request != null && callbackBinder.isBinderAlive) {
            publish {
                callback.onCancelled(
                    YoloCodec.encodeCancellation(
                        YoloCancellation(
                            requestId = request.requestId,
                            sequence = request.sequence,
                            reason = reason,
                            elapsedMillis = 0L,
                        ),
                    ),
                )
            }
        }
        runCatching { callbackBinder.unlinkToDeath(callbackDeathRecipient, 0) }
        worker.shutdownNow()
        onFinished(this)
    }
}
