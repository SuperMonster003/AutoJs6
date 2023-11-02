package org.autojs.autojs.ui.timing

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.widget.Toolbar
import com.github.aakira.expandablelayout.ExpandableRelativeLayout
import org.autojs.autojs.core.ui.BiMaps
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ExecutionConfig.CREATOR.default
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TaskReceiver
import org.autojs.autojs.timing.TimedTask
import org.autojs.autojs.timing.TimedTaskManager.addTask
import org.autojs.autojs.timing.TimedTaskManager.getIntentTask
import org.autojs.autojs.timing.TimedTaskManager.getTimedTask
import org.autojs.autojs.timing.TimedTaskManager.removeTask
import org.autojs.autojs.timing.TimedTaskManager.updateTask
import org.autojs.autojs.tool.MapBuilder
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityTimedTaskSettingBinding
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat

/**
 * Created by Stardust on 2017/11/28.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
class TimedTaskSettingActivity : BaseActivity() {

    private lateinit var mToolbar: Toolbar
    private lateinit var mTimingGroup: RadioGroup
    private lateinit var mDisposableTaskRadio: RadioButton
    private lateinit var mDailyTaskRadio: RadioButton
    private lateinit var mWeeklyTaskRadio: RadioButton
    private lateinit var mRunOnBroadcastRadio: RadioButton
    private lateinit var mRunOnOtherBroadcast: RadioButton
    private lateinit var mOtherBroadcastAction: EditText
    private lateinit var mBroadcastGroup: RadioGroup
    private lateinit var mDisposableTaskTime: TextView
    private lateinit var mDisposableTaskDate: TextView
    private lateinit var mDailyTaskTimePicker: TimePicker
    private lateinit var mWeeklyTaskTimePicker: TimePicker
    private lateinit var mWeeklyTaskContainer: LinearLayout

    private val mDayOfWeekCheckBoxes: MutableList<CheckBox> = ArrayList()
    private var mScriptFile: ScriptFile? = null
    private var mTimedTask: TimedTask? = null
    private var mIntentTask: IntentTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityTimedTaskSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mToolbar = binding.toolbar
        mTimingGroup = binding.timingGroup
        mRunOnOtherBroadcast = binding.runOnOtherBroadcast
        mOtherBroadcastAction = binding.action
        mBroadcastGroup = binding.broadcastGroup
        mDailyTaskTimePicker = binding.dailyTaskTimePicker
        mWeeklyTaskTimePicker = binding.weeklyTaskTimePicker
        mWeeklyTaskContainer = binding.weeklyTaskContainer
        mDailyTaskRadio = binding.dailyTaskRadio.apply {
            setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        }
        mWeeklyTaskRadio = binding.weeklyTaskRadio.apply {
            setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        }
        mDisposableTaskRadio = binding.disposableTaskRadio.apply {
            setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        }
        mRunOnBroadcastRadio = binding.runOnBroadcast.apply {
            setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        }
        mDisposableTaskTime = binding.disposableTaskTime.apply {
            setOnClickListener { showDisposableTaskTimePicker() }
        }
        mDisposableTaskDate = binding.disposableTaskDate.apply {
            setOnClickListener { showDisposableTaskDatePicker() }
        }

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId != -1L) {
            mTimedTask = getTimedTask(taskId)
            if (mTimedTask != null) {
                mScriptFile = ScriptFile(mTimedTask!!.scriptPath)
            }
        } else {
            val intentTaskId = intent.getLongExtra(EXTRA_INTENT_TASK_ID, -1)
            if (intentTaskId != -1L) {
                mIntentTask = getIntentTask(intentTaskId)
                if (mIntentTask != null) {
                    mScriptFile = ScriptFile(mIntentTask!!.scriptPath)
                }
            } else {
                val path = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PATH)
                if (TextUtils.isEmpty(path)) {
                    finish()
                }
                mScriptFile = ScriptFile(path)
            }
        }

        setToolbarAsBack(R.string.text_timed_task)
        mToolbar.subtitle = mScriptFile!!.name
        mDailyTaskTimePicker.setIs24HourView(true)
        mWeeklyTaskTimePicker.setIs24HourView(true)
        findDayOfWeekCheckBoxes(mWeeklyTaskContainer)
        setUpTaskSettings(this)
    }

    private fun findDayOfWeekCheckBoxes(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is CheckBox) {
                mDayOfWeekCheckBoxes.add(child)
            } else if (child is ViewGroup) {
                findDayOfWeekCheckBoxes(child)
            }
            if (mDayOfWeekCheckBoxes.size >= 7) break
        }
    }

    private fun setUpTaskSettings(context: Context) {
        mDisposableTaskDate.text = DATE_FORMATTER.print(LocalDate.now())
        mDisposableTaskTime.text = TIME_FORMATTER.print(LocalTime.now())
        if (mTimedTask != null) {
            setupTime(context)
            return
        }
        if (mIntentTask != null) {
            setupAction()
            return
        }
        mDailyTaskRadio.isChecked = true
    }

    private fun setupAction() {
        mRunOnBroadcastRadio.isChecked = true
        val buttonId = ACTIONS.getKey(mIntentTask!!.action)
        if (buttonId == null) {
            mRunOnOtherBroadcast.isChecked = true
            mOtherBroadcastAction.setText(mIntentTask!!.action)
        } else {
            (findViewById<View>(buttonId) as RadioButton).isChecked = true
        }
    }

    private fun setupTime(context: Context) {
        if (mTimedTask!!.isDisposable) {
            mDisposableTaskRadio.isChecked = true
            mDisposableTaskTime.text = TIME_FORMATTER.print(mTimedTask!!.millis)
            mDisposableTaskDate.text = DATE_FORMATTER.print(mTimedTask!!.millis)
            return
        }
        val time = LocalTime.fromMillisOfDay(mTimedTask!!.millis)
        mDailyTaskTimePicker.hour = time.hourOfDay
        mDailyTaskTimePicker.minute = time.minuteOfHour
        mWeeklyTaskTimePicker.hour = time.hourOfDay
        mWeeklyTaskTimePicker.minute = time.minuteOfHour
        if (mTimedTask!!.isDaily) {
            mDailyTaskRadio.isChecked = true
        } else {
            mWeeklyTaskRadio.isChecked = true
            for (i in mDayOfWeekCheckBoxes.indices) {
                mDayOfWeekCheckBoxes[i].isChecked = mTimedTask!!.hasDayOfWeek(context, i + 1)
            }
        }
    }

    fun onCheckedChanged(button: CompoundButton) {
        val relativeLayout = findExpandableLayoutOf(button)
        if (button.isChecked) {
            relativeLayout.post { relativeLayout.expand() }
        } else {
            relativeLayout.collapse()
        }
    }

    private fun findExpandableLayoutOf(button: CompoundButton): ExpandableRelativeLayout {
        val parent = button.parent as ViewGroup
        for (i in 0 until parent.childCount) {
            if (parent.getChildAt(i) === button) {
                return parent.getChildAt(i + 1) as ExpandableRelativeLayout
            }
        }
        throw IllegalStateException("findExpandableLayout: button = " + button + ", parent = " + parent + ", childCount = " + parent.childCount)
    }

    private fun showDisposableTaskTimePicker() {
        val time = TIME_FORMATTER.parseLocalTime(mDisposableTaskTime.text.toString())
        TimePickerDialog(this, { _, hourOfDay, minute -> mDisposableTaskTime.text = TIME_FORMATTER.print(LocalTime(hourOfDay, minute)) }, time.hourOfDay, time.minuteOfHour, true)
            .show()
    }

    private fun showDisposableTaskDatePicker() {
        val date = DATE_FORMATTER.parseLocalDate(mDisposableTaskDate.text.toString())
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth -> mDisposableTaskDate.text = DATE_FORMATTER.print(LocalDate(year, month + 1, dayOfMonth)) },
            date.year,
            date.monthOfYear - 1,
            date.dayOfMonth
        ).show()
    }

    private fun createTimedTask(): TimedTask? {
        return if (mDisposableTaskRadio.isChecked) {
            createDisposableTask()
        } else if (mDailyTaskRadio.isChecked) {
            createDailyTask()
        } else {
            createWeeklyTask()
        }
    }

    private fun createWeeklyTask(): TimedTask? {
        var timeFlag: Long = 0
        for (i in mDayOfWeekCheckBoxes.indices) {
            if (mDayOfWeekCheckBoxes[i].isChecked) {
                timeFlag = timeFlag or TimedTask.getDayOfWeekTimeFlag(this, i + 1)
            }
        }
        if (timeFlag == 0L) {
            showToast(this, R.string.text_weekly_task_should_check_day_of_week)
            return null
        }
        val time = LocalTime(mWeeklyTaskTimePicker.hour, mWeeklyTaskTimePicker.minute)
        return TimedTask.weeklyTask(time, timeFlag, mScriptFile!!.path, default)
    }

    private fun createDailyTask(): TimedTask {
        val time = LocalTime(mDailyTaskTimePicker.hour, mDailyTaskTimePicker.minute)
        return TimedTask.dailyTask(time, mScriptFile!!.path, ExecutionConfig())
    }

    private fun createDisposableTask(): TimedTask? {
        val time = TIME_FORMATTER.parseLocalTime(mDisposableTaskTime.text.toString())
        val date = DATE_FORMATTER.parseLocalDate(mDisposableTaskDate.text.toString())
        val dateTime = LocalDateTime(
            date.year, date.monthOfYear, date.dayOfMonth,
            time.hourOfDay, time.minuteOfHour
        )
        if (dateTime.isBefore(LocalDateTime.now())) {
            showToast(this, R.string.text_disposable_task_time_before_now)
            return null
        }
        return TimedTask.disposableTask(dateTime, mScriptFile!!.path, default)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_timed_task_setting, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_done) {
            if ((getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) {
                createOrUpdateTask()
            } else {
                try {
                    @SuppressLint("BatteryLife") val intent = Intent()
                        .setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CODE_IGNORE_BATTERY)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    createOrUpdateTask()
                }
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_IGNORE_BATTERY) {
            Log.d(LOG_TAG, "result code = $requestCode")
            createOrUpdateTask()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun createOrUpdateTask() {
        if (mRunOnBroadcastRadio.isChecked) {
            createOrUpdateIntentTask()
            return
        }
        val task = createTimedTask() ?: return
        if (mTimedTask == null) {
            addTask(task)
            if (mIntentTask != null) {
                removeTask(mIntentTask!!)
            }
            showToast(this, R.string.text_already_created)
        } else {
            task.id = mTimedTask!!.id
            updateTask(task)
        }
        finish()
    }

    private fun createOrUpdateIntentTask() {
        val buttonId = mBroadcastGroup.checkedRadioButtonId
        if (buttonId == -1) {
            showToast(this, R.string.error_empty_broadcast_selection)
            return
        }
        val action: String?
        if (buttonId == R.id.run_on_other_broadcast) {
            action = mOtherBroadcastAction.text.toString()
            if (action.isEmpty()) {
                mOtherBroadcastAction.error = getString(R.string.text_should_not_be_empty)
                return
            }
        } else {
            action = ACTIONS[buttonId]
        }
        val task = IntentTask()
        task.action = action
        task.scriptPath = mScriptFile!!.path
        task.isLocal = action != null && action == DynamicBroadcastReceivers.ACTION_STARTUP
        if (mIntentTask != null) {
            task.id = mIntentTask!!.id
            updateTask(task)
            showToast(this, R.string.text_already_created)
        } else {
            addTask(task)
            if (mTimedTask != null) {
                removeTask(mTimedTask!!)
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_INTENT_TASK_ID = "intent_task_id"
        const val EXTRA_TASK_ID = TaskReceiver.EXTRA_TASK_ID
        private val TIME_FORMATTER = DateTimeFormat.forPattern("HH:mm")
        private val DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd")
        private const val REQUEST_CODE_IGNORE_BATTERY = 27101
        private const val LOG_TAG = "TimedTaskSettings"

        @JvmField
        @Suppress("DEPRECATION")
        val ACTION_DESC_MAP: MutableMap<String, Int> = MapBuilder<String, Int>()
            .put(DynamicBroadcastReceivers.ACTION_STARTUP, R.string.text_run_on_startup)
            .put(Intent.ACTION_BOOT_COMPLETED, R.string.text_run_on_boot)
            .put(Intent.ACTION_SCREEN_OFF, R.string.text_run_on_screen_off)
            .put(Intent.ACTION_SCREEN_ON, R.string.text_run_on_screen_on)
            .put(Intent.ACTION_USER_PRESENT, R.string.text_run_on_screen_unlock)
            .put(Intent.ACTION_BATTERY_CHANGED, R.string.text_run_on_battery_change)
            .put(Intent.ACTION_POWER_CONNECTED, R.string.text_run_on_power_connect)
            .put(Intent.ACTION_POWER_DISCONNECTED, R.string.text_run_on_power_disconnect)
            .put(ConnectivityManager.CONNECTIVITY_ACTION, R.string.text_run_on_conn_change)
            .put(Intent.ACTION_PACKAGE_ADDED, R.string.text_run_on_package_install)
            .put(Intent.ACTION_PACKAGE_REMOVED, R.string.text_run_on_package_uninstall)
            .put(Intent.ACTION_PACKAGE_REPLACED, R.string.text_run_on_package_update)
            .put(Intent.ACTION_HEADSET_PLUG, R.string.text_run_on_headset_plug)
            .put(Intent.ACTION_CONFIGURATION_CHANGED, R.string.text_run_on_config_change)
            .put(Intent.ACTION_TIME_TICK, R.string.text_run_on_time_tick)
            .build()

        @Suppress("DEPRECATION")
        private val ACTIONS = BiMaps.newBuilder<Int, String>()
            .put(R.id.run_on_startup, DynamicBroadcastReceivers.ACTION_STARTUP)
            .put(R.id.run_on_boot, Intent.ACTION_BOOT_COMPLETED)
            .put(R.id.run_on_screen_off, Intent.ACTION_SCREEN_OFF)
            .put(R.id.run_on_screen_on, Intent.ACTION_SCREEN_ON)
            .put(R.id.run_on_screen_unlock, Intent.ACTION_USER_PRESENT)
            .put(R.id.run_on_battery_change, Intent.ACTION_BATTERY_CHANGED)
            .put(R.id.run_on_power_connect, Intent.ACTION_POWER_CONNECTED)
            .put(R.id.run_on_power_disconnect, Intent.ACTION_POWER_DISCONNECTED)
            .put(R.id.run_on_conn_change, ConnectivityManager.CONNECTIVITY_ACTION)
            .put(R.id.run_on_package_install, Intent.ACTION_PACKAGE_ADDED)
            .put(R.id.run_on_package_uninstall, Intent.ACTION_PACKAGE_REMOVED)
            .put(R.id.run_on_package_update, Intent.ACTION_PACKAGE_REPLACED)
            .put(R.id.run_on_headset_plug, Intent.ACTION_HEADSET_PLUG)
            .put(R.id.run_on_config_change, Intent.ACTION_CONFIGURATION_CHANGED)
            .put(R.id.run_on_time_tick, Intent.ACTION_TIME_TICK)
            .build()
    }

}
