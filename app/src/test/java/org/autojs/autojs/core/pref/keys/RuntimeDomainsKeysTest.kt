package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The domains of docs/settings/ROADMAP.md P2.4: [TimedTaskKeys], [A11yKeys], [RunningKeys], [RecordKeys],
 * [ExtensibilityKeys] and [UpdatesKeys], with the readings that the deprecated `Pref` members forward to.
 *
 * zh-CN: ROADMAP P2.4 的领域: 定时任务, 无障碍, 脚本运行, 录制, 扩展性, 更新, 以及已弃用的 `Pref` 成员所转发的读法.
 */
class RuntimeDomainsKeysTest : DomainKeysTestBase() {

    @Test
    fun timedTaskBackendIsReadAsTheStoredKey() {
        // TimedTaskScheduler.init rejects a key out of the candidates, so the raw key is what it reads.
        assertEquals(key("key_timed_task_backend_alarm"), TimedTaskKeys.TIMED_TASK_BACKEND.storedKeyOrDefault())
        assertEquals(SettingEffect.APPLY, TimedTaskKeys.TIMED_TASK_BACKEND.effect)
        store.put(TimedTaskKeys.TIMED_TASK_BACKEND.name, "key_\$_timed_task_backend_removed")
        assertEquals("key_\$_timed_task_backend_removed", TimedTaskKeys.TIMED_TASK_BACKEND.storedKeyOrDefault())
    }

    @Test
    fun accessibilitySwitchesKeepTheirResourceDefaults() {
        assertTrue(A11yKeys.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS.get())
        assertTrue(A11yKeys.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS.get())
        assertTrue(A11yKeys.ENABLE_A11Y_SERVICE_WITH_SHIZUKU.get())
        assertFalse(A11yKeys.STABLE_MODE.get())
        assertFalse(A11yKeys.GESTURE_OBSERVING.get())
        A11yKeys.STABLE_MODE.set(true)
        assertTrue(store.getBoolean(key("key_stable_mode"), false))
    }

    @Test
    fun runningKeys() {
        assertTrue(RunningKeys.USE_VOLUME_CONTROL_RUNNING.get())
        assertFalse(RunningKeys.GUARD_MODE.get())
        assertEquals(key("key_root_mode_auto_detect"), RunningKeys.ROOT_MODE.storedKeyOrDefault())
        // The delay is kept within the minimum (0) and maximum (2000) resources.
        assertEquals(350, RunningKeys.screenCaptureRequestDelay)
        RunningKeys.SCREEN_CAPTURE_REQUEST_DELAY.set(5000)
        assertEquals(2000, RunningKeys.screenCaptureRequestDelay)
        RunningKeys.SCREEN_CAPTURE_REQUEST_DELAY.set(-5)
        assertEquals(0, RunningKeys.screenCaptureRequestDelay)
        assertEquals(emptySet<String>(), RunningKeys.ROOT_MANAGER_APPS_CUSTOM.get())
        RunningKeys.ROOT_MANAGER_APPS_CUSTOM.set(setOf("com.topjohnwu.magisk"))
        assertEquals(setOf("com.topjohnwu.magisk"), store.getStringSet(key("key_root_manager_apps_custom"), null))
        // The list found on the device is a cache: no category, never exported.
        assertEquals(SettingKind.CACHE, RunningKeys.ROOT_MANAGER_APPS_CACHE.kind)
        assertNull(RunningKeys.ROOT_MANAGER_APPS_CACHE.category)
        assertNull(RunningKeys.ROOT_MANAGER_APPS_CACHE.storedOrNull())
    }

    @Test
    fun recordKeys() {
        assertTrue(RecordKeys.USE_VOLUME_CONTROL_RECORD.get())
        assertTrue(RecordKeys.RECORD_TOAST.get())
        assertTrue(RecordKeys.rootRecordGeneratesBinary())
        RecordKeys.ROOT_RECORD_OUT_FILE_TYPE.set(key("key_root_record_out_file_type_js"))
        assertFalse(RecordKeys.rootRecordGeneratesBinary())
        store.put(RecordKeys.ROOT_RECORD_OUT_FILE_TYPE.name, "key_\$_root_record_out_file_type_removed")
        assertFalse(RecordKeys.rootRecordGeneratesBinary())
    }

    @Test
    fun extensibilityKeys() {
        assertFalse(ExtensibilityKeys.EXTENDING_JS_BUILD_IN_OBJECTS.get())
        assertTrue(ExtensibilityKeys.RHINO_JAVA_PRIMITIVE_WRAP.get())
    }

    @Test
    fun ignoredUpdatesKeepTheGsonText() {
        assertTrue(UpdatesKeys.AUTO_CHECK_FOR_UPDATES.get())
        assertEquals(linkedSetOf<String>(), UpdatesKeys.ignoredUpdates())
        UpdatesKeys.setIgnoredUpdates(linkedSetOf("v6.8.0 <beta>", "v6.7.0"))
        // The text that Pref.putLinkedHashSet (Gson) wrote, byte for byte (P1.4).
        assertEquals(SettingsJson.encodeStringList(listOf("v6.8.0 <beta>", "v6.7.0")), store.getString(key("key_ignored_updates"), null))
        assertEquals(listOf("v6.8.0 <beta>", "v6.7.0"), UpdatesKeys.ignoredUpdates().toList())
        assertEquals(listOf("v6.8.0 <beta>", "v6.7.0"), UpdatesKeys.IGNORED_UPDATES.get())
        // Malformed text throws as Pref.getLinkedHashSet did, while the typed key falls back to the default.
        store.put(key("key_ignored_updates"), "[not json")
        assertThrows(SettingsJson.SettingsJsonException::class.java) { UpdatesKeys.ignoredUpdates() }
        assertEquals(emptyList<String>(), UpdatesKeys.IGNORED_UPDATES.get())
    }
}
