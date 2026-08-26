package org.autojs.autojs.script

import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.timing.TimedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.joda.time.LocalTime
import java.io.File

class AutoJsProjectPersistedLaunchContextInstrumentationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun classicProjectDirectoryRestoresEntryWorkingDirectoryAndFeatures() {
        val root = project(
            """{"name":"Scheduled","packageName":"org.autojs.scheduled","versionName":"1.0.0","versionCode":1,"main":"src/main.js","useFeatures":["continuation"]}""",
            "src/main.js",
        )
        val launch = persistedLaunch(
            root,
            ScriptLaunchSourceFactory.capturePersistedLaunchContext(root.path),
        )
        val source = launch.source as JavaScriptFileSource

        assertEquals(File(root, "src/main.js").canonicalFile, source.file.canonicalFile)
        assertEquals(root.canonicalPath, launch.workingDirectory)
        assertEquals(listOf("continuation"), launch.createExecutionConfig().scriptConfig.features)
        assertNull(launch.nodeProjectLaunchSource)
        assertNull(launch.pythonProjectSource)
    }

    @Test
    fun nodeAndPythonProjectsKeepTheirDedicatedLaunchRoutes() {
        val nodeRoot = project("""{"type":"node","main":"main.cjs"}""", "main.cjs")
        val nodeLaunch = persistedLaunch(
            nodeRoot,
            ScriptLaunchSourceFactory.capturePersistedLaunchContext(nodeRoot.path),
        )
        assertTrue(nodeLaunch.nodeProjectLaunchSource != null)
        assertEquals(nodeRoot.canonicalPath, nodeLaunch.workingDirectory)

        val pythonRoot = project("""{"type":"python","main":"main.py"}""", "main.py")
        val pythonLaunch = persistedLaunch(
            pythonRoot,
            ScriptLaunchSourceFactory.capturePersistedLaunchContext(pythonRoot.path),
            pythonPreviewEnabled = true,
        )
        assertTrue(pythonLaunch.source is PythonProjectSource)
        assertEquals(pythonRoot.canonicalPath, pythonLaunch.workingDirectory)
    }

    @Test
    fun markerBasedRecognitionAlsoCoversMalformedConfigs() {
        val root = project("{ malformed", ProjectConfig.DEFAULT_MAIN_SCRIPT_FILE_NAME)

        val captured = ScriptLaunchSourceFactory.capturePersistedLaunchContext(root.path)
        val launch = persistedLaunch(root, captured)

        assertEquals(PersistedScriptLaunchPolicy.AUTOJS_PROJECT_V1, captured.sourceKind)
        assertTrue(launch.source is JavaScriptFileSource)
    }

    @Test
    fun timedTaskIntentRestoresTheProjectLaunchInsteadOfExecutingTheDirectoryAsAFile() {
        val root = project(
            """{"name":"Scheduled","packageName":"org.autojs.scheduled","versionName":"1.0.0","versionCode":1,"main":"main.js"}""",
            "main.js",
        )
        val task = TimedTask.dailyTask(LocalTime(12, 0), root.path, ExecutionConfig())

        assertEquals(PersistedScriptLaunchPolicy.AUTOJS_PROJECT_V1, task.sourceKind)
        assertEquals(root.canonicalPath, task.projectRoot)

        File(root, "scheduled.js").writeText("// updated scheduled project entry")
        File(root, "project.json").writeText(
            """{"name":"Scheduled","packageName":"org.autojs.scheduled","versionName":"1.0.0","versionCode":1,"main":"scheduled.js","useFeatures":["continuation"]}""",
        )
        val prepared = requireNotNull(ScriptIntents.prepareLaunch(null, task.createIntent()))
        val source = prepared.launch.source as JavaScriptFileSource

        assertEquals(File(root, "scheduled.js").canonicalFile, source.file.canonicalFile)
        assertEquals(root.canonicalPath, prepared.launch.workingDirectory)
        assertEquals(root.canonicalPath, prepared.config.workingDirectory)
        assertEquals(listOf("continuation"), prepared.config.scriptConfig.features)
    }

    private fun project(config: String, entryPath: String): File {
        val root = temporaryFolder.newFolder()
        File(root, "project.json").writeText(config)
        File(root, entryPath).apply {
            parentFile?.mkdirs()
            writeText("// scheduled project entry")
        }
        return root
    }

    private fun persistedLaunch(
        root: File,
        context: PersistedScriptLaunchContext,
        pythonPreviewEnabled: Boolean = false,
    ): ScriptLaunchSourceFactory.Launch = ScriptLaunchSourceFactory.createPersistedPathLaunchForPythonPreview(
        path = root.path,
        sourceKind = context.sourceKind,
        projectRoot = context.projectRoot,
        preExecuteScript = null,
        workingDirectory = null,
        defaultWorkingDirectory = "default",
        pythonPreviewEnabled = pythonPreviewEnabled,
    )
}
