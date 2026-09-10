package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.afollestad.materialdialogs.MaterialDialog
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.ui.dialog.InfoDialog
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.SnackExceptionHolder
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.StringUtils.dropBom
import org.autojs.autojs6.R

object EditableFileInfoDialogManager {

    @JvmStatic
    @SuppressLint("SetTextI18n")
    fun showEditableFileInfoDialog(context: Context, file: File?, fileContentGetter: (() -> String)? = null): InfoDialog? {
        if (file == null || !file.canRead()) {
            MaterialDialog.Builder(context)
                .title(R.string.text_failed)
                .content(R.string.file_not_exist_or_readable)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .showAdaptive()
            return null
        }
        val infoDialog = InfoDialog(context)
        val items = infoDialog.items.apply {
            addItem(R.string.editable_file_info_file_path_label, id = "filePath").maxLines = 10
            addItem(R.string.editable_file_info_file_charset_label, id = "fileCharset")
            addItem(R.string.editable_file_info_line_break_label, id = "lineBreak")
            addItem(R.string.editable_file_info_file_size_label, id = "fileSize")
            addSeparator(id = "splitLine")
            addItem(R.string.editable_file_info_byte_count_label, id = "byteCount")
            addItem(R.string.editable_file_info_char_count_label, id = "charCount")
            addItem(R.string.editable_file_info_line_count_label, id = "lineCount")
        }

        val textUnknown = context.getString(R.string.text_unknown)

        val scope = infoDialog.scope

        infoDialog.builder
            .title(file.name)
            .iconRes(R.drawable.ic_edit_smaller)
            .limitIconToDefaultSize()
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .let { infoDialog.show() }
            .apply {
                iconView?.setOnClickListener {
                    IntentUtils.viewFile(
                        context = context,
                        path = file.path,
                        mimeType = Mime.TEXT_PLAIN,
                        fileProviderAuthority = AppFileProvider.AUTHORITY,
                        exceptionHolder = SnackExceptionHolder(view),
                    )
                }
            }

        scope.launch {
            items["filePath"].setValueIfAbsent(file.absolutePath)

            @Suppress("DEPRECATION")
            val bytes: ByteArray? = withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Files.readAllBytes(file.toPath())
                    } else {
                        file.readBytes()
                    }
                }.getOrNull()
            }
            when (bytes) {
                null -> {
                    items["byteCount"].setValueIfAbsent(textUnknown)
                    items["fileCharset"].setValueIfAbsent(textUnknown)
                    items["lineCount"].setValueIfAbsent(textUnknown)
                    items["charCount"].setValueIfAbsent(textUnknown)
                    items["lineBreak"].setValueIfAbsent(textUnknown)
                    items["fileSize"].setValueIfAbsent(scope) { PFiles.formatSizeWithUnit(file.length()) }
                }
                else -> {
                    val charsetMatch = StringUtils.detectCharset(bytes)
                    val charset = charsetMatch.charsetOrDefault()
                    val text = withContext(Dispatchers.IO) {
                        fileContentGetter?.invoke() ?: String(dropBom(bytes, charset), charset)
                    }
                    items["fileCharset"].setValueIfAbsent(
                        charsetMatch.nameOrDefault(textUnknown),
                        charsetMatch.confidence?.takeUnless { it == 100 }?.let {
                            " [ ${context.getString(R.string.text_confidence_level)}: $it ]"
                        },
                    )
                    items["lineBreak"].setValueIfAbsent(scope) {
                        val hasLineBreaks = text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                        if (hasLineBreaks) detectLineBreak(text) else textUnknown
                    }
                    items["fileSize"].setValueIfAbsent(getReadableSizeString(context, charset, bytes, text))
                    items["byteCount"].setValueIfAbsent(getByteCountString(context, charset, bytes, text))
                    items["charCount"].setValueIfAbsent(scope) { "${text.codePointCount(0, text.length)}" }
                    items["lineCount"].setValueIfAbsent(scope) { "${text.lineSequence().count()}" }
                }
            }
        }
        return infoDialog
    }

    private fun detectLineBreak(text: String): String {
        var lf = 0   // \n
        var crlf = 0 // \r\n
        var cr = 0   // \r

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\r') {
                if (i + 1 < text.length && text[i + 1] == '\n') {
                    crlf++; i++ // 跳过 \n
                } else cr++
            } else if (c == '\n') lf++
            i++
        }
        return when {
            crlf > 0 && lf == 0 && cr == 0 -> "Windows (CRLF)"
            lf > 0 && crlf == 0 && cr == 0 -> "Unix (LF)"
            cr > 0 && crlf == 0 && lf == 0 -> "Mac (CR)"
            else -> "Mixed"
        }
    }

    private fun getReadableSizeString(context: Context, charset: Charset, bytes: ByteArray, text: String): Pair<String, String?> {
        val (size, suffix) = getByteCount(context, charset, bytes, text)
        return PFiles.formatSizeWithUnit(size) to suffix
    }

    private fun getByteCountString(context: Context, charset: Charset, bytes: ByteArray, text: String): Pair<String, String?> {
        val (size, suffix) = getByteCount(context, charset, bytes, text)
        return "$size" to suffix
    }

    private fun getByteCount(context: Context, charset: Charset, bytes: ByteArray, text: String): Pair<Long, String?> {
        return when (text) {
            String(dropBom(bytes, charset), charset) -> {
                bytes.size.toLong() to null
            }
            else -> text.toByteArray(charset).size.toLong() to " [ ${context.getString(R.string.text_estimated)} ]"
        }
    }

}
