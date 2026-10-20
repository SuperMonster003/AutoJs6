package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.ui.settings.RetentionLimitSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HistoryKeys] (docs/settings/ROADMAP.md P2.2): the readers of the former `HistoryPrefs` and the retention limit
 * page (`RetentionLimitSettings`) on the declared keys, with the same defaults and stored types as before.
 *
 * zh-CN: [HistoryKeys] (ROADMAP P2.2): 原 `HistoryPrefs` 的读取函数与保留上限页面 (`RetentionLimitSettings`) 改为经键声明,
 * 默认值与存储类型不变.
 */
class HistoryKeysTest : DomainKeysTestBase() {

    @Test
    fun readersReturnTheFormerDefaults() {
        assertEquals(10, HistoryKeys.keys.size)
        assertEquals(20L * 1024 * 1024, HistoryKeys.maxFileSizeToTrackBytes())
        assertEquals(30, HistoryKeys.historyMaxDays())
        assertEquals(50, HistoryKeys.historyMaxVersions())
        assertEquals(200L * 1024 * 1024, HistoryKeys.historyMaxTotalBytesPerFile())
        assertEquals(2L * 1024 * 1024 * 1024, HistoryKeys.historyMaxTotalBytes())
        assertEquals(30, HistoryKeys.trashMaxDays())
        assertEquals(500L * 1024 * 1024, HistoryKeys.trashMaxTotalBytes())
        assertEquals(7, HistoryKeys.draftsMaxDays())
        assertEquals(200L * 1024 * 1024, HistoryKeys.draftsMaxTotalBytes())
        assertFalse(HistoryKeys.deletePermanentlyAlsoClearHistory())
    }

    @Test
    fun readersReadTheStoredValues() {
        store.put(key("key_history_max_days"), 0)
        store.put(key("key_trash_max_total_bytes"), -1L)
        store.put(key("key_delete_permanently_also_clear_history"), true)
        assertEquals(0, HistoryKeys.historyMaxDays())
        assertEquals(-1L, HistoryKeys.trashMaxTotalBytes())
        assertTrue(HistoryKeys.deletePermanentlyAlsoClearHistory())
    }

    @Test
    fun retentionPageReadsAndWritesTheKeys() {
        assertEquals(9, RetentionLimitSettings.specs.size)
        assertEquals(HistoryKeys.keys.dropLast(1).map { it.name }.toSet(), RetentionLimitSettings.specs.map { it.key.name }.toSet())
        val days = RetentionLimitSettings.specForKey(key("key_drafts_max_days"))
        assertNotNull(days)
        assertNull(RetentionLimitSettings.specForKey("key_\$_unknown"))
        assertEquals(7L, days!!.defaultValue)
        assertEquals(7L, RetentionLimitSettings.read(days))
        // Days and counts are stored as Int, bytes as Long, clamped as before.
        RetentionLimitSettings.write(days, Long.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, store.getInt(key("key_drafts_max_days"), 0))
        val bytes = RetentionLimitSettings.specForKey(key("key_history_max_total_bytes"))!!
        assertEquals(2L * 1024 * 1024 * 1024, bytes.defaultValue)
        RetentionLimitSettings.write(bytes, -5L)
        assertEquals(0L, store.getLong(key("key_history_max_total_bytes"), 1L))
        assertEquals(0L, RetentionLimitSettings.read(bytes))
    }
}
