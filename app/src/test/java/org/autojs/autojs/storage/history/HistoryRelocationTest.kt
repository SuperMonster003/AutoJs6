package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRelocationTest {

    private val root = "/storage/emulated/0"

    @Test
    fun fileRename() {
        assertEquals("$root/b.js", HistoryRelocation.mapPath("$root/a.js", "$root/a.js", "$root/b.js"))
        assertTrue(HistoryRelocation.isAffected("$root/a.js", "$root/a.js"))
    }

    @Test
    fun fileMoveIntoOtherDirectory() {
        assertEquals("$root/dir/sub/a.js", HistoryRelocation.mapPath("$root/a.js", "$root/a.js", "$root/dir/sub/a.js"))
    }

    @Test
    fun directoryMoveRewritesChildren() {
        assertEquals("$root/new/x/y.js", HistoryRelocation.mapPath("$root/old/x/y.js", "$root/old", "$root/new"))
        assertEquals("$root/new", HistoryRelocation.mapPath("$root/old", "$root/old/", "$root/new/"))
        assertTrue(HistoryRelocation.isAffected("$root/old/x/y.js", "$root/old"))
    }

    @Test
    fun siblingsSharingPrefixAreUntouched() {
        assertNull(HistoryRelocation.mapPath("$root/oldish/y.js", "$root/old", "$root/new"))
        assertNull(HistoryRelocation.mapPath("$root/other/a.js", "$root/a.js", "$root/b.js"))
        assertFalse(HistoryRelocation.isAffected("$root/oldish/y.js", "$root/old"))
        assertFalse(HistoryRelocation.isAffected("$root/ol", "$root/old"))
    }

    @Test
    fun trailingSlashesAndWhitespaceAreNormalized() {
        assertEquals("$root/new/a.js", HistoryRelocation.mapPath("$root/old/a.js", " $root/old/ ", "$root/new/"))
    }

    @Test
    fun restoreFromTrashToAnotherDirectory() {
        // A trashed directory restored under a different parent (P3.7) keeps every nested path.
        assertEquals("$root/restored/proj/lib/a.js", HistoryRelocation.mapPath("$root/proj/lib/a.js", "$root/proj", "$root/restored/proj"))
        // Restore with auto-rename: "a.js" becomes "a (1).js".
        assertEquals("$root/a (1).js", HistoryRelocation.mapPath("$root/a.js", "$root/a.js", "$root/a (1).js"))
        // Moving a directory into a new location below its own old name still maps by prefix.
        assertEquals("$root/old/inner/x/y.js", HistoryRelocation.mapPath("$root/old/x/y.js", "$root/old", "$root/old/inner"))
    }

    @Test
    fun samePathMapsToItself() {
        assertEquals("$root/a.js", HistoryRelocation.mapPath("$root/a.js", "$root/a.js/", "$root/a.js"))
        assertEquals("$root/dir/a.js", HistoryRelocation.mapPath("$root/dir/a.js", "$root/dir", "$root/dir/"))
    }
}
