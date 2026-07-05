package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.project.ProjectConfig
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.IApkBuildCallback
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object RemoteApkBuildClient {

    private const val TAG = "RemoteApkBuildClient"
    private const val WAIT_SLICE_MILLIS = 250L
    private const val BUFFER_SIZE = 256 * 1024

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

        companion object {
            const val STATUS_NOT_AVAILABLE = 100
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun buildBlocking(
        context: Context,
        projectConfig: ProjectConfig,
        outApk: File,
        cancelSignal: AtomicBoolean?,
        progressListener: ProgressListener? = null,
    ): BuildOutcome {
        val appContext = context.applicationContext
        val hasRemoteBuildPlugin = runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.hasRemoteBuildPlugin(appContext)
        }
        if (!hasRemoteBuildPlugin) {
            return BuildOutcome(
                status = BuildOutcome.STATUS_NOT_AVAILABLE,
                warnings = unavailableWarningsBlocking(appContext),
            )
        }

        val prepared = RemoteApkBuildRequestPreparer.prepare(
            context = appContext,
            projectConfig = projectConfig,
            outputFileName = outApk.name,
        )
        try {
            val callback = Callback(progressListener)
            val opened = runBlocking(Dispatchers.IO) {
                ApkBuilderTemplatePluginHost.openBuildSession(appContext, prepared.request, callback)
            } ?: return BuildOutcome(
                status = BuildOutcome.STATUS_NOT_AVAILABLE,
                warnings = unavailableWarningsBlocking(appContext),
            )

            val session = opened.session
            try {
                session.start()
                while (!callback.await(WAIT_SLICE_MILLIS)) {
                    if (cancelSignal?.get() == true || Thread.currentThread().isInterrupted) {
                        runCatching { session.cancel() }
                            .onFailure { Log.w(TAG, "Failed to cancel remote build session", it) }
                        return BuildOutcome(status = ApkBuildResult.STATUS_CANCELLED)
                    }
                }
                val result = callback.result ?: return BuildOutcome(
                    status = ApkBuildResult.STATUS_FAILED,
                    errors = listOf("Remote APK build finished without a result."),
                )
                return handleResult(result, outApk)
            } finally {
                runCatching { session.close() }
            }
        } finally {
            prepared.cleanup()
        }
    }

    private fun handleResult(result: ApkBuildResult, outApk: File): BuildOutcome {
        if (result.status != ApkBuildResult.STATUS_OK) {
            return BuildOutcome(
                status = result.status,
                warnings = result.warnings,
                errors = result.errors,
                updatedProjectConfigJson = result.updatedProjectConfigJson,
            )
        }

        val outputFd = result.outputApkFd ?: return BuildOutcome(
            status = ApkBuildResult.STATUS_FAILED,
            warnings = result.warnings,
            errors = result.errors + "Remote APK build completed without an output APK.",
            updatedProjectConfigJson = result.updatedProjectConfigJson,
        )

        outApk.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        copyOutput(outputFd, outApk)

        val expectedSha256 = result.outputSha256
        if (!expectedSha256.isNullOrBlank()) {
            val actualSha256 = sha256(outApk)
            if (!expectedSha256.equals(actualSha256, ignoreCase = true)) {
                outApk.delete()
                return BuildOutcome(
                    status = ApkBuildResult.STATUS_FAILED,
                    warnings = result.warnings,
                    errors = result.errors + "Remote output APK SHA-256 mismatch: expected=$expectedSha256 actual=$actualSha256",
                    updatedProjectConfigJson = result.updatedProjectConfigJson,
                )
            }
        }

        return BuildOutcome(
            status = ApkBuildResult.STATUS_OK,
            warnings = result.warnings,
            errors = result.errors,
            updatedProjectConfigJson = result.updatedProjectConfigJson,
        )
    }

    private fun copyOutput(outputFd: ParcelFileDescriptor, outApk: File) {
        ParcelFileDescriptor.AutoCloseInputStream(outputFd).use { input ->
            FileOutputStream(outApk, false).use { output ->
                input.copyTo(output, BUFFER_SIZE)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private class Callback(
        private val progressListener: ProgressListener?,
    ) : IApkBuildCallback.Stub() {

        private val latch = CountDownLatch(1)

        @Volatile
        var result: ApkBuildResult? = null
            private set

        fun await(timeoutMillis: Long): Boolean = latch.await(timeoutMillis, TimeUnit.MILLISECONDS)

        override fun onStarted(progress: ApkBuildProgress) {
            progressListener?.onProgress(progress)
        }

        override fun onProgress(progress: ApkBuildProgress) {
            progressListener?.onProgress(progress)
        }

        override fun onCompleted(result: ApkBuildResult) {
            this.result = result
            latch.countDown()
        }

        override fun onFailed(result: ApkBuildResult) {
            this.result = result.apply {
                if (status == ApkBuildResult.STATUS_OK) {
                    status = ApkBuildResult.STATUS_FAILED
                }
            }
            latch.countDown()
        }

        override fun onCancelled(result: ApkBuildResult) {
            this.result = result.apply {
                if (status == ApkBuildResult.STATUS_OK) {
                    status = ApkBuildResult.STATUS_CANCELLED
                }
            }
            latch.countDown()
        }
    }

    private fun unavailableWarningsBlocking(context: Context): List<String> {
        return runBlocking(Dispatchers.IO) {
            ApkBuilderTemplatePluginHost.unavailableWarnings(context)
        }
    }
}
