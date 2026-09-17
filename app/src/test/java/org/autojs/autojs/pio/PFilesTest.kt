package org.autojs.autojs.pio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PFilesTest {

    @Test
    fun displayPathsKeepTheRootLabelAndActualEntryName() {
        val root = "/storage/emulated/0/Scripts"
        assertEquals("\$cwd/TestQ/media-library/main.cjs", PFiles.getElegantPath("$root/TestQ/media-library/main.cjs", root, true))
        assertEquals("\$cwd/nested/index.mjs", PFiles.getElegantPath("$root/nested/index.mjs", "$root/", true))
        assertEquals("\$cwd", PFiles.getElegantPath(root, "$root/", true))
        assertEquals("/main.cjs", PFiles.getElegantPath("$root/main.cjs", root, false))
        assertEquals("\$cwd/etc/config", PFiles.getElegantPath("/etc/config", "/", true))
    }

    @Test
    fun directoryMatchingRequiresACompletePathComponent() {
        assertNull(PFiles.relativePathWithin("/ScriptsBackup/main.cjs", "/Scripts"))
        assertNull(PFiles.relativePathWithin("/Scripts2/main.cjs", "/Scripts/"))
        assertNull(PFiles.relativePathWithin("/storage/emulated/01/main.cjs", "/storage/emulated/0"))
        assertNull(PFiles.relativePathWithin("content://provider/Scripts/main.cjs", "/Scripts"))
        assertNull(PFiles.relativePathWithin("relative/main.cjs", "/"))
        assertNull(PFiles.relativePathWithin("/Scripts/main.cjs", ""))
        assertNull(PFiles.relativePathWithin("/Scripts/main.cjs", null))
        assertEquals("/子目录/a b.cjs", PFiles.relativePathWithin("/Scripts/子目录/a b.cjs", "/Scripts///"))
    }

    @Test
    fun typeScriptDeclarationNameUsesCompoundExtension() {
        assertEquals("abc", PFiles.getNameWithoutExtension("abc.d.ts"))
        assertEquals("abc", PFiles.getNameWithoutExtension("C:\\tmp\\abc.d.ts"))
        assertEquals("abc", PFiles.getNameWithoutExtension("/tmp/abc.d.mts"))
        assertEquals("abc", PFiles.getNameWithoutExtension("/tmp/abc.d.cts"))
        assertEquals("abc.d", PFiles.getNameWithoutExtension("abc.d.txt"))
    }
}
