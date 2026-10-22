package org.autojs.autojs.ui.settings.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Kind
import org.autojs.autojs.ui.wizard.WizardUi
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogSettingsCategoryPickerBinding
import org.autojs.autojs6.databinding.SettingsCategoryPickerItemBinding

/**
 * The category step of the export and the import wizard (docs/settings/ROADMAP.md T10, D2 / D6): the rows of
 * [SettingsCategoryChoices] in page order, each with the number of its entries (the current settings for the export,
 * the manifest for the import), and "Select all" / "Deselect all" / "Invert selection" at the bottom (as in IDEA). The
 * import lists only the categories of the file (P7.5) and disables the credentials sealed for another device (D23;
 * P7.7 asks for a password where this step tells that it comes later). Ticking `credentials` for an export asks once
 * per wizard run (D2). The data categories show a "Refine..." entry and stay disabled until their detail pages exist
 * (P10.2). Every change of the selection is published to the host at once, so it survives the recreation of the page;
 * "Next" of the export remembers it without `credentials` ([MiscStateKeys.SETTINGS_EXPORT_CATEGORIES], appendix C Q28).
 * The rows are loaded off the main thread; a file that cannot be read any more ends the step at the failure page.
 *
 * zh-CN: 导出与导入向导的分类步骤 (ROADMAP T10, D2 / D6): 按页面顺序列出 [SettingsCategoryChoices] 的行, 每项显示条目数 (导出为当前
 * 设置, 导入为 manifest 的记录), 底部为 "全选" / "全不选" / "反选" (与 IDEA 一致). 导入只列出文件中的分类 (P7.5), 以其他设备密钥
 * 加密的凭据不可勾选 (D23; P7.7 在本步骤提示 "后续版本提供" 的位置询问口令). 导出时勾选 `credentials` 每次运行向导询问一次 (D2).
 * 数据分类显示 "细化..." 入口, 在其细化页完成 (P10.2) 之前不可勾选. 勾选每次变化都立即写入宿主, 因此页面重建后仍保留; 导出的
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

        adapter = CategoryAdapter(
            activity,
            onToggle = { category -> selectable?.let { request(SettingsCategorySelection.toggled(current.selected, category.id, it)) } },
            onRefine = { ViewUtils.showToast(activity, R.string.settings_transfer_refine_later, true) },
            onLocked = { ViewUtils.showToast(activity, R.string.settings_transfer_locked_later, true) },
        )
        adapter.setSelection(current.selected)
        binding.settingsCategoryPickerHint.setText(if (state.kind == Kind.IMPORT) R.string.settings_transfer_pick_import else R.string.settings_transfer_pick_export)
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

        val rows = async { withContext(Dispatchers.IO) { runCatching { rowsOf(activity, state) } } }
        launch {
            rows.await().onSuccess { loaded ->
                val categories = SettingsCategoryChoices.selectable(loaded)
                selectable = categories
                adapter.setRows(loaded)
                publish(SettingsCategorySelection.normalize(current.selected, categories))
                binding.settingsCategoryPickerProgress.isVisible = false
                binding.settingsCategoryPickerList.isVisible = true
            }.onFailure {
                loadFailure = it
                dialog?.dismiss()
            }
        }

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

    /** The rows of the wizard of [state]; runs on a background thread. zh-CN: [state] 所属向导的行; 在后台线程调用. */
    private fun rowsOf(context: Context, state: SettingsWizardState): List<SettingsCategoryChoice> = when (state.kind) {
        Kind.IMPORT -> SettingsImporter.inspect(context, state).choices
        else -> SettingsCategoryChoices.export(entryCounts())
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
        private val onLocked: (SettingCategory) -> Unit,
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private val refineColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
        private var rows: List<SettingsCategoryChoice> = emptyList()
        private var selected: Set<String> = emptySet()

        @SuppressLint("NotifyDataSetChanged")
        fun setSelection(ids: Collection<String>) {
            selected = ids.toSet()
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
                        SettingsCategoryChoice.State.LOCKED -> onLocked(row.category)
                        SettingsCategoryChoice.State.LATER_VERSION -> Unit
                    }
                }
                binding.settingsCategoryPickerItemRefine.setOnClickListener { row?.let { onRefine(it.category) } }
                binding.settingsCategoryPickerItemRefine.setTextColor(refineColor)
            }

            fun bind(row: SettingsCategoryChoice) {
                this.row = row
                val category = row.category
                binding.settingsCategoryPickerItemTitle.setText(category.titleRes)
                binding.settingsCategoryPickerItemSubtitle.text = when (row.state) {
                    SettingsCategoryChoice.State.LATER_VERSION -> context.getString(R.string.settings_transfer_later_version)
                    SettingsCategoryChoice.State.LOCKED -> context.getString(R.string.settings_transfer_category_locked)
                    SettingsCategoryChoice.State.SELECTABLE -> row.entries?.let { context.resources.getQuantityString(R.plurals.settings_transfer_category_entries, it, it) }.orEmpty()
                }
                binding.settingsCategoryPickerItemRefine.isVisible = category.isData
                binding.settingsCategoryPickerItemCheckbox.apply {
                    isEnabled = row.isSelectable
                    isChecked = category.id in selected
                }
                binding.root.isClickable = row.state != SettingsCategoryChoice.State.LATER_VERSION
                binding.root.alpha = if (row.isSelectable) 1f else 0.5f
            }
        }
    }
}
