package org.autojs.autojs.ui.wizard.permission

import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.autojs.autojs.core.pref.keys.WizardKeys
import org.autojs.autojs.ui.wizard.WizardProfile
import org.autojs.autojs.ui.wizard.WizardProfileController
import org.autojs.autojs.ui.wizard.WizardProfileStore
import org.autojs.autojs.ui.wizard.WizardSelectionPolicy
import org.autojs.autojs.ui.wizard.WizardUi
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogWizardBinding

/**
 * The permission wizard dialog: the drawer permissions as a checkable list with built-in and
 * custom selection profiles. The selected permissions are then requested one after another.
 *
 * zh-CN: 权限申请向导对话框: 以可勾选列表展示抽屉中的权限, 支持内置及自定义选择配置.
 * 选中的权限随后依次申请.
 */
object PermissionWizardDialog {

    val store = WizardProfileStore(
        keys = WizardKeys.PERMISSION_WIZARD,
        membersKey = "permissions",
    )

    /**
     * Shows the wizard. [onFinished] runs once, after the wizard closed without requesting or
     * after the requests it started have ended; its argument tells whether the all files access
     * was granted during the run. [isFollowedByAnotherWizard] labels the dismiss button "Skip".
     */
    fun show(
        activity: FragmentActivity,
        isFollowedByAnotherWizard: Boolean = false,
        onFinished: (allFilesAccessGranted: Boolean) -> Unit = {},
    ) {
        Session(activity, isFollowedByAnotherWizard, onFinished).show()
    }

    private class Session(
        private val activity: FragmentActivity,
        private val isFollowedByAnotherWizard: Boolean,
        private val onFinished: (Boolean) -> Unit,
    ) : PermissionWizardAdapter.Listener, WizardProfileController.Callbacks {

        private val binding = DialogWizardBinding.inflate(LayoutInflater.from(activity))
        private val adapter = PermissionWizardAdapter(this)
        private val profiles = WizardProfileController(activity, store, binding.wizardProfileSpinner, this)
        private val options = ArrayList<MaterialDialog.OptionMenuItemSpec>()

        private var showGranted = store.isShowDone(activity)
        private var loadJob: Job? = null
        private var dialog: MaterialDialog? = null
        private var handedOver = false

        fun show() {
            binding.wizardHint.setText(R.string.text_permission_wizard_hint)
            WizardUi.tintLoadingProgress(binding.wizardProgress)
            binding.wizardRetry.isVisible = false
            binding.wizardList.apply {
                layoutManager = LinearLayoutManager(activity)
                adapter = this@Session.adapter
                itemAnimator = null
            }

            profiles.setUp()
            adapter.setShowGranted(showGranted)
            rebuildOptions()

            dialog = MaterialDialog.Builder(activity)
                .title(R.string.text_permission_wizard)
                .options(options)
                .customView(binding.root, false)
                .widgetThemeColor()
                .negativeText(if (isFollowedByAnotherWizard) R.string.text_skip else R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.text_permission_wizard_grant)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ -> onGrantClicked(d) }
                .autoDismiss(false)
                .dismissListener {
                    loadJob?.cancel()
                    if (!handedOver) onFinished(false)
                }
                .show()

            updateGrantButton(0)
            load()
        }

        private fun load() {
            binding.wizardList.isVisible = false
            binding.wizardStatusContainer.isVisible = true
            binding.wizardProgress.isVisible = true
            binding.wizardStatusText.text = null
            loadJob = activity.lifecycleScope.launch(Dispatchers.Main) {
                adapter.submit(PermissionWizardStates.load(activity))
                applyProfile(profiles.current)
                binding.wizardStatusContainer.isVisible = false
                binding.wizardList.isVisible = true
            }
        }

        // ---- Profiles and options ----

        private fun candidates() = adapter.allItems.map { it.toCandidate() }

        private fun applyProfile(profile: WizardProfile) {
            adapter.applyProfile(profile, WizardSelectionPolicy.resolveSelection(profile, candidates()))
        }

        override fun membersToSave(profile: WizardProfile): Set<String> =
            WizardSelectionPolicy.membersToSave(profile, candidates(), adapter.selectedIds)

        override fun onProfileApplied(profile: WizardProfile) = applyProfile(profile)

        override fun onProfilesChanged() = rebuildOptions()

        private fun rebuildOptions() {
            options.clear()
            options += MaterialDialog.OptionMenuItemSpec(
                activity.getString(R.string.text_permission_wizard_show_granted),
                { toggleShowGranted() },
                showGranted,
            )
            options += MaterialDialog.OptionMenuItemSpec(activity.getString(R.string.text_select_all)) { adapter.selectAll() }
            options += MaterialDialog.OptionMenuItemSpec(activity.getString(R.string.text_deselect_all)) { adapter.clearSelection() }
            options += profiles.options()
        }

        private fun toggleShowGranted() {
            showGranted = !showGranted
            store.setShowDone(activity, showGranted)
            adapter.setShowGranted(showGranted)
            rebuildOptions()
        }

        // ---- Selection / requests ----

        override fun onSelectionChanged(selectedIds: Set<String>) {
            updateGrantButton(selectedIds.size)
        }

        override fun onDescriptionRequested(item: PermissionWizardItem) {
            MaterialDialog.Builder(activity)
                .title(item.entry.titleRes)
                .content(item.entry.descriptionRes)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .show()
        }

        private fun updateGrantButton(selectedCount: Int) {
            val d = dialog ?: return
            d.setActionButton(DialogAction.POSITIVE, activity.getString(R.string.text_permission_wizard_grant_with_count, selectedCount))
            d.getActionButton(DialogAction.POSITIVE).isEnabled = selectedCount > 0
        }

        private fun onGrantClicked(d: MaterialDialog) {
            val entries = adapter.selectedEntries()
            if (entries.isEmpty()) return
            handedOver = true
            d.dismiss()
            val hadAllFilesAccess = PermissionWizardStates.isGranted(activity, PermissionWizardEntry.ALL_FILES_ACCESS)
            activity.lifecycleScope.launch(Dispatchers.Main) {
                var allFilesAccessGranted = false
                try {
                    PermissionWizardRunner.run(activity, entries)
                    allFilesAccessGranted = !hadAllFilesAccess &&
                            PermissionWizardStates.isGranted(activity, PermissionWizardEntry.ALL_FILES_ACCESS)
                } finally {
                    onFinished(allFilesAccessGranted)
                }
            }
        }

    }

}
