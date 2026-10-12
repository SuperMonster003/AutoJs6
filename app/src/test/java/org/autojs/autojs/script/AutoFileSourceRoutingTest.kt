package org.autojs.autojs.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutoFileSourceRoutingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun autoFileNameDetectionIsCaseInsensitiveAndSuffixBound() {
        assertTrue(AutoFileSource.isAutoFileName("record.auto"))
        assertTrue(AutoFileSource.isAutoFileName("Record.AUTO"))
        assertFalse(AutoFileSource.isAutoFileName("record.auto.js"))
        assertFalse(AutoFileSource.isAutoFileName("auto"))
        assertFalse(AutoFileSource.isAutoFileName(null))
    }

    @Test
    fun fileRawPathAndPersistedPathLaunchesRouteAutoFilesToTheRootAutomatorEngine() {
        val file = temporaryFolder.newFile("record.auto")
        val fallback = temporaryFolder.newFolder("fallback")

        val fileLaunch = ScriptLaunchSourceFactory.createFileLaunch(
            file,
            workingDirectory = null,
            defaultWorkingDirectory = fallback.path,
        )
        val pathLaunch = ScriptLaunchSourceFactory.createPathLaunch(
            file.path,
            workingDirectory = null,
            defaultWorkingDirectory = fallback.path,
        )
        val persistedLaunch = ScriptLaunchSourceFactory.createPersistedPathLaunch(
            path = file.path,
            workingDirectory = null,
            defaultWorkingDirectory = fallback.path,
        )

        listOf(fileLaunch, pathLaunch, persistedLaunch).forEach { launch ->
            assertTrue(launch.source is AutoFileSource)
            assertEquals(AutoFileSource.ENGINE, launch.engineName)
            assertEquals("auto", launch.source.extension)
            assertEquals(file.canonicalFile.parent, launch.workingDirectory)
        }
    }

    @Test
    fun autoFileRejectsJavaScriptPreExecuteWithoutFallingBackToJavaScript() {
        val file = temporaryFolder.newFile("record.auto")

        val error = assertThrows(IllegalArgumentException::class.java) {
            ScriptLaunchSourceFactory.createPathLaunch(
                path = file.path,
                preExecuteScript = "console.log('must not run')",
                workingDirectory = file.parent,
                defaultWorkingDirectory = checkNotNull(file.parent),
            )
        }

        assertTrue(error.message.orEmpty().contains("Auto file"))
        assertTrue(error.message.orEmpty().contains("preExecuteScript"))
    }
}
