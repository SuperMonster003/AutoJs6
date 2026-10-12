package org.autojs.autojs.script

import org.autojs.plugin.autojs4.runtime.api.AutoJs4RuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AutoJs4ScriptSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun javaScriptAndAutoFilesMapToTheirAutoJs4SourceTypes() {
        val js = temporaryFolder.newFile("main.js")
        val auto = temporaryFolder.newFile("record.auto")

        val jsSource = AutoJs4ScriptSource.forFile(js)
        assertEquals(AutoJs4ScriptSource.ENGINE, jsSource.engineName)
        assertEquals(AutoJs4RuntimeContract.SOURCE_TYPE_JAVASCRIPT, jsSource.sourceType)
        assertEquals(js.absoluteFile, jsSource.file)
        assertEquals(js.absoluteFile.parent, jsSource.workingDirectory)
        assertEquals("main", jsSource.name)
        assertEquals("js", jsSource.extension)
        assertNull(jsSource.projectDirectory)

        val autoSource = AutoJs4ScriptSource.forFile(auto)
        assertEquals(AutoJs4RuntimeContract.SOURCE_TYPE_AUTO, autoSource.sourceType)
        assertEquals("auto", autoSource.extension)

        assertTrue(AutoJs4ScriptSource.isEligibleFile(js))
        assertTrue(AutoJs4ScriptSource.isEligibleFile(auto))
        assertFalse(AutoJs4ScriptSource.isEligibleFile(temporaryFolder.newFile("notes.txt")))
        assertFalse(AutoJs4ScriptSource.isEligibleFile(temporaryFolder.newFolder("dir")))
    }

    @Test
    fun projectsResolveTheMainScriptAndUseTheProjectAsWorkingDirectory() {
        val project = temporaryFolder.newFolder("project")
        File(project, "entry.js").writeText("toast('hi');")
        File(project, "project.json").writeText("""{"name":"demo","main":"entry.js","versionName":"1.0.0","versionCode":1,"packageName":"org.example.demo"}""")

        assertTrue(AutoJs4ScriptSource.isEligibleProject(project))
        // ProjectConfig parsing touches android.util.Log, so the JVM test exercises the pure resolver.
        val source = requireNotNull(AutoJs4ScriptSource.resolveProjectMain(project, "entry.js"))
        assertEquals(File(project, "entry.js").absoluteFile, source.file)
        assertEquals(project.absoluteFile, source.projectDirectory)
        assertEquals(project.absolutePath, source.workingDirectory)
        assertEquals(AutoJs4RuntimeContract.SOURCE_TYPE_JAVASCRIPT, source.sourceType)
        assertEquals(AutoJs4ScriptSource.ENGINE, source.engineName)
    }

    @Test
    fun projectsWithoutUsableMainAreRejected() {
        val missingMain = temporaryFolder.newFolder("missing")
        File(missingMain, "project.json").writeText("""{"name":"demo","main":"absent.js"}""")
        assertTrue(AutoJs4ScriptSource.isEligibleProject(missingMain))
        assertNull(AutoJs4ScriptSource.resolveProjectMain(missingMain, "absent.js"))
        assertNull(AutoJs4ScriptSource.resolveProjectMain(missingMain, ""))
        assertNull(AutoJs4ScriptSource.resolveProjectMain(missingMain, null))

        val notAProject = temporaryFolder.newFolder("plain")
        assertFalse(AutoJs4ScriptSource.isEligibleProject(notAProject))
    }
}
