package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogPluginInstallWizardBinding

/**
 * The plugin install wizard dialog: a categorized, checkable plugin list with
 * built-in and custom selection profiles, shown once on the first launch and on
 * demand from the Plugins tab and the Plugin Center.
 *
 * zh-CN: 插件安装向导对话框: 按分类展示可勾选的插件列表, 支持内置及自定义选择配置,
 * 首次启动时展示一次, 之后可从插件标签页及插件中心手动打开.
 */
object PluginInstallWizardDialog {

    private const val FIRST_RUN_DELAY_MILLIS = 800L

    /** Shows the wizard once after a fresh install; the flag is set before the dialog appears. */
    fun showOnFirstRunIfNeeded(activity: AppCompatActivity) {
        if (PluginInstallWizardProfileStore.isFirstRunShown(activity)) return
        PluginInstallWizardProfileStore.markFirstRunShown(activity)
        activity.lifecycleScope.launch(Dispatchers.Main) {
            delay(FIRST_RUN_DELAY_MILLIS)
            if (activity.isFinishing || activity.isDestroyed) return@launch
            show(activity, activity.lifecycleScope, firstRun = true)
        }
    }

    fun show(context: Context, scope: CoroutineScope, firstRun: Boolean = false) {
        Session(context, scope, firstRun).show()
    }

    private class Session(
        private val context: Context,
        private val scope: CoroutineScope,
        private val firstRun: Boolean,
    ) : PluginInstallWizardAdapter.Listener {

        private val binding = DialogPluginInstallWizardBinding.inflate(LayoutInflater.from(context))
        private val adapter = PluginInstallWizardAdapter(this)
        private val options = ArrayList<MaterialDialog.OptionMenuItemSpec>()

        private var profiles: List<PluginInstallWizardProfile> = emptyList()
        private var currentProfile: PluginInstallWizardProfile = PluginInstallWizardProfile.STANDARD
        private var suppressSpinnerCallback = false
        private var loadJob: Job? = null
        private var dialog: MaterialDialog? = null

        fun show() {
            binding.pluginInstallWizardList.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = this@Session.adapter
                itemAnimator = null
            }
            binding.pluginInstallWizardRetry.setOnClickListener { load() }

            reloadProfiles(selectedId = PluginInstallWizardProfileStore.getLastProfileId(context))
            setUpSpinner()

            dialog = MaterialDialog.Builder(context)
                .title(R.string.text_plugin_install_wizard)
                .options(options)
                .customView(binding.root, false)
                .negativeText(if (firstRun) R.string.text_skip else R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.text_install)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ -> onInstallClicked(d) }
                .autoDismiss(false)
                .cancelable(!firstRun)
                .dismissListener { loadJob?.cancel() }
                .show()

            updateInstallButton(0)
            rebuildOptions()
            load()
        }

        // ---- Loading ----

        private fun load() {
            loadJob?.cancel()
            showLoading()
            loadJob = scope.launch(Dispatchers.Main) {
                when (val result = PluginInstallWizardLoader.load(context)) {
                    is PluginInstallWizardLoader.Result.Success -> {
                        if (result.items.isEmpty()) {
                            showError(context.getString(R.string.text_plugin_install_wizard_no_plugins_available))
                        } else {
                            adapter.submit(result.items)
                            applyProfile(currentProfile, persist = false)
                            showList()
                            result.warning?.let { reason ->
                                // Cached entries are shown; tell the user the index may be outdated.
                                // zh-CN: 展示的是缓存条目, 提示用户索引可能已过时.
                                ViewUtils.showToast(context, context.getString(R.string.error_plugin_index_fetch_failed_with_reason, reason), true)
                            }
                        }
                    }
                    is PluginInstallWizardLoader.Result.Failure -> {
                        showError(context.getString(R.string.text_plugin_install_wizard_index_unavailable, result.message))
                    }
                }
            }
        }

        private fun showLoading() {
            binding.pluginInstallWizardList.isVisible = false
            binding.pluginInstallWizardStatusContainer.isVisible = true
            binding.pluginInstallWizardProgress.isVisible = true
            binding.pluginInstallWizardRetry.isVisible = false
            binding.pluginInstallWizardStatusText.setText(R.string.text_plugin_install_wizard_loading_index)
        }

        private fun showError(message: String) {
            binding.pluginInstallWizardList.isVisible = false
            binding.pluginInstallWizardStatusContainer.isVisible = true
            binding.pluginInstallWizardProgress.isVisible = false
            binding.pluginInstallWizardRetry.isVisible = true
            binding.pluginInstallWizardStatusText.text = message
        }

        private fun showList() {
            binding.pluginInstallWizardStatusContainer.isVisible = false
            binding.pluginInstallWizardList.isVisible = true
        }

        // ---- Profiles ----

        private fun reloadProfiles(selectedId: String?) {
            profiles = PluginInstallWizardProfile.builtIns + PluginInstallWizardProfileStore.getCustomProfiles(context)
            currentProfile = profiles.firstOrNull { it.id == selectedId } ?: PluginInstallWizardProfile.STANDARD
        }

        private fun setUpSpinner() {
            val spinner = binding.pluginInstallWizardProfileSpinner
            spinner.adapter = buildSpinnerAdapter()
            spinner.setSelection(profiles.indexOf(currentProfile).coerceAtLeast(0), false)
            spinner.post {
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (suppressSpinnerCallback) return
                        val profile = profiles.getOrNull(position) ?: return
                        if (profile.id == currentProfile.id) return
                        applyProfile(profile, persist = true)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
        }

        private fun buildSpinnerAdapter(): ArrayAdapter<String> {
            val labels = profiles.map { it.displayName() }
            return object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, labels) {
                init {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getView(position, convertView, parent).apply {
                        (this as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, SPINNER_TEXT_SIZE_SP)
                    }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getDropDownView(position, convertView, parent).apply {
                        (this as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, SPINNER_TEXT_SIZE_SP)
                    }
            }
        }

        private fun refreshSpinner(selectedId: String) {
            reloadProfiles(selectedId)
            suppressSpinnerCallback = true
            binding.pluginInstallWizardProfileSpinner.adapter = buildSpinnerAdapter()
            binding.pluginInstallWizardProfileSpinner.setSelection(profiles.indexOf(currentProfile).coerceAtLeast(0), false)
            binding.pluginInstallWizardProfileSpinner.post { suppressSpinnerCallback = false }
        }

        private fun applyProfile(profile: PluginInstallWizardProfile, persist: Boolean) {
            currentProfile = profile
            val selection = PluginInstallWizardSelectionPolicy.resolveSelection(
                profile = profile,
                candidates = adapter.allItems.map { it.toCandidate() },
            )
            adapter.setSelection(selection)
            if (persist) {
                PluginInstallWizardProfileStore.setLastProfileId(context, profile.id)
            }
            rebuildOptions()
        }

        private fun rebuildOptions() {
            options.clear()
            options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_select_all)) { adapter.selectAll() }
            options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_deselect_all)) { adapter.clearSelection() }
            options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_plugin_install_wizard_profile_save_as)) { saveSelectionAsNewProfile() }
            if (currentProfile.isCustom) {
                options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_plugin_install_wizard_profile_update)) { updateCurrentProfile() }
                options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_plugin_install_wizard_profile_rename)) { renameCurrentProfile() }
                options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_plugin_install_wizard_profile_delete)) { deleteCurrentProfile() }
            }
        }

        private fun saveSelectionAsNewProfile() {
            promptProfileName(R.string.text_plugin_install_wizard_profile_save_as, prefill = null, excludingId = null) { name ->
                val profile = PluginInstallWizardProfile.custom(name, adapter.selectedPackageNames)
                PluginInstallWizardProfileStore.addCustomProfile(context, profile)
                PluginInstallWizardProfileStore.setLastProfileId(context, profile.id)
                refreshSpinner(profile.id)
                rebuildOptions()
                ViewUtils.showToast(context, R.string.text_plugin_install_wizard_profile_saved)
            }
        }

        private fun updateCurrentProfile() {
            val profile = currentProfile.takeIf { it.isCustom } ?: return
            val updated = profile.copy(packageNames = adapter.selectedPackageNames)
            PluginInstallWizardProfileStore.updateCustomProfile(context, updated)
            refreshSpinner(updated.id)
            ViewUtils.showToast(context, R.string.text_plugin_install_wizard_profile_saved)
        }

        private fun renameCurrentProfile() {
            val profile = currentProfile.takeIf { it.isCustom } ?: return
            promptProfileName(R.string.text_plugin_install_wizard_profile_rename, prefill = profile.customName, excludingId = profile.id) { name ->
                val renamed = profile.copy(customName = name)
                PluginInstallWizardProfileStore.updateCustomProfile(context, renamed)
                refreshSpinner(renamed.id)
                ViewUtils.showToast(context, R.string.text_plugin_install_wizard_profile_saved)
            }
        }

        private fun deleteCurrentProfile() {
            val profile = currentProfile.takeIf { it.isCustom } ?: return
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(context.getString(R.string.text_plugin_install_wizard_profile_delete_confirm, profile.customName.orEmpty()))
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_delete)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ ->
                    PluginInstallWizardProfileStore.deleteCustomProfile(context, profile.id)
                    refreshSpinner(PluginInstallWizardProfile.ID_STANDARD)
                    applyProfile(PluginInstallWizardProfile.STANDARD, persist = false)
                    ViewUtils.showToast(context, R.string.text_plugin_install_wizard_profile_deleted)
                }
                .show()
        }

        private fun promptProfileName(
            titleRes: Int,
            prefill: String?,
            excludingId: String?,
            onConfirmed: (String) -> Unit,
        ) {
            MaterialDialog.Builder(context)
                .title(titleRes)
                .input(context.getString(R.string.text_plugin_install_wizard_profile_name_hint), prefill, false) { d, input ->
                    val validation = PluginInstallWizardSelectionPolicy.validateCustomName(input.toString(), profiles, excludingId)
                    val positiveButton = d.getActionButton(DialogAction.POSITIVE)
                    positiveButton.isEnabled = validation == PluginInstallWizardSelectionPolicy.NameValidation.OK
                    if (validation == PluginInstallWizardSelectionPolicy.NameValidation.DUPLICATE) {
                        d.inputEditText?.error = context.getString(R.string.text_plugin_install_wizard_profile_name_exists)
                    } else {
                        d.inputEditText?.error = null
                    }
                }
                .alwaysCallInputCallback()
                .widgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ ->
                    val name = d.inputEditText?.text?.toString()?.trim().orEmpty()
                    if (PluginInstallWizardSelectionPolicy.validateCustomName(name, profiles, excludingId) != PluginInstallWizardSelectionPolicy.NameValidation.OK) {
                        return@onPositive
                    }
                    d.dismiss()
                    onConfirmed(name)
                }
                .autoDismiss(false)
                .show()
        }

        private fun PluginInstallWizardProfile.displayName(): String = when (kind) {
            PluginInstallWizardProfileKind.STANDARD -> context.getString(R.string.text_plugin_install_wizard_profile_standard)
            PluginInstallWizardProfileKind.MINIMAL -> context.getString(R.string.text_plugin_install_wizard_profile_minimal)
            PluginInstallWizardProfileKind.ALL -> context.getString(R.string.text_all)
            PluginInstallWizardProfileKind.CUSTOM -> customName.orEmpty()
        }

        // ---- Selection / install ----

        override fun onSelectionChanged(selectedPackageNames: Set<String>) {
            updateInstallButton(selectedPackageNames.size)
        }

        private fun updateInstallButton(selectedCount: Int) {
            val d = dialog ?: return
            d.setActionButton(DialogAction.POSITIVE, context.getString(R.string.text_plugin_install_wizard_install_with_count, selectedCount))
            d.getActionButton(DialogAction.POSITIVE).isEnabled = selectedCount > 0
        }

        private fun onInstallClicked(d: MaterialDialog) {
            val items = adapter.selectedItems()
            if (items.isEmpty()) {
                ViewUtils.showToast(context, R.string.text_plugin_install_wizard_no_selection)
                return
            }
            d.dismiss()
            PluginInstallWizardInstaller.start(context, scope, items)
        }

    }

    private const val SPINNER_TEXT_SIZE_SP = 14f

}
