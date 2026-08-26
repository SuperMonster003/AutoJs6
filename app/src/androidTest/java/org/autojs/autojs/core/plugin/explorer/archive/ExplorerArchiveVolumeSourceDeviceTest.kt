package org.autojs.autojs.core.plugin.explorer.archive

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.plugin.explorer.api.ExplorerArchiveVolumeSourceKeys
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ExplorerArchiveVolumeSourceDeviceTest {

    @Test
    fun catalogExposesOnlyExistingNamesAllowedForTheSelectedArchive() = withTestRoot { root ->
        val primary = File(root, "archive.zip").apply { writeText("primary") }
        val first = File(root, "archive.z01").apply { writeText("first-volume") }
        val third = File(root, "archive.z03").apply { writeText("third-volume") }
        File(root, "unrelated.z02").writeText("unrelated")
        File(root, "archive.z02").mkdir()

        val source = requireNotNull(
            ExplorerArchiveVolumeSource.create(
                source = primary,
                expectedUid = TEST_UID,
                callingUid = { TEST_UID },
            ),
        )
        try {
            val catalog = source.catalogVolumes()

            assertEquals(listOf(first.name, third.name), catalog.map(VolumeRecord::displayName))
            assertTrue(catalog.all { it.id != it.displayName && File(it.id).name == it.id })
            assertEquals(listOf(first.length(), third.length()), catalog.map(VolumeRecord::size))
            assertTrue(catalog.all { it.lastModifiedMillis >= 0L })

            val firstRecord = catalog.first()
            ParcelFileDescriptor.AutoCloseInputStream(source.openVolume(firstRecord.id)).use { input ->
                assertArrayEquals(first.readBytes(), input.readBytes())
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun wrongCallerUnknownIdAndClosedSourceAreRejected() = withTestRoot { root ->
        val primary = File(root, "set.part1.rar").apply { writeText("primary") }
        File(root, "set.part2.rar").writeText("second")
        val callerUid = AtomicInteger(TEST_UID + 1)
        val source = requireNotNull(
            ExplorerArchiveVolumeSource.create(
                source = primary,
                expectedUid = TEST_UID,
                callingUid = callerUid::get,
            ),
        )

        expectFailure<SecurityException> { source.catalog }
        callerUid.set(TEST_UID)
        assertEquals(1, source.catalogVolumes().size)
        expectFailure<IllegalArgumentException> {
            source.openVolume(UUID.randomUUID().toString())
        }

        source.close()
        expectFailure<IllegalStateException> { source.catalog }
    }

    @Test
    fun changedVolumeIsRejectedBeforeCatalogOrOpen() = withTestRoot { root ->
        val primary = File(root, "set.part1.rar").apply { writeText("primary") }
        val companion = File(root, "set.part2.rar").apply { writeText("second") }
        val source = requireNotNull(
            ExplorerArchiveVolumeSource.create(
                source = primary,
                expectedUid = TEST_UID,
                callingUid = { TEST_UID },
            ),
        )
        try {
            val id = source.catalogVolumes().single().id
            companion.appendText("-changed")

            expectFailure<IllegalStateException> { source.catalog }
            expectFailure<IllegalStateException> { source.openVolume(id) }
        } finally {
            source.close()
        }
    }

    @Test
    fun symbolicLinksAndDirectoriesAreNeverCatalogedAsVolumes() = withTestRoot { root ->
        val primary = File(root, "archive.zip").apply { writeText("primary") }
        val target = File(root, "target.bin").apply { writeText("target") }
        val link = File(root, "archive.z01")
        Os.symlink(target.absolutePath, link.absolutePath)
        assertTrue(File(root, "archive.z02").mkdir())
        assertEquals(target.canonicalFile, link.canonicalFile)

        assertNull(
            ExplorerArchiveVolumeSource.create(
                source = primary,
                expectedUid = TEST_UID,
                callingUid = { TEST_UID },
            ),
        )
    }

    private fun withTestRoot(block: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "archive-volume-source-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError(
                "Expected ${T::class.java.name}, but received ${error.javaClass.name}",
                error,
            )
        }
        throw AssertionError("Expected ${T::class.java.name}")
    }

    @Suppress("DEPRECATION")
    private fun ExplorerArchiveVolumeSource.catalogVolumes(): List<VolumeRecord> =
        catalog.getParcelableArrayList<Bundle>(ExplorerArchiveVolumeSourceKeys.VOLUMES)
            .orEmpty()
            .map { item ->
                VolumeRecord(
                    id = requireNotNull(item.getString(ExplorerArchiveVolumeSourceKeys.VOLUME_ID)),
                    displayName = requireNotNull(
                        item.getString(ExplorerArchiveVolumeSourceKeys.DISPLAY_NAME),
                    ),
                    size = item.getLong(ExplorerArchiveVolumeSourceKeys.SIZE, -1L),
                    lastModifiedMillis = item.getLong(
                        ExplorerArchiveVolumeSourceKeys.LAST_MODIFIED,
                        -1L,
                    ),
                )
            }

    private data class VolumeRecord(
        val id: String,
        val displayName: String,
        val size: Long,
        val lastModifiedMillis: Long,
    )

    private companion object {
        const val TEST_UID = 41_234
    }
}
