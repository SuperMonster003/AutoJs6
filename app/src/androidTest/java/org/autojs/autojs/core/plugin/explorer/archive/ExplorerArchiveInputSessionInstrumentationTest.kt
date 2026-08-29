@file:Suppress("DEPRECATION")

package org.autojs.autojs.core.plugin.explorer.archive

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerArchiveInputKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveInputValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExplorerArchiveInputSessionInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun frozenSessionExposesOnlyOpaquePagedMetadataAndOneShotFiles() {
        val fixture = File(context.cacheDir, "archive-input-grant-${UUID.randomUUID()}")
        assertTrue(fixture.mkdirs())
        val loose = File(fixture, "loose.txt").apply { writeText("loose") }
        val folder = File(fixture, "folder").apply { assertTrue(mkdir()) }
        val empty = File(folder, "empty").apply { assertTrue(mkdir()) }
        val nested = File(folder, "nested.txt").apply { writeText("nested") }
        val operationId = UUID.randomUUID().toString()
        val session = ExplorerArchiveInputSession.create(
            expectedUid = Process.myUid(),
            archiveSessionId = "archive-session",
            operationId = operationId,
            selectedRoots = listOf(loose, folder),
        )

        try {
            val info = session.info
            assertEquals(
                setOf(
                    ExplorerArchiveInputKeys.GRANT_ID,
                    ExplorerArchiveInputKeys.ARCHIVE_SESSION_ID,
                    ExplorerArchiveInputKeys.OPERATION_ID,
                    ExplorerArchiveInputKeys.VIRTUAL_ROOT_ID,
                    ExplorerArchiveInputKeys.ROOT_COUNT,
                    ExplorerArchiveInputKeys.NODE_COUNT,
                    ExplorerArchiveInputKeys.FILE_COUNT,
                    ExplorerArchiveInputKeys.DIRECTORY_COUNT,
                    ExplorerArchiveInputKeys.KNOWN_TOTAL_BYTES,
                ),
                info.keySet(),
            )
            assertEquals("archive-session", info.getString(ExplorerArchiveInputKeys.ARCHIVE_SESSION_ID))
            assertEquals(operationId, info.getString(ExplorerArchiveInputKeys.OPERATION_ID))
            assertEquals(2, info.getInt(ExplorerArchiveInputKeys.ROOT_COUNT))
            assertEquals(4, info.getInt(ExplorerArchiveInputKeys.NODE_COUNT))
            assertEquals(2, info.getInt(ExplorerArchiveInputKeys.FILE_COUNT))
            assertEquals(2, info.getInt(ExplorerArchiveInputKeys.DIRECTORY_COUNT))
            assertEquals(loose.length() + nested.length(), info.getLong(ExplorerArchiveInputKeys.KNOWN_TOTAL_BYTES))
            val virtualRoot = requireNotNull(
                info.getString(ExplorerArchiveInputKeys.VIRTUAL_ROOT_ID),
            )
            assertFalse(virtualRoot.contains(fixture.name))

            val firstPage = session.listChildren(virtualRoot, 0, 1)
            val firstRoot = firstPage.items().single()
            assertEquals("loose.txt", firstRoot.getString(ExplorerArchiveInputKeys.NAME))
            assertFalse(firstPage.getBoolean(ExplorerArchiveInputKeys.COMPLETE))
            assertEquals(1, firstPage.getInt(ExplorerArchiveInputKeys.NEXT_OFFSET))
            val secondPage = session.listChildren(virtualRoot, 1, 1)
            val folderNode = secondPage.items().single()
            assertEquals("folder", folderNode.getString(ExplorerArchiveInputKeys.NAME))
            assertTrue(secondPage.getBoolean(ExplorerArchiveInputKeys.COMPLETE))
            listOf(firstRoot, folderNode).forEach { item ->
                assertEquals(
                    setOf(
                        ExplorerArchiveInputKeys.ID,
                        ExplorerArchiveInputKeys.PARENT_ID,
                        ExplorerArchiveInputKeys.NAME,
                        ExplorerArchiveInputKeys.KIND,
                        ExplorerArchiveInputKeys.SIZE,
                        ExplorerArchiveInputKeys.LAST_MODIFIED,
                    ),
                    item.keySet(),
                )
                assertEquals(virtualRoot, item.getString(ExplorerArchiveInputKeys.PARENT_ID))
                assertFalse(requireNotNull(item.getString(ExplorerArchiveInputKeys.ID)).contains('/'))
                assertFalse(item.toString().contains(fixture.absolutePath))
            }

            val folderId = requireNotNull(folderNode.getString(ExplorerArchiveInputKeys.ID))
            val nestedItems = session.listChildren(
                folderId,
                0,
                ExplorerActionProtocol.MAX_ARCHIVE_INPUT_PAGE_SIZE,
            ).items()
            assertEquals(listOf("empty", "nested.txt"), nestedItems.map { item ->
                item.getString(ExplorerArchiveInputKeys.NAME)
            })
            val emptyNode = nestedItems.first()
            assertEquals(
                ExplorerArchiveInputValues.KIND_DIRECTORY,
                emptyNode.getInt(ExplorerArchiveInputKeys.KIND),
            )
            assertTrue(
                session.listChildren(
                    requireNotNull(emptyNode.getString(ExplorerArchiveInputKeys.ID)),
                    0,
                    ExplorerActionProtocol.MAX_ARCHIVE_INPUT_PAGE_SIZE,
                ).items().isEmpty(),
            )

            val looseId = requireNotNull(firstRoot.getString(ExplorerArchiveInputKeys.ID))
            assertEquals(
                "loose",
                ParcelFileDescriptor.AutoCloseInputStream(session.openFile(looseId)).use { input ->
                    input.readBytes().decodeToString()
                },
            )
            assertTrue(runCatching { session.openFile(looseId) }.isFailure)
            val nestedId = requireNotNull(
                nestedItems.last().getString(ExplorerArchiveInputKeys.ID),
            )
            assertEquals(
                "nested",
                ParcelFileDescriptor.AutoCloseInputStream(session.openFile(nestedId)).use { input ->
                    input.readBytes().decodeToString()
                },
            )
            assertEquals(
                ExplorerArchiveInputValues.VERIFICATION_VALID,
                session.verifySnapshot().getInt(ExplorerArchiveInputKeys.VERIFICATION_STATUS),
            )
        } finally {
            session.closeFromHost()
            fixture.deleteRecursively()
        }

        assertFalse(fixture.exists())
        assertTrue(runCatching { session.info }.isFailure)
    }

    @Test
    fun frozenSessionPreservesSameNamedDirectoryRootsFromDifferentParents() {
        val fixture = File(context.cacheDir, "archive-input-same-roots-${UUID.randomUUID()}")
        assertTrue(fixture.mkdirs())
        val firstRoot = File(fixture, "first/shared").apply { assertTrue(mkdirs()) }
        val secondRoot = File(fixture, "second/shared").apply { assertTrue(mkdirs()) }
        File(firstRoot, "one.txt").writeText("one")
        File(secondRoot, "two.txt").writeText("two")
        val session = ExplorerArchiveInputSession.create(
            expectedUid = Process.myUid(),
            archiveSessionId = "archive-session",
            operationId = UUID.randomUUID().toString(),
            selectedRoots = listOf(firstRoot, secondRoot),
        )

        try {
            val virtualRoot = requireNotNull(
                session.info.getString(ExplorerArchiveInputKeys.VIRTUAL_ROOT_ID),
            )
            val roots = session.listChildren(
                virtualRoot,
                0,
                ExplorerActionProtocol.MAX_ARCHIVE_INPUT_PAGE_SIZE,
            ).items()
            assertEquals(listOf("shared", "shared"), roots.map { root ->
                root.getString(ExplorerArchiveInputKeys.NAME)
            })
            val rootIds = roots.map { root ->
                requireNotNull(root.getString(ExplorerArchiveInputKeys.ID))
            }
            assertEquals(2, rootIds.toSet().size)

            val contents = rootIds.map { rootId ->
                val fileId = requireNotNull(
                    session.listChildren(rootId, 0, 1).items().single()
                        .getString(ExplorerArchiveInputKeys.ID),
                )
                ParcelFileDescriptor.AutoCloseInputStream(session.openFile(fileId)).use { input ->
                    input.readBytes().decodeToString()
                }
            }
            assertEquals(listOf("one", "two"), contents)
            assertEquals(
                ExplorerArchiveInputValues.VERIFICATION_VALID,
                session.verifySnapshot().getInt(ExplorerArchiveInputKeys.VERIFICATION_STATUS),
            )
        } finally {
            session.closeFromHost()
            fixture.deleteRecursively()
        }

        assertFalse(fixture.exists())
    }

    @Test
    fun finalVerificationDetectsAFileChangedAfterItWasRead() {
        val fixture = File(context.cacheDir, "archive-input-changed-${UUID.randomUUID()}")
        assertTrue(fixture.mkdirs())
        val selected = File(fixture, "selected.txt").apply { writeText("before") }
        val session = ExplorerArchiveInputSession.create(
            expectedUid = Process.myUid(),
            archiveSessionId = "archive-session",
            operationId = UUID.randomUUID().toString(),
            selectedRoots = listOf(selected),
        )

        try {
            val root = requireNotNull(
                session.info.getString(ExplorerArchiveInputKeys.VIRTUAL_ROOT_ID),
            )
            val itemId = requireNotNull(
                session.listChildren(root, 0, 1).items().single()
                    .getString(ExplorerArchiveInputKeys.ID),
            )
            ParcelFileDescriptor.AutoCloseInputStream(session.openFile(itemId)).use { input ->
                assertEquals("before", input.readBytes().decodeToString())
            }
            val originalLength = selected.length()
            SystemClock.sleep(1_100L)
            selected.writeText("after!")
            assertEquals(originalLength, selected.length())

            assertEquals(
                ExplorerArchiveInputValues.VERIFICATION_SOURCE_CHANGED,
                session.verifySnapshot().getInt(ExplorerArchiveInputKeys.VERIFICATION_STATUS),
            )
        } finally {
            session.closeFromHost()
            fixture.deleteRecursively()
        }
    }

    @Test
    fun openingRejectsASelectedAncestorReplacedByASymlinkToTheSameFile() {
        val fixture = File(context.cacheDir, "archive-input-ancestor-${UUID.randomUUID()}")
        assertTrue(fixture.mkdirs())
        val selected = File(fixture, "selected").apply { assertTrue(mkdir()) }
        File(selected, "same.txt").writeText("selected")
        val displaced = File(fixture, "selected-frozen")
        val session = ExplorerArchiveInputSession.create(
            expectedUid = Process.myUid(),
            archiveSessionId = "archive-session",
            operationId = UUID.randomUUID().toString(),
            selectedRoots = listOf(selected),
        )

        try {
            val virtualRoot = requireNotNull(
                session.info.getString(ExplorerArchiveInputKeys.VIRTUAL_ROOT_ID),
            )
            val selectedId = requireNotNull(
                session.listChildren(virtualRoot, 0, 1).items().single()
                    .getString(ExplorerArchiveInputKeys.ID),
            )
            val fileId = requireNotNull(
                session.listChildren(selectedId, 0, 1).items().single()
                    .getString(ExplorerArchiveInputKeys.ID),
            )

            assertTrue(selected.renameTo(displaced))
            Os.symlink(displaced.absolutePath, selected.absolutePath)
            assertTrue(runCatching { session.openFile(fileId) }.isFailure)
        } finally {
            session.closeFromHost()
            val selectedStat = runCatching { Os.lstat(selected.absolutePath) }.getOrNull()
            if (selectedStat != null && OsConstants.S_ISLNK(selectedStat.st_mode)) {
                selected.delete()
            }
            if (displaced.exists()) {
                assertTrue(displaced.renameTo(selected))
            }
            fixture.deleteRecursively()
        }

        assertFalse(fixture.exists())
    }

    @Test
    fun freezingRejectsOverlappingAmbiguousAndSymbolicLinkInputs() {
        val fixture = File(context.cacheDir, "archive-input-invalid-${UUID.randomUUID()}")
        assertTrue(fixture.mkdirs())
        val tree = File(fixture, "tree").apply { assertTrue(mkdir()) }
        val child = File(tree, "child.txt").apply { writeText("child") }

        assertTrue(
            runCatching {
                ExplorerArchiveInputSession.create(
                    Process.myUid(),
                    "archive-session",
                    UUID.randomUUID().toString(),
                    listOf(tree, child),
                )
            }.isFailure,
        )

        val ambiguous = File(fixture, "ambiguous").apply { assertTrue(mkdir()) }
        File(ambiguous, "Name.txt").writeText("one")
        File(ambiguous, "name.TXT").writeText("two")
        assertTrue(
            runCatching {
                ExplorerArchiveInputSession.create(
                    Process.myUid(),
                    "archive-session",
                    UUID.randomUUID().toString(),
                    listOf(ambiguous),
                )
            }.isFailure,
        )

        val symbolicLink = File(fixture, "linked.txt")
        val directoryLink = File(fixture, "linked-tree")
        try {
            Os.symlink(child.absolutePath, symbolicLink.absolutePath)
            assertTrue(
                runCatching {
                    ExplorerArchiveInputSession.create(
                        Process.myUid(),
                        "archive-session",
                        UUID.randomUUID().toString(),
                        listOf(symbolicLink),
                    )
                }.isFailure,
            )
            Os.symlink(tree.absolutePath, directoryLink.absolutePath)
            assertTrue(
                runCatching {
                    ExplorerArchiveInputSession.create(
                        Process.myUid(),
                        "archive-session",
                        UUID.randomUUID().toString(),
                        listOf(File(directoryLink, child.name)),
                    )
                }.isFailure,
            )
        } finally {
            symbolicLink.delete()
            directoryLink.delete()
            fixture.deleteRecursively()
        }
        assertFalse(fixture.exists())
    }

    private fun Bundle.items(): List<Bundle> =
        getParcelableArrayList<Bundle>(ExplorerArchiveInputKeys.ITEMS).orEmpty()
}
