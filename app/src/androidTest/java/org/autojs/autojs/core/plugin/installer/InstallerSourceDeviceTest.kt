package org.autojs.autojs.core.plugin.installer

import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual Android descriptors, inode checks and provider pipes; no user files are touched. */
@RunWith(AndroidJUnit4::class)
class InstallerSourceDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun unchangedOwnedFileIsDeletedWhileDescriptorRemainsReadable() = withDirectory { directory ->
        val file = File(directory, "original.apk").apply { writeText("owned package bytes") }
        InstallerSource.fromFile(file).use { source ->
            assertTrue(source.deleteOwnedFileIfUnchanged())
            assertFalse(file.exists())
            assertEquals(19L, source.descriptor.statSize)
            assertFalse(source.deleteOwnedFileIfUnchanged())
        }
    }

    @Test
    fun replacementAndModifiedFilesAreRetained() = withDirectory { directory ->
        val file = File(directory, "replace.apk").apply { writeText("initial") }
        InstallerSource.fromFile(file).use { source ->
            assertTrue(file.renameTo(File(directory, "saved.apk")))
            file.writeText("new content")
            assertFalse(source.deleteOwnedFileIfUnchanged())
            assertEquals("new content", file.readText())
        }
        InstallerSource.fromFile(file).use { source ->
            file.appendText(" modified")
            assertFalse(source.deleteOwnedFileIfUnchanged())
            assertTrue(file.exists())
        }
    }

    @Test
    fun symlinksAndClosedSourcesAreRetained() = withDirectory { directory ->
        val file = File(directory, "target.apk").apply { writeText("owned bytes") }
        val link = File(directory, "link.apk")
        Os.symlink(file.path, link.path)
        InstallerSource.fromFile(link).use { source ->
            assertFalse(source.deleteOwnedFileIfUnchanged())
            assertTrue(file.exists())
            assertTrue(link.exists())
        }
        val source = InstallerSource.fromFile(file)
        source.close()
        assertFalse(source.deleteOwnedFileIfUnchanged())
        assertTrue(file.exists())
    }

    @Test
    fun unknownSizeContentPipeCanBeConsumedWithoutOwningItsSource() {
        InstallerSource.fromUri(context, Uri.parse("content://org.autojs.autojs6.test.installer.sources/stream")).use { source ->
            assertEquals("stream.apk", source.displayName)
            assertEquals(-1L, source.size)
            assertNull(source.ownedFile)
            assertFalse(source.deleteOwnedFileIfUnchanged())
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(source.descriptor.fileDescriptor)).use { input ->
                assertEquals("installer pipe bytes", input.bufferedReader().readText())
            }
        }
    }

    @Test
    fun cancelledProviderOpenAndDeniedPermissionHaveSourceErrors() {
        val cancelled = CancellationSignal().apply { cancel() }
        val failure = assertThrows(OperationCanceledException::class.java) {
            InstallerSource.fromUri(context, Uri.parse("content://org.autojs.autojs6.test.installer.sources/stream"), cancellationSignal = cancelled)
        }
        assertEquals(InstallerErrorCodes.CANCELLED, InstallerErrorMapper.fromThrowable(failure, InstallerErrorMapper.Phase.SOURCE).code)
        val denied = assertThrows(SecurityException::class.java) {
            InstallerSource.fromUri(context, Uri.parse("content://org.autojs.autojs6.test.installer.sources/denied"))
        }
        assertEquals(InstallerErrorCodes.SOURCE_UNREADABLE, InstallerErrorMapper.fromThrowable(denied, InstallerErrorMapper.Phase.SOURCE).code)
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = File(context.cacheDir, "installer-source-${java.util.UUID.randomUUID()}")
        check(directory.mkdirs())
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
