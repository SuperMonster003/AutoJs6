package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.project.ProjectConfig
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.IApkBuildCallback
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object RemoteApkBuildClient {

    private const val TAG = "RemoteApkBuildClient"
    private const val WAIT_SLICE_MILLIS = 250L
    private val SHA_256_HEX = Regex("^[0-9a-fA-F]{64}$")
    const val STATUS_NOT_AVAILABLE = 100

    fun interface ProgressListener {
        fun onProgress(progress: ApkBuildProgress)
    }

    data class BuildOutcome(
        val status: Int,
        val warnings: List<String> = emptyList(),
        val errors: List<String> = emptyList(),
        val updatedProjectConfigJson: String? = null,
    ) {
        val isSuccess: Boolean
            get() = status == ApkBuildResult.STATUS_OK
    }

    @JvmStatic
    @Throws(Exception::class)
    fun buildBlocking(
        context: Context,
        projectConfig: ProjectConfig,
        outApk: File,
        cancelSignal: AtomicBoolean?,
        progressListener: ProgressListener? = null,
        preferredPluginPackageName: String? = null,
    ): BuildOutcome {
        val buildStartedAtElapsedMillis = SystemClock.elapsedRealtime()
        val appContext = context.applicationContext
        val hasRemoteBuildPlugin = runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.hasRemoteBuildPlugin(
                appContext,
                preferredPackageName = preferredPluginPackageName,
            )
        }
        if (!hasRemoteBuildPlugin) {
            return BuildOutcome(
                status = STATUS_NOT_AVAILABLE,
                warnings = unavailableWarningsBlocking(appContext),
            )
        }

        progressListener?.onProgress(
            ApkBuildProgress(
                step = ApkBuildProgress.STEP_PREPARE,
                title = context.getString(org.autojs.autojs6.R.string.text_preparing_workspace),
                detail = projectConfig.sourcePath,
            )
        )
        val prepared = RemoteApkBuildRequestPreparer.prepare(
            context = appContext,
            projectConfig = projectConfig,
            outputFileName = outApk.name,
            cancelSignal = cancelSignal,
            progressListener = progressListener?.let { listener ->
                { progress: ApkBuildProgress -> listener.onProgress(progress) }
            },
        )
        try {
            val callback = Callback(progressListener, SystemClock::elapsedRealtime)
            val opened = runBlocking(Dispatchers.IO) {
                ApkBuilderTemplatePluginHost.openBuildSession(
                    appContext,
                    prepared.request,
                    callback,
                    preferredPluginPackageName,
                )
            } ?: return BuildOutcome(
                status = STATUS_NOT_AVAILABLE,
                warnings = unavailableWarningsBlocking(appContext),
            )

            val session = opened.session
            val sessionBinder = session.asBinder()
            val binderLost = AtomicBoolean(!sessionBinder.isBinderAlive)
            val deathRecipient = IBinder.DeathRecipient {
                binderLost.set(true)
                callback.wakeWaiter()
            }
            var deathRecipientLinked = false
            try {
                if (!binderLost.get()) {
                    try {
                        sessionBinder.linkToDeath(deathRecipient, 0)
                        deathRecipientLinked = true
                        if (!sessionBinder.isBinderAlive) {
                            binderLost.set(true)
                        }
                    } catch (error: RemoteException) {
                        Log.w(TAG, "Remote build session binder is already dead.", error)
                        binderLost.set(true)
                    }
                }
                if (binderLost.get()) {
                    return failedSessionOutcome(
                        "Remote APK build session was lost before it could start.",
                        opened.hostCompatibilityWarnings,
                    )
                }
                runCatching { session.start() }.onFailure { error ->
                    Log.w(TAG, "Failed to start remote APK build session.", error)
                    if (error is RemoteException || !sessionBinder.isBinderAlive) {
                        binderLost.set(true)
                    }
                }.exceptionOrNull()?.let { error ->
                    return failedSessionOutcome(
                        if (binderLost.get()) {
                            "Remote APK build session was lost while starting."
                        } else {
                            "Failed to start remote APK build session: ${error.message ?: error}"
                        },
                        opened.hostCompatibilityWarnings,
                    )
                }

                while (true) {
                    val terminalCallbackReceived = try {
                        callback.await(WAIT_SLICE_MILLIS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        runCatching { session.cancel() }
                            .onFailure { Log.w(TAG, "Failed to cancel interrupted remote build session", it) }
                        return BuildOutcome(status = ApkBuildResult.STATUS_CANCELLED)
                    }
                    if (terminalCallbackReceived) {
                        break
                    }
                    if (cancelSignal?.get() == true || Thread.currentThread().isInterrupted) {
                        runCatching { session.cancel() }
                            .onFailure { Log.w(TAG, "Failed to cancel remote build session", it) }
                        return BuildOutcome(status = ApkBuildResult.STATUS_CANCELLED)
                    }
                    when (
                        RemoteBuildWaitPolicy.evaluate(
                            nowElapsedMillis = SystemClock.elapsedRealtime(),
                            startedAtElapsedMillis = buildStartedAtElapsedMillis,
                            lastActivityElapsedMillis = callback.lastActivityElapsedMillis(),
                            binderAlive = !binderLost.get() && sessionBinder.isBinderAlive,
                        )
                    ) {
                        RemoteBuildWaitPolicy.Decision.CONTINUE -> Unit
                        RemoteBuildWaitPolicy.Decision.BINDER_LOST -> {
                            return failedSessionOutcome(
                                "Remote APK build session was lost before completion.",
                                opened.hostCompatibilityWarnings,
                            )
                        }
                        RemoteBuildWaitPolicy.Decision.NO_PROGRESS_TIMEOUT -> {
                            runCatching { session.cancel() }
                                .onFailure { Log.w(TAG, "Failed to cancel stalled remote build session", it) }
                            return failedSessionOutcome(
                                "Remote APK build made no progress for 12 minutes and was cancelled.",
                                opened.hostCompatibilityWarnings,
                            )
                        }
                        RemoteBuildWaitPolicy.Decision.TOTAL_TIMEOUT -> {
                            runCatching { session.cancel() }
                                .onFailure { Log.w(TAG, "Failed to cancel timed-out remote build session", it) }
                            return failedSessionOutcome(
                                "Remote APK build exceeded the 30-minute total time limit and was cancelled.",
                                opened.hostCompatibilityWarnings,
                            )
                        }
                    }
                }
                val result = callback.result ?: return if (binderLost.get() || !sessionBinder.isBinderAlive) {
                    failedSessionOutcome(
                        "Remote APK build session was lost before returning a result.",
                        opened.hostCompatibilityWarnings,
                    )
                } else {
                    failedSessionOutcome(
                        "Remote APK build finished without a result.",
                        opened.hostCompatibilityWarnings,
                    )
                }
                return handleResult(
                    context = context,
                    result = result,
                    outApk = outApk,
                    cancelSignal = cancelSignal,
                    progressListener = progressListener,
                    hostCompatibilityWarnings = opened.hostCompatibilityWarnings,
                )
            } finally {
                if (deathRecipientLinked) {
                    runCatching {
                        sessionBinder.unlinkToDeath(deathRecipient, 0)
                    }.onFailure {
                        Log.d(TAG, "Remote build death recipient was already unlinked.", it)
                    }
                }
                runCatching {
                    callback.result?.takeIf { it.status == ApkBuildResult.STATUS_OK }?.let {
                        progressListener?.onProgress(
                            ApkBuildProgress(
                                step = ApkBuildProgress.STEP_CLEAN,
                                title = context.getString(org.autojs.autojs6.R.string.text_cleaning_workspace),
                                detail = null,
                            )
                        )
                    }
                }.onFailure { Log.w(TAG, "Failed to report remote build cleanup progress.", it) }
                runCatching { session.close() }
                    .onFailure { Log.w(TAG, "Failed to close remote build session.", it) }
                runCatching { opened.bindingLease.close() }
                    .onFailure { Log.w(TAG, "Failed to release remote build plugin binding lease.", it) }
            }
        } finally {
            prepared.cleanup()
        }
    }

    private fun failedSessionOutcome(message: String, warnings: List<String>): BuildOutcome {
        return BuildOutcome(
            status = ApkBuildResult.STATUS_FAILED,
            warnings = warnings,
            errors = listOf(message),
        )
    }

    private fun handleResult(
        context: Context,
        result: ApkBuildResult,
        outApk: File,
        cancelSignal: AtomicBoolean?,
        progressListener: ProgressListener?,
        hostCompatibilityWarnings: List<String>,
    ): BuildOutcome {
        val warnings = (hostCompatibilityWarnings + result.warnings).filter { it.isNotBlank() }.distinct()
        if (result.status != ApkBuildResult.STATUS_OK) {
            result.outputApkFd?.let { outputFd ->
                runCatching(outputFd::close)
                    .onFailure { Log.w(TAG, "Failed to close unexpected remote build output fd.", it) }
            }
            return BuildOutcome(
                status = result.status,
                warnings = warnings,
                errors = result.errors,
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }

        val outputFd = result.outputApkFd ?: return BuildOutcome(
            status = ApkBuildResult.STATUS_FAILED,
            warnings = warnings,
            errors = result.errors + "Remote APK build completed without an output APK.",
            updatedProjectConfigJson = result.updatedProjectConfigJson,
        )
        val expectedSha256 = result.outputSha256
        if (expectedSha256 == null || !SHA_256_HEX.matches(expectedSha256)) {
            runCatching(outputFd::close)
                .onFailure { Log.w(TAG, "Failed to close remote output with an invalid SHA-256.", it) }
            return BuildOutcome(
                status = ApkBuildResult.STATUS_FAILED,
                warnings = warnings,
                errors = result.errors + "Remote APK build returned a missing or invalid output SHA-256.",
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }

        val transferError = try {
            progressListener?.onProgress(
                ApkBuildProgress(
                    step = ApkBuildProgress.STEP_SIGN,
                    title = context.getString(org.autojs.autojs6.R.string.text_writing_signed_apk),
                    detail = outApk.path,
                )
            )
            copyOutput(
                outputFd = outputFd,
                outApk = outApk,
                declaredSizeBytes = result.outputSizeBytes,
                expectedSha256 = expectedSha256,
                cancelSignal = cancelSignal,
            )
            null
        } catch (error: Throwable) {
            error
        } finally {
            runCatching(outputFd::close)
                .onFailure { Log.w(TAG, "Failed to close remote build output fd.", it) }
        }
        if (transferError is CancellationException) {
            return BuildOutcome(
                status = ApkBuildResult.STATUS_CANCELLED,
                warnings = warnings,
                errors = result.errors,
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }
        transferError?.let { error ->
            return BuildOutcome(
                status = ApkBuildResult.STATUS_FAILED,
                warnings = warnings,
                errors = result.errors + "Failed to receive remote output APK: ${error.message ?: error}",
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }

        progressListener?.onProgress(
            ApkBuildProgress(
                step = ApkBuildProgress.STEP_SIGN,
                title = context.getString(org.autojs.autojs6.R.string.text_sign_completed),
                detail = outApk.path,
            )
        )

        return BuildOutcome(
            status = ApkBuildResult.STATUS_OK,
            warnings = warnings,
            errors = result.errors,
            updatedProjectConfigJson = result.updatedProjectConfigJson,
        )
    }

    private fun copyOutput(
        outputFd: ParcelFileDescriptor,
        outApk: File,
        declaredSizeBytes: Long,
        expectedSha256: String?,
        cancelSignal: AtomicBoolean?,
    ) {
        ParcelFileDescriptor.AutoCloseInputStream(outputFd).use { input ->
            RemoteBuildOutputTransport.copyToFile(
                input = input,
                outputFile = outApk,
                declaredSizeBytes = declaredSizeBytes,
                expectedSha256 = expectedSha256,
                isCancelled = { cancelSignal?.get() == true },
            )
        }
    }

    private class Callback(
        private val progressListener: ProgressListener?,
        private val elapsedRealtime: () -> Long,
    ) : IApkBuildCallback.Stub() {

        private val latch = CountDownLatch(1)
        private val completed = AtomicBoolean(false)
        private val lastActivityElapsedMillis = AtomicLong(elapsedRealtime())

        @Volatile
        var result: ApkBuildResult? = null
            private set

        fun await(timeoutMillis: Long): Boolean = latch.await(timeoutMillis, TimeUnit.MILLISECONDS)

        fun lastActivityElapsedMillis(): Long = lastActivityElapsedMillis.get()

        fun wakeWaiter() {
            latch.countDown()
        }

        override fun onStarted(progress: ApkBuildProgress) {
            notifyProgress(progress)
        }

        override fun onProgress(progress: ApkBuildProgress) {
            notifyProgress(progress)
        }

        override fun onCompleted(result: ApkBuildResult) {
            complete(result)
        }

        override fun onFailed(result: ApkBuildResult) {
            complete(result.apply {
                if (status == ApkBuildResult.STATUS_OK) {
                    status = ApkBuildResult.STATUS_FAILED
                }
            })
        }

        override fun onCancelled(result: ApkBuildResult) {
            complete(result.apply {
                if (status == ApkBuildResult.STATUS_OK) {
                    status = ApkBuildResult.STATUS_CANCELLED
                }
            })
        }

        private fun notifyProgress(progress: ApkBuildProgress) {
            if (!completed.get()) {
                lastActivityElapsedMillis.set(elapsedRealtime())
                progressListener?.onProgress(progress)
            }
        }

        private fun complete(result: ApkBuildResult) {
            if (completed.compareAndSet(false, true)) {
                lastActivityElapsedMillis.set(elapsedRealtime())
                this.result = result
                latch.countDown()
            }
        }
    }

    private fun unavailableWarningsBlocking(context: Context): List<String> {
        return runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.unavailableWarnings(context)
        }
    }
}
