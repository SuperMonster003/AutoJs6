package org.autojs.autojs.core.terminal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NodeCliInstallerTest {

    private lateinit var root: File
    private lateinit var paths: TerminalPaths

    @Before
    fun setUp() {
        root = Files.createTempDirectory("terminal-test").toFile()
        paths = TerminalPaths(root).ensureLayout()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun zip(entries: Map<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun launcher(sha: String, versionCode: Long = 169, entries: Int? = null, bytes: Long? = null) = NodeCliLocator.Launcher(
        packageName = NodeCliLocator.OFFICIAL_PLUGIN_PACKAGE,
        versionCode = versionCode,
        nativeLibraryDir = "/data/app/plugin/lib/arm64",
        executable = File("/data/app/plugin/lib/arm64/libnodexe.so"),
        descriptor = NodeCliMetadata.Descriptor(
            executableName = "libnodexe.so",
            commands = listOf("node", "npm", "npx", "corepack"),
            archivePath = "nodejs/cli/node-cli-24.21.0.bin",
            archiveSha256 = sha,
            archiveRoot = "lib/node_modules",
            archiveEntryCount = entries,
            archiveBytes = bytes,
            npmVersion = "11.19.0",
            corepackVersion = "0.36.0",
        ),
    )

    private val goodEntries = mapOf(
        "lib/node_modules/npm/package.json" to "{\"version\":\"11.19.0\"}",
        "lib/node_modules/npm/bin/npm-cli.js" to "#!node npm",
        "lib/node_modules/npm/bin/npx-cli.js" to "#!node npx",
        "lib/node_modules/corepack/dist/corepack.js" to "corepack",
        "README.md" to "outside the root, skipped",
    )

    @Test
    fun installExtractsBelowTheRootAndWritesAStamp() {
        val archive = zip(goodEntries)
        val launcher = launcher(sha256(archive), entries = 4, bytes = 200)
        val stamp = NodeCliInstaller.install(paths, launcher) { ByteArrayInputStream(archive) }

        assertTrue(File(paths.nodeCliRoot, "npm/bin/npm-cli.js").isFile)
        assertTrue(File(paths.nodeCliRoot, "corepack/dist/corepack.js").isFile)
        assertFalse(File(paths.nodeCliRoot, "README.md").exists())
        assertFalse(File(paths.nodeCliRoot.path + ".tmp").exists())
        assertEquals(4, stamp.entries)
        assertEquals(launcher.descriptor.archiveSha256, stamp.archiveSha256)
        assertEquals(stamp, NodeCliInstaller.readStamp(paths))
        assertTrue(NodeCliInstaller.isInstalled(paths, launcher))
    }

    @Test
    fun digestMismatchLeavesThePreviousInstallUntouched() {
        val first = zip(goodEntries)
        NodeCliInstaller.install(paths, launcher(sha256(first))) { ByteArrayInputStream(first) }
        val marker = File(paths.nodeCliRoot, "npm/bin/npm-cli.js").readText()

        val tampered = zip(goodEntries + ("lib/node_modules/npm/bin/npm-cli.js" to "evil"))
        val wrongDigest = launcher(sha256(first))
        val failure = runCatching { NodeCliInstaller.install(paths, wrongDigest) { ByteArrayInputStream(tampered) } }
        assertTrue(failure.exceptionOrNull() is IOException)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("digest"))

        assertEquals(marker, File(paths.nodeCliRoot, "npm/bin/npm-cli.js").readText())
        assertFalse(File(paths.nodeCliRoot.path + ".tmp").exists())
        assertFalse(File(paths.nodeCliRoot.path + ".old").exists())
        assertTrue(NodeCliInstaller.isInstalled(paths, launcher(sha256(first))))
    }

    @Test
    fun zipSlipEntriesAreRejected() {
        val archive = zip(goodEntries + ("lib/node_modules/../../escape.txt" to "x"))
        val failure = runCatching { NodeCliInstaller.install(paths, launcher(sha256(archive))) { ByteArrayInputStream(archive) } }
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("unsafe"))
        assertFalse(File(root, "escape.txt").exists())
        assertFalse(File(paths.lib, "escape.txt").exists())
        assertFalse(paths.nodeCliRoot.exists())
        assertNull(NodeCliInstaller.readStamp(paths))
    }

    @Test
    fun declaredLimitsAreEnforced() {
        val archive = zip(goodEntries)
        val tooFew = launcher(sha256(archive), entries = 2)
        val failure = runCatching { NodeCliInstaller.install(paths, tooFew) { ByteArrayInputStream(archive) } }
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("entries"))
        assertFalse(paths.nodeCliRoot.exists())

        val tooSmall = launcher(sha256(archive), bytes = 10)
        val sizeFailure = runCatching { NodeCliInstaller.install(paths, tooSmall) { ByteArrayInputStream(archive) } }
        assertTrue(sizeFailure.exceptionOrNull()?.message.orEmpty().contains("bytes"))
    }

    @Test
    fun missingLauncherScriptsFailTheInstall() {
        val archive = zip(goodEntries - "lib/node_modules/corepack/dist/corepack.js")
        val failure = runCatching { NodeCliInstaller.install(paths, launcher(sha256(archive))) { ByteArrayInputStream(archive) } }
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("corepack"))
        assertFalse(paths.nodeCliRoot.exists())
    }

    @Test
    fun pluginUpdateRequiresReinstall() {
        val archive = zip(goodEntries)
        val installed = launcher(sha256(archive), versionCode = 169)
        NodeCliInstaller.install(paths, installed) { ByteArrayInputStream(archive) }
        assertTrue(NodeCliInstaller.isInstalled(paths, installed))

        assertFalse(NodeCliInstaller.isInstalled(paths, launcher(sha256(archive), versionCode = 170)))
        assertFalse(NodeCliInstaller.isInstalled(paths, launcher("0".repeat(64), versionCode = 169)))

        File(paths.nodeCliRoot, "npm/bin/npx-cli.js").delete()
        assertFalse("a missing launcher script invalidates the install", NodeCliInstaller.isInstalled(paths, installed))
    }

    @Test
    fun corruptStampIsIgnored() {
        File(paths.nodeCliRoot.also { it.mkdirs() }, NodeCliInstaller.STAMP_FILE_NAME).writeText("{not json")
        assertNull(NodeCliInstaller.readStamp(paths))
        assertNotNull(NodeCliInstaller.stampFor(launcher("a".repeat(64)), 1, 1))
    }

    @Test
    fun limitsAreClampedToAbsoluteBounds() {
        val (entries, bytes) = NodeCliArchive.limitsFor(null, null)
        assertEquals(NodeCliArchive.DEFAULT_MAX_ENTRIES, entries)
        assertEquals(NodeCliArchive.DEFAULT_MAX_BYTES, bytes)
        val (hugeEntries, hugeBytes) = NodeCliArchive.limitsFor(Int.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(NodeCliArchive.ABSOLUTE_MAX_ENTRIES, hugeEntries)
        assertEquals(NodeCliArchive.ABSOLUTE_MAX_BYTES, hugeBytes)
        assertEquals(1699 to 10_068_758L, NodeCliArchive.limitsFor(1699, 10_068_758L))
    }

}
