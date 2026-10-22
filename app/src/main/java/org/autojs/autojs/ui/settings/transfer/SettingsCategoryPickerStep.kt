package org.autojs.autojs.ui.settings.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsPassword
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.core.pref.data.SettingsDataCapture
import org.autojs.autojs.core.pref.data.SettingsDataEntries
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Kind
import org.autojs.autojs.ui.wizard.WizardUi
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogSettingsCategoryPickerBinding
import org.autojs.autojs6.databinding.DialogSettingsPasswordBinding
import org.autojs.autojs6.databinding.SettingsCategoryPickerItemBinding

/**
 * The category step of the export, the import and the reset wizard (docs/settings/ROADMAP.md T10, D2 / D6 / D11): the rows of
 * [SettingsCategoryChoices] in page order, each with the number of its entries (the current settings for the export,
 * the manifest for the import), and "Select all" / "Deselect all" / "Invert selection" at the bottom (as in IDEA). The
 * import lists only the categories of the file (P7.5) and disables the credentials sealed for another device (D23);
 * when they have a password slot, tapping the row asks for the password and ticks them once it opens them (P7.7, the
 * password stays in [SettingsPasswords]). Ticking `credentials` for an export asks once
 * per wizard run (D2). The data categories show a "Refine..." entry that opens their detail page (P10.2): their
 * entries, all ticked at first ([SettingsWizardState.refine] keeps a choice of some of them; none unticks the
 * category, some tick it); a reset cannot tick `other` and the data categories (P8.1). Every change of the selection
 * is published to the host at once, so it survives the recreation of the page;
 * "Next" of the export remembers it without `credentials` ([MiscStateKeys.SETTINGS_EXPORT_CATEGORIES], appendix C Q28).
 * The rows are loaded off the main thread; a file that cannot be read any more ends the step at the failure page.
 *
 * zh-CN: 导出, 导入与恢复默认向导的分类步骤 (ROADMAP T10, D2 / D6 / D11): 按页面顺序列出 [SettingsCategoryChoices] 的行, 每项显示条目数 (导出为当前
 * 设置, 导入为 manifest 的记录), 底部为 "全选" / "全不选" / "反选" (与 IDEA 一致). 导入只列出文件中的分类 (P7.5), 以其他设备密钥
 * 加密的凭据不可勾选 (D23); 带口令槽时点击该行询问口令, 口令正确后勾选 (P7.7, 口令保存在 [SettingsPasswords]). 导出时勾选 `credentials` 每次运行向导询问一次 (D2).
 * 数据分类显示 "细化..." 入口, 打开其细化页 (P10.2): 列出其条目, 初始全部勾选 ([SettingsWizardState.refine] 记录选中部分条目的情况;
 * 一条都不选时取消勾选该分类, 选了条目时勾选该分类); 恢复默认不可勾选 `other` 与数据分类 (P8.1). 勾选每次变化都立即写入宿主,
 * 因此页面重建后仍保留; 导出的
 * "下一步" 记住不含 `credentials` 的勾选 ([MiscStateKeys.SETTINGS_EXPORT_CATEGORIES], 附录 C Q28). 行在主线程之外加载; 文件已无法
 * 读取时本步骤转到失败页.
 */
class SettingsCategoryPickerStep : SettingsWizardStep(SettingsWizardState.Step.PICK_CATEGORIES) {

    override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? = coroutineScope {
        val activity = wizard.host.hostActivity
        val binding = DialogSettingsCategoryPickerBinding.inflate(LayoutInflater.from(activity))
        var current = state
        var dialog: MaterialDialog? = null
        // The categories that can be ticked; null until the rows are loaded.
        var selectable: List<SettingCategory>? = null
        var loadFailure: Throwable? = null
        // The entries of the data categories: captured with the rows for an export, read on demand for an import.
        val dataEntries = mutableMapOf<String, List<SettingsDataEntries.Entry>>()
        var refining = false
        wizard.host.wizardState = current

        lateinit var adapter: CategoryAdapter

        fun publish(selected: List<String>) {
            current = current.copy(selected = selected)
            wizard.host.wizardState = current
            adapter.setSelection(selected)
            dialog?.getActionButton(DialogAction.POSITIVE)?.isEnabled = selectable != null && selected.isNotEmpty()
        }

        fun request(selected: List<String>) {
            if (state.kind != Kind.EXPORT || current.credentialsConfirmed || !SettingsCategorySelection.addsCredentials(current.selected, selected)) {
                publish(selected)
                return
            }
            launch {
                val action = MaterialDialog.Builder(activity)
                    .title(R.string.settings_transfer_credentials_warning_title)
                    .content(R.string.settings_transfer_credentials_warning)
                    .canceledOnTouchOutside(false)
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_continue)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .showAndAwait()
                if (action == DialogAction.POSITIVE) {
                    current = current.copy(credentialsConfirmed = true)
                    publish(selected)
                } else {
                    publish(selected - SettingCategory.CREDENTIALS.id)
                }
            }
        }

        // Loads the rows of the current state off the main thread and ticks [select] among them.
        suspend fun load(select: List<String>) {
            withContext(Dispatchers.IO) { runCatching { rowsOf(activity, current) } }.onSuccess { (loaded, entries) ->
                val categories = SettingsCategoryChoices.selectable(loaded)
                selectable = categories
                dataEntries.putAll(entries)
                adapter.setRows(loaded)
                publish(SettingsCategorySelection.normalize(select, categories))
                binding.settingsCategoryPickerProgress.isVisible = false
                binding.settingsCategoryPickerList.isVisible = true
            }.onFailure {
                loadFailure = it
                dialog?.dismiss()
            }
        }

        // A password opens the sealed row (P7.7): the rows are loaded again with it, and the row is ticked.
        fun unlock(category: SettingCategory) {
            launch {
                val password = askPassword(wizard, current, category) ?: return@launch
                SettingsPasswords.forget(current.password)
                current = current.copy(password = SettingsPasswords.remember(password))
                wizard.host.wizardState = current
                load(current.selected + category.id)
            }
        }

        // The detail page of a data category (P10.2): the chosen entries are kept, and the category is ticked with them.
        fun refine(category: SettingCategory) {
            val categories = selectable ?: return
            if (refining) return
            refining = true
            launch {
                try {
                    val entries = dataEntries[category.id] ?: withContext(Dispatchers.IO) {
                        runCatching { SettingsImporter.dataEntries(activity, current, category.id) }
                    }.getOrElse { e ->
                        ViewUtils.showToast(activity, SettingsTransferMessages.failureText(activity, Failure.of(e), e.message ?: e.javaClass.simpleName), true)
                        return@launch
                    }.also { dataEntries[category.id] = it }
                    if (entries.isEmpty()) {
                        ViewUtils.showToast(activity, R.string.settings_transfer_refine_empty, true)
                        return@launch
                    }
                    val chosen = chooseEntries(wizard, category, entries, current.refine[category.id]) ?: return@launch
                    current = current.copy(
                        refine = when (chosen.size) {
                            0, entries.size -> current.refine - category.id
                            else -> current.refine + (category.id to chosen)
                        },
                    )
                    adapter.setRefine(current.refine)
                    publish(
                        when {
                            chosen.isEmpty() -> current.selected - category.id
                            category.id in current.selected -> current.selected
                            else -> SettingsCategorySelection.toggled(current.selected, category.id, categories)
                        },
                    )
                } finally {
                    refining = false
                }
            }
        }

        adapter = CategoryAdapter(
            activity,
            onToggle = { category -> selectable?.let { request(SettingsCategorySelection.toggled(current.selected, category.id, it)) } },
            onRefine = { refine(it) },
            onLocked = { row ->
                when (row.state) {
                    SettingsCategoryChoice.State.PASSWORD -> unlock(row.category)
                    else -> ViewUtils.showToast(activity, R.string.settings_transfer_locked_no_password, true)
                }
            },
        )
        adapter.setSelection(current.selected)
        adapter.setRefine(current.refine)
        binding.settingsCategoryPickerHint.setText(
            when (state.kind) {
                Kind.IMPORT -> R.string.settings_transfer_pick_import
                Kind.RESET -> R.string.settings_transfer_pick_reset
                else -> R.string.settings_transfer_pick_export
            }
        )
        WizardUi.tintLoadingProgress(binding.settingsCategoryPickerProgress)
        binding.settingsCategoryPickerList.apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
            itemAnimator = null
        }
        val actionColor = actionColor(activity)
        binding.settingsCategoryPickerSelectAll.apply {
            setTextColor(actionColor)
            setOnClickListener { selectable?.let { request(SettingsCategorySelection.all(it)) } }
        }
        binding.settingsCategoryPickerDeselectAll.apply {
            setTextColor(actionColor)
            setOnClickListener { if (selectable != null) publish(emptyList()) }
        }
        binding.settingsCategoryPickerInvert.apply {
            setTextColor(actionColor)
            setOnClickListener { selectable?.let { request(SettingsCategorySelection.inverted(current.selected, it)) } }
        }

        launch { load(current.selected) }

        val builder = dialog(wizard, state)
            .customView(binding.root, false)
            .showListener {
                dialog = it as MaterialDialog
                it.getActionButton(DialogAction.POSITIVE).isEnabled = selectable != null && current.selected.isNotEmpty()
                if (loadFailure != null) it.dismiss()
            }
        val next = navigate(wizard, state, builder) {
            if (state.kind == Kind.EXPORT) MiscStateKeys.SETTINGS_EXPORT_CATEGORIES.set(SettingsCategorySelection.toRemember(current.selected))
            wizard.next(current)
        }
        coroutineContext.cancelChildren()
        loadFailure?.let { e ->
            current.copy(step = SettingsWizardState.Step.FAILED, failure = Failure.of(e), failureDetail = e.message ?: e.javaClass.simpleName)
        } ?: next
    }

    /**
     * Asks for the password of the sealed [category] of the import (P7.7, D23 / T21) and checks it off the main thread
     * behind a progress dialog; a wrong password offers to try again or to skip the credentials. Returns the password
     * that opens the category, or null when the user cancels or skips (the row stays locked).
     * zh-CN: 询问导入文件中加密分类 [category] 的口令 (P7.7, D23 / T21), 在主线程之外校验并显示进度; 口令错误时可重试或跳过凭据. 返回能
     * 打开该分类的口令; 用户取消或跳过时为 null (该行保持锁定).
     */
    private suspend fun askPassword(wizard: SettingsWizard, state: SettingsWizardState, category: SettingCategory): SettingsPassword? {
        val activity = wizard.host.hostActivity
        while (true) {
            val binding = DialogSettingsPasswordBinding.inflate(LayoutInflater.from(activity))
            binding.settingsPasswordHint.setText(R.string.settings_transfer_password_import_hint)
            binding.settingsPasswordConfirmLayout.isVisible = false
            var dialog: MaterialDialog? = null
            binding.settingsPassword.doAfterTextChanged { dialog?.getActionButton(DialogAction.POSITIVE)?.isEnabled = !it.isNullOrEmpty() }
            val action = MaterialDialog.Builder(activity)
                .title(R.string.settings_transfer_password_enter_title)
                .customView(binding.root, true)
                .widgetThemeColor()
                .canceledOnTouchOutside(false)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .showListener {
                    dialog = it as MaterialDialog
                    it.getActionButton(DialogAction.POSITIVE).isEnabled = false
                }
                .showAndAwait()
            if (action != DialogAction.POSITIVE) return null
            val password = SettingsPassword.of(binding.settingsPassword.text ?: "") ?: return null
            binding.settingsPassword.text?.clear()
            val progress = MaterialDialog.Builder(activity)
                .title(wizard.titleRes)
                .content(R.string.settings_transfer_password_checking)
                .progress(true, 0)
                .widgetThemeColor()
                .cancelable(false)
                .show()
            val opens = try {
                withContext(Dispatchers.IO) { SettingsImporter.checkPassword(activity, state, category.id, password) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ViewUtils.showToast(activity, SettingsTransferMessages.failureText(activity, Failure.of(e), e.message ?: e.javaClass.simpleName), true)
                return null
            } finally {
                progress.dismiss()
            }
            if (opens) return password
            val again = MaterialDialog.Builder(activity)
                .title(R.string.settings_transfer_password_wrong_title)
                .content(R.string.settings_transfer_password_wrong)
                .widgetThemeColor()
                .canceledOnTouchOutside(false)
                .negativeText(R.string.settings_transfer_password_skip)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_retry)
                .positiveColorRes(R.color.dialog_button_attraction)
                .showAndAwait()
            if (again != DialogAction.POSITIVE) return null
        }
    }

    /**
     * Asks which of [entries] of the data category [category] to keep (P10.2), [chosen] ticked (all of them when null),
     * with "Invert selection"; returns the ids of the ticked entries in page order, or null when the user cancels.
     * zh-CN: 询问保留数据分类 [category] 的哪些条目 (P10.2), 初始勾选 [chosen] (为 null 时全部勾选), 提供 "反选"; 返回勾选条目的 id
     * (按页面顺序), 用户取消时为 null.
     */
    private suspend fun chooseEntries(
        wizard: SettingsWizard,
        category: SettingCategory,
        entries: List<SettingsDataEntries.Entry>,
        chosen: List<String>?,
    ): List<String>? {
        var result: List<String>? = null
        val ticked = entries.indices.filter { chosen == null || entries[it].id in chosen }
        MaterialDialog.Builder(wizard.host.hostActivity)
            .title(category.titleRes)
            .items(entries.map { entry -> entry.detail?.let { "${entry.title}\n$it" } ?: entry.title })
            .itemsCallbackMultiChoice(ticked.toTypedArray()) { _, _, _ -> true }
            .widgetThemeColor()
            .autoDismiss(false)
            .canceledOnTouchOutside(false)
            .neutralText(R.string.text_invert_selection)
            .neutralColorRes(R.color.dialog_button_default)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onNeutral { dialog, _ ->
                val selected = dialog.selectedIndices.orEmpty().toSet()
                dialog.setSelectedIndices(entries.indices.filterNot { it in selected }.toTypedArray())
            }
            .onNegative { dialog, _ -> dialog.dismiss() }
            .onPositive { dialog, _ ->
                val selected = dialog.selectedIndices.orEmpty().toSet()
                result = entries.filterIndexed { index, _ -> index in selected }.map { it.id }
                dialog.dismiss()
            }
            .showAndAwait()
        return result
    }

    /**
     * The rows of the wizard of [state] and, for an export, the entries of the data categories now (P10.2); runs on a
     * background thread.
     * zh-CN: [state] 所属向导的行, 导出时另有数据分类当前的条目 (P10.2); 在后台线程调用.
     */
    private fun rowsOf(context: Context, state: SettingsWizardState): Pair<List<SettingsCategoryChoice>, Map<String, List<SettingsDataEntries.Entry>>> =
        when (state.kind) {
            Kind.IMPORT -> SettingsImporter.inspect(context, state).choices to emptyMap()
            Kind.RESET -> SettingsCategoryChoices.reset(entryCounts()) to emptyMap()
            else -> {
                val entries = SettingsDataCapture.capture(context, SettingCategory.ALL.filter { it.isData }, System.currentTimeMillis())
                    .associate { it.id to SettingsDataEntries.entries(it.id, it.bytes) }
                SettingsCategoryChoices.export(entryCounts() + entries.mapValues { it.value.size }) to entries
            }
        }

    /** Entries per settings category in the current settings (the data categories are not in snapshots, T19). */
    private fun entryCounts(): Map<String, Int> =
        SettingsSnapshot.capture(SettingCategory.ALL).categories.associate { it.id to it.entries }

    private fun actionColor(context: Context): Int =
        ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)

    private class CategoryAdapter(
        private val context: Context,
        private val onToggle: (SettingCategory) -> Unit,
        private val onRefine: (SettingCategory) -> Unit,
        private val onLocked: (SettingsCategoryChoice) -> Unit,
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private val refineColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
        private var rows: List<SettingsCategoryChoice> = emptyList()
        private var selected: Set<String> = emptySet()
        private var refine: Map<String, List<String>> = emptyMap()

        @SuppressLint("NotifyDataSetChanged")
        fun setSelection(ids: Collection<String>) {
            selected = ids.toSet()
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun setRefine(refine: Map<String, List<String>>) {
            this.refine = refine
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun setRows(rows: List<SettingsCategoryChoice>) {
            this.rows = rows
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(SettingsCategoryPickerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(rows[position])

        inner class ViewHolder(private val binding: SettingsCategoryPickerItemBinding) : RecyclerView.ViewHolder(binding.root) {

            private var row: SettingsCategoryChoice? = null

            init {
                binding.root.setOnClickListener {
                    val row = row ?: return@setOnClickListener
                    when (row.state) {
                        SettingsCategoryChoice.State.SELECTABLE -> onToggle(row.category)
                        SettingsCategoryChoice.State.LOCKED, SettingsCategoryChoice.State.PASSWORD -> onLocked(row)
                        SettingsCategoryChoice.State.NOT_RESETTABLE -> Unit
                    }
                }
                binding.settingsCategoryPickerItemRefine.setOnClickListener { row?.let { onRefine(it.category) } }
                binding.settingsCategoryPickerItemRefine.setTextColor(refineColor)
            }

            fun bind(row: SettingsCategoryChoice) {
                this.row = row
                val category = row.category
                binding.settingsCategoryPickerItemTitle.setText(category.titleRes)
                val refined = refine[category.id]
                binding.settingsCategoryPickerItemSubtitle.text = when (row.state) {
                    SettingsCategoryChoice.State.LOCKED -> context.getString(R.string.settings_transfer_category_locked)
                    SettingsCategoryChoice.State.PASSWORD -> context.getString(R.string.settings_transfer_category_password)
                    SettingsCategoryChoice.State.NOT_RESETTABLE -> context.getString(R.string.settings_transfer_category_not_resettable)
                    SettingsCategoryChoice.State.SELECTABLE -> when {
                        refined != null && row.entries != null -> context.getString(R.string.settings_transfer_category_refined, refined.size, row.entries)
                        else -> row.entries?.let { context.resources.getQuantityString(R.plurals.settings_transfer_category_entries, it, it) }.orEmpty()
                    }
                }
                binding.settingsCategoryPickerItemRefine.isVisible =
                    SettingsDataEntries.isRefinable(category) && row.isSelectable && (row.entries ?: 0) > 0
                binding.settingsCategoryPickerItemCheckbox.apply {
                    isEnabled = row.isSelectable
                    isChecked = category.id in selected
                }
                val asks = row.state == SettingsCategoryChoice.State.PASSWORD
                binding.root.isClickable = row.isSelectable || asks || row.state == SettingsCategoryChoice.State.LOCKED
                binding.root.alpha = if (row.isSelectable || asks) 1f else 0.5f
            }
        }
    }
}
