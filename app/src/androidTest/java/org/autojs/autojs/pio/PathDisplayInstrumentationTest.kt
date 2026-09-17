package org.autojs.autojs.pio

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.script.NodeProjectJavaScriptFileSource
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.ui.main.task.Task
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class PathDisplayInstrumentationTest {

    @Test
    fun taskAndScriptDisplayPathsAbbreviateWithoutChangingExecutionPaths() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workingDirectory = WorkingDirectoryUtils.path
        val entry = File(workingDirectory, "TestQ/media-library/main.cjs")
        val source = NodeProjectJavaScriptFileSource(entry.parentFile!!, entry)
        assertEquals(entry.absolutePath, source.fullPath)
        assertEquals("\$cwd/TestQ/media-library/main.cjs", source.elegantPath)
        val pending = IntentTask().apply { scriptPath = entry.absolutePath }
        assertEquals("\$cwd/TestQ/media-library/main.cjs", Task.PendingTask(context, pending).name)
        assertEquals(entry.absolutePath, pending.scriptPath)
        source.overriddenFullPath = entry.absolutePath
        assertEquals("\$cwd/TestQ/media-library/main.cjs [cache]", source.elegantPath)
    }

    @Test
    fun knownRootsHaveExplicitLabelsAndSimilarDirectoryNamesStayDistinct() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun display(path: String) = PFiles.getElegantPath(path, "/not-the-working-directory", true)
        assertEquals("\$sdcard/Download/main.mjs", display("${EnvironmentUtils.externalStoragePath}/Download/main.mjs"))
        assertEquals("\$sample/main.js", display("${context.filesDir}/sample/main.js"))
        assertEquals("\$files/samples/main.js", display("${context.filesDir}/samples/main.js"))
        assertEquals("\$files/notes.txt", display("${context.filesDir}/notes.txt"))
        assertEquals("\$cache/main.cjs", display("${context.cacheDir}/main.cjs"))
        assertEquals("\$data/cache-other/main.cjs", display("${context.dataDir}/cache-other/main.cjs"))
        assertFalse(PFiles.isCachePrefix("${context.cacheDir}-other/main.cjs"))
        assertEquals("/vendor/config.txt", display("/vendor/config.txt"))
        assertEquals("content://documents/primary%3AScripts%2Fmain.cjs", display("content://documents/primary%3AScripts%2Fmain.cjs"))
    }
}
