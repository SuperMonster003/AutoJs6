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
import org.autojs.autojs.theme.intelligent.DateOverride
import org.autojs.autojs.theme.intelligent.GroupOrder
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
    private var intervalChecked = false
    private var interval = RotationTrigger.Interval(1, IntervalUnit.HOUR, IntervalAnchor.NEXT_HOUR)
    private var order: GroupOrder = GroupOrder.Sequential()

    /** Items this version cannot edit, kept as they are. zh-CN: 当前版本无法编辑的条目, 原样保留. */
    private var keptRotation: List<RotationTrigger> = emptyList()
    private var keptOverrides: List<DateOverride> = emptyList()

    private val groups = mutableListOf<IntelligentGroup>()
    private var nextTemporaryGroupId = -1L

    private var initialModel: EditorModel? = null
    private var isBinding = false

    private val groupAdapter = EditorGroupAdapter()

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

        binding.stateSourceRow.setOnClickListener { chooseStateSource() }
        binding.intervalSummary.setOnClickListener { editInterval() }
        binding.orderRow.setOnClickListener { chooseOrder() }
        binding.referenceStateRow.setOnClickListener { chooseReferenceState() }
        binding.addGroup.setOnClickListener { editGroup(null) }
        binding.rotationStateChange.setOnCheckedChangeListener { _, checked -> if (!isBinding) stateChangeChecked = checked.also { refresh() } }
        binding.rotationAppStart.setOnCheckedChangeListener { _, checked -> if (!isBinding) appStartChecked = checked.also { refresh() } }
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
                config.interval?.let {
                    intervalChecked = true
                    interval = it
                }
                keptRotation = config.rotation.filterIsInstance<RotationTrigger.Unsupported>()
                keptOverrides = config.overrides
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
            if (intervalChecked) add(interval)
            addAll(keptRotation)
        },
        order = order,
        overrides = keptOverrides,
    )

    private fun refresh() {
        isBinding = true
        binding.stateSourceSummary.text = IntelligentSummaryFormatter.stateSourceLabel(this, stateSource) ?: getString(R.string.text_state_source_none)
        binding.rotationStateChange.isChecked = stateChangeChecked && stateKeys.size > 1
        binding.rotationStateChange.isEnabled = stateKeys.size > 1
        binding.rotationAppStart.isChecked = appStartChecked
        binding.rotationInterval.isChecked = intervalChecked
        binding.intervalSummary.text = intervalSummary()
        binding.orderSummary.text = IntelligentSummaryFormatter.orderLabel(this, order)
        val sequential = order as? GroupOrder.Sequential
        binding.orderDescending.isVisible = sequential != null
        binding.orderDescending.isChecked = sequential?.descending == true
        binding.referenceStateRow.isVisible = sequential != null && sequential.key in COLOR_SORT_KEYS && stateKeys.size > 1
        binding.referenceStateSummary.text = IntelligentSummaryFormatter.stateLabel(this, referenceStateOf(sequential))
        isBinding = false

        groupAdapter.notifyDataSetChanged()
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

    private fun chooseStateSource() {
        val sources = listOf(StateSource.None, StateSource.NightMode)
        val labels = sources.map { IntelligentSummaryFormatter.stateSourceLabel(this, it) ?: getString(R.string.text_state_source_none) }
        MaterialDialog.Builder(this)
            .title(R.string.text_state_source)
            .items(labels)
            .itemsCallbackSingleChoice(sources.indexOf(stateSource)) { _, _, which, _ ->
                sources.getOrNull(which)?.takeIf { it != stateSource }?.let { changeStateSource(it) }
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    /**
     * Moves each group's colors to the new states by position; the first new state falls back to the first colored state.
     * zh-CN: 按位置将各组颜色迁移到新的状态; 第一个新状态回退为第一个有颜色的状态.
     */
    private fun changeStateSource(newSource: StateSource) {
        val oldKeys = stateKeys
        val newKeys = newSource.stateKeys
        groups.replaceAll { group ->
            val moved = newKeys.mapIndexedNotNull { index, key ->
                val colors = oldKeys.getOrNull(index)?.let { group.states[it] }
                    ?: if (index == 0) oldKeys.firstNotNullOfOrNull { group.states[it]?.takeIf { c -> c.primary != null } } else null
                colors?.let { key to it }
            }.toMap()
            group.copy(states = moved)
        }
        stateSource = newSource
        (order as? GroupOrder.Sequential)?.let { order = it.copy(referenceState = null) }
        refresh()
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
        ColorLibraryDialogs.showSingleChoice(this, R.string.text_reference_state, stateKeys.map { IntelligentSummaryFormatter.stateLabel(this, it) }) { which ->
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
            fun render() {
                val color = colors[key] ?: keys.firstNotNullOfOrNull { colors[it] }
                (swatch.background as GradientDrawable).setColor(color ?: getColor(R.color.window_background))
                hex.text = color?.let { String.format("#%06X", 0xFFFFFF and it) } ?: "-"
            }
            val pick = View.OnClickListener {
                showColorPickerFor(colors[key] ?: remembered[key] ?: ThemeColorManager.colorPrimary) { picked ->
                    colors[key] = picked
                    remembered[key] = picked
                    sameCheck?.isChecked = false
                    render()
                }
            }
            swatch.setOnClickListener(pick)
            hex.setOnClickListener(pick)
            sameCheck?.setOnCheckedChangeListener { _, checked ->
                colors[key] = if (checked) null else remembered[key] ?: colors[keys.first()]
                render()
            }
            render()
            row.addView(label)
            row.addView(swatch)
            row.addView(hex)
            content.addView(row)
            sameCheck?.let { content.addView(it) }
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
                val states = colors.mapNotNull { (key, color) -> color?.let { key to (original?.states?.get(key)?.copy(primary = it) ?: StateColors(it)) } }.toMap()
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
                        groups.removeAt(index)
                        dialog.dismiss()
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun stateLabelForEditor(key: String) = when (stateSource) {
        StateSource.NightMode -> IntelligentSummaryFormatter.stateLabel(this, key)
        else -> getString(R.string.text_state_color_single)
    }

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
