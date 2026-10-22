package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs6.R

/**
 * The preferences of "Manage AutoJs6 settings" (docs/settings/ROADMAP.md D4 / T10), written like `ShortcutBackupFragment`:
 * "Import settings" and "Export settings" on top, then "Restore default settings..." (P8.1) and "Undo last import"
 * (enabled while the last import or reset can be undone, its summary names it), then the automatic backup (D13 / D22,
 * P9: `fragment_settings_auto_backup`, the switches of its three mechanisms and the period). The page hosts the wizards
 * ([SettingsWizardHost]) and resumes a running one after it is recreated.
 *
 * zh-CN: "管理 AutoJs6 设置" 的偏好页 (ROADMAP D4 / T10), 写法同 `ShortcutBackupFragment`: 顶部为 "导入设置" 与 "导出设置",
 * 其下 "恢复默认设置..." (P8.1) 与 "撤销上次导入" (上次导入或恢复默认可撤销时可用, 摘要说明是哪次操作), 然后是自动备份 (D13 / D22,
 * P9: `fragment_settings_auto_backup`, 三种机制的开关与周期). 页面承载向导 ([SettingsWizardHost]), 重建后恢复运行中的向导.
 */
class SettingsTransferFragment : PreferenceFragmentCompat(), SettingsWizardHost {

    override val hostActivity: FragmentActivity
        get() = requireActivity()

    override var wizardState: SettingsWizardState? = null

    private var wizardJob: Job? = null
    private var startWizard: String? = null
    private lateinit var undoPreference: Preference

    // A new page instance gets the result of a request made by the one before it (rotation, process death).
    // zh-CN: 新的页面实例接收前一个实例发出的请求的结果 (旋转屏幕, 进程被回收).
    private var document = CompletableDeferred<Uri?>()

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        document.complete(uri)
    }

    private val openDocument = registerForActivityResult(OpenSettingsFile()) { uri ->
        document.complete(uri)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        wizardState = SettingsWizardState.fromText(savedInstanceState?.getString(STATE_WIZARD))
        if (savedInstanceState == null) {
            startWizard = requireActivity().intent.getStringExtra(SettingsTransferActivity.EXTRA_WIZARD)
            // No wizard runs yet: a copy left by an import that ended with its process is stale.
            SettingsImporter.clearCache(requireContext())
        }
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        preference(preferenceScreen, R.string.settings_transfer_description).apply { isSelectable = false }
        preference(preferenceScreen, R.string.settings_transfer_import) { startWizard(SettingsImportWizard.initialState()) }
            .setSummary(R.string.settings_transfer_import_summary)
        preference(preferenceScreen, R.string.settings_transfer_export) { startWizard(exportState()) }
            .setSummary(R.string.settings_transfer_export_summary)
        preference(preferenceScreen, R.string.settings_transfer_reset) { startWizard(SettingsResetWizard.initialState()) }
            .setSummary(R.string.settings_transfer_reset_summary)
        undoPreference = preference(preferenceScreen, R.string.settings_transfer_undo_import) { startWizard(SettingsUndoWizard.initialState()) }.apply {
            isEnabled = false
        }
        // The switches and the period are `general` settings, followed by AndroidSettingsAutoBackup wherever they change.
        addPreferencesFromResource(R.xml.fragment_settings_auto_backup)
    }

    private fun preference(group: PreferenceGroup, titleRes: Int, action: (() -> Unit)? = null): Preference = MaterialPreference(requireContext()).apply {
        setTitle(titleRes)
        layoutResource = R.layout.preference_custom
        isSingleLineTitle = false
        isPersistent = false
        if (action != null) setOnPreferenceClickListener { action(); true }
        group.addPreference(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val state = wizardState
        when {
            state != null -> startWizard(state)
            startWizard == SettingsTransferActivity.WIZARD_EXPORT -> startWizard(exportState())
            startWizard == SettingsTransferActivity.WIZARD_IMPORT -> startWizard(SettingsImportWizard.initialState())
        }
        startWizard = null
    }

    override fun onResume() {
        super.onResume()
        refreshUndo()
    }

    /** "Undo last import" names the last import and is enabled while its backup is there. */
    private fun refreshUndo() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val last = withContext(Dispatchers.IO) { SettingsImporter.lastOperation(context) }
            undoPreference.isEnabled = last != null
            undoPreference.summary = SettingsTransferMessages.undoSummary(context, last)
        }
    }

    override fun requestDocument(fileName: String) {
        document = CompletableDeferred()
        createDocument.launch(fileName)
    }

    override fun requestOpenDocument() {
        document = CompletableDeferred()
        openDocument.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    override suspend fun awaitDocument(): Uri? = document.await()

    private fun exportState() = SettingsExportWizard.initialState(MiscStateKeys.SETTINGS_EXPORT_CATEGORIES.storedOrNull())

    private fun startWizard(state: SettingsWizardState) {
        if (wizardJob?.isActive == true) return
        val wizard = when (state.kind) {
            SettingsWizardState.Kind.EXPORT -> SettingsExportWizard(this)
            SettingsWizardState.Kind.IMPORT -> SettingsImportWizard(this)
            SettingsWizardState.Kind.UNDO -> SettingsUndoWizard(this)
            SettingsWizardState.Kind.RESET -> SettingsResetWizard(this)
        }
        wizardJob = viewLifecycleOwner.lifecycleScope.launch {
            wizard.run(state)
            refreshUndo()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        wizardState?.let { outState.putString(STATE_WIZARD, it.toText()) }
        super.onSaveInstanceState(outState)
    }

    /**
     * "Open document" that also shows the internal storage, which the picker of Android 7 hides (its "Downloads" lists
     * only what the download manager fetched, not the files of the Download folder).
     * zh-CN: 同时显示内部存储的 "打开文档": Android 7 的选择器默认隐藏内部存储 (其 "下载" 只列出下载管理器下载的文件, 不含 Download
     * 目录中的文件).
     */
    private class OpenSettingsFile : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).putExtra(EXTRA_SHOW_ADVANCED, true)
    }

    private companion object {
        const val STATE_WIZARD = "settings_wizard_state"

        /** `DocumentsContract.EXTRA_SHOW_ADVANCED`, not in the public SDK. */
        const val EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED"
    }
}
