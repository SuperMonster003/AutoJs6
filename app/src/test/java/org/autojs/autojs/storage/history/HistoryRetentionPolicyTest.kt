package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRetentionPolicyTest {

    private data class Item(val id: String, val createdAt: Long, val size: Long)

    private val now = 1_800_000_000_000L
    private val day = HistoryRetentionPolicy.DAY_MS

    private fun oldestFirst(vararg items: Item) = items.sortedBy { it.createdAt }

    @Test
    fun zeroOrNegativeMeansUnlimited() {
        assertTrue(HistoryRetentionPolicy.isUnlimited(0))
        assertTrue(HistoryRetentionPolicy.isUnlimited(-1))
        assertTrue(HistoryRetentionPolicy.isUnlimited(0L))
        assertTrue(HistoryRetentionPolicy.isUnlimited(-5L))
        assertEquals(false, HistoryRetentionPolicy.isUnlimited(1))
        assertEquals(false, HistoryRetentionPolicy.isUnlimited(1L))
        assertNull(HistoryRetentionPolicy.expiredBeforeOrNull(now, 0))
        assertEquals(now - 3 * day, HistoryRetentionPolicy.expiredBeforeOrNull(now, 3))
    }

    @Test
    fun expiredSelectionUsesStrictBefore() {
        val boundary = Item("boundary", now - 2 * day, 1)
        val older = Item("older", now - 2 * day - 1, 1)
        val newer = Item("newer", now - day, 1)
        val items = oldestFirst(boundary, older, newer)

        assertEquals(listOf(older), HistoryRetentionPolicy.selectExpired(items, now, 2) { it.createdAt })
        assertEquals(emptyList<Item>(), HistoryRetentionPolicy.selectExpired(items, now, 0) { it.createdAt })
    }

    @Test
    fun overCountDropsOldestOnly() {
        val items = oldestFirst(
            Item("a", now - 3, 1),
            Item("b", now - 2, 1),
            Item("c", now - 1, 1),
        )
        assertEquals(listOf("a", "b"), HistoryRetentionPolicy.selectOverCount(items, 1).map { it.id })
        assertEquals(emptyList<Item>(), HistoryRetentionPolicy.selectOverCount(items, 3))
        assertEquals(emptyList<Item>(), HistoryRetentionPolicy.selectOverCount(items, 0))
    }

    @Test
    fun overBytesDropsOldestUntilWithinLimit() {
        val items = oldestFirst(
            Item("a", now - 3, 40),
            Item("b", now - 2, 40),
            Item("c", now - 1, 40),
        )
        assertEquals(listOf("a", "b"), HistoryRetentionPolicy.selectOverBytes(items, 50) { it.size }.map { it.id })
        assertEquals(listOf("a"), HistoryRetentionPolicy.selectOverBytes(items, 80) { it.size }.map { it.id })
        assertEquals(emptyList<Item>(), HistoryRetentionPolicy.selectOverBytes(items, 120) { it.size })
        assertEquals(emptyList<Item>(), HistoryRetentionPolicy.selectOverBytes(items, 0) { it.size })
    }

    @Test
    fun overBytesHonoursExternalTotalForWindowedLists() {
        val window = oldestFirst(Item("a", now - 3, 10), Item("b", now - 2, 10))
        // Total of the full set is 100, limit 85: two oldest from the window must go.
        assertEquals(listOf("a", "b"), HistoryRetentionPolicy.selectOverBytes(window, 85, 100L) { it.size }.map { it.id })
        // Limit 95: only one.
        assertEquals(listOf("a"), HistoryRetentionPolicy.selectOverBytes(window, 95, 100L) { it.size }.map { it.id })
    }

    @Test
    fun combinedSelectionAppliesDaysThenCountThenBytesWithoutDuplicates() {
        val items = oldestFirst(
            Item("expired", now - 40 * day, 10),
            Item("old1", now - 5 * day, 10),
            Item("old2", now - 4 * day, 10),
            Item("mid", now - 3 * day, 10),
            Item("new", now - day, 10),
        )
        val result = HistoryRetentionPolicy.selectToDelete(
            itemsOldestFirst = items,
            now = now,
            maxDays = 30,
            maxCount = 3,
            maxBytes = 25,
            createdAt = { it.createdAt },
            sizeOf = { it.size },
        )
        // days: expired; count (4 left, max 3): old1; bytes (30 left, max 25): old2.
        assertEquals(listOf("expired", "old1", "old2"), result.map { it.id })
        assertEquals(result.size, result.distinct().size)
    }

    @Test
    fun combinedSelectionWithAllUnlimitedKeepsEverything() {
        val items = oldestFirst(Item("a", now - 400 * day, 1_000_000), Item("b", now, 1_000_000))
        val result = HistoryRetentionPolicy.selectToDelete(items, now, 0, 0, 0L, { it.createdAt }, { it.size })
        assertTrue(result.isEmpty())
    }
}
