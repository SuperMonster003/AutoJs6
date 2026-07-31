package org.autojs.autojs.ui.media

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs6.R
import java.io.File

object ImageConverter {

    @JvmStatic
    @JvmOverloads
    fun show(context: Context, source: File, onCompleted: ((File) -> Unit)? = null) {
        show(context, source, onCompleted, null)
    }

    @JvmStatic
    fun show(
        context: Context,
        source: File,
        onCompleted: ((File) -> Unit)?,
        onDismissed: (() -> Unit)?,
    ) {
        var selected = ImageBitmapIO.OutputFormat.PNG
        var conversionStarted = false
        MaterialDialog.Builder(context)
            .title(R.string.dialog_button_convert)
            .items(ImageBitmapIO.OutputFormat.entries.map { it.displayName })
            .itemsCallbackSingleChoice(0) { _, _, index, _ ->
                selected = ImageBitmapIO.OutputFormat.entries[index]
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_convert)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                conversionStarted = true
                convert(context, source, selected, onCompleted, onDismissed)
            }
            .build()
            .apply {
                setOnDismissListener {
                    if (!conversionStarted) onDismissed?.invoke()
                }
            }
            .showAdaptive()
    }

    private fun convert(
        context: Context,
        source: File,
        format: ImageBitmapIO.OutputFormat,
        onCompleted: ((File) -> Unit)?,
        onDismissed: (() -> Unit)?,
    ) {
        val progress = MaterialDialog.Builder(context)
            .title(R.string.dialog_button_convert)
            .content(R.string.text_please_wait)
            .progress(true, 0)
            .cancelable(false)
            .build()
            .showAdaptive()
        val scope = (context as? LifecycleOwner)?.lifecycleScope
            ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = ImageBitmapIO.decodeForConversion(source)
                    try {
                        ImageBitmapIO.convert(bitmap, source, format)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            progress.dismiss()
            if (!currentCoroutineContext().isActive) return@launch
            result.onSuccess { output ->
                Explorers.workspace().notifyItemCreated(
                    ExplorerFileItem(output, ExplorerDirPage(output.parentFile, null)),
                )
                ViewUtils.showToast(context, context.getString(R.string.text_path_colon_value, output.path), true)
                onCompleted?.invoke(output)
            }.onFailure {
                ViewUtils.showToast(context, R.string.error_failed_to_save, true)
            }
            onDismissed?.invoke()
        }
    }
}
