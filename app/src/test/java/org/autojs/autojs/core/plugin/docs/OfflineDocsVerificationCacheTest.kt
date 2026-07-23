package org.autojs.autojs.core.plugin.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDocsVerificationCacheTest {

    @Test
    fun `verification invalidated in flight cannot repopulate the next generation`() {
        val cache = OfflineDocsVerificationCache<String>()
        val oldGeneration = cache.snapshotGeneration()

        cache.invalidate()

        assertFalse(cache.putIfCurrent(oldGeneration, IDENTITY, "old inventory"))
        val currentGeneration = cache.snapshotGeneration()
        assertNull(cache.get(currentGeneration, IDENTITY))

        assertTrue(cache.putIfCurrent(currentGeneration, IDENTITY, "new inventory"))
        assertEquals("new inventory", cache.get(currentGeneration, IDENTITY))
        assertNull(cache.get(oldGeneration, IDENTITY))
    }

    private companion object {
        const val IDENTITY = "official-docs@same-logical-identity"
    }
}
