package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerRefreshRowTest {

    @Test
    fun sameEntryPathKeepsRowIdentity() {
        val before = ExplorerRefreshRow.entry(isDir = false, path = "/sdcard/a.js", fingerprint = 1L)
        val after = ExplorerRefreshRow.entry(isDir = false, path = "/sdcard/a.js", fingerprint = 2L)
        assertTrue(before.sameItemAs(after))
        assertFalse(before.sameContentAs(after))
    }

    @Test
    fun unchangedMetadataKeepsRowContent() {
        val fingerprint = ExplorerRefreshRow.fingerprintOf(lastModified = 1_700_000_000_000L, size = 2_048L)
        val before = ExplorerRefreshRow.entry(isDir = false, path = "/sdcard/a.js", fingerprint = fingerprint)
        val after = ExplorerRefreshRow.entry(isDir = false, path = "/sdcard/a.js", fingerprint = fingerprint)
        assertTrue(before.sameItemAs(after))
        assertTrue(before.sameContentAs(after))
    }

    @Test
    fun differentPathsAreDifferentRows() {
        val a = ExplorerRefreshRow.entry(isDir = false, path = "/sdcard/a.js", fingerprint = 1L)
        val b = ExplorerRefreshRow.entry(isDir = false, path = "/sdcard/b.js", fingerprint = 1L)
        assertFalse(a.sameItemAs(b))
    }

    @Test
    fun directoryAndFileWithSamePathAreDifferentRows() {
        val dir = ExplorerRefreshRow.entry(isDir = true, path = "/sdcard/x", fingerprint = 1L)
        val file = ExplorerRefreshRow.entry(isDir = false, path = "/sdcard/x", fingerprint = 1L)
        assertFalse(dir.sameItemAs(file))
    }

    @Test
    fun categoryIdentityIsIndependentOfCount() {
        val before = ExplorerRefreshRow.category(isDir = true, count = 3)
        val after = ExplorerRefreshRow.category(isDir = true, count = 5)
        assertTrue(before.sameItemAs(after))
        assertFalse(before.sameContentAs(after))
    }

    @Test
    fun categoriesOfDifferentKindsAreDifferentRows() {
        val dirCategory = ExplorerRefreshRow.category(isDir = true, count = 3)
        val fileCategory = ExplorerRefreshRow.category(isDir = false, count = 3)
        assertFalse(dirCategory.sameItemAs(fileCategory))
    }

    @Test
    fun categoryNeverMatchesEntryWithEmptyPath()  {
        val category = ExplorerRefreshRow.category(isDir = true, count = 0)
        val entry = ExplorerRefreshRow.entry(isDir = true, path = "", fingerprint = 0L)
        assertFalse(category.sameItemAs(entry))
    }

    @Test
    fun metadataChangesChangeFingerprint() {
        val base = ExplorerRefreshRow.fingerprintOf(lastModified = 1_700_000_000_000L, size = 2_048L)
        assertNotEquals(base, ExplorerRefreshRow.fingerprintOf(lastModified = 1_700_000_000_001L, size = 2_048L))
        assertNotEquals(base, ExplorerRefreshRow.fingerprintOf(lastModified = 1_700_000_000_000L, size = 2_049L))
    }

    @Test
    fun unknownFingerprintNeverCollidesWithComputedOnes() {
        assertEquals(
            ExplorerRefreshRow.FINGERPRINT_UNKNOWN + 1,
            ExplorerRefreshRow.fingerprintOf(
                lastModified = Long.MIN_VALUE / 31,
                size = Long.MIN_VALUE % 31,
            ),
        )
        assertNotEquals(
            ExplorerRefreshRow.FINGERPRINT_UNKNOWN,
            ExplorerRefreshRow.fingerprintOf(lastModified = 0L, size = 0L),
        )
    }

}
