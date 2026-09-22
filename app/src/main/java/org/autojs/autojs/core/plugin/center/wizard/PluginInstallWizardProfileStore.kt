package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persists the wizard's custom profiles, the last chosen profile and the
 * "shown once on first run" flag in a dedicated preference file.
 *
 * zh-CN: 在独立的偏好文件中保存向导的自定义配置, 上次选择的配置以及 "首次启动仅展示一次" 标记.
 */
object PluginInstallWizardProfileStore {

    private const val SP_NAME = "plugin_install_wizard"
    private const val KEY_CUSTOM_PROFILES = "key_\$_plugin_install_wizard_custom_profiles"
    private const val KEY_FIRST_RUN_SHOWN = "key_\$_plugin_install_wizard_first_run_shown"
    private const val KEY_LAST_PROFILE_ID = "key_\$_plugin_install_wizard_last_profile_id"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun getCustomProfiles(context: Context): List<PluginInstallWizardProfile> =
        PluginInstallWizardProfileCodec.decode(sp(context).getString(KEY_CUSTOM_PROFILES, null))

    fun saveCustomProfiles(context: Context, profiles: List<PluginInstallWizardProfile>) {
        sp(context).edit { putString(KEY_CUSTOM_PROFILES, PluginInstallWizardProfileCodec.encode(profiles)) }
    }

    fun addCustomProfile(context: Context, profile: PluginInstallWizardProfile) {
        require(profile.isCustom) { "Only custom profiles can be stored" }
        saveCustomProfiles(context, getCustomProfiles(context).filterNot { it.id == profile.id } + profile)
    }

    fun updateCustomProfile(context: Context, profile: PluginInstallWizardProfile) {
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

    fun isFirstRunShown(context: Context): Boolean = sp(context).getBoolean(KEY_FIRST_RUN_SHOWN, false)

    fun markFirstRunShown(context: Context) {
        sp(context).edit { putBoolean(KEY_FIRST_RUN_SHOWN, true) }
    }

    fun getLastProfileId(context: Context): String? = sp(context).getString(KEY_LAST_PROFILE_ID, null)

    fun setLastProfileId(context: Context, profileId: String?) {
        sp(context).edit {
            if (profileId == null) remove(KEY_LAST_PROFILE_ID) else putString(KEY_LAST_PROFILE_ID, profileId)
        }
    }

}
