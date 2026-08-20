package org.autojs.plugin.ondeviceai.fake.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.IAiModelListCallback
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiCallback
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiProvider
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiSession
import org.autojs.plugin.ondeviceai.fake.FakeAiBinderInputPolicy
import org.autojs.plugin.ondeviceai.fake.FakeBoundedExecutorFactory
import org.autojs.plugin.ondeviceai.fake.FakeAiModelPager
import org.autojs.plugin.ondeviceai.fake.FakeOnDeviceAiFixture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

class FakeOnDeviceAiProviderService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var worker: ExecutorService
    private lateinit var timeoutScheduler: ScheduledExecutorService
    private lateinit var callbackLane: SerialCallbackLane
    private val activeSession = AtomicReference<RemoteFakeOnDeviceAiSession?>()
    private val sessions = ConcurrentHashMap.newKeySet<RemoteFakeOnDeviceAiSession>()

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
        worker = FakeBoundedExecutorFactory.worker()
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "on-device-ai-fake-timeout").apply { isDaemon = true }
        }
        callbackLane = SerialCallbackLane()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        activeSession.set(null)
        sessions.toList().forEach(RemoteFakeOnDeviceAiSession::serviceDestroyed)
        sessions.clear()
        worker.shutdownNow()
        timeoutScheduler.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private val binder = object : IOnDeviceAiProvider.Stub() {
        override fun getProviderInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return AiCommonCodec.encodeProviderInfo(FakeOnDeviceAiFixture.providerInfo)
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return OnDeviceAiCodec.encodeCapabilities(FakeOnDeviceAiFixture.capabilities)
        }

        override fun listModels(request: ByteArray, callback: IAiModelListCallback) {
            callerVerifier.enforceAllowedCaller()
            try {
                FakeAiBinderInputPolicy.requireEnvelopeSize(request.size)
            } catch (_: IllegalArgumentException) {
                callbackLane.dispatch(
                    callback = {
                        callback.onFailed(
                            AiCommonCodec.encodeError(
                                AiError(
                                    code = AiErrorCode.INVALID_REQUEST,
                                    message = "Fake model-list request is invalid",
                                ),
                            ),
                        )
                    },
                    onFailure = {},
                )
                return
            }
            val requestCopy = request.copyOf()
            val accepted = submitListWork {
                val result = runCatching {
                    val decoded = OnDeviceAiCodec.decodeModelListRequest(requestCopy)
                    OnDeviceAiCodec.encodeModelPage(FakeAiModelPager.page(decoded))
                }
                callbackLane.dispatch(
                    callback = {
                        result.fold(
                            onSuccess = callback::onPage,
                            onFailure = {
                                callback.onFailed(
                                    AiCommonCodec.encodeError(
                                        AiError(
                                            code = AiErrorCode.INVALID_REQUEST,
                                            message = "Fake model-list request is invalid",
                                        ),
                                    ),
                                )
                            },
                        )
                    },
                    onFailure = {},
                )
            }
            if (!accepted) {
                callbackLane.dispatch(
                    callback = {
                        callback.onFailed(
                            AiCommonCodec.encodeError(
                                AiError(
                                    code = AiErrorCode.BACKPRESSURE,
                                    message = "Fake model-list queue is full",
                                ),
                            ),
                        )
                    },
                    onFailure = {},
                )
            }
        }

        override fun openSession(
            request: ByteArray?,
            descriptors: Array<out ParcelFileDescriptor>?,
            callback: IOnDeviceAiCallback?,
        ): IOnDeviceAiSession {
            val ownerUid: Int
            val safeRequest: ByteArray
            val safeDescriptors: Array<out ParcelFileDescriptor>
            val safeCallback: IOnDeviceAiCallback
            try {
                ownerUid = callerVerifier.enforceAllowedCaller()
                safeRequest = requireNotNull(request) { "Fake AI request metadata is missing" }
                safeDescriptors = requireNotNull(descriptors) { "Fake AI request descriptors are missing" }
                safeCallback = requireNotNull(callback) { "Fake AI callback is missing" }
                FakeAiBinderInputPolicy.requireEnvelopeSize(safeRequest.size)
                FakeAiBinderInputPolicy.requireRequestDescriptorCount(
                    descriptorCount = safeDescriptors.size,
                    providerMaximum = minOf(
                        FakeOnDeviceAiFixture.capabilities.maximumRequestDescriptors,
                        AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
                    ),
                )
            } catch (error: Throwable) {
                OwnedParcelFileDescriptors.closeIncoming(descriptors)
                throw error
            }
            val ownedDescriptors = OwnedParcelFileDescriptors.duplicateBeforeAsync(safeDescriptors)
            val session = try {
                RemoteFakeOnDeviceAiSession(
                    context = applicationContext,
                    ownerUid = ownerUid,
                    requestMetadata = safeRequest.copyOf(),
                    descriptors = ownedDescriptors,
                    callback = safeCallback,
                    callerVerifier = callerVerifier,
                    worker = worker,
                    timeoutScheduler = timeoutScheduler,
                    callbackLane = callbackLane,
                    onFinished = { finished ->
                        activeSession.compareAndSet(finished, null)
                        sessions.remove(finished)
                    },
                )
            } catch (error: Throwable) {
                ownedDescriptors.close()
                throw error
            }
            sessions += session
            if (activeSession.compareAndSet(null, session)) {
                session.start()
            } else {
                session.rejectBusy()
            }
            return session
        }
    }

    private fun submitListWork(block: () -> Unit): Boolean = try {
            worker.execute(block)
            true
        } catch (_: RejectedExecutionException) {
            false
        }

}
