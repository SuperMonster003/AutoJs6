package org.autojs.autojs.ui.settings.transfer

import com.afollestad.materialdialogs.DialogAction
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Kind
import org.autojs.autojs.ui.settings.transfer.SettingsWizardState.Step
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs6.R

/**
 * "Restore default settings..." (docs/settings/ROADMAP.md D11, P8.1): the category step of the other wizards with every
 * category (`other` and the data categories cannot be ticked, nothing is ticked first), a confirmation, the reset
 * itself ([SettingsImporter.reset]: internal backup "auto-reset", removes, effects, operation backup) and the result
 * page. "Undo last import" undoes a reset as well.
 *
 * zh-CN: "恢复默认设置..." (ROADMAP D11, P8.1): 与其他向导相同的分类步骤, 列出全部分类 (`other` 与数据分类不可勾选, 初始不勾选),
 * 然后确认, 恢复默认 ([SettingsImporter.reset]: 内部备份 "auto-reset", 删除, 生效, 操作后备份), 最后是结果页. "撤销上次导入" 同样
 * 可以撤销恢复默认.
 */
class SettingsResetWizard(host: SettingsWizardHost) : SettingsWizard(host) {

    override val titleRes: Int = R.string.settings_transfer_reset

    override val steps: List<SettingsWizardStep> = listOf(
        SettingsCategoryPickerStep(),
        ConfirmStep(),
        SettingsApplyStep(R.string.settings_transfer_reset_applying, SettingsImporter::reset),
        SettingsResultStep(R.string.settings_transfer_reset_done_title),
        SettingsFailedStep(R.string.settings_transfer_reset_failed_title) { it.copy(step = Step.PICK_CATEGORIES) },
    )

    private class ConfirmStep : SettingsWizardStep(Step.CONFIRM) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            val activity = wizard.host.hostActivity
            val action = dialog(wizard, state, R.string.settings_transfer_reset_button)
                .content(activity.getString(R.string.settings_transfer_reset_confirm, state.selected.size))
                .positiveColorRes(R.color.dialog_button_caution)
                .showAndAwait()
            return when (action) {
                DialogAction.POSITIVE -> wizard.next(state)
                DialogAction.NEUTRAL -> wizard.previous(state)
                else -> null
            }
        }
    }

    companion object {

        @JvmStatic
        fun initialState(): SettingsWizardState = SettingsWizardState(Kind.RESET, Step.PICK_CATEGORIES)
    }
}
