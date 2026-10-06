package org.autojs.autojs.ui.wizard

/**
 * Pure selection rules shared by the setup wizards, kept free of Android types so
 * they can be unit tested. An entry is "done" once its goal is already reached
 * (a plugin is installed, a permission is granted).
 *
 * zh-CN: 各设置向导共用的纯选择规则, 不依赖 Android 类型以便单元测试.
 * 条目的目标已达成 (插件已安装, 权限已授予) 时视为 "已完成".
 */
object WizardSelectionPolicy {

    data class Candidate(
        val id: String,
        val inStandard: Boolean,
        val inMinimal: Boolean,
        val selectable: Boolean,
        val done: Boolean = false,
    )

    enum class NameValidation {
        OK,
        BLANK,
        DUPLICATE,
    }

    /** How the check box of one row is presented. */
    enum class CheckBoxState {
        /** A done entry outside the current profile shows no check box. */
        HIDDEN,

        /** A done entry of the current profile shows a disabled, ticked check box. */
        DONE_IN_PROFILE,

        /** An entry that cannot be selected shows a disabled, unticked check box. */
        UNAVAILABLE,

        /** The user may tick or untick the entry. */
        SELECTABLE,
    }

    fun isMember(profile: WizardProfile, candidate: Candidate): Boolean = when (profile.kind) {
        WizardProfileKind.STANDARD -> candidate.inStandard
        WizardProfileKind.MINIMAL -> candidate.inMinimal
        WizardProfileKind.ALL -> true
        WizardProfileKind.CUSTOM -> candidate.id in profile.memberIds
    }

    /** Members of [profile] among the selectable [candidates], in candidate order. */
    fun resolveSelection(profile: WizardProfile, candidates: List<Candidate>): Set<String> = candidates
        .asSequence()
        .filter { it.selectable && isMember(profile, it) }
        .mapTo(LinkedHashSet()) { it.id }

    fun checkBoxState(isDone: Boolean, isSelectable: Boolean, isMemberOfProfile: Boolean): CheckBoxState = when {
        isDone -> if (isMemberOfProfile) CheckBoxState.DONE_IN_PROFILE else CheckBoxState.HIDDEN
        isSelectable -> CheckBoxState.SELECTABLE
        else -> CheckBoxState.UNAVAILABLE
    }

    /** Done rows disappear while the "show done entries" option is off. */
    fun isRowVisible(isDone: Boolean, showDone: Boolean): Boolean = showDone || !isDone

    /**
     * Members written when the selection is saved as a profile: the ticked entries, the done
     * members of [profile] that are shown ticked, and custom members unknown to [candidates]
     * (for example plugins missing from the current index), in candidate order.
     */
    fun membersToSave(profile: WizardProfile, candidates: List<Candidate>, selectedIds: Set<String>): Set<String> {
        val members = LinkedHashSet<String>()
        candidates.forEach { candidate ->
            val ticked = candidate.id in selectedIds && candidate.selectable
            val doneMember = candidate.done && isMember(profile, candidate)
            if (ticked || doneMember) members += candidate.id
        }
        if (profile.isCustom) {
            val known = candidates.mapTo(HashSet()) { it.id }
            profile.memberIds.filterTo(members) { it !in known }
        }
        return members
    }

    fun validateCustomName(
        name: String,
        existing: List<WizardProfile>,
        excludingId: String? = null,
    ): NameValidation {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NameValidation.BLANK
        val duplicate = existing.any { profile ->
            profile.isCustom && profile.id != excludingId && profile.customName.equals(trimmed, ignoreCase = true)
        }
        return if (duplicate) NameValidation.DUPLICATE else NameValidation.OK
    }

}
