package org.autojs.plugin.ai.provider.fake.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.IAiTargetListCallback
import org.autojs.plugin.ai.provider.api.IAiCallback
import org.autojs.plugin.ai.provider.api.IAiProvider
import org.autojs.plugin.ai.provider.api.IAiSession
import org.autojs.plugin.ai.provider.fake.FakeAiBinderInputPolicy
import org.autojs.plugin.ai.provider.fake.FakeBoundedExecutorFactory
import org.autojs.plugin.ai.provider.fake.FakeAiTargetPager
import org.autojs.plugin.ai.provider.fake.FakeAiProviderFixture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

class FakeAiProviderService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var worker: ExecutorService
    private lateinit var timeoutScheduler: ScheduledExecutorService
    private lateinit var callbackLane: SerialCallbackLane
    private val activeSession = AtomicReference<RemoteFakeAiProviderSession?>()
    private val sessions = ConcurrentHashMap.newKeySet<RemoteFakeAiProviderSession>()

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
        worker = FakeBoundedExecutorFactory.worker()
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "ai-provider-fake-timeout").apply { isDaemon = true }
        }
        callbackLane = SerialCallbackLane()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        activeSession.set(null)
        sessions.toList().forEach(RemoteFakeAiProviderSession::serviceDestroyed)
        sessions.clear()
        worker.shutdownNow()
        timeoutScheduler.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private val binder = object : IAiProvider.Stub() {
        override fun getProviderInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return AiCommonCodec.encodeProviderInfo(FakeAiProviderFixture.providerInfo)
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return AiProviderCodec.encodeCapabilities(FakeAiProviderFixture.capabilities)
        }

        override fun listTargets(request: ByteArray, callback: IAiTargetListCallback) {
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
                                    message = "Fake target-list request is invalid",
                                ),
                            ),
                        )
                    },
                    onFailure = {},
                )
                return
            }
            val requestCopy = request.copyOf()
            val accepted = submitCatalogWork {
                val result = runCatching {
                    val decoded = AiProviderCodec.decodeTargetListRequest(requestCopy)
                    AiProviderCodec.encodeTargetPage(FakeAiTargetPager.page(decoded))
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
                                            message = "Fake target-list request is invalid",
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
                                    message = "Fake target-list queue is full",
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
            callback: IAiCallback?,
        ): IAiSession {
            val ownerUid: Int
            val safeRequest: ByteArray
            val safeDescriptors: Array<out ParcelFileDescriptor>
            val safeCallback: IAiCallback
            try {
                ownerUid = callerVerifier.enforceAllowedCaller()
                safeRequest = requireNotNull(request) { "Fake AI request metadata is missing" }
                safeDescriptors = requireNotNull(descriptors) { "Fake AI request descriptors are missing" }
                safeCallback = requireNotNull(callback) { "Fake AI callback is missing" }
                FakeAiBinderInputPolicy.requireEnvelopeSize(safeRequest.size)
                FakeAiBinderInputPolicy.requireRequestDescriptorCount(
                    descriptorCount = safeDescriptors.size,
                    providerMaximum = minOf(
                        FakeAiProviderFixture.capabilities.maximumRequestDescriptors,
                        AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
                    ),
                )
            } catch (error: Throwable) {
                OwnedParcelFileDescriptors.closeIncoming(descriptors)
                throw error
            }
            val ownedDescriptors = OwnedParcelFileDescriptors.duplicateBeforeAsync(safeDescriptors)
            val session = try {
                RemoteFakeAiProviderSession(
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

    private fun submitCatalogWork(block: () -> Unit): Boolean = try {
            worker.execute(block)
            true
        } catch (_: RejectedExecutionException) {
            false
        }

}
