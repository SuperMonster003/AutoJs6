package org.autojs.autojs.ui.wizard

import android.content.Context
import org.autojs.autojs.core.pref.keys.WizardKeys

/**
 * Persists the custom profiles, the last chosen profile and the "show done entries"
 * option of one wizard in a dedicated preference file, the named store of [keys]
 * (docs/settings/ROADMAP.md P3.3, names unchanged). The [Context] parameters are kept for the
 * callers; the store is opened by the settings layer with the application context.
 *
 * zh-CN: 在独立的偏好文件中保存某个向导的自定义配置, 上次选择的配置以及 "显示已完成条目" 选项; 文件即 [keys] 的命名存储
 * (ROADMAP P3.3, 名称不变).
 */
@Suppress("UNUSED_PARAMETER")
class WizardProfileStore(
    private val keys: WizardKeys.WizardProfileKeys,
    membersKey: String,
    private val showDoneByDefault: Boolean = true,
) {

    private val codec = WizardProfileCodec(membersKey)

    fun getCustomProfiles(context: Context): List<WizardProfile> =
        codec.decode(keys.customProfiles.storedText())

    fun saveCustomProfiles(context: Context, profiles: List<WizardProfile>) {
        keys.customProfiles.setStoredText(codec.encode(profiles))
    }

    fun addCustomProfile(context: Context, profile: WizardProfile) {
        require(profile.isCustom) { "Only custom profiles can be stored" }
        saveCustomProfiles(context, getCustomProfiles(context).filterNot { it.id == profile.id } + profile)
    }

    fun updateCustomProfile(context: Context, profile: WizardProfile) {
        require(profile.isCustom) { "Only custom profiles can be stored" }
        val existing = getCustomProfiles(context)
        val updated = if (existing.any { it.id == profile.id }) {
            existing.map { if (it.id == profile.id) profile else it }
        } else {
            existing + profile
        }
        saveCustomProfiles(context, updated)
    }

    fun deleteCustomProfile(context: Context, profileId: String) {
        saveCustomProfiles(context, getCustomProfiles(context).filterNot { it.id == profileId })
        if (getLastProfileId(context) == profileId) {
            setLastProfileId(context, null)
        }
    }

    fun getLastProfileId(context: Context): String? = keys.lastProfileId.storedOrNull()

    fun setLastProfileId(context: Context, profileId: String?) {
        // A null id removes the key, as before.
        keys.lastProfileId.set(profileId)
    }

    fun isShowDone(context: Context): Boolean = keys.showDone.storedOrNull() ?: showDoneByDefault

    fun setShowDone(context: Context, show: Boolean) {
        keys.showDone.set(show)
    }

}
