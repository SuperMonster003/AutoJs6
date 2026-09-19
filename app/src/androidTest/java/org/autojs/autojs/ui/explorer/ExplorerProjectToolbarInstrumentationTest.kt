package org.autojs.autojs.ui.explorer

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerProjectPage
import org.autojs.autojs.model.explorer.WorkspaceFileProvider
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ExplorerProjectToolbarInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext

    @Test
    fun nodeProjectWithoutPackagingMetadataIsRecognizedByToolbarAndFileList() = withProjectDirectory { root ->
        val project = File(root, "host-events").apply { mkdir() }
        File(project, "project.json").writeText("""{"name":"host-events","type":"node","main":"main.cjs","node":{"timeoutMs":65000,"permissions":["events","events.key","events.notification","events.toast"]}}""")
        File(project, "package.json").writeText("""{"name":"@autojs6-sample/host-events","private":true,"version":"1.0.0","type":"commonjs"}""")
        File(project, "main.cjs").writeText("console.log('ready');")
        val toolbar = createToolbar()
        assertTrue(onMain { toolbar.trySetProject(PFile(project.path)) })
        onMain {
            assertEquals("host-events", toolbar.mProjectName.text.toString())
            assertEquals(View.VISIBLE, toolbar.action(R.id.project_run).visibility)
            assertEquals(View.VISIBLE, toolbar.action(R.id.project_terminal).visibility)
            assertEquals(View.VISIBLE, toolbar.action(R.id.project_edit).visibility)
            assertEquals(View.GONE, toolbar.action(R.id.project_build).visibility)
        }
        val page = WorkspaceFileProvider(targetContext) { true }.getExplorerPage(ExplorerDirPage(root, null)).blockingGet()
        val entry = page.single { it.path == project.path }
        assertTrue(entry is ExplorerProjectPage)
        assertEquals(page, entry.parent)
    }

    @Test
    fun twoKeyPythonManifestIsRunnableOnlyAndKeepsAValidDirectory() = withProjectDirectory { root ->
        File(root, "project.json").writeText("""{"type":"python","main":"main.py"}""")
        File(root, "main.py").writeText("print('ready')")

        val toolbar = createToolbar()
        assertTrue(onMain { toolbar.trySetProject(PFile(root.path)) })

        onMain {
            assertEquals(View.VISIBLE, toolbar.visibility)
            assertEquals(root.name, toolbar.mProjectName.text.toString())
            assertEquals(View.VISIBLE, toolbar.action(R.id.project_run).visibility)
            assertEquals(View.GONE, toolbar.action(R.id.project_edit).visibility)
            assertEquals(View.GONE, toolbar.action(R.id.project_build).visibility)
            assertEquals(root.canonicalFile, toolbar.projectDirectory().canonicalFile)

            toolbar.setRunnableOnly(false)
            assertEquals(View.GONE, toolbar.action(R.id.project_edit).visibility)
            assertEquals(View.GONE, toolbar.action(R.id.project_build).visibility)
        }
    }

    @Test
    fun rejectedManifestHidesToolbarClearsStateAndLeavesActionsNullSafe() = withProjectDirectory { root ->
        File(root, "project.json").writeText("""{"type":"python","main":"main.py"}""")
        File(root, "main.py").writeText("print('ready')")

        val toolbar = createToolbar()
        assertTrue(onMain { toolbar.trySetProject(PFile(root.path)) })
        File(root, "project.json").writeText("""{"type":"python","main":"../main.py"}""")

        assertFalse(onMain { toolbar.trySetProject(PFile(root.path)) })
        onMain {
            assertEquals(View.GONE, toolbar.visibility)
            assertNull(toolbar.projectDirectoryOrNull())
            toolbar.run()
            toolbar.build()
            toolbar.edit()
            assertEquals(View.GONE, toolbar.visibility)
        }
    }

    @Test
    fun legacyProjectKeepsItsExistingActionSet() = withProjectDirectory { root ->
        File(root, "project.json").writeText(
            """
            {
              "name": "Legacy project",
              "versionName": "1.0.0",
              "versionCode": 1,
              "packageName": "org.example.legacy",
              "main": "main.js"
            }
            """.trimIndent(),
        )

        val toolbar = createToolbar()
        assertTrue(onMain { toolbar.trySetProject(PFile(root.path)) })

        onMain {
            assertEquals("Legacy project", toolbar.mProjectName.text.toString())
            assertEquals(View.VISIBLE, toolbar.action(R.id.project_run).visibility)
            assertEquals(View.VISIBLE, toolbar.action(R.id.project_edit).visibility)
            assertEquals(View.VISIBLE, toolbar.action(R.id.project_build).visibility)
        }
    }

    private fun createToolbar(): ExplorerProjectToolbar = onMain {
        ExplorerProjectToolbar(ContextThemeWrapper(targetContext, R.style.AppTheme))
    }

    private fun ExplorerProjectToolbar.action(id: Int): ImageView = findViewById(id)

    private fun ExplorerProjectToolbar.projectDirectory(): PFile =
        requireNotNull(projectDirectoryOrNull())

    private fun ExplorerProjectToolbar.projectDirectoryOrNull(): PFile? {
        val field = ExplorerProjectToolbar::class.java.getDeclaredField("mDirectory")
        field.isAccessible = true
        return field.get(this) as? PFile
    }

    private fun <T> onMain(block: () -> T): T {
        val outcome = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { outcome.set(runCatching(block)) }
        return requireNotNull(outcome.get()).getOrThrow()
    }

    private fun withProjectDirectory(block: (File) -> Unit) {
        val root = File(targetContext.cacheDir, "python-toolbar-${System.nanoTime()}")
        check(root.mkdir()) { "Could not create project fixture" }
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
