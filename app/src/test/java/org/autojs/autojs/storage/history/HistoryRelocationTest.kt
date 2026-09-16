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
}
