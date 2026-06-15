package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import android.net.Uri
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.storage.file.StableDraftFileHelper
import org.autojs.autojs.storage.history.HistoryPrefs
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadMetadata
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadPipeline
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadResult
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class EditorFileService(
    private val context: Context,
    private val hostView: View,
    private val versionHistoryService: EditorVersionHistoryService,
    private val onSaveAsSucceeded: (File) -> Unit,
) {

    private val fileReadPipeline = EditorFileReadPipeline(context)

    fun queryContentLengthOrNull(uri: Uri): Long? {
        return fileReadPipeline.queryContentLengthOrNull(uri)
    }

    fun readText(uri: Uri): EditorFileReadResult {
        return fileReadPipeline.readText(uri)
    }

    fun streamTextChunks(
        uri: Uri,
        cancel: AtomicBoolean,
        streamRef: AtomicReference<InputStream?>,
        onMetadata: (EditorFileReadMetadata) -> Unit,
    ): Observable<String> {
        return fileReadPipeline.streamTextChunks(
            uri = uri,
            cancel = cancel,
            streamRef = streamRef,
            onMetadata = onMetadata,
        )
    }

    fun saveTextTransactional(
        uri: Uri,
        text: String,
        encoding: EditorFileSaveEncoding,
        displayName: String,
    ) {
        writeTextWithCharsetTransactional(
            uri = uri,
            text = text,
            encoding = encoding,
            displayName = displayName,
        )
        StableDraftFileHelper(context, uri.path).deleteDraft()
    }

    private fun writeTextWithCharsetTransactional(
        uri: Uri,
        text: String,
        encoding: EditorFileSaveEncoding,
        displayName: String,
    ) {
        val resolver = context.contentResolver

        val (targetCharset, needBom) = when {
            encoding.hadBom -> {
                encoding.charset to true
            }
            encoding.charsetConfidence >= MIN_CONFIDENCE_TO_WRITE_FILE -> {
                encoding.charset to false
            }
            else -> {
                DEFAULT_CHARSET_TO_WRITE_FILE to false
            }
        }

        val newBytes = buildBytesToWrite(text, targetCharset, needBom)
        val oldBytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

        versionHistoryService.recordSavePreIfNeeded(
            uri = uri,
            oldBytes = oldBytes,
            newBytes = newBytes,
            encodingName = targetCharset.name(),
            hadBom = needBom,
        )

        val newHash = sha256(newBytes)

        try {
            resolver.openOutputStream(uri, "rwt")?.use { out ->
                out.write(newBytes)
                out.flush()
            } ?: throw IOException("Cannot open output stream for $uri")

            val readBack = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            }.getOrElse { e ->
                throw IOException("Read-back failed after write: $uri", e)
            }

            val readBackHash = sha256(readBack)
            if (!readBackHash.contentEquals(newHash)) {
                throw IOException("Write verification failed (hash mismatch) for uri: $uri")
            }
        } catch (e: SecurityException) {
            handleSaveFailure(uri, displayName, oldBytes, newBytes, IOException("Permission denied for uri: $uri", e))
        } catch (e: Throwable) {
            handleSaveFailure(uri, displayName, oldBytes, newBytes, e)
        }
    }

    private fun handleSaveFailure(
        uri: Uri,
        displayName: String,
        oldBytes: ByteArray?,
        newBytes: ByteArray,
        error: Throwable,
    ): Nothing {
        val resolver = context.contentResolver

        if (oldBytes != null) {
            runCatching {
                resolver.openOutputStream(uri, "rwt")?.use { out ->
                    out.write(oldBytes)
                    out.flush()
                }
            }
        }

        val draftFile = runCatching {
            EmergencyDraftStore(context).saveDraft(
                displayName = displayName.ifBlank { "untitled" },
                bytes = newBytes,
            )
        }.getOrNull()

        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.error_save_failed)
                .content(
                    buildString {
                        append(error.message ?: error.toString())
                        if (draftFile != null) {
                            append("\n\n")
                            append(context.getString(R.string.text_draft_file_path))
                            append(":\n")
                            append(draftFile.absolutePath)
                        }
                    }
                )
                .also { builder ->
                    if (draftFile != null) {
                        builder.neutralText(R.string.dialog_button_copy_path)
                        builder.neutralColorRes(R.color.dialog_button_hint)
                        builder.onNeutral { d, _ ->
                            ClipboardUtils.setClip(context, draftFile.absolutePath)
                            showSnack(d.view, R.string.text_already_copied_to_clip, false)
                        }
                    }
                }
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.dialog_button_save_as)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ -> showSaveAsDialog(d, uri, displayName, newBytes) }
                .autoDismiss(false)
                .cancelable(false)
                .build()
        }

        if (error is IOException) {
            throw error
        }
        throw IOException(error)
    }

    private fun showSaveAsDialog(
        failureDialog: MaterialDialog,
        uri: Uri,
        displayName: String,
        newBytes: ByteArray,
    ) {
        runCatching {
            FileChooserDialogBuilder(context)
                .title(R.string.text_save_to)
                .dir(INTERNAL_STORAGE_ROOT)
                .chooseDir()
                .singleChoice()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ dir ->
                    val ext = uri.path?.let { targetExtFromPath(it) } ?: ""
                    val dest = File(
                        dir.path,
                        buildSafeFileNameForSaveAs(
                            baseName = displayName,
                            ext = ext,
                        )
                    )

                    Schedulers.io().scheduleDirect {
                        runCatching {
                            dest.parentFile?.mkdirs()
                            File(dest.path).outputStream().use { it.write(newBytes) }

                            hostView.post {
                                failureDialog.dismiss()
                                onSaveAsSucceeded(dest)
                            }
                        }.onFailure {
                            hostView.post {
                                showSnack(failureDialog.view, R.string.error_save_failed, false)
                                showToast(context, it.message, true)
                            }
                        }
                    }
                }, { e ->
                    e.printStackTrace()
                })
        }
    }

    private fun buildBytesToWrite(text: String, charset: Charset, needBom: Boolean): ByteArray {
        val body = text.toByteArray(charset)
        if (!needBom) return body

        val bom = StringUtils.bomBytes(charset)
        return ByteArray(bom.size + body.size).also {
            System.arraycopy(bom, 0, it, 0, bom.size)
            System.arraycopy(body, 0, it, bom.size, body.size)
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun targetExtFromPath(path: String): String {
        val name = File(path).name
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot >= name.length - 1) return ""
        return name.substring(dot + 1)
    }

    private fun buildSafeFileNameForSaveAs(baseName: String, ext: String): String {
        val raw = baseName.ifBlank { "untitled" }
        val sanitized = raw.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val niceExt = ext.trim().trimStart('.')
        return if (niceExt.isBlank()) sanitized else "$sanitized.$niceExt"
    }

    private class EmergencyDraftStore(private val context: Context) {

        private val draftsDir: File by lazy { File(context.filesDir, "drafts") }

        fun saveDraft(displayName: String, bytes: ByteArray): File {
            draftsDir.mkdirs()

            val safeName = displayName
                .ifBlank { "untitled" }
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .lowercase(Locale.getDefault())

            val now = System.currentTimeMillis()
            val f = File(draftsDir, "${now}_${safeName}.bin")

            f.outputStream().use { it.write(bytes) }
            runCatching { cleanupLocked() }

            return f
        }

        private fun cleanupLocked() {
            val files = draftsDir.listFiles()?.toList() ?: return
            if (files.isEmpty()) return

            val now = System.currentTimeMillis()
            val maxDraftLifetime = HistoryPrefs.draftsMaxDays().coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L
            val expiredBefore = now - maxDraftLifetime
            files.forEach { f ->
                if (f.lastModified() < expiredBefore) {
                    f.delete()
                }
            }

            val remained = draftsDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: return
            var total = remained.sumOf { it.length().coerceAtLeast(0L) }
            val draftLimit = HistoryPrefs.draftsMaxTotalBytes().coerceAtLeast(0L)

            if (total <= draftLimit) return

            for (f in remained.asReversed()) {
                if (total <= draftLimit) break
                val len = f.length().coerceAtLeast(0L)
                if (f.delete()) {
                    total -= len
                }
            }
        }
    }

    companion object {
        const val MIN_CONFIDENCE_TO_WRITE_FILE = 90
        val DEFAULT_CHARSET_TO_WRITE_FILE: Charset = StandardCharsets.UTF_8

        private const val INTERNAL_STORAGE_ROOT = "/storage/emulated/0"
    }
}

data class EditorFileSaveEncoding(
    val charsetConfidence: Int,
    val charset: Charset,
    val hadBom: Boolean,
)
