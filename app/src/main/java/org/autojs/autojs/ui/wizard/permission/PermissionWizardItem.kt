package org.autojs.autojs.ui.wizard.permission

import org.autojs.autojs.ui.wizard.WizardSelectionPolicy

/**
 * One row of the permission wizard: a drawer permission with its current state.
 * [unavailableReason] explains why an ungranted permission cannot be requested here.
 *
 * zh-CN: 权限申请向导中的一行: 一项抽屉权限及其当前状态.
 * [unavailableReason] 说明未授予的权限为何无法在此申请.
 */
data class PermissionWizardItem(
    val entry: PermissionWizardEntry,
    val isGranted: Boolean,
    val unavailableReason: String? = null,
) {

    val id: String
        get() = entry.id

    val isSelectable: Boolean
        get() = !isGranted && unavailableReason == null

    fun toCandidate() = WizardSelectionPolicy.Candidate(
        id = entry.id,
        inStandard = entry.inStandard,
        inMinimal = entry.inMinimal,
        selectable = isSelectable,
        done = isGranted,
    )

}
