package org.autojs.autojs.core.console

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogConsoleSettingsBinding
import kotlin.math.roundToInt

/**
 * The console settings dialog: time prefix, time format, level coloring and text size for every
 * console view, plus status bar avoidance and background opacity for the floating window. Every
 * change is applied to the [Model] immediately, so the console previews it live.
 *
 * zh-CN: 控制台设置对话框: 各控制台视图通用的时间前缀, 时间格式, 级别着色与字体大小, 以及浮动窗口专有的避开状态栏与背景不透明度.
 * 每项更改都会立即应用到 [Model], 因此控制台可实时预览.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
class ConsoleSettingsDialog(private val context: Context, private val model: Model) {

    interface Model {
        var isTimeVisible: Boolean
        var timeFormat: String
        var isColorful: Boolean
        var textSize: Float
        val hasWindowOptions: Boolean
        var isAvoidStatusBar: Boolean
        var titleBackgroundAlpha: Double
        var contentBackgroundAlpha: Double
    }

    private val binding = DialogConsoleSettingsBinding.inflate(LayoutInflater.from(context))

    fun build(): MaterialDialog {
        setupRows()
        return MaterialDialog.Builder(context)
            .title(R.string.text_settings)
            .customView(binding.root, false)
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .widgetThemeColor()
            .build()
    }

    fun show(): MaterialDialog = build().showAdaptive()

    private fun setupRows() {
        binding.switchTimeVisible.isChecked = model.isTimeVisible
        binding.switchTimeVisible.setOnCheckedChangeListener { _, checked -> model.isTimeVisible = checked }
        binding.rowTimeVisible.setOnClickListener { binding.switchTimeVisible.toggle() }

        binding.textTimeFormatValue.text = model.timeFormat
        binding.rowTimeFormat.setOnClickListener { showTimeFormatChooser() }

        binding.switchColorful.isChecked = model.isColorful
        binding.switchColorful.setOnCheckedChangeListener { _, checked -> model.isColorful = checked }
        binding.rowColorful.setOnClickListener { binding.switchColorful.toggle() }

        binding.seekTextSize.max = MAX_TEXT_SIZE - MIN_TEXT_SIZE
        binding.seekTextSize.progress = (model.textSize.roundToInt() - MIN_TEXT_SIZE).coerceIn(0, MAX_TEXT_SIZE - MIN_TEXT_SIZE)
        updateTextSizeLabel(binding.seekTextSize.progress + MIN_TEXT_SIZE)
        binding.seekTextSize.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgress(progress: Int, fromUser: Boolean) {
                val size = progress + MIN_TEXT_SIZE
                updateTextSizeLabel(size)
                if (fromUser) model.textSize = size.toFloat()
            }
        })

        binding.windowOptions.isVisible = model.hasWindowOptions
        if (!model.hasWindowOptions) return

        binding.switchAvoidStatusBar.isChecked = model.isAvoidStatusBar
        binding.switchAvoidStatusBar.setOnCheckedChangeListener { _, checked -> model.isAvoidStatusBar = checked }
        binding.rowAvoidStatusBar.setOnClickListener { binding.switchAvoidStatusBar.toggle() }

        binding.seekTitleOpacity.progress = (model.titleBackgroundAlpha * 100).roundToInt().coerceIn(0, 100)
        updateOpacityLabel(binding.textTitleOpacityValue, R.string.text_title_bar_opacity, binding.seekTitleOpacity.progress)
        binding.seekTitleOpacity.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgress(progress: Int, fromUser: Boolean) {
                updateOpacityLabel(binding.textTitleOpacityValue, R.string.text_title_bar_opacity, progress)
                if (fromUser) model.titleBackgroundAlpha = progress / 100.0
            }
        })

        binding.seekContentOpacity.progress = (model.contentBackgroundAlpha * 100).roundToInt().coerceIn(0, 100)
        updateOpacityLabel(binding.textContentOpacityValue, R.string.text_content_opacity, binding.seekContentOpacity.progress)
        binding.seekContentOpacity.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgress(progress: Int, fromUser: Boolean) {
                updateOpacityLabel(binding.textContentOpacityValue, R.string.text_content_opacity, progress)
                if (fromUser) model.contentBackgroundAlpha = progress / 100.0
            }
        })
    }

    private fun updateTextSizeLabel(size: Int) {
        binding.textTextSizeValue.text = context.getString(R.string.text_text_size_current_value, size)
    }

    private fun updateOpacityLabel(view: android.widget.TextView, labelRes: Int, percent: Int) {
        view.text = "${context.getString(labelRes)}: $percent%"
    }

    private fun showTimeFormatChooser() {
        val presets = ConsoleLogFormat.PRESET_TIME_PATTERNS
        val items = presets + context.getString(R.string.text_custom)
        val selected = presets.indexOf(model.timeFormat)
        MaterialDialog.Builder(context)
            .title(R.string.text_time_format)
            .items(items)
            .itemsCallbackSingleChoice(selected) { dialog, _, which, _ ->
                if (which < presets.size) {
                    applyTimeFormat(presets[which])
                    dialog.dismiss()
                } else {
                    dialog.dismiss()
                    showCustomTimeFormatInput()
                }
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .widgetThemeColor()
            .build()
            .showAdaptive()
    }

    private fun showCustomTimeFormatInput() {
        MaterialDialog.Builder(context)
            .title(R.string.text_time_format)
            .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            .input(context.getString(R.string.text_time_format_pattern_hint), model.timeFormat, false) { dialog, input ->
                val pattern = input.toString().trim()
                if (ConsoleLogFormat.isValidTimePattern(pattern)) {
                    applyTimeFormat(pattern)
                    dialog.dismiss()
                } else {
                    ViewUtils.showToast(context, R.string.error_invalid_time_format_pattern)
                }
            }
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .widgetThemeColor()
            .build()
            .showAdaptive()
    }

    private fun applyTimeFormat(pattern: String) {
        model.timeFormat = pattern
        binding.textTimeFormatValue.text = model.timeFormat
    }

    private abstract class SimpleSeekBarListener : android.widget.SeekBar.OnSeekBarChangeListener {

        abstract fun onProgress(progress: Int, fromUser: Boolean)

        override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) = onProgress(progress, fromUser)

        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {
            /* Empty body. */
        }

        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {
            /* Empty body. */
        }

    }

    companion object {

        private const val MIN_TEXT_SIZE = 8
        private const val MAX_TEXT_SIZE = 40

    }

}
