package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerNavigationHistoryTest {

    @Test
    fun `unavailable directions do not add phantom history`() {
        val history = ExplorerNavigationHistory<String>()

        assertNull(history.goBack("/"))
        assertNull(history.goForward("/"))
        assertFalse(history.canGoBack)
        assertFalse(history.canGoForward)
    }

    @Test
    fun `back and forward restore complete page state in navigation order`() {
        val home = Page("/home", 8, directoriesCollapsed = true)
        val child = Page("/home/child", 16, filesCollapsed = true)
        val leaf = Page("/home/child/leaf", 24)
        val history = ExplorerNavigationHistory<Page>()
        history.recordNavigation(home)
        history.recordNavigation(child)

        assertSame(child, history.goBack(leaf))
        assertSame(home, history.goBack(child))
        assertFalse(history.canGoBack)
        assertTrue(history.canGoForward)
        assertSame(child, history.goForward(home))
        assertSame(leaf, history.goForward(child))
        assertFalse(history.canGoForward)
        assertEquals(listOf(home, child), history.backwardEntries)
        assertEquals(8, home.scrollPosition)
        assertTrue(home.directoriesCollapsed)
        assertEquals(16, child.scrollPosition)
        assertTrue(child.filesCollapsed)
        assertEquals(24, leaf.scrollPosition)
    }

    @Test
    fun `new navigation after going back discards the old forward branch`() {
        val history = ExplorerNavigationHistory<String>()
        history.recordNavigation("/home")
        history.recordNavigation("/home/old")
        assertEquals("/home/old", history.goBack("/home/old/child"))
        assertEquals("/home", history.goBack("/home/old"))

        history.recordNavigation("/home")

        assertFalse(history.canGoForward)
        assertEquals("/home", history.goBack("/home/new"))
        assertEquals("/home/new", history.goForward("/home"))
    }

    @Test
    fun `archive isolation restores both outer directions without archive pages`() {
        val history = ExplorerNavigationHistory<String>()
        history.recordNavigation("/home")
        history.recordNavigation("/downloads")
        assertEquals("/downloads", history.goBack("/pictures"))
        val outerBackward = history.backwardEntries
        val outerForward = history.forwardEntries

        history.clear()
        history.recordNavigation("archive:/")
        history.recordNavigation("archive:/folder")
        assertEquals("archive:/folder", history.goBack("archive:/folder/child"))
        history.restore(outerBackward, outerForward)

        assertEquals(listOf("/home"), history.backwardEntries)
        assertEquals(listOf("/pictures"), history.forwardEntries)
        assertEquals("/pictures", history.goForward("/downloads"))
        assertEquals(listOf("/home", "/downloads"), history.backwardEntries)
    }

    @Test
    fun `replacing history drops obsolete backward and forward page objects`() {
        val history = ExplorerNavigationHistory<String>()
        history.recordNavigation("old-root")
        history.recordNavigation("old-parent")
        history.goBack("old-child")

        history.restore(listOf("new-root", "new-parent"))

        assertFalse(history.canGoForward)
        assertEquals("new-parent", history.goBack("new-child"))
        assertEquals("new-root", history.goBack("new-parent"))
        assertFalse(history.canGoBack)
    }

    @Test
    fun `clearing a view removes both directions and leaves snapshots independent`() {
        val history = ExplorerNavigationHistory<String>()
        history.recordNavigation("/home")
        history.recordNavigation("/downloads")
        history.goBack("/pictures")
        val previousBackward = history.backwardEntries
        val previousForward = history.forwardEntries

        history.clear()

        assertFalse(history.canGoBack)
        assertFalse(history.canGoForward)
        assertEquals(listOf("/home"), previousBackward)
        assertEquals(listOf("/pictures"), previousForward)
    }

    private data class Page(
        val path: String,
        val scrollPosition: Int,
        val directoriesCollapsed: Boolean = false,
        val filesCollapsed: Boolean = false,
    )
}
