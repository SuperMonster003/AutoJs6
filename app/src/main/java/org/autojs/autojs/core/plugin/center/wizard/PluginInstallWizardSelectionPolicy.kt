package org.autojs.autojs.core.plugin.center.wizard

/**
 * Pure selection rules of the plugin install wizard, kept free of Android types
 * so they can be unit tested.
 *
 * zh-CN: 插件安装向导的纯选择规则, 不依赖 Android 类型以便单元测试.
 */
object PluginInstallWizardSelectionPolicy {

    data class Candidate(
        val packageName: String,
        val inStandard: Boolean,
        val inMinimal: Boolean,
        val selectable: Boolean,
    )

    enum class NameValidation {
        OK,
        BLANK,
        DUPLICATE,
    }

    /** Installed plugins and index entries without an APK cannot be ticked. */
    fun isSelectable(isInstalled: Boolean, hasInstallableApk: Boolean): Boolean = !isInstalled && hasInstallableApk

    /** Members of [profile] among the selectable [candidates], in candidate order. */
    fun resolveSelection(
        profile: PluginInstallWizardProfile,
        candidates: List<Candidate>,
    ): Set<String> = candidates
        .asSequence()
        .filter { it.selectable }
        .filter { candidate ->
            when (profile.kind) {
                PluginInstallWizardProfileKind.STANDARD -> candidate.inStandard
                PluginInstallWizardProfileKind.MINIMAL -> candidate.inMinimal
                PluginInstallWizardProfileKind.ALL -> true
                PluginInstallWizardProfileKind.CUSTOM -> candidate.packageName in profile.packageNames
            }
        }
        .mapTo(LinkedHashSet()) { it.packageName }

    fun validateCustomName(
        name: String,
        existing: List<PluginInstallWizardProfile>,
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
