package org.autojs.autojs.ui.settings.transfer

import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs6.R

/**
 * Step 1 of the export wizard (docs/settings/ROADMAP.md T10): the categories of appendix A.1 in page order, checkable.
 * The data categories (D21) stay disabled until their codecs exist (P10). Every change of the selection is published
 * to the host at once, so it survives the recreation of the page.
 *
 * zh-CN: 导出向导第 1 步 (ROADMAP T10): 按页面顺序列出附录 A.1 的分类供勾选. 数据分类 (D21) 在其编解码完成 (P10) 之前不可选.
 * 勾选每次变化都立即写入宿主, 因此页面重建后仍保留.
 */
class SettingsCategoryPickerStep : SettingsWizardStep(SettingsWizardState.Step.PICK_CATEGORIES) {

    override suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState? {
        val activity = wizard.host.hostActivity
        val categories = SettingCategory.ALL
        var current = state.copy(selected = categories.filter { it.id in state.selected && !it.isData }.map { it.id })
        wizard.host.wizardState = current
        val builder = dialog(wizard, state)
            .content(R.string.settings_transfer_pick_export)
            .items(categories.map { activity.getString(it.titleRes) })
            .itemsCallbackMultiChoice(categories.indices.filter { categories[it].id in current.selected }.toTypedArray()) { dialog, which, _ ->
                current = current.copy(selected = which.sorted().map { categories[it].id })
                wizard.host.wizardState = current
                dialog.getActionButton(DialogAction.POSITIVE).isEnabled = current.selected.isNotEmpty()
                true
            }
            .alwaysCallMultiChoiceCallback()
            .itemsDisabledIndices(*categories.indices.filter { categories[it].isData }.toTypedArray())
            .choiceWidgetThemeColor()
            .showListener { dialog ->
                (dialog as MaterialDialog).getActionButton(DialogAction.POSITIVE).isEnabled = current.selected.isNotEmpty()
            }
        return navigate(wizard, state, builder) { wizard.next(current) }
    }
}
