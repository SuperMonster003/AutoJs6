package org.autojs.autojs.ui.floating.layoutinspector.search

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.app.AppLevelThemeDialogBuilder
import org.autojs.autojs.core.accessibility.inspector.ClassPreset
import org.autojs.autojs.core.accessibility.inspector.FilterProfile
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.BooleanProperty
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.IntRangeSpec
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.Tri
import org.autojs.autojs.core.accessibility.inspector.FilterProfileCatalog
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.LayoutInspectorFilterDialogBinding
import java.util.EnumMap
import kotlin.math.roundToInt

/**
 * The filter profile dialog of the layout inspector (roadmap P3.2 / P3.3, D6 / D7): a profile
 * picker with rename / delete, then one control per dimension of [FilterProfile]. Every change is
 * applied to the session at once and marks the profile modified; "Save" writes a user profile back
 * (on the default profile it becomes "Save as", D6), "Save as" asks for a name, "Reset" drops the
 * edits. Closing the dialog (back, outside) with unsaved edits asks to save, save as or discard.
 * The region row shows the session's region and hands region dragging to the window (roadmap P3.4).
 *
 * All secondary prompts are dialogs, never popup windows (see [InspectorSearchBar]).
 *
 * zh-CN: 布局分析的筛选配置对话框 (Roadmap P3.2 / P3.3, D6 / D7): 带改名 / 删除的配置选择器, 其后为 [FilterProfile] 每个维度一个控件.
 * 每次修改即时应用到会话并标记已修改; "保存" 写回用户配置 (对默认配置则变为 "另存为", D6), "另存为" 询问名称, "重置" 丢弃修改.
 * 带着未保存修改关闭对话框 (返回键, 点击外部) 时询问保存 / 另存为 / 放弃. 区域行显示会话的区域条件并把拖拽交给窗口 (Roadmap P3.4).
 * 全部二级提示均为对话框, 不用弹出窗口 (见 [InspectorSearchBar]).
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class FilterProfileDialog(
    private val context: Context,
    private val session: InspectorSearchSession,
    private val host: InspectorSearchBar.Host,
) {

    private val binding = LayoutInspectorFilterDialogBinding.inflate(LayoutInflater.from(context))

    private var dialog: MaterialDialog? = null

    /** Set while the controls are rebuilt from the profile, so their listeners stay quiet. zh-CN: 由配置重建控件期间置位, 使监听器保持沉默. */
    private var rebuilding = false

    private val themeTint = ColorStateList.valueOf(ThemeColorManager.colorPrimary)

    private val density = context.resources.displayMetrics.density

    private val booleanGroups = EnumMap<BooleanProperty, RadioGroup>(BooleanProperty::class.java)

    private val presetChecks = EnumMap<ClassPreset, CheckBox>(ClassPreset::class.java)

    private val packageChecks = LinkedHashMap<String, CheckBox>()

    private val actionChecks = LinkedHashMap<String, CheckBox>()

    private lateinit var depthRange: Pair<EditText, EditText>

    private lateinit var childCountRange: Pair<EditText, EditText>

    private lateinit var indexInParentRange: Pair<EditText, EditText>

    private val changeWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable) = onChanged()
    }

    fun show() {
        buildBooleanRows()
        buildPresetChecks()
        buildRangeRows()
        bindStaticControls()
        render()
        val dialog = AppLevelThemeDialogBuilder(context)
            .title(R.string.text_inspector_filter_profiles)
            .customView(binding.root, true)
            .autoDismiss(false)
            .positiveText(R.string.text_done)
            .neutralText(R.string.text_inspector_profile_save_as)
            .negativeText(R.string.text_reset)
            .onPositive { _, _ -> onSaveOrDone() }
            .onNeutral { _, _ -> promptSaveAs(null) }
            .onNegative { _, _ -> onReset() }
            .cancelListener { onCancelled() }
            .build()
        dialog.window!!.setType(FloatyWindowManger.getWindowType())
        this.dialog = dialog
        dialog.show()
        updateButtons()
    }

    /* Building. zh-CN: 构建. */

    private fun buildBooleanRows() {
        val container = binding.booleans
        container.removeAllViews()
        for (property in BooleanProperty.entries) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(36)
            }
            val label = TextView(context).apply {
                text = property.label
                textSize = 13f
                setTextColor(context.getColor(R.color.day_night))
                maxLines = 1
            }
            val group = RadioGroup(context).apply {
                orientation = RadioGroup.HORIZONTAL
                for (tri in Tri.entries) {
                    addView(RadioButton(context).apply {
                        id = View.generateViewId()
                        tag = tri
                        text = triLabel(tri)
                        textSize = 12f
                        setTextColor(context.getColor(R.color.day_night))
                        buttonTintList = themeTint
                        minHeight = dp(32)
                        minimumHeight = dp(32)
                    }, RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                setOnCheckedChangeListener { _, _ -> onChanged() }
            }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            container.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            booleanGroups[property] = group
        }
    }

    private fun buildPresetChecks() {
        binding.presets.removeAllViews()
        for (preset in ClassPreset.entries) {
            presetChecks[preset] = addCheck(binding.presets, presetLabel(preset))
        }
    }

    private fun buildRangeRows() {
        binding.ranges.removeAllViews()
        depthRange = addRangeRow("depth")
        childCountRange = addRangeRow("childCount")
        indexInParentRange = addRangeRow("indexInParent")
    }

    private fun addRangeRow(label: String): Pair<EditText, EditText> {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(context.getColor(R.color.day_night))
        }
        val min = numberInput(context.getString(R.string.text_inspector_range_min))
        val max = numberInput(context.getString(R.string.text_inspector_range_max))
        row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(min, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(max, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) })
        binding.ranges.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return min to max
    }

    private fun numberInput(hint: String): EditText = EditText(context).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER
        maxLines = 1
        isSingleLine = true
        textSize = 13f
        setTextColor(context.getColor(R.color.day_night))
        setHintTextColor(context.getColor(R.color.day_night_alpha_40))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        addTextChangedListener(changeWatcher)
    }

    private fun addCheck(parent: ViewGroup, label: CharSequence): CheckBox {
        val check = CheckBox(context).apply {
            text = label
            textSize = 13f
            setTextColor(context.getColor(R.color.day_night))
            buttonTintList = themeTint
            minHeight = dp(36)
            minimumHeight = dp(36)
            setOnCheckedChangeListener { _, _ -> onChanged() }
        }
        parent.addView(check, ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        return check
    }

    private fun bindStaticControls() {
        binding.profilePicker.setOnClickListener { pickProfile() }
        binding.profileRename.setOnClickListener { promptRename() }
        binding.profileDelete.setOnClickListener { confirmDelete() }
        for (check in listOf(binding.requireId, binding.requireText, binding.requireDesc, binding.onScreenOnly)) {
            check.buttonTintList = themeTint
            check.setOnCheckedChangeListener { _, _ -> onChanged() }
        }
        binding.minWidth.addTextChangedListener(changeWatcher)
        binding.minHeight.addTextChangedListener(changeWatcher)
        binding.classNamesToggle.setTextColor(ThemeColorManager.colorPrimary)
        binding.classNamesToggle.setOnClickListener {
            binding.classNames.isVisible = !binding.classNames.isVisible
        }
        binding.selectRegion.setTextColor(ThemeColorManager.colorPrimary)
        binding.clearRegion.setTextColor(ThemeColorManager.colorPrimary)
        binding.selectRegion.setOnClickListener {
            if (host.onSelectRegionRequested()) {
                dialog?.dismiss()
            } else {
                ViewUtils.showToast(context, R.string.text_inspector_region_bounds_window_only, true)
            }
        }
        binding.clearRegion.setOnClickListener {
            session.setRegion(null)
            renderRegion()
        }
        binding.profileModified.setTextColor(ThemeColorManager.colorPrimary)
        val candidates = session.candidates
        binding.classNamesToggle.text = context.getString(R.string.text_inspector_class_names_in_tree, candidates.classNames.size)
        binding.classNames.text = candidates.classNames.joinToString("\n") { (name, count) -> "$name x $count" }
    }

    /* Rendering the profile into the controls. zh-CN: 把配置渲染到控件. */

    private fun render() {
        rebuilding = true
        try {
            val profile = session.profile
            renderHeader(profile)
            for ((property, group) in booleanGroups) {
                val tri = profile.booleans[property] ?: Tri.ANY
                for (i in 0 until group.childCount) {
                    val button = group.getChildAt(i) as RadioButton
                    if (button.tag == tri) group.check(button.id)
                }
            }
            binding.requireId.isChecked = profile.requireId
            binding.requireText.isChecked = profile.requireText
            binding.requireDesc.isChecked = profile.requireDesc
            for ((preset, check) in presetChecks) check.isChecked = preset in profile.classPresets
            renderRange(depthRange, profile.depth)
            renderRange(childCountRange, profile.childCount)
            renderRange(indexInParentRange, profile.indexInParent)
            setNumber(binding.minWidth, profile.minWidth)
            setNumber(binding.minHeight, profile.minHeight)
            binding.onScreenOnly.isChecked = profile.onScreenOnly
            renderRegion()
            rebuildChecks(binding.packages, packageChecks, session.candidates.packages, profile.packages)
            rebuildChecks(binding.actions, actionChecks, session.candidates.actions, profile.actions)
        } finally {
            rebuilding = false
        }
        updateButtons()
    }

    private fun renderHeader(profile: FilterProfile) {
        binding.profileName.text = profileName(profile)
        binding.profileModified.visibility = if (session.isProfileDirty) View.VISIBLE else View.GONE
        binding.profileDefaultHint.visibility = if (profile.isDefault) View.VISIBLE else View.GONE
        for (button in listOf(binding.profileRename, binding.profileDelete)) {
            button.isEnabled = !profile.isDefault
            button.alpha = if (profile.isDefault) DISABLED_ALPHA else 1f
        }
    }

    private fun renderRange(inputs: Pair<EditText, EditText>, range: IntRangeSpec) {
        setNumber(inputs.first, range.min)
        setNumber(inputs.second, range.max)
    }

    private fun renderRegion() {
        val region = session.region
        val value = region?.toShortString() ?: context.getString(R.string.text_none)
        binding.regionValue.text = context.getString(R.string.text_inspector_region_label, value)
        binding.clearRegion.isEnabled = region != null
        binding.clearRegion.alpha = if (region != null) 1f else DISABLED_ALPHA
    }

    /** Candidates from the tree first, then the profile's own values that the tree lacks (kept, marked). zh-CN: 先是树中的候选, 再是树里没有而配置中有的值 (保留并标记). */
    private fun rebuildChecks(parent: ViewGroup, checks: LinkedHashMap<String, CheckBox>, candidates: List<String>, selected: Set<String>) {
        parent.removeAllViews()
        checks.clear()
        val names = LinkedHashSet(candidates) + selected.filterNot { it in candidates }.sorted()
        for (name in names) {
            val label = if (name in candidates) name else "$name *"
            val check = addCheck(parent, label)
            check.isChecked = name in selected
            checks[name] = check
        }
    }

    private fun setNumber(input: EditText, value: Int?) {
        val text = value?.toString().orEmpty()
        if (input.text.toString() != text) input.setText(text)
    }

    /* Collecting the controls into a profile. zh-CN: 把控件收集为配置. */

    private fun collect(): FilterProfile {
        val booleans = EnumMap<BooleanProperty, Tri>(BooleanProperty::class.java)
        for ((property, group) in booleanGroups) {
            val tri = group.findViewById<RadioButton>(group.checkedRadioButtonId)?.tag as? Tri ?: Tri.ANY
            if (tri != Tri.ANY) booleans[property] = tri
        }
        return session.profile.copy(
            booleans = booleans,
            requireId = binding.requireId.isChecked,
            requireText = binding.requireText.isChecked,
            requireDesc = binding.requireDesc.isChecked,
            classPresets = presetChecks.filterValues { it.isChecked }.keys,
            depth = collectRange(depthRange),
            childCount = collectRange(childCountRange),
            indexInParent = collectRange(indexInParentRange),
            minWidth = number(binding.minWidth)?.takeIf { it > 0 },
            minHeight = number(binding.minHeight)?.takeIf { it > 0 },
            onScreenOnly = binding.onScreenOnly.isChecked,
            packages = packageChecks.filterValues { it.isChecked }.keys,
            actions = actionChecks.filterValues { it.isChecked }.keys,
        )
    }

    private fun collectRange(inputs: Pair<EditText, EditText>) = IntRangeSpec(number(inputs.first), number(inputs.second))

    private fun number(input: EditText): Int? = input.text.toString().trim().toIntOrNull()

    private fun onChanged() {
        if (rebuilding) return
        session.setProfile(collect())
        renderHeader(session.profile)
        updateButtons()
    }

    private fun updateButtons() {
        val dialog = dialog ?: return
        val dirty = session.isProfileDirty
        dialog.setActionButton(DialogAction.POSITIVE, if (dirty) R.string.dialog_button_save else R.string.text_done)
        dialog.getActionButton(DialogAction.NEGATIVE).let { button ->
            button.isEnabled = dirty
            button.alpha = if (dirty) 1f else DISABLED_ALPHA
        }
    }

    /* Actions. zh-CN: 操作. */

    private fun onSaveOrDone() {
        if (!session.isProfileDirty) {
            dialog?.dismiss()
            return
        }
        if (session.profile.isDefault) {
            promptSaveAs(null)
            return
        }
        if (session.saveProfile()) {
            ViewUtils.showToast(context, R.string.text_inspector_profile_saved)
            render()
        }
    }

    private fun onReset() {
        session.discardProfileEdits()
        render()
    }

    private fun onCancelled() {
        if (session.isProfileDirty) promptUnsaved(null)
    }

    /**
     * The rows are built from the window's themed context instead of the library's list items:
     * MaterialDialog inflates its list rows with the builder context (the application context here),
     * whose default theme cannot resolve the row background attribute and crashes the inflater.
     * zh-CN: 行由窗口的主题 context 构建而非使用对话框库的列表项: MaterialDialog 用构建器 context (此处为 application context)
     * 加载列表行, 其默认主题无法解析行背景属性, 会让加载器崩溃.
     */
    private fun pickProfile() {
        val profiles = session.catalog.profiles
        val selectedId = session.catalog.selectedId
        val background = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }.resourceId
        var picker: MaterialDialog? = null
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        profiles.forEach { profile ->
            val selected = profile.id == selectedId
            list.addView(TextView(context).apply {
                text = profileName(profile)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(48)
                minimumHeight = dp(48)
                setPadding(dp(24), 0, dp(24), 0)
                setTextColor(if (selected) ThemeColorManager.colorPrimary else context.getColor(R.color.day_night))
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setBackgroundResource(background)
                setOnClickListener {
                    picker?.dismiss()
                    if (!selected) withEditsSettled { switchTo(profile.id) }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        picker = AppLevelThemeDialogBuilder(context)
            .title(R.string.text_inspector_choose_profile)
            .customView(list, true)
            .negativeText(R.string.dialog_button_cancel)
            .build()
            .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }
        picker.show()
    }

    private fun switchTo(id: String) {
        session.selectProfile(id)
        render()
    }

    /** Runs [then] right away without edits, otherwise after the save / save as / discard prompt. zh-CN: 无修改时直接执行 [then], 否则在保存 / 另存为 / 放弃提示之后执行. */
    private fun withEditsSettled(then: () -> Unit) {
        if (session.isProfileDirty) promptUnsaved(then) else then()
    }

    private fun promptUnsaved(then: (() -> Unit)?) {
        AppLevelThemeDialogBuilder(context)
            .title(R.string.text_inspector_profile_unsaved_title)
            .content(R.string.text_inspector_profile_unsaved_content)
            .cancelable(false)
            .positiveText(R.string.dialog_button_save)
            .neutralText(R.string.text_inspector_profile_save_as)
            .negativeText(R.string.text_inspector_profile_discard)
            .onPositive { _, _ ->
                if (session.profile.isDefault) {
                    promptSaveAs(then)
                } else {
                    if (session.saveProfile()) ViewUtils.showToast(context, R.string.text_inspector_profile_saved)
                    render()
                    then?.invoke()
                }
            }
            .onNeutral { _, _ -> promptSaveAs(then) }
            .onNegative { _, _ ->
                session.discardProfileEdits()
                render()
                then?.invoke()
            }
            .build()
            .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }
            .show()
    }

    /** Asks for a new profile name, validated live, then saves the current conditions under it (D6). zh-CN: 询问新配置名称 (实时校验), 然后把当前条件存为该配置 (D6). */
    private fun promptSaveAs(then: (() -> Unit)?) {
        promptName(R.string.text_inspector_profile_save_as, prefill = null, exceptId = null) { name ->
            val created = session.saveProfileAs(name)
            ViewUtils.showToast(context, context.getString(R.string.text_inspector_profile_created, created.name), true)
            render()
            then?.invoke()
        }
    }

    private fun promptRename() {
        val profile = session.profile
        if (profile.isDefault) return
        promptName(R.string.text_rename, prefill = profile.name, exceptId = profile.id) { name ->
            if (session.renameProfile(profile.id, name)) render()
        }
    }

    private fun promptName(titleRes: Int, prefill: String?, exceptId: String?, onConfirmed: (String) -> Unit) {
        AppLevelThemeDialogBuilder(context)
            .title(titleRes)
            .input(context.getString(R.string.text_inspector_profile_name_hint), prefill, false) { d, input ->
                val error = session.catalog.validateName(input.toString(), exceptId)
                d.getActionButton(DialogAction.POSITIVE).isEnabled = error == null
                d.inputEditText?.error = when (error) {
                    FilterProfileCatalog.NameError.DUPLICATE -> context.getString(R.string.text_inspector_profile_name_exists)
                    FilterProfileCatalog.NameError.BLANK, null -> null
                }
            }
            .alwaysCallInputCallback()
            .widgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .onNegative { d, _ -> d.dismiss() }
            .onPositive { d, _ ->
                val name = d.inputEditText?.text?.toString()?.trim().orEmpty()
                if (session.catalog.validateName(name, exceptId) != null) return@onPositive
                d.dismiss()
                onConfirmed(name)
            }
            .autoDismiss(false)
            .build()
            .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }
            .show()
    }

    private fun confirmDelete() {
        val profile = session.profile
        if (profile.isDefault) return
        AppLevelThemeDialogBuilder(context)
            .content(context.getString(R.string.text_inspector_profile_delete_confirm, profile.name))
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.text_delete)
            .onPositive { _, _ ->
                if (session.deleteProfile(profile.id)) {
                    ViewUtils.showToast(context, R.string.text_inspector_profile_deleted)
                    render()
                }
            }
            .build()
            .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }
            .show()
    }

    /* Labels. zh-CN: 标签. */

    private fun profileName(profile: FilterProfile): String = if (profile.isDefault) context.getString(R.string.text_default) else profile.name

    private fun triLabel(tri: Tri): String = context.getString(
        when (tri) {
            Tri.ANY -> R.string.text_inspector_tri_any
            Tri.YES -> R.string.text_inspector_tri_yes
            Tri.NO -> R.string.text_inspector_tri_no
        }
    )

    private fun presetLabel(preset: ClassPreset): String = context.getString(
        when (preset) {
            ClassPreset.BUTTON -> R.string.text_inspector_preset_button
            ClassPreset.TEXT -> R.string.text_inspector_preset_text
            ClassPreset.INPUT -> R.string.text_inspector_preset_input
            ClassPreset.IMAGE -> R.string.text_inspector_preset_image
            ClassPreset.TOGGLE -> R.string.text_inspector_preset_toggle
            ClassPreset.SCROLL -> R.string.text_inspector_preset_scroll
            ClassPreset.CONTAINER -> R.string.text_inspector_preset_container
        }
    )

    private fun dp(value: Int): Int = (value * density).roundToInt()

    companion object {

        private const val DISABLED_ALPHA = 0.38f

    }

}
