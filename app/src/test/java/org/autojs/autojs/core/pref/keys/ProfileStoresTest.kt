package org.autojs.autojs.core.pref.keys

import android.content.Context
import android.content.ContextWrapper
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardProfileStore
import org.autojs.autojs.core.plugin.jvm.JvmSourceProviderSelection
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.network.UpdateIgnoreStore
import org.autojs.autojs.ui.project.BuildConfigStore
import org.autojs.autojs.ui.wizard.WizardProfile
import org.autojs.autojs.ui.wizard.WizardProfileStore
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P3.3: the profile stores on the declared layer ([ApkBuildKeys], [WizardKeys], the JVM
 * source selection of [PluginCenterKeys] and the ignored plugin versions of [UpdatesKeys]): file and key names
 * unchanged (R1), and the moved readers and writers reading and writing what they did (R2).
 *
 * zh-CN: ROADMAP P3.3: 配置档类存储迁移到声明层: 文件名与键名不变 (R1), 迁移后的读写方读写的值与原先一致 (R2).
 */
class ProfileStoresTest : DomainKeysTestBase() {

    private val context: Context = ContextWrapper(null)

    @Test
    fun storesAndCategories() {
        assertEquals(
            listOf("build_apk_config_profiles", "permission_wizard", "plugin_install_wizard", "jvm_source_provider_selection"),
            listOf(NamedStores.BUILD_APK_CONFIG_PROFILES, NamedStores.PERMISSION_WIZARD, NamedStores.PLUGIN_INSTALL_WIZARD, NamedStores.JVM_SOURCE_PROVIDER_SELECTION).map { it.name },
        )
        assertEquals(key("key_ignored_updates"), NamedStores.IGNORED_UPDATES.name)
        val rows = SettingsRegistry.describe()
        fun categories(store: String) = rows.filter { it.store == store }.map { it.category }.toSet()
        assertEquals(setOf("apk_build"), categories("build_apk_config_profiles"))
        assertEquals(setOf("wizards", "other"), categories("permission_wizard"))
        assertEquals(setOf("plugin_center"), categories("jvm_source_provider_selection"))
        assertEquals(setOf("updates"), categories(NamedStores.IGNORED_UPDATES.name))
        // The last chosen profile id is a state; the profiles and options are settings.
        assertEquals(SettingKind.STATE, WizardKeys.PERMISSION_WIZARD.lastProfileId.kind)
        assertEquals(SettingCategory.WIZARDS, WizardKeys.PLUGIN_INSTALL_WIZARD.customProfiles.category)
    }

    @Test
    fun buildProfilesKeepTheirKeys() {
        val build = store(NamedStores.BUILD_APK_CONFIG_PROFILES)
        assertEquals(BuildConfigStore.PROFILE_DEFAULT_ID, BuildConfigStore.getActiveProfileName(context))
        build.put("active_profile", "  ")
        assertEquals("a blank name is the default", BuildConfigStore.PROFILE_DEFAULT_ID, BuildConfigStore.getActiveProfileName(context))
        BuildConfigStore.setActiveProfileName(context, "release")
        assertEquals("release", build.getString("active_profile", null))
        assertEquals("release", BuildConfigStore.getActiveProfileName(context))
    }

    @Test
    fun wizardProfilesAndOptions() {
        val wizard = store(NamedStores.PERMISSION_WIZARD)
        val profiles = WizardProfileStore(WizardKeys.PERMISSION_WIZARD, "permissions")
        assertEquals(emptyList<WizardProfile>(), profiles.getCustomProfiles(context))
        val profile = WizardProfile.custom("Mine", setOf("a11y", "overlay"), id = "custom-1")
        profiles.addCustomProfile(context, profile)
        assertTrue(wizard.getString("key_\$_permission_wizard_custom_profiles", null)!!.contains("\"custom-1\""))
        assertEquals(listOf(profile), profiles.getCustomProfiles(context))
        profiles.setLastProfileId(context, "custom-1")
        assertEquals("custom-1", wizard.getString("key_\$_permission_wizard_last_profile_id", null))
        profiles.deleteCustomProfile(context, "custom-1")
        assertNull(profiles.getLastProfileId(context))
        assertFalse(wizard.contains("key_\$_permission_wizard_last_profile_id"))
        assertTrue(profiles.isShowDone(context))
        profiles.setShowDone(context, false)
        assertFalse(wizard.getBoolean("key_\$_permission_wizard_show_done", true))

        val install = store(NamedStores.PLUGIN_INSTALL_WIZARD)
        assertTrue(PluginInstallWizardProfileStore.showLauncherIcons(context))
        PluginInstallWizardProfileStore.setShowLauncherIcons(context, false)
        assertFalse(install.getBoolean("show_launcher_icons_for_new_plugins", true))
        assertTrue(PluginInstallWizardProfileStore.store.isShowDone(context))
    }

    @Test
    fun jvmSourceSelectionReadsTheExactComponentPerLanguage() {
        val selection = store(NamedStores.JVM_SOURCE_PROVIDER_SELECTION)
        assertEquals("exact_component", JvmSourceProviderSelection.storageKey(JvmSourceLanguage.JAVA))
        assertEquals("exact_component_kotlin", JvmSourceProviderSelection.storageKey(JvmSourceLanguage.KOTLIN))
        assertNull(JvmSourceProviderSelection.getComponent(context, JvmSourceLanguage.KOTLIN))
        assertNull(PluginCenterKeys.JVM_SOURCE_EXACT_COMPONENT_JAVA.storedOrNull())
        selection.put("exact_component", "org.example.jvm/org.example.jvm.Provider")
        assertEquals("org.example.jvm/org.example.jvm.Provider", PluginCenterKeys.JVM_SOURCE_EXACT_COMPONENT_JAVA.storedOrNull())
        assertNull("Kotlin never inherits the Java choice", PluginCenterKeys.JVM_SOURCE_EXACT_COMPONENT_KOTLIN.storedOrNull())
    }

    @Test
    fun ignoredPluginVersions() {
        val ignored = store(NamedStores.IGNORED_UPDATES)
        assertFalse(UpdateIgnoreStore.isIgnored("org.example.plugin", 12))
        UpdateIgnoreStore.ignoreVersion("org.example.plugin", 12, "1.2.0")
        UpdateIgnoreStore.ignoreVersion("org.example.plugin", 13)
        assertEquals(setOf("12|1.2.0", "13"), ignored.getStringSet("key_\$_ignored_plugin_\$_org.example.plugin", null))
        assertTrue(UpdateIgnoreStore.isIgnored("org.example.plugin", 12))
        assertEquals(setOf(12L, 13L), UpdateIgnoreStore.ignoredVersionCodes("org.example.plugin"))
        UpdateIgnoreStore.unignoreVersion("org.example.plugin", 12)
        assertEquals(setOf("13"), ignored.getStringSet("key_\$_ignored_plugin_\$_org.example.plugin", null))
        assertEquals(listOf("key_\$_ignored_plugin_\$_org.example.plugin"), UpdatesKeys.IGNORED_PLUGIN_VERSIONS.storedNames())
    }
}
