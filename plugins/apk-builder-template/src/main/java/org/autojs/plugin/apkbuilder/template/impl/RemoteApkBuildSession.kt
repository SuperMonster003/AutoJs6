package org.autojs.plugin.apkbuilder.template.impl

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateResult
import org.autojs.plugin.apkbuilder.template.IApkBuildCallback
import org.autojs.plugin.apkbuilder.template.IApkBuildSession
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class RemoteApkBuildSession(
    context: Context,
    private val request: ApkBuildRequest,
    private val callback: IApkBuildCallback?,
    private val executor: Executor,
) : IApkBuildSession.Stub() {

    private val appContext = context.applicationContext
    private val started = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var workspace: RemoteApkBuildWorkspace? = null

    @Volatile
    private var progress = ApkBuildProgress()

    override fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        executor.execute { runSession() }
    }

    override fun cancel() {
        cancelled.set(true)
    }

    override fun close() {
        closed.set(true)
        cancelled.set(true)
        workspace?.close()
        workspace = null
    }

    override fun getProgress(): ApkBuildProgress = progress

    private fun runSession() {
        var keepWorkspaceForResult = false
        val requestWarnings = arrayListOf<String>()
        notifyStarted(updateProgress(ApkBuildProgress.STEP_PREPARE, "Preparing remote build workspace", null))
        runCatching {
            ensureActive()
            requestWarnings += validateHostRequest()
            notifyProgress(updateProgress(ApkBuildProgress.STEP_PREPARE, "Reading remote build request", request.outputFileName))

            val preparedWorkspace = RemoteApkBuildWorkspace.prepare(appContext, request, cancelled)
            workspace = preparedWorkspace
            notifyProgress(updateProgress(ApkBuildProgress.STEP_PREPARE, "Remote build request validated", preparedWorkspace.sourcePath.path))

            ensureActive()
            val output = RemoteApkLightweightBuilder(
                context = appContext,
                request = request,
                workspace = preparedWorkspace,
                cancelSignal = cancelled,
                progress = { progress ->
                    this.progress = progress
                    notifyProgress(progress)
                },
            ).build()
            val outputFd = ParcelFileDescriptor.open(output.apkFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val warnings = (requestWarnings + output.warnings).filter { it.isNotBlank() }.distinct()
            val result = ApkBuildResult(
                status = ApkBuildResult.STATUS_OK,
                outputApkFd = outputFd,
                outputFileName = output.outputFileName,
                outputSizeBytes = output.apkFile.length(),
                outputSha256 = output.sha256,
                updatedProjectConfigJson = output.updatedProjectConfigJson,
                compatibilityLevel = if (warnings.isEmpty()) {
                    ApkBuilderTemplateResult.LEVEL_OK
                } else {
                    ApkBuilderTemplateResult.LEVEL_WARN
                },
                warnings = ArrayList(warnings),
                extras = Bundle().apply {
                    putAll(preparedWorkspace.toExtras())
                    putAll(output.extras)
                },
            )
            keepWorkspaceForResult = notifyCompleted(result)
            closeOutputFd(outputFd)
        }.onFailure { error ->
            val unsupported = error.findUnsupportedCause()
            val status = when {
                cancelled.get() -> ApkBuildResult.STATUS_CANCELLED
                unsupported != null -> ApkBuildResult.STATUS_UNSUPPORTED
                else -> ApkBuildResult.STATUS_FAILED
            }
            val result = ApkBuildResult(
                status = status,
                compatibilityLevel = if (unsupported != null) {
                    ApkBuilderTemplateResult.LEVEL_WARN
                } else {
                    ApkBuilderTemplateResult.LEVEL_BLOCK
                },
                warnings = ArrayList(
                    (requestWarnings + listOfNotNull(unsupported?.message))
                        .filter { it.isNotBlank() }
                        .distinct(),
                ),
                errors = if (unsupported == null && !cancelled.get()) {
                    arrayListOf(error.message ?: error.toString())
                } else {
                    arrayListOf()
                },
            )
            if (cancelled.get()) {
                notifyCancelled(result)
            } else {
                notifyFailed(result)
            }
        }.also {
            if (!keepWorkspaceForResult) {
                notifyProgress(updateProgress(ApkBuildProgress.STEP_CLEAN, "Cleaning remote build workspace", null))
                workspace?.close()
                workspace = null
            }
            if (!closed.get()) {
                progress = updateProgress(ApkBuildProgress.STEP_FINISH, "Remote build session finished", null)
            }
        }
    }

    private fun notifyStarted(progress: ApkBuildProgress) {
        notifyCallback("started") { callback?.onStarted(progress) }
    }

    private fun notifyProgress(progress: ApkBuildProgress) {
        notifyCallback("progress") { callback?.onProgress(progress) }
    }

    private fun notifyCompleted(result: ApkBuildResult): Boolean {
        return notifyTerminalCallback("completed") { callback!!.onCompleted(result) }
    }

    private fun notifyFailed(result: ApkBuildResult): Boolean {
        return notifyTerminalCallback("failed") { callback!!.onFailed(result) }
    }

    private fun notifyCancelled(result: ApkBuildResult): Boolean {
        return notifyTerminalCallback("cancelled") { callback!!.onCancelled(result) }
    }

    private fun notifyTerminalCallback(event: String, block: () -> Unit): Boolean {
        if (callback == null) {
            Log.w(TAG, "Remote build $event callback is missing.")
            return false
        }
        return notifyCallback(event, block)
    }

    private fun notifyCallback(event: String, block: () -> Unit): Boolean {
        return runCatching {
            block()
            true
        }.getOrElse { error ->
            Log.w(TAG, "Failed to send remote build $event callback.", error)
            false
        }
    }

    private fun closeOutputFd(outputFd: ParcelFileDescriptor) {
        runCatching {
            outputFd.close()
        }.onFailure {
            Log.w(TAG, "Failed to close remote build output fd.", it)
        }
    }

    private fun Throwable.findUnsupportedCause(): RemoteApkBuildUnsupportedException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is RemoteApkBuildUnsupportedException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun validateHostRequest(): List<String> {
        val warnings = arrayListOf<String>()
        val info = ApkBuilderTemplateMetadata.templateInfo(appContext)
        if (request.requiredProtocolVersion > ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION) {
            throw IllegalStateException(
                "Host requires newer remote build protocol: host=${request.requiredProtocolVersion}, plugin=${ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION}"
            )
        }
        if (request.hostPackageName.isNotBlank() && request.hostPackageName != info.hostPackageName) {
            warnings += handleRiskyHostMismatch("Host package mismatch: plugin targets ${info.hostPackageName}, host=${request.hostPackageName}")
        }
        if (request.hostVersionName.isNotBlank() && request.hostVersionName != info.hostVersionName) {
            warnings += handleRiskyHostMismatch("Host versionName mismatch: plugin=${info.hostVersionName}, host=${request.hostVersionName}")
        }
        if (request.hostVersionCode > 0L && request.hostVersionCode != info.hostVersionCode) {
            warnings += handleRiskyHostMismatch("Host versionCode mismatch: plugin=${info.hostVersionCode}, host=${request.hostVersionCode}")
        }
        return warnings
    }

    private fun handleRiskyHostMismatch(message: String): String {
        if (!request.allowRiskyBuild) {
            throw IllegalStateException(message)
        }
        return message
    }

    private fun ensureActive() {
        if (closed.get() || cancelled.get()) {
            throw InterruptedException("Remote APK build session was cancelled.")
        }
    }

    private fun updateProgress(step: Int, title: String, detail: String?): ApkBuildProgress {
        return ApkBuildProgress(
            step = step,
            title = title,
            detail = detail,
        ).also { progress = it }
    }

    companion object {
        private const val TAG = "RemoteApkBuildSession"
    }
}
