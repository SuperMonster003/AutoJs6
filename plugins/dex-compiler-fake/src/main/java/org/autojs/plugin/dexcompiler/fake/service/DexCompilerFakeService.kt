package org.autojs.plugin.dexcompiler.fake.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.dexcompiler.api.DexCompilerCodec
import org.autojs.plugin.dexcompiler.api.IDexCompilerCallback
import org.autojs.plugin.dexcompiler.api.IDexCompilerProvider
import org.autojs.plugin.dexcompiler.api.IDexCompilerSession
import org.autojs.plugin.dexcompiler.fake.FakeDexCompilerFixture
import org.autojs.plugin.dexcompiler.fake.FakeDexCompilerScenario
import org.autojs.plugin.dexcompiler.fake.FakeSingleActiveSessionGate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val processSessionGate = FakeSingleActiveSessionGate<RemoteFakeDexCompileSession>()

abstract class DexCompilerFakeService : Service() {
    protected abstract val scenario: FakeDexCompilerScenario

    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var worker: ExecutorService
    private lateinit var callbackLane: SerialCallbackLane
    private val sessions = ConcurrentHashMap.newKeySet<RemoteFakeDexCompileSession>()

    override fun onCreate() {
        super.onCreate()
        verifyManifestScenario()
        callerVerifier = HostCallerVerifier(this)
        worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dex-fake-worker").apply { isDaemon = true }
        }
        callbackLane = SerialCallbackLane()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        sessions.toList().forEach(RemoteFakeDexCompileSession::serviceDestroyed)
        sessions.clear()
        worker.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private val binder = object : IDexCompilerProvider.Stub() {
        override fun getCompilerInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return DexCompilerCodec.encodeInfo(FakeDexCompilerFixture.info(scenario))
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return DexCompilerCodec.encodeCapabilities(FakeDexCompilerFixture.capabilities)
        }

        override fun openSession(
            request: ByteArray?,
            programFd: ParcelFileDescriptor?,
            outputFd: ParcelFileDescriptor?,
            callback: IDexCompilerCallback?,
        ): IDexCompilerSession {
            val ownerUid = try {
                callerVerifier.enforceAllowedCaller()
            } catch (error: Throwable) {
                OwnedParcelFileDescriptors.closeIncoming(programFd, outputFd)
                throw error
            }
            if (request == null || programFd == null || outputFd == null || callback == null) {
                OwnedParcelFileDescriptors.closeIncoming(programFd, outputFd)
                throw IllegalArgumentException("DEX compiler session arguments must not be null")
            }
            val descriptors = OwnedParcelFileDescriptors.duplicateBeforeAsync(programFd, outputFd)
            val session = try {
                RemoteFakeDexCompileSession(
                    ownerUid = ownerUid,
                    requestMetadata = request.copyOf(),
                    descriptors = descriptors,
                    callback = callback,
                    scenario = scenario,
                    callerVerifier = callerVerifier,
                    worker = worker,
                    callbackLane = callbackLane,
                    onFinished = { finished ->
                        processSessionGate.release(finished)
                        sessions.remove(finished)
                    },
                )
            } catch (error: Throwable) {
                descriptors.close()
                throw error
            }

            sessions += session
            if (processSessionGate.tryAcquire(session)) {
                session.start()
            } else {
                session.rejectBusy()
            }
            return session
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyManifestScenario() {
        val info = packageManager.getServiceInfo(
            ComponentName(this, javaClass),
            PackageManager.GET_META_DATA,
        )
        val declaredValue = info.metaData?.getString(FakeDexCompilerScenario.METADATA_KEY)
            ?: error("DEX compiler fixture service is missing scenario metadata")
        check(FakeDexCompilerScenario.fromMetadata(declaredValue) == scenario) {
            "DEX compiler fixture service scenario does not match its manifest metadata"
        }
    }
}

class NormalDexCompilerService : DexCompilerFakeService() {
    override val scenario = FakeDexCompilerScenario.NORMAL
}

class ProgressBeforeStartDexCompilerService : DexCompilerFakeService() {
    override val scenario = FakeDexCompilerScenario.PROGRESS_BEFORE_START
}

class DuplicateTerminalDexCompilerService : DexCompilerFakeService() {
    override val scenario = FakeDexCompilerScenario.DUPLICATE_TERMINAL
}

class InvalidOutputDexCompilerService : DexCompilerFakeService() {
    override val scenario = FakeDexCompilerScenario.INVALID_OUTPUT
}

class StallDexCompilerService : DexCompilerFakeService() {
    override val scenario = FakeDexCompilerScenario.STALL
}
