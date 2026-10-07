package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.intelligent.DateCondition
import org.autojs.autojs.theme.intelligent.DateOverride
import org.autojs.autojs.theme.intelligent.GroupOrder
import org.autojs.autojs.theme.intelligent.IntelligentAutoFill
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentConfigValidator
import org.autojs.autojs.theme.intelligent.IntelligentConfigValidator.Issue
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.IntervalAnchor
import org.autojs.autojs.theme.intelligent.IntervalSchedule
import org.autojs.autojs.theme.intelligent.IntervalUnit
import org.autojs.autojs.theme.intelligent.RotationTrigger
import org.autojs.autojs.theme.intelligent.SortKey
import org.autojs.autojs.theme.intelligent.StateColors
import org.autojs.autojs.theme.intelligent.StateSource
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.theme.library.LibraryNaming
import org.autojs.autojs.theme.widget.ThemeColorCheckBox
import org.autojs.autojs.theme.widget.ThemeColorRadioButton
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityIntelligentLibraryEditorBinding
import org.autojs.autojs6.databinding.MtColorLibraryRecyclerViewItemBinding
import java.time.ZoneId
import org.autojs.autojs.theme.color.ThemeColorDerivation

/**
 * Creates or edits a user intelligent library (roadmap P1.3): name, state source, rotation triggers,
 * group order and groups with one color per state.
 * zh-CN: 新建或编辑用户智能库 (Roadmap P1.3): 名称, 状态源, 换组时机, 切换方式, 以及每个状态各有一个颜色的组.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
class IntelligentLibraryEditorActivity : ColorSelectBaseActivity() {

    private data class EditorModel(val name: String, val config: IntelligentConfig, val groups: List<IntelligentGroup>)

    private lateinit var binding: MtActivityIntelligentLibraryEditorBinding

    private val repository by lazy { ColorLibraryRepository.get(this) }

    private val rowId by lazy { intent.getLongExtra(EXTRA_ROW_ID, -1L).takeIf { it > 0L } }

    private var stateSource: StateSource = StateSource.NightMode
    private var stateChangeChecked = true
    private var appStartChecked = false
    private var scriptStartChecked = false
    private var intervalChecked = false
    private var interval = RotationTrigger.Interval(1, IntervalUnit.HOUR, IntervalAnchor.NEXT_HOUR)
    private var dailyChecked = false
    private var daily = RotationTrigger.Daily(listOf(8 * 60, 20 * 60))
    private var order: GroupOrder = GroupOrder.Sequential()

    /** Rotation triggers this version cannot edit, kept as they are. zh-CN: 当前版本无法编辑的换组时机, 原样保留. */
    private var keptRotation: List<RotationTrigger> = emptyList()

    /** Date overrides in priority order (roadmap P3.5). zh-CN: 按优先级排列的日期覆盖层 (Roadmap P3.5). */
    private val overrides = mutableListOf<DateOverride>()

    private val groups = mutableListOf<IntelligentGroup>()
    private var nextTemporaryGroupId = -1L

    private var initialModel: EditorModel? = null
    private var isBinding = false

    private val groupAdapter = EditorGroupAdapter()
    private val overrideAdapter = EditorOverrideAdapter()

    private val stateKeys: List<String>
        get() = stateSource.stateKeys

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MtActivityIntelligentLibraryEditorBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpAppBar(it.appBar, it.appBarContainer)
            setUpToolbar(it.toolbar)
            it.toolbar.title = getString(if (rowId == null) R.string.text_new_intelligent_color_library else R.string.text_edit_intelligent_color_library)
            it.toolbar.setNavigationOnClickListener { attemptExit() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = attemptExit()
        })

        binding.groupsRecyclerView.let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = groupAdapter
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            ItemTouchHelper(GroupDragCallback()).attachToRecyclerView(it)
        }

        binding.overridesRecyclerView.let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = overrideAdapter
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            ItemTouchHelper(OverrideDragCallback()).attachToRecyclerView(it)
        }

        binding.stateSourceRow.setOnClickListener { chooseStateSource() }
        binding.intervalSummary.setOnClickListener { editInterval() }
        binding.dailySummary.setOnClickListener { editDailyTimes() }
        binding.rotationDaily.setOnCheckedChangeListener { _, checked -> if (!isBinding) dailyChecked = checked.also { refresh() } }
        binding.addOverride.setOnClickListener { editOverride(null) }
        binding.orderRow.setOnClickListener { chooseOrder() }
        binding.referenceStateRow.setOnClickListener { chooseReferenceState() }
        binding.addGroup.setOnClickListener { editGroup(null) }
        binding.rotationStateChange.setOnCheckedChangeListener { _, checked -> if (!isBinding) stateChangeChecked = checked.also { refresh() } }
        binding.rotationAppStart.setOnCheckedChangeListener { _, checked -> if (!isBinding) appStartChecked = checked.also { refresh() } }
        binding.rotationScriptStart.setOnCheckedChangeListener { _, checked -> if (!isBinding) scriptStartChecked = checked.also { refresh() } }
        binding.rotationInterval.setOnCheckedChangeListener { _, checked -> if (!isBinding) intervalChecked = checked.also { refresh() } }
        binding.orderDescending.setOnCheckedChangeListener { _, checked ->
            if (!isBinding) (order as? GroupOrder.Sequential)?.let { order = it.copy(descending = checked).also { refresh() } }
        }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val existing = rowId?.let { id -> withContext(Dispatchers.IO) { repository.loadIntelligent(id) } }
            if (rowId != null && existing == null) {
                finish()
                return@launch
            }
            if (existing == null) {
                binding.nameInput.setText(getString(R.string.color_library_intelligent_colors))
                groups += IntelligentGroup(id = nextTemporaryGroupId--, states = mapOf(stateKeys.first() to StateColors(ThemeColorManager.colorPrimary)))
            } else {
                binding.nameInput.setText(existing.name)
                val config = existing.config
                stateSource = config.stateSource
                stateChangeChecked = RotationTrigger.StateChange in config.rotation
                appStartChecked = RotationTrigger.AppStart in config.rotation
                scriptStartChecked = RotationTrigger.ScriptStart in config.rotation
                config.interval?.let {
                    intervalChecked = true
                    interval = it
                }
                config.daily?.let {
                    dailyChecked = true
                    daily = it
                }
                keptRotation = config.rotation.filterIsInstance<RotationTrigger.Unsupported>()
                overrides += config.overrides
                order = config.order
                groups += existing.groups
            }
            initialModel = currentModel()
            refresh()
        }
    }

    private fun currentModel() = EditorModel(binding.nameInput.text?.toString().orEmpty().trim(), buildConfig(), groups.toList())

    private fun buildConfig() = IntelligentConfig(
        stateSource = stateSource,
        rotation = buildList {
            if (stateChangeChecked && stateKeys.size > 1) add(RotationTrigger.StateChange)
            if (appStartChecked) add(RotationTrigger.AppStart)
            if (scriptStartChecked) add(RotationTrigger.ScriptStart)
            if (intervalChecked) add(interval)
            if (dailyChecked) add(daily)
            addAll(keptRotation)
        },
        order = order,
        overrides = overrides.toList(),
    )

    private fun refresh() {
        isBinding = true
        binding.stateSourceSummary.text = IntelligentSummaryFormatter.stateSourceDetail(this, stateSource)
        binding.rotationStateChange.isChecked = stateChangeChecked && stateKeys.size > 1
        binding.rotationStateChange.isEnabled = stateKeys.size > 1
        binding.rotationAppStart.isChecked = appStartChecked
        binding.rotationScriptStart.isChecked = scriptStartChecked
        binding.rotationInterval.isChecked = intervalChecked
        binding.intervalSummary.text = intervalSummary()
        binding.rotationDaily.isChecked = dailyChecked
        binding.dailySummary.text = IntelligentSummaryFormatter.dailyLabel(this, daily.times)
        binding.orderSummary.text = IntelligentSummaryFormatter.orderLabel(this, order)
        val sequential = order as? GroupOrder.Sequential
        binding.orderDescending.isVisible = sequential != null
        binding.orderDescending.isChecked = sequential?.descending == true
        binding.referenceStateRow.isVisible = sequential != null && sequential.key in COLOR_SORT_KEYS && stateKeys.size > 1
        binding.referenceStateSummary.text = IntelligentSummaryFormatter.stateLabel(this, stateSource, referenceStateOf(sequential))
        isBinding = false

        groupAdapter.notifyDataSetChanged()
        overrideAdapter.notifyDataSetChanged()
        binding.groupsEmpty.isVisible = groups.isEmpty()
        binding.hintsView.text = hints().joinToString("\n") { "- $it" }
        binding.hintsView.isVisible = binding.hintsView.text.isNotEmpty()
    }

    private fun referenceStateOf(order: GroupOrder.Sequential?) = order?.referenceState?.takeIf { it in stateKeys } ?: stateKeys.first()

    private fun intervalSummary(): String {
        val anchor = getString(
            when (interval.anchor) {
                IntervalAnchor.NOW -> R.string.text_interval_anchor_now
                IntervalAnchor.NEXT_MINUTE -> R.string.text_interval_anchor_next_minute
                IntervalAnchor.NEXT_HOUR -> R.string.text_interval_anchor_next_hour
            }
        )
        return "${IntelligentSummaryFormatter.intervalLabel(this, interval.every, interval.unit)} | $anchor"
    }

    private fun hints(): List<String> {
        val issues = IntelligentConfigValidator.validate(buildConfig(), groups).issues
        return HINTS.filter { (issue, _) -> issue in issues }.map { (_, res) -> getString(res) }
    }

    /**
     * All state sources (roadmap 4.4); configurable ones keep their current settings when already selected.
     * zh-CN: 全部状态源 (Roadmap 4.4); 可设置的状态源在已选中时沿用当前设置.
     */
    private fun sourceOptions(): List<StateSource> = listOf(
        StateSource.None,
        StateSource.NightMode,
        stateSource as? StateSource.TimePeriods ?: StateSource.TimePeriods.DEFAULT,
        stateSource as? StateSource.SunriseSunset ?: StateSource.SunriseSunset(),
        StateSource.Weekday(workdaysOnly = false),
        StateSource.Weekday(workdaysOnly = true),
        stateSource as? StateSource.Seasons ?: StateSource.Seasons(),
        stateSource as? StateSource.Battery ?: StateSource.Battery.DEFAULT,
        StateSource.Charging,
        StateSource.PowerSave,
        StateSource.AccessibilityService,
        StateSource.ScriptRunning,
        stateSource as? StateSource.ScriptStates ?: StateSource.ScriptStates.DEFAULT,
    )

    private fun chooseStateSource() {
        val sources = sourceOptions()
        val labels = sources.map { IntelligentSummaryFormatter.stateSourceLabel(this, it) ?: getString(R.string.text_state_source_none) }
        MaterialDialog.Builder(this)
            .title(R.string.text_state_source)
            .items(labels)
            .itemsCallbackSingleChoice(sources.indexOf(stateSource)) { _, _, which, _ ->
                sources.getOrNull(which)?.let { configureStateSource(it) }
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    /** Opens the settings of configurable sources, then applies the source. zh-CN: 可设置的状态源先打开其设置, 再应用该状态源. */
    private fun configureStateSource(source: StateSource) {
        when (source) {
            is StateSource.TimePeriods -> IntelligentConditionDialogs.editTimes(
                this, getString(R.string.text_state_source_time_periods), source.starts,
                StateSource.TimePeriods.MIN_PERIODS, StateSource.TimePeriods.MAX_PERIODS,
                getString(R.string.text_period_count_range, StateSource.TimePeriods.MIN_PERIODS, StateSource.TimePeriods.MAX_PERIODS),
            ) { starts -> StateSource.TimePeriods.normalized(starts)?.let(::changeStateSource) }
            is StateSource.SunriseSunset -> IntelligentConditionDialogs.editLocation(this, source.location) { changeStateSource(StateSource.SunriseSunset(it)) }
            is StateSource.Seasons -> IntelligentConditionDialogs.editHemisphere(this, source.hemisphere) { changeStateSource(StateSource.Seasons(it)) }
            is StateSource.Battery -> IntelligentConditionDialogs.editBatteryThresholds(this, source.thresholds) { changeStateSource(StateSource.Battery(it)) }
            is StateSource.ScriptStates -> IntelligentConditionDialogs.editScriptStates(this, source.names) { changeStateSource(StateSource.ScriptStates(it)) }
            else -> if (source != stateSource) changeStateSource(source)
        }
    }

    /**
     * Moves each group's colors to the new states: by state key when the sources share keys (day / night, on / off,
     * or the same source with new settings), otherwise by position. A group never loses all of its colors: the first
     * colored state moves to the first new state (roadmap P3.6).
     * zh-CN: 将各组颜色迁移到新的状态: 两个状态源有相同的 key 时 (日 / 夜, 开 / 关, 或同一状态源换了设置) 按 key 迁移,
     * 否则按位置迁移. 组不会丢失全部颜色: 第一个有颜色的状态会迁移到第一个新状态 (Roadmap P3.6).
     */
    private fun changeStateSource(newSource: StateSource) {
        val oldKeys = stateKeys
        val newKeys = newSource.stateKeys
        val byKey = oldKeys.any { it in newKeys }
        groups.replaceAll { group ->
            val moved = newKeys.mapIndexedNotNull { index, key ->
                val colors = if (byKey) group.states[key] else oldKeys.getOrNull(index)?.let { group.states[it] }
                colors?.takeIf { it.primary != null }?.let { key to it }
            }.toMap()
            val firstColored = oldKeys.firstNotNullOfOrNull { group.states[it]?.takeIf { colors -> colors.primary != null } }
                ?: group.states.values.firstOrNull { it.primary != null }
            group.copy(states = moved.ifEmpty { firstColored?.let { mapOf(newKeys.first() to it) }.orEmpty() })
        }
        stateSource = newSource
        (order as? GroupOrder.Sequential)?.let { order = it.copy(referenceState = it.referenceState?.takeIf { key -> key in newKeys }) }
        refresh()
    }

    private fun editDailyTimes() {
        IntelligentConditionDialogs.editTimes(
            this, getString(R.string.text_rotation_daily), daily.times, 1, RotationTrigger.Daily.MAX_TIMES,
            getString(R.string.text_time_count_limit, RotationTrigger.Daily.MAX_TIMES),
        ) { times ->
            RotationTrigger.Daily.normalized(times)?.let {
                daily = it
                dailyChecked = true
                refresh()
            }
        }
    }

    private fun editOverride(index: Int?) {
        IntelligentConditionDialogs.editOverride(
            this, buildConfig(), groups.toList(), index?.let { overrides.getOrNull(it) },
            onDone = { edited ->
                if (index == null) overrides += edited else overrides[index] = edited
                refresh()
            },
            onDelete = index?.let { position ->
                {
                    overrides.removeAt(position)
                    refresh()
                }
            },
        )
    }

    @SuppressLint("SetTextI18n")
    private fun editInterval() {
        val padding = dp(20)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(8), padding, 0)
        }
        val valueInput = AppCompatEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.text_interval_value)
            setText(interval.every.toString())
        }
        val units = IntervalUnit.entries
        val unitLabels = listOf(R.string.text_interval_unit_second, R.string.text_interval_unit_minute, R.string.text_interval_unit_hour)
        val unitGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        units.forEachIndexed { index, unit ->
            unitGroup.addView(ThemeColorRadioButton(this).apply {
                id = View.generateViewId()
                text = getString(unitLabels[index])
                tag = unit
                isChecked = unit == interval.unit
            })
        }
        val anchorHeader = TextView(this).apply {
            text = getString(R.string.text_interval_first_rotation)
            setPadding(0, dp(12), 0, dp(4))
            setTextColor(getColor(R.color.day_night_alpha_60))
        }
        val anchors = IntervalAnchor.entries
        val anchorGroup = RadioGroup(this)
        val anchorButtons = anchors.map { anchor ->
            ThemeColorRadioButton(this).apply {
                id = View.generateViewId()
                tag = anchor
                isChecked = anchor == interval.anchor
            }.also { anchorGroup.addView(it) }
        }
        fun checkedUnit() = unitGroup.findViewById<View>(unitGroup.checkedRadioButtonId)?.tag as? IntervalUnit ?: interval.unit
        fun enteredEvery(unit: IntervalUnit) = RotationTrigger.Interval.normalizedEvery(valueInput.text?.toString()?.toIntOrNull() ?: interval.every, unit)
        fun updateValueHint() {
            valueInput.hint = getString(R.string.text_interval_value_range, checkedUnit().minimumEvery, RotationTrigger.Interval.MAX_EVERY)
        }
        fun updateAnchorLabels() {
            val now = System.currentTimeMillis()
            val unit = checkedUnit()
            val every = enteredEvery(unit)
            anchorButtons.forEach { button ->
                val anchor = button.tag as IntervalAnchor
                val anchorAt = IntervalSchedule.anchorAt(now, anchor, ZoneId.systemDefault())
                val firstDue = IntervalSchedule.nextDue(anchorAt, every * unit.millis, now)
                val label = getString(
                    when (anchor) {
                        IntervalAnchor.NOW -> R.string.text_interval_anchor_now
                        IntervalAnchor.NEXT_MINUTE -> R.string.text_interval_anchor_next_minute
                        IntervalAnchor.NEXT_HOUR -> R.string.text_interval_anchor_next_hour
                    }
                )
                button.text = "$label (${getString(R.string.text_first_rotation_at, formatTime(firstDue))})"
            }
        }
        updateValueHint()
        updateAnchorLabels()
        valueInput.addTextChangedListener(SimpleTextWatcher { updateAnchorLabels() })
        unitGroup.setOnCheckedChangeListener { _, _ ->
            updateValueHint()
            updateAnchorLabels()
        }
        content.addView(valueInput)
        content.addView(unitGroup)
        content.addView(anchorHeader)
        content.addView(anchorGroup)

        MaterialDialog.Builder(this)
            .title(R.string.text_rotation_interval)
            .customView(content, true)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                val unit = checkedUnit()
                val every = enteredEvery(unit)
                val anchor = anchorGroup.findViewById<View>(anchorGroup.checkedRadioButtonId)?.tag as? IntervalAnchor ?: interval.anchor
                interval = RotationTrigger.Interval(every, unit, anchor)
                intervalChecked = true
                refresh()
            }
            .show()
    }

    private fun chooseOrder() {
        val options: List<GroupOrder> = SortKey.entries.map { key ->
            (order as? GroupOrder.Sequential)?.copy(key = key) ?: GroupOrder.Sequential(key)
        } + GroupOrder.Random
        val labels = options.map { IntelligentSummaryFormatter.orderLabel(this, it) }
        val selected = when (val current = order) {
            GroupOrder.Random -> options.lastIndex
            is GroupOrder.Sequential -> SortKey.entries.indexOf(current.key)
        }
        MaterialDialog.Builder(this)
            .title(R.string.text_group_order)
            .items(labels)
            .itemsCallbackSingleChoice(selected) { _, _, which, _ ->
                options.getOrNull(which)?.let { order = it.also { refresh() } }
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    private fun chooseReferenceState() {
        val sequential = order as? GroupOrder.Sequential ?: return
        ColorLibraryDialogs.showSingleChoice(this, R.string.text_reference_state, stateKeys.map { IntelligentSummaryFormatter.stateLabel(this, stateSource, it) }) { which ->
            order = sequential.copy(referenceState = stateKeys[which])
            refresh()
        }
    }

    /**
     * Edits the group at [index], or adds a new one when it is null.
     * zh-CN: 编辑下标为 [index] 的组; 为 null 时新建组.
     */
    @SuppressLint("SetTextI18n")
    private fun editGroup(index: Int?) {
        val original = index?.let { groups.getOrNull(it) }
        val keys = stateKeys
        // Primary color per state; null means "same as the first colored state". zh-CN: 各状态的主色; null 表示 "与第一个有颜色的状态相同".
        val colors = keys.associateWith { key -> original?.states?.get(key)?.primary }.toMutableMap()
        if (original == null) colors[keys.first()] = ThemeColorManager.colorPrimary
        // Colors chosen before "same as" was checked, restored when it is unchecked. zh-CN: 勾选 "相同" 之前选择的颜色, 取消勾选时恢复.
        val remembered = colors.filterValues { it != null }.toMutableMap()
        // Explicit accent / dark per state; null follows the derivation setting (D2). zh-CN: 各状态显式的强调色 / 深色; null 表示跟随派生配色设置 (D2).
        val accents = keys.associateWith { key -> original?.states?.get(key)?.accent }.toMutableMap()
        val darks = keys.associateWith { key -> original?.states?.get(key)?.dark }.toMutableMap()

        val padding = dp(20)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(8), padding, 0)
        }
        val nameInput = AppCompatEditText(this).apply {
            hint = getString(R.string.text_group_name_optional)
            isSingleLine = true
            setText(original?.name.orEmpty())
        }
        val enabledCheck = ThemeColorCheckBox(this).apply {
            text = getString(R.string.text_group_enabled)
            isChecked = original?.enabled ?: true
        }
        content.addView(nameInput)
        content.addView(enabledCheck)

        val firstLabel = stateLabelForEditor(keys.first())
        val renders = mutableListOf<() -> Unit>()
        val sameChecks = mutableMapOf<String, ThemeColorCheckBox>()
        // Set while the auto fill updates the checkboxes, so their listeners leave the colors alone.
        // zh-CN: 自动填充更新复选框期间置位, 使其监听器不改动颜色.
        var updating = false
        var onManualChange: () -> Unit = {}
        keys.forEachIndexed { position, key ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val label = TextView(this).apply {
                text = stateLabelForEditor(key)
                minWidth = dp(56)
                setTextColor(getColor(R.color.day_night_full))
            }
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(1), getColor(R.color.day_night_alpha_20))
                }
                isClickable = true
            }
            val hex = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(getColor(R.color.day_night_alpha_60))
            }
            val sameCheck = if (position == 0) null else ThemeColorCheckBox(this).apply {
                text = getString(R.string.text_same_as_state, firstLabel)
                isChecked = colors[key] == null
            }
            // Accent and dark of this state, shown on demand (roadmap P4.6). zh-CN: 该状态的强调色与深色, 按需展开 (Roadmap P4.6).
            val derivedPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, 0, dp(4))
                visibility = if (accents[key] != null || darks[key] != null) View.VISIBLE else View.GONE
            }
            val derivedToggle = TextView(this).apply {
                text = getString(R.string.text_accent_and_dark)
                textSize = 12f
                setTextColor(getColor(R.color.day_night_alpha_60))
                setPadding(dp(8), dp(4), 0, dp(4))
                setOnClickListener { derivedPanel.visibility = if (derivedPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            }
            val derivedRenders = listOf(
                addDerivedColorRow(derivedPanel, R.string.text_accent_color, { colors[key] }, accents, key, ThemeColorDerivation::deriveAccent),
                addDerivedColorRow(derivedPanel, R.string.text_dark_color, { colors[key] }, darks, key, ThemeColorDerivation::deriveDark),
            )
            fun render() {
                val color = colors[key] ?: keys.firstNotNullOfOrNull { colors[it] }
                (swatch.background as GradientDrawable).setColor(color ?: getColor(R.color.window_background))
                hex.text = color?.let { String.format("#%06X", 0xFFFFFF and it) } ?: "-"
                // A state without its own primary color shows the first state as a whole. zh-CN: 没有自身主色的状态整体显示第一个状态.
                val own = colors[key] != null
                derivedToggle.visibility = if (own) View.VISIBLE else View.GONE
                if (!own) derivedPanel.visibility = View.GONE
                derivedRenders.forEach { it() }
            }
            val pick = View.OnClickListener {
                showColorPickerFor(colors[key] ?: remembered[key] ?: ThemeColorManager.colorPrimary) { picked ->
                    colors[key] = picked
                    remembered[key] = picked
                    sameCheck?.isChecked = false
                    onManualChange()
                    renders.forEach { it() }
                }
            }
            swatch.setOnClickListener(pick)
            hex.setOnClickListener(pick)
            sameCheck?.setOnCheckedChangeListener { _, checked ->
                if (updating) return@setOnCheckedChangeListener
                colors[key] = if (checked) null else remembered[key] ?: colors[keys.first()]
                onManualChange()
                renders.forEach { it() }
            }
            renders += ::render
            sameCheck?.let { sameChecks[key] = it }
            render()
            row.addView(label)
            row.addView(swatch)
            row.addView(hex)
            row.addView(derivedToggle)
            content.addView(row)
            content.addView(derivedPanel)
            sameCheck?.let { content.addView(it) }
        }

        // Auto fill derives the empty states from the first colored one; tapping again undoes it (roadmap P4.4).
        // zh-CN: 自动填充由第一个有颜色的状态推导其余空状态; 再次点击可撤销 (Roadmap P4.4).
        if (keys.size > 1) {
            var undo: Map<String, Int?>? = null
            val autoFill = TextView(this).apply {
                text = getString(R.string.text_auto_fill)
                setTextColor(getColor(R.color.day_night_full))
                setPadding(0, dp(12), 0, dp(4))
            }
            fun sync() {
                updating = true
                sameChecks.forEach { (key, check) -> check.isChecked = colors[key] == null }
                updating = false
                renders.forEach { it() }
            }
            autoFill.setOnClickListener {
                when (val previous = undo) {
                    null -> {
                        val filled = IntelligentAutoFill.fill(keys, colors) ?: run {
                            ViewUtils.showToast(this, R.string.text_choose_at_least_one_color)
                            return@setOnClickListener
                        }
                        undo = colors.toMap()
                        colors.putAll(filled)
                        remembered.putAll(filled)
                        autoFill.text = getString(R.string.text_undo_auto_fill)
                    }
                    else -> {
                        colors.clear()
                        colors.putAll(previous)
                        undo = null
                        autoFill.text = getString(R.string.text_auto_fill)
                    }
                }
                sync()
            }
            onManualChange = {
                undo = null
                autoFill.text = getString(R.string.text_auto_fill)
            }
            content.addView(autoFill)
        }

        MaterialDialog.Builder(this)
            .title(if (original == null) R.string.text_new_group else R.string.text_edit_group)
            .customView(content, true)
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { dialog, _ ->
                val states = colors.mapNotNull { (key, color) -> color?.let { key to StateColors(it, accents[key], darks[key]) } }.toMap()
                if (states.isEmpty()) {
                    ViewUtils.showToast(this, R.string.text_choose_at_least_one_color)
                    return@onPositive
                }
                val edited = (original ?: IntelligentGroup(id = nextTemporaryGroupId--)).copy(
                    name = LibraryNaming.normalize(nameInput.text?.toString()),
                    enabled = enabledCheck.isChecked,
                    states = states,
                    modifiedTime = System.currentTimeMillis(),
                )
                if (index == null) groups += edited else groups[index] = edited
                dialog.dismiss()
                refresh()
            }
            .apply {
                if (index != null) {
                    neutralText(R.string.text_delete)
                    neutralColorRes(R.color.dialog_button_caution)
                    onNeutral { dialog, _ ->
                        // Overrides showing the deleted group could never apply. zh-CN: 显示已删除组的覆盖层永远无法生效.
                        val removed = groups.removeAt(index)
                        overrides.removeAll { it.groupId == removed.id }
                        dialog.dismiss()
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun stateLabelForEditor(key: String) = IntelligentSummaryFormatter.stateLabel(this, stateSource, key)

    private val isDirty: Boolean
        get() = initialModel != null && currentModel() != initialModel

    private fun attemptExit() {
        if (!isDirty) {
            finish()
            return
        }
        MaterialDialog.Builder(this)
            .title(R.string.text_unsaved_changes)
            .content(R.string.text_save_changes_confirm)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .neutralText(R.string.dialog_button_discard_changes)
            .neutralColorRes(R.color.dialog_button_caution)
            .onNeutral { _, _ -> finish() }
            .positiveText(R.string.dialog_button_save)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ -> save() }
            .show()
    }

    private fun save() {
        val name = LibraryNaming.normalize(binding.nameInput.text?.toString()) ?: run {
            ViewUtils.showToast(this, R.string.text_name_required)
            return
        }
        val config = buildConfig()
        val issues = IntelligentConfigValidator.validate(config, groups).issues
        when (Issue.NOT_INTELLIGENT) {
            in issues -> MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.text_not_intelligent_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_save)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { _, _ -> persist(name, config) }
                .show()
            else -> persist(name, config)
        }
    }

    private fun persist(name: String, config: IntelligentConfig) {
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { repository.saveIntelligent(rowId, name, config, groups.toList()) }
            IntelligentColorEngine.onLibraryChanged(saved)
            if (rowId == null) IntelligentLibraryActivity.launch(this@IntelligentLibraryEditorActivity, saved.libraryId)
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_intelligent_library_editor, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_save_intelligent_library -> true.also { save() }
        else -> super.onOptionsItemSelected(item)
    }

    private fun formatTime(millis: Long): String {
        val flags = DateUtils.FORMAT_SHOW_TIME or (if (DateUtils.isToday(millis)) 0 else DateUtils.FORMAT_SHOW_DATE)
        return DateUtils.formatDateTime(this, millis, flags)
    }

    /**
     * Adds the row of a state's accent or dark color to [parent] and returns its render function. A null value
     * follows the derivation setting and shows the color that setting currently gives (D2).
     * zh-CN: 向 [parent] 添加某状态强调色或深色的行, 并返回其渲染函数. 值为 null 时跟随派生配色设置, 并显示该设置当前给出的颜色 (D2).
     */
    private fun addDerivedColorRow(parent: LinearLayout, @StringRes labelRes: Int, primaryOf: () -> Int?, values: MutableMap<String, Int?>, key: String, derive: (Int) -> Int): () -> Unit {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val label = TextView(this).apply {
            text = getString(labelRes)
            minWidth = dp(56)
            textSize = 13f
            setTextColor(getColor(R.color.day_night_alpha_60))
        }
        val swatch = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), getColor(R.color.day_night_alpha_20))
            }
            isClickable = true
        }
        val hex = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 13f
            setTextColor(getColor(R.color.day_night_alpha_60))
        }
        val reset = TextView(this).apply {
            text = getString(R.string.text_reset)
            textSize = 12f
            setTextColor(getColor(R.color.day_night_full))
            setPadding(dp(8), dp(4), 0, dp(4))
        }
        fun effective(primary: Int) = values[key] ?: if (ThemeColorManager.isDerivationEnabled) derive(primary) else primary
        fun render() {
            val primary = primaryOf() ?: return
            val color = effective(primary)
            (swatch.background as GradientDrawable).setColor(color)
            val hexText = String.format("#%06X", 0xFFFFFF and color)
            hex.text = if (values[key] != null) hexText else getString(R.string.text_follows_derived_colors, hexText)
            reset.visibility = if (values[key] != null) View.VISIBLE else View.GONE
        }
        val pick = View.OnClickListener {
            val primary = primaryOf() ?: return@OnClickListener
            showColorPickerFor(effective(primary)) { picked ->
                values[key] = picked
                render()
            }
        }
        swatch.setOnClickListener(pick)
        hex.setOnClickListener(pick)
        reset.setOnClickListener {
            values[key] = null
            render()
        }
        row.addView(label)
        row.addView(swatch)
        row.addView(hex)
        row.addView(reset)
        parent.addView(row)
        return ::render
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @SuppressLint("NotifyDataSetChanged")
    private inner class EditorGroupAdapter : RecyclerView.Adapter<ColorItemViewHolder>() {

        override fun getItemCount() = groups.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
            return ColorItemViewHolder(MtColorLibraryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ColorItemViewHolder, position: Int) {
            val config = buildConfig()
            val group = groups[position]
            holder.bind(
                colors = IntelligentSummaryFormatter.stateColors(config, group).ifEmpty { listOf(getColor(R.color.window_background)) },
                name = IntelligentSummaryFormatter.groupName(config, group),
                identifier = if (group.enabled) null else getString(R.string.text_group_disabled),
                checked = false,
            )
            holder.itemView.alpha = if (group.enabled && group.hasColor) 1f else 0.5f
            holder.itemView.setOnClickListener { holder.bindingAdapterPosition.takeIf { it in groups.indices }?.let { editGroup(it) } }
        }

    }

    /**
     * Date overrides in priority order; each row shows the colors and the name of the group it shows.
     * zh-CN: 按优先级排列的日期覆盖层; 每行显示其所显示组的颜色与名称.
     */
    @SuppressLint("NotifyDataSetChanged")
    private inner class EditorOverrideAdapter : RecyclerView.Adapter<ColorItemViewHolder>() {

        override fun getItemCount() = overrides.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
            return ColorItemViewHolder(MtColorLibraryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ColorItemViewHolder, position: Int) {
            val config = buildConfig()
            val override = overrides[position]
            val group = groups.firstOrNull { it.id == override.groupId }
            holder.bind(
                colors = group?.let { IntelligentSummaryFormatter.stateColors(config, it) }?.ifEmpty { null } ?: listOf(getColor(R.color.window_background)),
                name = IntelligentSummaryFormatter.overrideLabel(this@IntelligentLibraryEditorActivity, override.condition),
                identifier = group?.let { IntelligentSummaryFormatter.groupName(config, it) } ?: "-",
                checked = false,
            )
            holder.itemView.alpha = if (group != null && override.condition !is DateCondition.Unsupported) 1f else 0.5f
            holder.itemView.setOnClickListener { holder.bindingAdapterPosition.takeIf { it in overrides.indices }?.let { editOverride(it) } }
        }

    }

    private inner class OverrideDragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from !in overrides.indices || to !in overrides.indices) return false
            overrides.add(to, overrides.removeAt(from))
            overrideAdapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            refresh()
        }

    }

    private inner class GroupDragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from !in groups.indices || to !in groups.indices) return false
            groups.add(to, groups.removeAt(from))
            groupAdapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            refresh()
        }

    }

    private class SimpleTextWatcher(private val onChanged: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) = onChanged()
    }

    companion object {

        private const val EXTRA_ROW_ID = "INTELLIGENT_LIBRARY_ROW_ID"


        private val COLOR_SORT_KEYS = setOf(SortKey.LIGHTNESS, SortKey.HUE, SortKey.CHROMA)

        private val HINTS = listOf(
            Issue.NO_AVAILABLE_GROUPS to R.string.text_hint_no_available_groups,
            Issue.NOT_INTELLIGENT to R.string.text_hint_not_intelligent,
            Issue.ROTATION_WITHOUT_EFFECT to R.string.text_hint_rotation_without_effect,
            Issue.MISSING_STATE_COLORS to R.string.text_hint_missing_state_colors,
            Issue.GROUPS_WITHOUT_COLOR to R.string.text_hint_groups_without_color,
            Issue.SHORT_INTERVAL to R.string.text_hint_short_interval,
            Issue.UNSUPPORTED_ITEMS to R.string.text_hint_unsupported_items,
        )

        /**
         * Opens the editor for the user library [libraryId], or for a new library when it is null.
         * zh-CN: 打开用户库 [libraryId] 的编辑器; 为 null 时新建智能库.
         */
        fun launch(context: Context, libraryId: Int?) {
            Intent(context, IntelligentLibraryEditorActivity::class.java).apply {
                libraryId?.let(ColorLibraryIds::userRowIdOf)?.let { putExtra(EXTRA_ROW_ID, it) }
            }.startSafely(context)
        }

    }

}
