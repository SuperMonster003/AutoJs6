package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs6.R
import org.autojs.autojs.project.ProjectConfig
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
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
        /** The Bundled Libraries modules packaged or missing, for the success dialog (roadmap P7.1, Q186). */
        val notices: List<String> = emptyList(),
    ) {
        val isSuccess: Boolean
            get() = status == ApkBuildResult.STATUS_OK
    }

    internal fun outcomeForCaughtFailure(
        error: Throwable,
        cancelRequested: Boolean,
    ): BuildOutcome? =
        if (error is CancellationException || cancelRequested) {
            BuildOutcome(status = ApkBuildResult.STATUS_CANCELLED)
        } else {
            null
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
        val diagnosticTracker = DiagnosticTracker(buildStartedAtElapsedMillis)
        return try {
            buildBlockingInternal(
                context = context,
                projectConfig = projectConfig,
                outApk = outApk,
                cancelSignal = cancelSignal,
                progressListener = progressListener,
                preferredPluginPackageName = preferredPluginPackageName,
                buildStartedAtElapsedMillis = buildStartedAtElapsedMillis,
                diagnosticTracker = diagnosticTracker,
            ).also { outcome ->
                diagnosticTracker.record(outcome.status)
            }
        } catch (error: Throwable) {
            val cancellationOutcome = outcomeForCaughtFailure(
                error = error,
                cancelRequested = cancelSignal?.get() == true,
            )
            if (cancellationOutcome != null) {
                diagnosticTracker.mark(RemoteApkBuildErrorCategory.CANCELLED)
                diagnosticTracker.record(ApkBuildResult.STATUS_CANCELLED)
                cancellationOutcome
            } else {
                diagnosticTracker.markUnhandledFailure()
                diagnosticTracker.record(ApkBuildResult.STATUS_FAILED)
                throw error
            }
        }
    }

    private fun buildBlockingInternal(
        context: Context,
        projectConfig: ProjectConfig,
        outApk: File,
        cancelSignal: AtomicBoolean?,
        progressListener: ProgressListener?,
        preferredPluginPackageName: String?,
        buildStartedAtElapsedMillis: Long,
        diagnosticTracker: DiagnosticTracker,
    ): BuildOutcome {
        val appContext = context.applicationContext
        diagnosticTracker.moveTo(RemoteApkBuildDiagnosticPhase.DISCOVERY)
        val hasRemoteBuildPlugin = runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.hasRemoteBuildPlugin(
                appContext,
                preferredPackageName = preferredPluginPackageName,
            )
        }
        if (!hasRemoteBuildPlugin) {
            diagnosticTracker.mark(RemoteApkBuildErrorCategory.PROVIDER_NOT_AVAILABLE)
            return BuildOutcome(
                status = STATUS_NOT_AVAILABLE,
                warnings = unavailableWarningsBlocking(appContext),
            )
        }

        diagnosticTracker.moveTo(RemoteApkBuildDiagnosticPhase.PREPARE)
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
            diagnosticTracker.moveTo(RemoteApkBuildDiagnosticPhase.OPEN_SESSION)
            val opened = runBlocking(Dispatchers.IO) {
                ApkBuilderTemplatePluginHost.openBuildSession(
                    appContext,
                    prepared.request,
                    callback,
                    preferredPluginPackageName,
                )
            }
            if (opened == null) {
                diagnosticTracker.mark(RemoteApkBuildErrorCategory.PROVIDER_NOT_AVAILABLE)
                return BuildOutcome(
                    status = STATUS_NOT_AVAILABLE,
                    warnings = unavailableWarningsBlocking(appContext),
                )
            }

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
                    diagnosticTracker.mark(RemoteApkBuildErrorCategory.BINDER_LOST)
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
                    diagnosticTracker.mark(
                        if (binderLost.get()) {
                            RemoteApkBuildErrorCategory.BINDER_LOST
                        } else {
                            RemoteApkBuildErrorCategory.SESSION_OPEN_FAILED
                        }
                    )
                    return failedSessionOutcome(
                        if (binderLost.get()) {
                            "Remote APK build session was lost while starting."
                        } else {
                            "Failed to start remote APK build session: ${error.message ?: error}"
                        },
                        opened.hostCompatibilityWarnings,
                    )
                }

                diagnosticTracker.moveTo(RemoteApkBuildDiagnosticPhase.WAIT)
                while (true) {
                    val terminalCallbackReceived = try {
                        callback.await(WAIT_SLICE_MILLIS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        runCatching { session.cancel() }
                            .onFailure { Log.w(TAG, "Failed to cancel interrupted remote build session", it) }
                        diagnosticTracker.mark(RemoteApkBuildErrorCategory.CANCELLED)
                        return BuildOutcome(status = ApkBuildResult.STATUS_CANCELLED)
                    }
                    if (terminalCallbackReceived) {
                        break
                    }
                    if (cancelSignal?.get() == true || Thread.currentThread().isInterrupted) {
                        runCatching { session.cancel() }
                            .onFailure { Log.w(TAG, "Failed to cancel remote build session", it) }
                        diagnosticTracker.mark(RemoteApkBuildErrorCategory.CANCELLED)
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
                            diagnosticTracker.mark(RemoteApkBuildErrorCategory.BINDER_LOST)
                            return failedSessionOutcome(
                                "Remote APK build session was lost before completion.",
                                opened.hostCompatibilityWarnings,
                            )
                        }
                        RemoteBuildWaitPolicy.Decision.NO_PROGRESS_TIMEOUT -> {
                            runCatching { session.cancel() }
                                .onFailure { Log.w(TAG, "Failed to cancel stalled remote build session", it) }
                            diagnosticTracker.mark(RemoteApkBuildErrorCategory.NO_PROGRESS_TIMEOUT)
                            return failedSessionOutcome(
                                "Remote APK build made no progress for 12 minutes and was cancelled.",
                                opened.hostCompatibilityWarnings,
                            )
                        }
                        RemoteBuildWaitPolicy.Decision.TOTAL_TIMEOUT -> {
                            runCatching { session.cancel() }
                                .onFailure { Log.w(TAG, "Failed to cancel timed-out remote build session", it) }
                            diagnosticTracker.mark(RemoteApkBuildErrorCategory.TOTAL_TIMEOUT)
                            return failedSessionOutcome(
                                "Remote APK build exceeded the 30-minute total time limit and was cancelled.",
                                opened.hostCompatibilityWarnings,
                            )
                        }
                    }
                }
                val result = callback.result ?: return if (binderLost.get() || !sessionBinder.isBinderAlive) {
                    diagnosticTracker.mark(RemoteApkBuildErrorCategory.BINDER_LOST)
                    failedSessionOutcome(
                        "Remote APK build session was lost before returning a result.",
                        opened.hostCompatibilityWarnings,
                    )
                } else {
                    diagnosticTracker.mark(RemoteApkBuildErrorCategory.REMOTE_FAILED)
                    failedSessionOutcome(
                        "Remote APK build finished without a result.",
                        opened.hostCompatibilityWarnings,
                    )
                }
                return handleResult(
                    context = context,
                    result = result,
                    outApk = outApk,
                    expectedIdentity = RemoteBuildExpectedApkIdentity(
                        packageName = projectConfig.packageName,
                        versionName = projectConfig.versionName,
                        versionCode = projectConfig.versionCode.toLong(),
                        signerCertificateSha256 = prepared.expectedSignerCertificateSha256,
                    ),
                    cancelSignal = cancelSignal,
                    progressListener = progressListener,
                    hostCompatibilityWarnings = opened.hostCompatibilityWarnings + prepared.warnings,
                    diagnosticTracker = diagnosticTracker,
                ).copy(notices = prepared.notices)
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
        expectedIdentity: RemoteBuildExpectedApkIdentity,
        cancelSignal: AtomicBoolean?,
        progressListener: ProgressListener?,
        hostCompatibilityWarnings: List<String>,
        diagnosticTracker: DiagnosticTracker,
    ): BuildOutcome {
        val warnings = (hostCompatibilityWarnings + result.warnings).filter { it.isNotBlank() }.distinct()
        if (result.status != ApkBuildResult.STATUS_OK) {
            result.outputApkFd?.let { outputFd ->
                runCatching(outputFd::close)
                    .onFailure { Log.w(TAG, "Failed to close unexpected remote build output fd.", it) }
            }
            diagnosticTracker.moveTo(RemoteApkBuildDiagnosticPhase.COMPLETE)
            diagnosticTracker.mark(
                when (result.status) {
                    ApkBuildResult.STATUS_CANCELLED -> RemoteApkBuildErrorCategory.CANCELLED
                    ApkBuildResult.STATUS_UNSUPPORTED -> RemoteApkBuildErrorCategory.REMOTE_UNSUPPORTED
                    else -> RemoteApkBuildErrorCategory.REMOTE_FAILED
                }
            )
            return BuildOutcome(
                status = result.status,
                warnings = warnings,
                errors = result.errors,
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }

        val outputFd = result.outputApkFd
        if (outputFd == null) {
            diagnosticTracker.mark(RemoteApkBuildErrorCategory.OUTPUT_METADATA_INVALID)
            return BuildOutcome(
                status = ApkBuildResult.STATUS_FAILED,
                warnings = warnings,
                errors = result.errors + "Remote APK build completed without an output APK.",
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }
        val expectedSha256 = result.outputSha256
        if (expectedSha256 == null || !SHA_256_HEX.matches(expectedSha256)) {
            runCatching(outputFd::close)
                .onFailure { Log.w(TAG, "Failed to close remote output with an invalid SHA-256.", it) }
            diagnosticTracker.mark(RemoteApkBuildErrorCategory.OUTPUT_METADATA_INVALID)
            return BuildOutcome(
                status = ApkBuildResult.STATUS_FAILED,
                warnings = warnings,
                errors = result.errors + "Remote APK build returned a missing or invalid output SHA-256.",
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }

        diagnosticTracker.moveTo(RemoteApkBuildDiagnosticPhase.TRANSFER)
        diagnosticTracker.setOutputSizeBytes(result.outputSizeBytes)
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
                context = context,
                expectedIdentity = expectedIdentity,
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
            diagnosticTracker.mark(RemoteApkBuildErrorCategory.CANCELLED)
            return BuildOutcome(
                status = ApkBuildResult.STATUS_CANCELLED,
                warnings = warnings,
                errors = result.errors,
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }
        transferError?.let { error ->
            diagnosticTracker.mark(RemoteApkBuildErrorCategory.OUTPUT_REJECTED)
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

        diagnosticTracker.moveTo(RemoteApkBuildDiagnosticPhase.COMPLETE)
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
        context: Context,
        expectedIdentity: RemoteBuildExpectedApkIdentity,
        cancelSignal: AtomicBoolean?,
    ) {
        ParcelFileDescriptor.AutoCloseInputStream(outputFd).use { input ->
            RemoteBuildOutputTransport.copyToFile(
                input = input,
                outputFile = outApk,
                declaredSizeBytes = declaredSizeBytes,
                expectedSha256 = expectedSha256,
                isCancelled = { cancelSignal?.get() == true },
                validateTemporaryFile = { temporaryFile ->
                    RemoteBuildApkValidator.validate(context, temporaryFile, expectedIdentity)
                },
            )
        }
    }

    private class DiagnosticTracker(
        private val startedAtElapsedMillis: Long,
    ) {
        private var phase = RemoteApkBuildDiagnosticPhase.DISCOVERY
        private var errorCategory = RemoteApkBuildErrorCategory.NONE
        private var outputSizeBytes = -1L
        private val recorded = AtomicBoolean(false)

        fun moveTo(nextPhase: RemoteApkBuildDiagnosticPhase) {
            phase = nextPhase
        }

        fun mark(category: RemoteApkBuildErrorCategory) {
            errorCategory = category
        }

        fun setOutputSizeBytes(sizeBytes: Long) {
            outputSizeBytes = sizeBytes
        }

        fun markUnhandledFailure() {
            if (errorCategory != RemoteApkBuildErrorCategory.NONE) {
                return
            }
            errorCategory = when (phase) {
                RemoteApkBuildDiagnosticPhase.DISCOVERY -> RemoteApkBuildErrorCategory.DISCOVERY_FAILED
                RemoteApkBuildDiagnosticPhase.PREPARE -> RemoteApkBuildErrorCategory.PREPARATION_FAILED
                RemoteApkBuildDiagnosticPhase.OPEN_SESSION -> RemoteApkBuildErrorCategory.SESSION_OPEN_FAILED
                RemoteApkBuildDiagnosticPhase.WAIT,
                RemoteApkBuildDiagnosticPhase.COMPLETE -> RemoteApkBuildErrorCategory.REMOTE_FAILED
                RemoteApkBuildDiagnosticPhase.TRANSFER -> RemoteApkBuildErrorCategory.OUTPUT_REJECTED
            }
        }

        fun record(status: Int) {
            if (!recorded.compareAndSet(false, true)) {
                return
            }
            val diagnosticStatus = when (status) {
                STATUS_NOT_AVAILABLE -> RemoteApkBuildDiagnosticStatus.NOT_AVAILABLE
                ApkBuildResult.STATUS_OK -> RemoteApkBuildDiagnosticStatus.SUCCEEDED
                ApkBuildResult.STATUS_FAILED -> RemoteApkBuildDiagnosticStatus.FAILED
                ApkBuildResult.STATUS_CANCELLED -> RemoteApkBuildDiagnosticStatus.CANCELLED
                ApkBuildResult.STATUS_UNSUPPORTED -> RemoteApkBuildDiagnosticStatus.UNSUPPORTED
                else -> RemoteApkBuildDiagnosticStatus.OTHER
            }
            if (errorCategory == RemoteApkBuildErrorCategory.NONE) {
                errorCategory = when (diagnosticStatus) {
                    RemoteApkBuildDiagnosticStatus.NOT_AVAILABLE ->
                        RemoteApkBuildErrorCategory.PROVIDER_NOT_AVAILABLE
                    RemoteApkBuildDiagnosticStatus.FAILED,
                    RemoteApkBuildDiagnosticStatus.OTHER -> RemoteApkBuildErrorCategory.REMOTE_FAILED
                    RemoteApkBuildDiagnosticStatus.CANCELLED -> RemoteApkBuildErrorCategory.CANCELLED
                    RemoteApkBuildDiagnosticStatus.UNSUPPORTED -> RemoteApkBuildErrorCategory.REMOTE_UNSUPPORTED
                    RemoteApkBuildDiagnosticStatus.SUCCEEDED -> RemoteApkBuildErrorCategory.NONE
                }
            }
            if (diagnosticStatus == RemoteApkBuildDiagnosticStatus.SUCCEEDED) {
                phase = RemoteApkBuildDiagnosticPhase.COMPLETE
            }
            val event = RemoteApkBuildDiagnostics.record(
                protocolVersion = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
                status = diagnosticStatus,
                phase = phase,
                elapsedMillis = SystemClock.elapsedRealtime() - startedAtElapsedMillis,
                outputSizeBytes = outputSizeBytes,
                errorCategory = errorCategory,
            )
            Log.i(TAG, event.safeLogLine())
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
