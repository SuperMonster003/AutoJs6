package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerPathSegmentBuilderTest {

    @Test
    fun internalStoragePathProducesClickableAncestorsAndCurrentDirectory() {
        val segments = ExplorerPathSegmentBuilder.build(
            currentPath = "/storage/emulated/0/Scripts/utils",
            rootPath = "/storage/emulated/0",
            rootLabel = "Internal Storage",
        )

        assertEquals(
            listOf("Internal Storage", "Scripts", "utils"),
            segments.map(ExplorerPathSegment::label),
        )
        assertEquals(
            listOf(
                "/storage/emulated/0",
                "/storage/emulated/0/Scripts",
                "/storage/emulated/0/Scripts/utils",
            ),
            segments.map(ExplorerPathSegment::path),
        )
    }

    @Test
    fun rootPathProducesOnlyTheHighlightedCurrentSegment() {
        val segments = ExplorerPathSegmentBuilder.build(
            currentPath = "/storage/emulated/0/",
            rootPath = "/storage/emulated/0",
            rootLabel = "Internal Storage",
        )

        assertEquals(listOf(ExplorerPathSegment("Internal Storage", "/storage/emulated/0")), segments)
    }

    @Test
    fun similarlyPrefixedSiblingIsNotTreatedAsDescendant() {
        assertFalse(
            ExplorerPathSegmentBuilder.isSameOrDescendant(
                path = "/storage/emulated/01/Scripts",
                rootPath = "/storage/emulated/0",
            ),
        )
        assertTrue(
            ExplorerPathSegmentBuilder.isSameOrDescendant(
                path = "/storage/emulated/0/Scripts",
                rootPath = "/storage/emulated/0/",
            ),
        )
    }

    @Test
    fun filesystemRootSupportsPathsOutsideSharedStorage() {
        assertEquals("/", ExplorerPathSegmentBuilder.filesystemRoot("/data/user/0/app/files"))
        assertEquals("C:", ExplorerPathSegmentBuilder.filesystemRoot("C:\\Scripts\\utils"))
    }

    @Test
    fun directoryNamesKeepMeaningfulWhitespace() {
        val segments = ExplorerPathSegmentBuilder.build(
            currentPath = "/storage/emulated/0/Scripts/ spaced folder ",
            rootPath = "/storage/emulated/0",
            rootLabel = "Internal Storage",
        )

        assertEquals(" spaced folder ", segments.last().label)
        assertEquals("/storage/emulated/0/Scripts/ spaced folder ", segments.last().path)
    }
}
