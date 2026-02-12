package org.autojs.autojs.ui.edit.keyboard

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivitySymbolsSettingsBinding
import org.autojs.autojs6.databinding.FragmentSymbolsToolbarBinding
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Modified by SuperMonster003 as of Feb 11, 2026.
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 11, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 12, 2026.
 * Modified by SuperMonster003 as of Feb 12, 2026.
 */
@SuppressLint("NotifyDataSetChanged")
class SymbolsSettingsActivity : BaseActivity() {

    private lateinit var menu: Menu

    private lateinit var activityBinding: ActivitySymbolsSettingsBinding
    private lateinit var toolbarBinding: FragmentSymbolsToolbarBinding

    private lateinit var profileSpinner: Spinner
    private lateinit var recycler: RecyclerView

    private lateinit var ivNewProfile: ImageView
    private lateinit var ivDeleteProfile: ImageView
    private lateinit var ivMoreProfile: ImageView

    private lateinit var emptyHintContainer: View

    private var currentProfileName: String = ""

    private val items = mutableListOf<SymbolsConfigStore.SymbolItem>()
    private lateinit var adapter: SymbolsAdapter

    private var itemTouchHelper: ItemTouchHelper? = null

    // Baseline items for current profile (i.e. last saved state / reset target).
    // zh-CN: 当前配置的基线 items (即最近一次保存状态/重置目标).
    private var baselineItems: List<SymbolsConfigStore.SymbolItem> = emptyList()

    // Undo/redo snapshot stacks (each snapshot is a full list).
    // zh-CN: 撤销/重做快照栈 (每个快照是一份完整列表).
    private val undoStack = ArrayDeque<List<SymbolsConfigStore.SymbolItem>>()
    private val redoStack = ArrayDeque<List<SymbolsConfigStore.SymbolItem>>()

    // Save button sticky flag.
    // zh-CN: 保存按钮粘性标记.
    private var saveSticky: Boolean = false

    // RecyclerView user scrolling flag.
    // zh-CN: RecyclerView 用户滚动标记.
    @Volatile
    private var recyclerUserScrolling: Boolean = false

    // Current editing session (single editor at a time).
    // zh-CN: 当前编辑会话 (同一时刻仅允许一个编辑器).
    private var editing: EditingSession? = null

    // Guard flag to avoid handling spinner callback when we programmatically change selection.
    // zh-CN: 防护标记, 避免代码设置 spinner 选中项时触发回调逻辑.
    private var suppressSpinnerCallback: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityBinding = ActivitySymbolsSettingsBinding.inflate(layoutInflater).also {
            setContentView(it.root)
            this.activityBinding = it
        }

        setToolbarAsBack(R.string.text_symbols_settings)

        toolbarBinding = FragmentSymbolsToolbarBinding.inflate(layoutInflater, activityBinding.toolbarMenu, true).also {
            it.actionUndo.apply {
                setOnClickListener {
                    commitEditingIfNeeded(reason = CommitReason.ToolbarAction)
                    performUndo()
                }
            }
            it.actionRedo.apply {
                setOnClickListener {
                    commitEditingIfNeeded(reason = CommitReason.ToolbarAction)
                    performRedo()
                }
            }
            it.actionSave.apply {
                setOnClickListener {
                    commitEditingIfNeeded(reason = CommitReason.ToolbarAction)
                    saveWithDefaultProtectionOrCreateNew()
                }
                setOnLongClickListener {
                    promptCreateProfileByName(
                        titleRes = R.string.text_save_configuration_as,
                        contentRes = R.string.text_set_a_name_for_the_new_configuration,
                        baseItems = items.toList(),
                        afterCreated = null,
                    )
                    true
                }
            }
        }

        // Initialize action buttons to disabled state.
        // zh-CN: 初始化三个动作按钮为不可用状态.
        updateActionButtons()

        SymbolsConfigStore.ensureDefaultProfileExists(this)

        profileSpinner = activityBinding.profileSpinner

        recycler = activityBinding.recycler.apply {
            excludePaddingClippableViewFromBottomNavigationBar()
        }

        ivNewProfile = activityBinding.ivNewProfile
        ivDeleteProfile = activityBinding.ivDeleteProfile
        ivMoreProfile = activityBinding.ivMoreProfile

        emptyHintContainer = activityBinding.symbolsEmptyHintContainer.apply {
            setOnClickListener {
                commitEditingIfNeeded(reason = CommitReason.ItemAction)

                val newText = findFirstUniqueUnicodeSymbol()

                recordChange(markSaveSticky = true) {
                    items.add(0, SymbolsConfigStore.SymbolItem(newText, true))
                }

                adapter.notifyItemInserted(0)
                recycler.smoothScrollToPosition(0)

                updateToggleAllButton()
                updateActionButtons()

                adapter.requestEditAt(0, isNewItem = true)
            }
        }

        createAdapter()

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                recyclerUserScrolling = newState != RecyclerView.SCROLL_STATE_IDLE

                // If scrolling ends and the editor already lost focus during scroll,
                // commit it now.
                //
                // zh-CN:
                // 若滚动结束且编辑器在滚动期间已丢失焦点,
                // 则此时再提交一次.
                if (!recyclerUserScrolling) {
                    commitEditingIfNeeded(reason = CommitReason.ScrollIdle)
                }
            }
        })

        attachDragToReorder()

        ivNewProfile.setOnClickListener {
            commitEditingIfNeeded(reason = CommitReason.ToolbarAction)
            runWithUnsavedChangesGuard(onProceed = { promptCreateProfileWithBasePicker() })
        }

        ivDeleteProfile.setOnClickListener {
            commitEditingIfNeeded(reason = CommitReason.ToolbarAction)
            runWithUnsavedChangesGuard(onProceed = { promptDeleteProfile() })
        }

        ivMoreProfile.setOnClickListener {
            commitEditingIfNeeded(reason = CommitReason.ToolbarAction)
            showMorePopup(anchor = it)
        }

        setupProfilesSpinner()

        // Apply empty hint status after initial profile load.
        // zh-CN: 初次加载配置后应用空提示状态.
        updateEmptyHint()
    }

    private fun updateEmptyHint() {
        emptyHintContainer.isVisible = items.isEmpty()
    }

    override fun finish() {
        commitEditingIfNeeded(reason = CommitReason.LeavingScreen)
        runWithUnsavedChangesGuard(onProceed = { finishAndRemoveTask() }, isExit = true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menuInflater.inflate(R.menu.menu_symbols_options, menu)
        updateToggleAllButton()

        activityBinding.toolbar.setMenuIconsColorByThemeColorLuminance(this)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        commitEditingIfNeeded(reason = CommitReason.ToolbarAction)

        when (item.itemId) {
            R.id.action_toggle_all -> {
                if (items.isEmpty()) return true

                recordChange(markSaveSticky = true) {
                    val allEnabled = items.all { it.enabled }
                    val target = !allEnabled
                    for (i in items.indices) {
                        items[i] = items[i].copy(enabled = target)
                    }
                }

                adapter.notifyDataSetChanged()
                updateToggleAllButton()
                return true
            }
            R.id.action_import -> {
                runWithUnsavedChangesGuard(onProceed = { startImportJson() })
                return true
            }
            R.id.action_export -> {
                runWithUnsavedChangesGuard(onProceed = { startExportJson() })
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createAdapter() {
        adapter = SymbolsAdapter(
            data = items,
            onToggle = { pos, enabled ->
                commitEditingIfNeeded(reason = CommitReason.ItemAction)

                if (pos < 0 || pos >= items.size) return@SymbolsAdapter
                recordChange(markSaveSticky = true) {
                    items[pos] = items[pos].copy(enabled = enabled)
                }
                updateToggleAllButton()
                adapter.notifyItemChanged(pos)
                updateActionButtons()
            },
            onAdd = { pos ->
                commitEditingIfNeeded(reason = CommitReason.ItemAction)

                if (pos < 0 || pos >= items.size) return@SymbolsAdapter

                val newText = findFirstUniqueUnicodeSymbol()

                recordChange(markSaveSticky = true) {
                    items.add(pos + 1, SymbolsConfigStore.SymbolItem(newText, true))
                }

                adapter.notifyItemInserted(pos + 1)
                recycler.smoothScrollToPosition(pos + 1)

                updateToggleAllButton()
                updateActionButtons()

                // Auto enter edit mode for the newly inserted item.
                // zh-CN: 新增条目后自动进入编辑态.
                adapter.requestEditAt(pos + 1, isNewItem = true)
            },
            onDelete = { pos ->
                commitEditingIfNeeded(reason = CommitReason.ItemAction)

                if (pos < 0 || pos >= items.size) return@SymbolsAdapter

                recordChange(markSaveSticky = true) {
                    items.removeAt(pos)
                }

                adapter.notifyItemRemoved(pos)
                updateToggleAllButton()
                updateActionButtons()
            },
            onBeginEdit = { session ->
                // Ensure single editor.
                // zh-CN: 确保同一时刻仅存在一个编辑器.
                if (editing != null && editing?.position != session.position) {
                    commitEditingIfNeeded(reason = CommitReason.SwitchEditor)
                }

                editing = session

                // Auto show keyboard and select all.
                // zh-CN: 自动全选并弹出软键盘.
                session.editText.post {
                    session.editText.requestFocus()
                    session.editText.selectAll()
                    showKeyboard(session.editText)
                }
            },

            onEditFocusChanged = { session, hasFocus ->
                if (hasFocus) return@SymbolsAdapter

                // Focus loss during scrolling should not be treated as leaving edit area.
                // We will commit when scroll becomes idle.
                //
                // zh-CN:
                // 滚动期间的焦点丢失不视为离开编辑区域,
                // 等滚动停止后再提交.
                if (recyclerUserScrolling) return@SymbolsAdapter

                commitEditingIfNeeded(reason = CommitReason.FocusLost)
            },
            onRequestDrag = { vh ->
                commitEditingIfNeeded(reason = CommitReason.ItemAction)
                itemTouchHelper?.startDrag(vh)
            }
        )
    }

    private fun canEditStructureNow(): Boolean =
        currentProfileName.isNotBlank()

    private fun applyUiForProfile() {
        adapter.notifyDataSetChanged()
        updateToggleAllButton()

        // Reset action buttons on profile switch.
        // zh-CN: 切换配置后重置动作按钮状态.
        updateActionButtons()
    }

    private fun updateToggleAllButton() {
        // Rule:
        // - Only when ALL checked -> show "取消全选" and action is deselect all.
        // - Otherwise -> show "全选" and action is select all.
        //
        // zh-CN: 规则:
        // - 仅当全部勾选时显示 "取消全选", 功能为全部取消.
        // - 其它情况一律显示 "全选", 功能为全部勾选.
        if (::menu.isInitialized) {
            val allEnabled = items.isNotEmpty() && items.all { it.enabled }
            menu.findItem(R.id.action_toggle_all)?.let { menuItem ->
                menuItem.title = getString(if (allEnabled) R.string.text_deselect_all else R.string.text_select_all)
            }
        }
    }

    private fun switchToProfile(name: String) {
        currentProfileName = name
        SymbolsConfigStore.setActiveProfileName(this, name)

        val loaded = SymbolsConfigStore.loadProfile(this, name)

        items.clear()
        items.addAll(loaded)

        baselineItems = loaded.toList()

        undoStack.clear()
        redoStack.clear()

        // Reset sticky save status on profile switch.
        // zh-CN: 切换配置时重置保存粘性状态.
        saveSticky = false

        editing = null

        adapter.notifyDataSetChanged()

        applyUiForProfile()
        updateToggleAllButton()
        updateActionButtons()
        updateEmptyHint()
    }

    private fun rebuildProfilesSpinnerAndSelect(internalName: String) {
        val internalProfiles = SymbolsConfigStore.listProfiles(this).ifEmpty {
            listOf(SymbolsConfigStore.PROFILE_DEFAULT_ID)
        }

        val entries = internalProfiles.map { internal ->
            val display = if (internal == SymbolsConfigStore.PROFILE_DEFAULT_ID) {
                SymbolsConfigStore.getDefaultProfileDisplayName(this)
            } else internal
            ProfileEntry(internalName = internal, displayName = display)
        }

        val spinAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries.map { it.displayName }).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        suppressSpinnerCallback = true
        profileSpinner.adapter = spinAdapter

        val idx = entries.indexOfFirst { it.internalName == internalName }.coerceAtLeast(0)
        profileSpinner.setSelection(idx, false)
        suppressSpinnerCallback = false

        // Ensure the page actually switches to the selected profile.
        // zh-CN: 确保页面确实切换到目标配置.
        if (currentProfileName != internalName) {
            switchToProfile(internalName)
        }
    }

    private fun setupProfilesSpinner() {
        val internalProfiles = SymbolsConfigStore.listProfiles(this).ifEmpty {
            listOf(SymbolsConfigStore.PROFILE_DEFAULT_ID)
        }

        val entries = internalProfiles.map { internal ->
            val display = if (internal == SymbolsConfigStore.PROFILE_DEFAULT_ID) {
                SymbolsConfigStore.getDefaultProfileDisplayName(this)
            } else internal
            ProfileEntry(internalName = internal, displayName = display)
        }

        val activeInternal = SymbolsConfigStore.getActiveProfileName(this)

        val spinAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries.map { it.displayName }).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        profileSpinner.adapter = spinAdapter

        val idx = entries.indexOfFirst { it.internalName == activeInternal }.coerceAtLeast(0)
        profileSpinner.setSelection(idx, false)

        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallback) return

                val chosen = entries.getOrNull(position) ?: return
                if (chosen.internalName == currentProfileName) return

                commitEditingIfNeeded(reason = CommitReason.SwitchProfile)

                runWithUnsavedChangesGuard(
                    onProceed = {
                        switchToProfile(chosen.internalName)
                    },
                    onCancel = {
                        val oldIdx = entries.indexOfFirst { it.internalName == currentProfileName }.coerceAtLeast(0)
                        suppressSpinnerCallback = true
                        profileSpinner.setSelection(oldIdx, false)
                        suppressSpinnerCallback = false
                    }
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        switchToProfile(entries[idx].internalName)
    }

    private fun isDirty(): Boolean = items != baselineItems

    private fun pushUndoSnapshot() {
        undoStack.addLast(items.toList())
        redoStack.clear()
    }

    private fun applySnapshot(snapshot: List<SymbolsConfigStore.SymbolItem>) {
        items.clear()
        items.addAll(snapshot)
        adapter.notifyDataSetChanged()
        updateToggleAllButton()
        updateActionButtons()
        updateEmptyHint()
    }

    private fun recordChange(markSaveSticky: Boolean, change: () -> Unit) {
        pushUndoSnapshot()
        change()

        if (markSaveSticky) {
            // Once turned on, do not auto turn off except by explicit save or full undo-to-baseline.
            // zh-CN: 一旦亮起, 除非显式保存或完全撤销回基线, 否则不自动熄灭.
            saveSticky = true
        }

        updateActionButtons()
        updateEmptyHint()
    }

    private fun performUndo() {
        if (undoStack.isEmpty()) return

        // Undo will end any editing session.
        // zh-CN: 撤销会结束当前编辑会话.
        editing = null

        redoStack.addLast(items.toList())
        val prev = undoStack.removeLast()

        applySnapshot(prev)

        // Auto turn off sticky save only when fully undone and state equals baseline.
        // zh-CN: 仅当撤销到尽头且状态等于基线时, 才允许自动熄灭保存按钮.
        if (undoStack.isEmpty() && items == baselineItems) {
            saveSticky = false
        }

        updateActionButtons()
        updateEmptyHint()
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) return

        editing = null

        undoStack.addLast(items.toList())
        val next = redoStack.removeLast()

        applySnapshot(next)

        // Redo implies user is making changes again.
        // zh-CN: 重做意味着用户再次推进修改.
        saveSticky = true

        updateActionButtons()
        updateEmptyHint()
    }

    private fun updateActionButtons() {
        val undoEnabled = undoStack.isNotEmpty()
        val redoEnabled = redoStack.isNotEmpty()

        // Save button strategy:
        // - Sticky: once enabled it stays enabled.
        // - Can auto turn off only when undoStack is empty AND items == baselineItems.
        // - Explicit save will turn it off.
        //
        // zh-CN:
        // - 粘性: 一旦亮起保持亮起.
        // - 仅在 undo 为空且 items == baseline 时可自动熄灭.
        // - 用户手动点击保存后熄灭.
        val saveEnabled = saveSticky || isDirty()

        toolbarBinding.actionUndo.isEnabled = undoEnabled
        toolbarBinding.actionRedo.isEnabled = redoEnabled
        toolbarBinding.actionSave.isEnabled = saveEnabled
    }

    private fun saveWithDefaultProtectionOrCreateNew(afterSaved: (() -> Unit)? = null) {
        if (currentProfileName.isBlank()) return

        // Explicit save: turn off sticky.
        // zh-CN: 显式保存: 关闭粘性.
        saveSticky = false

        if (!isDirty()) {
            updateActionButtons()
            afterSaved?.invoke()
            return
        }

        if (currentProfileName == SymbolsConfigStore.PROFILE_DEFAULT_ID) {
            promptCreateProfileByName(
                contentRes = R.string.text_default_profile_overwrite_hint,
                baseItems = items.toList(),
                afterCreated = afterSaved,
            )
            return
        }

        SymbolsConfigStore.saveProfile(this, currentProfileName, items.toList())
        baselineItems = items.toList()

        // IMPORTANT: Do NOT clear undo/redo after save.
        // zh-CN: 重要: 保存后不要清空撤销/重做栈.
        ViewUtils.showToast(this, R.string.text_done)
        updateActionButtons()

        afterSaved?.invoke()
    }

    private fun saveWithDefaultProtectionOrCreateNew() {
        saveWithDefaultProtectionOrCreateNew(afterSaved = null)
    }

    private fun runWithUnsavedChangesGuard(
        onProceed: () -> Unit,
        onCancel: (() -> Unit)? = null,
        isExit: Boolean = false,
    ) {
        if (!(saveSticky || isDirty())) {
            onProceed()
            return
        }

        val contentRes = when {
            isExit -> R.string.warn_exit_without_saving_settings
            else -> R.string.warn_continue_operation_without_saving_settings
        }

        val negativeTextRes = when {
            isExit -> R.string.text_exit_directly
            else -> R.string.dialog_button_discard_changes
        }

        val positiveTextRes = when {
            isExit -> R.string.text_save_and_exit
            else -> R.string.dialog_button_save_and_continue
        }

        // Prompt to save or discard. "Discard" still proceeds.
        // zh-CN: 提示保存或放弃. 选择 "放弃" 仍继续执行后续操作.
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(contentRes)
                .neutralText(R.string.dialog_button_back)
                .neutralColorRes(R.color.dialog_button_default)
                .onNeutral { _, _ ->
                    onCancel?.invoke()
                }
                .negativeText(negativeTextRes)
                .negativeColorRes(R.color.dialog_button_caution)
                .onNegative { _, _ ->
                    onProceed()
                }
                .positiveText(positiveTextRes)
                .positiveColorRes(R.color.dialog_button_warn)
                .onPositive { _, _ ->
                    saveWithDefaultProtectionOrCreateNew(afterSaved = { onProceed() })
                }
                .build()
        }
    }

    private fun showMorePopup(anchor: View) {
        val popupMenu = PopupMenu(anchor.context, anchor, Gravity.END)
        popupMenu.menuInflater.inflate(R.menu.menu_symbols_config_settings_more, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reset -> {
                    performResetToBaseline()
                    true
                }
                R.id.action_manage -> {
                    runWithUnsavedChangesGuard(onProceed = { promptManageProfiles() })
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun performResetToBaseline() {
        commitEditingIfNeeded(reason = CommitReason.ItemAction)

        if (baselineItems == items) return

        // Reset is treated as a normal change, and should NOT turn off save sticky.
        // zh-CN: 重置视为普通操作, 且不应熄灭保存粘性.
        recordChange(markSaveSticky = true) {
            items.clear()
            items.addAll(baselineItems)
        }

        adapter.notifyDataSetChanged()
        updateToggleAllButton()
        updateActionButtons()
    }

    private fun promptManageProfiles() {
        val profiles = SymbolsConfigStore.listProfiles(this)
            .filter { it != SymbolsConfigStore.PROFILE_DEFAULT_ID }
            .sorted()

        if (profiles.isEmpty()) {
            ViewUtils.showToast(this, R.string.text_no_data, true)
            return
        }

        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_manage)
                .items(profiles)
                .itemsCallback { d, _, which, _ ->
                    d.dismiss()
                    val name = profiles.getOrNull(which) ?: return@itemsCallback
                    promptManageSingleProfile(name)
                }
                .build()
        }
    }

    private fun promptManageSingleProfile(name: String) {
        val actions = listOf(
            getString(R.string.text_open),
            getString(R.string.text_rename),
            getString(R.string.text_delete),
        )

        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(name)
                .items(actions)
                .itemsCallback { d, _, which, _ ->
                    d.dismiss()
                    when (which) {
                        0 -> {
                            // Switch to selected profile immediately (no recreate).
                            // zh-CN: 立即切换到选中配置 (不依赖 recreate).
                            rebuildProfilesSpinnerAndSelect(name)
                        }
                        1 -> promptRenameProfile(name)
                        2 -> {
                            currentProfileName = name
                            promptDeleteProfile()
                        }
                    }
                }
                .build()
        }
    }

    private fun promptRenameProfile(oldName: String) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_rename)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(getString(R.string.text_symbols_profile_name_hint), oldName) { d, input ->
                    val newName = input?.toString()?.trim().orEmpty()
                    if (newName.isBlank()) {
                        ViewUtils.showSnack(d.view, R.string.text_symbols_profile_name_invalid, true)
                        return@input
                    }
                    if (SymbolsConfigStore.isReservedDefaultName(this, newName)) {
                        ViewUtils.showSnack(d.view, R.string.text_symbols_profile_name_cannot_be_default, true)
                        return@input
                    }
                    val existing = SymbolsConfigStore.listProfiles(this).toSet()
                    if (existing.contains(newName)) {
                        ViewUtils.showSnack(d.view, R.string.text_symbols_profile_name_conflict, true)
                        return@input
                    }

                    val oldItems = SymbolsConfigStore.loadProfile(this, oldName)
                    SymbolsConfigStore.saveProfile(this, newName, oldItems)
                    SymbolsConfigStore.deleteProfile(this, oldName)
                    SymbolsConfigStore.setActiveProfileName(this, newName)

                    d.dismiss()
                    recreate()
                }
                .widgetThemeColor()
                .positiveText(R.string.dialog_button_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .autoDismiss(false)
                .build()
        }
    }

    private fun promptCreateProfileWithBasePicker() {
        val libraryProfiles = SymbolsConfigStore.listProfiles(this)
            .filter { it != SymbolsConfigStore.PROFILE_DEFAULT_ID }
            .sorted()

        val baseLabels = ArrayList<String>().apply {
            add(getString(R.string.text_empty_configuration))
            add(getString(R.string.text_default_configuration))
            add(getString(R.string.text_current_page_configuration))
            addAll(libraryProfiles)
        }

        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_symbols_new_profile)
                .content(getString(R.string.text_select_a_configuration_template))
                .items(baseLabels)
                .itemsCallbackSingleChoice(0) { d, _, which, _ ->
                    d.dismiss()

                    // IMPORTANT: if base is "empty", keep it empty.
                    // zh-CN: 重要: 若基于 "空白配置", 则必须保持空白.
                    val baseItems = when (which) {
                        0 -> emptyList()
                        1 -> SymbolsConfigStore.loadProfile(this, SymbolsConfigStore.PROFILE_DEFAULT_ID)
                        2 -> items.toList()
                        else -> {
                            val idx = which - 3
                            val name = libraryProfiles.getOrNull(idx) ?: return@itemsCallbackSingleChoice false
                            SymbolsConfigStore.loadProfile(this, name)
                        }
                    }

                    promptCreateProfileByName(
                        contentRes = R.string.text_set_a_name_for_the_new_configuration,
                        baseItems = baseItems,
                        afterCreated = null,
                    )
                    return@itemsCallbackSingleChoice true
                }
                .choiceWidgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.dialog_button_next_step)
                .positiveColorRes(R.color.dialog_button_attraction)
                .cancelable(false)
                .autoDismiss(false)
                .build()
        }
    }

    private fun promptCreateProfileByName(
        titleRes: Int? = null,
        contentRes: Int? = null,
        baseItems: List<SymbolsConfigStore.SymbolItem>,
        afterCreated: (() -> Unit)?,
    ) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(titleRes ?: R.string.text_symbols_new_profile)
                .apply { contentRes?.let { content(it) } }
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(getString(R.string.text_symbols_profile_name_hint), SymbolsConfigStore.getPrefillProfileName(this)) { d, input ->
                    val name = input?.toString()?.trim().orEmpty()
                    if (name.isBlank()) return@input

                    if (SymbolsConfigStore.isReservedDefaultName(this, name)) {
                        ViewUtils.showSnack(d.view, R.string.text_symbols_profile_name_cannot_be_default, true)
                        return@input
                    }

                    val existing = SymbolsConfigStore.listProfiles(this).toSet()
                    if (existing.contains(name)) {
                        ViewUtils.showSnack(d.view, R.string.text_symbols_profile_name_conflict, true)
                        return@input
                    }

                    // Save exactly what baseItems provides (including empty list).
                    // zh-CN: 严格保存 baseItems 提供的数据 (包含空列表).
                    SymbolsConfigStore.saveProfile(this, name, baseItems)
                    SymbolsConfigStore.setActiveProfileName(this, name)

                    d.dismiss()
                    ViewUtils.showToast(this, R.string.text_done)

                    // Auto switch to the newly created profile immediately.
                    // zh-CN: 新建配置后立即自动切换到该配置.
                    rebuildProfilesSpinnerAndSelect(name)

                    afterCreated?.invoke()
                }
                .widgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .cancelable(false)
                .autoDismiss(false)
                .build()
        }
    }

    private fun promptDeleteProfile() {
        if (currentProfileName == SymbolsConfigStore.PROFILE_DEFAULT_ID) {
            ViewUtils.showToast(this, R.string.text_cannot_delete_default, true)
            return
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(getString(R.string.text_symbols_delete_profile_confirm, currentProfileName))
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .negativeText(R.string.dialog_button_cancel)
                .onPositive { d, _ ->
                    SymbolsConfigStore.deleteProfile(this, currentProfileName)
                    d.dismiss()
                    recreate()
                }
                .build()
        }
    }

    private fun attachDragToReorder() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {

            // Drag gesture snapshot marker.
            // zh-CN: 拖拽手势快照标记.
            private var dragSnapshotPushed: Boolean = false

            override fun isLongPressDragEnabled(): Boolean = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    if (!canEditStructureNow()) return
                    if (dragSnapshotPushed) return

                    // Record one snapshot per drag gesture.
                    // zh-CN: 每次拖拽手势仅记录一次快照.
                    pushUndoSnapshot()
                    saveSticky = true
                    dragSnapshotPushed = true
                    updateActionButtons()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                // End drag gesture.
                // zh-CN: 结束拖拽手势.
                dragSnapshotPushed = false

                updateToggleAllButton()
                updateActionButtons()
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                if (!canEditStructureNow()) return false
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false

                // Do NOT record undo here, it is already recorded once in onSelectedChanged().
                // zh-CN: 不要在此处记录撤销, 已在 onSelectedChanged() 记录一次.
                val item = items.removeAt(from)
                items.add(to, item)

                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })

        helper.attachToRecyclerView(recycler)
        itemTouchHelper = helper
    }

    private fun commitEditingIfNeeded(reason: CommitReason) {
        val s = editing ?: return

        // Ignore focus-lost commits while user scrolling.
        // zh-CN: 用户滚动期间忽略焦点丢失提交.
        if (reason == CommitReason.FocusLost && recyclerUserScrolling) return

        val pos = s.position
        if (pos < 0 || pos >= items.size) {
            editing = null
            return
        }

        val raw = s.editText.text?.toString().orEmpty()

        // Exit edit UI first to avoid flicker when adapter updates.
        // zh-CN: 先退出编辑 UI, 避免 adapter 更新造成闪烁.
        s.editText.clearFocus()
        hideKeyboard(s.editText)

        val hasWhitespace = raw.any { it.isWhitespace() }

        // Empty string: treat as cancel edit.
        // zh-CN: 空字符串: 视为放弃编辑.
        if (raw.isEmpty()) {
            if (s.isNewItem) {
                recordChange(markSaveSticky = true) {
                    items.removeAt(pos)
                }
                adapter.notifyItemRemoved(pos)
                updateToggleAllButton()
                updateActionButtons()
            } else {
                // Restore original content silently.
                // zh-CN: 静默恢复原内容.
                adapter.notifyItemChanged(pos)
            }

            editing = null
            return
        }

        // Whitespace chars are not allowed: toast + revert or delete.
        // zh-CN: 不允许包含空白字符: toast + 回滚或删除.
        if (hasWhitespace) {
            ViewUtils.showToast(this, R.string.text_symbol_invalid_no_whitespace, true)

            if (s.isNewItem) {
                recordChange(markSaveSticky = true) {
                    items.removeAt(pos)
                }
                adapter.notifyItemRemoved(pos)
                updateToggleAllButton()
                updateActionButtons()
            } else {
                adapter.notifyItemChanged(pos)
            }

            editing = null
            return
        }

        val newText = raw

        // If unchanged: just exit edit.
        // zh-CN: 若无变化: 仅退出编辑态.
        if (!s.isNewItem && newText == s.originalText) {
            adapter.notifyItemChanged(pos)
            editing = null
            return
        }

        // Avoid duplicates: treat as invalid and revert/delete.
        // zh-CN: 避免重复: 视为非法并回滚或删除.
        val dup = items.anyIndexed { i, it -> i != pos && it.text == newText }
        if (dup) {
            val msg = getString(R.string.text_symbol_name_conflict_with_value, newText)
            ViewUtils.showToast(this, msg, true)

            if (s.isNewItem) {
                recordChange(markSaveSticky = true) {
                    items.removeAt(pos)
                }
                adapter.notifyItemRemoved(pos)
                updateToggleAllButton()
                updateActionButtons()
            } else {
                adapter.notifyItemChanged(pos)
            }

            editing = null
            return
        }

        if (newText != s.originalText) {
            recordChange(markSaveSticky = true) {
                items[pos] = items[pos].copy(text = newText)
            }
            adapter.notifyItemChanged(pos)
        }

        updateActionButtons()
        editing = null
    }

    private fun <T> List<T>.anyIndexed(p: (index: Int, item: T) -> Boolean): Boolean {
        for (i in indices) {
            if (p(i, this[i])) return true
        }
        return false
    }

    private fun showKeyboard(editText: EditText) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        editText.post {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(editText: EditText) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun findFirstUniqueUnicodeSymbol(): String {
        // Start from U+0021 '!' and find the first code point not present in list.
        // zh-CN: 从 U+0021 '!' 开始遍历, 找到列表中不存在的第一个 Unicode 字符.
        val existing = items.map { it.text }.toHashSet()

        var cp = 0x21
        val max = 0x10FFFF

        while (cp <= max) {
            // Skip surrogate range.
            // zh-CN: 跳过代理对区间.
            if (cp in 0xD800..0xDFFF) {
                cp = 0xE000
                continue
            }

            if (cp in 0x007F..0x0390) {
                cp = 0x0391
                continue
            }

            if (cp == 0x03A2) {
                cp += 1
                continue
            }

            val s = String(Character.toChars(cp))

            // Must be valid symbol (non-blank and contains no whitespace).
            // zh-CN: 必须满足符号合法性 (非空且不含空白字符).
            if (SymbolsConfigStore.isValidSymbolText(s) && !existing.contains(s)) {
                return s
            }

            cp++
        }

        // Fallback.
        // zh-CN: 兜底.
        return "!"
    }

    @Suppress("DEPRECATION")
    private fun startExportJson() {
        val name = currentProfileName.ifBlank { "default" }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "autojs6-symbols-$name.json")
        }
        startActivityForResult(i, REQ_EXPORT_JSON)
    }

    @Suppress("DEPRECATION")
    private fun startImportJson() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(i, REQ_IMPORT_JSON)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_EXPORT_JSON -> doExportToUri(uri)
            REQ_IMPORT_JSON -> doImportFromUri(uri)
        }
    }

    private fun doExportToUri(uri: Uri) {
        runCatching {
            val json = SymbolsConfigStore.exportProfileToJson(this, currentProfileName)
            contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.toString(2).toByteArray(StandardCharsets.UTF_8))
                out.flush()
            } ?: error("Cannot open output stream")
            ViewUtils.showToast(this, R.string.text_done)
        }.onFailure {
            it.printStackTrace()
            ViewUtils.showToast(this, it.message, true)
        }
    }

    private fun doImportFromUri(uri: Uri) {
        runCatching {
            val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot open input stream")
            val json = JSONObject(String(raw, StandardCharsets.UTF_8))
            val (name, importedItems) = SymbolsConfigStore.importProfileFromJson(json)

            val reservedDefault = SymbolsConfigStore.isReservedDefaultName(this, name)

            // If name is a reserved "default" alias, force conflict flow:
            // - show 3 options
            // - overwrite disabled
            //
            // zh-CN: 若导入名是 "默认" 保留字别名, 则强制走冲突流程, 并禁用覆盖.
            if (reservedDefault) {
                handleImportNameConflictAndSave(
                    desiredName = name,
                    importedItems = importedItems,
                    overwriteEnabled = false,
                    forceConflict = true,
                )
                return@runCatching
            }

            handleImportNameConflictAndSave(
                desiredName = name,
                importedItems = importedItems,
                overwriteEnabled = true,
                forceConflict = false,
            )
        }.onFailure {
            it.printStackTrace()
            ViewUtils.showToast(this, it.message, true)
        }
    }

    private fun handleImportNameConflictAndSave(
        desiredName: String,
        importedItems: List<SymbolsConfigStore.SymbolItem>,
        overwriteEnabled: Boolean,
        forceConflict: Boolean,
    ) {
        val existing = SymbolsConfigStore.listProfiles(this).toSet()
        val conflict = forceConflict || existing.contains(desiredName)

        if (!conflict) {
            SymbolsConfigStore.saveProfile(this, desiredName, importedItems)
            SymbolsConfigStore.setActiveProfileName(this, desiredName)
            ViewUtils.showToast(this, R.string.text_done)
            recreate()
            return
        }

        val options = listOf(
            getString(R.string.text_import_strategy_overwrite),
            getString(R.string.text_import_strategy_auto_rename),
            getString(R.string.text_import_strategy_manual_rename),
        )

        DialogUtils.buildAndShowAdaptive {
            val builder = MaterialDialog.Builder(this)
                .title(R.string.text_import)
                .content(getString(R.string.text_import_name_conflict, desiredName))
                .items(options)
                .itemsCallback { d, _, which, _ ->
                    d.dismiss()
                    when (which) {
                        0 -> { // overwrite
                            if (!overwriteEnabled) return@itemsCallback
                            SymbolsConfigStore.saveProfile(this, desiredName, importedItems)
                            SymbolsConfigStore.setActiveProfileName(this, desiredName)
                            ViewUtils.showToast(this, R.string.text_done)
                            recreate()
                        }
                        1 -> { // auto rename
                            val unique = makeUniqueName(existing, desiredName)
                            SymbolsConfigStore.saveProfile(this, unique, importedItems)
                            SymbolsConfigStore.setActiveProfileName(this, unique)
                            ViewUtils.showToast(this, R.string.text_done)
                            recreate()
                        }
                        2 -> { // manual rename
                            promptManualRenameAndImport(existing, desiredName, importedItems)
                        }
                    }
                }

            if (!overwriteEnabled) {
                builder.itemsDisabledIndices(0)
            }

            builder.build()
        }
    }

    private fun promptManualRenameAndImport(
        existing: Set<String>,
        suggestedName: String,
        importedItems: List<SymbolsConfigStore.SymbolItem>,
    ) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_import_strategy_manual_rename)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(getString(R.string.text_symbols_profile_name_hint), suggestedName) { d, input ->
                    val raw = input?.toString()?.trim().orEmpty()
                    if (raw.isBlank()) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_invalid, true)
                        return@input
                    }
                    if (SymbolsConfigStore.isReservedDefaultName(this, raw)) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_cannot_be_default, true)
                        return@input
                    }
                    if (existing.contains(raw)) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_conflict, true)
                        return@input
                    }

                    SymbolsConfigStore.saveProfile(this, raw, importedItems)
                    SymbolsConfigStore.setActiveProfileName(this, raw)

                    d.dismiss()
                    ViewUtils.showToast(this, R.string.text_done)
                    recreate()
                }
                .widgetThemeColor()
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .autoDismiss(false)
                .build()
        }
    }

    private fun makeUniqueName(existing: Set<String>, base: String): String {
        if (!existing.contains(base)) return base
        var i = 2
        while (true) {
            val candidate = "$base ($i)"
            if (!existing.contains(candidate)) return candidate
            i++
        }
    }

    private class SymbolsAdapter(
        private val data: List<SymbolsConfigStore.SymbolItem>,
        private val onToggle: (pos: Int, enabled: Boolean) -> Unit,
        private val onAdd: (pos: Int) -> Unit,
        private val onDelete: (pos: Int) -> Unit,
        private val onBeginEdit: (EditingSession) -> Unit,
        private val onEditFocusChanged: (EditingSession, hasFocus: Boolean) -> Unit,
        private val onRequestDrag: (RecyclerView.ViewHolder) -> Unit,
    ) : RecyclerView.Adapter<SymbolsAdapter.VH>() {

        // Pending edit request for a position.
        // zh-CN: 指定 position 的待触发编辑请求.
        private var pendingEdit: PendingEdit? = null

        private data class PendingEdit(
            val position: Int,
            val isNewItem: Boolean,
        )

        fun requestEditAt(position: Int, isNewItem: Boolean) {
            pendingEdit = PendingEdit(position = position, isNewItem = isNewItem)
            notifyItemChanged(position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_symbol_config, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = data[position]

            // Normal view.
            // zh-CN: 普通展示态.
            holder.tvSymbol.text = item.text

            // Default state: show text, hide editor.
            // zh-CN: 默认态: 显示文本, 隐藏编辑器.
            holder.tvSymbol.visibility = View.VISIBLE
            holder.symbolEditPanel.visibility = View.GONE

            holder.cbEnabled.setOnCheckedChangeListener(null)
            holder.cbEnabled.isChecked = item.enabled
            holder.cbEnabled.setOnCheckedChangeListener { _, isChecked ->
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) onToggle(pos, isChecked)
            }

            holder.ivAdd.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) onAdd(pos)
            }

            holder.ivDelete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) onDelete(pos)
            }
            holder.ivDelete.setOnLongClickListener(null)

            holder.tvSymbol.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos < 0) return@setOnClickListener
                enterEditMode(holder = holder, position = pos, isNewItem = false)
            }

            holder.tvSymbol.setOnLongClickListener {
                onRequestDrag(holder)
                true
            }

            holder.ivDrag.setOnLongClickListener {
                onRequestDrag(holder)
                true
            }

            // Apply pending edit.
            // zh-CN: 应用待触发编辑请求.
            val p = pendingEdit
            if (p != null && p.position == position) {
                pendingEdit = null
                enterEditMode(holder = holder, position = position, isNewItem = p.isNewItem)
            }
        }

        private fun enterEditMode(holder: VH, position: Int, isNewItem: Boolean) {
            val item = data.getOrNull(position) ?: return

            holder.tvSymbol.visibility = View.GONE
            holder.symbolEditPanel.visibility = View.VISIBLE

            holder.etSymbol.onFocusChangeListener = null
            holder.etSymbol.setOnEditorActionListener(null)

            holder.etSymbol.setText(item.text)
            holder.etSymbol.setSelection(0, holder.etSymbol.text?.length ?: 0)

            val session = EditingSession(
                position = position,
                originalText = item.text,
                editText = holder.etSymbol,
                isNewItem = isNewItem,
            )

            holder.etSymbol.setOnFocusChangeListener { _, hasFocus ->
                onEditFocusChanged(session, hasFocus)
            }

            holder.etSymbol.setOnEditorActionListener { _, actionId, event ->
                val imeDone = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                val enterDown = event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER
                if (imeDone || enterDown) {
                    // Clear focus to trigger auto-commit.
                    // zh-CN: 清除焦点以触发自动提交.
                    holder.etSymbol.clearFocus()
                    true
                } else {
                    false
                }
            }

            onBeginEdit(session)
        }

        override fun getItemCount(): Int = data.size

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cbEnabled: CheckBox = itemView.findViewById(R.id.cbEnabled)

            val tvSymbol: TextView = itemView.findViewById(R.id.tvSymbol)

            val symbolEditPanel: View = itemView.findViewById(R.id.symbolEditPanel)
            val etSymbol: EditText = itemView.findViewById(R.id.etSymbol)

            val ivAdd: ImageView = itemView.findViewById(R.id.ivAdd)
            val ivDrag: ImageView = itemView.findViewById(R.id.ivDrag)
            val ivDelete: ImageView = itemView.findViewById(R.id.ivDelete)
        }
    }

    private data class ProfileEntry(
        val internalName: String,
        val displayName: String,
    )

    private enum class CommitReason {
        ToolbarAction,
        ItemAction,
        FocusLost,
        ScrollIdle,
        SwitchEditor,
        SwitchProfile,
        LeavingScreen,
    }

    private data class EditingSession(
        val position: Int,
        val originalText: String,
        val editText: EditText,
        val isNewItem: Boolean,
    )

    companion object {
        private const val REQ_EXPORT_JSON = 1001
        private const val REQ_IMPORT_JSON = 1002
    }
}
