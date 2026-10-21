package org.autojs.autojs.ui.settings.transfer

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.theme.preference.ThemeColorPreferenceCategory
import org.autojs.autojs.theme.preference.ThemeColorSwitchPreference
import org.autojs.autojs6.R

/**
 * The preferences of "Manage AutoJs6 settings" (docs/settings/ROADMAP.md D4 / T10), written like `ShortcutBackupFragment`:
 * "Import settings" and "Export settings" on top, then "Restore default settings..." and "Undo last import", then the
 * three switches of the automatic backup (D13 / D22). Only the export works so far; the other entries show that they
 * come in a later version (P7 - P9). The page hosts the wizards ([SettingsWizardHost]) and resumes a running one after
 * it is recreated.
 *
 * zh-CN: "管理 AutoJs6 设置" 的偏好页 (ROADMAP D4 / T10), 写法同 `ShortcutBackupFragment`: 顶部为 "导入设置" 与 "导出设置",
 * 其下 "恢复默认设置..." 与 "撤销上次导入", 然后是自动备份的三个开关 (D13 / D22). 目前只有导出可用, 其他入口标明后续版本提供
 * (P7 - P9). 页面承载向导 ([SettingsWizardHost]), 重建后恢复运行中的向导.
 */
class SettingsTransferFragment : PreferenceFragmentCompat(), SettingsWizardHost {

    override val hostActivity: FragmentActivity
        get() = requireActivity()

    override var wizardState: SettingsWizardState? = null

    private var wizardJob: Job? = null
    private var startExport = false

    // A new page instance gets the result of a request made by the one before it (rotation, process death).
    // zh-CN: 新的页面实例接收前一个实例发出的请求的结果 (旋转屏幕, 进程被回收).
    private var document = CompletableDeferred<Uri?>()

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        document.complete(uri)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        wizardState = SettingsWizardState.fromText(savedInstanceState?.getString(STATE_WIZARD))
        startExport = savedInstanceState == null &&
                requireActivity().intent.getStringExtra(SettingsTransferActivity.EXTRA_WIZARD) == SettingsTransferActivity.WIZARD_EXPORT
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        preference(preferenceScreen, R.string.settings_transfer_description).apply { isSelectable = false }
        laterVersion(preference(preferenceScreen, R.string.settings_transfer_import))
        preference(preferenceScreen, R.string.settings_transfer_export) { startWizard(exportState()) }
            .setSummary(R.string.settings_transfer_export_summary)
        laterVersion(preference(preferenceScreen, R.string.settings_transfer_reset))
        laterVersion(preference(preferenceScreen, R.string.settings_transfer_undo_import))
        val autoBackup = ThemeColorPreferenceCategory(requireContext()).apply {
            setTitle(R.string.settings_transfer_auto_backup)
            isIconSpaceReserved = false
            preferenceScreen.addPreference(this)
        }
        listOf(
            R.string.settings_transfer_auto_backup_periodic,
            R.string.settings_transfer_auto_backup_change,
            R.string.settings_transfer_auto_backup_operation,
        ).forEach { titleRes ->
            laterVersion(ThemeColorSwitchPreference(requireContext()).apply {
                setTitle(titleRes)
                layoutResource = R.layout.preference_custom
                isPersistent = false
                isChecked = false
                autoBackup.addPreference(this)
            })
        }
    }

    private fun preference(group: PreferenceGroup, titleRes: Int, action: (() -> Unit)? = null): Preference = MaterialPreference(requireContext()).apply {
        setTitle(titleRes)
        layoutResource = R.layout.preference_custom
        isSingleLineTitle = false
        isPersistent = false
        if (action != null) setOnPreferenceClickListener { action(); true }
        group.addPreference(this)
    }

    private fun laterVersion(preference: Preference) = preference.apply {
        setSummary(R.string.settings_transfer_later_version)
        isEnabled = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val state = wizardState
        when {
            state != null -> startWizard(state)
            startExport -> startWizard(exportState())
        }
        startExport = false
    }

    override fun requestDocument(fileName: String) {
        document = CompletableDeferred()
        createDocument.launch(fileName)
    }

    override suspend fun awaitDocument(): Uri? = document.await()

    private fun exportState() = SettingsExportWizard.initialState(MiscStateKeys.SETTINGS_EXPORT_CATEGORIES.storedOrNull())

    private fun startWizard(state: SettingsWizardState) {
        if (wizardJob?.isActive == true) return
        val wizard = when (state.kind) {
            SettingsWizardState.Kind.EXPORT -> SettingsExportWizard(this)
        }
        wizardJob = viewLifecycleOwner.lifecycleScope.launch { wizard.run(state) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        wizardState?.let { outState.putString(STATE_WIZARD, it.toText()) }
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val STATE_WIZARD = "settings_wizard_state"
    }
}
