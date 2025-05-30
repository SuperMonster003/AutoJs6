package org.autojs.autojs.ui.timing

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
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
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import com.github.aakira.expandablelayout.ExpandableRelativeLayout
import org.autojs.autojs.core.ui.BiMaps
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ExecutionConfig.CREATOR.default
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.theme.ThemeColorHelper
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
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityTimedTaskSettingBinding
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog as MaterialDatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog as MaterialTimePickerDialog

/**
 * Created by Stardust on Nov 28, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 */
class TimedTaskSettingActivity : BaseActivity() {

    private lateinit var mToolbar: Toolbar
    private lateinit var mTimingGroup: RadioGroup
    private lateinit var mDisposableTaskRadio: RadioButton
    private lateinit var mDailyTaskRadio: RadioButton
    private lateinit var mWeeklyTaskRadio: RadioButton
    private lateinit var mRunOnBroadcastRadio: RadioButton
    private lateinit var mRunOnCustomBroadcast: RadioButton
    private lateinit var mCustomBroadcastAction: EditText
    private lateinit var mBroadcastGroup: RadioGroup
    private lateinit var mDisposableTaskTime: TextView
    private lateinit var mDisposableTaskDate: TextView
    private lateinit var mDailyTaskTimePicker: TimePicker
    private lateinit var mWeeklyTaskTimePicker: TimePicker
    private lateinit var mWeeklyTaskContainer: LinearLayout

    private val mDayOfWeekCheckBoxes: MutableList<CheckBox> = ArrayList()

    private var mTimedTask: TimedTask? = null
    private var mIntentTask: IntentTask? = null
    private lateinit var mScriptFile: ScriptFile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityTimedTaskSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mToolbar = binding.toolbar
        mTimingGroup = binding.timingGroup
        mRunOnCustomBroadcast = binding.runOnCustomBroadcast
        mCustomBroadcastAction = binding.action.apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    mRunOnCustomBroadcast.isChecked = true
                }
            }
        }
        mBroadcastGroup = binding.broadcastGroup.apply {
            setUpRadioButtonListeners()
        }
        mWeeklyTaskContainer = binding.weeklyTaskContainer.apply {
            setUpDayOfWeekCheckBoxes()
        }
        mWeeklyTaskTimePicker = binding.weeklyTaskTimePicker.apply {
            setIs24HourView(true)
        }
        mDailyTaskTimePicker = binding.dailyTaskTimePicker.apply {
            setIs24HourView(true)
        }
        mDailyTaskRadio = binding.dailyTaskRadio.apply {
            setUpRadioButton()
        }
        mWeeklyTaskRadio = binding.weeklyTaskRadio.apply {
            setUpRadioButton()
        }
        mDisposableTaskRadio = binding.disposableTaskRadio.apply {
            setUpRadioButton()
        }
        mRunOnBroadcastRadio = binding.runOnBroadcast.apply {
            setUpRadioButton()
        }
        mDisposableTaskTime = binding.disposableTaskTime.apply {
            text = TIME_FORMATTER.print(LocalTime.now())
            setOnClickListener { showDisposableTaskTimePicker() }
        }
        mDisposableTaskDate = binding.disposableTaskDate.apply {
            text = DATE_FORMATTER.print(LocalDate.now())
            setOnClickListener { showDisposableTaskDatePicker() }
        }

        loadTaskFromIntent()
        setToolbarAsBack(R.string.text_timed_task)
        mToolbar.subtitle = mScriptFile.name

        setUpTaskSettings()

        ViewUtils.excludePaddingClippableViewFromBottomNavigationBar(binding.scrollView)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            v.setPadding(0, 0, 0, maxOf(sysBars.bottom, ime.bottom))

            // 只把 IME 的 inset 标记为已消费, 其余继续下传
            val outInsets = WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .build()

            outInsets
        }
    }

    private fun loadTaskFromIntent() {
        when (val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)) {
            -1L -> when (val intentTaskId = intent.getLongExtra(EXTRA_INTENT_TASK_ID, -1)) {
                -1L -> {
                    val path = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PATH)
                    if (TextUtils.isEmpty(path)) {
                        finish()
                    }
                    mScriptFile = ScriptFile(path)
                }
                else -> mIntentTask = getIntentTask(intentTaskId)?.also {
                    mScriptFile = ScriptFile(it.scriptPath)
                }
            }
            else -> mTimedTask = getTimedTask(taskId)?.also {
                mScriptFile = ScriptFile(it.scriptPath)
            }
        }
    }

    private fun RadioGroup.setUpRadioButtonListeners() {
        forEach { view ->
            if (view !is RadioButton) {
                return@forEach
            }
            view.setOnClickListener {
                mCustomBroadcastAction.apply {
                    if (view == mRunOnCustomBroadcast) {
                        requestFocus()
                        setSelection(length())
                        ViewUtils.showSoftInput(this)
                    } else {
                        clearFocus()
                        ViewUtils.hideSoftInput(this)
                    }
                }
            }
        }
    }

    private fun RadioButton.setUpRadioButton() {
        setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
    }

    private fun ViewGroup.setUpDayOfWeekCheckBoxes() {
        for (i in 0 until this.childCount) {
            val child = this.getChildAt(i)
            if (child is CheckBox) {
                mDayOfWeekCheckBoxes.add(child)
            } else if (child is ViewGroup) {
                child.setUpDayOfWeekCheckBoxes()
            }
            if (mDayOfWeekCheckBoxes.size >= 7) break
        }
    }

    private fun setUpTaskSettings() {
        mTimedTask?.let {
            setUpTime(it)
            return
        }
        mIntentTask?.let {
            setUpAction(it)
            return
        }
        mDailyTaskRadio.isChecked = true
    }

    private fun setUpTime(timedTask: TimedTask) {
        if (timedTask.isDisposable) {
            mDisposableTaskRadio.isChecked = true
            mDisposableTaskTime.text = TIME_FORMATTER.print(timedTask.millis)
            mDisposableTaskDate.text = DATE_FORMATTER.print(timedTask.millis)
            return
        }
        val time = LocalTime.fromMillisOfDay(timedTask.millis)
        mDailyTaskTimePicker.hour = time.hourOfDay
        mDailyTaskTimePicker.minute = time.minuteOfHour
        mWeeklyTaskTimePicker.hour = time.hourOfDay
        mWeeklyTaskTimePicker.minute = time.minuteOfHour
        if (timedTask.isDaily) {
            mDailyTaskRadio.isChecked = true
        } else {
            mWeeklyTaskRadio.isChecked = true
            for (i in mDayOfWeekCheckBoxes.indices) {
                mDayOfWeekCheckBoxes[i].isChecked = timedTask.hasDayOfWeek(this, i + 1)
            }
        }
    }

    private fun setUpAction(intentTask: IntentTask) {
        mRunOnBroadcastRadio.isChecked = true
        val buttonId = ACTIONS.getKey(intentTask.action)
        if (buttonId == null) {
            mRunOnCustomBroadcast.isChecked = true
            mCustomBroadcastAction.setText(intentTask.action)
        } else {
            (findViewById<View>(buttonId) as RadioButton).isChecked = true
        }
    }

    fun onCheckedChanged(button: CompoundButton) {
        findExpandableLayoutOf(button).run {
            when (button.isChecked) {
                true -> post { expand() }
                else -> post { collapse() }
            }
        }
        ViewUtils.hideSoftInput(mCustomBroadcastAction)
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
        MaterialTimePickerDialog.newInstance(
            /* callback = */ { _, hourOfDay, minute, _ -> mDisposableTaskTime.text = TIME_FORMATTER.print(LocalTime(hourOfDay, minute)) },
            /* hourOfDay = */ time.hourOfDay,
            /* minute = */ time.minuteOfHour,
            /* is24HourMode = */ true,
        ).apply {
            version = MaterialTimePickerDialog.Version.VERSION_2
            ThemeColorHelper.setThemeColorPrimary(this, this@TimedTaskSettingActivity, true)
            isThemeDark = ViewUtils.isNightModeYes(this@TimedTaskSettingActivity)
        }.show(supportFragmentManager, "TimedTaskTimePickerDialog")
    }

    private fun showDisposableTaskDatePicker() {
        val date = DATE_FORMATTER.parseLocalDate(mDisposableTaskDate.text.toString())
        MaterialDatePickerDialog.newInstance(
            /* callBack = */ { _, year, month, dayOfMonth -> mDisposableTaskDate.text = DATE_FORMATTER.print(LocalDate(year, month + 1, dayOfMonth)) },
            /* year = */ date.year,
            /* monthOfYear = */ date.monthOfYear - 1,
            /* dayOfMonth = */ date.dayOfMonth,
        ).apply {
            version = MaterialDatePickerDialog.Version.VERSION_2
            ThemeColorHelper.setThemeColorPrimary(this, this@TimedTaskSettingActivity, true)
            isThemeDark = ViewUtils.isNightModeYes(this@TimedTaskSettingActivity)
        }.show(supportFragmentManager, "TimedTaskDatePickerDialog")
    }

    private fun createTimedTask(): TimedTask? = when {
        mDisposableTaskRadio.isChecked -> createDisposableTask()
        mDailyTaskRadio.isChecked -> createDailyTask()
        else -> createWeeklyTask()
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
        return TimedTask.weeklyTask(time, timeFlag, mScriptFile.path, default)
    }

    private fun createDailyTask(): TimedTask {
        val time = LocalTime(mDailyTaskTimePicker.hour, mDailyTaskTimePicker.minute)
        return TimedTask.dailyTask(time, mScriptFile.path, ExecutionConfig())
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
        return TimedTask.disposableTask(dateTime, mScriptFile.path, default)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_timed_task_setting, menu)
        mToolbar.setMenuIconsColorByThemeColorLuminance(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_done) {
            if ((getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) {
                createOrUpdateTask()
                return true
            }
            try {
                @SuppressLint("BatteryLife")
                val intent = Intent()
                    .setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData("package:$packageName".toUri())
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_CODE_IGNORE_BATTERY)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                createOrUpdateTask()
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
            showToast(this, R.string.error_empty_broadcast_selection, true)
            return
        }
        val actionString: String?
        if (buttonId == R.id.run_on_custom_broadcast) {
            actionString = mCustomBroadcastAction.text.toString()
            if (actionString.isEmpty()) {
                mCustomBroadcastAction.error = getString(R.string.text_should_not_be_empty)
                return
            }
        } else {
            actionString = ACTIONS[buttonId]
        }
        val task = IntentTask().apply {
            action = actionString
            scriptPath = mScriptFile.path
            isLocal = actionString == DynamicBroadcastReceivers.ACTION_STARTUP
        }
        when (val intentTask = mIntentTask) {
            null -> {
                addTask(task)
                mTimedTask?.let { removeTask(it) }
            }
            else -> {
                task.id = intentTask.id
                updateTask(task)
            }
        }
        showToast(this, R.string.text_already_created)
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
