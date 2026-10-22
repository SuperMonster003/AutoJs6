package org.autojs.autojs.core.plugin

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ConcurrentSnapshotTest {

    private val clearer = Executors.newSingleThreadExecutor()

    @After
    fun tearDown() {
        clearer.shutdownNow()
    }

    /**
     * Reports its size, then has another thread clear it before the caller goes on: the window in which C35 lost the only
     * overlay session of an execution.
     */
    private inner class ClearedAfterSize<K, V> : ConcurrentHashMap<K, V>() {
        @Volatile
        var armed = false

        override val size: Int
            get() {
                val count = super.size
                if (armed) {
                    armed = false
                    clearer.submit { clear() }.get(5, TimeUnit.SECONDS)
                }
                return count
            }
    }

    @Test
    fun toListOfAMapViewThrowsWhenAnotherThreadRemovesItsOnlyEntryMeanwhile() {
        val map = ClearedAfterSize<String, String>().apply { put("overlay-1", "session") }
        map.armed = true
        val failure = runCatching { map.values.toList() }.exceptionOrNull()
        assertTrue("toList() should throw, got $failure", failure is NoSuchElementException)
    }

    @Test
    fun theSnapshotOfAMapViewToleratesTheSameRemoval() {
        val map = ClearedAfterSize<String, String>().apply { put("overlay-1", "session") }
        map.armed = true
        assertEquals(1, map.size)
        assertEquals(emptyList<String>(), map.values.concurrentSnapshot())
        map["overlay-2"] = "session"
        map.armed = true
        assertEquals(1, map.size)
        assertEquals(emptyList<String>(), map.keys.concurrentSnapshot().sortedWith(naturalOrder()))
    }

    @Test
    fun theSnapshotDoesNotReadTheSizeFirst() {
        val map = ClearedAfterSize<String, String>().apply { put("overlay-1", "session") }
        map.armed = true
        assertEquals(listOf("session"), map.values.concurrentSnapshot())
        assertTrue("toArray() should not have asked for the size", map.armed)
    }

    @Test
    fun theSnapshotNeverThrowsWhileAnotherThreadAddsAndRemovesTheOnlyEntry() {
        val map = ConcurrentHashMap<String, String>()
        val stop = AtomicBoolean(false)
        val churn = clearer.submit {
            while (!stop.get()) {
                map["only"] = "entry"
                map.clear()
            }
        }
        try {
            repeat(200_000) {
                val snapshot = map.values.concurrentSnapshot()
                assertTrue(snapshot.size <= 1)
            }
        } finally {
            stop.set(true)
            churn.get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun theSnapshotIsACopyOfTheOtherConcurrentCollections() {
        val list = CopyOnWriteArrayList(listOf("a", "b"))
        val queue = ConcurrentLinkedQueue(listOf(1, 2, 3))
        val set = ConcurrentHashMap.newKeySet<String>().apply { add("x") }
        val listCopy = list.concurrentSnapshot()
        val queueCopy = queue.concurrentSnapshot()
        val setCopy = set.concurrentSnapshot()
        list.clear()
        queue.clear()
        set.clear()
        assertEquals(listOf("a", "b"), listCopy)
        assertEquals(listOf(1, 2, 3), queueCopy)
        assertEquals(listOf("x"), setCopy)
    }
}
