package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.widget.ThemeColorSeekBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogScreenCaptureRequestDelaySettingsBinding
import kotlin.properties.Delegates

class ScreenCaptureRequestDelayDialogBuilder(context: Context, onChangeConfirmed: () -> Unit = {}) : MaterialDialog.Builder(context), OnSeekBarChangeListener {

    private var _binding: DialogScreenCaptureRequestDelaySettingsBinding? = null
    private val binding: DialogScreenCaptureRequestDelaySettingsBinding get() = _binding!!

    private var mScreenCaptureRequestDelayTitlePrefix: String
    private var mScreenCaptureRequestDelayTitle: String

    private var mStartDelayMinValue by Delegates.notNull<Int>()
    private var mStartDelayMaxValue by Delegates.notNull<Int>()

    private var mSeekBar: ThemeColorSeekBar

    init {
        var initialRestartDelayValue = Pref.getInt(R.string.key_screen_capture_request_delay, context.resources.getInteger(R.integer.screen_capture_request_delay_default_value))

        _binding = DialogScreenCaptureRequestDelaySettingsBinding.inflate(LayoutInflater.from(context)).also { binding ->
            mScreenCaptureRequestDelayTitlePrefix = context.getString(R.string.text_delay_time)
            mScreenCaptureRequestDelayTitle = "$mScreenCaptureRequestDelayTitlePrefix: ${initialRestartDelayValue}ms"

            mStartDelayMinValue = context.resources.getInteger(R.integer.screen_capture_request_delay_min_value).also {
                binding.requestScreenCaptureDelayMinValue.text = "$it"
            }
            mStartDelayMaxValue = context.resources.getInteger(R.integer.screen_capture_request_delay_max_value).also {
                binding.requestScreenCaptureDelayMaxValue.text = "$it"
            }

            mSeekBar = binding.seekBar
        }

        mSeekBar.setOnSeekBarChangeListener(this)
        mSeekBar.max = (mStartDelayMaxValue - mStartDelayMinValue) / 50
        mSeekBar.progress = (initialRestartDelayValue - mStartDelayMinValue) / 50

        title(R.string.text_screen_capture_request_delay)
        customView(binding.root, false)
        options(
            listOf(
                MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_use_default)) {
                    mSeekBar.progress = (context.resources.getInteger(R.integer.screen_capture_request_delay_default_value) - mStartDelayMinValue) / 50
                },
                MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_details)) {
                    MaterialDialog.Builder(context)
                        .title(R.string.text_screen_capture_request_delay)
                        .content(R.string.description_screen_capture_request_delay)
                        .positiveText(R.string.dialog_button_dismiss)
                        .positiveColorRes(R.color.dialog_button_default)
                        .show()
                },
            ),
        )
        negativeText(R.string.dialog_button_cancel)
        negativeColorRes(R.color.dialog_button_default)
        onNegative { d, _ -> d.dismiss() }
        positiveText(R.string.dialog_button_confirm)
        positiveColorRes(R.color.dialog_button_attraction)
        onPositive { d, _ ->
            Pref.putInt(R.string.key_screen_capture_request_delay, mStartDelayMinValue + mSeekBar.progress * 50)
            onChangeConfirmed()
            d.dismiss()
        }
        autoDismiss(false)
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        binding.requestScreenCaptureDelayTitle.text = context.getString(R.string.text_property_colon_value_unit, mScreenCaptureRequestDelayTitlePrefix, mStartDelayMinValue + progress * 50, "ms")
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

}