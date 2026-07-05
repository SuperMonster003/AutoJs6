package org.autojs.plugin.apkbuilder.template.impl

import android.content.Context
import android.os.Bundle
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
        callback?.onStarted(updateProgress(ApkBuildProgress.STEP_PREPARE, "Preparing remote build workspace", null))
        runCatching {
            ensureActive()
            validateHostRequest()
            callback?.onProgress(updateProgress(ApkBuildProgress.STEP_PREPARE, "Reading remote build request", request.outputFileName))

            val preparedWorkspace = RemoteApkBuildWorkspace.prepare(appContext, request)
            workspace = preparedWorkspace
            callback?.onProgress(updateProgress(ApkBuildProgress.STEP_PREPARE, "Remote build request validated", preparedWorkspace.sourcePath.path))

            ensureActive()
            callback?.onProgress(updateProgress(ApkBuildProgress.STEP_BUILD, "Remote APK build is not implemented", null))
            callback?.onFailed(
                ApkBuildResult(
                    status = ApkBuildResult.STATUS_UNSUPPORTED,
                    compatibilityLevel = ApkBuilderTemplateResult.LEVEL_BLOCK,
                    warnings = arrayListOf("Remote build request was accepted and staged successfully."),
                    errors = arrayListOf("Remote APK build execution is not implemented by this plugin version."),
                    extras = Bundle().apply {
                        putAll(preparedWorkspace.toExtras())
                    },
                )
            )
        }.onFailure { error ->
            val result = ApkBuildResult(
                status = if (cancelled.get()) ApkBuildResult.STATUS_CANCELLED else ApkBuildResult.STATUS_FAILED,
                compatibilityLevel = ApkBuilderTemplateResult.LEVEL_BLOCK,
                errors = arrayListOf(error.message ?: error.toString()),
            )
            if (cancelled.get()) {
                callback?.onCancelled(result)
            } else {
                callback?.onFailed(result)
            }
        }.also {
            callback?.onProgress(updateProgress(ApkBuildProgress.STEP_CLEAN, "Cleaning remote build workspace", null))
            workspace?.close()
            workspace = null
            if (!closed.get()) {
                progress = updateProgress(ApkBuildProgress.STEP_FINISH, "Remote build session finished", null)
            }
        }
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
