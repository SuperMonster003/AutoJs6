package org.autojs.autojs.storage.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPathsTest {

    private val root = "/storage/emulated/0"

    @Test
    fun normalizePathTrimsTrailingSlashesButKeepsRoot() {
        assertEquals("/storage/emulated/0", HistoryPaths.normalizePath("/storage/emulated/0/"))
        assertEquals("/storage/emulated/0", HistoryPaths.normalizePath(" /storage/emulated/0// "))
        assertEquals("/", HistoryPaths.normalizePath("/"))
        assertEquals("/", HistoryPaths.normalizePath("///"))
    }

    @Test
    fun isUnderInternalStorageRequiresRootOrChild() {
        assertTrue(HistoryPaths.isUnderInternalStorage("/storage/emulated/0", root))
        assertTrue(HistoryPaths.isUnderInternalStorage("/storage/emulated/0/", root))
        assertTrue(HistoryPaths.isUnderInternalStorage("/storage/emulated/0/Scripts/a.js", root))
        assertFalse(HistoryPaths.isUnderInternalStorage("/storage/emulated/0abc/a.js", root))
        assertFalse(HistoryPaths.isUnderInternalStorage("/storage/emulated/10/a.js", root))
        assertFalse(HistoryPaths.isUnderInternalStorage("/data/data/x", root))
    }

    @Test
    fun rootOfAnotherUserIsHonoured() {
        val workProfileRoot = "/storage/emulated/10"
        assertTrue(HistoryPaths.isUnderInternalStorage("/storage/emulated/10/Scripts/a.js", workProfileRoot))
        assertFalse(HistoryPaths.isUnderInternalStorage("/storage/emulated/0/Scripts/a.js", workProfileRoot))
        assertTrue(HistoryPaths.isInternalStorageRoot("/storage/emulated/10/", workProfileRoot))
        assertFalse(HistoryPaths.isInternalStorageRoot(null, workProfileRoot))
    }

    @Test
    fun logicalPathFromFileUri() {
        assertEquals(
            "/storage/emulated/0/Scripts/a.js",
            HistoryPaths.toLogicalPathOrNull("file:///storage/emulated/0/Scripts/a.js", "/storage/emulated/0/Scripts/a.js", root),
        )
    }

    @Test
    fun logicalPathExtractedFromProviderUriContainingRoot() {
        val raw = "content://com.example.provider/root/storage/emulated/0/Scripts/dir/"
        assertEquals("/storage/emulated/0/Scripts/dir", HistoryPaths.toLogicalPathOrNull(raw, "/root/storage/emulated/0/Scripts/dir/", root))
    }

    @Test
    fun logicalPathIsNullOutsideInternalStorage() {
        assertNull(HistoryPaths.toLogicalPathOrNull("content://com.android.externalstorage.documents/document/primary%3AScripts%2Fa.js", "/document/primary:Scripts/a.js", root))
        assertNull(HistoryPaths.toLogicalPathOrNull("file:///data/local/tmp/a.js", "/data/local/tmp/a.js", root))
        assertNull(HistoryPaths.toLogicalPathOrNull("content://x", null, root))
    }

    @Test
    fun logicalPathRejectsSiblingPrefixOfRoot() {
        assertNull(HistoryPaths.toLogicalPathOrNull("file:///storage/emulated/0abc/a.js", "/storage/emulated/0abc/a.js", root))
    }
}
