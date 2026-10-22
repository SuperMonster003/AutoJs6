package org.autojs.autojs.ui.settings.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.pref.deploy.DeploymentItem
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Failure
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.autojs.autojs.ui.wizard.WizardUi
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogSettingsCategoryPickerBinding
import org.autojs.autojs6.databinding.SettingsCategoryPickerItemBinding
import org.autojs.plugin.editor.api.EditorPluginContract

/**
 * Step 3 of the import wizard (docs/settings/ROADMAP.md T10 / T18, D20, P7.6): the deployment items the selected
 * categories need on this device, each ticked to deploy or unticked to give it up (its key keeps the current value,
 * R8); what an item lacks is shown under it: the storage permission, or the Ace editor plugin, which "Install..." opens
 * in the plugin center and which is checked again on return. Without any item the step is skipped. The items run in
 * the apply step ([SettingsImporter.importArchive], [DeploymentProgressDialog]).
 *
 * zh-CN: 导入向导第 3 步 (ROADMAP T10 / T18, D20, P7.6): 所选分类在本机需要的部署项, 勾选即部署, 不勾选即放弃 (其键保持当前值, R8);
 * 部署项缺少的条件显示在其下方: 存储权限, 或 Ace 编辑器插件 ("安装..." 在插件中心打开, 返回后重新检查). 没有部署项时跳过本步骤.
 * 部署项在应用步骤中运行 ([SettingsImporter.importArchive], [DeploymentProgressDialog]).
 */
class DeploymentConfirmStep : SettingsWizardStep(Step.CONFIRM_DEPLOYMENTS) {

    override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? = coroutineScope {
        val activity = wizard.host.hostActivity
        val load: suspend () -> List<SettingsImporter.PendingDeployment> = { withContext(Dispatchers.IO) { SettingsImporter.pendingDeployments(activity, state) } }
        val first = try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@coroutineScope state.copy(step = Step.FAILED, failure = Failure.of(e), failureDetail = e.message ?: e.javaClass.simpleName)
        }
        if (first.isEmpty()) return@coroutineScope wizard.next(state.copy(deploy = emptyList()))

        val binding = DialogSettingsCategoryPickerBinding.inflate(LayoutInflater.from(activity))
        var rows = first
        var selected: Set<String> = state.deploy?.toSet() ?: rows.filterNot { it.pending.blocked }.map { it.pending.key }.toSet()
        lateinit var adapter: ItemAdapter

        fun publish() {
            val deploy = rows.filter { !it.pending.blocked && it.pending.key in selected }.map { it.pending.key }
            wizard.host.wizardState = state.copy(deploy = deploy)
            adapter.update(rows, selected)
        }

        adapter = ItemAdapter(
            activity,
            onToggle = { row ->
                selected = if (row.pending.key in selected) selected - row.pending.key else selected + row.pending.key
                publish()
            },
            onInstall = {
                launch {
                    WizardUi.launchAndAwaitReturn(activity) {
                        PluginCenterActivity.launch(activity, PluginIndexSource.OFFICIAL, EditorPluginContract.DEFAULT_PACKAGE_NAME)
                        true
                    }
                    rows = runCatching { load() }.getOrDefault(rows)
                    selected = selected + rows.filterNot { it.pending.blocked }.map { it.pending.key }
                    publish()
                }
            },
        )
        binding.settingsCategoryPickerHint.setText(R.string.settings_transfer_pick_deployments)
        binding.settingsCategoryPickerProgress.isVisible = false
        binding.settingsCategoryPickerList.isVisible = true
        listOf(binding.settingsCategoryPickerSelectAll, binding.settingsCategoryPickerDeselectAll, binding.settingsCategoryPickerInvert).forEach { it.isVisible = false }
        binding.settingsCategoryPickerList.apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
            itemAnimator = null
        }
        publish()

        val builder = dialog(wizard, state).customView(binding.root, false)
        val next = navigate(wizard, state, builder) { wizard.next(wizard.host.wizardState ?: state) }
        coroutineContext.cancelChildren()
        next
    }

    private class ItemAdapter(
        private val context: Context,
        private val onToggle: (SettingsImporter.PendingDeployment) -> Unit,
        private val onInstall: () -> Unit,
    ) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

        private val actionColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
        private var rows: List<SettingsImporter.PendingDeployment> = emptyList()
        private var selected: Set<String> = emptySet()

        @SuppressLint("NotifyDataSetChanged")
        fun update(rows: List<SettingsImporter.PendingDeployment>, selected: Set<String>) {
            this.rows = rows
            this.selected = selected
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(SettingsCategoryPickerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(rows[position])

        inner class ViewHolder(private val binding: SettingsCategoryPickerItemBinding) : RecyclerView.ViewHolder(binding.root) {

            private var row: SettingsImporter.PendingDeployment? = null

            init {
                binding.root.setOnClickListener { row?.takeUnless { it.pending.blocked }?.let(onToggle) }
                binding.settingsCategoryPickerItemRefine.setOnClickListener { onInstall() }
                binding.settingsCategoryPickerItemRefine.setTextColor(actionColor)
                binding.settingsCategoryPickerItemRefine.setText(R.string.settings_transfer_deploy_install_ace)
                binding.settingsCategoryPickerItemSubtitle.maxLines = 3
            }

            fun bind(row: SettingsImporter.PendingDeployment) {
                this.row = row
                val description = row.description
                binding.settingsCategoryPickerItemTitle.setText(SettingsTransferMessages.deploymentRes(description.kind))
                binding.settingsCategoryPickerItemSubtitle.text = buildString {
                    append(description.target)
                    if (DeploymentItem.Permission.ALL_FILES_ACCESS in description.permissions) {
                        append("\n").append(context.getString(R.string.settings_transfer_deploy_needs_all_files_access))
                    }
                    if (row.pending.blocked) append("\n").append(context.getString(R.string.text_ace_editor_plugin_required))
                }
                binding.settingsCategoryPickerItemRefine.isVisible = row.pending.blocked
                binding.settingsCategoryPickerItemCheckbox.apply {
                    isEnabled = !row.pending.blocked
                    isChecked = !row.pending.blocked && row.pending.key in selected
                }
                binding.root.alpha = if (row.pending.blocked) 0.5f else 1f
            }
        }
    }
}
