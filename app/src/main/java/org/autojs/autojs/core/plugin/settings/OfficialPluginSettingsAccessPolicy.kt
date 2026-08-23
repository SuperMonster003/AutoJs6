package org.autojs.autojs.core.plugin.settings

internal data class OfficialPluginSettingsCallerFacts(
    val callingPackage: String?,
    val packagesForUid: Set<String>,
    val applicationEnabled: Boolean,
    val protectedPluginEntryEnabled: Boolean,
    val officialSignature: Boolean,
)

internal enum class OfficialPluginSettingsAccessDenial {
    MISSING_CALLING_PACKAGE,
    UID_PACKAGE_MISMATCH,
    APPLICATION_DISABLED,
    PLUGIN_ENTRY_UNAVAILABLE,
    NOT_OFFICIAL,
}

internal object OfficialPluginSettingsAccessPolicy {
    fun denial(facts: OfficialPluginSettingsCallerFacts): OfficialPluginSettingsAccessDenial? {
        val packageName = facts.callingPackage
            ?: return OfficialPluginSettingsAccessDenial.MISSING_CALLING_PACKAGE
        if (packageName !in facts.packagesForUid) {
            return OfficialPluginSettingsAccessDenial.UID_PACKAGE_MISMATCH
        }
        if (!facts.applicationEnabled) {
            return OfficialPluginSettingsAccessDenial.APPLICATION_DISABLED
        }
        if (!facts.protectedPluginEntryEnabled) {
            return OfficialPluginSettingsAccessDenial.PLUGIN_ENTRY_UNAVAILABLE
        }
        if (!facts.officialSignature) {
            return OfficialPluginSettingsAccessDenial.NOT_OFFICIAL
        }
        return null
    }
}
