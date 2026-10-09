package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.autojs.autojs.ui.wizard.WizardProfile
import org.autojs.autojs.ui.wizard.WizardProfileController
import org.autojs.autojs.ui.wizard.WizardSelectionPolicy
import org.autojs.autojs.ui.wizard.WizardUi
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogWizardBinding

/**
 * The plugin install wizard dialog: a categorized, checkable plugin list with
 * built-in and custom selection profiles, opened from the setup wizards, the
 * Plugins tab and the Plugin Center.
 *
 * zh-CN: 插件安装向导对话框: 按分类展示可勾选的插件列表, 支持内置及自定义选择配置,
 * 可从设置向导, 插件标签页及插件中心打开.
 */
object PluginInstallWizardDialog {

    /**
     * Shows the wizard. [onFinished] runs once, after the wizard closed without installing or after
     * the installation it started has finished. [isFollowedByAnotherWizard] labels the dismiss
     * button "Skip".
     */
    fun show(
        context: Context,
        scope: CoroutineScope,
        isFollowedByAnotherWizard: Boolean = false,
        onFinished: () -> Unit = {},
    ) {
        Session(context, scope, isFollowedByAnotherWizard, onFinished).show()
    }

    private class Session(
        private val context: Context,
        private val scope: CoroutineScope,
        private val isFollowedByAnotherWizard: Boolean,
        private val onFinished: () -> Unit,
    ) : PluginInstallWizardAdapter.Listener, WizardProfileController.Callbacks {

        private val store = PluginInstallWizardProfileStore.store
        private val binding = DialogWizardBinding.inflate(LayoutInflater.from(context))
        private val adapter = PluginInstallWizardAdapter(this)
        private val profiles = WizardProfileController(context, store, binding.wizardProfileSpinner, this)
        private val options = ArrayList<MaterialDialog.OptionMenuItemSpec>()

        private var showInstalled = store.isShowDone(context)
        private var showLauncherIcons = PluginInstallWizardProfileStore.showLauncherIcons(context)
        private var loadJob: Job? = null
        private var dialog: MaterialDialog? = null

        /** Set once the installer took over; it then reports the end of the wizard itself. */
        private var handedOver = false

        fun show() {
            binding.wizardHint.setText(R.string.text_plugin_install_wizard_hint)
            WizardUi.tintLoadingProgress(binding.wizardProgress)
            binding.wizardList.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = this@Session.adapter
                itemAnimator = null
            }
            binding.wizardRetry.setOnClickListener { load() }

            profiles.setUp()
            adapter.setShowInstalled(showInstalled)
            rebuildOptions()

            dialog = MaterialDialog.Builder(context)
                .title(R.string.text_plugin_install_wizard)
                .options(options)
                .customView(binding.root, false)
                .widgetThemeColor()
                .negativeText(if (isFollowedByAnotherWizard) R.string.text_skip else R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.text_install)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ -> onInstallClicked(d) }
                .autoDismiss(false)
                .dismissListener {
                    loadJob?.cancel()
                    if (!handedOver) onFinished()
                }
                .show()

            updateInstallButton(0)
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
                            applyProfile(profiles.current)
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
            binding.wizardList.isVisible = false
            binding.wizardStatusContainer.isVisible = true
            binding.wizardProgress.isVisible = true
            binding.wizardRetry.isVisible = false
            binding.wizardStatusText.setText(R.string.text_plugin_install_wizard_loading_index)
        }

        private fun showError(message: String) {
            binding.wizardList.isVisible = false
            binding.wizardStatusContainer.isVisible = true
            binding.wizardProgress.isVisible = false
            binding.wizardRetry.isVisible = true
            binding.wizardStatusText.text = message
        }

        private fun showList() {
            binding.wizardStatusContainer.isVisible = false
            binding.wizardList.isVisible = true
        }

        // ---- Profiles and options ----

        private fun candidates() = adapter.allItems.map { it.toCandidate() }

        private fun applyProfile(profile: WizardProfile) {
            adapter.applyProfile(profile, WizardSelectionPolicy.resolveSelection(profile, candidates()))
        }

        override fun membersToSave(profile: WizardProfile): Set<String> =
            WizardSelectionPolicy.membersToSave(profile, candidates(), adapter.selectedPackageNames)

        override fun onProfileApplied(profile: WizardProfile) = applyProfile(profile)

        override fun onProfilesChanged() = rebuildOptions()

        private fun rebuildOptions() {
            options.clear()
            options += MaterialDialog.OptionMenuItemSpec(
                context.getString(R.string.text_plugin_install_wizard_show_installed),
                { toggleShowInstalled() },
                showInstalled,
            )
            options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_select_all)) { adapter.selectAll() }
            options += MaterialDialog.OptionMenuItemSpec(
                context.getString(R.string.text_plugin_install_wizard_show_launcher_icons),
                {
                    showLauncherIcons = !showLauncherIcons
                    PluginInstallWizardProfileStore.setShowLauncherIcons(context, showLauncherIcons)
                    rebuildOptions()
                },
                showLauncherIcons,
            )
            options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_deselect_all)) { adapter.clearSelection() }
            options += profiles.options()
        }

        private fun toggleShowInstalled() {
            showInstalled = !showInstalled
            store.setShowDone(context, showInstalled)
            adapter.setShowInstalled(showInstalled)
            rebuildOptions()
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
            handedOver = true
            d.dismiss()
            PluginInstallWizardInstaller.start(context, scope, items, showLauncherIcons, onFinished)
        }

    }

}
