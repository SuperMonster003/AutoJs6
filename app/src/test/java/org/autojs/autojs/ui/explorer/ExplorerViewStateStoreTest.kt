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
    fun `page state is a json object and the comma separated text of older versions is still read`() {
        val page = ExplorerStoredPageState("/workspace/a,b", 3, directoriesCollapsed = false, filesCollapsed = true)
        assertEquals(
            mapOf("path" to "/workspace/a,b", "scrollPosition" to 3, "directoriesCollapsed" to false, "filesCollapsed" to true),
            ExplorerViewStateStore.encodePageState(page),
        )
        // docs/settings/ROADMAP.md appendix C Q40: items written before are "path,scroll,directories collapsed,files collapsed".
        assertEquals(page, ExplorerViewStateStore.decodePageState("/workspace/a,b,3,false,true"))
        // Numbers parsed from JSON text may be Long or Double.
        assertEquals(page, ExplorerViewStateStore.decodePageState(mapOf("path" to "/workspace/a,b", "scrollPosition" to 3.0, "directoriesCollapsed" to false, "filesCollapsed" to true)))
    }

    @Test
    fun `page state codec ignores malformed persisted entries`() {
        assertNull(ExplorerViewStateStore.decodePageState(mapOf("path" to "/workspace", "scrollPosition" to 1, "directoriesCollapsed" to true)))
        assertNull(ExplorerViewStateStore.decodePageState(mapOf("path" to "", "scrollPosition" to 1, "directoriesCollapsed" to true, "filesCollapsed" to false)))
        assertNull(ExplorerViewStateStore.decodePageState(mapOf("path" to "/workspace", "scrollPosition" to 1.5, "directoriesCollapsed" to true, "filesCollapsed" to false)))
        assertNull(ExplorerViewStateStore.decodePageState(12))
        assertNull(ExplorerViewStateStore.decodePageState(null))
        assertNull(ExplorerViewStateStore.decodePageState(""))
        assertNull(ExplorerViewStateStore.decodePageState("/workspace,not-a-number,true,false"))
        assertNull(ExplorerViewStateStore.decodePageState("/workspace,1,yes,false"))
        assertNull(ExplorerViewStateStore.decodePageState("/workspace,1,true"))
    }
}
