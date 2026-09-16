package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExportNamingTest {

    @Test
    fun stampGoesBeforeLastExtension() {
        assertEquals("main.20260916-200103.js", HistoryExportNaming.suggestName("main.js", "20260916-200103"))
        assertEquals("archive.tar.20260916-200103.gz", HistoryExportNaming.suggestName("archive.tar.gz", "20260916-200103"))
    }

    @Test
    fun namesWithoutExtensionGetStampAppended() {
        assertEquals("README.20260916-200103", HistoryExportNaming.suggestName("README", "20260916-200103"))
        assertEquals(".bashrc.20260916-200103", HistoryExportNaming.suggestName(".bashrc", "20260916-200103"))
        assertEquals("notes..20260916-200103", HistoryExportNaming.suggestName("notes.", "20260916-200103"))
        assertEquals("revision.20260916-200103", HistoryExportNaming.suggestName("  ", "20260916-200103"))
    }

    @Test
    fun fileNameValidation() {
        assertTrue(HistoryExportNaming.isValidFileName("main.js"))
        assertTrue(HistoryExportNaming.isValidFileName("  spaced name.txt "))
        assertFalse(HistoryExportNaming.isValidFileName(""))
        assertFalse(HistoryExportNaming.isValidFileName("   "))
        assertFalse(HistoryExportNaming.isValidFileName("."))
        assertFalse(HistoryExportNaming.isValidFileName(".."))
        assertFalse(HistoryExportNaming.isValidFileName("dir/main.js"))
        assertFalse(HistoryExportNaming.isValidFileName("dir\\main.js"))
    }
}
