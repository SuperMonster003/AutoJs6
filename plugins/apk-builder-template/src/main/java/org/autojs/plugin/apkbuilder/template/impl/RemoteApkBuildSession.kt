package org.autojs.plugin.apkbuilder.template.impl

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
        callback?.onStarted(updateProgress(ApkBuildProgress.STEP_PREPARE, "Preparing remote build workspace", null))
        runCatching {
            ensureActive()
            validateHostRequest()
            callback?.onProgress(updateProgress(ApkBuildProgress.STEP_PREPARE, "Reading remote build request", request.outputFileName))

            val preparedWorkspace = RemoteApkBuildWorkspace.prepare(appContext, request)
            workspace = preparedWorkspace
            callback?.onProgress(updateProgress(ApkBuildProgress.STEP_PREPARE, "Remote build request validated", preparedWorkspace.sourcePath.path))

            ensureActive()
            val output = RemoteApkLightweightBuilder(
                context = appContext,
                request = request,
                workspace = preparedWorkspace,
                cancelSignal = cancelled,
                progress = { progress ->
                    this.progress = progress
                    callback?.onProgress(progress)
                },
            ).build()
            val outputFd = ParcelFileDescriptor.open(output.apkFile, ParcelFileDescriptor.MODE_READ_ONLY)
            keepWorkspaceForResult = true
            callback?.onCompleted(
                ApkBuildResult(
                    status = ApkBuildResult.STATUS_OK,
                    outputApkFd = outputFd,
                    outputFileName = output.outputFileName,
                    outputSizeBytes = output.apkFile.length(),
                    outputSha256 = output.sha256,
                    updatedProjectConfigJson = output.updatedProjectConfigJson,
                    compatibilityLevel = ApkBuilderTemplateResult.LEVEL_WARN,
                    warnings = output.warnings,
                    extras = Bundle().apply {
                        putAll(preparedWorkspace.toExtras())
                        putAll(output.extras)
                    },
                )
            )
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
                warnings = unsupported?.message?.let { arrayListOf(it) } ?: arrayListOf(),
                errors = if (unsupported == null && !cancelled.get()) {
                    arrayListOf(error.message ?: error.toString())
                } else {
                    arrayListOf()
                },
            )
            if (cancelled.get()) {
                callback?.onCancelled(result)
            } else {
                callback?.onFailed(result)
            }
        }.also {
            if (!keepWorkspaceForResult) {
                callback?.onProgress(updateProgress(ApkBuildProgress.STEP_CLEAN, "Cleaning remote build workspace", null))
                workspace?.close()
                workspace = null
            }
            if (!closed.get()) {
                progress = updateProgress(ApkBuildProgress.STEP_FINISH, "Remote build session finished", null)
            }
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

    private fun validateHostRequest() {
        if (request.requiredProtocolVersion > ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION) {
            throw IllegalStateException(
                "Host requires newer remote build protocol: host=${request.requiredProtocolVersion}, plugin=${ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION}"
            )
        }
        if (request.hostPackageName.isNotBlank() && request.hostPackageName != BuildConfig.HOST_PACKAGE_NAME) {
            throw IllegalStateException("Host package mismatch: plugin targets ${BuildConfig.HOST_PACKAGE_NAME}, host=${request.hostPackageName}")
        }
        if (request.hostVersionName.isNotBlank() && request.hostVersionName != BuildConfig.HOST_VERSION_NAME) {
            throw IllegalStateException("Host versionName mismatch: plugin=${BuildConfig.HOST_VERSION_NAME}, host=${request.hostVersionName}")
        }
        if (request.hostVersionCode > 0L && request.hostVersionCode != BuildConfig.HOST_VERSION_CODE) {
            throw IllegalStateException("Host versionCode mismatch: plugin=${BuildConfig.HOST_VERSION_CODE}, host=${request.hostVersionCode}")
        }
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
}
