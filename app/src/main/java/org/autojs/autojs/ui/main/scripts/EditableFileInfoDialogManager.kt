package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import androidx.constraintlayout.widget.ConstraintLayout
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.extension.MaterialDialogExtensions.makeTextCopyable
import org.autojs.autojs.extension.MaterialDialogExtensions.setCopyableTextIfAbsent
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.SnackExceptionHolder
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.StringUtils.dropBom
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.EditableFileInfoDialogListItemBinding
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

object EditableFileInfoDialogManager {

    @JvmStatic
    @SuppressLint("SetTextI18n")
    fun showEditableFileInfoDialog(context: Context, file: File?, fileContentGetter: (() -> String)? = null) {
        if (file == null || !file.canRead()) {
            MaterialDialog.Builder(context)
                .title(R.string.text_failed)
                .content(R.string.file_not_exist_or_readable)
                .show()
            return
        }
        val binding = EditableFileInfoDialogListItemBinding.inflate(LayoutInflater.from(context))

        val textUnknown = context.getString(R.string.text_unknown)

        // Create an independent Scope for the Dialog, bind its lifecycle with the Dialog.
        // zh-CN: 针对 Dialog 独立创建一个 Scope, 生命周期与 Dialog 绑定.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val dialog = MaterialDialog.Builder(context)
            .title(file.name)
            .customView(binding.root, false)
            .iconRes(R.drawable.ic_edit_smaller)
            .limitIconToDefaultSize()
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .show()
            .apply {
                makeTextCopyable { titleView }
                setOnDismissListener { scope.cancel() }
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
            dialog.setCopyableTextIfAbsent(binding.filePathValue, file.absolutePath)

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
                null -> dialog.apply {
                    setCopyableTextIfAbsent(binding.byteCountValue, textUnknown)
                    setCopyableTextIfAbsent(binding.fileCharsetValue, textUnknown)
                    setCopyableTextIfAbsent(binding.lineCountValue, textUnknown)
                    setCopyableTextIfAbsent(binding.charCountValue, textUnknown)
                    setCopyableTextIfAbsent(binding.lineBreakValue, textUnknown)
                    setCopyableTextIfAbsent(binding.fileSizeValue, scope) { PFiles.getHumanReadableSize(file.length()) }
                }
                else -> dialog.apply {
                    val charsetMatch = StringUtils.detectCharset(bytes)
                    val charset = charsetMatch.charsetOrDefault()
                    val text = withContext(Dispatchers.IO) {
                        fileContentGetter?.invoke() ?: dropBom(bytes, charset).decodeToString()
                    }
                    setCopyableTextIfAbsent(
                        binding.fileCharsetValue,
                        charsetMatch.nameOrDefault(textUnknown),
                        charsetMatch.confidence?.takeUnless { it == 100 }?.let {
                            " [ ${context.getString(R.string.text_confidence_level)}: $it ]"
                        },
                    )
                    setCopyableTextIfAbsent(binding.lineBreakValue, scope) {
                        val hasLineBreaks = text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                        if (hasLineBreaks) detectLineBreak(text) else textUnknown
                    }
                    setCopyableTextIfAbsent(binding.fileSizeValue, getReadableSizeString(context, charset, bytes, text))
                    setCopyableTextIfAbsent(binding.byteCountValue, getByteCountString(context, charset, bytes, text))
                    setCopyableTextIfAbsent(binding.charCountValue, scope) { "${text.codePointCount(0, text.length)}" }
                    setCopyableTextIfAbsent(binding.lineCountValue, scope) { "${text.lineSequence().count()}" }
                }
            }
            updateGuidelines(binding)
        }
    }

    private fun updateGuidelines(binding: EditableFileInfoDialogListItemBinding) {
        val bindings = listOf(
            binding.filePathLabel to binding.filePathGuideline,
            binding.fileSizeLabel to binding.fileSizeGuideline,
            binding.fileCharsetLabel to binding.fileCharsetGuideline,
            binding.lineBreakLabel to binding.lineBreakGuideline,
            binding.byteCountLabel to binding.byteCountGuideline,
            binding.charCountLabel to binding.charCountGuideline,
            binding.lineCountLabel to binding.lineCountGuideline,
        )

        @Suppress("DuplicatedCode")
        val maxWidth = bindings.maxOfOrNull { it.first.apply { measure(UNSPECIFIED, UNSPECIFIED) }.measuredWidth } ?: return

        bindings.forEach { (_, guideline) ->
            guideline.layoutParams = (guideline.layoutParams as ConstraintLayout.LayoutParams).also {
                it.guideBegin = maxWidth
            }
        }
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
        return PFiles.getHumanReadableSize(size) to suffix
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