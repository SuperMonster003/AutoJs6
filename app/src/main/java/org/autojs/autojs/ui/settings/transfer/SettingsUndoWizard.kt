package org.autojs.autojs.ui.settings.transfer

import com.afollestad.materialdialogs.DialogAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Kind
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * "Undo last import" (docs/settings/ROADMAP.md D8 / T9, P7.5): a confirmation that names the operation and the number
 * of categories it touched (and, until P10.3, the data categories an undo leaves as they are, appendix C Q27), then the
 * undo itself ([SettingsImporter.undo], no backup of its own) and the same result page as an import.
 *
 * zh-CN: "撤销上次导入" (ROADMAP D8 / T9, P7.5): 先确认 (说明是哪次操作与触及的分类数; 在 P10.3 之前还说明撤销不处理的数据分类, 附录 C
 * Q27), 然后撤销 ([SettingsImporter.undo], 不另写备份), 最后是与导入相同的结果页.
 */
class SettingsUndoWizard(host: SettingsWizardHost) : SettingsWizard(host) {

    override val titleRes: Int = R.string.settings_transfer_undo_import

    override val steps: List<SettingsWizardStep> = listOf(
        ConfirmStep(),
        SettingsApplyStep(R.string.settings_transfer_undo_applying) { context, _ -> SettingsImporter.undo(context) },
        SettingsResultStep(R.string.settings_transfer_undo_done_title),
        SettingsFailedStep(R.string.settings_transfer_undo_failed_title),
    )

    private class ConfirmStep : SettingsWizardStep(Step.CONFIRM) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            val activity = wizard.host.hostActivity
            val last = withContext(Dispatchers.IO) { SettingsImporter.lastOperation(activity) }
            if (last == null) {
                ViewUtils.showToast(activity, R.string.settings_transfer_undo_none, true)
                return null
            }
            val action = dialog(wizard, state, R.string.text_undo)
                .content(SettingsTransferMessages.undoConfirmText(activity, last))
                .showAndAwait()
            return if (action == DialogAction.POSITIVE) wizard.next(state) else null
        }
    }

    companion object {

        @JvmStatic
        fun initialState(): SettingsWizardState = SettingsWizardState(Kind.UNDO, Step.CONFIRM)
    }
}
