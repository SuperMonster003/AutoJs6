package org.autojs.autojs.ui.wizard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persists the custom profiles, the last chosen profile and the "show done entries"
 * option of one wizard in a dedicated preference file.
 *
 * zh-CN: 在独立的偏好文件中保存某个向导的自定义配置, 上次选择的配置以及 "显示已完成条目" 选项.
 */
class WizardProfileStore(
    private val spName: String,
    keyPrefix: String,
    membersKey: String,
    private val showDoneByDefault: Boolean = true,
) {

    private val codec = WizardProfileCodec(membersKey)

    private val keyCustomProfiles = "${keyPrefix}_custom_profiles"
    private val keyLastProfileId = "${keyPrefix}_last_profile_id"
    private val keyShowDone = "${keyPrefix}_show_done"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(spName, Context.MODE_PRIVATE)

    fun getCustomProfiles(context: Context): List<WizardProfile> =
        codec.decode(sp(context).getString(keyCustomProfiles, null))

    fun saveCustomProfiles(context: Context, profiles: List<WizardProfile>) {
        sp(context).edit { putString(keyCustomProfiles, codec.encode(profiles)) }
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

    fun getLastProfileId(context: Context): String? = sp(context).getString(keyLastProfileId, null)

    fun setLastProfileId(context: Context, profileId: String?) {
        sp(context).edit {
            if (profileId == null) remove(keyLastProfileId) else putString(keyLastProfileId, profileId)
        }
    }

    fun isShowDone(context: Context): Boolean = sp(context).getBoolean(keyShowDone, showDoneByDefault)

    fun setShowDone(context: Context, show: Boolean) {
        sp(context).edit { putBoolean(keyShowDone, show) }
    }

}
