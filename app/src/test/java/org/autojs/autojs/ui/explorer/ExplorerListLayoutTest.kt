package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExplorerListLayoutTest {

    @Test
    fun `expanded groups map categories and entries consistently`() {
        val layout = ExplorerListLayout(
            directoryCount = 2,
            fileCount = 3,
            directoriesCollapsed = false,
            filesCollapsed = false,
        )

        assertEquals(7, layout.itemCount)
        assertEquals(0, layout.directoryCategoryPosition)
        assertEquals(3, layout.fileCategoryPosition)
        assertEquals(ExplorerListRow.Category(directories = true), layout.rowAt(0))
        assertEquals(ExplorerListRow.Entry(directories = true, index = 1), layout.rowAt(2))
        assertEquals(ExplorerListRow.Category(directories = false), layout.rowAt(3))
        assertEquals(ExplorerListRow.Entry(directories = false, index = 2), layout.rowAt(6))
        assertEquals(2, layout.positionOf(directories = true, index = 1))
        assertEquals(6, layout.positionOf(directories = false, index = 2))
    }

    @Test
    fun `collapsed directory group moves file category directly after it`() {
        val layout = ExplorerListLayout(
            directoryCount = 4,
            fileCount = 2,
            directoriesCollapsed = true,
            filesCollapsed = false,
        )

        assertEquals(4, layout.itemCount)
        assertEquals(1, layout.fileCategoryPosition)
        assertEquals(ExplorerListRow.Category(directories = true), layout.rowAt(0))
        assertEquals(ExplorerListRow.Category(directories = false), layout.rowAt(1))
        assertEquals(ExplorerListRow.Entry(directories = false, index = 0), layout.rowAt(2))
    }

    @Test
    fun `collapsed file group retains category but hides file entries`() {
        val layout = ExplorerListLayout(
            directoryCount = 1,
            fileCount = 5,
            directoriesCollapsed = false,
            filesCollapsed = true,
        )

        assertEquals(3, layout.itemCount)
        assertEquals(2, layout.fileCategoryPosition)
        assertEquals(ExplorerListRow.Category(directories = false), layout.rowAt(2))
        assertThrows(IllegalArgumentException::class.java) { layout.rowAt(3) }
    }
}
