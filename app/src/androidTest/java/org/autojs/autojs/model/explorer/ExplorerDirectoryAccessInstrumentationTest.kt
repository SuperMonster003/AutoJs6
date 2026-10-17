package org.autojs.autojs.model.explorer

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExplorerDirectoryAccessInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testDirectory: File

    @Before
    fun createTestDirectory() {
        testDirectory = File(context.cacheDir, "explorer-directory-access-${UUID.randomUUID()}")
        assertTrue(testDirectory.mkdir())
    }

    @After
    fun removeTestDirectory() {
        testDirectory.deleteRecursively()
    }

    @Test
    fun anActuallyEmptyDirectoryIsSuccessfullyLoaded() {
        val page = ExplorerFileProvider().getExplorerPage(ExplorerDirPage.createRoot(testDirectory))
            .blockingGet()

        assertEquals(testDirectory.path, page.path)
        assertTrue(page.none())
    }

    @Test
    fun missingDirectoryIsReportedInsteadOfAppearingEmpty() {
        assertListingFailure(File(testDirectory, "missing"), ExplorerDirectoryAccessException.Reason.NOT_FOUND)
    }

    @Test
    fun replacedDirectoryWhichIsNowAFileIsReported() {
        val file = File(testDirectory, "replaced-directory").apply { writeText("content") }
        assertListingFailure(file, ExplorerDirectoryAccessException.Reason.NOT_DIRECTORY)
    }

    @Test
    fun inaccessibleAncestorIsNotMisreportedAsAMissingOrEmptyDirectory() {
        val parent = File(testDirectory, "denied-parent").apply { mkdir() }
        val child = File(parent, "child").apply { mkdir() }
        Os.chmod(parent.path, 0)
        try {
            assertListingFailure(child, ExplorerDirectoryAccessException.Reason.ACCESS_DENIED)
        } finally {
            Os.chmod(parent.path, 448) // 0700: restore access before cleanup.
        }
    }

    private fun assertListingFailure(directory: File, reason: ExplorerDirectoryAccessException.Reason) {
        ExplorerFileProvider().getExplorerPage(ExplorerDirPage.createRoot(directory))
            .test()
            .awaitDone(10, TimeUnit.SECONDS)
            .assertNoValues()
            .assertError { error ->
                error is ExplorerDirectoryAccessException && error.path == directory.path && error.reason == reason
            }
    }
}
