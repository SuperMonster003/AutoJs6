package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.SettingEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GeneralKeys] (docs/settings/ROADMAP.md P2.1): the readings that `DocsUtils`, the restart entries of `IntentUtils`,
 * the scheduled restart dialog, `LauncherIconPreference`, the drawer switch of the foreground service and the
 * former `Pref.isQuickRestartEnabled` / `Pref.isScheduledRestartPreferredWorkManager` (removed in P14.1) forward to.
 *
 * zh-CN: [GeneralKeys] (ROADMAP P2.1): `DocsUtils`, `IntentUtils` 的重启入口, 计划重启对话框, `LauncherIconPreference`, 前台
 * 服务抽屉开关, 以及原 `Pref.isQuickRestartEnabled` / `Pref.isScheduledRestartPreferredWorkManager` (P14.1 已删除) 所转发的读法.
 */
class GeneralKeysTest : DomainKeysTestBase() {

    @Test
    fun keysAndEffects() {
        assertEquals(10, GeneralKeys.keys.size)
        assertEquals(SettingEffect.APPLY, GeneralKeys.LAUNCHER_ICON.effect)
        assertEquals(SettingEffect.APPLY, GeneralKeys.FOREGROUND_SERVICE.effect)
        assertEquals(SettingEffect.NONE, GeneralKeys.RESTART_STRATEGY.effect)
        // AndroidSettingsAutoBackup follows the automatic backup keys from any source, so they have no effect of their own.
        GeneralKeys.SETTINGS_AUTO_BACKUP_KEYS.forEach { assertEquals(it.name, SettingEffect.NONE, it.effect) }
    }

    @Test
    fun automaticBackupSwitchesAndPeriod() {
        // T20: the operation backup is on by default, the periodic and change backups off.
        assertTrue(GeneralKeys.SETTINGS_AUTO_BACKUP_OPERATION.get())
        assertFalse(GeneralKeys.SETTINGS_AUTO_BACKUP_PERIODIC.get())
        assertFalse(GeneralKeys.SETTINGS_AUTO_BACKUP_CHANGE.get())
        // Weekly by default (appendix C Q32).
        assertEquals(key("key_settings_auto_backup_period_weekly"), GeneralKeys.SETTINGS_AUTO_BACKUP_PERIOD.get())
        assertEquals(7, GeneralKeys.settingsAutoBackupPeriodDays)
        GeneralKeys.SETTINGS_AUTO_BACKUP_PERIOD.set(key("key_settings_auto_backup_period_daily"))
        assertEquals(1, GeneralKeys.settingsAutoBackupPeriodDays)
        GeneralKeys.SETTINGS_AUTO_BACKUP_PERIOD.set(key("key_settings_auto_backup_period_monthly"))
        assertEquals(30, GeneralKeys.settingsAutoBackupPeriodDays)
        // A stored key out of the candidates counts as the default.
        store.put(GeneralKeys.SETTINGS_AUTO_BACKUP_PERIOD.name, "key_\$_settings_auto_backup_period_removed")
        assertEquals(7, GeneralKeys.settingsAutoBackupPeriodDays)
    }

    @Test
    fun quickRestartComparesTheStoredKey() {
        // The default strategy is quick (R.string.default_key_restart_strategy).
        assertTrue(GeneralKeys.isQuickRestartEnabled)
        GeneralKeys.RESTART_STRATEGY.set(key("key_restart_strategy_scheduled"))
        assertFalse(GeneralKeys.isQuickRestartEnabled)
        GeneralKeys.RESTART_STRATEGY.set(key("key_restart_strategy_quick"))
        assertTrue(GeneralKeys.isQuickRestartEnabled)
        // A key out of the candidates is not quick, as with the old string comparison (R2).
        store.put(GeneralKeys.RESTART_STRATEGY.name, "key_\$_restart_strategy_removed")
        assertFalse(GeneralKeys.isQuickRestartEnabled)
    }

    @Test
    fun scheduledRestartBackendAndDelay() {
        assertTrue(GeneralKeys.isScheduledRestartPreferredWorkManager)
        GeneralKeys.SCHEDULED_RESTART_BACKEND.set(key("key_scheduled_restart_backend_alarm_manager"))
        assertFalse(GeneralKeys.isScheduledRestartPreferredWorkManager)
        store.put(GeneralKeys.SCHEDULED_RESTART_BACKEND.name, "key_\$_scheduled_restart_backend_removed")
        assertFalse(GeneralKeys.isScheduledRestartPreferredWorkManager)
        assertEquals("key_\$_scheduled_restart_backend_removed", GeneralKeys.SCHEDULED_RESTART_BACKEND.storedKeyOrDefault())

        assertEquals(300, GeneralKeys.SCHEDULED_RESTART_DELAY.get())
        GeneralKeys.SCHEDULED_RESTART_DELAY.set(1200)
        assertEquals(1200, GeneralKeys.SCHEDULED_RESTART_DELAY.get())
    }

    @Test
    fun documentationSourceIsReadAsTheStoredKey() {
        // DocsUtils.currentSource: a missing key is offline, like the local source.
        assertNull(GeneralKeys.DOCUMENTATION_SOURCE.storedKey())
        assertEquals(key("key_documentation_source_local"), GeneralKeys.DOCUMENTATION_SOURCE.get())
        GeneralKeys.DOCUMENTATION_SOURCE.set(key("key_documentation_source_online"))
        assertEquals(key("key_documentation_source_online"), GeneralKeys.DOCUMENTATION_SOURCE.storedKey())
    }

    @Test
    fun launcherIconAndForegroundService() {
        assertNull(GeneralKeys.LAUNCHER_ICON.storedKey())
        GeneralKeys.LAUNCHER_ICON.set(key("key_launcher_icon_transparent_background"))
        assertEquals(key("key_launcher_icon_transparent_background"), store.getString(key("key_launcher_icon"), null))

        // The drawer switch reads a missing value as off (DrawerMenuToggleableItem).
        assertFalse(GeneralKeys.FOREGROUND_SERVICE.get())
        assertNull(GeneralKeys.FOREGROUND_SERVICE.storedOrNull())
        GeneralKeys.FOREGROUND_SERVICE.set(true)
        assertTrue(store.getBoolean(key("key_foreground_service"), false))
        GeneralKeys.FOREGROUND_SERVICE.remove()
        assertFalse(GeneralKeys.FOREGROUND_SERVICE.isStored())
    }
}
