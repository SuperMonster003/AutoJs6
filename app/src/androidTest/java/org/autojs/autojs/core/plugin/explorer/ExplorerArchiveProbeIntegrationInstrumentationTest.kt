package org.autojs.autojs.core.plugin.explorer

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerProvider
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerFileItem
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveMutationFailure
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveMutationListener
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveMutationProgress
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveMutationResult
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenRequestOptions
import org.autojs.autojs.core.plugin.explorer.session.ExplorerActionReplacementHistoryStore
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ExplorerArchiveProbeIntegrationInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installedArchiveManagerRoutesUnmatchedNamesThroughOneReadOnlySession() = runBlocking {
        assumeTrue("Archive Manager is not installed", pluginIsInstalled())
        assumeTrue(
            "Archive Manager is not trusted by this host build",
            PluginTrustManager.isAuthorized(context, PLUGIN_PACKAGE),
        )
        val root = File(context.cacheDir, "archive-probe-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        val recognizedZip = createZip(File(root, "recognized.zip"))
        val disguisedZip = createZip(File(root, "disguised.data"))
        val compoundTarName = File(root, "recognized.tar.gz").apply { writeBytes(byteArrayOf(1)) }
        val genericGzipName = File(root, "ordinary.gz").apply { writeBytes(byteArrayOf(1)) }

        try {
            ExplorerActionRegistry.refresh(context)

            assertTrue(
                ACTION_OPEN_ARCHIVE in actionIds(
                    recognizedZip,
                    ExplorerActionEntryPoint.PRIMARY_BUTTON,
                ),
            )
            assertFalse(
                ACTION_OPEN_AS_ARCHIVE in actionIds(
                    recognizedZip,
                    ExplorerActionEntryPoint.OVERFLOW_MENU,
                ),
            )
            assertTrue(
                ACTION_OPEN_ARCHIVE in actionIds(
                    compoundTarName,
                    ExplorerActionEntryPoint.PRIMARY_BUTTON,
                ),
            )
            assertFalse(
                ACTION_OPEN_ARCHIVE in actionIds(
                    genericGzipName,
                    ExplorerActionEntryPoint.PRIMARY_BUTTON,
                ),
            )
            assertTrue(
                ACTION_OPEN_AS_ARCHIVE in actionIds(
                    genericGzipName,
                    ExplorerActionEntryPoint.OVERFLOW_MENU,
                ),
            )

            val item = ExplorerFileItem(disguisedZip, null)
            val actions = ExplorerActionRegistry.actionsFor(
                ExplorerSurface.MAIN_FILES,
                ExplorerActionEntryPoint.OVERFLOW_MENU,
                listOf(item),
            ).filter { action -> action.packageName == PLUGIN_PACKAGE }
            assertEquals(
                listOf(ACTION_OPEN_AS_ARCHIVE),
                actions.filter { action ->
                    action.descriptor.presentation ==
                        org.autojs.plugin.explorer.api.ExplorerActionValues.PRESENTATION_HOST_EXPLORER
                }.map { action -> action.descriptor.id },
            )
            val fallback = actions.single { action ->
                action.descriptor.id == ACTION_OPEN_AS_ARCHIVE
            }

            val connection = ExplorerArchiveOpenRequestOptions().use { options ->
                ExplorerActionRegistry.openArchive(
                    context = context,
                    surface = ExplorerSurface.MAIN_FILES,
                    entryPoint = ExplorerActionEntryPoint.OVERFLOW_MENU,
                    item = item,
                    action = fallback,
                    options = options,
                )
            }
            val provider = ArchiveExplorerProvider(
                connection = connection,
                cacheDirectory = File(root, "entry-cache"),
            )
            try {
                assertEquals("zip", connection.info.formatId)
                assertEquals("ZIP", provider.formatDisplayName)
                assertTrue(provider.shouldAnnotateFormat)
                val loadedRoot = provider.getExplorerPage(provider.rootPage).blockingGet()
                assertEquals(listOf(ENTRY_NAME), loadedRoot.map { child -> child.name })
            } finally {
                provider.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun installedArchiveManagerRenamesAndDeletesZipEntriesThroughTheHostSession() = runBlocking {
        assumeTrue("Archive Manager is not installed", pluginIsInstalled())
        assumeTrue(
            "Archive Manager is not trusted by this host build",
            PluginTrustManager.isAuthorized(context, PLUGIN_PACKAGE),
        )
        val root = File(context.cacheDir, "archive-mutation-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        val archive = createWritableZip(File(root, "managed.zip"))
        val historyStore = ExplorerActionReplacementHistoryStore(context)
        var provider: ArchiveExplorerProvider? = null

        try {
            ExplorerActionRegistry.refresh(context)
            val item = ExplorerFileItem(archive, null)
            val action = ExplorerActionRegistry.actionsFor(
                ExplorerSurface.MAIN_FILES,
                ExplorerActionEntryPoint.PRIMARY_BUTTON,
                listOf(item),
            ).single { candidate ->
                candidate.packageName == PLUGIN_PACKAGE &&
                    candidate.descriptor.id == ACTION_OPEN_ARCHIVE
            }
            val connection = ExplorerArchiveOpenRequestOptions().use { options ->
                ExplorerActionRegistry.openArchive(
                    context = context,
                    surface = ExplorerSurface.MAIN_FILES,
                    entryPoint = ExplorerActionEntryPoint.PRIMARY_BUTTON,
                    item = item,
                    action = action,
                    options = options,
                )
            }
            provider = ArchiveExplorerProvider(
                connection = connection,
                cacheDirectory = File(root, "entry-cache"),
            )
            val initial = provider.getExplorerPage(provider.rootPage).blockingGet()
                .filterIsInstance<ArchiveExplorerFileItem>()
                .single { entry -> entry.name == ENTRY_NAME }
            assertTrue(initial.canRenameEntry)
            assertTrue(initial.canDeleteEntry)
            val stableEntryId = initial.entryId

            val renamedResult = awaitMutation { listener ->
                provider.startRename(initial, RENAMED_ENTRY_NAME, listener)
            }
            assertEquals(1, renamedResult.mutatedEntries)
            provider.reloadAfterMutation()
            val renamed = provider.getExplorerPage(provider.rootPage).blockingGet()
                .filterIsInstance<ArchiveExplorerFileItem>()
                .single { entry -> entry.name == RENAMED_ENTRY_NAME }
            assertEquals(RENAMED_ENTRY_NAME, renamed.name)
            assertEquals(stableEntryId, renamed.entryId)
            assertEquals(setOf(KEEP_ENTRY_NAME, RENAMED_ENTRY_NAME), zipEntryNames(archive).toSet())

            val deletedResult = awaitMutation { listener ->
                provider.startDeletion(listOf(renamed), listener)
            }
            assertEquals(1, deletedResult.mutatedEntries)
            provider.reloadAfterMutation()
            assertEquals(
                listOf(KEEP_ENTRY_NAME),
                provider.getExplorerPage(provider.rootPage).blockingGet().map { entry -> entry.name },
            )
            assertEquals(listOf(KEEP_ENTRY_NAME), zipEntryNames(archive))
        } finally {
            provider?.close()
            historyStore.removeAllForTarget(context.applicationInfo.uid, archive)
            root.deleteRecursively()
        }
    }

    private fun awaitMutation(
        start: (ExplorerArchiveMutationListener) -> String,
    ): ExplorerArchiveMutationResult {
        val terminal = CountDownLatch(1)
        val completed = AtomicReference<ExplorerArchiveMutationResult?>()
        val failed = AtomicReference<ExplorerArchiveMutationFailure?>()
        start(
            object : ExplorerArchiveMutationListener {
                override fun onProgress(progress: ExplorerArchiveMutationProgress) = Unit

                override fun onCompleted(result: ExplorerArchiveMutationResult) {
                    completed.set(result)
                    terminal.countDown()
                }

                override fun onFailed(failure: ExplorerArchiveMutationFailure) {
                    failed.set(failure)
                    terminal.countDown()
                }
            },
        )
        assertTrue(
            "Archive mutation did not finish within the deadline",
            terminal.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        assertNull(failed.get()?.message, failed.get())
        return requireNotNull(completed.get())
    }

    private fun actionIds(
        file: File,
        entryPoint: ExplorerActionEntryPoint,
    ): Set<String> = ExplorerActionRegistry.actionsFor(
        ExplorerSurface.MAIN_FILES,
        entryPoint,
        listOf(ExplorerFileItem(file, null)),
    ).asSequence()
        .filter { action -> action.packageName == PLUGIN_PACKAGE }
        .map { action -> action.descriptor.id }
        .toSet()

    private fun createZip(target: File): File {
        ZipOutputStream(FileOutputStream(target)).use { output ->
            output.putNextEntry(ZipEntry(ENTRY_NAME))
            output.write("archive probe payload".toByteArray())
            output.closeEntry()
        }
        return target
    }

    private fun createWritableZip(target: File): File {
        ZipOutputStream(FileOutputStream(target)).use { output ->
            listOf(ENTRY_NAME, KEEP_ENTRY_NAME).forEach { name ->
                output.putNextEntry(ZipEntry(name))
                output.write("archive mutation payload for $name".toByteArray())
                output.closeEntry()
            }
        }
        return target
    }

    private fun zipEntryNames(archive: File): List<String> = ZipFile(archive).use { zip ->
        zip.entries().asSequence().map(ZipEntry::getName).toList()
    }

    private fun pluginIsInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PLUGIN_PACKAGE, 0)
    }.isSuccess

    private companion object {
        const val PLUGIN_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.archivemanager"
        const val ACTION_OPEN_ARCHIVE = "open-archive"
        const val ACTION_OPEN_AS_ARCHIVE = "open-as-archive"
        const val ENTRY_NAME = "payload.txt"
        const val KEEP_ENTRY_NAME = "keep.txt"
        const val RENAMED_ENTRY_NAME = "renamed.txt"
        const val OPERATION_TIMEOUT_SECONDS = 30L
    }
}
