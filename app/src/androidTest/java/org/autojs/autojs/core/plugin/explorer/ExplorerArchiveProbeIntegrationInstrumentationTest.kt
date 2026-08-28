package org.autojs.autojs.core.plugin.explorer

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerProvider
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenRequestOptions
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    private fun pluginIsInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PLUGIN_PACKAGE, 0)
    }.isSuccess

    private companion object {
        const val PLUGIN_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.archivemanager"
        const val ACTION_OPEN_ARCHIVE = "open-archive"
        const val ACTION_OPEN_AS_ARCHIVE = "open-as-archive"
        const val ENTRY_NAME = "payload.txt"
    }
}
