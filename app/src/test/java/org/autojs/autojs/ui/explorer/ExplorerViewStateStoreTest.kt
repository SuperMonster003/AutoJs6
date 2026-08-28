package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerViewStateStoreTest {

    @Test
    fun `page state codec preserves navigation fields`() {
        val original = ExplorerStoredPageState(
            path = "/workspace/project,archive",
            scrollPosition = 12,
            directoriesCollapsed = true,
            filesCollapsed = false,
        )

        val restored = ExplorerViewStateStore.decodePageState(
            ExplorerViewStateStore.encodePageState(original),
        )!!

        assertEquals(original.path, restored.path)
        assertEquals(12, restored.scrollPosition)
        assertTrue(restored.directoriesCollapsed)
        assertFalse(restored.filesCollapsed)
    }

    @Test
    fun `page state codec ignores malformed persisted entries`() {
        assertNull(ExplorerViewStateStore.decodePageState(""))
        assertNull(ExplorerViewStateStore.decodePageState("/workspace,not-a-number,true,false"))
        assertNull(ExplorerViewStateStore.decodePageState("/workspace,1,yes,false"))
        assertNull(ExplorerViewStateStore.decodePageState("/workspace,1,true"))
    }
}
