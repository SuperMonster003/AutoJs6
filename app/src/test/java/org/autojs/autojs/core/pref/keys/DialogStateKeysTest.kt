package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs.ui.main.drawer.DrawerMenuToggleableItem
import org.autojs.autojs.ui.settings.AboutActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P4.1: [DialogStateKeys], the "do not show again" states of `NotAskAgainDialog`, with the
 * names their callers used (R1) and the reads and writes of the dialog (default true, false after "do not show again",
 * one write each, R2).
 *
 * zh-CN: ROADMAP P4.1: `NotAskAgainDialog` 的 "不再提示" 状态 [DialogStateKeys], 名称沿用调用方 (R1), 读写与对话框一致
 * (默认 true, 勾选 "不再提示" 后为 false, 每次一次写入, R2).
 */
class DialogStateKeysTest : DomainKeysTestBase() {

    @Test
    fun namesAreThoseTheCallersUsed() {
        val byResource = mapOf(
            DialogStateKeys.DIALOG_NUM_OF_LOG_ENTRIES_EXCEEDS_LIMIT_FOR_SENDING to "key_dialog_num_of_log_entries_exceeds_limit_for_sending",
            DialogStateKeys.DIALOG_SELECTED_ABI_IS_UNAVAILABLE to "key_dialog_selected_abi_is_unavailable",
            DialogStateKeys.DIALOG_TRANSPARENT_BACKGROUND_LAUNCHER_ICON to "key_dialog_transparent_background_launcher_icon",
            DialogStateKeys.VERSION_HISTORY_RESTORE_DOES_NOT_AUTO_SAVE_TO_DISK to "key_version_history_restore_does_not_auto_save_to_disk",
            DialogStateKeys.TIMED_TASK_EXACT_ALARM_PROMPT to "key_timed_task_backend_alarm",
            DialogStateKeys.DIALOG_FOREGROUND_SVC to "key_dialog_foreground_svc",
            DialogStateKeys.DIALOG_LOG_ENTRIES_EXPORTED to "key_dialog_log_entries_exported",
            DialogStateKeys.DIALOG_LAYOUT_INSPECTOR_EXIT_TIPS to "key_dialog_layout_inspector_exit_tips",
        )
        byResource.forEach { (stateKey, resName) -> assertEquals(resName, key(resName), stateKey.name) }
        assertEquals("key_\$_plugin_abi_mismatch_download_prompt", DialogStateKeys.PLUGIN_ABI_MISMATCH_DOWNLOAD_PROMPT.name)
        assertEquals("key_\$_plugin_abi_mismatch_enable_prompt", DialogStateKeys.PLUGIN_ABI_MISMATCH_ENABLE_PROMPT.name)
        assertEquals("key_\$_plugin_hide_confirm_prompt", DialogStateKeys.PLUGIN_HIDE_CONFIRM_PROMPT.name)
        assertEquals("editor.debug.long_click_hint", DialogStateKeys.EDITOR_DEBUG_LONG_CLICK_HINT.name)
        // Appendix C Q23: a fixed name instead of the one made from the stack trace.
        assertEquals("key_\$_ip_address_may_be_invalid_for_server_connection_prompt", DialogStateKeys.IP_ADDRESS_MAY_BE_INVALID_FOR_SERVER_CONNECTION_PROMPT.name)
        // AboutActivity and DrawerMenuToggleableItem built these names from their simple class names.
        assertEquals(AboutActivity::class.java.simpleName + ".start_feedback_activity", DialogStateKeys.ABOUT_START_FEEDBACK_ACTIVITY.name)
        assertEquals(DrawerMenuToggleableItem::class.java.simpleName + "\$", DialogStateKeys.DRAWER_TOGGLE_PROMPT.prefix)
        assertEquals("prompt_\$_", DialogStateKeys.LIST_PREFERENCE_PROMPT.prefix)
        // MaterialListPreference used "prompt_$_${key}", DrawerMenuToggleableItem "<class>$<title>".
        assertEquals("prompt_\$_key_\$_launcher_icon", DialogStateKeys.LIST_PREFERENCE_PROMPT.key("key_\$_launcher_icon").name)
        assertEquals("DrawerMenuToggleableItem\$Floating button", DialogStateKeys.DRAWER_TOGGLE_PROMPT.key("Floating button").name)
    }

    @Test
    fun everyKeyIsAStateThatDefaultsToShown() {
        val keys = DialogStateKeys.keys + DialogStateKeys.families.map { it.template }
        assertEquals(16, keys.size)
        keys.forEach {
            assertTrue(it.name, it is StateKey<*>)
            assertEquals(it.name, SettingKind.STATE, it.kind)
            assertEquals(it.name, SettingCategory.OTHER, it.category)
            assertEquals(it.name, true, it.get())
            assertEquals(it.name, "true", it.defaultText)
        }
        DialogStateKeys.keys.forEach { assertSame(it.name, it, SettingsRegistry.find(it.name)) }
        DialogStateKeys.families.forEach { assertSame(it.prefix, it, SettingsRegistry.findFamily(it.prefix + "x")) }
    }

    @Test
    fun aCandidateValueAlsoNamesTheExactAlarmPrompt() {
        // key_timed_task_backend_alarm is a candidate of the timed task backend and the name of the exact alarm prompt.
        val name = key("key_timed_task_backend_alarm")
        val rows = SettingsRegistry.describe().filter { it.store == "default" && it.name == name }
        assertEquals(setOf(SettingKind.VALUE_CONSTANT, SettingKind.STATE), rows.map { it.kind }.toSet())
        assertTrue(TimedTaskKeys.TIMED_TASK_BACKEND.candidates.contains(name))
        TimedTaskKeys.TIMED_TASK_BACKEND.setStoredKey(name)
        DialogStateKeys.TIMED_TASK_EXACT_ALARM_PROMPT.set(false)
        assertEquals(name, store.getString(key("key_timed_task_backend"), null))
        assertFalse(store.getBoolean(name, true))
    }

    @Test
    fun readsAndWritesAsTheDialogDid() {
        // NotAskAgainDialog read getBoolean(key, true) and wrote putBoolean(key, !checked) with apply().
        val key = DialogStateKeys.PLUGIN_HIDE_CONFIRM_PROMPT
        assertTrue(key.get())
        assertFalse(store.contains(key.name))
        val commits = store.commitCount
        key.set(false)
        assertFalse(store.getBoolean("key_\$_plugin_hide_confirm_prompt", true))
        assertEquals(commits + 1, store.commitCount)
        assertFalse(key.get())
        key.set(true)
        assertTrue(store.getBoolean("key_\$_plugin_hide_confirm_prompt", false))
        // A family member and a name no declaration names (the deprecated string constructor, stack trace keys).
        DialogStateKeys.LIST_PREFERENCE_PROMPT.key("key_\$_launcher_icon").set(false)
        assertFalse(store.getBoolean("prompt_\$_key_\$_launcher_icon", true))
        assertEquals(listOf("prompt_\$_key_\$_launcher_icon"), DialogStateKeys.LIST_PREFERENCE_PROMPT.storedNames())
        val hash = "0123456789abcdef0123456789abcdef"
        assertTrue(DialogStateKeys.forName(hash).get())
        store.put(hash, false)
        assertFalse(DialogStateKeys.forName(hash).get())
        assertEquals(null, SettingsRegistry.find(hash))
    }
}
