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
import org.autojs.autojs.ui.wizard.WizardUi
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogSettingsCategoryPickerBinding
import org.autojs.autojs6.databinding.SettingsCategoryPickerItemBinding

/**
 * Step 1 of the export wizard (docs/settings/ROADMAP.md T10, D2 / D6): the categories of appendix A.1 in page order,
 * each with the number of entries the current [SettingsSnapshot] holds for it, and "Select all" / "Deselect all" /
 * "Invert selection" at the bottom (as in IDEA). Ticking `credentials` asks once per wizard run (D2). The data
 * categories show a "Refine..." entry and stay disabled until their codecs and detail pages exist (P10). Every change
 * of the selection is published to the host at once, so it survives the recreation of the page; "Next" remembers it
 * without `credentials` ([MiscStateKeys.SETTINGS_EXPORT_CATEGORIES], appendix C Q28).
 *
 * zh-CN: 导出向导第 1 步 (ROADMAP T10, D2 / D6): 按页面顺序列出附录 A.1 的分类, 每项显示当前 [SettingsSnapshot] 中的条目数, 底部为
 * "全选" / "全不选" / "反选" (与 IDEA 一致). 勾选 `credentials` 时每次运行向导询问一次 (D2). 数据分类显示 "细化..." 入口, 在其编解码
 * 与细化页完成 (P10) 之前不可勾选. 勾选每次变化都立即写入宿主, 因此页面重建后仍保留; "下一步" 记住不含 `credentials` 的勾选
 * ([MiscStateKeys.SETTINGS_EXPORT_CATEGORIES], 附录 C Q28).
 */
class SettingsCategoryPickerStep : SettingsWizardStep(SettingsWizardState.Step.PICK_CATEGORIES) {

    override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? = coroutineScope {
        val activity = wizard.host.hostActivity
        val binding = DialogSettingsCategoryPickerBinding.inflate(LayoutInflater.from(activity))
        var current = state.copy(selected = SettingsCategorySelection.normalize(state.selected))
        var dialog: MaterialDialog? = null
        wizard.host.wizardState = current

        lateinit var adapter: CategoryAdapter

        fun publish(selected: List<String>) {
            current = current.copy(selected = selected)
            wizard.host.wizardState = current
            adapter.setSelection(selected)
            dialog?.getActionButton(DialogAction.POSITIVE)?.isEnabled = selected.isNotEmpty()
        }

        fun request(selected: List<String>) {
            if (current.credentialsConfirmed || !SettingsCategorySelection.addsCredentials(current.selected, selected)) {
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
            onToggle = { request(SettingsCategorySelection.toggled(current.selected, it.id)) },
            onRefine = { ViewUtils.showToast(activity, R.string.settings_transfer_refine_later, true) },
        )
        adapter.setSelection(current.selected)
        binding.settingsCategoryPickerHint.setText(R.string.settings_transfer_pick_export)
        WizardUi.tintLoadingProgress(binding.settingsCategoryPickerProgress)
        binding.settingsCategoryPickerList.apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
            itemAnimator = null
        }
        val actionColor = actionColor(activity)
        binding.settingsCategoryPickerSelectAll.apply { setTextColor(actionColor); setOnClickListener { request(SettingsCategorySelection.all()) } }
        binding.settingsCategoryPickerDeselectAll.apply { setTextColor(actionColor); setOnClickListener { publish(emptyList()) } }
        binding.settingsCategoryPickerInvert.apply { setTextColor(actionColor); setOnClickListener { request(SettingsCategorySelection.inverted(current.selected)) } }

        val counts = async { withContext(Dispatchers.IO) { entryCounts() } }
        launch {
            adapter.setCounts(counts.await())
            binding.settingsCategoryPickerProgress.isVisible = false
            binding.settingsCategoryPickerList.isVisible = true
        }

        val builder = dialog(wizard, state)
            .customView(binding.root, false)
            .showListener {
                dialog = it as MaterialDialog
                it.getActionButton(DialogAction.POSITIVE).isEnabled = current.selected.isNotEmpty()
            }
        val next = navigate(wizard, state, builder) {
            MiscStateKeys.SETTINGS_EXPORT_CATEGORIES.set(SettingsCategorySelection.toRemember(current.selected))
            wizard.next(current)
        }
        coroutineContext.cancelChildren()
        next
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
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private val categories = SettingCategory.ALL
        private val refineColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
        private var selected: Set<String> = emptySet()
        private var counts: Map<String, Int>? = null

        @SuppressLint("NotifyDataSetChanged")
        fun setSelection(ids: Collection<String>) {
            selected = ids.toSet()
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun setCounts(counts: Map<String, Int>) {
            this.counts = counts
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = categories.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(SettingsCategoryPickerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(categories[position])

        inner class ViewHolder(private val binding: SettingsCategoryPickerItemBinding) : RecyclerView.ViewHolder(binding.root) {

            private var category: SettingCategory? = null

            init {
                binding.root.setOnClickListener { category?.takeUnless { it.isData }?.let(onToggle) }
                binding.settingsCategoryPickerItemRefine.setOnClickListener { category?.let(onRefine) }
                binding.settingsCategoryPickerItemRefine.setTextColor(refineColor)
            }

            fun bind(category: SettingCategory) {
                this.category = category
                binding.settingsCategoryPickerItemTitle.setText(category.titleRes)
                binding.settingsCategoryPickerItemSubtitle.text = when {
                    category.isData -> context.getString(R.string.settings_transfer_later_version)
                    else -> counts?.get(category.id)?.let { context.resources.getQuantityString(R.plurals.settings_transfer_category_entries, it, it) }.orEmpty()
                }
                binding.settingsCategoryPickerItemRefine.isVisible = category.isData
                binding.settingsCategoryPickerItemCheckbox.apply {
                    isEnabled = !category.isData
                    isChecked = category.id in selected
                }
                binding.root.isClickable = !category.isData
                binding.root.alpha = if (category.isData) 0.5f else 1f
            }
        }
    }
}
