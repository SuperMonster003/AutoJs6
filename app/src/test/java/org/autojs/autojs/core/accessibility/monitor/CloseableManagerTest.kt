package org.autojs.autojs.core.accessibility.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.Closeable

class CloseableManagerTest {

    @Test
    fun recycleAllAllowsCloseablesToRemoveThemselves() {
        val manager = CloseableManager()
        val closed = mutableListOf<Int>()
        lateinit var first: Closeable
        lateinit var second: Closeable
        first = Closeable {
            closed += 1
            manager.remove(first)
        }
        second = Closeable {
            closed += 2
            manager.remove(second)
        }
        manager.add(first)
        manager.add(second)

        manager.recycleAll()
        manager.recycleAll()

        assertEquals(listOf(1, 2), closed)
    }

    @Test
    fun recycleAllClosesEverythingBeforeRethrowingFailures() {
        val manager = CloseableManager()
        val firstFailure = IllegalStateException("first")
        val secondFailure = IllegalArgumentException("second")
        val closed = mutableListOf<Int>()
        manager.add(Closeable {
            closed += 1
            throw firstFailure
        })
        manager.add(Closeable {
            closed += 2
            throw secondFailure
        })
        manager.add(Closeable { closed += 3 })

        val thrown = runCatching { manager.recycleAll() }.exceptionOrNull()

        assertSame(firstFailure, thrown)
        assertEquals(listOf(1, 2, 3), closed)
        assertEquals(listOf(secondFailure), thrown?.suppressed?.toList())
    }
}
