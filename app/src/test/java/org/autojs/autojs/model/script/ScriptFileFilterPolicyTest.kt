package org.autojs.autojs.model.script

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScriptFileFilterPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun scriptPickerIncludesLuaAndExistingScriptKinds() {
        assertTrue(ScriptFileFilterPolicy.accepts(temporaryFolder.newFolder("folder"), pythonEnabled = false))
        assertTrue(ScriptFileFilterPolicy.accepts(temporaryFolder.newFile("main.LUA"), pythonEnabled = false))
        assertTrue(ScriptFileFilterPolicy.accepts(temporaryFolder.newFile("main.js"), pythonEnabled = false))
        assertTrue(ScriptFileFilterPolicy.accepts(temporaryFolder.newFile("Main.JAVA"), pythonEnabled = false))
        assertTrue(ScriptFileFilterPolicy.accepts(temporaryFolder.newFile("Main.KT"), pythonEnabled = false))
        assertTrue(ScriptFileFilterPolicy.accepts(temporaryFolder.newFile("main.AUTO"), pythonEnabled = false))
        assertFalse(ScriptFileFilterPolicy.accepts(temporaryFolder.newFile("notes.txt"), pythonEnabled = true))
    }

    @Test
    fun pythonRemainsControlledByItsHostChannel() {
        val python = temporaryFolder.newFile("main.PY")

        assertFalse(ScriptFileFilterPolicy.accepts(python, pythonEnabled = false))
        assertTrue(ScriptFileFilterPolicy.accepts(python, pythonEnabled = true))
    }
}
