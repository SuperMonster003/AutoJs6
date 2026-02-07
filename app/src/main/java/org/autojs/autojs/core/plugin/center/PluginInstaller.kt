package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.autojs.autojs.util.DialogUtils.applyProgressThemeColorTintLists
import org.autojs.autojs.util.DialogUtils.setProgressNumberFormatByBytes
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.ui.main.scripts.ApkInfoDialogManager
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.FileUtils.toCacheFile
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.SnackExceptionHolder
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Installer:
 * - Local file: Uri installation
 * - URL: Install after downloading to cache
 *
 * zh-CN:
 *
 * 安装器:
 * - 本地文件: Uri 安装
 * - URL: 下载到缓存后安装
 */
object PluginInstaller {

    suspend fun installFromFileUriWithPrompt(context: Context, uri: Uri) = runCatching {
        ApkInfoDialogManager.showApkInfoDialog(context, uri.toCacheFile(context)) {
            onPositive { dialog, _ ->
                dialog.dismiss()
                if (FileUtils.isLikelyApk(context, uri)) {
                    installFromFileUri(context, uri)
                    return@onPositive
                }
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(context.getString(R.string.prompt_file_may_not_be_a_valid_plugin_package_with_uri, "$uri"))
                    .negativeText(R.string.dialog_button_abandon)
                    .negativeColorRes(R.color.dialog_button_default)
                    .onNegative { d, _ -> d.dismiss() }
                    .positiveText(R.string.dialog_button_continue)
                    .positiveColorRes(R.color.dialog_button_not_recommended)
                    .onPositive { d, _ ->
                        d.dismiss()
                        installFromFileUri(context, uri)
                    }
                    .autoDismiss(false)
                    .cancelable(false)
                    .show()
            }
        }
    }.onFailure { e ->
        MaterialDialog.Builder(context)
            .title(R.string.text_failed_to_install)
            .content(e.message ?: e.toString())
            .positiveText(R.string.dialog_button_dismiss)
            .show()
    }

    fun installFromFileUri(context: Context, uri: Uri) {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, Mime.APPLICATION_VND_ANDROID_PACKAGE_ARCHIVE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.startSafely(context)
    }

    suspend fun installFromUrlWithPrompt(context: Context, url: String, expectedSha256: String? = null) {
        when (val result = downloadWithProgress(context, url, expectedSha256)) {
            is DownloadResult.Success -> {
                installFromFileUriWithPrompt(context, result.uri)
            }
            is DownloadResult.Cancelled -> {
                // User cancelled, no need to prompt.
                // zh-CN: 用户取消, 无需提示.
            }
            is DownloadResult.Failure -> {
                // Wait for user action in the failure dialog (retry/quit).
                // zh-CN: 等待用户在失败对话框中的操作 (重试/放弃).
                val wantRetry = showFailureDialogAndAwaitDecision(context, result)
                if (wantRetry) {
                    installFromUrlWithPrompt(context, url, expectedSha256)
                }
            }
        }
    }

    suspend fun installFromUrl(context: Context, url: String, expectedSha256: String? = null) {
        when (val result = downloadWithProgress(context, url, expectedSha256)) {
            is DownloadResult.Success -> {
                installFromFileUri(context, result.uri)
            }
            is DownloadResult.Cancelled -> {
                // User cancelled, no need to prompt.
                // zh-CN: 用户取消, 无需提示.
            }
            is DownloadResult.Failure -> {
                // Wait for user action in the failure dialog (retry/quit).
                // zh-CN: 等待用户在失败对话框中的操作 (重试/放弃).
                val wantRetry = showFailureDialogAndAwaitDecision(context, result)
                if (wantRetry) {
                    installFromUrl(context, url, expectedSha256)
                }
            }
        }
    }

    // Probe URL size (HEAD).
    // zh-CN: 探测 URL 大小 (HEAD).
    suspend fun probeContentLength(url: String): Long? = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        runCatching {
            conn.connect()
            val code = conn.responseCode
            if (code in 200..299) {
                val len = conn.getHeaderFieldLong("Content-Length", -1L)
                if (len > 0) len else null
            } else null
        }.onFailure { conn.disconnect() }
            .also { conn.disconnect() }
            .getOrNull()
    }

    private suspend fun downloadWithProgress(
        context: Context,
        url: String,
        expectedSha256: String?,
    ): DownloadResult {
        val cancelFlag = AtomicBoolean(false)
        val dialog = MaterialDialog.Builder(context)
            .title(R.string.text_downloading)
            .progress(false, 100, true)
            .neutralText(R.string.dialog_button_download_with_browser)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { d, _ ->
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.text_download_interruption_warning)
                    .negativeText(R.string.dialog_button_back)
                    .negativeColorRes(R.color.dialog_button_hint)
                    .positiveText(R.string.dialog_button_continue)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { _, _ ->
                        d.getActionButton(DialogAction.POSITIVE).performClick()
                        IntentUtils.browse(context, url, SnackExceptionHolder(d.view))
                    }
                    .cancelable(false)
                    .build()
                    .show()
            }
            .positiveText(R.string.dialog_button_cancel_download)
            .positiveColorRes(R.color.dialog_button_default)
            .onPositive { d, _ ->
                cancelFlag.set(true)
                d.getActionButton(DialogAction.POSITIVE).isEnabled = false
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()

        dialog.applyProgressThemeColorTintLists()
        dialog.setProgressNumberFormat(context.getString(R.string.text_half_ellipsis))
        dialog.setProgress(0)

        try {
            val cache = File(context.cacheDir, "plugin_dl").apply { if (!exists()) mkdirs() }
            val name = guessFileName(url)
            val out = File(cache, name)

            val (len, sha256Hex) = withContext(Dispatchers.IO) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                conn.connect()
                val code = conn.responseCode
                if (code !in 200..299) throw HttpStatusException(code, conn.responseMessage ?: "HTTP error")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L

                dialog.setProgressNumberFormatByBytes(0, total, true)

                conn.inputStream.use { input ->
                    val md = MessageDigest.getInstance("SHA-256")
                    DigestInputStream(input, md).use { din ->
                        out.outputStream().use { fos ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read: Int
                            var downloaded = 0L
                            var lastUpdateTs = 0L
                            while (true) {
                                if (cancelFlag.get()) throw CancellationException("User cancelled")
                                read = din.read(buf)
                                if (read == -1) break
                                fos.write(buf, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()

                                if (total > 0 && (now - lastUpdateTs > 80)) {
                                    val pct = ((downloaded * 100f) / total).coerceIn(0f, 100f)
                                    withContext(Dispatchers.Main) {
                                        dialog.setProgressNumberFormatByBytes(downloaded, total, true)
                                        dialog.setProgress(pct.roundToInt())
                                    }
                                    lastUpdateTs = now
                                } else if (total <= 0 && (now - lastUpdateTs > 300)) {
                                    withContext(Dispatchers.Main) {
                                        val cur = dialog.currentProgress
                                        dialog.setProgress((cur + 1).coerceAtMost(99))
                                    }
                                    lastUpdateTs = now
                                }
                            }
                            fos.flush()
                        }
                    }
                    val hex = md.digest().joinToString("") { "%02x".format(it) }
                    Pair(if (total > 0) total else out.length(), hex)
                }
            }

            if (expectedSha256 != null && !expectedSha256.equals(sha256Hex, ignoreCase = true)) {
                throw ChecksumMismatchException(expectedSha256, sha256Hex)
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
            return DownloadResult.Success(uri, len, sha256Hex)
        } catch (_: CancellationException) {
            return DownloadResult.Cancelled
        } catch (he: HttpStatusException) {
            return DownloadResult.Failure(R.string.text_failed_to_retrieve, "HTTP ${he.code}: ${he.message}")
        } catch (me: ChecksumMismatchException) {
            return DownloadResult.Failure(
                R.string.text_integrity_verification_failed,
                context.getString(R.string.text_sha256_mismatch_multiline_expected_actual, me.expected, me.actual),
            )
        } catch (ioe: EOFException) {
            return DownloadResult.Failure(R.string.text_failed_to_retrieve, "Unexpected EOF: ${ioe.message}")
        } catch (ioe: IOException) {
            return DownloadResult.Failure(R.string.text_failed_to_retrieve, "Network/IO error: ${ioe.message}")
        } catch (e: SecurityException) {
            return DownloadResult.Failure(R.string.text_failed_to_install, "Security error: ${e.message}")
        } catch (e: Throwable) {
            return DownloadResult.Failure(R.string.text_failed_to_retrieve, e.message ?: e.toString())
        } finally {
            dialog.dismiss()
        }
    }

    private fun guessFileName(url: String): String {
        val last = url.substringAfterLast('/').substringBefore('?')
        require(last.isNotBlank()) { "Invalid url: $url" }
        return if (last.endsWith(".apk", ignoreCase = true)) last else "$last.apk"
    }

    private suspend fun showFailureDialogAndAwaitDecision(
        context: Context,
        result: DownloadResult.Failure,
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            var resumed = false

            fun tryResume(value: Boolean) {
                if (resumed) return
                resumed = true
                if (cont.isActive) {
                    cont.resume(value) { _, _, _ -> }
                }
            }

            MaterialDialog.Builder(context)
                .title(result.titleRes)
                .content(result.message)
                .neutralText(R.string.text_copy_all)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { d, _ ->
                    ClipboardUtils.setClip(context, result.message)
                    ViewUtils.showSnack(d.view, R.string.text_already_copied_to_clip, false)
                }
                .negativeText(R.string.dialog_button_abandon)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ ->
                    d.dismiss()
                    tryResume(false)
                }
                .positiveText(R.string.dialog_button_retry)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ ->
                    d.dismiss()
                    tryResume(true)
                }
                .cancelable(false)
                .autoDismiss(false)
                .show()
        }
    }

    private data class HttpStatusException(val code: Int, override val message: String) : RuntimeException(message)

    private data class ChecksumMismatchException(val expected: String, val actual: String) : RuntimeException("sha256 mismatch")

    sealed interface DownloadResult {
        data class Success(val uri: Uri, val length: Long, val sha256: String) : DownloadResult
        data class Failure(val titleRes: Int, val message: String) : DownloadResult
        data object Cancelled : DownloadResult
    }

}