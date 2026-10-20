package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.theme.preference.MaterialDialogPreference
import org.autojs.autojs.theme.widget.ThemeColorSeekBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogEditorThresholdSettingsBinding
import kotlin.properties.Delegates

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 20, 2026.
 */
class EditorThresholdDialogBuilder(
    context: Context,
    private val mode: Mode,
    private val onChangeConfirmed: () -> Unit = {},
) : MaterialDialog.Builder(context), OnSeekBarChangeListener {

    enum class Mode {
        CODE_HIGHLIGHT,
        LARGE_FILE_MODE,
    }

    private var _binding: DialogEditorThresholdSettingsBinding? = null
    private val binding: DialogEditorThresholdSettingsBinding get() = _binding!!

    private var mSeekBar by Delegates.notNull<ThemeColorSeekBar>()

    init {
        _binding = DialogEditorThresholdSettingsBinding.inflate(LayoutInflater.from(context)).also { binding ->
            mSeekBar = binding.seekBar
        }

        mSeekBar.setOnSeekBarChangeListener(this)
        mSeekBar.max = EditorThresholdSettings.valuesBytes.lastIndex
        mSeekBar.progress = EditorThresholdSettings.progressOf(initialValue())
        binding.thresholdDescription.text = textForProgress(mSeekBar.progress)

        title(titleRes())
        customView(binding.root, false)
        options(
            listOf(
                MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_use_default)) {
                    mSeekBar.progress = EditorThresholdSettings.progressOf(defaultValue())
                },
                MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_details)) {
                    MaterialDialogPreference.displayDetailsOptionMenuItemSpecDialog(context, longClickPrompt = context.getString(descRes()))
                },
            ),
        )
        negativeText(R.string.dialog_button_cancel)
        negativeColorRes(R.color.dialog_button_default)
        onNegative { dialog, _ -> dialog.dismiss() }
        positiveText(R.string.dialog_button_confirm)
        positiveColorRes(R.color.dialog_button_attraction)
        onPositive { dialog, _ ->
            saveValue(EditorThresholdSettings.valueAt(mSeekBar.progress))
            onChangeConfirmed()
            dialog.dismiss()
        }
        autoDismiss(false)
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        binding.thresholdDescription.text = textForProgress(progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        /* Ignored. */
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        /* Ignored. */
    }

    override fun dismissListener(listener: DialogInterface.OnDismissListener): MaterialDialog.Builder? {
        _binding = null
        return super.dismissListener(listener)
    }

    private fun initialValue(): Int = when (mode) {
        Mode.CODE_HIGHLIGHT -> EditorKeys.codeHighlightThresholdChars
        Mode.LARGE_FILE_MODE -> EditorKeys.largeFileModeThresholdBytes
    }

    private fun defaultValue(): Int = when (mode) {
        Mode.CODE_HIGHLIGHT -> EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.defaultOrNull()!!
        Mode.LARGE_FILE_MODE -> EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES.defaultOrNull()!!
    }

    private fun titleRes(): Int = when (mode) {
        Mode.CODE_HIGHLIGHT -> R.string.text_editor_code_highlight_threshold
        Mode.LARGE_FILE_MODE -> R.string.text_editor_large_file_mode_threshold
    }

    private fun descRes(): Int = when (mode) {
        Mode.CODE_HIGHLIGHT -> R.string.description_editor_code_highlight_threshold
        Mode.LARGE_FILE_MODE -> R.string.description_editor_large_file_mode_threshold
    }

    private fun textForProgress(progress: Int): String {
        val value = EditorThresholdSettings.valueAt(progress)
        return when (mode) {
            Mode.CODE_HIGHLIGHT -> EditorThresholdSettings.formatCodeHighlightSummary(context, value)
            Mode.LARGE_FILE_MODE -> EditorThresholdSettings.formatLargeFileModeSummary(context, value)
        }
    }

    private fun saveValue(value: Int) {
        when (mode) {
            Mode.CODE_HIGHLIGHT -> EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(value)
            Mode.LARGE_FILE_MODE -> EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES.set(value)
        }
    }
}
