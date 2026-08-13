package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LuaFileTypeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun luaFilesAreExecutableAndEditableAfterTheProxyGatePasses() {
        val file = temporaryFolder.newFile("main.LUA")
        val type = FileUtils.TYPE.determineBy(file)

        assertEquals(FileUtils.TYPE.LUA, type)
        assertTrue(type.isExecutable())
        assertTrue(type.isTextEditable())
    }
}
