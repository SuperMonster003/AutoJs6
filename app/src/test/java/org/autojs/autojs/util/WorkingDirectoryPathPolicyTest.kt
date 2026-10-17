package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingDirectoryPathPolicyTest {

    @Test
    fun `legacy values with a leading slash still refer to internal storage`() {
        assertEquals("$storage/Scripts", resolve("Scripts"))
        assertEquals("$storage/Scripts", resolve("/Scripts"))
        assertEquals(storage, resolve(""))
        assertEquals(storage, resolve("/"))
        assertEquals("$storage/Scripts/utils", resolve("//Scripts//utils/"))
        // Existing untagged values are not reinterpreted as a new absolute-path format.
        assertEquals("$storage/storage/USB/Scripts", resolve("/storage/USB/Scripts"))
    }

    @Test
    fun `explicit removable storage path survives persistence without an internal prefix`() {
        val path = "/storage/1234-5678/Scripts"
        val stored = WorkingDirectoryPathPolicy.encodeAbsolute(path, storage)

        assertEquals("file:///storage/1234-5678/Scripts", stored)
        assertEquals(path, resolve(stored))
        assertEquals(path, WorkingDirectoryPathPolicy.toRelativePath(resolve(stored), storage))
        assertEquals(path, WorkingDirectoryPathPolicy.displayStored(stored))
    }

    @Test
    fun `returning to internal storage removes the absolute marker`() {
        val external = WorkingDirectoryPathPolicy.encodeAbsolute("/storage/USB/Scripts", storage)
        assertTrue(external.startsWith("file:///"))

        val internal = WorkingDirectoryPathPolicy.encodeAbsolute("$storage/Scripts", storage)
        assertEquals("/Scripts", internal)
        assertNull(WorkingDirectoryPathPolicy.decodeAbsolute(internal))
        assertEquals("$storage/Scripts", resolve(internal))
        assertEquals("", WorkingDirectoryPathPolicy.encodeAbsolute(storage, storage))
        assertEquals(storage, resolve(""))
    }

    @Test
    fun `storage prefix requires a directory boundary`() {
        val sibling = "/storage/emulated/01/Scripts"
        assertFalse(WorkingDirectoryPathPolicy.isWithinStorage(sibling, storage))
        assertEquals(sibling, WorkingDirectoryPathPolicy.toRelativePath(sibling, storage))
        assertEquals(sibling, resolve(WorkingDirectoryPathPolicy.encodeAbsolute(sibling, storage)))
        assertTrue(WorkingDirectoryPathPolicy.isWithinStorage(storage, storage))
        assertTrue(WorkingDirectoryPathPolicy.isWithinStorage("$storage/Scripts", "$storage/"))
    }

    @Test
    fun `absolute encoding round trips characters meaningful to URI syntax`() {
        val path = "/storage/USB/腳本 space/#100%/it's here"
        val stored = WorkingDirectoryPathPolicy.encodeAbsolute(path, storage)

        assertTrue(stored.contains("%20"))
        assertTrue(stored.contains("%23"))
        assertTrue(stored.contains("%25"))
        assertEquals(path, resolve(stored))
        assertEquals(path, WorkingDirectoryPathPolicy.displayStored(stored))
    }

    @Test
    fun `selected external path keeps its meaning when confirming settings or history`() {
        val path = "/storage/USB/Scripts"
        val stored = WorkingDirectoryPathPolicy.encodeAbsolute(path, storage)
        val displayed = WorkingDirectoryPathPolicy.displayStored(stored)
        val selected = WorkingDirectoryPathPolicy.decodeAbsolute(stored)

        assertEquals(path, WorkingDirectoryPathPolicy.resolveInput(displayed, storage, selected))
        assertEquals(path, WorkingDirectoryPathPolicy.resolveInput(stored, storage))
        assertEquals("$storage/Other", WorkingDirectoryPathPolicy.resolveInput("/Other", storage))
        assertEquals("/Other", WorkingDirectoryPathPolicy.resolveInput("/Other", storage, selected))
    }

    @Test
    fun `editing a selected external directory preserves absolute input semantics`() {
        val selected = "/storage/USB/Scripts"
        assertEquals("/storage/USB/Scripts2", WorkingDirectoryPathPolicy.resolveInput("/storage/USB/Scripts2", storage, selected))
        assertEquals("$storage/Scripts", WorkingDirectoryPathPolicy.resolveInput("Scripts", storage, selected))
        assertEquals("$storage/Scripts", WorkingDirectoryPathPolicy.resolveInput("/Scripts", storage))
    }

    @Test
    fun `internal picker results and old relative history keep existing input semantics`() {
        assertEquals("$storage/Scripts", WorkingDirectoryPathPolicy.resolveInput("/Scripts", storage, "$storage/Scripts"))
        assertEquals("$storage/Scripts", WorkingDirectoryPathPolicy.resolveInput("/Scripts", storage))
        assertEquals("$storage/Scripts", WorkingDirectoryPathPolicy.resolveInput("$storage/Scripts", storage))
        assertEquals("/Scripts", WorkingDirectoryPathPolicy.displayStored("/Scripts"))
        assertNull(WorkingDirectoryPathPolicy.decodeAbsolute("/Scripts"))
    }

    @Test
    fun `invalid and remote URI values are not accepted as local absolute paths`() {
        assertNull(WorkingDirectoryPathPolicy.decodeAbsolute("file://server/storage/Scripts"))
        assertNull(WorkingDirectoryPathPolicy.decodeAbsolute("file:///storage/%zz"))
        assertNull(WorkingDirectoryPathPolicy.decodeAbsolute("file:///storage/Scripts?query"))
        assertNull(WorkingDirectoryPathPolicy.decodeAbsolute("file:///storage/Scripts#fragment"))
        assertNull(WorkingDirectoryPathPolicy.decodeAbsolute("file:///storage/%00"))
    }

    private fun resolve(value: String) = WorkingDirectoryPathPolicy.resolveStored(value, storage)

    private val storage = "/storage/emulated/0"
}
