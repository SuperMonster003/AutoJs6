package org.autojs.autojs.ui.media

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogImageConverterBinding
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
        val sourceInfo = runCatching { ImageBitmapIO.inspect(source) }.getOrElse {
            ViewUtils.showToast(context, R.string.error_image_conversion_invalid_source, true)
            onDismissed?.invoke()
            return
        }
        val binding = DialogImageConverterBinding.inflate(LayoutInflater.from(context))
        var conversionStarted = false
        lateinit var controller: ConversionDialogController
        val dialog = MaterialDialog.Builder(context)
            .title(R.string.dialog_button_convert)
            .customView(binding.root, false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_convert)
            .positiveColorRes(R.color.dialog_button_attraction)
            .autoDismiss(false)
            .onNegative { current, _ -> current.dismiss() }
            .onPositive { current, _ ->
                val options = controller.currentOptions() ?: return@onPositive
                conversionStarted = true
                current.dismiss()
                convert(context, source, options, onCompleted, onDismissed)
            }
            .build()
            .apply {
                setOnDismissListener {
                    if (!conversionStarted) onDismissed?.invoke()
                }
            }
        controller = ConversionDialogController(context, source, sourceInfo, binding, dialog)
        dialog.showAdaptive()
    }

    private fun convert(
        context: Context,
        source: File,
        options: ImageConversionOptions,
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
                    val bitmap = ImageBitmapIO.decodeForConversion(source, options.targetSize)
                    try {
                        ImageBitmapIO.convert(bitmap, source, options)
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
                val message = buildString {
                    append(context.getString(R.string.text_path_colon_value, output.path))
                    append('\n')
                    append(context.getString(R.string.text_size))
                    append(context.getString(R.string.symbol_colon))
                    append(' ')
                    append(PFiles.formatSizeWithUnit(output.length()))
                }
                ViewUtils.showToast(context, message, true)
                onCompleted?.invoke(output)
            }.onFailure {
                ViewUtils.showToast(context, R.string.error_failed_to_save, true)
            }
            onDismissed?.invoke()
        }
    }

    private class ConversionDialogController(
        private val context: Context,
        private val source: File,
        private val sourceInfo: ImageBitmapIO.SourceInfo,
        private val binding: DialogImageConverterBinding,
        private val dialog: MaterialDialog,
    ) {

        private var updatingLockedDimension = false

        init {
            binding.format.adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                ImageOutputFormat.entries.map { it.displayName },
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            binding.format.setSelection(ImageOutputFormat.PNG.ordinal)
            binding.quality.progress = ImageConversionOptions.DEFAULT_QUALITY - ImageConversionOptions.MIN_QUALITY
            binding.width.setText(sourceInfo.displaySize.width.toString())
            binding.height.setText(sourceInfo.displaySize.height.toString())

            binding.format.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateControlsAndSummary()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            binding.quality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateControlsAndSummary()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            binding.resizeMode.setOnCheckedChangeListener { _, _ -> updateControlsAndSummary() }
            binding.jpegBackground.setOnCheckedChangeListener { _, _ -> updateControlsAndSummary() }
            binding.lockAspectRatio.setOnCheckedChangeListener { _, checked ->
                if (checked) updateHeightFromWidth()
                updateControlsAndSummary()
            }
            binding.percentage.afterTextChanged { updateControlsAndSummary() }
            binding.width.afterTextChanged {
                if (!updatingLockedDimension && binding.lockAspectRatio.isChecked && binding.width.hasFocus()) {
                    updateHeightFromWidth()
                }
                updateControlsAndSummary()
            }
            binding.height.afterTextChanged {
                if (!updatingLockedDimension && binding.lockAspectRatio.isChecked && binding.height.hasFocus()) {
                    updateWidthFromHeight()
                }
                updateControlsAndSummary()
            }
            updateControlsAndSummary()
        }

        fun currentOptions(): ImageConversionOptions? {
            val result = currentResizeResult(showErrors = true)
            val targetSize = result.size ?: return null
            return ImageConversionOptions(
                format = selectedFormat(),
                quality = selectedQuality(),
                targetSize = targetSize,
                jpegBackgroundColor = if (binding.jpegBackgroundBlack.isChecked) Color.BLACK else Color.WHITE,
            )
        }

        private fun updateControlsAndSummary() {
            val format = selectedFormat()
            val qualityEnabled = format.supportsQuality
            binding.quality.isEnabled = qualityEnabled
            binding.qualityTitle.isEnabled = qualityEnabled
            binding.qualityValue.isEnabled = qualityEnabled
            binding.quality.alpha = if (qualityEnabled) 1f else DISABLED_ALPHA
            binding.qualityTitle.alpha = if (qualityEnabled) 1f else DISABLED_ALPHA
            binding.qualityValue.alpha = if (qualityEnabled) 1f else DISABLED_ALPHA
            binding.qualityValue.text = selectedQuality().toString()

            val resizeMode = selectedResizeMode()
            binding.percentageParent.visibility = if (resizeMode == ImageResizeMode.PERCENTAGE) View.VISIBLE else View.GONE
            binding.customSizeParent.visibility = if (resizeMode == ImageResizeMode.CUSTOM) View.VISIBLE else View.GONE
            binding.jpegBackgroundParent.visibility = if (format == ImageOutputFormat.JPEG) View.VISIBLE else View.GONE

            clearFieldErrors()
            val result = currentResizeResult(showErrors = false)
            val targetSize = result.size
            val error = result.error
            binding.validationError.visibility = if (error == null) View.GONE else View.VISIBLE
            binding.validationError.text = error?.let(::errorText).orEmpty()
            dialog.getActionButton(DialogAction.POSITIVE).isEnabled = targetSize != null

            if (targetSize == null) {
                binding.outputResolution.text = context.getString(R.string.text_image_conversion_output_resolution_unavailable)
                binding.outputSize.text = context.getString(R.string.text_image_conversion_estimated_size_unavailable)
            } else {
                val options = ImageConversionOptions(
                    format = format,
                    quality = selectedQuality(),
                    targetSize = targetSize,
                    jpegBackgroundColor = if (binding.jpegBackgroundBlack.isChecked) Color.BLACK else Color.WHITE,
                )
                binding.outputResolution.text = context.getString(
                    R.string.text_image_conversion_output_resolution,
                    targetSize.width,
                    targetSize.height,
                )
                binding.outputSize.text = context.getString(
                    R.string.text_image_conversion_estimated_size,
                    PFiles.formatSizeWithUnit(ImageConversionSizing.estimateEncodedBytes(options)),
                )
            }
            val suggested = ImageBitmapIO.suggestedOutputFile(source, format)
            binding.outputFilename.text = buildString {
                append(context.getString(R.string.text_image_conversion_output_filename, suggested.name))
                append('\n')
                append(context.getString(R.string.text_image_conversion_filename_strategy))
            }
        }

        private fun currentResizeResult(showErrors: Boolean): ImageResizeResult {
            val request = when (selectedResizeMode()) {
                ImageResizeMode.ORIGINAL -> ImageResizeRequest(ImageResizeMode.ORIGINAL)
                ImageResizeMode.PERCENTAGE -> ImageResizeRequest(
                    mode = ImageResizeMode.PERCENTAGE,
                    percentage = binding.percentage.text?.toString()?.toIntOrNull(),
                )
                ImageResizeMode.CUSTOM -> ImageResizeRequest(
                    mode = ImageResizeMode.CUSTOM,
                    width = binding.width.text?.toString()?.toIntOrNull(),
                    height = binding.height.text?.toString()?.toIntOrNull(),
                )
            }
            val resolved = ImageConversionSizing.resolve(sourceInfo.displaySize, request)
            val result = if (resolved.size != null && !ImageBitmapIO.isWithinConversionMemoryBudget(sourceInfo, resolved.size)) {
                ImageResizeResult(error = ImageResizeError.MEMORY_BUDGET_EXCEEDED)
            } else {
                resolved
            }
            return result.also {
                if (showErrors && result.error != null) showFieldError(result.error)
            }
        }

        private fun selectedFormat(): ImageOutputFormat {
            return ImageOutputFormat.entries.getOrElse(binding.format.selectedItemPosition) { ImageOutputFormat.PNG }
        }

        private fun selectedQuality(): Int {
            return binding.quality.progress + ImageConversionOptions.MIN_QUALITY
        }

        private fun selectedResizeMode(): ImageResizeMode = when (binding.resizeMode.checkedRadioButtonId) {
            R.id.resize_percentage -> ImageResizeMode.PERCENTAGE
            R.id.resize_custom -> ImageResizeMode.CUSTOM
            else -> ImageResizeMode.ORIGINAL
        }

        private fun updateHeightFromWidth() {
            val width = binding.width.text?.toString()?.toIntOrNull() ?: return
            val height = ImageConversionSizing.heightForWidth(sourceInfo.displaySize, width) ?: return
            updateDimension(binding.height, height)
        }

        private fun updateWidthFromHeight() {
            val height = binding.height.text?.toString()?.toIntOrNull() ?: return
            val width = ImageConversionSizing.widthForHeight(sourceInfo.displaySize, height) ?: return
            updateDimension(binding.width, width)
        }

        private fun updateDimension(field: EditText, value: Int) {
            val text = value.toString()
            if (field.text?.toString() == text) return
            updatingLockedDimension = true
            field.setText(text)
            field.setSelection(text.length)
            updatingLockedDimension = false
        }

        private fun clearFieldErrors() {
            binding.percentageParent.error = null
            binding.widthParent.error = null
            binding.heightParent.error = null
        }

        private fun showFieldError(error: ImageResizeError) {
            clearFieldErrors()
            when (error) {
                ImageResizeError.INVALID_PERCENTAGE -> binding.percentageParent.error = errorText(error)
                ImageResizeError.INVALID_WIDTH -> binding.widthParent.error = errorText(error)
                ImageResizeError.INVALID_HEIGHT -> binding.heightParent.error = errorText(error)
                else -> binding.validationError.apply {
                    visibility = View.VISIBLE
                    text = errorText(error)
                }
            }
        }

        private fun errorText(error: ImageResizeError): String = when (error) {
            ImageResizeError.INVALID_SOURCE -> context.getString(R.string.error_image_conversion_invalid_source)
            ImageResizeError.INVALID_PERCENTAGE -> context.getString(
                R.string.error_image_conversion_invalid_percentage,
                ImageConversionSizing.MIN_PERCENTAGE,
                ImageConversionSizing.MAX_PERCENTAGE,
            )
            ImageResizeError.INVALID_WIDTH -> context.getString(R.string.error_image_conversion_invalid_width)
            ImageResizeError.INVALID_HEIGHT -> context.getString(R.string.error_image_conversion_invalid_height)
            ImageResizeError.DIMENSION_TOO_LARGE -> context.getString(
                R.string.error_image_conversion_dimension_too_large,
                ImageConversionSizing.MAX_DIMENSION,
            )
            ImageResizeError.PIXEL_COUNT_TOO_LARGE -> context.getString(
                R.string.error_image_conversion_pixel_count_too_large,
                ImageConversionSizing.MAX_PIXEL_COUNT,
            )
            ImageResizeError.MEMORY_BUDGET_EXCEEDED -> context.getString(
                R.string.error_image_conversion_memory_budget_exceeded,
            )
        }

        private fun EditText.afterTextChanged(action: () -> Unit) {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(text: Editable?) = action()
            })
        }

        companion object {
            private const val DISABLED_ALPHA = 0.45f
        }
    }
}
