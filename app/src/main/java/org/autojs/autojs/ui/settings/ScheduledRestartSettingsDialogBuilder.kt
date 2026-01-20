package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.widget.ThemeColorRadioButton
import org.autojs.autojs.theme.widget.ThemeColorSeekBar
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogScheduledRestartSettingsBinding
import kotlin.properties.Delegates

class ScheduledRestartSettingsDialogBuilder(context: Context) : MaterialDialog.Builder(context), OnSeekBarChangeListener {

    private var _binding: DialogScheduledRestartSettingsBinding? = null
    private val binding: DialogScheduledRestartSettingsBinding get() = _binding!!

    private var mScheduledRestartStartDelayTitlePrefix: String
    private var mScheduledRestartStartDelayTitle: String

    private var mStartDelayMinValue by Delegates.notNull<Int>()
    private var mStartDelayMaxValue by Delegates.notNull<Int>()

    private var mOptionWorkManager: ThemeColorRadioButton
    private var mOptionAlarmManager: ThemeColorRadioButton

    private var mSeekBar: ThemeColorSeekBar

    init {
        var initialRestartDelayValue = Pref.getInt(R.string.key_scheduled_restart_delay, context.resources.getInteger(R.integer.scheduled_restart_start_delay_default_value))

        _binding = DialogScheduledRestartSettingsBinding.inflate(LayoutInflater.from(context)).also { binding ->
            mOptionWorkManager = binding.optionWorkManager
            mOptionAlarmManager = binding.optionAlarmManager

            when (Pref.getString(R.string.key_scheduled_restart_backend, key(R.string.default_key_scheduled_restart_backend))) {
                key(R.string.key_scheduled_restart_backend_alarm_manager) -> {
                    mOptionAlarmManager.isChecked = true
                }
                else -> {
                    mOptionWorkManager.isChecked = true
                }
            }

            mScheduledRestartStartDelayTitlePrefix = context.getString(R.string.text_scheduled_restart_start_delay)
            mScheduledRestartStartDelayTitle = "$mScheduledRestartStartDelayTitlePrefix: ${initialRestartDelayValue}ms"

            mStartDelayMinValue = context.resources.getInteger(R.integer.scheduled_restart_start_delay_min_value).also {
                binding.scheduledRestartStartDelayMinValue.text = "$it"
            }
            mStartDelayMaxValue = context.resources.getInteger(R.integer.scheduled_restart_start_delay_max_value).also {
                binding.scheduledRestartStartDelayMaxValue.text = "$it"
            }

            mSeekBar = binding.seekBar
        }

        mSeekBar.setOnSeekBarChangeListener(this)
        mSeekBar.max = (mStartDelayMaxValue - mStartDelayMinValue) / 100
        mSeekBar.progress = (initialRestartDelayValue - mStartDelayMinValue) / 100

        title(R.string.entry_restart_strategy_scheduled)
        customView(binding.root, false)
        options(
            listOf(
                MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_use_default)) {
                    when (key(R.string.default_key_scheduled_restart_backend)) {
                        key(R.string.key_scheduled_restart_backend_alarm_manager) -> {
                            mOptionAlarmManager.isChecked = true
                        }
                        else -> {
                            mOptionWorkManager.isChecked = true
                        }
                    }
                    mSeekBar.progress = (context.resources.getInteger(R.integer.scheduled_restart_start_delay_default_value) - mStartDelayMinValue) / 100
                },
            ),
        )
        negativeText(R.string.dialog_button_cancel)
        negativeColorRes(R.color.dialog_button_default)
        onNegative { d, _ -> d.dismiss() }
        positiveText(R.string.dialog_button_confirm)
        positiveColorRes(R.color.dialog_button_attraction)
        onPositive { d, _ ->
            when {
                mOptionWorkManager.isChecked -> Pref.putString(R.string.key_scheduled_restart_backend, key(R.string.key_scheduled_restart_backend_work_manager))
                mOptionAlarmManager.isChecked -> Pref.putString(R.string.key_scheduled_restart_backend, key(R.string.key_scheduled_restart_backend_alarm_manager))
            }
            Pref.putInt(R.string.key_scheduled_restart_delay, mStartDelayMinValue + mSeekBar.progress * 100)
            d.dismiss()
        }
        autoDismiss(false)
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        binding.scheduledRestartStartDelayTitle.text = context.getString(R.string.text_property_colon_value_unit, mScheduledRestartStartDelayTitlePrefix, mStartDelayMinValue + progress * 100, "ms")
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