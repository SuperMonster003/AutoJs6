package org.autojs.autojs.ui.timing

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.github.aakira.expandablelayout.ExpandableRelativeLayout
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.mail.MailPluginHost
import org.autojs.autojs.core.plugin.mail.MailTriggerFilter
import org.autojs.autojs.core.plugin.mail.MailTriggerTaskExtras
import org.autojs.autojs.core.plugin.mail.MailTriggerWatchEntry
import org.autojs.autojs.core.ui.BiMaps
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ExecutionConfig.CREATOR.default
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.permission.IgnoreBatteryOptimizationsPermission
import org.autojs.autojs.script.PersistedScriptLaunchContext
import org.autojs.autojs.script.PythonProjectLaunchException
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.timing.ExactAlarmPermissionHelper.canScheduleExactAlarms
import org.autojs.autojs.timing.ExactAlarmPermissionHelper.requestExactAlarmPermission
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
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityTimedTaskSettingBinding
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import org.joda.time.LocalTime
import org.autojs.plugin.mail.api.MailActions
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
    private lateinit var mCountdownTaskRadio: RadioButton
    private lateinit var mDailyTaskRadio: RadioButton
    private lateinit var mWeeklyTaskRadio: RadioButton
    private lateinit var mMonthlyTaskRadio: RadioButton
    private lateinit var mYearlyTaskRadio: RadioButton
    private lateinit var mRunOnBroadcastRadio: RadioButton
    private lateinit var mRunOnCustomBroadcast: RadioButton
    private lateinit var mCustomBroadcastAction: EditText
    private lateinit var mRunOnMailArrived: RadioButton
    private lateinit var mMailTriggerWatch: EditText
    private lateinit var mMailTriggerFrom: EditText
    private lateinit var mMailTriggerSubject: EditText
    private lateinit var mBroadcastGroup: RadioGroup
    private lateinit var mDisposableTaskTime: TextView
    private lateinit var mDisposableTaskDate: TextView
    private lateinit var mCountdownTaskHours: EditText
    private lateinit var mCountdownTaskMinutes: EditText
    private lateinit var mDailyTaskTimePicker: TimePicker
    private lateinit var mWeeklyTaskTimePicker: TimePicker
    private lateinit var mMonthlyTaskTimePicker: TimePicker
    private lateinit var mYearlyTaskTimePicker: TimePicker
    private lateinit var mMonthlyTaskDate: TextView
    private lateinit var mYearlyTaskDate: TextView
    private lateinit var mWeeklyTaskContainer: LinearLayout
    private lateinit var mRepeatEndContainer: LinearLayout
    private lateinit var mRepeatEndGroup: RadioGroup
    private lateinit var mRepeatEndForever: RadioButton
    private lateinit var mRepeatEndUntilDate: RadioButton
    private lateinit var mRepeatEndCount: RadioButton
    private lateinit var mRepeatUntilDate: TextView
    private lateinit var mRepeatCount: EditText
    private lateinit var mVirtualScreenModeGroup: RadioGroup

    private val mDayOfWeekCheckBoxes: MutableList<CheckBox> = ArrayList()

    private var mTimedTask: TimedTask? = null
    private var mIntentTask: IntentTask? = null

    /** The watch a "mail arrived" task listens to; empty for every watch (mail roadmap P8). zh-CN: "邮件到达" 任务监听的守望; 空表示全部 (邮件路线图 P8). */
    private var mMailTriggerWatchId: String = ""
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
        mRunOnMailArrived = binding.runOnMailArrived
        mMailTriggerWatch = binding.mailTriggerWatch.apply {
            setOnClickListener {
                mRunOnMailArrived.isChecked = true
                showMailTriggerWatchPicker()
            }
        }
        mMailTriggerFrom = binding.mailTriggerFrom.apply {
            setUpMailTriggerInput()
        }
        mMailTriggerSubject = binding.mailTriggerSubject.apply {
            setUpMailTriggerInput()
        }
        renderMailTriggerWatch()
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
        mMonthlyTaskTimePicker = binding.monthlyTaskTimePicker.apply {
            setIs24HourView(true)
        }
        mYearlyTaskTimePicker = binding.yearlyTaskTimePicker.apply {
            setIs24HourView(true)
        }
        mMonthlyTaskDate = binding.monthlyTaskDate.apply {
            text = DATE_FORMATTER.print(LocalDate.now())
            setOnClickListener { showMonthlyTaskDatePicker() }
        }
        mYearlyTaskDate = binding.yearlyTaskDate.apply {
            text = DATE_FORMATTER.print(LocalDate.now())
            setOnClickListener { showYearlyTaskDatePicker() }
        }
        mRepeatEndContainer = binding.repeatEndContainer
        mRepeatEndGroup = binding.repeatEndGroup
        mRepeatEndForever = binding.repeatEndForever
        mRepeatEndUntilDate = binding.repeatEndUntilDate
        mRepeatEndCount = binding.repeatEndCount
        mRepeatUntilDate = binding.repeatUntilDate.apply {
            text = DATE_FORMATTER.print(LocalDate.now())
            setOnClickListener {
                mRepeatEndUntilDate.isChecked = true
                showRepeatUntilDatePicker()
            }
        }
        mRepeatCount = binding.repeatCount.apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) mRepeatEndCount.isChecked = true
            }
        }
        mRepeatEndForever.isChecked = true
        mRepeatEndGroup.setOnCheckedChangeListener { _, _ -> updateRepeatEndInputState() }
        mDailyTaskRadio = binding.dailyTaskRadio.apply {
            setUpRadioButton()
        }
        mWeeklyTaskRadio = binding.weeklyTaskRadio.apply {
            setUpRadioButton()
        }
        mMonthlyTaskRadio = binding.monthlyTaskRadio.apply {
            setUpRadioButton()
        }
        mYearlyTaskRadio = binding.yearlyTaskRadio.apply {
            setUpRadioButton()
        }
        mDisposableTaskRadio = binding.disposableTaskRadio.apply {
            setUpRadioButton()
        }
        mCountdownTaskRadio = binding.countdownTaskRadio.apply {
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
        mCountdownTaskHours = binding.countdownTaskHours.apply {
            setUpCountdownInput()
        }
        mCountdownTaskMinutes = binding.countdownTaskMinutes.apply {
            setUpCountdownInput()
        }
        mVirtualScreenModeGroup = binding.virtualScreenModeGroup

        loadTaskFromIntent()
        setToolbarAsBack(R.string.text_timed_task)
        mToolbar.subtitle = mScriptFile.name

        setUpTaskSettings()
        setUpVirtualScreenMode()
        updateRepeatEndVisibility()
        updateRepeatEndInputState()

        binding.scrollView.excludePaddingClippableViewFromBottomNavigationBar()

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

    private fun EditText.setUpMailTriggerInput() {
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                mRunOnMailArrived.isChecked = true
            }
        }
    }

    private fun renderMailTriggerWatch() {
        mMailTriggerWatch.setText(mMailTriggerWatchId.ifEmpty { getString(R.string.text_mail_trigger_watch_any) })
    }

    /**
     * Offers the plugin's background watches (`IMailPlugin.listTriggers`, contract version 2) plus
     * "any watch"; a missing plugin or one without the feature is reported, not treated as an error.
     * zh-CN: 提供插件的后台守望 (`IMailPlugin.listTriggers`, 契约版本 2) 以及 "任意守望"; 插件缺失或不支持时提示, 不视为错误.
     */
    private fun showMailTriggerWatchPicker() {
        lifecycleScope.launch {
            val entries = runCatching { MailTriggerWatchEntry.parseList(MailPluginHost.listTriggers(this@TimedTaskSettingActivity)) }
                .onFailure { Log.w(LOG_TAG, "listTriggers failed: ${it.javaClass.simpleName}") }
                .getOrNull()
            if (isFinishing || isDestroyed) return@launch
            if (entries == null) {
                showToast(this@TimedTaskSettingActivity, R.string.text_mail_trigger_plugin_unavailable, true)
                return@launch
            }
            if (entries.isEmpty()) {
                showToast(this@TimedTaskSettingActivity, R.string.text_mail_trigger_watch_none, true)
            }
            val labels = listOf(getString(R.string.text_mail_trigger_watch_any)) + entries.map { it.label }
            MaterialDialog.Builder(this@TimedTaskSettingActivity)
                .title(R.string.text_mail_trigger_watch)
                .items(labels)
                .itemsCallback { _, _, which, _ ->
                    mMailTriggerWatchId = if (which <= 0) "" else entries[which - 1].triggerId
                    renderMailTriggerWatch()
                }
                .show()
        }
    }

    private fun EditText.setUpCountdownInput() {
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                mCountdownTaskRadio.isChecked = true
            }
        }
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

    /** Restores the persisted task-level virtual screen mode (FOLLOW for a new task). */
    private fun setUpVirtualScreenMode() {
        val mode = mTimedTask?.virtualScreenMode ?: ExecutionConfig.VIRTUAL_SCREEN_MODE_FOLLOW
        mVirtualScreenModeGroup.check(
            when (mode) {
                ExecutionConfig.VIRTUAL_SCREEN_MODE_ON -> R.id.virtual_screen_mode_on
                ExecutionConfig.VIRTUAL_SCREEN_MODE_OFF -> R.id.virtual_screen_mode_off
                else -> R.id.virtual_screen_mode_follow
            },
        )
    }

    /** The launch mode written into the task Intent as [ScriptIntents.EXTRA_KEY_VIRTUAL_SCREEN_MODE]. */
    private fun selectedVirtualScreenMode(): Int = when (mVirtualScreenModeGroup.checkedRadioButtonId) {
        R.id.virtual_screen_mode_on -> ExecutionConfig.VIRTUAL_SCREEN_MODE_ON
        R.id.virtual_screen_mode_off -> ExecutionConfig.VIRTUAL_SCREEN_MODE_OFF
        else -> ExecutionConfig.VIRTUAL_SCREEN_MODE_FOLLOW
    }

    private fun setUpTime(timedTask: TimedTask) {
        if (timedTask.isCountdown) {
            mCountdownTaskRadio.isChecked = true
            setUpCountdownDuration(timedTask.millis - System.currentTimeMillis())
            return
        }
        if (timedTask.isDisposable) {
            mDisposableTaskRadio.isChecked = true
            mDisposableTaskTime.text = TIME_FORMATTER.print(timedTask.millis)
            mDisposableTaskDate.text = DATE_FORMATTER.print(timedTask.millis)
            return
        }
        val time = LocalTime.fromMillisOfDay(timedTask.millis)
        setTimePickerValue(mDailyTaskTimePicker, time)
        setTimePickerValue(mWeeklyTaskTimePicker, time)
        setTimePickerValue(mMonthlyTaskTimePicker, time)
        setTimePickerValue(mYearlyTaskTimePicker, time)
        when {
            timedTask.isDaily -> mDailyTaskRadio.isChecked = true
            timedTask.isMonthly -> {
                mMonthlyTaskRadio.isChecked = true
                mMonthlyTaskDate.text = DATE_FORMATTER.print(createDateForDayOfMonth(timedTask.dayOfMonth))
            }
            timedTask.isYearly -> {
                mYearlyTaskRadio.isChecked = true
                mYearlyTaskDate.text = DATE_FORMATTER.print(createDateForMonthDay(timedTask.monthOfYear, timedTask.dayOfMonth))
            }
            else -> {
                mWeeklyTaskRadio.isChecked = true
                for (i in mDayOfWeekCheckBoxes.indices) {
                    mDayOfWeekCheckBoxes[i].isChecked = timedTask.hasDayOfWeek(this, i + 1)
                }
            }
        }
        setUpRepeatEnd(timedTask)
    }

    private fun setTimePickerValue(timePicker: TimePicker, time: LocalTime) {
        timePicker.hour = time.hourOfDay
        timePicker.minute = time.minuteOfHour
    }

    private fun createDateForDayOfMonth(dayOfMonth: Int): LocalDate {
        val today = LocalDate.now()
        val firstDay = LocalDate(today.year, 1, 1)
        return firstDay.withDayOfMonth(dayOfMonth.coerceIn(1, 31))
    }

    private fun createDateForMonthDay(monthOfYear: Int, dayOfMonth: Int): LocalDate {
        val today = LocalDate.now()
        val month = monthOfYear.coerceIn(1, 12)
        val year = when {
            month == 2 && dayOfMonth == 29 -> findNearestLeapYear(today.year)
            else -> today.year
        }
        val firstDay = LocalDate(year, month, 1)
        val maxDay = firstDay.dayOfMonth().maximumValue
        return firstDay.withDayOfMonth(dayOfMonth.coerceIn(1, maxDay))
    }

    private fun findNearestLeapYear(startYear: Int): Int {
        return (startYear..startYear + 3).first { LocalDate(it, 2, 1).dayOfMonth().maximumValue == 29 }
    }

    private fun setUpRepeatEnd(timedTask: TimedTask) {
        when (timedTask.endMode) {
            TimedTask.END_MODE_UNTIL_DATE -> {
                mRepeatEndUntilDate.isChecked = true
                if (timedTask.endValue > 0) {
                    mRepeatUntilDate.text = DATE_FORMATTER.print(timedTask.endValue)
                }
            }
            TimedTask.END_MODE_COUNT -> {
                mRepeatEndCount.isChecked = true
                if (timedTask.endValue > 0) {
                    mRepeatCount.setText(timedTask.endValue.toString())
                }
            }
            else -> mRepeatEndForever.isChecked = true
        }
    }

    private fun setUpCountdownDuration(durationMillis: Long) {
        val totalMinutes = maxOf(1L, (durationMillis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE)
        mCountdownTaskHours.setText((totalMinutes / MINUTES_PER_HOUR).toString())
        mCountdownTaskMinutes.setText((totalMinutes % MINUTES_PER_HOUR).toString())
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
        if (intentTask.action == MailActions.HOST_TRIGGER_ACTION) {
            val extras = MailTriggerTaskExtras.parse(intentTask.extras)
            mMailTriggerWatchId = extras.triggerId
            mMailTriggerFrom.setText(extras.filter.from.joinToString(", "))
            mMailTriggerSubject.setText(extras.filter.subject.joinToString(", "))
            renderMailTriggerWatch()
        }
    }

    fun onCheckedChanged(button: CompoundButton) {
        findExpandableLayoutOf(button).run {
            when (button.isChecked) {
                true -> post { expand() }
                else -> post { collapse() }
            }
        }
        updateRepeatEndVisibility()
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

    private fun updateRepeatEndVisibility() {
        mRepeatEndContainer.visibility = when {
            mDailyTaskRadio.isChecked || mWeeklyTaskRadio.isChecked || mMonthlyTaskRadio.isChecked || mYearlyTaskRadio.isChecked -> View.VISIBLE
            else -> View.GONE
        }
    }

    private fun updateRepeatEndInputState() {
        val untilEnabled = mRepeatEndUntilDate.isChecked
        val countEnabled = mRepeatEndCount.isChecked
        mRepeatUntilDate.isEnabled = untilEnabled
        mRepeatUntilDate.alpha = if (untilEnabled) 1.0f else 0.45f
        mRepeatCount.isEnabled = countEnabled
        mRepeatCount.alpha = if (countEnabled) 1.0f else 0.45f
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

    private fun showMonthlyTaskDatePicker() {
        showDatePicker(mMonthlyTaskDate.text.toString(), "MonthlyTaskDatePickerDialog") {
            mMonthlyTaskDate.text = DATE_FORMATTER.print(it)
        }
    }

    private fun showYearlyTaskDatePicker() {
        showDatePicker(mYearlyTaskDate.text.toString(), "YearlyTaskDatePickerDialog") {
            mYearlyTaskDate.text = DATE_FORMATTER.print(it)
        }
    }

    private fun showRepeatUntilDatePicker() {
        showDatePicker(mRepeatUntilDate.text.toString(), "RepeatUntilDatePickerDialog") {
            mRepeatUntilDate.text = DATE_FORMATTER.print(it)
        }
    }

    private fun showDatePicker(initialDate: String, tag: String, callback: (LocalDate) -> Unit) {
        val date = DATE_FORMATTER.parseLocalDate(initialDate)
        MaterialDatePickerDialog.newInstance(
            /* callBack = */ { _, year, month, dayOfMonth -> callback(LocalDate(year, month + 1, dayOfMonth)) },
            /* year = */ date.year,
            /* monthOfYear = */ date.monthOfYear - 1,
            /* dayOfMonth = */ date.dayOfMonth,
        ).apply {
            version = MaterialDatePickerDialog.Version.VERSION_2
            ThemeColorHelper.setThemeColorPrimary(this, this@TimedTaskSettingActivity, true)
            isThemeDark = ViewUtils.isNightModeYes(this@TimedTaskSettingActivity)
        }.show(supportFragmentManager, tag)
    }

    private fun createTimedTask(): TimedTask? {
        val preservedContext = existingLaunchContext()
        // TimedTask factories capture from their path. Use a non-Python draft path while editing,
        // then restore the persisted context verbatim so stale and legacy records are not upgraded.
        val constructionPath = if (preservedContext == null) {
            mScriptFile.path
        } else {
            CONTEXT_PRESERVING_DRAFT_PATH
        }
        val task = when {
            mDisposableTaskRadio.isChecked -> createDisposableTask(constructionPath)
            mCountdownTaskRadio.isChecked -> createCountdownTask(constructionPath)
            mDailyTaskRadio.isChecked -> createDailyTask(constructionPath)
            mWeeklyTaskRadio.isChecked -> createWeeklyTask(constructionPath)
            mMonthlyTaskRadio.isChecked -> createMonthlyTask(constructionPath)
            else -> createYearlyTask(constructionPath)
        } ?: return null
        if (preservedContext != null) {
            task.scriptPath = mScriptFile.path
            task.sourceKind = preservedContext.sourceKind
            task.projectRoot = preservedContext.projectRoot
        }
        task.virtualScreenMode = selectedVirtualScreenMode()
        return task
    }

    private fun createWeeklyTask(scriptPath: String): TimedTask? {
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
        return applyRepeatEnd(TimedTask.weeklyTask(time, timeFlag, scriptPath, default))
    }

    private fun createDailyTask(scriptPath: String): TimedTask? {
        val time = LocalTime(mDailyTaskTimePicker.hour, mDailyTaskTimePicker.minute)
        return applyRepeatEnd(TimedTask.dailyTask(time, scriptPath, ExecutionConfig()))
    }

    private fun createMonthlyTask(scriptPath: String): TimedTask? {
        val time = LocalTime(mMonthlyTaskTimePicker.hour, mMonthlyTaskTimePicker.minute)
        val date = DATE_FORMATTER.parseLocalDate(mMonthlyTaskDate.text.toString())
        return applyRepeatEnd(TimedTask.monthlyTask(time, date.dayOfMonth, scriptPath, default))
    }

    private fun createYearlyTask(scriptPath: String): TimedTask? {
        val time = LocalTime(mYearlyTaskTimePicker.hour, mYearlyTaskTimePicker.minute)
        val date = DATE_FORMATTER.parseLocalDate(mYearlyTaskDate.text.toString())
        return applyRepeatEnd(TimedTask.yearlyTask(time, date.monthOfYear, date.dayOfMonth, scriptPath, default))
    }

    private fun createDisposableTask(scriptPath: String): TimedTask? {
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
        return TimedTask.disposableTask(dateTime, scriptPath, default)
    }

    private fun createCountdownTask(scriptPath: String): TimedTask? {
        val hours = parseCountdownValue(mCountdownTaskHours) ?: return null
        val minutes = parseCountdownValue(mCountdownTaskMinutes) ?: return null
        if (minutes >= MINUTES_PER_HOUR) {
            mCountdownTaskMinutes.error = getString(R.string.text_countdown_minutes_should_be_less_than_60)
            return null
        }
        val delayMillis = calculateCountdownDelayMillis(hours, minutes) ?: run {
            showToast(this, R.string.text_countdown_duration_should_be_valid)
            return null
        }
        if (delayMillis <= 0) {
            showToast(this, R.string.text_countdown_duration_must_be_positive)
            return null
        }
        return TimedTask.countdownTask(delayMillis, scriptPath, default)
    }

    private fun existingLaunchContext(): PersistedScriptLaunchContext? = when {
        mTimedTask != null -> PersistedScriptLaunchContext(
            sourceKind = mTimedTask!!.sourceKind,
            projectRoot = mTimedTask!!.projectRoot,
        )
        mIntentTask != null -> PersistedScriptLaunchContext(
            sourceKind = mIntentTask!!.sourceKind,
            projectRoot = mIntentTask!!.projectRoot,
        )
        else -> null
    }

    private fun parseCountdownValue(editText: EditText): Long? {
        val value = editText.text.toString().trim()
        if (value.isEmpty()) {
            return 0
        }
        val number = value.toLongOrNull()
        if (number == null || number < 0) {
            editText.error = getString(R.string.text_countdown_duration_should_be_valid)
            return null
        }
        return number
    }

    private fun calculateCountdownDelayMillis(hours: Long, minutes: Long): Long? = runCatching {
        val totalMinutes = Math.addExact(Math.multiplyExact(hours, MINUTES_PER_HOUR.toLong()), minutes)
        Math.multiplyExact(totalMinutes, MILLIS_PER_MINUTE)
    }.getOrNull()

    private fun applyRepeatEnd(task: TimedTask): TimedTask? {
        when (mRepeatEndGroup.checkedRadioButtonId) {
            R.id.repeat_end_until_date -> {
                val date = DATE_FORMATTER.parseLocalDate(mRepeatUntilDate.text.toString())
                if (date.isBefore(LocalDate.now())) {
                    showToast(this, R.string.text_repeat_until_date_before_now)
                    return null
                }
                task.endMode = TimedTask.END_MODE_UNTIL_DATE
                task.endValue = date.toDateTimeAtStartOfDay().millis
            }
            R.id.repeat_end_count -> {
                val count = mRepeatCount.text.toString().toIntOrNull() ?: 0
                if (count <= 0) {
                    mRepeatCount.error = getString(R.string.text_repeat_count_must_be_positive)
                    return null
                }
                task.endMode = TimedTask.END_MODE_COUNT
                task.endValue = count.toLong()
            }
            else -> {
                task.endMode = TimedTask.END_MODE_FOREVER
                task.endValue = 0
            }
        }
        return task
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_timed_task_setting, menu)
        mToolbar.setMenuIconsColorByThemeColorLuminance(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_done -> run {
            IgnoreBatteryOptimizationsPermission(this).let {
                if (!it.has() && it.request()) {
                    return@run true
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!canScheduleExactAlarms(this) && requestExactAlarmPermission(this)) {
                    return@run true
                }
            }
            createOrUpdateTask()
            return@run true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_IGNORE_BATTERY, REQUEST_CODE_SCHEDULE_EXACT_ALARM -> {
                Log.d(LOG_TAG, "result code = $requestCode")
                createOrUpdateTask()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun createOrUpdateTask() {
        if (mRunOnBroadcastRadio.isChecked) {
            createOrUpdateIntentTask()
            return
        }
        val task = try {
            createTimedTask()
        } catch (error: PythonProjectLaunchException) {
            showToast(this, error.message, true)
            return
        } ?: return
        if (mTimedTask == null) {
            addTask(task)
            if (mIntentTask != null) {
                removeTask(mIntentTask!!)
            }
            showToast(this, R.string.text_already_created)
        } else {
            task.id = mTimedTask!!.id
            task.finishedTimes = mTimedTask!!.finishedTimes
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
        val extrasJson = when (buttonId) {
            R.id.run_on_mail_arrived -> MailTriggerTaskExtras(
                triggerId = mMailTriggerWatchId,
                filter = MailTriggerFilter(
                    from = MailTriggerFilter.split(mMailTriggerFrom.text?.toString()),
                    subject = MailTriggerFilter.split(mMailTriggerSubject.text?.toString()),
                ),
            ).toJson()
            else -> null
        }
        val preservedContext = existingLaunchContext()
        val task = try {
            IntentTask().apply {
                action = actionString
                if (preservedContext == null) {
                    setScriptPathAndCaptureContext(mScriptFile.path)
                } else {
                    scriptPath = mScriptFile.path
                    sourceKind = preservedContext.sourceKind
                    projectRoot = preservedContext.projectRoot
                }
                if (actionString == DynamicBroadcastReceivers.ACTION_STARTUP) {
                    isLocal = true
                }
                extras = extrasJson
            }
        } catch (error: PythonProjectLaunchException) {
            showToast(this, error.message, true)
            return
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
        private const val REQUEST_CODE_SCHEDULE_EXACT_ALARM = 27102
        private const val LOG_TAG = "TimedTaskSettings"
        private const val MINUTES_PER_HOUR = 60
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val CONTEXT_PRESERVING_DRAFT_PATH = "autojs6-task-context-edit.js"

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
            .put(MailActions.HOST_TRIGGER_ACTION, R.string.text_run_on_mail_arrived)
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
            .put(R.id.run_on_mail_arrived, MailActions.HOST_TRIGGER_ACTION)
            .build()

    }

}
