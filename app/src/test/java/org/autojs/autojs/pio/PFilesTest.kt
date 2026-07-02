package org.autojs.autojs.pio

import org.junit.Assert.assertEquals
import org.junit.Test

class PFilesTest {

    @Test
    fun typeScriptDeclarationNameUsesCompoundExtension() {
        assertEquals("abc", PFiles.getNameWithoutExtension("abc.d.ts"))
        assertEquals("abc", PFiles.getNameWithoutExtension("C:\\tmp\\abc.d.ts"))
        assertEquals("abc", PFiles.getNameWithoutExtension("/tmp/abc.d.mts"))
        assertEquals("abc", PFiles.getNameWithoutExtension("/tmp/abc.d.cts"))
        assertEquals("abc.d", PFiles.getNameWithoutExtension("abc.d.txt"))
    }
}
