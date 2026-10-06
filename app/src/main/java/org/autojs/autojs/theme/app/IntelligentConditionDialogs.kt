package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.intelligent.DateCondition
import org.autojs.autojs.theme.intelligent.DateOverride
import org.autojs.autojs.theme.intelligent.GeoLocation
import org.autojs.autojs.theme.intelligent.Hemisphere
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.StateSource
import org.autojs.autojs.theme.intelligent.calendar.HolidayCalendar
import org.autojs.autojs.theme.intelligent.calendar.TimeZoneLocations
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.time.ZoneId

/**
 * Dialogs of the intelligent library editor for the conditions of roadmap P3: state source settings, time lists
 * and date overrides.
 * zh-CN: 智能库编辑器中 Roadmap P3 条件相关的对话框: 状态源设置, 时刻列表与日期覆盖层.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object IntelligentConditionDialogs {

    /**
     * Edits a list of times of the day (minutes); tap a time to change it, long press to remove it.
     * zh-CN: 编辑一天中的时刻列表 (分钟数); 点击时刻可修改, 长按可删除.
     */
    @SuppressLint("SetTextI18n")
    fun editTimes(context: Context, title: String, initial: List<Int>, min: Int, max: Int, rangeText: String, onDone: (List<Int>) -> Unit) {
        val times = initial.sorted().toMutableList()
        val padding = dp(context, 20)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(context, 8), padding, 0)
        }
        val hint = secondaryText(context, "${context.getString(R.string.text_time_list_hint)}\n$rangeText")
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val add = TextView(context).apply {
            text = context.getString(R.string.text_add_time)
            setTextColor(context.getColor(R.color.day_night_full))
            setPadding(0, dp(context, 12), 0, dp(context, 12))
            setBackgroundResource(selectableBackground(context))
        }
        fun pick(initialMinutes: Int, onPicked: (Int) -> Unit) {
            TimePickerDialog(context, { _, hour, minute -> onPicked(hour * 60 + minute) }, initialMinutes / 60, initialMinutes % 60, DateFormat.is24HourFormat(context)).show()
        }
        fun render() {
            list.removeAllViews()
            times.sort()
            times.forEachIndexed { index, minutes ->
                list.addView(TextView(context).apply {
                    text = IntelligentSummaryFormatter.formatMinutes(minutes)
                    textSize = 18f
                    setTextColor(context.getColor(R.color.day_night_full))
                    setPadding(0, dp(context, 10), 0, dp(context, 10))
                    setBackgroundResource(selectableBackground(context))
                    setOnClickListener {
                        pick(minutes) { picked ->
                            if (picked != minutes && picked in times) return@pick
                            times[index] = picked
                            render()
                        }
                    }
                    setOnLongClickListener {
                        if (times.size > min) {
                            times.removeAt(index)
                            render()
                        }
                        true
                    }
                })
            }
            add.visibility = if (times.size < max) View.VISIBLE else View.GONE
        }
        add.setOnClickListener {
            val suggested = ((times.lastOrNull() ?: (8 * 60 - 60)) + 60) % (24 * 60)
            pick(suggested) { picked ->
                if (picked !in times && times.size < max) times += picked
                render()
            }
        }
        render()
        content.addView(hint)
        content.addView(list)
        content.addView(add)
        MaterialDialog.Builder(context)
            .title(title)
            .customView(content, true)
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { dialog, _ ->
                if (times.size < min) {
                    ViewUtils.showToast(context, rangeText)
                    return@onPositive
                }
                dialog.dismiss()
                onDone(times.distinct().sorted())
            }
            .show()
    }

    /**
     * Chooses the location of sunrise / sunset: inferred from the time zone (null) or entered by hand (D20).
     * zh-CN: 选择日出日落所用的地点: 由时区推断 (null) 或手动输入 (D20).
     */
    fun editLocation(context: Context, current: GeoLocation?, onDone: (GeoLocation?) -> Unit) {
        val labels = listOf(inferredLocationLabel(context), context.getString(R.string.text_location_manual))
        MaterialDialog.Builder(context)
            .title(R.string.text_location)
            .items(labels)
            .itemsCallbackSingleChoice(if (current == null) 0 else 1) { _, _, which, _ ->
                when (which) {
                    0 -> onDone(null)
                    else -> enterCoordinates(context, current, onDone)
                }
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    fun inferredLocationLabel(context: Context): String {
        val zone = ZoneId.systemDefault()
        val inferred = TimeZoneLocations.infer(zone)
        val detail = buildString {
            append(zone.id).append(", ").append(formatLocation(inferred.location))
            if (inferred.isApproximate) append(", ").append(context.getString(R.string.text_location_estimated))
        }
        return context.getString(R.string.text_location_from_time_zone, detail)
    }

    fun formatLocation(location: GeoLocation) = "%.2f, %.2f".format(java.util.Locale.ROOT, location.latitude, location.longitude)

    private fun enterCoordinates(context: Context, current: GeoLocation?, onDone: (GeoLocation?) -> Unit) {
        val initial = current ?: TimeZoneLocations.infer(ZoneId.systemDefault()).location
        val padding = dp(context, 20)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(context, 8), padding, 0)
        }
        fun field(hintRes: Int, value: Double) = AppCompatEditText(context).apply {
            hint = context.getString(hintRes)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText("%.4f".format(java.util.Locale.ROOT, value))
        }
        val latitude = field(R.string.text_latitude_range, initial.latitude)
        val longitude = field(R.string.text_longitude_range, initial.longitude)
        content.addView(secondaryText(context, context.getString(R.string.text_latitude_range)))
        content.addView(latitude)
        content.addView(secondaryText(context, context.getString(R.string.text_longitude_range)))
        content.addView(longitude)
        MaterialDialog.Builder(context)
            .title(R.string.text_location_manual)
            .customView(content, true)
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { dialog, _ ->
                val lat = latitude.text?.toString()?.trim()?.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
                val lon = longitude.text?.toString()?.trim()?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                if (lat == null || lon == null) {
                    ViewUtils.showToast(context, R.string.text_invalid_coordinates)
                    return@onPositive
                }
                dialog.dismiss()
                onDone(GeoLocation(lat, lon))
            }
            .show()
    }

    /** Chooses the hemisphere of seasons; null infers it from the time zone (D20). zh-CN: 选择季节所用的半球; null 表示由时区推断 (D20). */
    fun editHemisphere(context: Context, current: Hemisphere?, onDone: (Hemisphere?) -> Unit) {
        val inferred = TimeZoneLocations.infer(ZoneId.systemDefault()).location.hemisphere
        val options = listOf(null, Hemisphere.NORTH, Hemisphere.SOUTH)
        val labels = listOf(
            context.getString(R.string.text_location_from_time_zone, hemisphereLabel(context, inferred)),
            hemisphereLabel(context, Hemisphere.NORTH),
            hemisphereLabel(context, Hemisphere.SOUTH),
        )
        MaterialDialog.Builder(context)
            .title(R.string.text_hemisphere)
            .items(labels)
            .itemsCallbackSingleChoice(options.indexOf(current)) { _, _, which, _ ->
                onDone(options[which])
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    fun hemisphereLabel(context: Context, hemisphere: Hemisphere): String = context.getString(
        if (hemisphere == Hemisphere.SOUTH) R.string.text_hemisphere_south else R.string.text_hemisphere_north
    )

    /** Edits battery thresholds as comma separated percentages. zh-CN: 以逗号分隔的百分比编辑电量阈值. */
    fun editBatteryThresholds(context: Context, current: List<Int>, onDone: (List<Int>) -> Unit) {
        MaterialDialog.Builder(context)
            .title(R.string.text_battery_thresholds)
            .content(context.getString(R.string.text_battery_thresholds_hint, StateSource.Battery.MAX_THRESHOLDS))
            .input("20, 80", current.joinToString(", "), false) { dialog, input ->
                val values = input.split(',', '，', ' ').filter { it.isNotBlank() }.map { it.trim().toIntOrNull() }
                val normalized = values.takeIf { list -> list.none { it == null } }?.filterNotNull()?.let(StateSource.Battery::normalized)
                if (normalized == null || normalized.thresholds.size != values.size) {
                    ViewUtils.showToast(context, R.string.text_invalid_battery_thresholds)
                } else {
                    dialog.dismiss()
                    onDone(normalized.thresholds)
                }
            }
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    /**
     * Creates or edits a date override: first the kind (for a new one), then its condition and the group to show.
     * zh-CN: 新建或编辑日期覆盖层: 新建时先选择类型, 然后设置条件与要显示的组.
     */
    fun editOverride(
        context: Context,
        config: IntelligentConfig,
        groups: List<IntelligentGroup>,
        existing: DateOverride?,
        onDone: (DateOverride) -> Unit,
        onDelete: (() -> Unit)?,
    ) {
        if (groups.isEmpty()) {
            ViewUtils.showToast(context, R.string.text_no_groups_for_override)
            return
        }
        when (val condition = existing?.condition) {
            is DateCondition.DateRange -> editDateRange(context, config, groups, condition, existing.groupId, onDone, onDelete)
            is DateCondition.Holiday -> editHoliday(context, config, groups, condition, existing.groupId, onDone, onDelete)
            is DateCondition.Unsupported -> onDelete?.let { delete ->
                ColorLibraryDialogs.showConfirm(context, context.getString(R.string.text_hint_unsupported_items)) { delete() }
            }
            null -> ColorLibraryDialogs.showSingleChoice(
                context, R.string.text_add_override,
                listOf(context.getString(R.string.text_override_date_range), context.getString(R.string.text_override_holiday)),
            ) { which ->
                val group = groups.first().id
                when (which) {
                    0 -> editDateRange(context, config, groups, null, group, onDone, null)
                    else -> editHoliday(context, config, groups, null, group, onDone, null)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun editDateRange(
        context: Context,
        config: IntelligentConfig,
        groups: List<IntelligentGroup>,
        range: DateCondition.DateRange?,
        groupId: Long,
        onDone: (DateOverride) -> Unit,
        onDelete: (() -> Unit)?,
    ) {
        val padding = dp(context, 20)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(context, 8), padding, 0)
        }
        fun field(hintRes: Int, value: String, type: Int) = AppCompatEditText(context).apply {
            hint = context.getString(hintRes)
            inputType = type
            setText(value)
        }
        val from = field(R.string.text_override_from, range?.let { monthDay(it.fromMonth, it.fromDay) } ?: "12-24", InputType.TYPE_CLASS_DATETIME)
        val to = field(R.string.text_override_to, range?.let { monthDay(it.toMonth, it.toDay) } ?: "12-26", InputType.TYPE_CLASS_DATETIME)
        val year = field(R.string.text_override_year_optional, range?.year?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER)
        var target = groupId
        val groupRow = groupRow(context, config, groups) { target }.also { row -> row.setOnClickListener { chooseGroup(context, config, groups, target) { target = it; updateGroupRow(context, config, groups, row, it) } } }
        listOf(R.string.text_override_from to from, R.string.text_override_to to to, R.string.text_override_year_optional to year).forEach { (label, view) ->
            content.addView(secondaryText(context, context.getString(label)))
            content.addView(view)
        }
        content.addView(groupRow)
        overrideDialog(context, if (range == null) R.string.text_add_override else R.string.text_edit_override, content, onDelete) {
            val start = parseMonthDay(from.text?.toString())
            val end = parseMonthDay(to.text?.toString())
            val yearText = year.text?.toString()?.trim().orEmpty()
            val onlyYear = yearText.toIntOrNull()?.takeIf { it in 1900..2100 }
            if (start == null || end == null || (yearText.isNotEmpty() && onlyYear == null)) {
                ViewUtils.showToast(context, R.string.text_invalid_month_day)
                return@overrideDialog false
            }
            onDone(DateOverride(DateCondition.DateRange(start.first, start.second, end.first, end.second, onlyYear), target))
            true
        }
    }

    private fun editHoliday(
        context: Context,
        config: IntelligentConfig,
        groups: List<IntelligentGroup>,
        holiday: DateCondition.Holiday?,
        groupId: Long,
        onDone: (DateOverride) -> Unit,
        onDelete: (() -> Unit)?,
    ) {
        val locale = context.resources.configuration.locales[0]
        val holidays = HolidayCalendar.suggestedOrder(locale)
        var id = holiday?.id ?: holidays.first().id
        var target = groupId
        val padding = dp(context, 20)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(context, 8), padding, 0)
        }
        val holidayRow = primaryRow(context, IntelligentSummaryFormatter.holidayName(context, id)).apply {
            setOnClickListener {
                val labels = holidays.map { IntelligentSummaryFormatter.holidayName(context, it.id) }
                ColorLibraryDialogs.showSingleChoice(context, R.string.text_choose_holiday, labels) { which ->
                    id = holidays[which].id
                    text = labels[which]
                }
            }
        }
        fun days(hintRes: Int, value: Int) = AppCompatEditText(context).apply {
            hint = context.getString(hintRes)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
        }
        val before = days(R.string.text_override_days_before, holiday?.daysBefore ?: 0)
        val after = days(R.string.text_override_days_after, holiday?.daysAfter ?: 0)
        val groupRow = groupRow(context, config, groups) { target }.also { row -> row.setOnClickListener { chooseGroup(context, config, groups, target) { target = it; updateGroupRow(context, config, groups, row, it) } } }
        content.addView(secondaryText(context, context.getString(R.string.text_override_holiday)))
        content.addView(holidayRow)
        content.addView(secondaryText(context, context.getString(R.string.text_override_days_before)))
        content.addView(before)
        content.addView(secondaryText(context, context.getString(R.string.text_override_days_after)))
        content.addView(after)
        content.addView(groupRow)
        overrideDialog(context, if (holiday == null) R.string.text_add_override else R.string.text_edit_override, content, onDelete) {
            fun extra(view: AppCompatEditText) = (view.text?.toString()?.trim()?.toIntOrNull() ?: 0).coerceIn(0, DateCondition.Holiday.MAX_EXTRA_DAYS)
            onDone(DateOverride(DateCondition.Holiday(id, extra(before), extra(after)), target))
            true
        }
    }

    private fun overrideDialog(context: Context, titleRes: Int, content: View, onDelete: (() -> Unit)?, onConfirm: () -> Boolean) {
        MaterialDialog.Builder(context)
            .title(titleRes)
            .customView(content, true)
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { dialog, _ -> if (onConfirm()) dialog.dismiss() }
            .apply {
                if (onDelete != null) {
                    neutralText(R.string.text_delete)
                    neutralColorRes(R.color.dialog_button_caution)
                    onNeutral { dialog, _ ->
                        dialog.dismiss()
                        onDelete()
                    }
                }
            }
            .show()
    }

    private fun groupRow(context: Context, config: IntelligentConfig, groups: List<IntelligentGroup>, current: () -> Long): TextView {
        return primaryRow(context, "").also { updateGroupRow(context, config, groups, it, current()) }
    }

    private fun updateGroupRow(context: Context, config: IntelligentConfig, groups: List<IntelligentGroup>, row: TextView, groupId: Long) {
        val name = groups.firstOrNull { it.id == groupId }?.let { IntelligentSummaryFormatter.groupName(config, it) } ?: "-"
        row.text = "${context.getString(R.string.text_override_target_group)}: $name"
    }

    private fun chooseGroup(context: Context, config: IntelligentConfig, groups: List<IntelligentGroup>, current: Long, onChosen: (Long) -> Unit) {
        val labels = groups.map { IntelligentSummaryFormatter.groupName(config, it) }
        MaterialDialog.Builder(context)
            .title(R.string.text_override_target_group)
            .items(labels)
            .itemsCallbackSingleChoice(groups.indexOfFirst { it.id == current }) { _, _, which, _ ->
                onChosen(groups[which].id)
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    fun monthDay(month: Int, day: Int) = "%02d-%02d".format(month, day)

    /** Parses `MM-DD` (also `M-D` and `MM/DD`), allowing 02-29. zh-CN: 解析 `MM-DD` (也接受 `M-D` 与 `MM/DD`), 允许 02-29. */
    fun parseMonthDay(text: String?): Pair<Int, Int>? {
        val parts = text?.trim()?.split('-', '/', '.')?.takeIf { it.size == 2 } ?: return null
        val month = parts[0].trim().toIntOrNull() ?: return null
        val day = parts[1].trim().toIntOrNull() ?: return null
        val days = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return if (month in 1..12 && day in 1..days[month - 1]) month to day else null
    }

    private fun primaryRow(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 16f
        setTextColor(context.getColor(R.color.day_night_full))
        setPadding(0, dp(context, 12), 0, dp(context, 12))
        setBackgroundResource(selectableBackground(context))
    }

    private fun secondaryText(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(context.getColor(R.color.day_night_alpha_60))
        setPadding(0, dp(context, 8), 0, 0)
    }

    private fun selectableBackground(context: Context): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return value.resourceId
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

}
