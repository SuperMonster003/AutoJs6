package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs6.R

/**
 * The export wizard (docs/settings/ROADMAP.md T10, D4 / D6): choose the categories, then the save location (P6.4).
 * zh-CN: 导出向导 (ROADMAP T10, D4 / D6): 选择分类, 然后选择保存位置 (P6.4).
 */
class SettingsExportWizard(host: SettingsWizardHost) : SettingsWizard(host) {

    override val titleRes: Int = R.string.settings_transfer_export

    override val steps: List<SettingsWizardStep> = listOf(
        SettingsCategoryPickerStep(),
        LaterVersionStep(SettingsWizardState.Step.CHOOSE_LOCATION),
    )

    /** Steps of later batches show a notice with "Back" and "Close". zh-CN: 后续批次的步骤显示提示, 带 "上一步" 与 "关闭". */
    private class LaterVersionStep(id: SettingsWizardState.Step) : SettingsWizardStep(id) {
        override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
            val builder = dialog(wizard, state, R.string.dialog_button_close).content(R.string.settings_transfer_later_version)
            return navigate(wizard, state, builder) { null }
        }
    }

    companion object {

        /** The state the wizard starts with: the default selection of appendix A.1. zh-CN: 向导的初始状态: 附录 A.1 的默认勾选. */
        @JvmStatic
        fun initialState(): SettingsWizardState = SettingsWizardState(
            kind = SettingsWizardState.Kind.EXPORT,
            step = SettingsWizardState.Step.PICK_CATEGORIES,
            selected = SettingCategory.ALL.filter { it.defaultSelected && !it.isData }.map { it.id },
        )
    }
}
